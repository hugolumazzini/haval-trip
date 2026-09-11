package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.Cluster
import br.com.hugolumazzini.havaltrip.LugarNoPainel
import br.com.hugolumazzini.havaltrip.painel.JanelaDoPainel
import br.com.hugolumazzini.havaltrip.painel.PaginaDoCluster
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

    // Na bola do ar o que está por baixo não é só o círculo: o painel desenha
    // ali um quadrado de cantos arredondados, com as marcas de canto azuis, que
    // é o alerta de cinto do carro. Um fundo redondo do tamanho do círculo
    // deixava as quatro quinas desse quadrado aparecendo em volta — foi o que a
    // foto no carro mostrou. Por isso o fundo aqui virou um quadrado um pouco
    // maior que o círculo, que engole o alerta inteiro, e o anel azul que se vê
    // passa a ser desenhado por nós: é a única forma de tapar o quadrado sem
    // perder o contorno redondo que o painel tinha ali.
    val naBola = ajustes.lugarDoCarro == LugarNoPainel.BOLA_DO_AC

    // Acompanhar o carrossel de bolas do painel. Quem projeta uma janela lá em
    // cima fica por cima de todas as páginas, e é isso que faz o carro aparecer
    // sobre a bola de mídia de quem só o queria numa. Se o motorista escolheu
    // uma página, a janela se apaga nas outras — como o ar do Impulse faz.
    val pagina by PaginaDoCluster.pagina.collectAsStateWithLifecycle()
    val paginaEscolhida = ajustes.paginaDoCarro
    // `pagina == null` é "o painel ainda não contou nenhuma". Nesse caso
    // aparece: sumir por falta de informação seria uma janela em branco sem
    // explicação, e o motorista não teria como distinguir isso de um defeito.
    val naPaginaCerta = paginaEscolhida == null || pagina == null || pagina == paginaEscolhida
    // A espiada na central mostra sempre: lá o que se quer ver é o ajuste, e um
    // retângulo vazio porque o painel está noutra página não ensina nada.
    if (!espiando && !naPaginaCerta) return

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
                // O estica sobre o tamanho escolhido, contido em 1: mais que a
                // janela inteira o `fillMaxHeight` não aceita, e a caixa
                // continuaria do mesmo tamanho de qualquer jeito.
                // Na bola, o tamanho escolhido continua sendo o do anel: a caixa
                // é a tapa, que é maior, então ela cresce pela folga. Sem esta
                // conta o contorno azul nasceria menor do que era antes e o
                // ajuste que o motorista já tinha acertado sairia do lugar.
                .fillMaxHeight(
                    (
                        ajustes.tamanhoDoCarro.fracao * ajustes.zoomDoCarro.fator *
                            (if (naBola) FOLGA_DA_TAPA else 1f)
                        ).coerceIn(0.05f, 1f),
                )
                .aspectRatio(if (naBola) 1f else LARGURA_POR_ALTURA)
                // O fundo é desta caixa, e não da janela: a janela é a tela
                // inteira, e pintá-la toda apagaria o carro em vez de tapar só o
                // pedaço que atrapalha. Era o que faltava aqui — o desenho ia
                // para a bola do ar, mas o ar continuava aparecendo por baixo.
                //
                // Reto nos dois casos, agora: na bola o que se quer esconder é
                // justamente um quadrado, e canto redondo deixa as quinas dele
                // vazarem. O arredondado leve só evita a aresta viva.
                .background(
                    Color(ajustes.fundoDoCarro.argb),
                    if (naBola) RoundedCornerShape(CANTO_DA_TAPA) else RectangleShape,
                )
                // Conta à central onde caiu. Ver `QuadroDeMedidas`.
                .medindo(
                    JanelaDoPainel.CARRO,
                    constraints.maxWidth,
                    constraints.maxHeight,
                    ajustes.fundoDoCarro.argb,
                    // A tapa deixou de ser redonda mesmo na bola; a régua na
                    // central tem de contar o que existe, não o que existia.
                    false,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // O anel azul que o painel desenhava e que a nossa tapa cobriu,
            // refeito por dentro dela e do tamanho do original: sem ele a bola
            // vira um quadrado preto no meio do painel.
            if (naBola) {
                Canvas(
                    Modifier
                        .fillMaxHeight(1f / FOLGA_DA_TAPA)
                        .aspectRatio(1f),
                ) {
                    val traco = size.minDimension * GROSSURA_DO_ANEL
                    drawCircle(
                        color = AZUL_DO_PAINEL,
                        radius = (size.minDimension - traco) / 2f,
                        style = Stroke(width = traco),
                    )
                }
            }

            Diagrama(
                painel,
                // Fração da tapa, não do anel: inscrito certinho no anel o
                // desenho daria 0,65 do diâmetro, que é o "carrinho pequeno" da
                // foto. Aqui ele passa disso de propósito — as quinas do
                // retângulo são espaço vazio, o carro é estreito — e o teto é a
                // pressão do pneu, que fica a 0,63 de altura do centro e não
                // pode cruzar o anel.
                Modifier
                    .fillMaxHeight(if (naBola) DENTRO_DA_BOLA else 1f)
                    .aspectRatio(LARGURA_POR_ALTURA),
                // A legenda "pressão em psi" só na tela da central: aqui ela
                // rouba altura do desenho para dizer uma unidade que não muda.
                legenda = false,
            )
        }
    }
}

/** O bloco do carro é um pouco mais largo que alto: os pneus e as pressões ao lado. */
internal const val LARGURA_POR_ALTURA = 1.15f

/** Altura do desenho como fração da tapa. Ver o comentário no lugar em que é usada. */
internal const val DENTRO_DA_BOLA = 0.58f

/**
 * Quanto a tapa preta é maior que o anel azul.
 *
 * O alerta de cinto do painel é um quadrado de cantos arredondados um pouco
 * maior que o círculo; 1,28 do diâmetro cobre as marcas de canto dele com folga.
 * Aumentar mais não custa nada de aparência — em volta é preto —, mas comeria
 * área útil da bola quando o motorista encolhe a janela.
 */
internal const val FOLGA_DA_TAPA = 1.28f

/** Canto da tapa: arredondado só o bastante para não ficar uma aresta viva. */
internal val CANTO_DA_TAPA = 10.dp

/** Grossura do anel, em fração do diâmetro, medida no contorno original. */
internal const val GROSSURA_DO_ANEL = 0.022f

/** O azul do contorno da bola do ar no painel do H6. */
internal val AZUL_DO_PAINEL = Color(0xFF2E8BD6)
