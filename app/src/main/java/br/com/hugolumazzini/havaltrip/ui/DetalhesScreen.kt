package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.engine.TripState
import br.com.hugolumazzini.havaltrip.format.TripFormat
import br.com.hugolumazzini.havaltrip.ui.theme.Cores
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloRotulo

/**
 * Tela de detalhes: tudo que foi tirado do painel para ele continuar legível.
 *
 * Aqui a densidade é bem-vinda — é uma tela consultada com o carro parado, ou
 * pelo passageiro.
 */
@Composable
fun DetalhesScreen(vm: TripViewModel, estado: TripState, tripId: String) {
    val trip = estado.trip(tripId) ?: run {
        Vazio("Esta Trip não existe mais.")
        return
    }
    val m = trip.metrics

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("DETALHES", style = EstiloRotulo)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PontoStatus(trip.status, tamanho = 12)
                    Text(trip.label, style = MaterialTheme.typography.headlineSmall, color = Cores.Texto)
                    Text(textoDoStatus(trip.status), color = Cores.TextoApoio)
                }
            }
            BotaoAcao("Voltar ao painel", vm::voltarAoPainel)
        }

        Spacer(Modifier.height(18.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Cartao(Modifier.weight(1f)) {
                Column {
                    Text("PERCURSO", style = EstiloRotulo)
                    Spacer(Modifier.height(6.dp))
                    LinhaDetalhe("Distância da Trip", TripFormat.km(m.distanceKm), destaque = true)
                    LinhaDetalhe("Hodômetro inicial", TripFormat.km(trip.odometerStartKm))
                    LinhaDetalhe(
                        "Hodômetro atual",
                        TripFormat.km(trip.odometerLastKm ?: estado.live.odometerTotalKm),
                    )
                    HorizontalDivider(color = Cores.Contorno)
                    LinhaDetalhe("Velocidade média", TripFormat.kmh(m.avgSpeedKmh))
                    LinhaDetalhe("Média em movimento", TripFormat.kmh(m.avgMovingSpeedKmh))
                    LinhaDetalhe("Velocidade máxima", TripFormat.kmh(m.maxSpeedKmh))
                }
            }

            Cartao(Modifier.weight(1f)) {
                Column {
                    Text("TEMPO", style = EstiloRotulo)
                    Spacer(Modifier.height(6.dp))
                    LinhaDetalhe("Tempo total", TripFormat.duracao(m.totalTimeS), destaque = true)
                    LinhaDetalhe("Em movimento", TripFormat.duracao(m.movingTimeS))
                    LinhaDetalhe("Parado com o motor ligado", TripFormat.duracao(m.idleTimeS))
                    LinhaDetalhe(
                        "Proporção parado",
                        m.idleRatio?.let { "${TripFormat.decimal(it * 100, 0)}%" } ?: TripFormat.AUSENTE,
                    )
                    HorizontalDivider(color = Cores.Contorno)
                    // O tempo desligado não aparece porque não é contado: o
                    // carro na garagem não faz parte da viagem.
                    Text(
                        "O tempo com a ignição desligada não entra em nenhuma conta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cores.TextoApoio,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Cartao(Modifier.fillMaxWidth()) {
            Column {
                Text("COMBUSTÍVEL", style = EstiloRotulo)
                Spacer(Modifier.height(6.dp))
                // Duas colunas, não quatro linhas: a central tem 600 px de
                // altura e o terceiro cartão é o primeiro a cair fora da tela.
                Row {
                    Column(Modifier.weight(1f)) {
                        LinhaDetalhe("Consumo médio", TripFormat.kml(m.avgFuelConsumptionKml), destaque = true)
                        LinhaDetalhe("Combustível queimado", TripFormat.litros(m.fuelLitres))
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        // Do veículo, não desta Trip — mas é aqui que a pergunta
                        // "e dá para chegar?" aparece junto com o resto.
                        LinhaDetalhe("Autonomia agora", TripFormat.km(estado.live.autonomyDteKm))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotaoAcao("Zerar esta Trip", { vm.zerar(trip.id) })
            BotaoAcao(
                "Fechar viagem no histórico",
                { vm.arquivar(trip.id); vm.voltarAoPainel() },
                habilitado = m.distanceKm > 0.0,
                cor = Cores.SuperficieSelecionada,
                corTexto = Cores.Destaque,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}
