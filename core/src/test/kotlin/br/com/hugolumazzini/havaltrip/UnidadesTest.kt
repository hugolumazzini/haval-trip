package br.com.hugolumazzini.havaltrip

import br.com.hugolumazzini.havaltrip.telemetry.Unidades
import br.com.hugolumazzini.havaltrip.telemetry.Unidades.ConsumoInstantaneo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * O consumo instantâneo do H6, que já foi lido errado uma vez.
 *
 * O app tratava `{1.0,0.0}` como um número solto, a conversão devolvia `null` e
 * nenhuma Trip somava um mililitro sequer. Estes testes existem para essa
 * regressão não voltar sem ninguém notar dentro do carro.
 */
class UnidadesTest {

    @Test
    fun `le o par que o carro publica`() {
        assertEquals(
            ConsumoInstantaneo(unidade = 1, valor = 7.4),
            Unidades.lerConsumoInstantaneo("{1.0,7.4}"),
        )
    }

    @Test
    fun `aceita espaco entre os numeros`() {
        assertEquals(
            ConsumoInstantaneo(unidade = 4, valor = 0.8),
            Unidades.lerConsumoInstantaneo(" {4.0, 0.8} "),
        )
    }

    @Test
    fun `recusa o que nao for par entre chaves`() {
        assertNull(Unidades.lerConsumoInstantaneo(null))
        assertNull(Unidades.lerConsumoInstantaneo("5.8"))
        assertNull(Unidades.lerConsumoInstantaneo("{}"))
        assertNull(Unidades.lerConsumoInstantaneo("{1.0}"))
        assertNull(Unidades.lerConsumoInstantaneo("{a,b}"))
    }

    @Test
    fun `andando, converte litros por 100 km em litros por hora`() {
        // 8 L/100 km a 50 km/h = 4 L/h.
        val consumo = ConsumoInstantaneo(ConsumoInstantaneo.POR_DISTANCIA, 8.0)
        assertEquals(4.0, Unidades.litrosPorHora(consumo, velocidadeKmh = 50.0), 1e-9)
    }

    @Test
    fun `parado, o valor em litros por hora passa direto`() {
        // É o gasto da marcha lenta, que a conversão por distância zeraria.
        val consumo = ConsumoInstantaneo(ConsumoInstantaneo.EM_MARCHA_LENTA, 0.9)
        assertEquals(0.9, Unidades.litrosPorHora(consumo, velocidadeKmh = 0.0), 1e-9)
    }

    @Test
    fun `hibrido em modo eletrico nao queima nada`() {
        // O caso real do H6 HEV: unidade válida, consumo zero, carro andando.
        val consumo = Unidades.lerConsumoInstantaneo("{1.0,0.0}")
        assertEquals(0.0, Unidades.litrosPorHora(consumo, velocidadeKmh = 38.4), 1e-9)
    }

    @Test
    fun `unidade desconhecida nao vira litro`() {
        val consumo = ConsumoInstantaneo(unidade = 9, valor = 12.0)
        assertEquals(0.0, Unidades.litrosPorHora(consumo, velocidadeKmh = 60.0), 1e-9)
    }

    @Test
    fun `tanque vem do percentual e nao passa do limite`() {
        assertEquals(61.0, Unidades.litrosNoTanque(100.0), 1e-9)
        assertEquals(30.5, Unidades.litrosNoTanque(50.0), 1e-9)
        assertEquals(0.0, Unidades.litrosNoTanque(null), 1e-9)
        assertEquals(61.0, Unidades.litrosNoTanque(140.0), 1e-9)
    }
}
