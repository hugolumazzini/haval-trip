package br.com.hugolumazzini.havaltrip

import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.TelemetrySample
import br.com.hugolumazzini.havaltrip.domain.Trip
import br.com.hugolumazzini.havaltrip.domain.TripStatus
import br.com.hugolumazzini.havaltrip.engine.TripManager
import br.com.hugolumazzini.havaltrip.storage.FileTripStorage
import br.com.hugolumazzini.havaltrip.storage.TripSnapshot
import br.com.hugolumazzini.havaltrip.storage.InMemoryTripStorage
import br.com.hugolumazzini.havaltrip.storage.SnapshotPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Persistência atômica, política de gravação e restauração após corte. */
class TripStorageTest {

    @get:Rule val pasta = TemporaryFolder()

    private var agora = 0L

    private fun amostra(v: Double, odometro: Double, t: Long) =
        TelemetrySample(t, v, 6.0, odometro, 40.0, IgnitionState.ON)

    @Test
    fun `snapshot sobrevive ao corte de ignicao e volta na proxima partida`() {
        val destino = pasta.newFolder("dados")

        var odometro = 20_000.0
        val primeiro = TripManager(storage = FileTripStorage(destino), clock = { agora })
        primeiro.handleIgnitionChange(IgnitionState.ON)
        primeiro.startTrip("A", "Trip A")
        repeat(1800) {
            agora += 1000
            odometro += 60.0 / 3600.0
            primeiro.processTelemetry(amostra(60.0, odometro, agora))
        }
        primeiro.handleIgnitionChange(IgnitionState.OFF)

        // Nova instância: é o que acontece quando a central religa.
        val segundo = TripManager(storage = FileTripStorage(destino), clock = { agora })
        val restaurada = segundo.state.value.trip("A")!!
        assertEquals(30.0, restaurada.metrics.distanceKm, 1e-3)
        assertEquals(TripStatus.STANDBY, restaurada.status)
    }

    @Test
    fun `a contagem dos cinco minutos sobrevive a central perder energia`() {
        val destino = pasta.newFolder("dados")

        var odometro = 20_000.0
        val primeiro = TripManager(storage = FileTripStorage(destino), clock = { agora })
        primeiro.handleIgnitionChange(IgnitionState.ON)
        repeat(600) {
            agora += 1000
            odometro += 60.0 / 3600.0
            primeiro.processTelemetry(amostra(60.0, odometro, agora))
        }
        primeiro.handleIgnitionChange(IgnitionState.OFF)

        // Meia hora de garagem com a central sem energia: o processo morreu,
        // nenhum cronômetro ficou rodando. Só o carimbo gravado sabe disso.
        agora += 30 * 60 * 1000
        val segundo = TripManager(storage = FileTripStorage(destino), clock = { agora })
        assertEquals(10.0, segundo.state.value.trip("AUTO")!!.metrics.distanceKm, 1e-3)

        segundo.handleIgnitionChange(IgnitionState.ON)
        assertEquals(0.0, segundo.state.value.trip("AUTO")!!.metrics.distanceKm, 1e-9)
        assertEquals(1, segundo.state.value.history.size)
    }

    @Test
    fun `contador gravado como inativo volta contando`() {
        val destino = pasta.newFolder("dados")
        // Snapshot de quando era preciso iniciar cada Trip à mão: sem esta
        // promoção a Trip A ficaria parada para sempre, sem botão que a tirasse.
        FileTripStorage(destino).save(
            TripSnapshot(
                trips = listOf(Trip(id = "A", label = "Trip A", status = TripStatus.INACTIVE)),
                savedAtMs = agora,
            )
        )

        val manager = TripManager(storage = FileTripStorage(destino), clock = { agora })
        assertEquals(TripStatus.STANDBY, manager.state.value.trip("A")!!.status)
        manager.handleIgnitionChange(IgnitionState.ON)
        assertEquals(TripStatus.ACTIVE, manager.state.value.trip("A")!!.status)
    }

    @Test
    fun `arquivo principal corrompido cai na copia de seguranca`() {
        val destino = pasta.newFolder("dados")
        val storage = FileTripStorage(destino)

        val m = TripManager(storage = storage, clock = { agora })
        m.handleIgnitionChange(IgnitionState.ON)
        m.startTrip("A")
        agora += 1000
        m.processTelemetry(amostra(60.0, 100.0, agora))
        m.flush()
        // Segunda gravação: agora existe .bak com conteúdo íntegro.
        agora += 1000
        m.processTelemetry(amostra(60.0, 100.02, agora))
        m.flush()

        val principal = java.io.File(destino, FileTripStorage.NOME)
        val copia = java.io.File(destino, "${FileTripStorage.NOME}.bak")
        assertTrue("a cópia de segurança deveria existir", copia.exists())
        principal.writeText("{ isto não é json vál")

        val recuperado = FileTripStorage(destino).load()
        assertNotNull("deveria ter caído no .bak", recuperado)
        assertEquals(1, recuperado!!.trips.count { it.id == "A" })
    }

    @Test
    fun `sem arquivo nenhum o load devolve nulo em vez de estourar`() {
        assertNull(FileTripStorage(pasta.newFolder("vazia")).load())
    }

    @Test
    fun `a gravacao nao deixa temporario para tras`() {
        val destino = pasta.newFolder("dados")
        val m = TripManager(storage = FileTripStorage(destino), clock = { agora })
        m.startTrip("A")
        m.flush()
        assertTrue(destino.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `politica grava a cada quilometro`() {
        val policy = SnapshotPolicy(everyKm = 1.0, everyMs = Long.MAX_VALUE)
        policy.mark(0.0, 0L)
        assertTrue(!policy.shouldSave(0.4, 1_000L))
        assertTrue(policy.shouldSave(1.0, 1_000L))
        policy.mark(1.0, 1_000L)
        assertTrue(!policy.shouldSave(1.5, 2_000L))
        assertTrue(policy.shouldSave(2.2, 2_000L))
    }

    @Test
    fun `politica grava a cada cinco minutos de ignicao ligada`() {
        val policy = SnapshotPolicy(everyKm = Double.MAX_VALUE, everyMs = 5 * 60 * 1000L)
        policy.mark(0.0, 0L)
        assertTrue(!policy.shouldSave(0.0, 4 * 60 * 1000L))
        assertTrue(policy.shouldSave(0.0, 5 * 60 * 1000L))
    }

    @Test
    fun `criar mais contadores nao aumenta a escrita na flash`() {
        // Mesmo trajeto, contado por um só contador e depois por dez.
        fun gravacoesEm(quantosContadores: Int): Int {
            val storage = InMemoryTripStorage()
            val m = TripManager(
                storage = storage,
                policy = SnapshotPolicy(everyKm = 1.0, everyMs = Long.MAX_VALUE),
                clock = { agora },
                initialTrips = (1..quantosContadores).map { Trip(id = "T$it", label = "Trip $it") },
            )
            m.handleIgnitionChange(IgnitionState.ON)
            var odometro = 1_000.0
            val antes = storage.saveCount
            repeat(600) {  // 10 km a 60 km/h
                agora += 1000
                odometro += 60.0 / 3600.0
                m.processTelemetry(amostra(60.0, odometro, agora))
            }
            return storage.saveCount - antes
        }

        val comUm = gravacoesEm(1)
        val comDez = gravacoesEm(10)
        assertEquals("o gatilho é o hodômetro, não a soma dos contadores", comUm, comDez)
    }

    @Test
    fun `desligar a ignicao grava na hora, sem esperar a politica`() {
        val storage = InMemoryTripStorage()
        // Política que nunca dispararia sozinha dentro do teste.
        val m = TripManager(
            storage = storage,
            policy = SnapshotPolicy(everyKm = 1_000.0, everyMs = Long.MAX_VALUE),
            clock = { agora },
        )
        m.handleIgnitionChange(IgnitionState.ON)
        m.startTrip("A")
        val antes = storage.saveCount
        agora += 1000
        m.processTelemetry(amostra(60.0, 100.0, agora))
        assertEquals("uma amostra comum não deveria gravar", antes, storage.saveCount)

        m.handleIgnitionChange(IgnitionState.OFF)
        assertTrue("o corte de ignição tem que gravar", storage.saveCount > antes)
    }
}
