package br.com.hugolumazzini.havaltrip.services

import br.com.hugolumazzini.havaltrip.domain.TripMetrics.Companion.EPSILON
import br.com.hugolumazzini.havaltrip.domain.TripRecord
import kotlin.math.abs

/**
 * Uma linha de comparação entre duas viagens.
 *
 * @param deltaPercent variação de [b] em relação a [a], em pontos percentuais.
 *   `null` quando a base é zero ou ausente — não existe "x% a mais que nada".
 * @param betterIsHigher se, nesta métrica, um valor maior é o melhor resultado.
 */
data class ComparisonLine(
    val label: String,
    val unit: String,
    val a: Double?,
    val b: Double?,
    val delta: Double?,
    val deltaPercent: Double?,
    val betterIsHigher: Boolean,
) {
    /**
     * Qual das duas ganhou: `1` para a A, `2` para a B, `0` para empate
     * prático, `null` quando falta dado dos dois lados.
     */
    val winner: Int? get() {
        val d = delta ?: return null
        if (abs(d) <= EPSILON) return 0
        val bMaior = d > 0
        return if (bMaior == betterIsHigher) 2 else 1
    }
}

/** Resultado completo da comparação entre duas viagens do histórico. */
data class TripComparisonResult(
    val a: TripRecord,
    val b: TripRecord,
    val lines: List<ComparisonLine>,
) {
    fun line(label: String): ComparisonLine? = lines.firstOrNull { it.label == label }
}

/**
 * Compara duas viagens já arquivadas.
 *
 * Trabalha sobre [TripRecord] e não sobre Trips vivas de propósito: comparar
 * com um contador que ainda está andando dá um resultado que muda a cada
 * segundo e não significa nada.
 *
 * Sobre a normalização: [LABEL_COMBUSTIVEL] é o gasto bruto em litros e sozinho
 * premiaria a viagem mais curta — queimar menos porque se andou menos não é
 * economia. Por isso ele vem acompanhado de [LABEL_CONSUMO] (km/L) e de
 * [LABEL_PROPORCAO_PARADO] (%), que independem do tamanho do percurso.
 */
object TripComparison {

    const val LABEL_CONSUMO = "Consumo médio"
    const val LABEL_TEMPO_PARADO = "Tempo parado"
    const val LABEL_COMBUSTIVEL = "Combustível"
    const val LABEL_DISTANCIA = "Distância"
    const val LABEL_VELOCIDADE = "Velocidade média"
    const val LABEL_PROPORCAO_PARADO = "Proporção parado"

    fun compare(a: TripRecord, b: TripRecord): TripComparisonResult {
        val linhas = listOf(
            linha(LABEL_DISTANCIA, "km", a.metrics.distanceKm, b.metrics.distanceKm, betterIsHigher = true),
            linha(LABEL_CONSUMO, "km/L", a.metrics.avgFuelConsumptionKml, b.metrics.avgFuelConsumptionKml, betterIsHigher = true),
            linha(LABEL_VELOCIDADE, "km/h", a.metrics.avgSpeedKmh, b.metrics.avgSpeedKmh, betterIsHigher = true),
            linha(LABEL_TEMPO_PARADO, "s", a.metrics.idleTimeS, b.metrics.idleTimeS, betterIsHigher = false),
            linha(LABEL_PROPORCAO_PARADO, "%", a.metrics.idleRatio?.times(100), b.metrics.idleRatio?.times(100), betterIsHigher = false),
            linha(LABEL_COMBUSTIVEL, "L", a.metrics.fuelLitres, b.metrics.fuelLitres, betterIsHigher = false),
        )
        return TripComparisonResult(a, b, linhas)
    }

    private fun linha(
        label: String,
        unit: String,
        a: Double?,
        b: Double?,
        betterIsHigher: Boolean,
    ): ComparisonLine {
        val delta = if (a != null && b != null) b - a else null
        val percentual = if (a != null && b != null && abs(a) > EPSILON) (b - a) / abs(a) * 100.0 else null
        return ComparisonLine(label, unit, a, b, delta, percentual, betterIsHigher)
    }
}
