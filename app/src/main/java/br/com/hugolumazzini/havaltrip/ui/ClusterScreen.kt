package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.AjustesDoCluster
import br.com.hugolumazzini.havaltrip.Cluster
import br.com.hugolumazzini.havaltrip.ItemDoCluster
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.domain.TripMetrics
import br.com.hugolumazzini.havaltrip.domain.VehicleLive
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
 *
 * O que aparece, de qual contador, em que tamanho e em que cor vem de
 * [Cluster], escolhido na tela de configuração da central.
 */
@Composable
fun ClusterScreen(vm: TripViewModel) {
    val estado by vm.state.collectAsStateWithLifecycle()
    val ajustes by Cluster.ajustes.collectAsStateWithLifecycle()

    // A Trip escolhida na configuração; se ela foi apagada desde então, cai na
    // selecionada da central em vez de deixar o painel em branco.
    val trip = ajustes.tripId?.let { id -> estado.trips.find { it.id == id } }
        ?: estado.selectedTrip
        ?: return

    Painel(trip.metrics, estado.live, ajustes)
}

@Composable
private fun Painel(m: TripMetrics, live: VehicleLive, ajustes: AjustesDoCluster) {
    val itens = ajustes.ItensSeguros
    val cor = Color(ajustes.cor.argb)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Preto de verdade seria uma mancha sobre o painel. Transparente é
            // o que faz a janela parecer conteúdo do carro.
            .background(Color.Transparent)
            .padding(8.dp),
    ) {
        // Um retângulo mais largo que alto comporta os dados lado a lado; um
        // mais alto que largo, empilhados. A conta é simplória de propósito —
        // é o formato que decide, não uma tabela de tamanhos que eu teria de
        // adivinhar sem ver o painel.
        val emLinha = maxWidth > maxHeight * 1.6f
        // Cada dado recebe uma fatia igual, e o tamanho da letra sai do menor
        // lado dela. Sem a largura nessa conta, quatro itens com a letra no
        // "Maior" saíam pela borda e o número aparecia cortado no painel — que
        // é pior do que um número pequeno, porque parece um valor errado.
        val altura: Dp = if (emLinha) maxHeight else maxHeight / itens.size
        val largura: Dp = if (emLinha) maxWidth / itens.size else maxWidth

        val leituras = itens.map { it to it.leitura(m, live) }

        // Um tamanho só para todos, e é o do que mais aperta. Deixar cada
        // número achar o seu deixava a faixa desalinhada, com o valor mais
        // curto virando o mais gritante — o olho lê isso como "este aqui é o
        // importante", que não é o que se quer dizer.
        val pelaAltura = (altura.value * 0.42f).coerceIn(16f, 96f)
        val tamanho = leituras.minOf { (_, leitura) ->
            // Um dígito ocupa mais ou menos 0,62 do tamanho da fonte nesta
            // família; o mínimo de 4 impede que um valor curto ("8") peça uma
            // letra gigantesca. Empilhado a unidade divide a linha com o
            // número, então ela também pesa na largura — em letra menor, daí o
            // 0,4 em vez de contá-la inteira.
            val caracteres = maxOf(leitura.first.length, 4) +
                if (emLinha) 0f else (leitura.second.length + 1) * 0.4f
            largura.value / (caracteres * 0.62f)
        }.let { cabe -> (pelaAltura * ajustes.escalaFonte).coerceAtMost(cabe) }

        if (emLinha) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leituras.forEach { (item, leitura) ->
                    Bloco(item, leitura, tamanho, cor, emLinha, Modifier.weight(1f))
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                leituras.forEach { (item, leitura) ->
                    Bloco(item, leitura, tamanho, cor, emLinha, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Bloco(
    item: ItemDoCluster,
    leitura: Pair<String, String>,
    numero: Float,
    cor: Color,
    emLinha: Boolean,
    modifier: Modifier = Modifier,
) {
    val (valor, unidade) = leitura
    val rotulo = (numero * 0.32f).coerceAtLeast(9f)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            item.rotulo,
            color = Cores.TextoApoio,
            fontSize = rotulo.sp,
            letterSpacing = (rotulo * 0.08f).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        // Lado a lado, a unidade ganha a sua própria linha embaixo do número —
        // é o desenho mais limpo. Empilhado, ela vai ao lado do número: uma
        // terceira linha por dado esbarrava no rótulo do dado seguinte.
        if (emLinha) {
            Numero(valor, numero, cor)
            Text(unidade, color = Cores.TextoApoio, fontSize = rotulo.sp, maxLines = 1)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Numero(valor, numero, cor)
                Text(
                    " $unidade",
                    color = Cores.TextoApoio,
                    fontSize = rotulo.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = (numero * 0.12f).dp),
                )
            }
        }
    }
}

@Composable
private fun Numero(valor: String, tamanho: Float, cor: Color) {
    Text(
        valor,
        color = cor,
        fontSize = tamanho.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}
