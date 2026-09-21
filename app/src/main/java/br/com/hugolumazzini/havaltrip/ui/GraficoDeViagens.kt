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
 * Gráfico de contagem de viagens por mês.
 *
 * Mostra um gráfico de barras dos últimos 12 meses de forma clara.
 */
@Composable
fun GraficoDeViagens(analise: TripsAnalysisResult) {
    Cartao(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text("VIAGENS", style = EstiloRotulo, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(12.dp))

            if (analise.monthlyData.isEmpty()) {
                Text(
                    "Sem dados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.TextoApoio,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // Gráfico de meses
                Text(
                    "Últimos 12 meses",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.TextoApoio,
                )
                Spacer(modifier = Modifier.height(8.dp))
                GraficoBarrasViagens(
                    dados = analise.monthlyData,
                    maxValor = analise.maxMonthlyCount,
                    altura = 120.dp,
                )
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
    val margemLateral = 6.dp.toPx()
    val alturaBarra = barraLargura - 2 * margemLateral
    val alturaDados = size.height - 8

    // Eixo Y (esquerda)
    drawLine(
        color = Cores.Contorno,
        start = Offset(0f, 0f),
        end = Offset(0f, alturaDados),
        strokeWidth = 1.5f,
    )

    // Eixo X (baixo)
    drawLine(
        color = Cores.Contorno,
        start = Offset(0f, alturaDados),
        end = Offset(size.width, alturaDados),
        strokeWidth = 1.5f,
    )

    // Barras com cantos arredondados
    dados.forEachIndexed { index, ponto ->
        val x = index * barraLargura + margemLateral
        val alturaNormalizada = (ponto.count.toFloat() / yMax) * alturaDados
        val yTopo = alturaDados - alturaNormalizada

        drawRect(
            color = Cores.Destaque,
            topLeft = Offset(x, yTopo),
            size = androidx.compose.ui.geometry.Size(alturaBarra, alturaNormalizada),
        )
    }
}
