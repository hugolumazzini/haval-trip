package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.hugolumazzini.havaltrip.services.TripsAnalysisResult
import br.com.hugolumazzini.havaltrip.services.TripsDataPoint
import br.com.hugolumazzini.havaltrip.ui.theme.Cores
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloRotulo

/**
 * Gráfico de contagem de viagens por dia, semana e mês.
 *
 * Mostra três gráficos de barras lado a lado:
 * - Esquerda: últimos 30 dias
 * - Centro: últimas 12 semanas
 * - Direita: últimos 12 meses
 */
@Composable
fun GraficoDeViagens(analise: TripsAnalysisResult) {
    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text("VIAGENS", style = EstiloRotulo, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(12.dp))

            if (analise.dailyData.isEmpty() && analise.weeklyData.isEmpty() && analise.monthlyData.isEmpty()) {
                Text(
                    "Sem dados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.TextoApoio,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Gráfico de dias
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Últimos 30 dias",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cores.TextoApoio,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (analise.dailyData.isEmpty()) {
                            Text(
                                "Sem dados",
                                style = MaterialTheme.typography.bodySmall,
                                color = Cores.TextoApoio,
                            )
                        } else {
                            GraficoBarrasViagens(
                                dados = analise.dailyData,
                                maxValor = analise.maxDailyCount,
                                altura = 100.dp,
                            )
                        }
                    }

                    // Gráfico de semanas
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Últimas 12 semanas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cores.TextoApoio,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (analise.weeklyData.isEmpty()) {
                            Text(
                                "Sem dados",
                                style = MaterialTheme.typography.bodySmall,
                                color = Cores.TextoApoio,
                            )
                        } else {
                            GraficoBarrasViagens(
                                dados = analise.weeklyData,
                                maxValor = analise.maxWeeklyCount,
                                altura = 100.dp,
                            )
                        }
                    }

                    // Gráfico de meses
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Últimos 12 meses",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cores.TextoApoio,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (analise.monthlyData.isEmpty()) {
                            Text(
                                "Sem dados",
                                style = MaterialTheme.typography.bodySmall,
                                color = Cores.TextoApoio,
                            )
                        } else {
                            GraficoBarrasViagens(
                                dados = analise.monthlyData,
                                maxValor = analise.maxMonthlyCount,
                                altura = 100.dp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Gráfico de barras para contagem de viagens.
 *
 * @param dados pontos de dados com label e contagem de viagens
 * @param maxValor valor máximo para escala
 * @param altura altura total do gráfico
 */
@Composable
private fun GraficoBarrasViagens(
    dados: List<TripsDataPoint>,
    maxValor: Int,
    altura: androidx.compose.ui.unit.Dp,
) {
    if (dados.isEmpty()) {
        return
    }

    val yMax = (maxValor * 1.3).toInt().coerceAtLeast(1)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(altura)
            .background(Cores.Campo)
            .drawWithCache {
                onDrawBehind {
                    drawGraficoViagens(dados, yMax)
                }
            }
    )

    // Labels do eixo X
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        dados.forEach { ponto ->
            Text(
                ponto.label,
                style = MaterialTheme.typography.labelSmall,
                color = Cores.TextoApoio,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .wrapContentSize(Alignment.Center),
            )
        }
    }
}

/**
 * Desenha o gráfico de barras com eixos e barras.
 */
private fun DrawScope.drawGraficoViagens(
    dados: List<TripsDataPoint>,
    yMax: Int,
) {
    val barraLargura = size.width / dados.size
    val margemLateral = 4.dp.toPx()
    val alturaBarra = barraLargura - 2 * margemLateral

    // Eixos
    drawLine(
        color = Cores.Contorno,
        start = Offset(0f, size.height - 5),
        end = Offset(size.width, size.height - 5),
        strokeWidth = 1.0f,
    )
    drawLine(
        color = Cores.Contorno,
        start = Offset(0f, 0f),
        end = Offset(0f, size.height - 5),
        strokeWidth = 1.0f,
    )

    // Barras
    dados.forEachIndexed { index, ponto ->
        val x = index * barraLargura + margemLateral
        val alturaNormalizada = (ponto.count.toFloat() / yMax) * (size.height - 5)
        val yTopo = size.height - 5 - alturaNormalizada

        drawRect(
            color = Cores.Destaque,
            topLeft = Offset(x, yTopo),
            size = androidx.compose.ui.geometry.Size(alturaBarra, alturaNormalizada),
        )
    }
}
