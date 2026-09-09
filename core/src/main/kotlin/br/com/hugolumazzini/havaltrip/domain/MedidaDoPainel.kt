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

    /** Altura de uma linha, com a entrelinha, em frações do tamanho da fonte. */
    private const val ENTRELINHA = 1.25f

    /** Menor letra que ainda se lê num painel, e maior que ainda não é cartaz. */
    private const val MINIMO = 12f
    private const val MAXIMO = 96f

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
        val linhas = if (emLinha) 1f + 2 * PROPORCAO_DO_ROTULO else 1f + PROPORCAO_DO_ROTULO
        val cabeNaAltura = alturaDaFatia / (linhas * ENTRELINHA)

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
        val linhas = if (emLinha) 1f + 2 * PROPORCAO_DO_ROTULO else 1f + PROPORCAO_DO_ROTULO
        return tamanhoDaFonte * linhas * ENTRELINHA
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
