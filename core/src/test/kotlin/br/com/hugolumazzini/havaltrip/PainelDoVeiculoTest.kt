package br.com.hugolumazzini.havaltrip

import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo.Abertura
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo.Assento
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo.Roda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Leitura das propriedades compostas do carro: posição por posição. */
class PainelDoVeiculoTest {

    @Test
    fun `cada posicao das portas cai no lugar certo`() {
        val p = PainelDoVeiculo.ler(portas = "{0,0,1,0,0,0}")
        assertEquals(listOf(Abertura.PORTA_TRASEIRA_ESQ), p.abertas)
    }

    @Test
    fun `carro fechado nao gera aviso nenhum`() {
        val p = PainelDoVeiculo.ler(portas = "{0,0,0,0,0,0}", cintos = "{0,0,0,0,0}")
        assertTrue(p.avisos.isEmpty())
        assertTrue(p.tudoCerto)
    }

    @Test
    fun `sem leitura nenhuma o app nao afirma que esta tudo certo`() {
        val p = PainelDoVeiculo.ler()
        assertFalse("sem dado não dá para garantir porta fechada", p.tudoCerto)
        assertFalse(p.algumaLeitura)
    }

    @Test
    fun `cinto vem antes de porta na lista de avisos`() {
        val p = PainelDoVeiculo.ler(portas = "{1,0,0,0,0,0}", cintos = "{1,0,0,0,0}")
        assertEquals(
            listOf(Assento.MOTORISTA.rotulo, Abertura.PORTA_MOTORISTA.rotulo),
            p.avisos,
        )
    }

    @Test
    fun `vidro fechado publica 1, e nao 0`() {
        // Lido no H6 em 02/09/2026 com as quatro janelas fechadas. Com a regra
        // de "diferente de zero é aberto", o carro parado acusava tudo aberto.
        val fechado = PainelDoVeiculo.ler(vidros = "{1,1,1,1}")
        assertTrue(fechado.vidrosAbertos.isEmpty())

        // O 3 apareceu no instante em que um vidro se moveu.
        val movendo = PainelDoVeiculo.ler(vidros = "{3,1,1,1}")
        assertEquals(listOf(PainelDoVeiculo.Vidro.MOTORISTA), movendo.vidrosAbertos)
    }

    @Test
    fun `cinto tem cinco lugares, nao tres`() {
        val p = PainelDoVeiculo.ler(cintos = "{0,0,0,1,0}")
        assertEquals(5, p.cintos.size)
        assertEquals(listOf(Assento.TRASEIRO_CENTRO), p.semCinto)
    }

    @Test
    fun `pneu vem em par de pressao e temperatura`() {
        // Valor real do carro: quatro pares, pressão em bar e grau.
        val p = PainelDoVeiculo.ler(
            pneus = "{2.29707,26.0,2.3657,25.0,2.33825,25.0,2.2559,25.0}",
            unidadePneus = "1",
        )
        assertEquals(4, p.pneus.size)
        assertEquals(Roda.DIANTEIRA_ESQ, p.pneus[0].roda)
        assertEquals(2.29707, p.pneus[0].pressaoBar!!, 1e-9)
        assertEquals(26.0, p.pneus[0].temperaturaC!!, 1e-9)
        // O segundo par é a roda dianteira direita, não a temperatura da esquerda.
        assertEquals(2.3657, p.pneus[1].pressaoBar!!, 1e-9)

        // Com o código 1 o cluster mostrava 34.1 psi para essa pressão.
        assertEquals(PainelDoVeiculo.Unidade.PSI, p.unidadeDePressao)
        assertEquals(33.3, p.pneus[0].pressao(p.unidadeDePressao)!!, 0.2)
    }

    @Test
    fun `formato antigo de quatro pressoes continua sendo lido`() {
        val p = PainelDoVeiculo.ler(pneus = "{2.3,2.4,2.3,2.4}")
        assertEquals(4, p.pneus.size)
        assertEquals(2.4, p.pneus[1].pressaoBar!!, 1e-9)
        assertNull(p.pneus[1].temperaturaC)
        assertEquals(PainelDoVeiculo.Unidade.BAR, p.unidadeDePressao)
    }

    @Test
    fun `pneu com zero e sensor mudo, nao pneu vazio`() {
        val p = PainelDoVeiculo.ler(pneus = "{2.3,25.0,0,0,2.3,25.0,2.3,25.0}")
        assertNull(p.pneus[1].pressaoBar)
    }

    @Test
    fun `formato inesperado nao derruba nem inventa`() {
        val p = PainelDoVeiculo.ler(portas = "sei la", pneus = "{a,b}")
        assertTrue(p.aberturas.isEmpty())
        assertTrue(p.pneus.isEmpty())
        assertFalse(p.tudoCerto)
    }

    @Test
    fun `posicao a mais no firmware nao quebra a leitura`() {
        val p = PainelDoVeiculo.ler(portas = "{0,0,0,0,0,0,1}")
        assertEquals(6, p.aberturas.size)
        assertTrue(p.abertas.isEmpty())
    }

    @Test
    fun `posicao a menos so le o que veio`() {
        val p = PainelDoVeiculo.ler(portas = "{1,0}")
        assertEquals(2, p.aberturas.size)
        assertEquals(listOf(Abertura.PORTA_MOTORISTA), p.abertas)
    }
}
