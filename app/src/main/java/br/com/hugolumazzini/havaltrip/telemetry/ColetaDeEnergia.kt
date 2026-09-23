package br.com.hugolumazzini.havaltrip.telemetry

import android.content.Context
import android.util.Log
import br.com.hugolumazzini.havaltrip.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mede quanta energia elétrica uma viagem gastou, num carro que tem tomada.
 *
 * ## Por que não basta olhar a bateria
 *
 * O jeito óbvio — ver quanto o SOC caiu e multiplicar pela capacidade do pacote
 * — é o pior de todos. O SOC anda em degraus de 1%, cada degrau vale centenas
 * de watt-hora, e numa viagem curta o número vira ruído. Pior: ele não enxerga a
 * energia que a frenagem regenerativa devolve no meio do caminho.
 *
 * O que esta coleta faz é o mesmo que o app já faz com combustível: integrar uma
 * taxa. Tensão vezes corrente é potência; potência vezes tempo é energia. E a
 * corrente vem **negativa** quando a bateria está sendo carregada, então a
 * regeneração se desconta sozinha, sem regra especial nenhuma.
 *
 * ## Por que 4 Hz
 *
 * Num PHEV a potência muda em menos de um segundo: uma arrancada vai de 5 kW a
 * 100 kW num piscar, e uma frenagem regenerativa inteira dura três. Lendo uma
 * vez por segundo, a conta supõe que a potência ficou parada durante aquele
 * segundo, e o erro não é aleatório — ele corta os dois extremos, subestimando
 * tanto o gasto da arrancada quanto o ganho da frenagem. Quatro vezes por
 * segundo é o que basta para acompanhar transientes dessa ordem sem encher a
 * central de trabalho.
 *
 * ## Por que grava em arquivo, e não em memória
 *
 * Quem vai rodar isto é outra pessoa, no carro dela, e a viagem pode durar
 * horas. Se a central reiniciar ou a energia cair no fim do trajeto, uma coleta
 * guardada em memória se perde inteira — e não dá para pedir a alguém que
 * refaça a viagem. Cada amostra vai para o disco na hora, e a conta final é
 * feita lendo o arquivo de volta.
 *
 * ## Só na linha direta
 *
 * Nenhuma destas propriedades está na lista que o HavalShisuku monitora de
 * fábrica, e pedir para um usuário marcar vinte caixinhas num menu avançado é
 * receita para dado faltando. A linha direta ([ShizukuTelemetrySource]) pergunta
 * qualquer propriedade sem configurar nada.
 */
class ColetaDeEnergia(
    private val context: Context,
    private val escopo: CoroutineScope,
) {

    sealed interface Estado {
        /** Desligada: é o normal, e é o que todo dono de HEV vai ver para sempre. */
        data object Desligada : Estado

        /** Ligada, esperando a ignição para começar sozinha. */
        data object Esperando : Estado

        /** Gravando agora. */
        data class Gravando(val amostras: Int, val kwh: Double, val km: Double) : Estado

        /** Viagem fechada, relatório pronto para enviar. */
        data class Pronta(val resumo: Resumo, val arquivo: String) : Estado

        /** A linha direta não está de pé; sem ela não há o que ler. */
        data class SemLinha(val motivo: String) : Estado
    }

    /** As contas de uma coleta fechada, as três lado a lado. */
    data class Resumo(
        val amostras: Int,
        val duracaoS: Double,
        val km: Double,
        /** A integral de tensão × corrente: a medida boa. */
        val kwhIntegrado: Double,
        /** Só o que saiu da bateria, sem descontar a regeneração. */
        val kwhGasto: Double,
        /** Só o que a frenagem devolveu. */
        val kwhRecuperado: Double,
        val socInicial: Double?,
        val socFinal: Double?,
        val potenciaMaximaKw: Double,
        val potenciaMinimaKw: Double,
        /** O que o próprio carro diz que consumiu, para comparar. */
        val consumoDoCarro: String?,
    )

    private val _estado = MutableStateFlow<Estado>(Estado.Desligada)
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ligada por quem está fazendo a medição, e lembrada entre aberturas. */
    var ligada: Boolean = prefs.getBoolean(LIGADA, false)
        private set

    private var laco: Job? = null
    private var arquivo: File? = null

    init {
        if (ligada) _estado.value = Estado.Esperando
    }

    fun ligar(valor: Boolean) {
        ligada = valor
        prefs.edit().putBoolean(LIGADA, valor).apply()
        if (!valor) {
            terminar()
            _estado.value = Estado.Desligada
        } else if (_estado.value is Estado.Desligada) {
            _estado.value = Estado.Esperando
        }
    }

    /** Chamado quando a ignição liga. Não faz nada se a coleta estiver desligada. */
    fun comecar() {
        if (!ligada || laco != null) return

        // Se estiver no simulador, simula dados em vez de ler do Shizuku
        val servico = ShizukuTelemetrySource.servicoDoCarro()
        if (servico == null) {
            if (BuildConfig.DEBUG) {
                // No debug simula uma coleta para teste no emulador
                simularColeta()
                return
            } else {
                _estado.value = Estado.SemLinha("o Shizuku não está rodando ou não autorizou o app")
                return
            }
        }
        val destino = File(pasta(), "energia-${carimbo()}.csv")
        runCatching { destino.writeText(CABECALHO + "\n") }
            .onFailure {
                _estado.value = Estado.SemLinha("não deu para criar o arquivo: ${it.message}")
                return
            }
        arquivo = destino
        laco = escopo.launch(Dispatchers.IO) {
            var amostras = 0
            var kwh = 0.0
            var kmInicial: Double? = null
            var km = 0.0
            var anteriorMs = System.currentTimeMillis()
            while (isActive) {
                delay(INTERVALO_MS)
                val agora = System.currentTimeMillis()
                val deltaS = (agora - anteriorMs) / 1000.0
                anteriorMs = agora
                val valores = runCatching { ler(servico) }.getOrNull() ?: continue

                val potenciaKw = potenciaKw(valores)
                if (potenciaKw != null) kwh += potenciaKw * deltaS / 3600.0
                numero(valores[CHAVE_HODOMETRO])?.let { atual ->
                    val partida = kmInicial ?: atual.also { kmInicial = it }
                    km = atual - partida
                }

                amostras++
                runCatching {
                    destino.appendText(linha(agora, deltaS, potenciaKw, valores) + "\n")
                }.onFailure { Log.w(TAG, "não deu para gravar a amostra", it) }

                _estado.value = Estado.Gravando(amostras, kwh, km)
            }
        }
    }

    /** Chamado quando a ignição desliga: fecha a coleta e faz as contas. */
    fun terminar() {
        laco?.cancel()
        laco = null
        val gravado = arquivo ?: return
        arquivo = null
        escopo.launch(Dispatchers.IO) {
            val resumo = runCatching { contar(gravado) }.getOrNull()
            _estado.value = when {
                resumo == null || resumo.amostras == 0 -> if (ligada) Estado.Esperando else Estado.Desligada
                else -> Estado.Pronta(resumo, gravado.absolutePath)
            }
        }
    }

    /**
     * O relatório para o link público.
     *
     * Leva as contas inteiras e a fita **rareada**: a integração acontece aqui
     * dentro, a 4 Hz, e mandar as quatro amostras de cada segundo só serviria
     * para estourar o tamanho do envio. Uma por segundo basta para conferir o
     * formato de cada valor e enxergar o perfil da viagem.
     */
    fun relato(): String {
        val pronta = _estado.value as? Estado.Pronta ?: return "Nenhuma coleta fechada."
        val r = pronta.resumo
        return buildString {
            appendLine("=== HAVAL TRIP — coleta de energia (PHEV) ===")
            appendLine("Arquivo: ${File(pronta.arquivo).name}")
            appendLine()
            appendLine("--- A VIAGEM ---")
            appendLine("amostras: ${r.amostras} (a ${1000 / INTERVALO_MS} por segundo)")
            appendLine("duração: ${"%.1f".format(r.duracaoS / 60)} min")
            appendLine("distância: ${"%.2f".format(r.km)} km")
            appendLine()
            appendLine("--- ENERGIA, PELAS TRÊS CONTAS ---")
            appendLine("integrada (tensão × corrente): ${"%.3f".format(r.kwhIntegrado)} kWh")
            appendLine("  saiu da bateria: ${"%.3f".format(r.kwhGasto)} kWh")
            appendLine("  voltou pela regeneração: ${"%.3f".format(r.kwhRecuperado)} kWh")
            appendLine("SOC: ${r.socInicial ?: "—"}% → ${r.socFinal ?: "—"}%")
            appendLine("o que o carro diz: ${r.consumoDoCarro ?: "(não respondeu)"}")
            if (r.km > 0.01 && r.kwhIntegrado > 0.0) {
                appendLine("rendimento: ${"%.2f".format(r.km / r.kwhIntegrado)} km/kWh " +
                    "(${"%.1f".format(r.kwhIntegrado * 100 / r.km)} kWh/100 km)")
            }
            appendLine("potência: de ${"%.1f".format(r.potenciaMinimaKw)} kW " +
                "a ${"%.1f".format(r.potenciaMaximaKw)} kW")
            appendLine()
            appendLine("--- FITA (uma amostra por segundo) ---")
            appendLine(CABECALHO)
            runCatching {
                File(pronta.arquivo).useLines { linhas ->
                    linhas.drop(1)
                        .filterIndexed { i, _ -> i % RAREACAO == 0 }
                        .take(MAX_LINHAS_ENVIADAS)
                        .forEach { appendLine(it) }
                }
            }.onFailure { appendLine("(não deu para reler o arquivo: ${it.message})") }
        }
    }

    // ------------------------------------------------------------------ internos

    private fun ler(servico: com.beantechs.intelligentvehiclecontrol.IIntelligentVehicleControlService):
        Map<String, String> {
        val vetor = CHAVES.toTypedArray()
        val resposta = mutableMapOf<String, String>()
        servico.fetchDatas(vetor).forEachIndexed { i, valor ->
            if (valor != null) resposta[vetor[i]] = valor
        }
        return resposta
    }

    /**
     * A potência elétrica neste instante, em kW.
     *
     * Tensão × corrente é o caminho bom, porque é medida e não estimativa. Se
     * alguma das duas não responder neste carro, `motor_power` entra como
     * reserva — vale menos, porque não se sabe se ele já vem em kW nem se
     * enxerga a regeneração.
     */
    private fun potenciaKw(valores: Map<String, String>): Double? {
        val volts = numero(valores[CHAVE_TENSAO])
        val amperes = numero(valores[CHAVE_CORRENTE])
        if (volts != null && amperes != null) return volts * amperes / 1000.0
        return numero(valores[CHAVE_POTENCIA_MOTOR])
    }

    private fun linha(
        agora: Long,
        deltaS: Double,
        potenciaKw: Double?,
        valores: Map<String, String>,
    ): String = buildString {
        append(agora)
        append(';')
        append("%.3f".format(deltaS))
        append(';')
        append(potenciaKw?.let { "%.4f".format(it) } ?: "")
        CHAVES.forEach { chave ->
            append(';')
            // O `;` é separador; nenhum valor do carro traz um, mas um dia pode.
            append(valores[chave]?.replace(';', ',') ?: "")
        }
    }

    /** Relê o arquivo e fecha as contas. Streaming: uma viagem longa não cabe na memória. */
    private fun contar(arquivo: File): Resumo {
        var amostras = 0
        var duracaoS = 0.0
        var kwh = 0.0
        var gasto = 0.0
        var recuperado = 0.0
        var maxKw = Double.NEGATIVE_INFINITY
        var minKw = Double.POSITIVE_INFINITY
        var kmInicial: Double? = null
        var kmFinal: Double? = null
        var socInicial: Double? = null
        var socFinal: Double? = null
        var consumoDoCarro: String? = null

        val indiceHodometro = COLUNAS_FIXAS + CHAVES.indexOf(CHAVE_HODOMETRO)
        val indiceSoc = COLUNAS_FIXAS + CHAVES.indexOf(CHAVE_SOC)
        val indiceConsumo = COLUNAS_FIXAS + CHAVES.indexOf(CHAVE_CONSUMO_DE_ENERGIA)

        arquivo.useLines { linhas ->
            linhas.drop(1).forEach { linha ->
                val campos = linha.split(';')
                if (campos.size <= indiceConsumo) return@forEach
                amostras++
                val deltaS = campos.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                duracaoS += deltaS
                campos.getOrNull(2)?.toDoubleOrNull()?.let { kw ->
                    val parcela = kw * deltaS / 3600.0
                    kwh += parcela
                    if (parcela >= 0) gasto += parcela else recuperado -= parcela
                    if (kw > maxKw) maxKw = kw
                    if (kw < minKw) minKw = kw
                }
                numero(campos.getOrNull(indiceHodometro))?.let {
                    if (kmInicial == null) kmInicial = it
                    kmFinal = it
                }
                numero(campos.getOrNull(indiceSoc))?.let {
                    if (socInicial == null) socInicial = it
                    socFinal = it
                }
                campos.getOrNull(indiceConsumo)?.takeIf { it.isNotBlank() }
                    ?.let { consumoDoCarro = it }
            }
        }

        return Resumo(
            amostras = amostras,
            duracaoS = duracaoS,
            km = (kmFinal ?: 0.0) - (kmInicial ?: 0.0),
            kwhIntegrado = kwh,
            kwhGasto = gasto,
            kwhRecuperado = recuperado,
            socInicial = socInicial,
            socFinal = socFinal,
            potenciaMaximaKw = if (maxKw.isFinite()) maxKw else 0.0,
            potenciaMinimaKw = if (minKw.isFinite()) minKw else 0.0,
            consumoDoCarro = consumoDoCarro,
        )
    }

    private fun pasta(): File = File(context.filesDir, "energia").apply { mkdirs() }

    private fun carimbo(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    /**
     * Lê um número que pode vir sozinho ou dentro de chaves.
     *
     * Várias propriedades do H6 chegam como `{4.0,0.0}` — um vetor de valores
     * relacionados. Aqui interessa o primeiro; o valor cru inteiro fica gravado
     * na fita para conferência.
     */
    private fun numero(valor: String?): Double? =
        valor?.trim()?.removePrefix("{")?.removeSuffix("}")
            ?.substringBefore(',')?.trim()?.toDoubleOrNull()

    companion object {
        private const val TAG = "ColetaDeEnergia"
        private const val PREFS = "coleta"
        private const val LIGADA = "ligada"

        /** Quatro leituras por segundo. Ver o cabeçalho da classe. */
        const val INTERVALO_MS = 250L

        /** Uma linha por segundo no relatório, das quatro gravadas. */
        private const val RAREACAO = 4

        /** Teto de linhas no envio: acima disso o site de texto recusa. */
        private const val MAX_LINHAS_ENVIADAS = 6_000

        /** As colunas antes das propriedades: instante, Δt e potência. */
        private const val COLUNAS_FIXAS = 3

        const val CHAVE_TENSAO = "car.ev_info.power_battery_voltage"
        const val CHAVE_CORRENTE = "car.ev_info.power_battery_current"
        const val CHAVE_POTENCIA_MOTOR = "car.ev_info.motor_power"
        const val CHAVE_SOC = "car.ev_info.soc_of_battery"
        const val CHAVE_HODOMETRO = "car.basic.total_odometer"
        const val CHAVE_CONSUMO_DE_ENERGIA = "car.ev_info.energy_consume_info"

        /**
         * O que a coleta lê, quatro vezes por segundo.
         *
         * A ordem importa: ela é a ordem das colunas do arquivo, e mudar a lista
         * invalida a leitura de uma coleta antiga.
         */
        val CHAVES = listOf(
            // A medida boa, e o que ela precisa para virar energia.
            CHAVE_TENSAO,
            CHAVE_CORRENTE,
            CHAVE_POTENCIA_MOTOR,
            "car.ev_info.motor_speed",
            "car.ev_info.rear_motor_speed",
            // A bateria, para a terceira conta e para saber se ela bate.
            CHAVE_SOC,
            "car.ev_info.battery_charge_percentage",
            "car.ev_info.cur_battery_power_percentage",
            "car.ev_info.attenuation_of_battery",
            // O que o próprio carro calcula, para comparar com a nossa integral.
            CHAVE_CONSUMO_DE_ENERGIA,
            "car.ev_info.cycle_energy_consume_info",
            "car.ev_info.avg_energy_consume_info_since_reset",
            "car.ev_info.energy_recovery_info",
            "car.ev_info.energy_output_percentage",
            // Qual motor está tocando o carro, e se ele está na tomada.
            "car.ev_info.hcu_power_train_state",
            "car.ev_info.energy_drive_state",
            "car.ev_info.charging_state",
            "car.ev_info.charging_gun_conn_state",
            // O combustível, porque num PHEV as duas contas convivem.
            "car.basic.instant_fuel_consumption",
            "car.ev_info.fuel_consume_info",
            // A régua de tudo.
            CHAVE_HODOMETRO,
            "car.basic.vehicle_speed",
            "car.basic.driving_ready_state",
        )

        val CABECALHO = (listOf("instante_ms", "delta_s", "potencia_kw") + CHAVES)
            .joinToString(";")
    }

    /**
     * Simula uma coleta de energia para teste no emulador.
     * Gera dados fictícios de uma viagem com ~40 km e ~3 kWh consumidos.
     */
    private fun simularColeta() {
        val destino = File(pasta(), "energia-simulado-${carimbo()}.csv")

        laco = escopo.launch(Dispatchers.IO) {
            runCatching {
                val sb = StringBuilder(CABECALHO + "\n")

                // Gera 1200 amostras = 5 minutos de coleta (250ms * 1200 = 300s)
                val numeroAmostras = 1200
                var kwh = 0.0
                var km = 0.0

                val agora = System.currentTimeMillis()
                for (i in 0 until numeroAmostras) {
                    if (!isActive) break

                    val deltaS = INTERVALO_MS / 1000.0
                    val progresso = i.toDouble() / numeroAmostras

                    // Simula potência: começa em 50 kW, varia, termina em 30 kW
                    val variacaoSeno = kotlin.math.sin(progresso * 2 * kotlin.math.PI) * 20
                    val potenciaKw = 50 + variacaoSeno - (progresso * 20)

                    kwh += potenciaKw * deltaS / 3600.0
                    km = progresso * 40.0

                    sb.append("${agora + (i * INTERVALO_MS)};$deltaS;$potenciaKw\n")

                    _estado.value = Estado.Gravando(i + 1, kwh, km)
                }

                destino.writeText(sb.toString())
                arquivo = destino
            }.onFailure {
                _estado.value = Estado.SemLinha("simulação falhou: ${it.message}")
                arquivo = null
            }
        }
    }
}
