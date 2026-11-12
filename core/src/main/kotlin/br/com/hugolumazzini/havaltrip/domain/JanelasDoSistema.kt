package br.com.hugolumazzini.havaltrip.domain

/**
 * Uma janela como o `dumpsys window windows` a descreve.
 *
 * @param dono o que vem dentro de `Window{… u0 <dono>}`: o pacote, o pacote com
 *   a Activity, ou o título que a janela deu a si mesma.
 * @param tipo o `ty=` da janela, por nome (`APPLICATION_OVERLAY`) ou número.
 * @param camada o `mBaseLayer`; quanto maior, mais acima. Duas janelas de mesma
 *   camada ficam na ordem em que foram colocadas: a última, na frente.
 * @param visivel se a view da janela está visível (`mViewVisibility=0x0`).
 *   O sistema mantém janelas invisíveis de sobreposição que não tapam nada.
 */
data class JanelaDoSistema(
    val id: String,
    val dono: String,
    val tela: Int?,
    val tipo: String?,
    val camada: Int?,
    val visivel: Boolean = true,
) {
    fun descrever(): String = buildString {
        append(dono)
        val extras = listOfNotNull(tipo, camada?.let { "camada $it" })
        if (extras.isNotEmpty()) append(" [").append(extras.joinToString(", ")).append("]")
    }
}

/**
 * Lê a saída do `dumpsys window windows`.
 *
 * O comando lista as janelas **de cima para baixo**: a primeira é a que está na
 * frente. Cada uma começa num cabeçalho `Window #N Window{id u0 dono}:` e os
 * detalhes vêm nas linhas seguintes, até o próximo cabeçalho. Basta passar
 * pelas linhas com `Window #`, `mDisplayId=`, `ty=`, `mBaseLayer=` e
 * `mViewVisibility=` ([FILTRO]); o resto pode vir cortado.
 */
object JanelasDoSistema {

    /** Para o `grep -E` no aparelho: só as linhas que [listar] usa. */
    const val FILTRO = "Window #|mDisplayId=|ty=|mBaseLayer=|mViewVisibility="

    private val cabecalho = Regex("""Window #\d+ Window\{(\w+) u\d+ (.*)\}:?\s*$""")
    private val tela = Regex("""\bmDisplayId=(\d+)""")
    private val tipo = Regex("""\bty=([A-Za-z_0-9]+)""")
    private val camada = Regex("""\bmBaseLayer=(\d+)""")
    private val visibilidade = Regex("""\bmViewVisibility=0x([0-9a-fA-F]+)""")

    fun listar(saida: String): List<JanelaDoSistema> {
        val janelas = mutableListOf<JanelaDoSistema>()
        var atual: JanelaDoSistema? = null
        var visibilidadeLida = false

        for (linha in saida.lines()) {
            val c = cabecalho.find(linha)
            if (c != null) {
                atual?.let { janelas += it }
                atual = JanelaDoSistema(c.groupValues[1], c.groupValues[2].trim(), null, null, null)
                visibilidadeLida = false
                continue
            }
            val j = atual ?: continue
            val v = if (visibilidadeLida) null else visibilidade.find(linha)?.groupValues?.get(1)
            if (v != null) visibilidadeLida = true
            atual = j.copy(
                tela = j.tela ?: tela.find(linha)?.groupValues?.get(1)?.toIntOrNull(),
                tipo = j.tipo ?: tipo.find(linha)?.groupValues?.get(1),
                camada = j.camada ?: camada.find(linha)?.groupValues?.get(1)?.toIntOrNull(),
                visivel = if (v != null) v.toIntOrNull(16) == 0 else j.visivel,
            )
        }
        atual?.let { janelas += it }
        return janelas
    }

    /**
     * As janelas visíveis de **outros** donos que estão na frente da nossa, na [telaId].
     *
     * Devolve `null` quando a nossa janela não aparece na lista — aí não dá para
     * dizer quem está na frente, e a pior resposta seria "ninguém".
     *
     * @param nossa como a nossa janela aparece em `Window{… u0 <dono>}`.
     * @param pacote o pacote do app: janelas cujo dono começa com ele (as
     *   Activities do painel) não contam como "outros".
     */
    fun naFrenteDaNossa(janelas: List<JanelaDoSistema>, telaId: Int, nossa: String, pacote: String): List<JanelaDoSistema>? {
        val daTela = janelas.filter { it.tela == telaId }
        val posicao = daTela.indexOfFirst { it.dono == nossa }
        if (posicao < 0) return null
        return daTela.take(posicao).filter { it.visivel && !it.dono.startsWith(pacote) && it.dono != nossa }
    }
}
