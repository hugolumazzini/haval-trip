package br.com.hugolumazzini.havaltrip

import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.TelemetrySample
import br.com.hugolumazzini.havaltrip.domain.TripMetrics
import br.com.hugolumazzini.havaltrip.engine.ConsumptionAverage
import br.com.hugolumazzini.havaltrip.engine.EngineConfig
import br.com.hugolumazzini.havaltrip.engine.TripEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Precisão dos cálculos e a divisão entre tempo parado e tempo em movimento. */
class TripEngineTest {

    private val engine = TripEngine()

    private fun amostra(
        v: Double,
        injecao: Double = 6.0,
        t: Long = 0,
        ignicao: IgnitionState = IgnitionState.ON,
    ) = TelemetrySample(t, v, injecao, 1000.0, 40.0, ignicao)

    @Test
    fun `uma hora a 60 km por hora percorre 60 km`() {
        var m = TripMetrics()
        repeat(3600) { m = engine.accumulate(m, amostra(60.0), 1.0) }
        assertEquals(60.0, m.distanceKm, 1e-6)
    }

    @Test
    fun `distancia acompanha o delta t configurado`() {
        val m = engine.accumulate(TripMetrics(), amostra(100.0), deltaS = 36.0)
        // 100 km/h por 36 s = 1 km.
        assertEquals(1.0, m.distanceKm, 1e-9)
    }

    @Test
    fun `velocidade abaixo do limiar conta como tempo parado e nao gera distancia`() {
        var m = TripMetrics()
        repeat(30) { m = engine.accumulate(m, amostra(0.4), 1.0) }
        assertEquals(0.0, m.distanceKm, 1e-9)
        assertEquals(30.0, m.idleTimeS, 1e-9)
        assertEquals(0.0, m.movingTimeS, 1e-9)
    }

    @Test
    fun `o limiar de 1 km por hora e inclusivo`() {
        val m = engine.accumulate(TripMetrics(), amostra(TripMetrics.MOVING_THRESHOLD_KMH), 1.0)
        assertEquals(1.0, m.movingTimeS, 1e-9)
        assertEquals(0.0, m.idleTimeS, 1e-9)
    }

    @Test
    fun `tempo parado e tempo em movimento somam o tempo total`() {
        var m = TripMetrics()
        repeat(600) { m = engine.accumulate(m, amostra(50.0), 1.0) }
        repeat(200) { m = engine.accumulate(m, amostra(0.0), 1.0) }
        assertEquals(600.0, m.movingTimeS, 1e-9)
        assertEquals(200.0, m.idleTimeS, 1e-9)
        assertEquals(800.0, m.totalTimeS, 1e-9)
    }

    @Test
    fun `motor ligado parado queima combustivel e derruba a media`() {
        var andando = TripMetrics()
        repeat(3600) { andando = engine.accumulate(andando, amostra(60.0, injecao = 6.0), 1.0) }
        // 60 km com 6 L = 10 km/L.
        assertEquals(10.0, andando.avgFuelConsumptionKml!!, 1e-6)

        var comSemaforo = andando
        repeat(1800) { comSemaforo = engine.accumulate(comSemaforo, amostra(0.0, injecao = 0.9), 1.0) }
        // Meia hora parado a 0,9 L/h = 0,45 L a mais, sem um metro a mais.
        assertEquals(6.45, comSemaforo.fuelLitres, 1e-6)
        assertTrue(comSemaforo.avgFuelConsumptionKml!! < andando.avgFuelConsumptionKml!!)
    }

    @Test
    fun `media de consumo e nula enquanto nao houver combustivel queimado`() {
        assertNull(TripMetrics().avgFuelConsumptionKml)
        val m = engine.accumulate(TripMetrics(), amostra(50.0, injecao = 0.0), 1.0)
        assertNull(m.avgFuelConsumptionKml)
    }

    @Test
    fun `media de consumo espera juntar combustivel antes de virar numero`() {
        // O caso real: híbrido saindo no elétrico. Anda 5 km injetando quase
        // nada e a divisão dava 4.000 km/L na tela.
        var m = TripMetrics()
        repeat(300) { m = engine.accumulate(m, amostra(60.0, injecao = 0.003), 1.0) }
        assertTrue(m.distanceKm > 4.0)
        assertTrue(m.fuelLitres < TripMetrics.MIN_LITROS_PARA_MEDIA)
        assertNull(m.avgFuelConsumptionKml)

        // Passado o meio litro, o número aparece — e plausível, não absurdo.
        repeat(600) { m = engine.accumulate(m, amostra(60.0, injecao = 6.0), 1.0) }
        val media = m.avgFuelConsumptionKml!!
        assertTrue("média fora do plausível: $media", media in 5.0..40.0)
    }

    @Test
    fun `velocidade media considera o tempo parado e a de movimento nao`() {
        var m = TripMetrics()
        repeat(1800) { m = engine.accumulate(m, amostra(80.0), 1.0) }   // 40 km em 30 min
        repeat(1800) { m = engine.accumulate(m, amostra(0.0), 1.0) }    // 30 min parado
        assertEquals(40.0, m.distanceKm, 1e-6)
        assertEquals(40.0, m.avgSpeedKmh!!, 1e-6)        // 40 km em 1 h
        assertEquals(80.0, m.avgMovingSpeedKmh!!, 1e-6)  // 40 km em 30 min
        assertEquals(0.5, m.idleRatio!!, 1e-9)
    }

    @Test
    fun `velocidade maxima guarda o pico mesmo depois de desacelerar`() {
        var m = TripMetrics()
        listOf(40.0, 118.0, 60.0, 0.0).forEach { m = engine.accumulate(m, amostra(it), 1.0) }
        assertEquals(118.0, m.maxSpeedKmh, 1e-9)
    }

    @Test
    fun `litros sao a integral da taxa de injecao`() {
        var m = TripMetrics()
        // 6 L/h durante uma hora inteira: exatamente 6 litros.
        repeat(3600) { m = engine.accumulate(m, amostra(60.0, injecao = 6.0), 1.0) }
        assertEquals(6.0, m.fuelLitres, 1e-6)
    }

    @Test
    fun `divisoes protegidas devolvem nulo em vez de infinito`() {
        val vazio = TripMetrics()
        assertNull(vazio.avgSpeedKmh)
        assertNull(vazio.avgMovingSpeedKmh)
        assertNull(vazio.idleRatio)
        assertNull(vazio.avgFuelConsumptionKml)
    }

    @Test
    fun `delta t e limitado para um engasgo do barramento nao virar distancia`() {
        val config = EngineConfig(maxDeltaS = 5.0)
        val comLimite = TripEngine(config)
        // Dois minutos sem amostra: só os 5 s de teto podem ser integrados.
        assertEquals(5.0, comLimite.deltaSeconds(0L, 120_000L), 1e-9)
        assertEquals(1.0, comLimite.deltaSeconds(0L, 1_000L), 1e-9)
        assertEquals(config.sampleIntervalS, comLimite.deltaSeconds(null, 1_000L), 1e-9)
        // Relógio do carro voltando não gera Δt negativo.
        assertEquals(0.0, comLimite.deltaSeconds(5_000L, 4_000L), 1e-9)
    }

    @Test
    fun `consumo instantaneo e nulo parado ou com injecao cortada`() {
        assertNull(engine.instantConsumptionKml(amostra(0.0)))
        assertNull(engine.instantConsumptionKml(amostra(80.0, injecao = 0.0)))
        assertEquals(10.0, engine.instantConsumptionKml(amostra(60.0, injecao = 6.0))!!, 1e-9)
    }

    @Test
    fun `autonomia e o tanque vezes o consumo da janela`() {
        var media = ConsumptionAverage()
        // 30 km a 0,1 L/km: a janela converge em 10 km/L.
        repeat(30) { media = media.update(deltaKm = 1.0, deltaLitres = 0.1, windowKm = 25.0) }
        assertEquals(10.0, media.kmPerLitre!!, 1e-6)
        assertEquals(400.0, engine.autonomyKm(40.0, media)!!, 1e-4)
        // Sem litro conhecido não há autonomia a dar. Zero litro aqui não é
        // tanque seco: é o `-1` que o H6 publica quando a bóia não respondeu,
        // já convertido. "0 km" seria um susto falso na cara de quem dirige.
        assertNull(engine.autonomyKm(0.0, media))
        assertNull(engine.autonomyKm(40.0, ConsumptionAverage()))
    }

    @Test
    fun `a janela nao pula com um instante atipico`() {
        var media = ConsumptionAverage()
        repeat(30) { media = media.update(1.0, 0.1, 25.0) }
        val antes = media.kmPerLitre!!
        // Um segundo em subida íngreme, gastando cinco vezes mais.
        media = media.update(0.01, 0.005, 25.0)
        val depois = media.kmPerLitre!!
        assertTrue("a autonomia não pode desabar com uma amostra", depois > antes * 0.9)
    }

    @Test
    fun `a arrancada nao vira a base da autonomia`() {
        // Primeiros 200 m de manobra, gastando muito para andar pouco.
        var media = ConsumptionAverage()
        repeat(20) { media = media.update(0.01, 0.005, 25.0) }
        assertNull("sem meio quilômetro rodado a autonomia não tem o que dizer", media.kmPerLitre)

        // Passado o mínimo, ela responde — e já diluída pelo trecho normal.
        repeat(60) { media = media.update(0.01, 0.001, 25.0) }
        assertTrue(media.kmPerLitre!! > 3.0)
    }

    @Test
    fun `o combustivel queimado parado derruba a autonomia`() {
        var media = ConsumptionAverage()
        repeat(10) { media = media.update(1.0, 0.1, 25.0) }
        val rodando = media.kmPerLitre!!

        // Dez minutos de engarrafamento: motor ligado, carro imóvel.
        repeat(600) { media = media.update(deltaKm = 0.0, deltaLitres = 0.8 / 3600.0, windowKm = 25.0) }
        val parado = media.kmPerLitre!!

        assertTrue("queimar sem andar tem que piorar a conta", parado < rodando)
    }

    @Test
    fun `a janela esquece o que ficou para tras`() {
        var media = ConsumptionAverage()
        // 25 km de cidade a 8 km/L…
        repeat(25) { media = media.update(1.0, 0.125, 25.0) }
        assertEquals(8.0, media.kmPerLitre!!, 1e-6)
        // …seguidos de 25 km de rodovia a 16 km/L. A cidade sai inteira.
        repeat(25) { media = media.update(1.0, 0.0625, 25.0) }
        assertEquals(16.0, media.kmPerLitre!!, 1e-6)
    }

    @Test
    fun `a janela nao cresce sem fim`() {
        var media = ConsumptionAverage()
        repeat(100_000) { media = media.update(0.01, 0.001, 25.0) }
        assertTrue("a janela guarda 25 trechos, não mil", media.buckets.size <= 25)
        assertTrue(media.windowKm <= 26.0)
    }
}
