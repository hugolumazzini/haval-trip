package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.format.TripFormat
import br.com.hugolumazzini.havaltrip.ui.theme.Cores

/**
 * O resumo de viagem que aparece no painel de instrumentos.
 *
 * Três diferenças em relação à tela da central, todas ditadas por onde ela
 * mora:
 *
 * 1. **Fundo transparente.** O painel já tem desenho próprio embaixo; um
 *    retângulo escuro por cima pareceria um app colado, não parte do carro.
 * 2. **Tamanho desconhecido.** Quem define largura e altura é o motorista, na
 *    tela "Telas" do Impulse. Então nada aqui tem medida fixa: o layout vira
 *    linha ou coluna conforme o formato do retângulo, e o tamanho da letra sai
 *    de uma conta sobre o espaço disponível.
 * 3. **Nada de tocar.** O painel não tem toque. Não há botão nenhum — só
 *    números, e o motorista zera a viagem na central.
 */
@Composable
fun ClusterScreen(vm: TripViewModel) {
    val estado by vm.state.collectAsStateWithLifecycle()
    val trip = estado.selectedTrip ?: return
    val m = trip.metrics

    val itens = listOf(
        Item("VIAGEM", TripFormat.decimal(m.distanceKm, 1), "km"),
        Item("MÉDIA", TripFormat.decimal(m.avgFuelConsumptionKml, 1), "km/L"),
        Item("TEMPO", TripFormat.duracao(m.totalTimeS), TripFormat.unidadeDuracao(m.totalTimeS)),
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Preto de verdade seria uma mancha sobre o painel. Transparente é
            // o que faz a janela parecer conteúdo do carro.
            .background(Color.Transparent)
            .padding(8.dp),
    ) {
        // Um retângulo mais largo que alto comporta os três lado a lado; um
        // mais alto que largo, empilhados. A conta é simplória de propósito —
        // é o formato que decide, não uma tabela de tamanhos que eu teria de
        // adivinhar sem ver o painel.
        val emLinha = maxWidth > maxHeight * 1.6f
        val corpo: Dp = if (emLinha) maxHeight else maxHeight / itens.size

        if (emLinha) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) { itens.forEach { Bloco(it, corpo) } }
        } else {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { itens.forEach { Bloco(it, corpo) } }
        }
    }
}

/** Um dado do painel: rótulo miúdo em cima, número grande embaixo. */
private data class Item(val rotulo: String, val valor: String, val unidade: String)

@Composable
private fun Bloco(item: Item, alturaDisponivel: Dp) {
    // O número ocupa cerca de metade da altura da sua fatia, e o rótulo um
    // terço dele. Os limites existem para o texto não sumir num retângulo
    // apertado nem virar cartaz num retângulo generoso.
    val numero = (alturaDisponivel.value * 0.45f).coerceIn(18f, 96f)
    val rotulo = (numero * 0.32f).coerceAtLeast(9f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            item.rotulo,
            color = Cores.TextoApoio,
            fontSize = rotulo.sp,
            letterSpacing = (rotulo * 0.08f).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            item.valor,
            color = Cores.Texto,
            fontSize = numero.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Text(
            item.unidade,
            color = Cores.TextoApoio,
            fontSize = rotulo.sp,
            maxLines = 1,
        )
    }
}
