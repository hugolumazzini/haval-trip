package br.com.hugolumazzini.havaltrip

import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.TelemetrySample
import br.com.hugolumazzini.havaltrip.engine.TripManager
import br.com.hugolumazzini.havaltrip.storage.FileTripStorage
import br.com.hugolumazzini.havaltrip.storage.GravacaoEmSegundoPlano
import br.com.hugolumazzini.havaltrip.storage.InMemoryTripStorage
import br.com.hugolumazzini.havaltrip.storage.TripSnapshot
import br.com.hugolumazzini.havaltrip.storage.TripStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** O que chega errado do carro, do disco ou do relógio não pode estragar as viagens. */
class RobustezTest {

    private var agora = 0L
    private val storage = InMemoryTripStorage()
    private fun manager(s: TripStorage = storage) = TripManager(storage = s, clock = { agora })

    private var odometro = 10_000.0

    private fun TripManager.dirigir(
        segundos: Int,
        velocidade: Double,
        ignicao: IgnitionState = IgnitionState.ON,
    ) {
        repeat(segundos) {
            agora += 1000
            odometro += velocidade / 3600.0
            processTelemetry(TelemetrySample(agora, velocidade, 6.0, odometro, 40.0, ignicao))
        }
    }

    private fun TripManager.amostra(
        velocidade: Double = 60.0,
        odometroKm: Double = odometro,
        injecao: Double = 6.0,
    ) {
        agora += 1000
        processTelemetry(TelemetrySample(agora, velocidade, injecao, odometroKm, 40.0, IgnitionState.ON))
    }

    private val auto get() = TripManager.ID_AUTOMATICA

    // ------------------------------------------------------------ valores do carro

    @Test
    fun `um NaN vindo do carro nao contamina as metricas`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)

        m.amostra(velocidade = Double.NaN, odometroKm = Double.NaN, injecao = Double.POSITIVE_INFINITY)
        m.dirigir(600, 60.0)

        val metricas = m.state.value.trip(auto)!!.metrics
        assertTrue(metricas.distanceKm.isFinite())
        assertTrue(metricas.fuelLitres.isFinite())
        assertEquals(20.0, metricas.distanceKm, 1e-6)
        assertTrue(m.state.value.live.odometerTotalKm.isFinite())
    }

    @Test
    fun `uma velocidade absurda nao soma quilometros`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(10, 60.0)
        val antes = m.state.value.trip(auto)!!.metrics.distanceKm

        // 0xFFFF: o "não sei" de alguns sensores.
        m.amostra(velocidade = 65_535.0)

        assertEquals(antes, m.state.value.trip(auto)!!.metrics.distanceKm, 1e-9)
    }

    @Test
    fun `um hodometro de nao-sei nao fica preso para sempre`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(60, 60.0)

        m.amostra(odometroKm = 16_777_215.0) // 0xFFFFFF
        m.dirigir(60, 60.0)

        assertEquals(odometro, m.state.value.live.odometerTotalKm, 0.01)
    }

    @Test
    fun `um hodometro errado gravado por versao antiga cede ao verdadeiro`() {
        // A versão antiga deixou gravado um hodômetro impossível de alcançar.
        storage.save(TripSnapshot(odometerTotalKm = 900_000.0, ignitionOffSinceMs = 0L))
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)

        m.dirigir(10, 60.0)

        assertEquals(odometro, m.state.value.live.odometerTotalKm, 0.01)
    }

    // ------------------------------------------------------------- hiato

    @Test
    fun `hodometro zero na volta do reinicio nao zera a viagem em andamento`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)
        m.flush()

        // A central ficou fora do ar dez minutos com o carro andando. Na volta,
        // a ignição chega antes do hodômetro, que vem como zero.
        agora += 10 * 60 * 1000
        odometro += 10.0
        val depois = manager()
        depois.amostra(odometroKm = 0.0)
        depois.dirigir(10, 60.0)

        assertTrue(depois.state.value.trip(auto)!!.metrics.distanceKm >= 10.0)
        assertTrue(depois.state.value.history.isEmpty())
    }

    @Test
    fun `chave fora depois de um corte de energia ainda zera na manha seguinte`() {
        val m = manager()
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)
        m.flush()

        // A central corta a energia com o app achando o carro ligado. Dois
        // minutos depois uma porta abre, a central acorda e a chave está fora.
        agora += 2 * 60 * 1000
        val depois = manager()
        depois.dirigir(40, 0.0, ignicao = IgnitionState.OFF)
        // Ainda dentro dos cinco minutos: nada foi zerado.
        assertTrue(depois.state.value.history.isEmpty())
        depois.flush()

        // A central dorme de novo e só volta de manhã, com a chave dentro.
        agora += 8 * 60 * 60 * 1000L
        val manha = manager()
        manha.dirigir(1, 0.0)

        assertEquals(1, manha.state.value.history.count { it.tripId == auto })
        assertEquals(0.0, manha.state.value.trip(auto)!!.metrics.distanceKm, 1e-9)
    }

    // ------------------------------------------------------------- gravação

    @Test
    fun `uma gravacao que falha nao derruba a contagem`() {
        val quebrado = object : TripStorage {
            override fun load(): TripSnapshot? = null
            override fun save(snapshot: TripSnapshot) = throw java.io.IOException("disco cheio")
        }
        val m = manager(quebrado)
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(120, 60.0)
        m.flush()

        assertEquals(2.0, m.state.value.trip(auto)!!.metrics.distanceKm, 1e-6)
        assertTrue(m.falhasAoGravar > 0)
    }

    @Test
    fun `arquivos ilegiveis sao guardados a parte em vez de sobrescritos`() {
        val pasta = Files.createTempDirectory("trips").toFile()
        File(pasta, FileTripStorage.NOME).writeText("{ isto não é json")
        File(pasta, "${FileTripStorage.NOME}.bak").writeText("nem isto")

        assertNull(FileTripStorage(pasta).load())

        val guardados = pasta.listFiles()!!.filter { it.name.contains(".corrompido-") }
        assertEquals(2, guardados.size)
        assertFalse(File(pasta, FileTripStorage.NOME).exists())
        pasta.deleteRecursively()
    }

    @Test
    fun `um valor de enum desconhecido nao invalida o arquivo`() {
        val pasta = Files.createTempDirectory("trips").toFile()
        val arquivo = FileTripStorage(pasta)
        arquivo.save(TripSnapshot(odometerTotalKm = 1234.0))
        val texto = File(pasta, FileTripStorage.NOME).readText()
        File(pasta, FileTripStorage.NOME).writeText(texto.replace("\"OFF\"", "\"ACESSORIOS\""))

        val lido = FileTripStorage(pasta).load()

        assertNotNull(lido)
        assertEquals(1234.0, lido!!.odometerTotalKm, 1e-9)
        pasta.deleteRecursively()
    }

    @Test
    fun `a gravacao em segundo plano chega ao disco com o ultimo estado`() {
        val pasta = Files.createTempDirectory("trips").toFile()
        val gravacao = GravacaoEmSegundoPlano(FileTripStorage(pasta))
        val m = manager(gravacao)
        m.handleIgnitionChange(IgnitionState.ON)
        m.dirigir(600, 60.0)
        m.flush()
        gravacao.aguardar()

        val lido = FileTripStorage(pasta).load()!!
        assertEquals(10.0, lido.trips.first { it.id == auto }.metrics.distanceKm, 1e-6)
        pasta.deleteRecursively()
    }
}
