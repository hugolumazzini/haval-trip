package br.com.hugolumazzini.havaltrip.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.ceil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import br.com.hugolumazzini.havaltrip.AjustesDoCluster
import br.com.hugolumazzini.havaltrip.AlvoNaBola
import br.com.hugolumazzini.havaltrip.Cluster
import br.com.hugolumazzini.havaltrip.Zoom
import br.com.hugolumazzini.havaltrip.RotuloDoCluster
import br.com.hugolumazzini.havaltrip.LugarNoPainel
import br.com.hugolumazzini.havaltrip.TamanhoDoCarro
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo
import br.com.hugolumazzini.havaltrip.domain.Trip
import br.com.hugolumazzini.havaltrip.domain.VehicleLive
import br.com.hugolumazzini.havaltrip.painel.JanelaDoPainel
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

    // Carro desligado: ou esta janela mostra o resumo, ou ela sai da frente de
    // quem vai mostrar. Ver [Despedida.janelaDoResumo].
    val despedindo = lembrarDespedida(estado.live.ignition) && !espiando
    val viagem = Despedida.viagemQueAcabou(estado.trips)
    val comResumo = despedindo &&
        Despedida.janelaDoResumo(ajustes) == JanelaDoPainel.MENU &&
        Despedida.valeMostrar(viagem)
    if (despedindo && !comResumo) return

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
        // Na bola o conteúdo é posicionado pelo centro medido, e não por um
        // canto: ver [BolaDoPainel]. O alinhamento de canto era o que punha a
        // bola no lugar errado — ele encosta o bloco na borda, e a bola do
        // painel não está encostada em borda nenhuma.
        contentAlignment = Alignment.TopStart,
    ) {
        val visao = visoes[indice]
        val cor = tinta(ajustes, paleta)

        // A medida que o motorista acertou à mão no painel, em frações da
        // janela. Ver [BolaDoPainel].
        // Sem o zoom e dividido pela folga. Duas correções que andam
        // juntas: [BolaDoPainel.LADO] foi medido na tapa preta, que era
        // maior que o círculo de propósito, então usá-lo como diâmetro
        // faria a bola nascer 28% maior que a do painel; e o zoom saiu
        // daqui porque a bola é medida do carro — mexer nela só a
        // desencaixaria da que o painel desenha embaixo. O zoom foi para o
        // conteúdo, logo abaixo.
        val lado = maxHeight * (BolaDoPainel.LADO / FOLGA_DA_TAPA).coerceIn(0.05f, 1f)
        Box(
            Modifier
                // Pelo centro: o canto de um quadrado que muda de tamanho
                // com o zoom não é lugar nenhum, o centro da bola é.
                .offset(
                    x = maxWidth * BolaDoPainel.CENTRO_X - lado / 2 + ajustes.empurraoDoMenu.x.dp,
                    y = maxHeight * BolaDoPainel.CENTRO_Y - lado / 2 + ajustes.empurraoDoMenu.y.dp,
                )
                .size(lado)
                // Redondo, e só. Antes era um quadrado preto com um anel
                // azul desenhado dentro: o quadrado tapava o alerta de cinto
                // do painel, que é maior que o círculo, e o anel devolvia o
                // contorno que o quadrado cobria. No carro o resultado foi
                // uma caixa preta com uma bola dentro, que não é o que o
                // painel desenha em nenhuma outra página. Agora a janela é o
                // círculo, e o que fica atrás dele é escolha ("Fundo").
                .background(Color(ajustes.fundoDoMenu.argb), CircleShape)
                // Conta à central onde o círculo caiu. Não existia aqui
                // porque a página é inteira e não havia posição a acertar;
                // passou a existir quando a bola virou uma caixa dentro
                // dela, com centro e diâmetro próprios. É desta leitura que
                // saem os números de [BolaDoPainel] — sem ela, a calibração
                // pede para medir e não mostra o que foi medido.
                .medindo(
                    JanelaDoPainel.MENU,
                    constraints.maxWidth,
                    constraints.maxHeight,
                    ajustes.fundoDoMenu.argb,
                    true,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Em pé ou deitado conforme o que vai dentro. Ver [LARGURA_UTIL].
            val deitado = visao is Visao.Carro ||
                ajustes.ItensDoMenuSeguros.size > MUITOS_ITENS
            // Cada conteúdo da bola tem posição e tamanho próprios: ver
            // [AlvoNaBola].
            val alvo = if (visao is Visao.Carro) AlvoNaBola.CARRO else AlvoNaBola.DADOS
            val empurraoDentro =
                if (alvo == AlvoNaBola.CARRO) ajustes.empurraoDoCarroNaBola
                else ajustes.empurraoDentroDoMenu
            val zoom = if (alvo == AlvoNaBola.CARRO) ajustes.zoomDoCarroNaBola else ajustes.zoomDoMenu
            // Três limites, e não dois: a coluna única não é a mesma peça
            // que as duas colunas. O teto dos dados foi medido com seis, que é
            // o caso mais apertado; aplicá-lo também a três travava o estica
            // muito antes de o bloco encostar na borda — sobrava círculo e o
            // botão de crescer não crescia mais nada.
            val limite = when {
                visao is Visao.Carro -> CARRINHO_NA_BOLA
                deitado -> DADOS_NA_BOLA
                ajustes.rotuloDoMenu == RotuloDoCluster.TEXTO -> DADOS_EM_COLUNA_COM_TEXTO
                else -> DADOS_EM_COLUNA
            }
            // O tamanho de partida desta peça, antes do estica.
            val baseLargura = (if (deitado) ALTURA_UTIL else LARGURA_UTIL) * SOBRA
            val baseAltura = (if (deitado) LARGURA_UTIL else ALTURA_UTIL) * SOBRA
            // E o quanto de estica ainda cabe nela. Ver [Cluster.anotarFaixaDoZoomDoMenu].
            LaunchedEffect(alvo, limite, baseLargura, baseAltura) {
                Cluster.anotarFaixaDoZoomNaBola(
                    alvo,
                    piso = emPorcento(limite.minimoLargura, limite.minimoAltura, baseLargura, baseAltura),
                    teto = emPorcento(limite.maximoLargura, limite.maximoAltura, baseLargura, baseAltura),
                )
            }
            Box(
                Modifier
                    // Dentro da bola, e não na tela: é o conteúdo que anda,
                    // a bola fica onde o painel a desenha.
                    .offset(x = empurraoDentro.x.dp, y = empurraoDentro.y.dp)
                    // Frações do círculo, e não da tapa: a caixa de fora já
                    // é o círculo. A [SOBRA] guarda a borda para a moldura.
                    // O teto de 1 é o próprio círculo — `fillMax` não aceita
                    // mais que a caixa, e nada maior caberia mesmo.
                    // Frações do círculo, e não da tapa: a caixa de fora já
                    // é o círculo. A [SOBRA] guarda a borda para a moldura.
                    // Os limites são medidos na régua, no painel, e são o
                    // que impede o estica de virar desenho cortado: ver
                    // [CARRINHO_NA_BOLA] e [DADOS_NA_BOLA].
                    .fillMaxWidth(
                        ((if (deitado) ALTURA_UTIL else LARGURA_UTIL) * SOBRA *
                            zoom.fator)
                            .coerceIn(limite.minimoLargura, limite.maximoLargura),
                    )
                    .fillMaxHeight(
                        ((if (deitado) LARGURA_UTIL else ALTURA_UTIL) * SOBRA *
                            zoom.fator)
                            .coerceIn(limite.minimoAltura, limite.maximoAltura),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (comResumo && viagem != null) {
                    // Dentro do anel, no lugar das visões: o resumo é a
                    // única coisa que fica no painel depois de desligar, e
                    // aqui ele herda o recorte redondo que já está acertado.
                    DespedidaDaViagem(viagem.metrics, estado.live, cor, painel)
                } else {
                    Conteudo(
                        visao, visoes.size, indice, painel, estado.live, ajustes, cor,
                        apertado = true,
                        // Título e bolinhas vêm logo abaixo, fora do zoom.
                        comMoldura = false,
                    )
                }
            }

            // Fora da caixa do zoom, de propósito. Título e bolinhas são
            // moldura, não dado: dizem "em que visão você está", e o lugar
            // deles é a borda do círculo. Dentro do zoom, aumentar o número
            // engordava as bolinhas junto e empurrava o título para fora da
            // bola.
            if (!comResumo) {
                Bolinhas(
                    visoes.size,
                    indice,
                    cor.cor,
                    Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = -lado * MARGEM_DA_BOLA),
                )
                Text(
                    visao.titulo,
                    color = cor.cor.copy(alpha = 0.75f),
                    fontSize = (lado.value * TITULO_NA_BOLA).coerceIn(9f, 26f).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = lado * MARGEM_DA_BOLA),
                )
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
    // Se o título e as bolinhas saem daqui. Na bola eles são presos ao círculo,
    // fora do zoom do conteúdo — ver [ClusterMenuScreen].
    comMoldura: Boolean = true,
) {
    val quantosItens = ajustes.ItensDoMenuSeguros.size

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val corpo = (maxHeight.value * TITULO_NA_ALTURA).coerceIn(9f, 26f).sp

        // As bolinhas de lado, e não sob o título: são um marcador de posição
        // vertical — para cima e para baixo é o que troca a visão —, e em pé,
        // encostadas na direita, elas apontam para o mesmo eixo do gesto.
        if (comMoldura) {
            Bolinhas(quantas, indice, cor.cor, Modifier.align(Alignment.CenterEnd))
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
                            // Medido para a régua da central: é daqui que sai
                            // quanto do círculo o carrinho come de verdade.
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(LARGURA_POR_ALTURA)
                                .medindoPeca("carrinho")
                        } else {
                            Modifier
                                .fillMaxHeight(ajustes.zoomDoCarroNaBola.fator.coerceIn(0.2f, 1f))
                                .aspectRatio(LARGURA_POR_ALTURA)
                        },
                        legenda = false,
                        aproximacao = ajustes.afastamentoDoCarro / 100f,
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
                        // Só na bola: é lá que uma linha de rótulo por dado
                        // custa caro. Na página larga o rótulo continua escrito.
                        rotulo = if (apertado) {
                            ajustes.rotuloDoMenu
                        } else {
                            RotuloDoCluster.TEXTO
                        },
                        // Idem: o afastamento é uma queixa de bola. Na página
                        // larga os dados ficam lado a lado, e não há altura
                        // sobrando para espalhar.
                        afastamento = if (apertado) {
                            ajustes.afastamentoDoMenu.fator
                        } else {
                            1f
                        },
                        // Idem ao carrinho: a régua conta quanto da bola a
                        // coluna de dados ocupa.
                        peca = if (apertado) "dados" else null,
                        // A lista desta página, que não é mais a dos números.
                        itens = ajustes.ItensDoMenuSeguros,
                    )
                }
            }
        }
    }
}

/**
 * Folga entre a moldura e a curva do círculo, em fração do diâmetro.
 *
 * Vale para os dois que ficam na borda: as bolinhas, de lado, e o título, em
 * cima. O retângulo do conteúdo acaba a 0,31 do centro e a curva está a 0,5 —
 * é essa sobra que eles ocupam, sem encostar na curva, onde o texto sairia
 * cortado nas pontas.
 */
private const val MARGEM_DA_BOLA = 0.07f

/**
 * Tamanho do título na bola, em fração do diâmetro.
 *
 * Fração da bola, e não da caixa do conteúdo, justamente porque o título não
 * cresce com o zoom: ele é moldura, e a moldura é do tamanho do círculo. O
 * número sai de casar com o que o [TITULO_NA_ALTURA] dava antes de o zoom do
 * conteúdo existir.
 */
private const val TITULO_NA_BOLA = 0.034f

/**
 * De quantos dados em diante a bola passa a mostrar duas colunas.
 *
 * Três é o limite de uma coluna só: até aí os números ficam empilhados, que é a
 * leitura mais rápida. Do quarto em diante eles se dividem — quatro em duas
 * filas de dois enche o círculo bem melhor do que quatro numa pilha, que obriga
 * cada número a encolher para caber na fatia de um quarto da altura.
 */
private const val MUITOS_ITENS = 3

/**
 * Que zoom faz a peça chegar a uma dada fração do círculo.
 *
 * O maior dos dois lados, e não o menor: enquanto um lado ainda pode crescer, o
 * botão ainda faz alguma coisa — parar no primeiro que trava roubaria estica
 * que existe.
 */
private fun emPorcento(largura: Float, altura: Float, baseLargura: Float, baseAltura: Float): Int =
    ceil(maxOf(largura / baseLargura, altura / baseAltura) * 100f)
        .toInt()
        .coerceIn(Zoom.MINIMO, Zoom.MAXIMO)

/**
 * Até onde uma peça pode crescer dentro do círculo, em fração do diâmetro.
 *
 * Os números saíram da régua, no painel: o motorista esticou até o desenho
 * encostar na borda e até ele ficar pequeno demais, e as duas leituras viraram
 * estas constantes. É por isso que são limites e não um tamanho fixo — o estica
 * continua valendo, só não passa mais do que cabe.
 */
private data class LimiteNaBola(
    val maximoLargura: Float,
    val maximoAltura: Float,
    // Só o carrinho tem piso, e por um motivo: ele é um desenho, e abaixo de um
    // certo tamanho vira mancha. Número encolhido ainda é número, então nos
    // dados não há piso nenhum — encolher é escolha legítima do motorista, e um
    // mínimo inventado aqui só brigaria com o botão de tamanho.
    val minimoLargura: Float = 0f,
    val minimoAltura: Float = 0f,
)

/**
 * O carrinho: de 0,62 a 0,90 do diâmetro de largura.
 *
 * Quem manda é a largura — o desenho é mais largo que alto, e é ela que encosta
 * no círculo primeiro. A altura acompanha pela proporção do desenho, então os
 * limites dela são frouxos de propósito: apertá-los deformaria o carro.
 */
private val CARRINHO_NA_BOLA = LimiteNaBola(
    maximoLargura = 0.898f,
    maximoAltura = 0.780f,
    minimoLargura = 0.620f,
    minimoAltura = 0.539f,
)

/**
 * Duas colunas de dados, de quatro a seis: até 0,82 de largura e 0,67 de altura.
 *
 * Medido no painel com seis dados e com o rótulo escrito, que é o caso mais
 * apertado que existe.
 */
private val DADOS_NA_BOLA = LimiteNaBola(0.820f, 0.671f)

/**
 * Coluna única, até três dados, com a palavra escrita em cima do número.
 *
 * Mais baixo que o caso do ícone, e não mais alto, porque a palavra rouba a
 * linha de cima de cada dado: o bloco chega à borda com o número menor. Os dois
 * casos foram medidos separados justamente por isso — um teto só serviria a um
 * deles e sobraria ou faltaria círculo no outro.
 */
private val DADOS_EM_COLUNA_COM_TEXTO = LimiteNaBola(0.620f, 0.759f)

/**
 * Coluna única, até três dados, sem a palavra: ícone ao lado ou só o número.
 *
 * Os dois andam juntos porque o ícone mora na mesma linha do número — nenhum
 * dos dois gasta a linha de cima —, e a medida do painel deu a mesma para os
 * dois.
 */
private val DADOS_EM_COLUNA = LimiteNaBola(0.671f, 0.820f)

/** Quanto de largura as bolinhas comem na direita, e o conteúdo não usa. */
private val FAIXA_DAS_BOLINHAS = 20.dp

/**
 * Tamanho de cada bolinha, igual para todas.
 *
 * Fixo, e não uma fração de nada: elas são moldura, não dado — crescer junto
 * com os números só roubaria espaço de quem importa. E igual entre si porque a
 * bolinha da página atual, quando era maior, empurrava as outras de lado a cada
 * troca de página: a coluna inteira dançava, e o olho ia atrás do movimento em
 * vez de ir ao número. Quem marca a página agora é só a opacidade.
 */
private val TAMANHO_DA_BOLINHA = 4.dp

/** Espaço entre uma bolinha e a seguinte. */
private val ENTRE_BOLINHAS = 5.dp

/** Quantas visões existem e em qual estamos, do jeito que o painel do carro mostra. */
@Composable
private fun Bolinhas(quantas: Int, atual: Int, cor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(quantas) { i ->
            Box(
                Modifier
                    .padding(bottom = ENTRE_BOLINHAS)
                    .size(TAMANHO_DA_BOLINHA)
                    .clip(CircleShape)
                    .background(cor.copy(alpha = if (i == atual) 0.95f else 0.25f)),
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

/**
 * Quanto do retângulo útil sobra para o conteúdo depois de reservar a borda.
 *
 * A moldura — título e bolinhas — mora na faixa entre o retângulo e a curva,
 * e é a [MARGEM_DA_BOLA] que a posiciona. Sem descontar esta sobra, na visão
 * do carro (a mais larga) o conteúdo ia até debaixo das bolinhas.
 */
private const val SOBRA = 0.86f
