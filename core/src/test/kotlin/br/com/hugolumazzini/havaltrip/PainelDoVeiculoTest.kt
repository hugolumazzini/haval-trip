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
        val p = PainelDoVeiculo.ler(portas = "{0,0,0,0,0,0}", cintos = "{0,0,0}")
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
        val p = PainelDoVeiculo.ler(portas = "{1,0,0,0,0,0}", cintos = "{1,0,0}")
        assertEquals(
            listOf(Assento.MOTORISTA.rotulo, Abertura.PORTA_MOTORISTA.rotulo),
            p.avisos,
        )
    }

    @Test
    fun `qualquer valor diferente de zero conta como aberto`() {
        // Vidro em movimento publica um código intermediário, e ele também
        // significa "não está fechado".
        val p = PainelDoVeiculo.ler(vidros = "{0,3,0,0}")
        assertEquals(1, p.vidrosAbertos.size)
    }

    @Test
    fun `pressao dos pneus vira roda a roda`() {
        val p = PainelDoVeiculo.ler(pneus = "{230,232,228,231}")
        assertEquals(4, p.pneus.size)
        assertEquals(Roda.DIANTEIRA_ESQ, p.pneus[0].roda)
        assertEquals(230.0, p.pneus[0].pressao!!, 1e-9)
        assertEquals("kPa", p.unidadeDePressao)
    }

    @Test
    fun `pneu com zero e sensor mudo, nao pneu vazio`() {
        val p = PainelDoVeiculo.ler(pneus = "{230,0,228,231}")
        assertNull(p.pneus[1].pressao)
    }

    @Test
    fun `a unidade sai da ordem de grandeza`() {
        assertEquals("bar", PainelDoVeiculo.ler(pneus = "{2.3,2.3,2.3,2.3}").unidadeDePressao)
        assertEquals("psi", PainelDoVeiculo.ler(pneus = "{33,33,33,33}").unidadeDePressao)
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
