package br.com.hugolumazzini.havaltrip

import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.LeituraDaIgnicao
import org.junit.Assert.assertEquals
import org.junit.Test

/** O que separa "a multimídia acendeu" de "o carro está pronto para andar". */
class LeituraDaIgnicaoTest {

    private fun ler(
        pronto: String? = null,
        motor: String? = null,
        rotacao: String? = null,
        energia: String? = null,
        velocidade: Double = 0.0,
    ) = LeituraDaIgnicao.ler(pronto, motor, rotacao, energia, velocidade)

    @Test
    fun `so a multimidia ligada nao e ignicao`() {
        // Porta aberta, tela acesa, chave no bolso: ACC, motor parado, carro parado.
        assertEquals(IgnitionState.OFF, ler(pronto = "0", motor = "0", rotacao = "0", energia = "1"))
    }

    @Test
    fun `carro trancado e calado fica desligado`() {
        assertEquals(IgnitionState.OFF, ler(energia = "0"))
        assertEquals(IgnitionState.OFF, ler())
    }

    @Test
    fun `pronto para andar liga mesmo com o motor a combustao parado`() {
        // O caso do híbrido no semáforo: READY aceso, motor desligado, 0 km/h.
        assertEquals(IgnitionState.ON, ler(pronto = "1", motor = "0", rotacao = "0", energia = "2"))
    }

    @Test
    fun `motor girando liga`() {
        assertEquals(IgnitionState.ON, ler(rotacao = "780", energia = "1"))
        assertEquals(IgnitionState.ON, ler(motor = "1"))
    }

    @Test
    fun `carro andando liga, digam o que disserem as outras chaves`() {
        assertEquals(IgnitionState.ON, ler(pronto = "0", motor = "0", rotacao = "0", energia = "1", velocidade = 42.0))
    }

    @Test
    fun `modo de energia em ON liga quando nao ha mais nada a consultar`() {
        assertEquals(IgnitionState.ON, ler(energia = "2"))
        assertEquals(IgnitionState.ON, ler(energia = "3"))
    }

    @Test
    fun `valor ilegivel nao liga nada`() {
        assertEquals(IgnitionState.OFF, ler(pronto = "", motor = "sim", energia = "ACC"))
    }
}
