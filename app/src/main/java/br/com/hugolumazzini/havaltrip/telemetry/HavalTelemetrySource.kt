package br.com.hugolumazzini.havaltrip.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
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
    private val estado: EstadoDoCarro,
    private val intervaloMs: Long = 1000L,
) : TelemetrySource {

    override fun samples(): Flow<TelemetrySample> = callbackFlow {
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val acao = intent?.action ?: return
                val chave = acao.removePrefix(PREFIXO)
                // O Shisuku emite dois broadcasts por mudança: um com o valor
                // em extra e outro com o valor grudado no nome da ação. Só o
                // primeiro interessa; o segundo chegaria com chave inventada.
                val valor = intent.getStringExtra("value") ?: return
                estado.registrar(chave, valor)
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
                estado.publicarFita()
                trySend(estado.montarAmostra())
            }
        }

        awaitClose { runCatching { context.unregisterReceiver(receptor) } }
    }

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
         * A metade híbrida do H6, que `car.basic.*` sozinho não enxerga.
         *
         * Num HEV o carro alterna entre motor elétrico e a combustão, e as
         * grandezas que descrevem essa troca vivem noutro domínio. A mais
         * importante é a autonomia: `car.basic.remain_odometer` chega zerada
         * porque num híbrido "quanto ainda dá para andar" se parte em duas.
         */
        const val CHAVE_AUTONOMIA_COMBUSTIVEL = "car.ev_info.fuel_mode_remain_odometer"
        const val CHAVE_AUTONOMIA_ELETRICA = "car.ev_info.electric_mode_remain_odometer"
        const val CHAVE_BATERIA = "car.ev_info.cur_battery_power_percentage"

        /** Positivo puxa da bateria; negativo é frenagem regenerativa. */
        const val CHAVE_FLUXO_DE_ENERGIA = "car.ev_info.energy_output_percentage"

        /** Qual motor está tocando o carro neste instante. */
        const val CHAVE_TREM_DE_FORCA = "car.ev_info.hcu_power_train_state"

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
            CHAVE_AUTONOMIA_COMBUSTIVEL,
            CHAVE_AUTONOMIA_ELETRICA,
            CHAVE_BATERIA,
            CHAVE_FLUXO_DE_ENERGIA,
            CHAVE_TREM_DE_FORCA,
            "car.ev_info.fuel_consume_info",
            "car.ev_info.cycle_fuel_consume_info",
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
            // Estas o Impulse já monitora de fábrica (o `DEFAULT_KEYS` dele),
            // então chegam pela ponte sem ninguém marcar caixinha nenhuma.
            CHAVE_BATERIA,
            CHAVE_FLUXO_DE_ENERGIA,
            "car.ev_info.fuel_consume_info",
            "car.ev_info.cycle_fuel_consume_info",
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
 * O que chegou do carro, cru, para a tela de diagnóstico e para o relatório.
 *
 * Guarda duas coisas diferentes de propósito: o **último valor de cada chave**,
 * que responde "o que o carro diz agora", e uma **fita dos últimos eventos**,
 * que responde "como esse número se comportou enquanto eu andava" — e é a fita
 * que revela a unidade de uma grandeza, não a foto parada.
 */
class DiarioDeCampo(private val limiteDaFita: Int = LIMITE_PADRAO_DA_FITA) {

    private val _atual = MutableStateFlow<Map<String, Leitura>>(emptyMap())
    val atual: StateFlow<Map<String, Leitura>> = _atual.asStateFlow()

    /**
     * A fita viva, onde os eventos entram um a um.
     *
     * É uma fila e não uma lista imutável por causa do volume: o carro publica
     * ~26 eventos por segundo, e recopiar dez mil elementos a cada um deles
     * daria um quarto de milhão de cópias por segundo na central — que é um
     * aparelho modesto. Aqui entra e sai pelas pontas, sem copiar nada.
     *
     * Fica sob trava porque quem escreve é o callback do carro, numa thread do
     * Binder, e quem lê é a interface.
     */
    private val fitaViva = ArrayDeque<Evento>()

    private val _fita = MutableStateFlow<List<Evento>>(emptyList())

    /** A fita como a tela e o relatório a enxergam: uma cópia estável. */
    val fita: StateFlow<List<Evento>> = _fita.asStateFlow()

    private var ultimaPublicacaoMs = 0L

    fun registrar(chave: String, valor: String) {
        val agora = System.currentTimeMillis()
        val anterior = _atual.value[chave]
        _atual.value = _atual.value + (chave to Leitura(
            valor = valor,
            emMs = agora,
            vezes = (anterior?.vezes ?: 0) + 1,
        ))

        val publicar = synchronized(fitaViva) {
            fitaViva.addLast(Evento(agora, chave, valor))
            // A fita tem teto porque a memória tem: sem ele, uma viagem longa
            // encheria o app.
            while (fitaViva.size > limiteDaFita) fitaViva.removeFirst()
            // A cópia para a tela custa caro e ninguém lê dez mil linhas por
            // segundo. Publicar de tempos em tempos mantém o diagnóstico vivo
            // aos olhos sem transformar a rolagem numa fábrica de lixo.
            (agora - ultimaPublicacaoMs >= INTERVALO_DE_PUBLICACAO_MS).also {
                if (it) ultimaPublicacaoMs = agora
            }
        }
        if (publicar) publicarFita()
    }

    /**
     * Força a fita publicada a alcançar a viva.
     *
     * O relatório chama isto antes de montar: sem ele, os últimos instantes
     * antes do toque no botão ficariam de fora por até meio segundo — e é
     * justamente o fim da coleta que costuma interessar.
     */
    fun publicarFita() {
        _fita.value = synchronized(fitaViva) { fitaViva.toList() }
    }

    data class Leitura(val valor: String, val emMs: Long, val vezes: Int)
    data class Evento(val emMs: Long, val chave: String, val valor: String)

    companion object {
        /**
         * Teto da fita, em eventos. A ~26 eventos por segundo, 10 mil dão uns
         * seis minutos de histórico — o bastante para pegar um trecho inteiro
         * de trânsito, com paradas e arrancadas, e não só o instante do envio.
         *
         * Quem recorta para caber no envio é o [Relatorio]; aqui o critério é
         * só quanto vale a pena manter em memória.
         */
        const val LIMITE_PADRAO_DA_FITA = 10_000

        /** De quanto em quanto tempo a fita da tela alcança a fita viva. */
        private const val INTERVALO_DE_PUBLICACAO_MS = 500L
    }
}
