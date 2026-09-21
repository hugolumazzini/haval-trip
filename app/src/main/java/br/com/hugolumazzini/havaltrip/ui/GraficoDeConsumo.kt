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
import br.com.hugolumazzini.havaltrip.services.ConsumptionAnalysisResult
import br.com.hugolumazzini.havaltrip.services.ConsumptionDataPoint
import br.com.hugolumazzini.havaltrip.ui.theme.Cores
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloRotulo

/**
 * Gráfico de consumo de combustível (km/L) por dia e mês.
 *
 * Mostra dois gráficos de barras lado a lado:
 * - Esquerda: últimos 30 dias
 * - Direita: últimos 12 meses
 *
 * Se não há dados, exibe mensagem de aviso.
 */
@Composable
fun GraficoDeConsumo(analise: ConsumptionAnalysisResult) {
    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text("CONSUMO", style = EstiloRotulo, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(12.dp))

            if (analise.dailyData.isEmpty() && analise.monthlyData.isEmpty()) {
                Text(
                    "Sem dados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.TextoApoio,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                            GraficoBarras(
                                dados = analise.dailyData,
                                maxValor = analise.maxDailyConsumption,
                                altura = 120.dp,
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
                            GraficoBarras(
                                dados = analise.monthlyData,
                                maxValor = analise.maxMonthlyConsumption,
                                altura = 120.dp,
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
 * Gráfico de barras com eixo X e Y simples.
 *
 * @param dados pontos de dados com label, valor (avgConsumptionKml)
 * @param maxValor valor máximo para escala (1.5x é aplicado na lógica de altura)
 * @param altura altura total do gráfico
 */
@Composable
private fun GraficoBarras(
    dados: List<ConsumptionDataPoint>,
    maxValor: Double,
    altura: androidx.compose.ui.unit.Dp,
) {
    if (dados.isEmpty()) {
        return
    }

    // Escala: o máximo de 1.5x do valor máximo visto
    val yMax = maxValor * 1.5

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(altura)
            .background(Cores.Campo)
            .drawWithCache {
                onDrawBehind {
                    drawGrafico(dados, yMax)
                }
            }
    )

    // Labels do eixo X (cada barra tem um rótulo)
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
                    .height(30.dp),
                maxLines = 1,
            )
        }
    }
}

/**
 * Desenha o gráfico de barras no canvas.
 *
 * Cada barra tem altura proporcional ao consumo médio (km/L).
 * - Eixo X: barras lado a lado
 * - Eixo Y: 0 até 1.5x do máximo
 * - Cor: Cores.Destaque (azul)
 */
private fun DrawScope.drawGrafico(
    dados: List<ConsumptionDataPoint>,
    yMax: Double,
) {
    val totalWidth = size.width
    val totalHeight = size.height
    val barWidth = totalWidth / dados.size
    val margin = 4.dp.toPx()

    // Desenha as barras
    dados.forEachIndexed { index, ponto ->
        val consumo = ponto.avgConsumptionKml ?: 0.0
        val relacao = (consumo / yMax).coerceIn(0.0, 1.0)
        val barHeight = relacao * totalHeight

        val x = index * barWidth + margin
        val y = totalHeight - barHeight

        drawRect(
            color = Cores.Destaque,
            topLeft = Offset(x, y.toFloat()),
            size = androidx.compose.ui.geometry.Size(
                width = barWidth - 2 * margin,
                height = barHeight.toFloat(),
            ),
        )
    }

    // Desenha o eixo X (linha na base)
    drawLine(
        color = Cores.Contorno,
        start = Offset(0f, totalHeight),
        end = Offset(totalWidth, totalHeight),
        strokeWidth = 1.dp.toPx(),
    )

    // Desenha o eixo Y (linha na esquerda)
    drawLine(
        color = Cores.Contorno,
        start = Offset(0f, 0f),
        end = Offset(0f, totalHeight),
        strokeWidth = 1.dp.toPx(),
    )
}
