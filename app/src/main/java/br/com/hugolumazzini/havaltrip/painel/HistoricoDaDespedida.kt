package br.com.hugolumazzini.havaltrip.painel

import android.content.Context
import androidx.activity.ComponentActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Em que tela cada janela do painel está agora, contado pela própria janela.
 *
 * O número da tela do painel muda entre uma partida e outra da central (no
 * mesmo dia o histórico registrou a janela da bola na tela 3 e depois na 1).
 * Quem sabe onde a janela está de verdade é ela mesma, ao nascer.
 */
object TelaDasJanelas {
    private val atual = java.util.concurrent.ConcurrentHashMap<JanelaDoPainel, Int>()

    fun de(janela: JanelaDoPainel): Int? = atual[janela]

    internal fun nasceu(janela: JanelaDoPainel, tela: Int) {
        atual[janela] = tela
    }

    internal fun morreu(janela: JanelaDoPainel, tela: Int) {
        atual.remove(janela, tela)
    }
}

/**
 * Anota no histórico o nascimento ou a morte de uma janela do painel, e em que
 * tela ela estava, e guarda a tela em [TelaDasJanelas].
 */
fun ComponentActivity.registrarCicloDaJanela(janela: JanelaDoPainel, viva: Boolean) {
    @Suppress("DEPRECATION")
    val tela = windowManager.defaultDisplay.displayId
    if (viva) TelaDasJanelas.nasceu(janela, tela) else TelaDasJanelas.morreu(janela, tela)
    val detalhes = buildList {
        add("tela $tela")
        if (isFinishing) add("finalizando")
        if (EstadoDaDespedida.estaAtiva()) add("despedida ativa")
    }.joinToString(", ")
    HistoricoDaDespedida.info("🪟 ${javaClass.simpleName} ${if (viva) "criada" else "destruída"} ($detalhes)")
}

/**
 * A linha do tempo da despedida, para se ler no próprio app.
 *
 * Fica em arquivo, e não só na memória: se o app cair no meio da despedida, o
 * que aconteceu até ali continua lá depois, e a marca de "processo iniciado"
 * mostra que houve a queda. Sem ver o `logcat` no carro, é isto que conta a
 * história.
 */
object HistoricoDaDespedida {

    data class Evento(val quando: Long, val tipo: TipoEvento, val descricao: String) {
        val hora: String get() = formato.format(Date(quando))
    }

    enum class TipoEvento {
        ATIVACAO,
        DESATIVACAO,
        TENTATIVA_REMOCAO,
        BLOQUEIO,
        PROJECAO,
        ERRO,
        INFO,
    }

    private const val MAX_EVENTOS = 300
    private const val ESCRITAS_ATE_COMPACTAR = 100

    private val formato = SimpleDateFormat("dd/MM HH:mm:ss", Locale.forLanguageTag("pt-BR"))
    private val eventos = ArrayList<Evento>()
    private var arquivo: File? = null
    private var escritas = 0

    /** Liga o arquivo e carrega o que já havia. Pode ser chamada quantas vezes quiser. */
    fun iniciar(context: Context) {
        synchronized(eventos) {
            if (arquivo != null) return
            val f = File(context.applicationContext.filesDir, "despedida-historico.tsv")
            arquivo = f
            eventos.addAll(0, lerDoArquivo(f))
            while (eventos.size > MAX_EVENTOS) eventos.removeAt(0)
            reescrever(f)
        }
        info("🚀 Processo do app iniciado")
    }

    fun ativou(origem: String) = registrar(TipoEvento.ATIVACAO, "✅ Despedida ATIVADA ($origem)")

    fun desativou(origem: String) = registrar(TipoEvento.DESATIVACAO, "❌ Despedida DESATIVADA ($origem)")

    fun tentouRemover(janela: String) =
        registrar(TipoEvento.TENTATIVA_REMOCAO, "⚠️ Pedido para remover $janela durante a despedida")

    fun bloqueou(janela: String) =
        registrar(TipoEvento.BLOQUEIO, "🛡️ Pedido de remoção de $janela BLOQUEADO (despedida ativa)")

    fun projetou(detalhe: String) = registrar(TipoEvento.PROJECAO, "📽️ $detalhe")

    fun erro(mensagem: String) = registrar(TipoEvento.ERRO, "💥 $mensagem")

    fun info(mensagem: String) = registrar(TipoEvento.INFO, mensagem)

    /** Do mais recente para o mais antigo, que é como a tela lê. */
    fun obterEventos(): List<Evento> = synchronized(eventos) { eventos.reversed() }

    /** Do mais antigo para o mais recente, para ir junto do relatório de logs. */
    fun comoTexto(): String = synchronized(eventos) {
        buildString {
            appendLine("=== HISTÓRICO DA DESPEDIDA (${eventos.size} eventos) ===")
            eventos.forEach { appendLine("${it.hora}  ${it.descricao}") }
            appendLine("=== FIM DO HISTÓRICO ===")
        }
    }

    fun limpar() {
        synchronized(eventos) {
            eventos.clear()
            arquivo?.let { reescrever(it) }
        }
        info("🧹 Histórico limpo")
    }

    private fun registrar(tipo: TipoEvento, descricao: String) {
        val evento = Evento(System.currentTimeMillis(), tipo, descricao)
        synchronized(eventos) {
            eventos.add(evento)
            if (eventos.size > MAX_EVENTOS) eventos.removeAt(0)
            val f = arquivo ?: return
            escritas++
            if (escritas % ESCRITAS_ATE_COMPACTAR == 0) reescrever(f)
            else runCatching { f.appendText(linha(evento)) }
        }
    }

    private fun linha(e: Evento): String =
        "${e.quando}\t${e.tipo.name}\t${e.descricao.replace('\n', ' ').replace('\t', ' ')}\n"

    private fun reescrever(f: File) {
        runCatching { f.writeText(eventos.joinToString("") { linha(it) }) }
    }

    private fun lerDoArquivo(f: File): List<Evento> = runCatching {
        if (!f.exists()) return emptyList()
        f.readLines().mapNotNull { l ->
            val partes = l.split('\t', limit = 3)
            if (partes.size < 3) return@mapNotNull null
            val quando = partes[0].toLongOrNull() ?: return@mapNotNull null
            val tipo = TipoEvento.entries.find { it.name == partes[1] } ?: TipoEvento.INFO
            Evento(quando, tipo, partes[2])
        }
    }.getOrDefault(emptyList())
}
