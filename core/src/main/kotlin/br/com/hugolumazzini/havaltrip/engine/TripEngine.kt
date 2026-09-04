package br.com.hugolumazzini.havaltrip.engine

import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.TelemetrySample
import br.com.hugolumazzini.havaltrip.domain.TripMetrics
import br.com.hugolumazzini.havaltrip.domain.TripMetrics.Companion.EPSILON
import br.com.hugolumazzini.havaltrip.domain.TripMetrics.Companion.MOVING_THRESHOLD_KMH
import br.com.hugolumazzini.havaltrip.domain.VehicleLive
import kotlin.math.max
import kotlin.math.min

/**
 * Ajustes do motor de cálculo. Ficam num objeto e não em constantes soltas
 * porque a bancada de testes precisa mexer neles sem esperar tempo real.
 */
data class EngineConfig(
    /** Intervalo nominal entre amostras, em segundos. */
    val sampleIntervalS: Double = 1.0,
    /**
     * Teto para o Δt calculado entre duas amostras, em segundos.
     *
     * Se o barramento engasgar por dois minutos, a amostra seguinte traria um
     * Δt gigante e o motor integraria a última velocidade lida por todo esse
     * buraco — inventando quilômetros que o carro não andou. Melhor descartar o
     * excedente do que registrar distância falsa.
     */
    val maxDeltaS: Double = 5.0,
    /**
     * Tamanho da janela, em km, usada para estimar a autonomia.
     *
     * 25 km é longo o bastante para uma subida de serra não derrubar o "até o
     * tanque acabar" pela metade, e curto o bastante para o número reagir
     * quando a viagem muda de caráter — sair da cidade e entrar na rodovia
     * precisa aparecer no painel antes de a rodovia acabar.
     */
    val dteWindowKm: Double = 25.0,
)

/**
 * Consumo dos últimos quilômetros rodados, usado só para estimar a autonomia.
 *
 * É uma janela e não uma média exponencial porque o que interessa é uma
 * pergunta concreta — "no ritmo dos últimos 25 km, quanto ainda dá?" — e a
 * janela responde exatamente isso somando quilômetros e litros de verdade.
 *
 * Os litros gastos parado entram na conta. Motor ligado no engarrafamento
 * queima combustível sem andar, e uma autonomia que ignora isso fica otimista
 * justamente na hora em que o motorista mais precisa dela honesta. Numa média
 * por amostra esses instantes teriam de ser descartados, porque litros
 * divididos por zero quilômetro não é número; aqui eles apenas engordam o
 * denominador da janela, que é o comportamento certo.
 */
@kotlinx.serialization.Serializable
data class ConsumptionAverage(
    /** Trechos já fechados, do mais antigo para o mais recente. */
    val buckets: List<ConsumptionBucket> = emptyList(),
    /** Trecho em formação, ainda sem [BUCKET_KM] completo. */
    val current: ConsumptionBucket = ConsumptionBucket(),
) {
    /**
     * Soma a contribuição de uma amostra.
     *
     * @param windowKm tamanho da janela, em km. Trechos mais antigos que isso
     *   saem pela outra ponta.
     */
    fun update(deltaKm: Double, deltaLitres: Double, windowKm: Double): ConsumptionAverage {
        if (deltaKm <= 0.0 && deltaLitres <= 0.0) return this

        val emFormacao = ConsumptionBucket(
            km = current.km + max(deltaKm, 0.0),
            litres = current.litres + max(deltaLitres, 0.0),
        )
        if (emFormacao.km < BUCKET_KM) return copy(current = emFormacao)

        // Fechou um quilômetro: entra na fila e os mais antigos caem fora.
        val fechados = buckets + emFormacao
        val maximo = max(1, (windowKm / BUCKET_KM).toInt())
        return ConsumptionAverage(
            buckets = fechados.takeLast(maximo),
            current = ConsumptionBucket(),
        )
    }

    /** Quilômetros dentro da janela, contando o trecho em formação. */
    val windowKm: Double get() = buckets.sumOf { it.km } + current.km

    /** Litros dentro da janela, contando o trecho em formação. */
    val windowLitres: Double get() = buckets.sumOf { it.litres } + current.litres

    /**
     * Consumo da janela em km/L, ou `null` sem base suficiente.
     *
     * Exige [MIN_KM] rodados antes de responder qualquer coisa. Os primeiros
     * metros são sempre a manobra de saída — carro frio, primeira marcha, dois
     * ou três km/L — e deixar isso virar a base da autonomia faria o número
     * nascer pela metade e depois só subir, parecendo defeito.
     */
    val kmPerLitre: Double? get() {
        val km = windowKm
        val litros = windowLitres
        if (km < MIN_KM || litros <= EPSILON) return null
        return km / litros
    }

    companion object {
        /** Granularidade da janela: cada trecho fechado vale um quilômetro. */
        const val BUCKET_KM = 1.0

        /** Distância mínima antes de a autonomia ter o que dizer. */
        const val MIN_KM = 0.5
    }
}

/** Um pedaço da janela: quanto se andou e quanto se queimou nele. */
@kotlinx.serialization.Serializable
data class ConsumptionBucket(
    val km: Double = 0.0,
    val litres: Double = 0.0,
)

/**
 * O motor de cálculo. Sem estado próprio e sem relógio: recebe o que tem e
 * devolve o resultado. Todo o estado vive no [TripManager], o que torna cada
 * regra aqui verificável com um único `assertEquals`.
 */
class TripEngine(val config: EngineConfig = EngineConfig()) {

    /**
     * Δt efetivo entre a amostra anterior e a atual, em segundos.
     *
     * Sem amostra anterior, usa o intervalo nominal. Nunca é negativo (relógio
     * do carro pode voltar ao sincronizar) nem maior que [EngineConfig.maxDeltaS].
     */
    fun deltaSeconds(previousMs: Long?, currentMs: Long): Double {
        if (previousMs == null) return config.sampleIntervalS
        val bruto = (currentMs - previousMs) / 1000.0
        return min(max(bruto, 0.0), config.maxDeltaS)
    }

    /**
     * Integra uma amostra nas métricas de uma Trip.
     *
     * Só deve ser chamado para Trip que está acumulando e com a ignição ligada
     * — a decisão de quem recebe o quê é do [TripManager].
     */
    fun accumulate(metrics: TripMetrics, sample: TelemetrySample, deltaS: Double): TripMetrics {
        if (deltaS <= 0.0) return metrics

        val emMovimento = sample.speedKmh >= MOVING_THRESHOLD_KMH
        // Δd = v · Δt, com a velocidade em km/h e o Δt em segundos.
        // Abaixo do limiar a leitura é ruído do sensor: não vira distância.
        val deltaKm = if (emMovimento) sample.speedKmh * (deltaS / 3600.0) else 0.0
        // Injeção em L/h pelo mesmo Δt. Conta com o carro parado também: motor
        // ligado no semáforo gasta combustível e isso tem que aparecer na média.
        val deltaLitros = max(sample.fuelRateLph, 0.0) * (deltaS / 3600.0)

        return metrics.copy(
            distanceKm = metrics.distanceKm + deltaKm,
            movingTimeS = if (emMovimento) metrics.movingTimeS + deltaS else metrics.movingTimeS,
            idleTimeS = if (emMovimento) metrics.idleTimeS else metrics.idleTimeS + deltaS,
            fuelLitres = metrics.fuelLitres + deltaLitros,
            maxSpeedKmh = max(metrics.maxSpeedKmh, sample.speedKmh),
        )
    }

    /**
     * Consumo instantâneo em km/L: velocidade dividida pela taxa de injeção,
     * as duas por hora, então a hora se cancela.
     *
     * `null` com o carro parado ou com injeção zerada (motor cortado na
     * desaceleração) — nesses instantes o valor real é 0 ou infinito, e nenhum
     * dos dois é informação útil no painel.
     */
    fun instantConsumptionKml(sample: TelemetrySample): Double? {
        if (sample.speedKmh < MOVING_THRESHOLD_KMH) return null
        if (sample.fuelRateLph <= EPSILON) return null
        return sample.speedKmh / sample.fuelRateLph
    }

    /**
     * Autonomia estimada: litros no tanque × consumo dos últimos quilômetros.
     *
     * Usa a janela, não o instantâneo, porque autonomia que oscila a cada
     * pisada no acelerador é ruído, não informação.
     */
    fun autonomyKm(fuelLevelL: Double, average: ConsumptionAverage): Double? {
        val kmPorLitro = average.kmPerLitre ?: return null
        // Zero litro é quase sempre "a bóia não respondeu", e não "o tanque
        // secou": o H6 publica `-1` em `remain_fuel_percentage` enquanto o
        // sensor não tem valor, e isso vira zero litro na conversão. Anunciar
        // "autonomia 0 km" nesse caso é o pior erro possível — assusta, e
        // assusta errado. Um traço diz a verdade: não sabemos. E um tanque que
        // seca de fato nunca chega a zero com o carro ainda andando.
        if (fuelLevelL <= 0.0) return null
        return fuelLevelL * kmPorLitro
    }

    /** Monta a leitura ao vivo do veículo a partir da amostra. */
    fun liveState(sample: TelemetrySample, average: ConsumptionAverage): VehicleLive = VehicleLive(
        odometerTotalKm = sample.odometerTotalKm,
        speedKmh = sample.speedKmh,
        fuelLevelL = sample.fuelLevelL,
        ignition = sample.ignition,
        instantFuelConsumptionKml = instantConsumptionKml(sample),
        autonomyDteKm = autonomyKm(sample.fuelLevelL, average),
        lastSampleMs = sample.timestampMs,
    )

    /** Contribuição de distância e litros de uma amostra, para a janela. */
    fun deltas(sample: TelemetrySample, deltaS: Double): Pair<Double, Double> {
        if (deltaS <= 0.0 || sample.ignition == IgnitionState.OFF) return 0.0 to 0.0
        val km = if (sample.speedKmh >= MOVING_THRESHOLD_KMH) sample.speedKmh * (deltaS / 3600.0) else 0.0
        val litros = max(sample.fuelRateLph, 0.0) * (deltaS / 3600.0)
        return km to litros
    }
}
