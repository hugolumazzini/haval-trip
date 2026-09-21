package br.com.hugolumazzini.havaltrip.services

import br.com.hugolumazzini.havaltrip.domain.TripRecord
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth as JavaYearMonth
import java.time.ZoneId
import kotlin.math.max

/**
 * Ponto de dados para contagem de viagens.
 *
 * @param period período analisado (ex: "dia", "semana", "mês")
 * @param label rótulo para exibição (data/período)
 * @param count número de viagens neste período
 * @param totalDistanceKm distância total do período em km
 */
data class TripsDataPoint(
    val period: String,
    val label: String,
    val count: Int,
    val totalDistanceKm: Double,
)

/**
 * Resultado da análise de viagens agrupado.
 *
 * @param dailyData contagem de viagens por dia (últimos 30 dias)
 * @param weeklyData contagem de viagens por semana (últimas 12 semanas)
 * @param monthlyData contagem de viagens por mês (últimos 12 meses)
 * @param maxDailyCount máximo de viagens num dia para escala do gráfico
 * @param maxWeeklyCount máximo de viagens numa semana para escala do gráfico
 * @param maxMonthlyCount máximo de viagens num mês para escala do gráfico
 */
data class TripsAnalysisResult(
    val dailyData: List<TripsDataPoint>,
    val weeklyData: List<TripsDataPoint>,
    val monthlyData: List<TripsDataPoint>,
    val maxDailyCount: Int,
    val maxWeeklyCount: Int,
    val maxMonthlyCount: Int,
)

/**
 * Agregação de viagens por período para análise histórica.
 */
object TripsAnalysis {

    /**
     * Agrupa o histórico de viagens por dia, semana e mês.
     *
     * @param history lista de registros de viagem
     * @param zoneId fuso horário para agrupar as datas (padrão: sistema)
     * @return análise com dados agrupados prontos para visualização
     */
    fun analyzeTrips(
        history: List<TripRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): TripsAnalysisResult {
        val dailyData = aggregateByDay(history, zoneId)
        val weeklyData = aggregateByWeek(history, zoneId)
        val monthlyData = aggregateByMonth(history, zoneId)

        val maxDaily = dailyData.maxOfOrNull { it.count } ?: 0
        val maxWeekly = weeklyData.maxOfOrNull { it.count } ?: 0
        val maxMonthly = monthlyData.maxOfOrNull { it.count } ?: 0

        return TripsAnalysisResult(
            dailyData = dailyData,
            weeklyData = weeklyData,
            monthlyData = monthlyData,
            maxDailyCount = max(maxDaily, 1),
            maxWeeklyCount = max(maxWeekly, 1),
            maxMonthlyCount = max(maxMonthly, 1),
        )
    }

    /**
     * Agrupa as viagens pelos últimos 30 dias.
     */
    private fun aggregateByDay(
        history: List<TripRecord>,
        zoneId: ZoneId,
    ): List<TripsDataPoint> {
        val grouped = history
            .mapNotNull { record ->
                val savedAt = record.savedAtMs ?: return@mapNotNull null
                val instant = Instant.ofEpochMilli(savedAt)
                val date = instant.atZone(zoneId).toLocalDate()
                Pair(date, record)
            }
            .groupBy { it.first }
            .mapValues { entry -> entry.value.map { it.second } }

        return grouped
            .entries
            .sortedBy { it.key }
            .takeLast(30)
            .map { entry ->
                val date = entry.key
                val records = entry.value
                TripsDataPoint(
                    period = date.toString(),
                    label = formatDayLabel(date),
                    count = records.size,
                    totalDistanceKm = records.fold(0.0) { acc, record -> acc + record.metrics.distanceKm },
                )
            }
    }

    /**
     * Agrupa as viagens pelas últimas 12 semanas.
     * Uma semana começa numa segunda-feira.
     */
    private fun aggregateByWeek(
        history: List<TripRecord>,
        zoneId: ZoneId,
    ): List<TripsDataPoint> {
        val grouped = history
            .mapNotNull { record ->
                val savedAt = record.savedAtMs ?: return@mapNotNull null
                val instant = Instant.ofEpochMilli(savedAt)
                val date = instant.atZone(zoneId).toLocalDate()
                // Semana ISO: começa numa segunda-feira
                val weekKey = "${date.year}-W${String.format("%02d", date.weekOfYear())}"
                Pair(weekKey, record)
            }
            .groupBy { it.first }
            .mapValues { entry -> entry.value.map { it.second } }

        return grouped
            .entries
            .sortedBy { it.key }
            .takeLast(12)
            .map { entry ->
                val weekKey = entry.key
                val records = entry.value
                TripsDataPoint(
                    period = weekKey,
                    label = formatWeekLabel(weekKey),
                    count = records.size,
                    totalDistanceKm = records.fold(0.0) { acc, record -> acc + record.metrics.distanceKm },
                )
            }
    }

    /**
     * Agrupa as viagens pelos últimos 12 meses.
     */
    private fun aggregateByMonth(
        history: List<TripRecord>,
        zoneId: ZoneId,
    ): List<TripsDataPoint> {
        val grouped = history
            .mapNotNull { record ->
                val savedAt = record.savedAtMs ?: return@mapNotNull null
                val instant = Instant.ofEpochMilli(savedAt)
                val date = instant.atZone(zoneId).toLocalDate()
                val yearMonth = JavaYearMonth.of(date.year, date.monthValue)
                Pair(yearMonth, record)
            }
            .groupBy { it.first }
            .mapValues { entry -> entry.value.map { it.second } }

        return grouped
            .entries
            .sortedBy { it.key }
            .takeLast(12)
            .map { entry ->
                val yearMonth = entry.key
                val records = entry.value
                TripsDataPoint(
                    period = yearMonth.toString(),
                    label = formatMonthLabel(yearMonth),
                    count = records.size,
                    totalDistanceKm = records.fold(0.0) { acc, record -> acc + record.metrics.distanceKm },
                )
            }
    }

    /**
     * Formata a data de um dia para exibição.
     * Exemplo: "21"
     */
    private fun formatDayLabel(date: LocalDate): String {
        return "${date.dayOfMonth}"
    }

    /**
     * Formata a semana para exibição.
     * Exemplo: "S42" para semana 42
     */
    private fun formatWeekLabel(weekKey: String): String {
        val parts = weekKey.split("-W")
        return if (parts.size == 2) "S${parts[1]}" else weekKey
    }

    /**
     * Formata o período de um mês para exibição.
     * Exemplo: "jan" para janeiro
     */
    private fun formatMonthLabel(yearMonth: JavaYearMonth): String {
        val monthNames = listOf(
            "jan", "fev", "mar", "abr", "mai", "jun",
            "jul", "ago", "set", "out", "nov", "dez"
        )
        return monthNames.getOrNull(yearMonth.monthValue - 1) ?: "?"
    }
}

/**
 * Extensão para obter o número da semana ISO.
 */
private fun LocalDate.weekOfYear(): Int {
    return java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR.getFrom(this).toInt()
}
