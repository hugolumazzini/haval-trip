package br.com.hugolumazzini.havaltrip.telemetry

import android.content.Context
import android.os.Build
import br.com.hugolumazzini.havaltrip.Fonte
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

    /**
     * Quantos eventos da fita cabem no relatório que sobe para a internet.
     *
     * A ~55 bytes por linha, 6 mil eventos dão uns 330 KB — abaixo do limite
     * dos dois serviços de paste, com folga para o resto do relatório. A cópia
     * gravada no aparelho não usa este teto: lá cabe a fita inteira.
     */
    const val MAX_EVENTOS_ENVIADOS = 6_000

    fun montar(
        diario: DiarioDeCampo,
        estado: TripState,
        fonte: Fonte,
        shisuku: Boolean,
        maxEventos: Int = Int.MAX_VALUE,
    ): String {
        // Sem isto, os últimos instantes antes do toque no botão ficariam de
        // fora: a fita da tela só alcança a viva de meio em meio segundo.
        diario.publicarFita()

        val sb = StringBuilder()
        sb.appendLine("=== HAVAL TRIP — diagnóstico de telemetria ===")
        sb.appendLine("Gerado em: ${carimbo.format(Date())}")
        sb.appendLine("Central: ${Build.MANUFACTURER} ${Build.MODEL} — Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("HavalShisuku instalado: ${if (shisuku) "sim" else "NÃO"}")
        sb.appendLine("Fonte em uso: ${fonte.rotulo}")
        sb.appendLine("Shizuku: ${if (ShizukuTelemetrySource.disponivel()) "rodando" else "ausente"}, autorizado: ${if (ShizukuTelemetrySource.autorizado()) "sim" else "NÃO"}")
        sb.appendLine()

        sb.appendLine("--- VALOR ATUAL DE CADA CHAVE ---")
        val atual = diario.atual.value
        if (atual.isEmpty()) {
            sb.appendLine("(nada recebido — a ponte com o carro não chegou a subir)")
        } else {
            atual.toSortedMap().forEach { (chave, leitura) ->
                sb.appendLine("$chave = ${leitura.valor}   [${leitura.vezes}x, última ${hora.format(Date(leitura.emMs))}]")
            }
        }
        sb.appendLine()

        // Sem isto, uma chave ausente é ambígua. Pela linha direta a lista de
        // monitoramento é nossa, então ausência significa que o carro não
        // publica aquilo — resposta de verdade sobre o H6. Pela ponte do
        // Shisuku pode ser só caixinha desmarcada, que não diz nada do carro.
        sb.appendLine("--- AINDA NÃO CHEGARAM ---")
        val faltando = HavalTelemetrySource.CHAVES.filterNot { it in atual }
        if (faltando.isEmpty()) {
            sb.appendLine("(nenhuma — chegou tudo que o app pede)")
        } else if (fonte == Fonte.SHIZUKU) {
            sb.appendLine("(a lista pedida é nossa, então ausência aqui é o carro que não publica)")
            faltando.forEach { sb.appendLine(it) }
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
        // O par cru vem logo abaixo porque num híbrido "zero" é resposta
        // legítima — rodando em elétrico não se queima nada — e sem a unidade
        // ao lado não dá para distinguir isso de leitura que falhou.
        val consumo = Unidades.lerConsumoInstantaneo(
            atual[HavalTelemetrySource.CHAVE_CONSUMO_INSTANTANEO]?.valor
        )
        sb.appendLine(
            "consumo (cru)   = " + when (consumo?.unidade) {
                Unidades.ConsumoInstantaneo.POR_DISTANCIA -> "${consumo.valor} L/100km (andando)"
                Unidades.ConsumoInstantaneo.EM_MARCHA_LENTA -> "${consumo.valor} L/h (marcha lenta)"
                null -> "não decodificado"
                else -> "unidade ${consumo.unidade} DESCONHECIDA, valor ${consumo.valor}"
            }
        )
        sb.appendLine("autonomia (app) = ${live.autonomyDteKm} km")
        sb.appendLine("autonomia combustível (carro) = ${atual[HavalTelemetrySource.CHAVE_AUTONOMIA_COMBUSTIVEL]?.valor}")
        sb.appendLine("autonomia elétrica (carro)    = ${atual[HavalTelemetrySource.CHAVE_AUTONOMIA_ELETRICA]?.valor}")
        sb.appendLine("autonomia genérica (car.basic) = ${atual[HavalTelemetrySource.CHAVE_AUTONOMIA_DO_CARRO]?.valor}")
        sb.appendLine("bateria híbrida = ${atual[HavalTelemetrySource.CHAVE_BATERIA]?.valor}")
        sb.appendLine("fluxo de energia = ${atual[HavalTelemetrySource.CHAVE_FLUXO_DE_ENERGIA]?.valor} (negativo = regeneração)")
        estado.trips.forEach {
            sb.appendLine("trip ${it.id} (${it.label}): ${it.metrics.distanceKm} km, ${it.metrics.fuelLitres} L, ${it.status}")
        }
        sb.appendLine()

        // A fita guarda 10 mil eventos, que em texto passam de meio megabyte —
        // mais do que os sites de paste aceitam. Cortam-se os mais antigos, e
        // não os recentes, porque o que interessa é o trecho que acabou de ser
        // dirigido. O corte é dito em voz alta: um relatório que some com
        // dados calado faria procurar defeito onde só houve limite de tamanho.
        val fita = diario.fita.value
        val cabem = fita.takeLast(maxEventos)
        sb.appendLine("--- FITA DOS ÚLTIMOS EVENTOS (hora | chave | valor cru) ---")
        sb.appendLine("(é aqui que a unidade aparece: como o número se move enquanto o carro anda)")
        if (cabem.size < fita.size) {
            sb.appendLine("(mostrando os ${cabem.size} mais recentes de ${fita.size}; o arquivo salvo no aparelho tem todos)")
        }
        cabem.forEach {
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
