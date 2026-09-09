package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.Cluster
import br.com.hugolumazzini.havaltrip.TripViewModel

/**
 * O mesmo carro da tela grande, sozinho numa janela do painel.
 *
 * Reaproveita o [Diagrama] em vez de redesenhar: portas abertas, cintos,
 * pressões e o alerta piscando são uma peça só, e ter duas cópias faria uma
 * delas envelhecer sem ninguém perceber.
 *
 * Sem o cartão "VEÍCULO" em volta e sem a lista de avisos: aqui o retângulo é
 * pequeno, o fundo tem de ser transparente para casar com o painel, e o aviso
 * escrito exigiria um espaço que o desenho aproveita melhor. Quem quiser o
 * texto tem a tela da central.
 */
@Composable
fun ClusterCarroScreen(vm: TripViewModel, espiando: Boolean = false) {
    val painel by vm.painelDoVeiculo.collectAsStateWithLifecycle()
    val ajustes by Cluster.ajustes.collectAsStateWithLifecycle()

    // Mesmo arranjo da tela dos números: a janela pode ser o painel inteiro, e
    // é aqui que se diz em que canto dela o carro aparece e de que tamanho.
    Box(
        Modifier
            .fillMaxSize()
            .background(fundo(espiando))
            .padding(4.dp),
        contentAlignment = ajustes.lugarDoCarro.alinhamento(),
    ) {
        // A largura vem da altura, e não da janela: sem isso, numa janela do
        // tamanho do painel as pressões dos pneus iam parar nas duas pontas da
        // tela, longe do carro a que se referem.
        Diagrama(
            painel,
            Modifier
                .fillMaxHeight(ajustes.tamanhoDoCarro.fracao)
                .aspectRatio(LARGURA_POR_ALTURA),
        )
    }
}

/** O bloco do carro é um pouco mais largo que alto: os pneus e as pressões ao lado. */
private const val LARGURA_POR_ALTURA = 1.15f
