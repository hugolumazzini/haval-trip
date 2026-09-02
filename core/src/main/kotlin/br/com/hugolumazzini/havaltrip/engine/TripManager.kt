package br.com.hugolumazzini.havaltrip.engine

import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.TelemetrySample
import br.com.hugolumazzini.havaltrip.domain.Trip
import br.com.hugolumazzini.havaltrip.domain.TripMetrics
import br.com.hugolumazzini.havaltrip.domain.TripRecord
import br.com.hugolumazzini.havaltrip.domain.TripStatus
import br.com.hugolumazzini.havaltrip.domain.VehicleLive
import br.com.hugolumazzini.havaltrip.storage.InMemoryTripStorage
import br.com.hugolumazzini.havaltrip.storage.SnapshotPolicy
import br.com.hugolumazzini.havaltrip.storage.TripSnapshot
import br.com.hugolumazzini.havaltrip.storage.TripStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tudo que a interface precisa desenhar, num objeto imutável só. A tela nunca
 * lê o [TripManager] por dentro: recebe este retrato e desenha.
 */
data class TripState(
    val trips: List<Trip> = emptyList(),
    val history: List<TripRecord> = emptyList(),
    val live: VehicleLive = VehicleLive(),
    val selectedTripId: String? = null,
) {
    val selectedTrip: Trip? get() = trips.firstOrNull { it.id == selectedTripId } ?: trips.firstOrNull()
    fun trip(id: String): Trip? = trips.firstOrNull { it.id == id }
}

/**
 * Gerencia N contadores de viagem simultâneos e a máquina de estados da
 * ignição.
 *
 * Cada Trip tem seu próprio [TripMetrics] e seu próprio ciclo de vida: uma
 * amostra de telemetria é entregue a todas as que estão acumulando, e cada uma
 * a integra por conta própria. É isso que garante que zerar a Trip A no meio da
 * viagem não encoste na Trip B — não existe número compartilhado entre elas.
 *
 * @param clock fonte de tempo, injetável para os testes não dependerem do
 *   relógio de parede.
 */
class TripManager(
    private val storage: TripStorage = InMemoryTripStorage(),
    private val engine: TripEngine = TripEngine(),
    private val policy: SnapshotPolicy = SnapshotPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
    initialTrips: List<Trip> = DEFAULT_TRIPS,
) {
    private val _state = MutableStateFlow(TripState())
    val state: StateFlow<TripState> = _state.asStateFlow()

    private var trips: MutableList<Trip> = mutableListOf()
    private var history: MutableList<TripRecord> = mutableListOf()
    private var average = ConsumptionAverage()
    private var ignition: IgnitionState = IgnitionState.OFF
    private var odometerTotalKm: Double = 0.0

    /**
     * Falso até a primeira amostra chegar. Sem isto, uma Trip iniciada antes do
     * barramento responder gravaria hodômetro inicial zero e a tela de detalhes
     * mostraria a viagem começando na quilometragem 0 de um carro com 48 mil km.
     */
    private var odometerKnown: Boolean = false
    private var lastSampleMs: Long? = null

    /**
     * Quando a ignição foi desligada. Persistido no snapshot porque a central
     * perde energia junto com a chave: a contagem dos 5 minutos não pode
     * depender de um cronômetro rodando na memória, que morre com o app. O que
     * vale é a diferença entre este instante e o de religar.
     */
    private var ignitionOffSinceMs: Long? = null

    /**
     * O instante da última gravação, quando o app voltou sem ter visto a chave
     * sair.
     *
     * A central não avisa que vai desligar: ela corta a energia. Nesse caso o
     * app morre com a ignição ainda marcada como ligada, [ignitionOffSinceMs]
     * fica vazio e a zeragem automática nunca acontece — que é exatamente o que
     * o carro mostrou na prática. O que sobra como pista é a hora da última
     * gravação: o app estava vivo até ali, e o buraco entre aquele instante e a
     * primeira amostra de agora é candidato a "carro dormindo".
     *
     * Candidato, e não certeza, porque o app também morre quando o motorista
     * abre outro aplicativo no meio da viagem. Quem desempata é o hodômetro,
     * em [avaliarHiato].
     */
    private var hiatoDesdeMs: Long? = null

    /** Hodômetro da última gravação, régua do desempate de [hiatoDesdeMs]. */
    private var odometroNoHiatoKm: Double = 0.0

    private var live = VehicleLive()
    private var selectedTripId: String? = null

    init {
        val salvo = storage.load()
        if (salvo != null) {
            restaurar(salvo)
        } else {
            trips = initialTrips.toMutableList()
        }
        // Contadores criados depois da última gravação (novo padrão do app)
        // entram zerados, sem apagar os que já existiam.
        initialTrips.forEachIndexed { indice, molde ->
            if (trips.none { it.id == molde.id }) {
                trips.add(indice.coerceAtMost(trips.size), molde)
            }
        }
        selectedTripId = selectedTripId ?: trips.firstOrNull()?.id
        policy.mark(distanciaAcumulada(), clock())
        publicar()
    }

    // ---------------------------------------------------------------- ciclo de vida

    /**
     * Cria um contador (ou reinicia do zero um que já exista), já contando.
     *
     * Não existe "ligar" um contador: eles contam desde que nascem. Este método
     * é como um contador novo entra na lista, não um interruptor.
     *
     * @param autoResetAfterOffS zera esta Trip sozinha depois de tantos
     *   segundos de ignição desligada. `null` mantém o que a Trip já era: quem
     *   recria a Trip automática pelo painel não quer torná-la manual.
     */
    fun startTrip(tripId: String, label: String = tripId, autoResetAfterOffS: Double? = null) {
        val agora = clock()
        val existente = trips.firstOrNull { it.id == tripId }
        val nova = Trip(
            id = tripId,
            label = label,
            status = emContagem(existente?.status ?: TripStatus.STANDBY),
            metrics = TripMetrics(),
            autoResetAfterOffS = autoResetAfterOffS ?: existente?.autoResetAfterOffS,
            startedAtMs = agora,
            odometerStartKm = odometroConhecido(),
            odometerLastKm = odometroConhecido(),
        )
        val indice = trips.indexOfFirst { it.id == tripId }
        if (indice >= 0) trips[indice] = nova else trips.add(nova)
        salvarAgora(agora)
        publicar()
    }

    /** Pausa por decisão do motorista. Sobrevive a desligar e ligar o carro. */
    fun pauseTrip(tripId: String) = alterar(tripId) { trip ->
        if (trip.status == TripStatus.ACTIVE || trip.status == TripStatus.STANDBY) {
            trip.copy(status = TripStatus.PAUSED)
        } else {
            trip
        }
    }

    /** Retoma uma Trip pausada. Só volta a ACTIVE se o carro estiver ligado. */
    fun resumeTrip(tripId: String) = alterar(tripId) { trip ->
        if (trip.status == TripStatus.PAUSED) {
            trip.copy(status = if (ignition == IgnitionState.ON) TripStatus.ACTIVE else TripStatus.STANDBY)
        } else {
            trip
        }
    }

    /**
     * Zera os contadores mantendo o estado de contagem: zerar a Trip A em plena
     * viagem faz ela recomeçar do zero e continuar contando, que é o que o
     * motorista espera ao apertar "zerar" na estrada. Uma Trip pausada continua
     * pausada, só que em zero.
     */
    fun resetTrip(tripId: String) = alterar(tripId) { trip ->
        trip.copy(
            metrics = TripMetrics(),
            startedAtMs = clock(),
            endedAtMs = null,
            odometerStartKm = odometroConhecido(),
            odometerLastKm = odometroConhecido(),
        )
    }

    /**
     * Fecha a viagem no histórico e recomeça a contagem do zero, na hora.
     *
     * Nenhum contador para de contar por ter sido arquivado. Um contador de
     * viagem parado não vale nada, e a informação que ele deixa de registrar
     * não volta depois — o motorista descobriria a perda já em casa.
     *
     * O registro salvo é uma cópia congelada: continuar dirigindo depois disso
     * não reescreve o que já foi para o histórico.
     *
     * @return o registro criado, ou `null` se a Trip não existir ou estiver vazia.
     */
    fun saveToHistory(tripId: String): TripRecord? {
        val trip = trips.firstOrNull { it.id == tripId } ?: return null
        if (trip.metrics.totalTimeS <= TripMetrics.EPSILON && trip.metrics.distanceKm <= TripMetrics.EPSILON) {
            return null
        }
        val agora = clock()
        val registro = arquivar(trip, agora, automatico = false)
        history.add(registro)
        val indice = trips.indexOfFirst { it.id == tripId }
        // Uma Trip pausada arquiva e continua pausada: o motorista que parou a
        // contagem de propósito não pediu para ela voltar.
        trips[indice] = trip.copy(
            status = emContagem(trip.status),
            metrics = TripMetrics(),
            startedAtMs = agora,
            endedAtMs = agora,
            odometerStartKm = odometroConhecido(),
            odometerLastKm = odometroConhecido(),
        )
        salvarAgora(agora)
        publicar()
        return registro
    }

    // ---------------------------------------------------------------- histórico

    /**
     * Troca o nome de uma viagem já arquivada.
     *
     * Só o registro muda; o contador que a gerou continua com o nome dele. São
     * coisas diferentes: "Trip A" é o contador, "Praia de janeiro" é a viagem
     * que ele mediu uma vez. Sem isto o histórico vira uma pilha de "Trip A"
     * distinguíveis só pela data.
     *
     * @return `true` se o registro existia e foi renomeado.
     */
    fun renameRecord(recordId: String, label: String): Boolean {
        val novo = label.trim()
        if (novo.isEmpty()) return false
        val indice = history.indexOfFirst { it.recordId == recordId }
        if (indice < 0) return false
        history[indice] = history[indice].copy(label = novo)
        salvarAgora(clock())
        publicar()
        return true
    }

    /**
     * Apaga uma viagem do histórico. Não tem volta: grava na hora.
     *
     * @return `true` se o registro existia e foi apagado.
     */
    fun deleteRecord(recordId: String): Boolean {
        if (!history.removeIf { it.recordId == recordId }) return false
        salvarAgora(clock())
        publicar()
        return true
    }

    // ---------------------------------------------------------------- seleção

    /** Troca a Trip em foco no painel. Não afeta contagem nenhuma. */
    fun selectTrip(tripId: String) {
        if (trips.any { it.id == tripId }) {
            selectedTripId = tripId
            publicar()
        }
    }

    // ---------------------------------------------------------------- telemetria

    /**
     * Integra uma amostra em todas as Trips que estão acumulando.
     *
     * Amostra fora de ordem (relógio do carro voltando) é descartada: aceitar
     * Δt negativo faria as métricas andarem para trás.
     */
    fun processTelemetry(sample: TelemetrySample) {
        val anterior = lastSampleMs
        if (anterior != null && sample.timestampMs < anterior) return

        avaliarHiato(sample)

        if (sample.ignition != ignition) {
            handleIgnitionChange(sample.ignition, sample.timestampMs)
        }

        val deltaS = engine.deltaSeconds(anterior, sample.timestampMs)
        lastSampleMs = sample.timestampMs
        odometerTotalKm = maxOf(odometerTotalKm, sample.odometerTotalKm)
        odometerKnown = true

        if (ignition == IgnitionState.ON) {
            trips = trips.map { trip ->
                if (!trip.isAccumulating) trip
                else trip.copy(
                    metrics = engine.accumulate(trip.metrics, sample, deltaS),
                    // Trip iniciada antes da primeira amostra fixa aqui a
                    // quilometragem de partida — só na primeira vez.
                    odometerStartKm = trip.odometerStartKm ?: sample.odometerTotalKm,
                    startedAtMs = trip.startedAtMs ?: sample.timestampMs,
                    odometerLastKm = sample.odometerTotalKm,
                )
            }.toMutableList()

            val (km, litros) = engine.deltas(sample, deltaS)
            average = average.update(km, litros, engine.config.dteWindowKm)
        }

        live = engine.liveState(sample, average)

        if (policy.shouldSave(distanciaAcumulada(), sample.timestampMs)) {
            salvarAgora(sample.timestampMs)
        }
        publicar()
    }

    /**
     * Trata a virada da chave.
     *
     * OFF: as Trips que estavam contando vão para STANDBY e o snapshot é
     * gravado na hora — a central pode perder energia no segundo seguinte, e o
     * que não estiver no disco agora não existe mais. As pausadas continuam
     * pausadas, porque quem pausou foi o motorista.
     *
     * ON: as que estavam em STANDBY voltam a contar sozinhas. O [lastSampleMs]
     * é esquecido para que o buraco de horas com o carro desligado não vire Δt
     * na primeira amostra da viagem seguinte.
     */
    fun handleIgnitionChange(state: IgnitionState, atMs: Long = clock()) {
        if (state == ignition) return
        ignition = state

        // Antes de promover STANDBY a ACTIVE: a Trip automática que passou do
        // tempo desligada fecha a viagem anterior e recomeça do zero.
        if (state == IgnitionState.ON) aplicarZeragemAutomatica(atMs)

        trips = when (state) {
            IgnitionState.OFF -> trips.map {
                if (it.status == TripStatus.ACTIVE) it.copy(status = TripStatus.STANDBY) else it
            }
            IgnitionState.ON -> trips.map {
                if (it.status == TripStatus.STANDBY) it.copy(status = TripStatus.ACTIVE) else it
            }
        }.toMutableList()

        if (state == IgnitionState.OFF) {
            ignitionOffSinceMs = atMs
            lastSampleMs = null
            live = live.copy(ignition = state, speedKmh = 0.0, instantFuelConsumptionKml = null)
            salvarAgora(atMs)
        } else {
            ignitionOffSinceMs = null
            lastSampleMs = null
            live = live.copy(ignition = state)
        }
        publicar()
    }

    /** Força uma gravação — útil ao sair do app ou em teste. */
    fun flush() = salvarAgora(clock())

    // ---------------------------------------------------------------- internos

    private fun alterar(tripId: String, bloco: (Trip) -> Trip) {
        val indice = trips.indexOfFirst { it.id == tripId }
        if (indice < 0) return
        trips[indice] = bloco(trips[indice])
        salvarAgora(clock())
        publicar()
    }

    /**
     * Zera as Trips automáticas que ficaram tempo demais com a chave fora.
     *
     * A viagem anterior é arquivada antes de sumir. Perder o percurso de ontem
     * porque o carro dormiu na garagem seria o pior comportamento possível
     * para um contador que se apaga sozinho — e é justamente o histórico que dá
     * sentido a ele: cada viagem vira um registro comparável.
     */
    /**
     * Decide, na primeira amostra depois de o app voltar, se o buraco desde a
     * última gravação foi o carro dormindo.
     *
     * O hodômetro é o juiz: se o carro andou nesse meio-tempo, o app é que
     * esteve fora do ar — o motorista trocou de aplicativo, atendeu o telefone,
     * e a viagem continuou. Zerar aí apagaria uma viagem em andamento, que é o
     * pior erro possível. Se o hodômetro está no mesmo lugar depois de todo esse
     * tempo, o carro estava parado, e é a mesma situação que a chave fora.
     */
    private fun avaliarHiato(sample: TelemetrySample) {
        val desde = hiatoDesdeMs ?: return
        hiatoDesdeMs = null

        val paradoS = (sample.timestampMs - desde) / 1000.0
        if (paradoS <= 0.0) return
        val andouKm = sample.odometerTotalKm - odometroNoHiatoKm
        if (andouKm > HIATO_TOLERANCIA_KM) return

        // A Trip que recomeça precisa marcar a quilometragem de agora, não a de
        // ontem, então o hodômetro entra antes.
        odometerTotalKm = maxOf(odometerTotalKm, sample.odometerTotalKm)
        odometerKnown = true

        // A partir daqui é indistinguível de ter visto a chave sair naquele
        // instante — então é isso que o resto do código passa a enxergar.
        ignitionOffSinceMs = desde
        aplicarZeragemAutomatica(sample.timestampMs)
        ignitionOffSinceMs = null
    }

    private fun aplicarZeragemAutomatica(atMs: Long) {
        val desligadaDesde = ignitionOffSinceMs ?: return
        val paradaS = (atMs - desligadaDesde) / 1000.0

        trips = trips.map { trip ->
            val limite = trip.autoResetAfterOffS
            if (limite == null || paradaS < limite) return@map trip
            if (trip.status == TripStatus.INACTIVE) return@map trip

            if (trip.metrics.distanceKm > TripMetrics.EPSILON) {
                history.add(arquivar(trip, atMs, automatico = true))
            }
            trip.copy(
                metrics = TripMetrics(),
                startedAtMs = atMs,
                endedAtMs = null,
                odometerStartKm = odometroConhecido(),
                odometerLastKm = odometroConhecido(),
            )
        }.toMutableList()
    }

    /**
     * O estado de contagem que um contador deve ter agora.
     *
     * Respeita a pausa do motorista, que é a única razão legítima para um
     * contador estar parado, e deixa a ignição decidir o resto.
     */
    private fun emContagem(atual: TripStatus): TripStatus = when {
        atual == TripStatus.PAUSED -> TripStatus.PAUSED
        ignition == IgnitionState.ON -> TripStatus.ACTIVE
        else -> TripStatus.STANDBY
    }

    /** Monta o registro congelado de uma Trip. Não mexe em nada. */
    private fun arquivar(trip: Trip, atMs: Long, automatico: Boolean) = TripRecord(
        recordId = "${trip.id}-$atMs",
        tripId = trip.id,
        label = trip.label,
        metrics = trip.metrics,
        startedAtMs = trip.startedAtMs,
        savedAtMs = atMs,
        odometerStartKm = trip.odometerStartKm,
        odometerEndKm = trip.odometerLastKm ?: odometroConhecido(),
        automatic = automatico,
    )

    private fun snapshot(atMs: Long) = TripSnapshot(
        trips = trips.toList(),
        history = history.toList(),
        consumptionAverage = average,
        ignition = ignition,
        odometerTotalKm = odometerTotalKm,
        lastSampleMs = lastSampleMs,
        ignitionOffSinceMs = ignitionOffSinceMs,
        savedAtMs = atMs,
    )

    private fun salvarAgora(atMs: Long) {
        storage.save(snapshot(atMs))
        policy.mark(distanciaAcumulada(), atMs)
    }

    private fun restaurar(salvo: TripSnapshot) {
        // Contador gravado como inativo vem de antes de todos contarem
        // sozinhos. Como INACTIVE deixou de ser alcançável, ele ficaria parado
        // para sempre, sem botão que o tirasse de lá: entra em espera.
        trips = salvo.trips
            .map { if (it.status == TripStatus.INACTIVE) it.copy(status = TripStatus.STANDBY) else it }
            .toMutableList()
        history = salvo.history.toMutableList()
        average = salvo.consumptionAverage
        ignition = salvo.ignition
        odometerTotalKm = salvo.odometerTotalKm
        odometerKnown = salvo.odometerTotalKm > 0.0
        ignitionOffSinceMs = salvo.ignitionOffSinceMs
        // Se a chave saiu direito, o instante já está gravado e não há hiato a
        // investigar. O caso interessante é o contrário: o app foi desligado no
        // tapa, ainda achando que o carro estava ligado.
        if (salvo.ignitionOffSinceMs == null && salvo.ignition == IgnitionState.ON) {
            hiatoDesdeMs = salvo.savedAtMs
            odometroNoHiatoKm = salvo.odometerTotalKm
        }
        // O último instante lido não é restaurado de propósito: entre gravar e
        // religar podem ter passado horas, e esse buraco não é tempo de viagem.
        lastSampleMs = null
        live = VehicleLive(odometerTotalKm = salvo.odometerTotalKm, ignition = salvo.ignition)
    }

    /** Hodômetro atual, ou `null` enquanto nenhuma amostra tiver chegado. */
    private fun odometroConhecido(): Double? = if (odometerKnown) odometerTotalKm else null

    /**
     * Régua de distância do gatilho de gravação: o hodômetro do veículo.
     *
     * Somar a distância das Trips seria tentador e estaria errado: com cinco
     * contadores rodando ao mesmo tempo, a soma cresce cinco vezes mais rápido
     * que o carro, e o "grava a cada 1 km" viraria "grava a cada 200 m" —
     * cinco vezes mais escrita na flash da central para o mesmo trajeto. Criar
     * um sexto contador pioraria de novo. O hodômetro anda um quilômetro por
     * quilômetro rodado, não importa quantos contadores existam.
     */
    private fun distanciaAcumulada() = odometerTotalKm

    private fun publicar() {
        _state.value = TripState(
            trips = trips.toList(),
            history = history.toList(),
            live = live,
            selectedTripId = selectedTripId,
        )
    }

    companion object {
        /**
         * Cinco minutos de chave fora fecham a viagem da Trip automática.
         *
         * O número separa "parei para abastecer" de "cheguei". Um posto, uma
         * padaria, um portão de garagem levam menos que isso; se o carro ficou
         * mais tempo desligado, o trecho seguinte é outra viagem.
         */
        const val AUTO_RESET_PADRAO_S = 5 * 60.0

        /**
         * Quanto o hodômetro pode ter andado durante o hiato e a parada ainda
         * contar como carro dormindo.
         *
         * Não é zero porque o hodômetro do carro tem passo grosso e a última
         * gravação acontece a cada quilômetro: a viagem pode ter terminado
         * algumas centenas de metros depois do último snapshot, com o carro
         * chegando na garagem. Um quilômetro é curto demais para ser "o app
         * ficou fora do ar enquanto eu dirigia".
         */
        const val HIATO_TOLERANCIA_KM = 1.0

        /** Os contadores que o painel mostra por padrão. */
        val DEFAULT_TRIPS = listOf(
            // Todos nascem em espera, nenhum inativo: um contador de viagem
            // que precisa ser ligado é um contador que o motorista vai
            // esquecer de ligar, e aí a viagem já passou. STANDBY vira ACTIVE
            // sozinho na primeira vez que o carro liga.
            Trip(
                id = "AUTO",
                label = "Viagem atual",
                status = TripStatus.STANDBY,
                autoResetAfterOffS = AUTO_RESET_PADRAO_S,
            ),
            Trip(id = "A", label = "Trip A", status = TripStatus.STANDBY),
            Trip(id = "B", label = "Trip B", status = TripStatus.STANDBY),
            Trip(id = "C", label = "Trip C", status = TripStatus.STANDBY),
            Trip(id = "D", label = "Trip D", status = TripStatus.STANDBY),
        )
    }
}
