package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.Cluster
import br.com.hugolumazzini.havaltrip.LugarNoPainel
import br.com.hugolumazzini.havaltrip.painel.JanelaDoPainel
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

    // Na bola do ar o que está por baixo é a tela do ar-condicionado, que é
    // redonda. Um retângulo opaco por cima dela deixaria as quinas do círculo
    // aparecendo em volta — pior do que não tapar nada, porque pareceria um
    // recorte torto. Nos nove cantos o fundo é reto, pelo motivo oposto: ali ele
    // serve para cobrir, e canto arredondado deixa vazar o que se quer esconder.
    val naBola = ajustes.lugarDoCarro == LugarNoPainel.BOLA_DO_AC

    // Mesmo arranjo da tela dos números: a janela pode ser o painel inteiro, e
    // é aqui que se diz em que canto dela o carro aparece e de que tamanho.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(fundo(espiando))
            .padding(4.dp),
        contentAlignment = ajustes.lugarDoCarro.alinhamento(),
    ) {
        // A largura vem da altura, e não da janela: sem isso, numa janela do
        // tamanho do painel as pressões dos pneus iam parar nas duas pontas da
        // tela, longe do carro a que se referem.
        Box(
            Modifier
                .offset(x = ajustes.empurraoDoCarro.x.dp, y = ajustes.empurraoDoCarro.y.dp)
                .fillMaxHeight(ajustes.tamanhoDoCarro.fracao)
                .aspectRatio(if (naBola) 1f else LARGURA_POR_ALTURA)
                // O fundo é desta caixa, e não da janela: a janela é a tela
                // inteira, e pintá-la toda apagaria o carro em vez de tapar só o
                // pedaço que atrapalha. Era o que faltava aqui — o desenho ia
                // para a bola do ar, mas o ar continuava aparecendo por baixo.
                .background(
                    Color(ajustes.fundoDoCarro.argb),
                    if (naBola) CircleShape else RectangleShape,
                )
                // Conta à central onde caiu. Ver `QuadroDeMedidas`.
                .medindo(JanelaDoPainel.CARRO, constraints.maxWidth, constraints.maxHeight),
            contentAlignment = Alignment.Center,
        ) {
            Diagrama(
                painel,
                // Dentro do círculo o desenho tem de caber no diâmetro, não
                // encostar nele: um retângulo 1,15 por 1 inscrito num círculo
                // ocupa dois terços da altura dele. Fora da bola a caixa já tem
                // a forma do desenho, e ele a preenche.
                Modifier
                    .fillMaxHeight(if (naBola) DENTRO_DO_CIRCULO else 1f)
                    .aspectRatio(LARGURA_POR_ALTURA),
            )
        }
    }
}

/** O bloco do carro é um pouco mais largo que alto: os pneus e as pressões ao lado. */
private const val LARGURA_POR_ALTURA = 1.15f

/** Altura do desenho como fração do diâmetro, para ele caber inscrito no círculo. */
private const val DENTRO_DO_CIRCULO = 0.65f
