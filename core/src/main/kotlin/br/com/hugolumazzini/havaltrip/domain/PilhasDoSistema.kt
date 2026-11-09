package br.com.hugolumazzini.havaltrip.domain

/** Uma pilha de janelas do Android, como o `am stack list` a descreve. */
data class Pilha(val id: Int, val tela: Int, val nossa: Boolean)

/**
 * Lê a saída do `am stack list`.
 *
 * O comando escreve o cabeçalho da pilha (`Stack id=5 … displayId=3 …`) numa
 * linha e as tarefas dela — `taskId=17: pacote/.Activity` — nas linhas
 * seguintes. O nome do app, portanto, **nunca está no cabeçalho**: quem procura
 * o pacote só nele conclui, sempre, que a pilha é de outro app. Foi o que fez a
 * limpeza da despedida apagar a própria janela da despedida.
 *
 * Se a saída não tiver o formato esperado (um Android que não tem o comando, um
 * erro no lugar da lista), o resultado é vazio — e vazio quer dizer "não mexa
 * em nada".
 */
object PilhasDoSistema {

    private val cabecalho = Regex("""Stack id=(\d+).*displayId=(\d+)""")

    fun listar(saida: String, pacote: String): List<Pilha> {
        val pilhas = mutableListOf<Pilha>()
        var id: Int? = null
        var tela = -1
        var nossa = false

        fun fechar() {
            id?.let { pilhas += Pilha(it, tela, nossa) }
        }

        for (linha in saida.lines()) {
            val m = cabecalho.find(linha)
            if (m != null) {
                fechar()
                id = m.groupValues[1].toIntOrNull()
                tela = m.groupValues[2].toIntOrNull() ?: -1
                nossa = pacote in linha
            } else if (id != null && pacote in linha) {
                nossa = true
            }
        }
        fechar()
        return pilhas
    }

    /** As pilhas da [telaId] em que não há nenhuma janela do [pacote]. */
    fun deOutrosApps(saida: String, telaId: Int, pacote: String): List<Int> =
        listar(saida, pacote).filter { it.tela == telaId && !it.nossa }.map { it.id }
}
