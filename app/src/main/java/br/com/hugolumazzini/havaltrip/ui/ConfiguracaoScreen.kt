package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.engine.TripState
import br.com.hugolumazzini.havaltrip.storage.TripSnapshot
import br.com.hugolumazzini.havaltrip.ui.theme.Cores
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloRotulo

/**
 * Paradas em que a barra de zeragem encaixa, em minutos, de "na hora" a 3 h.
 *
 * A barra anda por estas paradas em vez de deslizar contínua porque ninguém
 * quer "17 minutos": os tempos que importam são redondos, e num toque dentro do
 * carro acertar 15 num deslize livre seria sorte. Elas são mais juntas embaixo
 * de propósito — a diferença entre 5 e 10 minutos muda o que conta como uma
 * viagem só, e a diferença entre 2 h e 2 h 30 não muda nada.
 */
private val PARADAS = listOf(0, 1, 2, 5, 10, 15, 20, 30, 45, 60, 90, 120, 150, 180)

/** O rótulo de cada parada, na linguagem de quem está lendo o painel. */
fun rotuloDoTempo(minutos: Int): String = when {
    minutos == 0 -> "na hora de desligar"
    minutos < 60 -> "$minutos min"
    minutos % 60 == 0 -> "${minutos / 60} h"
    else -> "${minutos / 60} h ${minutos % 60} min"
}

/** A parada mais próxima do que está gravado — o que a barra mostra ao abrir. */
private fun paradaMaisProxima(segundos: Double?): Int {
    val minutos = ((segundos ?: 0.0) / 60.0)
    return PARADAS.indices.minBy { kotlin.math.abs(PARADAS[it] - minutos) }
}

/**
 * Ajustes do computador de bordo.
 *
 * Só duas decisões, e as duas são de gosto: quantos contadores manuais o
 * motorista quer ver na lateral, e quanto tempo de carro parado encerra a
 * viagem atual. Nada aqui apaga número nenhum — esconder um contador o congela
 * com o que ele já mediu, e voltar a mostrá-lo o traz inteiro de volta.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ConfiguracaoScreen(vm: TripViewModel, estado: TripState) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text("CONFIGURAÇÃO", style = EstiloRotulo)
        Spacer(Modifier.height(14.dp))

        Cartao(Modifier.fillMaxWidth()) {
            Column {
                Text("Contadores manuais", style = MaterialTheme.typography.titleMedium, color = Cores.Texto)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Quantos contadores aparecem na lateral, fora a Viagem atual. " +
                        "Os que saem da lista param de contar, mas guardam o que já mediram.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.TextoApoio,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    (1..TripSnapshot.MAX_CONTADORES_MANUAIS).forEach { quantos ->
                        Opcao(
                            texto = quantos.toString(),
                            marcada = estado.contadoresManuais == quantos,
                            onClick = { vm.definirContadoresManuais(quantos) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Cartao(Modifier.fillMaxWidth()) { ZeragemAutomatica(vm, estado) }
    }
}

/**
 * A barra do tempo de zeragem.
 *
 * Guarda a posição do dedo num estado próprio e só grava quando o dedo sai:
 * escrever no arquivo a cada pixel arrastado seria dezenas de gravações para um
 * ajuste só, e no cartão da central isso se sente.
 */
@Composable
private fun ZeragemAutomatica(vm: TripViewModel, estado: TripState) {
    val gravado = paradaMaisProxima(estado.zeragemAutomaticaS)
    // A chave amarra o rascunho ao valor gravado: quando ele muda por fora
    // (outra tela, o app reabrindo), a barra recomeça do valor novo em vez de
    // ficar presa onde o dedo largou da última vez.
    var parada by remember(gravado) { mutableFloatStateOf(gravado.toFloat()) }
    val minutos = PARADAS[parada.toInt().coerceIn(PARADAS.indices)]

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "Zerar a Viagem atual",
                style = MaterialTheme.typography.titleMedium,
                color = Cores.Texto,
                modifier = Modifier.weight(1f),
            )
            Text(
                rotuloDoTempo(minutos),
                style = MaterialTheme.typography.titleMedium,
                color = Cores.Destaque,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Com a chave fora por mais que este tempo, a Viagem atual é arquivada " +
                "no histórico e recomeça do zero. Abaixo dele, o trajeto continua o mesmo — " +
                "é o que faz uma parada rápida não virar duas viagens.",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(4.dp))
        // Numa tela de 1900 px a barra inteira ficaria com dez centímetros por
        // parada: precisa, mas exige atravessar o painel com o dedo.
        Slider(
            modifier = Modifier.widthIn(max = 700.dp),
            value = parada,
            onValueChange = { parada = it },
            onValueChangeFinished = { vm.definirZeragemAutomatica(minutos * 60.0) },
            valueRange = 0f..(PARADAS.size - 1).toFloat(),
            // Uma parada a menos que os pontos: `steps` conta só os do meio.
            steps = PARADAS.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = Cores.Destaque,
                activeTrackColor = Cores.Destaque,
                inactiveTrackColor = Cores.Campo,
                // Os tiquinhos das paradas somem: com catorze deles a barra
                // vira um pente e ninguém lê o número por cima disso.
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Row(Modifier.widthIn(max = 700.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("na hora", style = MaterialTheme.typography.bodySmall, color = Cores.TextoApoio)
            Text("3 h", style = MaterialTheme.typography.bodySmall, color = Cores.TextoApoio)
        }
    }
}

/** Botão de escolha única, no tamanho de dedo que a central pede. */
@Composable
private fun Opcao(texto: String, marcada: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (marcada) Cores.SuperficieSelecionada else Cores.Campo)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.titleSmall,
            color = if (marcada) Cores.Destaque else Cores.TextoCorrido,
        )
    }
}
