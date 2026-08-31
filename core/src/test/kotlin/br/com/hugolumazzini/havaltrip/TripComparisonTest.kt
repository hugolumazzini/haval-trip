package br.com.hugolumazzini.havaltrip

import br.com.hugolumazzini.havaltrip.domain.TripMetrics
import br.com.hugolumazzini.havaltrip.domain.TripRecord
import br.com.hugolumazzini.havaltrip.services.TripComparison
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripComparisonTest {

    private fun registro(
        label: String,
        distancia: Double,
        litros: Double,
        movimento: Double,
        parado: Double,
    ) = TripRecord(
        recordId = label,
        tripId = label,
        label = label,
        metrics = TripMetrics(
            distanceKm = distancia,
            movingTimeS = movimento,
            idleTimeS = parado,
            fuelLitres = litros,
            maxSpeedKmh = 100.0,
        ),
        startedAtMs = 0L,
        savedAtMs = 1L,
        odometerStartKm = 0.0,
        odometerEndKm = distancia,
    )

    // Cidade: 100 km com 12,5 L = 8 km/L, meia hora parado.
    private val cidade = registro("Cidade", 100.0, 12.5, movimento = 5400.0, parado = 1800.0)
    // Estrada: 100 km com 10 L = 10 km/L, cinco minutos parado.
    private val estrada = registro("Estrada", 100.0, 10.0, movimento = 4000.0, parado = 300.0)

    @Test
    fun `diferenca percentual de consumo`() {
        val r = TripComparison.compare(cidade, estrada)
        val linha = r.line(TripComparison.LABEL_CONSUMO)!!
        assertEquals(8.0, linha.a!!, 1e-9)
        assertEquals(10.0, linha.b!!, 1e-9)
        assertEquals(2.0, linha.delta!!, 1e-9)
        assertEquals(25.0, linha.deltaPercent!!, 1e-9)
        assertEquals("mais km/L é melhor", 2, linha.winner)
    }

    @Test
    fun `diferenca de tempo parado`() {
        val linha = TripComparison.compare(cidade, estrada).line(TripComparison.LABEL_TEMPO_PARADO)!!
        assertEquals(-1500.0, linha.delta!!, 1e-9)
        assertEquals("menos tempo parado é melhor", 2, linha.winner)
    }

    @Test
    fun `diferenca de combustivel gasto`() {
        val linha = TripComparison.compare(cidade, estrada).line(TripComparison.LABEL_COMBUSTIVEL)!!
        assertEquals(12.5, linha.a!!, 1e-9)
        assertEquals(10.0, linha.b!!, 1e-9)
        assertEquals(-2.5, linha.delta!!, 1e-9)
        assertEquals(-20.0, linha.deltaPercent!!, 1e-9)
        assertEquals("gastar menos litros é melhor", 2, linha.winner)
    }

    @Test
    fun `o consumo desfaz a vantagem que a viagem curta tem no litro absoluto`() {
        val curta = registro("Curta", 10.0, 1.5, 900.0, 100.0)     // 6,7 km/L
        val longa = registro("Longa", 100.0, 12.0, 5000.0, 500.0)  // 8,3 km/L
        val r = TripComparison.compare(curta, longa)
        // No litro absoluto a curta "ganha" só porque andou menos…
        assertEquals(1, r.line(TripComparison.LABEL_COMBUSTIVEL)!!.winner)
        // …mas em km/L, que independe do tamanho do percurso, a longa é melhor.
        assertEquals(2, r.line(TripComparison.LABEL_CONSUMO)!!.winner)
    }

    @Test
    fun `comparar com viagem sem dado devolve nulo em vez de zero`() {
        val vazia = registro("Vazia", 0.0, 0.0, 0.0, 0.0)
        val r = TripComparison.compare(vazia, estrada)
        val consumo = r.line(TripComparison.LABEL_CONSUMO)!!
        assertNull(consumo.a)
        assertNull(consumo.delta)
        assertNull(consumo.deltaPercent)
        assertNull(consumo.winner)
    }

    @Test
    fun `empate pratico nao elege vencedor`() {
        val outra = registro("Igual", 100.0, 12.5, 5400.0, 1800.0)
        val linha = TripComparison.compare(cidade, outra).line(TripComparison.LABEL_CONSUMO)!!
        assertEquals(0, linha.winner)
    }
}
