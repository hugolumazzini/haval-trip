package br.com.hugolumazzini.havaltrip

import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.TelemetrySample
import br.com.hugolumazzini.havaltrip.domain.TripStatus
import br.com.hugolumazzini.havaltrip.engine.TripManager
import br.com.hugolumazzini.havaltrip.storage.InMemoryTripStorage
import br.com.hugolumazzini.havaltrip.storage.SnapshotPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Máquina de estados, ignição e independência entre os contadores. */
class TripManagerTest {

    private var agora = 0L
    private val storage = InMemoryTripStorage()
    private fun manager(policy: SnapshotPolicy = SnapshotPolicy()) =
        TripManager(storage = storage, policy = policy, clock = { agora })

    private var odometro = 10_000.0

    /** Dirige [segundos] a [velocidade], alimentando o manager amostra a amostra. */
    private fun TripManager.dirigir(
        segundos: Int,
        velocidade: Double,
        injecao: Double = 6.0,
        ignicao: IgnitionState = IgnitionState.ON,
    ) {
        repeat(segundos) {
            agora += 1000
            odometro += velocidade / 3600.0
            processTelemetry(
                TelemetrySample(agora, velocidade, injecao, odometro, 40.0, ignicao)
            )
        }
    }

    // ------------------------------------------------------------- independência

    @Test
    fun `zerar a Trip A nao encosta na Trip B`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.startTrip("A", "Trip A")
        m.startTrip("B", "Trip B")
        m.dirigir(3600, 60.0)

        val bAntes = m.state.value.trip("B")!!.metrics
        m.resetTrip("A")
        val bDepois = m.state.value.trip("B")!!.metrics

        assertEquals(0.0, m.state.value.trip("A")!!.metrics.distanceKm, 1e-9)
        assertEquals(bAntes, bDepois)
        assertEquals(60.0, bDepois.distanceKm, 1e-6)
    }

    @Test
    fun `pausar a Trip A nao para a Trip B`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.startTrip("A")
        m.startTrip("B")
        m.dirigir(600, 60.0)
        m.pauseTrip("A")
        m.dirigir(600, 60.0)

        val a = m.state.value.trip("A")!!.metrics
        val b = m.state.value.trip("B")!!.metrics
        assertEquals(10.0, a.distanceKm, 1e-6)
        assertEquals(20.0, b.distanceKm, 1e-6)
        assertEquals(TripStatus.PAUSED, m.state.value.trip("A")!!.status)
    }

    @Test
    fun `zerar a Trip A no meio da viagem nao mexe nos litros da Trip B`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.startTrip("A", "Trip A")
        m.startTrip("B", "Trip B")
        m.dirigir(3600, 60.0, injecao = 6.0)
        m.resetTrip("A")
        m.dirigir(3600, 60.0, injecao = 6.0)

        assertEquals(6.0, m.state.value.trip("A")!!.metrics.fuelLitres, 1e-6)
        assertEquals(12.0, m.state.value.trip("B")!!.metrics.fuelLitres, 1e-6)
    }

    @Test
    fun `todos os contadores contam sem ninguem inicia-los`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)

        // Ninguém tocou em A, B, C nem D. Todas contaram a mesma viagem.
        listOf("AUTO", "A", "B", "C", "D").forEach { id ->
            val trip = m.state.value.trip(id)!!
            assertEquals("contador $id", TripStatus.ACTIVE, trip.status)
            assertEquals("contador $id", 10.0, trip.metrics.distanceKm, 1e-6)
        }
    }

    @Test
    fun `so a pausa do motorista para um contador`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.pauseTrip("C")
        m.dirigir(600, 60.0)

        assertEquals(0.0, m.state.value.trip("C")!!.metrics.distanceKm, 1e-9)
        assertEquals(10.0, m.state.value.trip("D")!!.metrics.distanceKm, 1e-6)
    }

    @Test
    fun `uma Trip nova pode nascer sem tocar nas que ja existem`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.startTrip("A")
        m.dirigir(600, 60.0)
        m.startTrip("E", "Trip E")
        m.dirigir(600, 60.0)

        assertEquals(20.0, m.state.value.trip("A")!!.metrics.distanceKm, 1e-6)
        assertEquals(10.0, m.state.value.trip("E")!!.metrics.distanceKm, 1e-6)
        // As cinco de fábrica (a automática mais A–D) continuam lá, mais a E.
        assertEquals(6, m.state.value.trips.size)
    }

    // ------------------------------------------------------------- ignição

    @Test
    fun `ON para OFF para ON devolve a Trip ativa a contagem`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.startTrip("A")
        m.dirigir(600, 60.0)

        m.handleIgnitionChange(IgnitionState.OFF)
        assertEquals(TripStatus.STANDBY, m.state.value.trip("A")!!.status)

        m.handleIgnitionChange(IgnitionState.ON)
        assertEquals(TripStatus.ACTIVE, m.state.value.trip("A")!!.status)

        m.dirigir(600, 60.0)
        assertEquals(20.0, m.state.value.trip("A")!!.metrics.distanceKm, 1e-6)
    }

    @Test
    fun `Trip pausada continua pausada depois de desligar e ligar`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.startTrip("A")
        m.dirigir(600, 60.0)
        m.pauseTrip("A")

        m.handleIgnitionChange(IgnitionState.OFF)
        assertEquals(TripStatus.PAUSED, m.state.value.trip("A")!!.status)
        m.handleIgnitionChange(IgnitionState.ON)
        assertEquals(TripStatus.PAUSED, m.state.value.trip("A")!!.status)

        m.dirigir(600, 60.0)
        assertEquals(10.0, m.state.value.trip("A")!!.metrics.distanceKm, 1e-6)
    }

    @Test
    fun `carro desligado nao acumula tempo parado`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.startTrip("A")
        m.dirigir(300, 0.0)
        val paradoLigado = m.state.value.trip("A")!!.metrics.idleTimeS

        m.handleIgnitionChange(IgnitionState.OFF)
        m.dirigir(3600, 0.0, injecao = 0.0, ignicao = IgnitionState.OFF)

        assertEquals(paradoLigado, m.state.value.trip("A")!!.metrics.idleTimeS, 1e-9)
        assertEquals(300.0, paradoLigado, 1e-9)
    }

    @Test
    fun `as horas com o carro desligado nao viram tempo de viagem`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.startTrip("A")
        m.dirigir(600, 60.0)
        m.handleIgnitionChange(IgnitionState.OFF)

        agora += 8 * 3600 * 1000  // carro na garagem a noite toda
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)

        assertEquals(1200.0, m.state.value.trip("A")!!.metrics.totalTimeS, 1e-6)
    }

    @Test
    fun `amostra com ignicao diferente vira mudanca de estado sozinha`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.startTrip("A")
        m.dirigir(60, 60.0)
        // A central nem sempre recebe o evento: a amostra é a única pista.
        m.dirigir(60, 0.0, injecao = 0.0, ignicao = IgnitionState.OFF)
        assertEquals(TripStatus.STANDBY, m.state.value.trip("A")!!.status)
        assertEquals(IgnitionState.OFF, m.state.value.live.ignition)
    }

    @Test
    fun `Trip iniciada com o carro desligado espera em standby`() {
        val m = manager()
        m.startTrip("A")
        assertEquals(TripStatus.STANDBY, m.state.value.trip("A")!!.status)
        m.handleIgnitionChange(IgnitionState.ON)
        assertEquals(TripStatus.ACTIVE, m.state.value.trip("A")!!.status)
    }

    // ------------------------------------------------- Trip automática

    @Test
    fun `cinco minutos de chave fora zeram a Trip automatica`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)
        assertEquals(10.0, m.state.value.trip("AUTO")!!.metrics.distanceKm, 1e-6)

        m.handleIgnitionChange(IgnitionState.OFF)
        agora += 5 * 60 * 1000
        m.handleIgnitionChange(IgnitionState.ON)

        val auto = m.state.value.trip("AUTO")!!
        assertEquals(0.0, auto.metrics.distanceKm, 1e-9)
        assertEquals(0.0, auto.metrics.totalTimeS, 1e-9)
        // Continua contando: a viagem nova começa sozinha, sem apertar nada.
        assertEquals(TripStatus.ACTIVE, auto.status)
        m.dirigir(600, 60.0)
        assertEquals(10.0, m.state.value.trip("AUTO")!!.metrics.distanceKm, 1e-6)
    }

    @Test
    fun `parada curta no posto nao zera a Trip automatica`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)

        m.handleIgnitionChange(IgnitionState.OFF)
        agora += 4 * 60 * 1000 + 59_000  // um segundo abaixo do limite
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)

        assertEquals(20.0, m.state.value.trip("AUTO")!!.metrics.distanceKm, 1e-6)
    }

    @Test
    fun `a zeragem automatica arquiva a viagem em vez de apaga-la`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)
        m.handleIgnitionChange(IgnitionState.OFF)
        agora += 30 * 60 * 1000
        m.handleIgnitionChange(IgnitionState.ON)

        assertEquals(1, m.state.value.history.size)
        val registro = m.state.value.history.first()
        assertEquals("AUTO", registro.tripId)
        assertEquals(10.0, registro.metrics.distanceKm, 1e-6)
        assertTrue("arquivada sozinha, não pelo motorista", registro.automatic)
    }

    @Test
    fun `viagem sem distancia nao entope o historico`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        // Ligou, não saiu do lugar, desligou: não é viagem nenhuma.
        m.dirigir(20, 0.0)
        m.handleIgnitionChange(IgnitionState.OFF)
        agora += 30 * 60 * 1000
        m.handleIgnitionChange(IgnitionState.ON)

        assertTrue(m.state.value.history.isEmpty())
    }

    @Test
    fun `a zeragem automatica nao encosta nas Trips manuais`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.startTrip("A")
        m.dirigir(600, 60.0)

        m.handleIgnitionChange(IgnitionState.OFF)
        agora += 60 * 60 * 1000
        m.handleIgnitionChange(IgnitionState.ON)

        assertEquals(10.0, m.state.value.trip("A")!!.metrics.distanceKm, 1e-6)
        assertEquals(0.0, m.state.value.trip("AUTO")!!.metrics.distanceKm, 1e-9)
    }

    @Test
    fun `arquivar a Trip automatica na mao ja comeca a viagem seguinte`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)
        assertNotNull(m.saveToHistory("AUTO"))

        assertEquals(TripStatus.ACTIVE, m.state.value.trip("AUTO")!!.status)
        m.dirigir(600, 60.0)
        assertEquals(10.0, m.state.value.trip("AUTO")!!.metrics.distanceKm, 1e-6)
    }

    // ------------------------------------------------------------- histórico

    @Test
    fun `renomear uma viagem nao mexe no nome do contador`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)
        val registro = m.saveToHistory("A")!!

        assertTrue(m.renameRecord(registro.recordId, "  Praia de janeiro  "))
        assertEquals("Praia de janeiro", m.state.value.history.first().label)
        // O contador continua sendo a Trip A; foi a viagem que ganhou nome.
        assertEquals("Trip A", m.state.value.trip("A")!!.label)
    }

    @Test
    fun `nome em branco nao apaga o nome da viagem`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)
        val registro = m.saveToHistory("A")!!

        assertTrue(!m.renameRecord(registro.recordId, "   "))
        assertEquals("Trip A", m.state.value.history.first().label)
    }

    @Test
    fun `excluir tira a viagem do historico e deixa as outras`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)
        val primeira = m.saveToHistory("A")!!
        m.dirigir(600, 60.0)
        val segunda = m.saveToHistory("A")!!

        assertTrue(m.deleteRecord(primeira.recordId))
        assertEquals(listOf(segunda.recordId), m.state.value.history.map { it.recordId })
        // Apagar de novo não quebra nem apaga outra coisa.
        assertTrue(!m.deleteRecord(primeira.recordId))
        assertEquals(1, m.state.value.history.size)
    }

    @Test
    fun `arquivar guarda a viagem e ja recomeca a contagem`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(3600, 60.0)

        val registro = m.saveToHistory("A")
        assertNotNull(registro)
        assertEquals(60.0, registro!!.metrics.distanceKm, 1e-6)
        assertEquals(0.0, m.state.value.trip("A")!!.metrics.distanceKm, 1e-9)
        // Não para: a próxima viagem já está sendo contada.
        assertEquals(TripStatus.ACTIVE, m.state.value.trip("A")!!.status)
        m.dirigir(600, 60.0)
        assertEquals(10.0, m.state.value.trip("A")!!.metrics.distanceKm, 1e-6)
        assertEquals(1, m.state.value.history.size)
    }

    @Test
    fun `arquivar um contador pausado nao o tira da pausa`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)
        m.pauseTrip("A")
        assertNotNull(m.saveToHistory("A"))

        assertEquals(TripStatus.PAUSED, m.state.value.trip("A")!!.status)
        m.dirigir(600, 60.0)
        assertEquals(0.0, m.state.value.trip("A")!!.metrics.distanceKm, 1e-9)
    }

    @Test
    fun `o historico e uma copia congelada`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.startTrip("A")
        m.dirigir(600, 60.0)
        val registro = m.saveToHistory("A")!!

        m.startTrip("A")
        m.dirigir(600, 90.0)

        assertEquals(10.0, m.state.value.history.first().metrics.distanceKm, 1e-6)
        assertEquals(10.0, registro.metrics.distanceKm, 1e-6)
    }

    @Test
    fun `arquivar Trip vazia nao suja o historico`() {
        val m = manager()
        assertNull(m.saveToHistory("A"))
        assertNull(m.saveToHistory("nao-existe"))
        assertTrue(m.state.value.history.isEmpty())
    }

    // ------------------------------------------------------------- hodômetro

    @Test
    fun `o hodometro vitalicio e lido e nunca alterado pela Trip`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        val partida = odometro
        m.startTrip("A")
        m.dirigir(3600, 60.0)

        val trip = m.state.value.trip("A")!!
        // A partida é fixada na primeira amostra, que já vem com o carro
        // andando: a tolerância cobre esse primeiro segundo de deslocamento.
        assertEquals(partida, trip.odometerStartKm!!, 0.05)
        assertEquals(partida + 60.0, m.state.value.live.odometerTotalKm, 1e-3)
        // Zerar a Trip não mexe no hodômetro do carro.
        m.resetTrip("A")
        assertEquals(partida + 60.0, m.state.value.live.odometerTotalKm, 1e-3)
    }
}
