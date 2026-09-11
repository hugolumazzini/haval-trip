package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import br.com.hugolumazzini.havaltrip.AjustesDoCluster
import br.com.hugolumazzini.havaltrip.Cluster
import br.com.hugolumazzini.havaltrip.LugarNoPainel
import br.com.hugolumazzini.havaltrip.TamanhoDoCarro
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo
import br.com.hugolumazzini.havaltrip.domain.Trip
import br.com.hugolumazzini.havaltrip.domain.VehicleLive
import br.com.hugolumazzini.havaltrip.painel.PaginaDoCluster
import br.com.hugolumazzini.havaltrip.painel.TeclaDoVolante
import br.com.hugolumazzini.havaltrip.painel.TecladoDoVolante

/**
 * Uma visão da página do painel: uma tela cheia de cada vez.
 *
 * O carro é sempre a primeira; depois vem um contador por Trip, na ordem em que
 * eles aparecem na central. Fazer a lista aqui, e não guardá-la nas
 * preferências, é de propósito: criar ou apagar uma Trip na central muda o
 * carrossel do painel na hora, sem um segundo lugar para o motorista manter em
 * dia.
 */
private sealed interface Visao {
    val titulo: String

    data object Carro : Visao {
        override val titulo = "CARRO"
    }

    data class DeTrip(val trip: Trip) : Visao {
        override val titulo get() = trip.label.uppercase()
    }
}

/**
 * A página do painel com várias visões, virada pela cruzinha do volante.
 *
 * ## O que ela é
 *
 * As páginas do painel do H6 se trocam com as setas para os lados; **dentro**
 * de uma página, para cima e para baixo. Esta janela é uma página: ocupa tudo,
 * aparece só na página que o motorista escolheu na Configuração, e o para
 * cima/baixo do volante troca a visão — o carro, cada contador.
 *
 * ## Por que é uma janela separada, e não as outras duas juntas
 *
 * Porque são dois jeitos de usar o painel, e o motorista escolhe. "Solto" é o
 * de sempre: janelinhas fixas, num canto, por cima de tudo. "Página" é este:
 * uma coisa de cada vez, do tamanho do painel, e nada aparece quando ele está
 * noutra página. Ter as duas coisas no mesmo lugar obrigaria a inventar o que
 * fazer quando os dois ajustes se contradissessem.
 *
 * ## O que ela não consegue fazer
 *
 * Criar uma página nova no carrossel. As páginas são do painel do carro, e não
 * há como acrescentar uma — o que dá é ocupar uma das que existem, de
 * preferência a que ficou vazia. Por isso a Configuração pede o número da
 * página em vez de oferecer uma lista.
 */
@Composable
fun ClusterMenuScreen(vm: TripViewModel, espiando: Boolean = false) {
    val estado by vm.state.collectAsStateWithLifecycle()
    val ajustes by Cluster.ajustes.collectAsStateWithLifecycle()
    val paleta by Cluster.paleta.collectAsStateWithLifecycle()
    val painel by vm.painelDoVeiculo.collectAsStateWithLifecycle()
    val pagina by PaginaDoCluster.pagina.collectAsStateWithLifecycle()

    val escolhida = ajustes.paginaDoMenu
    // Mesmo critério da janela do carro: sem informação, aparece. Sumir por não
    // saber em que página o painel está seria um retângulo vazio sem explicação.
    val naPaginaCerta = escolhida == null || pagina == null || pagina == escolhida

    val visoes = remember(estado.trips) {
        listOf(Visao.Carro) + estado.trips.map { Visao.DeTrip(it) }
    }
    var atual by remember { mutableIntStateOf(0) }
    // Uma Trip apagada encurta a lista debaixo do índice que já estava
    // guardado; sem isto a página ficaria em branco até alguém girar o volante.
    val indice = atual.coerceIn(0, visoes.lastIndex)

    // Só ouve a cruzinha quando esta página está na frente. Sem isso, o
    // motorista navegaria no menu do painel — mídia, telefone — e ao voltar
    // encontraria a nossa página noutra visão, sem ter pedido.
    // Também na espiada: é por aqui que a simulação de volante pelo `adb`
    // chega, e sem ela não haveria como conferir a página fora do carro.
    LaunchedEffect(naPaginaCerta, visoes.size) {
        if (!naPaginaCerta) return@LaunchedEffect
        TecladoDoVolante.teclas.collect { tecla ->
            // Circular: a lista é curta e chegar no fim sem poder continuar,
            // num carro, é o tipo de coisa que faz o motorista apertar três
            // vezes olhando para o painel em vez de para a estrada.
            // Pelo `atual`, e não pelo `indice`: este é um valor calculado na
            // composição em que a coroutine nasceu, e ficaria congelado no
            // primeiro — o volante trocaria sempre para a mesma visão.
            val onde = atual.coerceIn(0, visoes.lastIndex)
            when (tecla) {
                TeclaDoVolante.CIMA -> atual = (onde - 1 + visoes.size) % visoes.size
                TeclaDoVolante.BAIXO -> atual = (onde + 1) % visoes.size
                else -> Unit
            }
        }
    }

    if (!espiando && !naPaginaCerta) return

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Na espiada, o toque faz o papel da cruzinha: a central tem tela
            // sensível e o painel não, e sem isto não haveria como ver as
            // outras visões antes de projetar no carro.
            .then(
                if (espiando) {
                    Modifier.clickable { atual = (indice + 1) % visoes.size }
                } else {
                    Modifier
                }
            )
            // Sem a régua das outras janelas: aqui não há posição para acertar,
            // a página é inteira de propósito. A régua existe para o motorista
            // relatar onde uma janelinha caiu, e esta não cai em lugar nenhum.
            .background(fundo(espiando)),
        // Na bola, a página inteira não é nossa: o que se vê é o recorte
        // redondo, encostado à direita. Fora dela, é.
        contentAlignment =
            if (ajustes.menuNaBola) LugarNoPainel.BOLA_DO_AC.alinhamento() else Alignment.Center,
    ) {
        val visao = visoes[indice]
        val cor = tinta(ajustes, paleta)

        if (ajustes.menuNaBola) {
            // A mesma geometria da janela do carro na bola, e de propósito: é
            // uma medida já acertada dentro do carro, e refazê-la aqui a olho
            // seria começar de novo o que já custou uma viagem para ajustar.
            Box(
                Modifier
                    .offset(x = ajustes.empurraoDoMenu.x.dp, y = ajustes.empurraoDoMenu.y.dp)
                    .fillMaxHeight(
                        (TamanhoDoCarro.BOLA_DO_AC.fracao * ajustes.zoomDoMenu.fator * FOLGA_DA_TAPA)
                            .coerceIn(0.05f, 1f),
                    )
                    .aspectRatio(1f)
                    // A tapa preta: cobre o alerta de cinto do painel, que é um
                    // quadrado maior que o círculo. Ver `ClusterCarroScreen`.
                    .background(Color(0xFF000000), RoundedCornerShape(CANTO_DA_TAPA)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxHeight(1f / FOLGA_DA_TAPA).aspectRatio(1f)) {
                    val traco = size.minDimension * GROSSURA_DO_ANEL
                    drawCircle(
                        color = AZUL_DO_PAINEL,
                        radius = (size.minDimension - traco) / 2f,
                        style = Stroke(width = traco),
                    )
                }
                // Dentro do anel, e não da tapa: o que passar do anel cai em
                // cima do contorno azul e fica parecendo erro de desenho.
                // Em pé ou deitado conforme o que vai dentro. Ver [LARGURA_UTIL].
                val deitado = visao is Visao.Carro ||
                    ajustes.ItensSeguros.size > MUITOS_ITENS
                Box(
                    Modifier
                        .fillMaxWidth((if (deitado) ALTURA_UTIL else LARGURA_UTIL) / FOLGA_DA_TAPA)
                        .fillMaxHeight((if (deitado) LARGURA_UTIL else ALTURA_UTIL) / FOLGA_DA_TAPA),
                    contentAlignment = Alignment.Center,
                ) {
                    Conteudo(visao, visoes.size, indice, painel, estado.live, ajustes, cor, true)
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .offset(x = ajustes.empurraoDoMenu.x.dp, y = ajustes.empurraoDoMenu.y.dp)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Conteudo(visao, visoes.size, indice, painel, estado.live, ajustes, cor, false)
            }
        }
    }
}

/**
 * O miolo da visão: título, bolinhas e o desenho ou os números.
 *
 * O mesmo para os dois formatos porque a diferença entre eles é só o espaço
 * disponível — e é justamente por isso que quase tudo aqui é fração do que
 * couber, e não medida fixa.
 *
 * @param apertado a bola. Dentro dela os números ficam empilhados e o título
 *   sobe para uma linha só; deitados, como na página larga, não caberiam três
 *   valores lado a lado sem virar letra de bula.
 */
@Composable
private fun Conteudo(
    visao: Visao,
    quantas: Int,
    indice: Int,
    painel: PainelDoVeiculo,
    live: VehicleLive,
    ajustes: AjustesDoCluster,
    cor: Tinta,
    apertado: Boolean,
) {
    val quantosItens = ajustes.ItensSeguros.size

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val corpo = (maxHeight.value * TITULO_NA_ALTURA).coerceIn(9f, 26f).sp

        // As bolinhas de lado, e não sob o título: são um marcador de posição
        // vertical — para cima e para baixo é o que troca a visão —, e em pé,
        // encostadas na direita, elas apontam para o mesmo eixo do gesto.
        Bolinhas(
            quantas,
            indice,
            cor.cor,
            Modifier
                .align(Alignment.CenterEnd)
                // Empurradas para fora do quadrado útil, na direção do anel: o
                // círculo é mais largo na altura do meio do que o retângulo
                // útil, e sem isto as bolinhas ficariam coladas nos
                // números com um vazio grande entre elas e a borda azul.
                .offset(x = if (apertado) maxWidth * PARA_A_BORDA else 0.dp),
        )

        // O título sobe para a faixa entre o retângulo útil e o anel — espaço
        // que estava sobrando — em vez de comer uma linha do miolo. Dentro do
        // quadrado ele custava perto de um sexto da altura dos dados, que é o
        // que fazia os números parecerem pequenos para o tamanho da bola.
        if (apertado) {
            Text(
                visao.titulo,
                color = cor.cor.copy(alpha = 0.75f),
                fontSize = corpo,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -maxHeight * TITULO_ACIMA),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                // Na página larga, a faixa que as bolinhas ocupam, dos dois
                // lados para a coluna de números não nascer torta. Na bola não
                // é preciso: lá as bolinhas já ficam fora do quadrado útil, e
                // descontar a faixa só encolheria os números à toa.
                .padding(horizontal = if (apertado) 0.dp else FAIXA_DAS_BOLINHAS),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!apertado) {
                Text(
                    visao.titulo,
                    color = cor.cor.copy(alpha = 0.75f),
                    fontSize = corpo,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
            }

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                when (visao) {
                    is Visao.Carro -> Diagrama(
                        painel,
                        // Na bola o carro é limitado pela largura, e não pela
                        // altura: o retângulo útil é em pé, feito para números
                        // empilhados, e o desenho é mais largo que alto.
                        if (apertado) {
                            Modifier.fillMaxWidth().aspectRatio(LARGURA_POR_ALTURA)
                        } else {
                            Modifier
                                .fillMaxHeight(ajustes.zoomDoMenu.fator.coerceIn(0.2f, 1f))
                                .aspectRatio(LARGURA_POR_ALTURA)
                        },
                        legenda = false,
                    )

                    is Visao.DeTrip -> Painel(
                        visao.trip.metrics,
                        live,
                        ajustes,
                        // Deitado só na página larga: ela é muito mais larga que
                        // alta. Na bola é o contrário — três números lado a lado
                        // num círculo sobra vazio em cima e embaixo e falta
                        // largura no meio.
                        emLinha = !apertado,
                        tinta = cor,
                        // Na bola, duas colunas assim que a lista passa de
                        // quatro dados. Até quatro, uma coluna só é mais
                        // fácil de varrer com o olho; daí para cima o número
                        // já teria encolhido demais para valer a simetria.
                        colunas = if (apertado && quantosItens > MUITOS_ITENS) 2 else 1,
                    )
                }
            }
        }
    }
}

/**
 * Quanto o título sobe acima do retângulo útil, em fração da altura dele.
 *
 * O retângulo acaba a 0,36 do diâmetro do centro e o anel está a 0,5: sobra
 * uma calota de espaço em cima que nenhum dado alcança.
 * dentro dela sem encostar no azul e sem estreitar tanto que "VIAGEM ATUAL" —
 * o rótulo mais comprido — precise quebrar em duas linhas.
 */
private const val TITULO_ACIMA = 0.11f

/** De quantos dados em diante a bola passa a mostrar duas colunas. */
private const val MUITOS_ITENS = 4

/** Quanto de largura as bolinhas comem na direita, e o conteúdo não usa. */
private val FAIXA_DAS_BOLINHAS = 20.dp

/**
 * Quanto as bolinhas saem do quadrado útil rumo ao anel, em fração da largura.
 *
 * O quadrado inscrito acaba a 0,35 do diâmetro do centro e o anel está a 0,5;
 * 0,12 do lado do quadrado põe as bolinhas em cerca de 0,44 — perto da borda
 * sem encostar nela.
 */
private const val PARA_A_BORDA = 0.12f

/** Quantas visões existem e em qual estamos, do jeito que o painel do carro mostra. */
@Composable
private fun Bolinhas(quantas: Int, atual: Int, cor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(quantas) { i ->
            Box(
                Modifier
                    .padding(bottom = 6.dp)
                    .size(if (i == atual) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(cor.copy(alpha = if (i == atual) 0.9f else 0.3f)),
            )
        }
    }
}

/** O título ocupa pouco: a visão é que interessa. Fração da altura da página. */
private const val TITULO_NA_ALTURA = 0.055f

/**
 * O retângulo útil dentro do anel, em fração do diâmetro dele.
 *
 Não é um quadrado inscrito, e nem sempre está na mesma posição: o que decide
 * o tamanho da letra é a fatia de cada dado, e a fatia muda de forma conforme o
 * que está dentro. Numa coluna só de números o retângulo fica **em pé** — a
 * largura sobrava dos lados do número e a altura é que faltava. Em duas
 * colunas, ou com o desenho do carro, ele **deita**: aí é a largura que passa a
 * mandar, e uma caixa estreita apertaria o "49.566 km" do hodômetro.
 *
 Nos dois casos são os mesmos dois números, só trocados de lugar, e por isso o
 * canto do retângulo fica sempre a `√(0,60² + 0,72²)/2 = 0,469` do centro, com
 * folga até o anel, a 0,5.
 */
private const val LARGURA_UTIL = 0.60f
private const val ALTURA_UTIL = 0.72f
