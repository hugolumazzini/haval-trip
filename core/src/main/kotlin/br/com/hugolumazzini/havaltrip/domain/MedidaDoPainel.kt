package br.com.hugolumazzini.havaltrip.domain

/**
 * De que tamanho a letra pode ser na tela do painel de instrumentos.
 *
 * Vive aqui, longe da tela, porque já errou duas vezes e as duas só apareceram
 * numa captura de tela: primeiro o número saía cortado pela lateral, depois a
 * unidade sumia pela borda de baixo. São contas — e conta se testa.
 *
 * O problema é o de sempre nesta janela: quem escolhe o retângulo é o
 * motorista, na tela "Telas" do Impulse, e ele pode escolher qualquer coisa.
 * Então o tamanho tem de nascer do espaço, e não o contrário.
 */
object MedidaDoPainel {

    /** Largura média de um caractere, em frações do tamanho da fonte. */
    private const val LARGURA_DO_CARACTERE = 0.62f

    /** O rótulo e a unidade saem em um terço do tamanho do número. */
    const val PROPORCAO_DO_ROTULO = 0.32f

    /**
     * Menor letra aceitável para o rótulo e a unidade, em sp.
     *
     * Um terço de uma letra já pequena vira ilegível, então o rótulo tem piso
     * próprio. O piso mora aqui, e não na tela, porque quando morava lá a
     * conta de altura o ignorava: pedia 12 sp de número supondo 3,8 sp de
     * rótulo, a tela desenhava 9, e a unidade saía cortada pela borda de
     * baixo. Foi o que sumiu com "km" e "km/L" no overlay do simulador.
     */
    const val ROTULO_MINIMO = 9f

    /** O tamanho do rótulo que corresponde a este tamanho de número, em sp. */
    fun tamanhoDoRotulo(tamanhoDaFonte: Float): Float =
        (tamanhoDaFonte * PROPORCAO_DO_ROTULO).coerceAtLeast(ROTULO_MINIMO)

    /** Altura de uma linha, com a entrelinha, em frações do tamanho da fonte. */
    private const val ENTRELINHA = 1.25f

    /** Menor letra que ainda se lê num painel, e maior que ainda não é cartaz. */
    private const val MINIMO = 12f
    private const val MAXIMO = 96f

    /** Espaço em volta do conteúdo, em dp. A tela usa este mesmo valor. */
    const val RESPIRO = 8f

    /**
     * Um valor típico do painel — "1234,5" — em caracteres.
     *
     * Serve só para dimensionar a caixa mínima. Um número maior que isso
     * encolhe a letra, o que é aceitável; o que não é aceitável é a caixa
     * nascer estreita demais para qualquer número.
     */
    private const val CARACTERES_TIPICOS = 6f

    /**
     * Menor altura de caixa em que o conteúdo ainda aparece inteiro, em dp.
     *
     * Existe porque [tamanhoDaFonte] tem um piso: quando o espaço não dá nem
     * para a letra mínima, ela devolve a letra mínima assim mesmo — e a coluna
     * corta o que passar. Foi exatamente o que aconteceu no simulador, num
     * retângulo de 145 dp de altura: sobravam só os rótulos, sem os números.
     * Melhor a caixa crescer além da fração pedida do que o número sumir.
     */
    fun alturaMinimaDaCaixa(emLinha: Boolean, itens: Int): Float =
        alturaOcupada(MINIMO, emLinha) * (if (emLinha) 1 else itens) * FOLGA + 2 * RESPIRO

    /**
     * Uma folga sobre a altura calculada.
     *
     * [ENTRELINHA] é uma estimativa; a entrelinha de verdade sai da fonte, e
     * quando ela passa da estimativa por um fio o Compose corta a última linha
     * inteira. Uma caixa 10% mais alta é imperceptível; uma unidade cortada,
     * não.
     */
    private const val FOLGA = 1.1f

    /** Menor largura de caixa em que o conteúdo ainda aparece inteiro, em dp. */
    fun larguraMinimaDaCaixa(emLinha: Boolean, itens: Int): Float =
        MINIMO * CARACTERES_TIPICOS * LARGURA_DO_CARACTERE *
            (if (emLinha) itens else 1) + 2 * RESPIRO

    /**
     * @param alturaDaFatia altura disponível para este dado, em dp.
     * @param larguraDaFatia largura disponível para este dado, em dp.
     * @param valor o número já formatado — o que conta é o comprimento dele.
     * @param unidade "km", "km/L". Ao lado do número quando empilhado, embaixo
     *   dele quando lado a lado, e isso muda as duas contas.
     * @param emLinha `true` quando os dados estão lado a lado.
     * @param escala o multiplicador escolhido pelo motorista. Puxa o tamanho,
     *   mas nunca ao ponto de cortar: um número cortado não é uma preferência
     *   atendida, é um valor que parece errado.
     */
    fun tamanhoDaFonte(
        alturaDaFatia: Float,
        larguraDaFatia: Float,
        valor: String,
        unidade: String,
        emLinha: Boolean,
        escala: Float,
    ): Float {
        // Lado a lado são três linhas — rótulo, número, unidade. Empilhado são
        // duas, porque a unidade vai ao lado do número: uma terceira linha por
        // dado esbarrava no rótulo do dado seguinte.
        val rotulos = if (emLinha) 2f else 1f
        // Duas contas porque o rótulo tem piso. Enquanto a proporção rende
        // mais que o piso, vale a proporção; abaixo disso o rótulo é uma
        // altura fixa que o número tem de descontar do que sobra.
        val comProporcao = alturaDaFatia / ((1f + rotulos * PROPORCAO_DO_ROTULO) * ENTRELINHA)
        val cabeNaAltura =
            if (comProporcao * PROPORCAO_DO_ROTULO >= ROTULO_MINIMO) comProporcao
            else alturaDaFatia / ENTRELINHA - rotulos * ROTULO_MINIMO

        // O mínimo de 4 caracteres impede que um valor curto ("8") peça uma
        // letra gigantesca. Empilhado, a unidade divide a linha com o número e
        // por isso também pesa — em letra menor, daí a proporção.
        val caracteres = maxOf(valor.length, 4) +
            if (emLinha) 0f else (unidade.length + 1) * PROPORCAO_DO_ROTULO
        val cabeNaLargura = larguraDaFatia / (caracteres * LARGURA_DO_CARACTERE)

        val desejado = alturaDaFatia * 0.42f * escala
        return desejado
            .coerceAtMost(minOf(cabeNaAltura, cabeNaLargura))
            .coerceIn(MINIMO, MAXIMO)
    }

    /** Altura que um dado ocupa de fato com esta letra, em dp. */
    fun alturaOcupada(tamanhoDaFonte: Float, emLinha: Boolean): Float {
        val rotulos = if (emLinha) 2f else 1f
        return (tamanhoDaFonte + rotulos * tamanhoDoRotulo(tamanhoDaFonte)) * ENTRELINHA
    }

    /** Largura que um dado ocupa de fato com esta letra, em dp. */
    fun larguraOcupada(
        tamanhoDaFonte: Float,
        valor: String,
        unidade: String,
        emLinha: Boolean,
    ): Float {
        val caracteres = valor.length +
            if (emLinha) 0f else (unidade.length + 1) * PROPORCAO_DO_ROTULO
        return tamanhoDaFonte * caracteres * LARGURA_DO_CARACTERE
    }
}
