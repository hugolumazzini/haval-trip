package br.com.hugolumazzini.havaltrip.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.TelemetrySample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Leitura real do H6, pela ponte que o HavalShisuku já mantém.
 *
 * O aplicativo [HavalShisuku](https://github.com/bobaoapae/haval-app-tool-multimidia)
 * conversa com os serviços internos da GWM e, a cada valor que muda, reemite um
 * broadcast aberto `android.intent.haval.<chave>` com o valor em `"value"`.
 * Nada aqui fala com o barramento do carro: só escutamos o que ele publica.
 *
 * Por isso a dependência é dura — sem o Shisuku instalado e rodando, não chega
 * amostra nenhuma. É o que [shisukuInstalado] existe para responder antes de a
 * tela ficar mostrando zeros parecendo defeito nosso.
 */
class HavalTelemetrySource(
    private val context: Context,
    /** Espelho dos valores crus, para a tela de diagnóstico. */
    private val diario: DiarioDeCampo = DiarioDeCampo(),
    private val intervaloMs: Long = 1000L,
) : TelemetrySource {

    val campo: DiarioDeCampo get() = diario

    private val cache = ConcurrentHashMap<String, String>()

    override fun samples(): Flow<TelemetrySample> = callbackFlow {
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val acao = intent?.action ?: return
                val chave = acao.removePrefix(PREFIXO)
                // O Shisuku emite dois broadcasts por mudança: um com o valor
                // em extra e outro com o valor grudado no nome da ação. Só o
                // primeiro interessa; o segundo chegaria com chave inventada.
                val valor = intent.getStringExtra("value") ?: return
                cache[chave] = valor
                diario.registrar(chave, valor)
            }
        }

        val filtro = IntentFilter().apply { CHAVES.forEach { addAction(PREFIXO + it) } }
        ContextCompat.registerReceiver(context, receptor, filtro, ContextCompat.RECEIVER_EXPORTED)

        // Um broadcast só chega quando o valor muda. Sem este pedido, o
        // hodômetro só apareceria no primeiro quilômetro rodado, e com o carro
        // parado a tela ficaria vazia sem nada estar errado.
        pedirTudo(context)

        launch {
            while (isActive) {
                delay(intervaloMs)
                trySend(montarAmostra())
            }
        }

        awaitClose { runCatching { context.unregisterReceiver(receptor) } }
    }

    private fun montarAmostra(): TelemetrySample {
        val velocidade = numero(CHAVE_VELOCIDADE) ?: 0.0
        val consumoBruto = numero(CHAVE_CONSUMO_INSTANTANEO)
        val percentual = numero(CHAVE_TANQUE_PERCENTUAL)

        return TelemetrySample(
            timestampMs = System.currentTimeMillis(),
            speedKmh = velocidade,
            fuelRateLph = Unidades.litrosPorHora(consumoBruto, velocidade, diario.interpretacao.value),
            odometerTotalKm = numero(CHAVE_HODOMETRO) ?: 0.0,
            fuelLevelL = Unidades.litrosNoTanque(percentual),
            ignition = ignicao(),
        )
    }

    /**
     * Ignição a partir do estado do motor, com o modo de energia como reserva.
     *
     * O mapeamento dos valores ainda não foi confirmado no carro — por isso o
     * critério é "qualquer coisa diferente de 0 é ligado", que erra no máximo
     * para o lado seguro, e o valor cru vai inteiro para o diagnóstico.
     */
    private fun ignicao(): IgnitionState {
        val motor = cache[CHAVE_MOTOR]
        val energia = cache[CHAVE_MODO_ENERGIA]
        val ligado = when {
            motor != null -> motor != "0"
            energia != null -> energia != "0"
            else -> false
        }
        return if (ligado) IgnitionState.ON else IgnitionState.OFF
    }

    private fun numero(chave: String): Double? = cache[chave]?.trim()?.toDoubleOrNull()

    companion object {
        const val PREFIXO = "android.intent.haval."

        /** Pacote do HavalShisuku, de quem dependem todos os broadcasts. */
        const val PACOTE_SHISUKU = "br.com.redesurftank.havalshisuku"

        /** Faz o Shisuku reemitir todos os valores que já tem em cache. */
        const val ACAO_PEDIR_TUDO = "br.com.redesurftank.havalshisuku.ACTION_DISPATCH_ALL_DATAS"

        const val CHAVE_VELOCIDADE = "car.basic.vehicle_speed"
        const val CHAVE_HODOMETRO = "car.basic.total_odometer"
        const val CHAVE_CONSUMO_INSTANTANEO = "car.basic.instant_fuel_consumption"
        const val CHAVE_CONSUMO_MEDIO = "car.basic.avg_fuel_consumption"
        const val CHAVE_TANQUE_PERCENTUAL = "car.basic.remain_fuel_percentage"
        const val CHAVE_AUTONOMIA_DO_CARRO = "car.basic.remain_odometer"
        const val CHAVE_MOTOR = "car.basic.engine_state"
        const val CHAVE_MODO_ENERGIA = "car.basic.power_mode"

        /**
         * O que escutamos. A lista é fechada de propósito: `car.basic.vin_code`
         * também é publicado, e o chassi é o documento do carro — não entra na
         * memória do app para não haver como ele escapar num relatório.
         */
        val CHAVES = listOf(
            CHAVE_VELOCIDADE,
            CHAVE_HODOMETRO,
            CHAVE_CONSUMO_INSTANTANEO,
            CHAVE_CONSUMO_MEDIO,
            CHAVE_TANQUE_PERCENTUAL,
            CHAVE_AUTONOMIA_DO_CARRO,
            CHAVE_MOTOR,
            CHAVE_MODO_ENERGIA,
            "car.basic.engine_speed",
            "car.basic.gear_status",
            "car.basic.driving_ready_state",
            "car.basic.cur_journey_odometer",
            "car.basic.cur_journey_avg_fuel_consume",
            "car.basic.accumulated_odometer",
            "car.basic.accumulated_drivetime",
            "car.basic.vehicle_speed_since_reset",
            "car.basic.avg_vehicle_speed_since_startup",
        )

        /**
         * As chaves que o Shisuku monitora sem ninguém pedir.
         *
         * Ele só reemite o que está na lista de monitoramento, e essa lista sai
         * de fábrica com um punhado fixo (`DEFAULT_KEYS`) mais o que o dono
         * marcar à mão em "Configurar". Distinguir as duas metades importa
         * porque as ausências têm causas diferentes: falta de chave nesta lista
         * é problema de configuração do Shisuku, enquanto uma chave desta lista
         * que não chega significa que a ponte com o carro nem está de pé.
         */
        val CHAVES_PADRAO = setOf(
            CHAVE_VELOCIDADE,
            CHAVE_HODOMETRO,
            CHAVE_CONSUMO_INSTANTANEO,
            CHAVE_MOTOR,
            "car.basic.gear_status",
            "car.basic.driving_ready_state",
            "car.basic.accumulated_drivetime",
        )

        /** As que só chegam depois de marcadas no "Configurar" do Shisuku. */
        val CHAVES_A_HABILITAR = CHAVES.filterNot { it in CHAVES_PADRAO }

        /** `true` se o HavalShisuku está instalado nesta central. */
        fun shisukuInstalado(context: Context): Boolean = runCatching {
            context.packageManager.getPackageInfo(PACOTE_SHISUKU, 0)
        }.isSuccess

        /**
         * Abre o HavalShisuku, que é onde todo problema de dado se resolve.
         *
         * Sem ele de pé — com o Shizuku autorizado e os serviços da GWM
         * conectados — não existe broadcast nenhum para escutar. Estando dentro
         * do carro, procurar o ícone na gaveta de apps custa mais que um botão.
         */
        fun abrirShisuku(context: Context) {
            runCatching {
                context.packageManager.getLaunchIntentForPackage(PACOTE_SHISUKU)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let(context::startActivity)
            }
        }

        fun pedirTudo(context: Context) {
            runCatching {
                context.sendBroadcast(Intent(ACAO_PEDIR_TUDO).setPackage(PACOTE_SHISUKU))
            }
        }
    }
}

/**
 * Como transformar o que o carro publica no que o motor de cálculo espera.
 *
 * Está tudo aqui, isolado e nomeado, porque **nenhuma destas conversões foi
 * confirmada no carro ainda**. Quando o primeiro teste disser as unidades de
 * verdade, é só este objeto que muda.
 */
object Unidades {

    /** Capacidade do tanque do H6, em litros. Usada para virar % em litros. */
    const val TANQUE_L = 61.0

    /**
     * Litros por hora, que é o que o motor de cálculo integra.
     *
     * O carro pode publicar o consumo instantâneo de duas maneiras, e as duas
     * existem no mercado. Enquanto não soubermos qual é a do H6, a conversão
     * fica escolhível na tela de diagnóstico.
     */
    fun litrosPorHora(bruto: Double?, velocidadeKmh: Double, como: Interpretacao): Double {
        if (bruto == null || bruto <= 0.0) return 0.0
        return when (como) {
            Interpretacao.LITROS_POR_HORA -> bruto
            // L/100 km × km/h ÷ 100 = L/h. Parado dá zero, e é correto: sem
            // andar não há "por quilômetro" nenhum. O gasto da marcha lenta se
            // perde nessa conversão — é a maior suspeita a confirmar no carro.
            Interpretacao.LITROS_POR_100KM -> bruto * velocidadeKmh / 100.0
        }
    }

    /**
     * Litros no tanque a partir do percentual.
     *
     * É aproximação e não tem como não ser: a bóia não é linear e o carro só
     * publica a porcentagem. Serve para a autonomia ter uma base; não serve
     * para dizer quantos litros cabem ainda no abastecimento.
     */
    fun litrosNoTanque(percentual: Double?): Double {
        if (percentual == null) return 0.0
        return (percentual.coerceIn(0.0, 100.0) / 100.0) * TANQUE_L
    }
}

/** Como ler o consumo instantâneo que o carro publica. */
enum class Interpretacao(val rotulo: String) {
    LITROS_POR_100KM("por distância"),
    LITROS_POR_HORA("por hora"),
}

/**
 * O que chegou do carro, cru, para a tela de diagnóstico e para o relatório.
 *
 * Guarda duas coisas diferentes de propósito: o **último valor de cada chave**,
 * que responde "o que o carro diz agora", e uma **fita dos últimos eventos**,
 * que responde "como esse número se comportou enquanto eu andava" — e é a fita
 * que revela a unidade de uma grandeza, não a foto parada.
 */
class DiarioDeCampo(private val limiteDaFita: Int = 400) {

    private val _atual = MutableStateFlow<Map<String, Leitura>>(emptyMap())
    val atual: StateFlow<Map<String, Leitura>> = _atual.asStateFlow()

    private val _fita = MutableStateFlow<List<Evento>>(emptyList())
    val fita: StateFlow<List<Evento>> = _fita.asStateFlow()

    private val _interpretacao = MutableStateFlow(Interpretacao.LITROS_POR_100KM)
    val interpretacao: StateFlow<Interpretacao> = _interpretacao.asStateFlow()

    fun interpretarComo(valor: Interpretacao) { _interpretacao.value = valor }

    fun registrar(chave: String, valor: String) {
        val agora = System.currentTimeMillis()
        val anterior = _atual.value[chave]
        _atual.value = _atual.value + (chave to Leitura(
            valor = valor,
            emMs = agora,
            vezes = (anterior?.vezes ?: 0) + 1,
        ))
        // A fita tem teto: uma viagem de uma hora renderia milhares de eventos
        // e o relatório viraria grande demais para subir de dentro do carro.
        _fita.value = (_fita.value + Evento(agora, chave, valor)).takeLast(limiteDaFita)
    }

    data class Leitura(val valor: String, val emMs: Long, val vezes: Int)
    data class Evento(val emMs: Long, val chave: String, val valor: String)
}
