package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.domain.TripStatus
import br.com.hugolumazzini.havaltrip.engine.TripState
import br.com.hugolumazzini.havaltrip.format.TripFormat
import br.com.hugolumazzini.havaltrip.ui.theme.Cores

/**
 * Tela principal.
 *
 * Regra que guiou o desenho: quatro números grandes e nada mais competindo com
 * eles. Distância, consumo médio, velocidade média e tempo total são o que se
 * quer saber de relance; o resto — hodômetro, autonomia, custo — fica na faixa
 * de cima em corpo pequeno ou na tela de detalhes, a um toque de distância.
 */
@Composable
fun PainelScreen(vm: TripViewModel, estado: TripState) {
    val trip = estado.selectedTrip ?: return
    val m = trip.metrics
    val veiculo by vm.painelDoVeiculo.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        // Faixa do veículo: vale para todas as Trips, então fica fora delas.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            CelulaVeiculo("HODÔMETRO", TripFormat.km(estado.live.odometerTotalKm))
            CelulaVeiculo("VELOCIDADE", TripFormat.kmh(estado.live.speedKmh))
            CelulaVeiculo("CONSUMO AGORA", TripFormat.kml(estado.live.instantFuelConsumptionKml))
            CelulaVeiculo("AUTONOMIA", TripFormat.km(estado.live.autonomyDteKm))
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PontoStatus(trip.status, tamanho = 12)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        trip.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = Cores.TextoCorrido,
                        maxLines = 1,
                    )
                    // Um contador que se apaga sozinho precisa dizer isso na
                    // cara do motorista: descobrir a zeragem depois do fato,
                    // sem aviso, pareceria defeito. Em minutos redondos, não em
                    // mm:ss — "05:00" aqui se leria como relógio em contagem.
                    val apoio = textoDoStatus(trip.status) + (
                        trip.autoResetAfterOffS?.let {
                            val minutos = (it / 60).toInt()
                            if (minutos == 0) " • zera ao desligar"
                            else " • zera após ${rotuloDoTempo(minutos)} desligado"
                        } ?: ""
                        )
                    Text(
                        apoio,
                        style = MaterialTheme.typography.bodySmall,
                        color = Cores.TextoApoio,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Três colunas de mesma largura: os quadrantes ocupam duas, o estado do
        // carro ocupa a terceira. Por peso, e não por largura fixa em dp, porque
        // a central do H6 é bem mais larga que o emulador padrão — em dp fixos a
        // faixa do carro encolheria proporcionalmente quanto maior fosse a tela,
        // que é o contrário do que se quer num desenho que só se lê de relance.
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(2f).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Quadrante(
                        "DISTÂNCIA",
                        TripFormat.decimal(m.distanceKm, 1),
                        "km",
                        Modifier.weight(1f).fillMaxSize(),
                    )
                    Quadrante(
                        "CONSUMO MÉDIO",
                        TripFormat.decimal(m.avgFuelConsumptionKml, 1),
                        "km/L",
                        Modifier.weight(1f).fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Quadrante(
                        "VELOCIDADE MÉDIA",
                        TripFormat.decimal(m.avgSpeedKmh, 0),
                        "km/h",
                        Modifier.weight(1f).fillMaxSize(),
                    )
                    Quadrante(
                        "TEMPO TOTAL",
                        TripFormat.duracao(m.totalTimeS),
                        TripFormat.unidadeDuracao(m.totalTimeS),
                        Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
            // `fillMaxHeight`, nunca `fillMaxSize`: um filho sem peso que pede a
            // largura toda tomaria a linha inteira na medição e deixaria zero
            // para os quadrantes, que têm peso e são medidos depois.
            LateralDoVeiculo(veiculo, Modifier.weight(1f).fillMaxHeight())
        }

        Spacer(Modifier.height(16.dp))

        // Não existe "iniciar": os contadores contam sozinhos desde que o carro
        // liga. Zerar é a ação do dia a dia, então é ela que ganha o destaque
        // que antes era do botão que ninguém deveria ter precisado apertar.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotaoAcao(
                "Zerar",
                { vm.zerar(trip.id) },
                Modifier.weight(1f),
                cor = Cores.SuperficieSelecionada,
                corTexto = Cores.Destaque,
            )
            if (trip.status == TripStatus.PAUSED) {
                BotaoAcao("Retomar", { vm.retomar(trip.id) }, Modifier.weight(1f))
            } else {
                BotaoAcao("Pausar", { vm.pausar(trip.id) }, Modifier.weight(1f))
            }
            BotaoAcao(
                "Fechar viagem",
                { vm.arquivar(trip.id) },
                Modifier.weight(1f),
                habilitado = m.distanceKm > 0.0,
            )
            BotaoAcao("Detalhes", { vm.abrirDetalhes(trip.id) }, Modifier.weight(1f))
        }
    }
}
