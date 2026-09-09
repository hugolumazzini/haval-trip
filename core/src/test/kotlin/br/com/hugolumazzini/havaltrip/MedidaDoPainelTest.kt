package br.com.hugolumazzini.havaltrip

import br.com.hugolumazzini.havaltrip.domain.MedidaDoPainel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O contrato desta conta é um só: **nada pode passar da borda**. Cada teste
 * mede o que o texto ocuparia e compara com o retângulo que ele recebeu.
 */
class MedidaDoPainelTest {

    /** Retângulos de todo tipo, incluindo os absurdos que o motorista pode criar. */
    private val retangulos = listOf(
        550f to 145f,   // a faixa larga e baixa, o caso comum
        200f to 300f,   // alto e estreito
        60f to 40f,     // minúsculo
        900f to 500f,   // generoso
    )

    private val valores = listOf(
        "8" to "km",
        "73,3" to "km",
        "702,9" to "km",
        "15:05" to "h",
        "10,4" to "km/L",
        "48.901" to "km",
    )

    private val escalas = listOf(0.75f, 1.0f, 1.3f, 1.6f)

    @Test
    fun `nunca corta o texto, em nenhuma combinacao`() {
        var casos = 0
        for ((largura, altura) in retangulos) {
            for (itens in 1..5) {
                for (emLinha in listOf(true, false)) {
                    // As fatias, do mesmo jeito que a tela as reparte.
                    val fatiaL = if (emLinha) largura / itens else largura
                    val fatiaA = if (emLinha) altura else altura / itens
                    for ((valor, unidade) in valores) {
                        for (escala in escalas) {
                            val fonte = MedidaDoPainel.tamanhoDaFonte(
                                fatiaA, fatiaL, valor, unidade, emLinha, escala,
                            )
                            val alto = MedidaDoPainel.alturaOcupada(fonte, emLinha)
                            val largo = MedidaDoPainel.larguraOcupada(fonte, valor, unidade, emLinha)
                            val onde = "$valor $unidade em ${fatiaL}x$fatiaA, escala $escala, linha=$emLinha"
                            // O mínimo legível tem precedência sobre caber: numa
                            // fatia menor que a própria letra mínima não há
                            // tamanho que sirva, e uma letra invisível não é
                            // solução. Por isso a folga só é exigida acima dele.
                            if (fonte > 12.01f) {
                                assertTrue("passou da altura: $onde", alto <= fatiaA + 0.01f)
                                assertTrue("passou da largura: $onde", largo <= fatiaL + 0.01f)
                            }
                            casos++
                        }
                    }
                }
            }
        }
        assertTrue("os casos foram gerados", casos > 500)
    }

    @Test
    fun `o defeito que sumia com a unidade`() {
        // O caso exato: dois dados lado a lado numa faixa de 760x200 px a 220
        // dpi (≈ 552x145 dp), letra "Enorme", e o valor curto da Viagem atual.
        // Antes de a altura entrar na conta, a letra crescia até 97 e a linha
        // da unidade caía fora da janela.
        val fonte = MedidaDoPainel.tamanhoDaFonte(
            alturaDaFatia = 145f,
            larguraDaFatia = 552f / 2,
            valor = "73,3",
            unidade = "km",
            emLinha = true,
            escala = 1.6f,
        )
        assertTrue("a letra ainda estoura a altura: $fonte", MedidaDoPainel.alturaOcupada(fonte, true) <= 145.01f)
    }

    @Test
    fun `a escala do motorista faz diferenca quando ha espaco`() {
        fun com(escala: Float) = MedidaDoPainel.tamanhoDaFonte(
            alturaDaFatia = 300f, larguraDaFatia = 600f,
            valor = "73,3", unidade = "km", emLinha = true, escala = escala,
        )
        // Numa fatia folgada, pedir "Menor" tem de dar uma letra menor que
        // "Normal" — senão o botão existe sem fazer nada.
        assertTrue("menor não diminuiu", com(0.75f) < com(1.0f))
    }

    @Test
    fun `a letra nunca fica ilegivel`() {
        val fonte = MedidaDoPainel.tamanhoDaFonte(
            alturaDaFatia = 8f, larguraDaFatia = 8f,
            valor = "702,9", unidade = "km", emLinha = true, escala = 0.75f,
        )
        assertEquals(12f, fonte, 0.01f)
    }
    @Test
    fun `a caixa minima comporta a letra minima`() {
        // O piso de 12sp de `tamanhoDaFonte` promete uma letra que a fatia pode
        // não comportar. A caixa mínima é a contrapartida: com ela, a promessa
        // se cumpre.
        val altura = MedidaDoPainel.alturaMinimaDaCaixa(emLinha = true, itens = 3)
        val util = altura - 2 * MedidaDoPainel.RESPIRO
        assertTrue(
            "faltou altura: caixa de $altura dp",
            util >= MedidaDoPainel.alturaOcupada(12f, emLinha = true),
        )
    }

    @Test
    fun `empilhado a caixa minima cresce com o numero de itens`() {
        val tres = MedidaDoPainel.alturaMinimaDaCaixa(emLinha = false, itens = 3)
        val quatro = MedidaDoPainel.alturaMinimaDaCaixa(emLinha = false, itens = 4)
        assertTrue("quatro itens não pediram mais altura", quatro > tres)
    }

    @Test
    fun `a caixa minima cabe no overlay do simulador`() {
        // 760x200 px a 220 dpi = 552x145 dp. Foi este retângulo que cortou os
        // números e deixou só os rótulos na tela: 20% de 145 dp são 29 dp, e
        // não cabe nem a letra mínima. O mínimo tem de caber aqui — senão o
        // conserto empurra o conteúdo para fora da janela em vez de mostrá-lo.
        assertTrue(MedidaDoPainel.alturaMinimaDaCaixa(emLinha = true, itens = 3) <= 145f)
        assertTrue(MedidaDoPainel.larguraMinimaDaCaixa(emLinha = true, itens = 3) <= 552f)
    }
}
