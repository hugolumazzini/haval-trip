package br.com.hugolumazzini.havaltrip.domain

import kotlinx.serialization.Serializable

/**
 * Ciclo de vida de um contador de viagem.
 *
 * A distinção entre PAUSED e STANDBY existe porque as duas param de contar pelo
 * mesmo motivo aparente, mas por causas diferentes: PAUSED é decisão do
 * motorista e sobrevive a ligar o carro de novo; STANDBY é consequência da
 * ignição desligada e some sozinho quando o carro volta a ligar. Misturar as
 * duas faria a Trip que o motorista pausou voltar a contar na próxima viagem.
 */
enum class TripStatus {
    /** Acumulando telemetria agora. */
    ACTIVE,

    /** Pausada pelo motorista. Só volta com [TripStatus.ACTIVE] por ação dele. */
    PAUSED,

    /** Iniciada, mas com a ignição desligada. Volta sozinha ao ligar o carro. */
    STANDBY,

    /**
     * Legado: contador desligado, de quando era preciso iniciá-los à mão.
     *
     * Nada mais coloca uma Trip aqui — todos os contadores contam desde que
     * nascem. O valor continua no enum só para os snapshots antigos ainda
     * abrirem; o [br.com.hugolumazzini.havaltrip.engine.TripManager] os promove
     * a [STANDBY] ao carregar.
     */
    INACTIVE,
}

/** Estado da ignição do veículo. */
enum class IgnitionState { ON, OFF }

/**
 * Amostra crua de telemetria, do jeito que chega do barramento do carro.
 *
 * @param timestampMs instante da leitura, em milissegundos desde a época.
 * @param speedKmh velocidade instantânea, em km/h.
 * @param fuelRateLph taxa de injeção instantânea, em litros por hora.
 * @param odometerTotalKm hodômetro vitalício do veículo, em km. Só cresce e
 *   nunca é escrito por este módulo — ver [Trip.odometerStartKm].
 * @param fuelLevelL combustível restante no tanque, em litros.
 * @param ignition estado da ignição no instante da leitura.
 */
@Serializable
data class TelemetrySample(
    val timestampMs: Long,
    val speedKmh: Double,
    val fuelRateLph: Double,
    val odometerTotalKm: Double,
    val fuelLevelL: Double,
    val ignition: IgnitionState,
)

/**
 * Números acumulados de uma Trip. Só guarda o que é somatório; tudo que é
 * divisão (média de consumo, velocidade média) é calculado na hora, para não
 * existirem duas verdades sobre o mesmo dado.
 */
@Serializable
data class TripMetrics(
    /** Distância percorrida pela Trip, em km. */
    val distanceKm: Double = 0.0,
    /** Segundos com velocidade >= [MOVING_THRESHOLD_KMH]. */
    val movingTimeS: Double = 0.0,
    /** Segundos parado com a ignição ligada — trânsito, semáforo, espera. */
    val idleTimeS: Double = 0.0,
    /** Litros queimados na Trip. */
    val fuelLitres: Double = 0.0,
    /** Maior velocidade vista na Trip, em km/h. */
    val maxSpeedKmh: Double = 0.0,
) {
    /** Tempo total da Trip com o carro ligado, em segundos. */
    val totalTimeS: Double get() = movingTimeS + idleTimeS

    /**
     * Consumo médio da Trip, em km/L. `null` enquanto não houver combustível
     * queimado — devolver 0.0 aqui mentiria dizendo "o carro não anda nada".
     */
    val avgFuelConsumptionKml: Double? get() =
        if (fuelLitres > EPSILON) distanceKm / fuelLitres else null

    /** Velocidade média, em km/h, contando o tempo parado. `null` sem tempo. */
    val avgSpeedKmh: Double? get() =
        if (totalTimeS > EPSILON) distanceKm / (totalTimeS / 3600.0) else null

    /**
     * Velocidade média só do tempo em movimento, em km/h. É a que responde
     * "quanto o carro corre quando corre", sem o peso dos semáforos.
     */
    val avgMovingSpeedKmh: Double? get() =
        if (movingTimeS > EPSILON) distanceKm / (movingTimeS / 3600.0) else null

    /** Fração do tempo total gasta parado, de 0.0 a 1.0. `null` sem tempo. */
    val idleRatio: Double? get() =
        if (totalTimeS > EPSILON) idleTimeS / totalTimeS else null

    companion object {
        /**
         * Abaixo disso o carro é considerado parado. 1 km/h e não 0 porque o
         * sensor de roda oscila em torno de zero com o carro imóvel, e esse
         * ruído viraria distância fantasma e "tempo em movimento" no semáforo.
         */
        const val MOVING_THRESHOLD_KMH = 1.0

        /** Piso para comparações com zero em ponto flutuante. */
        const val EPSILON = 1e-9
    }
}

/**
 * Um contador de viagem independente — a Viagem atual ou a Trip A, B, C, D do
 * painel. Conta desde que nasce; o motorista zera quando quiser.
 *
 * @param odometerStartKm hodômetro do veículo quando a Trip começou, e
 *   [odometerLastKm] na última amostra. Guardados para a tela de detalhes: o
 *   módulo lê o hodômetro, nunca o altera.
 */
@Serializable
data class Trip(
    val id: String,
    val label: String,
    val status: TripStatus = TripStatus.INACTIVE,
    val metrics: TripMetrics = TripMetrics(),
    /**
     * Segundos de ignição desligada que zeram esta Trip sozinha na próxima
     * partida. `null` — o padrão — é a Trip manual, que só o motorista zera.
     *
     * É o que separa "quanto andei nesta viagem" de "quanto andei neste mês":
     * a Trip automática responde à primeira pergunta sem ninguém apertar nada.
     */
    val autoResetAfterOffS: Double? = null,
    val startedAtMs: Long? = null,
    val endedAtMs: Long? = null,
    val odometerStartKm: Double? = null,
    val odometerLastKm: Double? = null,
) {
    /** Só uma Trip ACTIVE consome telemetria. */
    val isAccumulating: Boolean get() = status == TripStatus.ACTIVE

    /** `true` se esta Trip se zera sozinha entre uma viagem e outra. */
    val isAutomatic: Boolean get() = autoResetAfterOffS != null
}

/**
 * Uma Trip encerrada e arquivada. É uma cópia congelada: mexer na Trip viva
 * depois de salvar não reescreve o histórico.
 */
@Serializable
data class TripRecord(
    val recordId: String,
    val tripId: String,
    val label: String,
    val metrics: TripMetrics,
    val startedAtMs: Long?,
    val savedAtMs: Long,
    val odometerStartKm: Double?,
    val odometerEndKm: Double?,
    /**
     * `true` quando o arquivamento foi automático — a Trip automática fechando
     * a viagem anterior sozinha. Serve para a tela não misturar as duas coisas:
     * "eu arquivei isto" e "isto se arquivou".
     */
    val automatic: Boolean = false,
)

/**
 * Leitura ao vivo do veículo, comum a todas as Trips: não pertence a nenhum
 * contador porque não depende de qual está ativo.
 */
@Serializable
data class VehicleLive(
    val odometerTotalKm: Double = 0.0,
    val speedKmh: Double = 0.0,
    val fuelLevelL: Double = 0.0,
    val ignition: IgnitionState = IgnitionState.OFF,
    /** Consumo no momento, em km/L. `null` parado ou com injeção zerada. */
    val instantFuelConsumptionKml: Double? = null,
    /** Estimativa de km até o tanque esvaziar. `null` sem base de consumo. */
    val autonomyDteKm: Double? = null,
    val lastSampleMs: Long? = null,
)
