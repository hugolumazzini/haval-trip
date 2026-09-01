package br.com.hugolumazzini.havaltrip.telemetry

import android.content.Context
import android.os.Build
import br.com.hugolumazzini.havaltrip.engine.TripState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * O relatório do teste no carro: o que o H6 publicou, do jeito que publicou.
 *
 * Existe para uma coisa só — descobrir as unidades e os códigos que o carro usa
 * sem precisar estar dentro dele. Por isso guarda o valor **cru**, em texto,
 * sem conversão nenhuma: converter antes de saber a unidade seria justamente
 * apagar a resposta que se está procurando.
 */
object Relatorio {

    private val hora = SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("pt-BR"))
    private val carimbo = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR"))

    fun montar(diario: DiarioDeCampo, estado: TripState, fonteReal: Boolean, shisuku: Boolean): String {
        val sb = StringBuilder()
        sb.appendLine("=== HAVAL TRIP — diagnóstico de telemetria ===")
        sb.appendLine("Gerado em: ${carimbo.format(Date())}")
        sb.appendLine("Central: ${Build.MANUFACTURER} ${Build.MODEL} — Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("HavalShisuku instalado: ${if (shisuku) "sim" else "NÃO"}")
        sb.appendLine("Fonte em uso: ${if (fonteReal) "carro (broadcasts)" else "simulador"}")
        sb.appendLine("Consumo interpretado como: ${diario.interpretacao.value.rotulo}")
        sb.appendLine()

        sb.appendLine("--- VALOR ATUAL DE CADA CHAVE ---")
        val atual = diario.atual.value
        if (atual.isEmpty()) {
            sb.appendLine("(nenhum broadcast recebido — ver se o HavalShisuku está rodando)")
        } else {
            atual.toSortedMap().forEach { (chave, leitura) ->
                sb.appendLine("$chave = ${leitura.valor}   [${leitura.vezes}x, última ${hora.format(Date(leitura.emMs))}]")
            }
        }
        sb.appendLine()

        // Sem isto, uma chave ausente no relatório é ambígua: pode ser o carro
        // que não publica, ou o Shisuku que não monitora. A distinção decide se
        // o próximo passo é código nosso ou uma caixinha marcada lá.
        sb.appendLine("--- AINDA NÃO CHEGARAM ---")
        val faltando = HavalTelemetrySource.CHAVES.filterNot { it in atual }
        if (faltando.isEmpty()) {
            sb.appendLine("(nenhuma — chegou tudo que o app escuta)")
        } else {
            faltando.forEach {
                val padrao = it in HavalTelemetrySource.CHAVES_PADRAO
                sb.appendLine("$it   [${if (padrao) "padrão do Shisuku" else "precisa marcar em Configurar"}]")
            }
        }
        sb.appendLine()

        sb.appendLine("--- O QUE O APP ENTENDEU ---")
        val live = estado.live
        sb.appendLine("velocidade      = ${live.speedKmh} km/h")
        sb.appendLine("hodômetro       = ${live.odometerTotalKm} km")
        sb.appendLine("tanque          = ${live.fuelLevelL} L (convertido do percentual)")
        sb.appendLine("ignição         = ${live.ignition}")
        sb.appendLine("consumo agora   = ${live.instantFuelConsumptionKml} km/L")
        sb.appendLine("autonomia (app) = ${live.autonomyDteKm} km")
        sb.appendLine("autonomia (carro, cru) = ${atual[HavalTelemetrySource.CHAVE_AUTONOMIA_DO_CARRO]?.valor}")
        estado.trips.forEach {
            sb.appendLine("trip ${it.id} (${it.label}): ${it.metrics.distanceKm} km, ${it.metrics.fuelLitres} L, ${it.status}")
        }
        sb.appendLine()

        sb.appendLine("--- FITA DOS ÚLTIMOS EVENTOS (hora | chave | valor cru) ---")
        sb.appendLine("(é aqui que a unidade aparece: como o número se move enquanto o carro anda)")
        diario.fita.value.forEach {
            sb.appendLine("${hora.format(Date(it.emMs))} | ${it.chave} | ${it.valor}")
        }
        return sb.toString()
    }

    /** Grava no armazenamento do app. Sempre acontece, mesmo sem internet. */
    fun salvar(context: Context, texto: String): File {
        val pasta = File(context.filesDir, "diagnostico").apply { mkdirs() }
        val arquivo = File(pasta, "haval-trip-${System.currentTimeMillis()}.txt")
        arquivo.writeText(texto)
        return arquivo
    }

    /**
     * Sobe para um site público de texto e devolve o endereço para copiar.
     *
     * Dois serviços porque o teste acontece dentro do carro: se o primeiro
     * estiver fora do ar, não dá para voltar para casa, corrigir e sair de novo.
     * Nenhum dos dois pede cadastro — e nada de identificável vai no texto: o
     * chassi nunca é lido ([HavalTelemetrySource.CHAVES]).
     */
    suspend fun enviar(texto: String): Result<String> = withContext(Dispatchers.IO) {
        val tentativas = listOf(::viaDpaste, ::viaPasteRs)
        var ultimoErro: Throwable? = null
        for (tentativa in tentativas) {
            runCatching { tentativa(texto) }
                .onSuccess { return@withContext Result.success(it) }
                .onFailure { ultimoErro = it }
        }
        Result.failure(ultimoErro ?: IllegalStateException("Falha desconhecida no envio"))
    }

    private fun viaDpaste(texto: String): String {
        val corpo = "content=${URLEncoder.encode(texto, "UTF-8")}" +
            "&syntax=text&expiry_days=30&title=${URLEncoder.encode("Haval Trip", "UTF-8")}"
        return postar("https://dpaste.com/api/v2/", corpo, "application/x-www-form-urlencoded")
    }

    private fun viaPasteRs(texto: String): String =
        postar("https://paste.rs/", texto, "text/plain")

    private fun postar(endereco: String, corpo: String, tipo: String): String {
        val conexao = (URL(endereco).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", tipo)
            setRequestProperty("User-Agent", "HavalTrip/1.0")
        }
        conexao.outputStream.use { it.write(corpo.toByteArray()) }
        val codigo = conexao.responseCode
        if (codigo !in 200..299) {
            val erro = conexao.errorStream?.bufferedReader()?.readText().orEmpty()
            // A mensagem é lida dentro do carro, por quem quer terminar o teste
            // e ir embora: precisa dizer o que fazer, não devolver o corpo do
            // erro do servidor.
            throw IllegalStateException(
                when (codigo) {
                    in 500..599 -> "O site de envio está fora do ar. Tente de novo."
                    else -> "Erro $codigo do servidor — ${erro.take(90)}"
                }
            )
        }
        return conexao.inputStream.bufferedReader().readText().trim()
    }
}
