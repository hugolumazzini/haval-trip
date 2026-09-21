package br.com.hugolumazzini.havaltrip.services

import br.com.hugolumazzini.havaltrip.domain.TripRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

/**
 * Análise de consumo de combustível agrupado por período.
 *
 * @param period período analisado (ex: "dia", "mês")
 * @param label rótulo para exibição (data/período)
 * @param avgConsumptionKml consumo médio do período em km/L
 * @param distanceKm distância total do período em km
 * @param fuelLitres combustível queimado no período em litros
 */
data class ConsumptionDataPoint(
    val period: String,
    val label: String,
    val avgConsumptionKml: Double?,
    val distanceKm: Double,
    val fuelLitres: Double,
)

/**
 * Resultado da análise de consumo agrupado.
 *
 * @param dailyData consumo agregado por dia (últimos 30 dias)
 * @param monthlyData consumo agregado por mês (últimos 12 meses)
 * @param maxDailyConsumption consumo máximo entre os dias para escala do gráfico
 * @param maxMonthlyConsumption consumo máximo entre os meses para escala do gráfico
 */
data class ConsumptionAnalysisResult(
    val dailyData: List<ConsumptionDataPoint>,
    val monthlyData: List<ConsumptionDataPoint>,
    val maxDailyConsumption: Double,
    val maxMonthlyConsumption: Double,
)

/**
 * Agregação de consumo por período para análise histórica.
 *
 * Trabalha sobre [TripRecord] para garantir dados finalizados — não faz sentido
 * contar a Trip que ainda está andando na agregação diária.
 */
object ConsumptionAnalysis {

    /**
     * Agrupa o histórico de viagens por dia e mês.
     *
     * @param history lista de registros de viagem
     * @param zoneId fuso horário para agrupar as datas (padrão: sistema)
     * @return análise com dados agrupados prontos para visualização
     */
    fun analyzeConsumption(
        history: List<TripRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ConsumptionAnalysisResult {
        val dailyData = aggregateByDay(history, zoneId)
        val monthlyData = aggregateByMonth(history, zoneId)

        val maxDaily = dailyData.maxOfOrNull { it.avgConsumptionKml ?: 0.0 } ?: 0.0
        val maxMonthly = monthlyData.maxOfOrNull { it.avgConsumptionKml ?: 0.0 } ?: 0.0

        return ConsumptionAnalysisResult(
            dailyData = dailyData,
            monthlyData = monthlyData,
            maxDailyConsumption = max(maxDaily, 1.0),  // Garante escala mínima
            maxMonthlyConsumption = max(maxMonthly, 1.0),
        )
    }

    /**
     * Agrupa as viagens pelos últimos 30 dias.
     *
     * Se não há dados para um dia, esse dia não aparece na lista (gaps). Isso
     * deixa o gráfico mais legível — sem barras fantasmas de zero no meio.
     */
    private fun aggregateByDay(
        history: List<TripRecord>,
        zoneId: ZoneId,
    ): List<ConsumptionDataPoint> {
        val grouped = history
            .mapNotNull { record ->
                val savedAt = record.savedAtMs ?: return@mapNotNull null
                val instant = Instant.ofEpochMilli(savedAt)
                val date = instant.atZone(zoneId).toLocalDate()
                Pair(date, record)
            }
            .groupBy { it.first }
            .mapKeys { it.key }
            .mapValues { it.value.map { p -> p.second } }

        return grouped
            .entries
            .sortedBy { it.key }
            .takeLast(30)  // Últimos 30 dias
            .map { (date, records) ->
                val totalDistance = records.sumOf { it.metrics.distanceKm }
                val totalFuel = records.sumOf { it.metrics.fuelLitres }
                val avgConsumption = if (totalFuel > 1e-9 && totalDistance >= 0.1) {
                    totalDistance / totalFuel
                } else {
                    null
                }

                ConsumptionDataPoint(
                    period = date.toString(),
                    label = formatDayLabel(date),
                    avgConsumptionKml = avgConsumption?.coerceAtMost(30.0),  // Teto de 30 km/L
                    distanceKm = totalDistance,
                    fuelLitres = totalFuel,
                )
            }
    }

    /**
     * Agrupa as viagens pelos últimos 12 meses.
     *
     * Agrupa por ano-mês em ordem crescente.
     */
    private fun aggregateByMonth(
        history: List<TripRecord>,
        zoneId: ZoneId,
    ): List<ConsumptionDataPoint> {
        val grouped = history
            .mapNotNull { record ->
                val savedAt = record.savedAtMs ?: return@mapNotNull null
                val instant = Instant.ofEpochMilli(savedAt)
                val date = instant.atZone(zoneId).toLocalDate()
                // Agrupar por ano e mês: 2024-01, 2024-02, etc
                Pair(YearMonth(date.year, date.monthValue), record)
            }
            .groupBy { it.first }
            .mapKeys { it.key }
            .mapValues { it.value.map { p -> p.second } }

        return grouped
            .entries
            .sortedBy { it.key }
            .takeLast(12)  // Últimos 12 meses
            .map { (yearMonth, records) ->
                val totalDistance = records.sumOf { it.metrics.distanceKm }
                val totalFuel = records.sumOf { it.metrics.fuelLitres }
                val avgConsumption = if (totalFuel > 1e-9 && totalDistance >= 0.1) {
                    totalDistance / totalFuel
                } else {
                    null
                }

                ConsumptionDataPoint(
                    period = yearMonth.toString(),
                    label = formatMonthLabel(yearMonth),
                    avgConsumptionKml = avgConsumption?.coerceAtMost(30.0),
                    distanceKm = totalDistance,
                    fuelLitres = totalFuel,
                )
            }
    }

    /**
     * Formata a data de um dia para exibição.
     * Exemplo: "21" para o dia 21, "21/9" para data mais antiga.
     */
    private fun formatDayLabel(date: LocalDate): String {
        return "${date.dayOfMonth}"
    }

    /**
     * Formata o período de um mês para exibição.
     * Exemplo: "jan" para janeiro, "set" para setembro.
     */
    private fun formatMonthLabel(yearMonth: YearMonth): String {
        val monthNames = listOf(
            "jan", "fev", "mar", "abr", "mai", "jun",
            "jul", "ago", "set", "out", "nov", "dez"
        )
        return monthNames.getOrNull(yearMonth.month - 1) ?: "?"
    }

    /**
     * Classe auxiliar para agrupar por ano e mês.
     */
    private data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
        override fun toString(): String = "$year-${month.toString().padStart(2, '0')}"
        override fun compareTo(other: YearMonth): Int {
            return when {
                year != other.year -> year - other.year
                else -> month - other.month
            }
        }
    }
}
