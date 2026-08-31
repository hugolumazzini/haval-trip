package br.com.hugolumazzini.havaltrip.telemetry

import android.content.Context
import android.os.Build
import android.util.Base64
import br.com.hugolumazzini.havaltrip.engine.TripState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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

    /** Nome do arquivo no repositório: ordena sozinho por data. */
    private val arquivo = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US)

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

    /** Repositório privado, do próprio Hugo, onde os relatórios são commitados. */
    const val REPOSITORIO = "hugolumazzini/haval-trip-relatorios"

    /**
     * Faz commit do relatório no repositório privado, pela API do GitHub.
     *
     * É o caminho principal por ser o único em que o arquivo chega fechado: o
     * repositório é privado, então o hábito de direção não fica exposto num
     * link que qualquer um abre. Em troca, exige o token configurado no
     * [Cofre] — sem ele não há como escrever.
     */
    suspend fun enviarPorGit(token: String, texto: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val nome = "relatorios/${arquivo.format(Date())}.txt"
                val corpo = JSONObject()
                    .put("message", "Coleta de ${carimbo.format(Date())}")
                    .put("content", Base64.encodeToString(texto.toByteArray(), Base64.NO_WRAP))
                    .toString()

                val resposta = postar(
                    endereco = "https://api.github.com/repos/$REPOSITORIO/contents/$nome",
                    corpo = corpo,
                    tipo = "application/json",
                    metodo = "PUT",
                    // O token vai no cabeçalho, nunca na URL: endereço entra em
                    // log de servidor, cabeçalho de autorização não.
                    autorizacao = "Bearer $token",
                )
                JSONObject(resposta).getJSONObject("content").getString("html_url")
            }
        }

    /**
     * Sobe para um site público de texto e devolve o endereço para copiar.
     *
     * Reserva para quando o GitHub não responde ou o token não está
     * configurado, e o teste já foi feito: se o primeiro
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

    private fun postar(
        endereco: String,
        corpo: String,
        tipo: String,
        metodo: String = "POST",
        autorizacao: String? = null,
    ): String {
        val conexao = (URL(endereco).openConnection() as HttpURLConnection).apply {
            requestMethod = metodo
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", tipo)
            setRequestProperty("User-Agent", "HavalTrip/1.0")
            autorizacao?.let { setRequestProperty("Authorization", it) }
        }
        conexao.outputStream.use { it.write(corpo.toByteArray()) }
        val codigo = conexao.responseCode
        if (codigo !in 200..299) {
            val erro = conexao.errorStream?.bufferedReader()?.readText().orEmpty()
            // A mensagem é lida dentro do carro, por quem quer terminar o teste
            // e ir embora. "Bad credentials" em JSON não diz o que fazer;
            // "o token foi recusado" diz.
            throw IllegalStateException(
                when (codigo) {
                    401 -> "O token foi recusado. Confira se copiou ele inteiro."
                    403 -> "O token não tem permissão de escrita neste repositório."
                    404 -> "Repositório não encontrado — ou o token não enxerga ele."
                    else -> "Erro $codigo do servidor — ${erro.take(90)}"
                }
            )
        }
        return conexao.inputStream.bufferedReader().readText().trim()
    }
}
