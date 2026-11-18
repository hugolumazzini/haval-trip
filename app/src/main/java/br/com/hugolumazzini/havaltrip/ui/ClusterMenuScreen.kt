package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import br.com.hugolumazzini.havaltrip.painel.HistoricoDaDespedida
import br.com.hugolumazzini.havaltrip.painel.JanelaDoPainel
import br.com.hugolumazzini.havaltrip.painel.ProjetorDoPainel
import br.com.hugolumazzini.havaltrip.painel.PaginaDoCluster
import br.com.hugolumazzini.havaltrip.painel.TeclaDoVolante
import br.com.hugolumazzini.havaltrip.painel.TecladoDoVolante
import kotlinx.coroutines.delay

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

    // Recolhe a janela se o toggle for desabilitado
    LaunchedEffect(ajustes.habilitarPaginaComVisoes) {
        if (!ajustes.habilitarPaginaComVisoes && !espiando) {
            ProjetorDoPainel.recolher(JanelaDoPainel.MENU)
        }
    }

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

    // A pergunta "fechar a viagem?" aberta pelo OK segurado: de qual Trip, e
    // se o realce está no Sim. Pelo id, e não pela Trip: a Trip muda a cada
    // amostra de telemetria e a guardada aqui envelheceria na hora.
    var fechando by remember { mutableStateOf<String?>(null) }
    var noSim by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val visoesAgora by rememberUpdatedState(visoes)

    // Sem resposta, a pergunta some. Esquecida aberta, ela prenderia as setas
    // do volante e o motorista não conseguiria mais trocar de visão.
    LaunchedEffect(fechando, noSim) {
        if (fechando == null) return@LaunchedEffect
        delay(PERGUNTA_ABERTA_MS)
        fechando = null
    }
    LaunchedEffect(aviso) {
        if (aviso == null) return@LaunchedEffect
        delay(AVISO_MS)
        aviso = null
    }

    // Só ouve a cruzinha quando esta página está na frente. Sem isso, o
    // motorista navegaria no menu do painel — mídia, telefone — e ao voltar
    // encontraria a nossa página noutra visão, sem ter pedido.
    // Também na espiada: é por aqui que a simulação de volante pelo `adb`
    // chega, e sem ela não haveria como conferir a página fora do carro.
    LaunchedEffect(naPaginaCerta || espiando, visoes.size) {
        if (!naPaginaCerta && !espiando) {
            // Ninguém responde a uma pergunta numa página que não está na frente.
            fechando = null
            return@LaunchedEffect
        }
        TecladoDoVolante.teclas.collect { tecla ->
            val pergunta = fechando
            if (pergunta != null) {
                when (tecla) {
                    TeclaDoVolante.CIMA, TeclaDoVolante.BAIXO -> noSim = !noSim
                    TeclaDoVolante.ENTRA -> {
                        fechando = null
                        if (noSim) {
                            vm.arquivar(pergunta)
                            aviso = "Viagem guardada\nno histórico"
                        }
                    }
                    TeclaDoVolante.VOLTA, TeclaDoVolante.SEGURA_VOLTA -> fechando = null
                    else -> Unit
                }
                return@collect
            }
            if (tecla == TeclaDoVolante.SEGURA_ENTRA) {
                val visao = visoesAgora.getOrNull(atual.coerceIn(0, visoesAgora.lastIndex))
                when {
                    visao !is Visao.DeTrip -> aviso = "Escolha uma Trip\npara fechar"
                    // O mesmo critério do botão "Fechar viagem" da central,
                    // que fica apagado enquanto a Trip não andou.
                    visao.trip.metrics.distanceKm <= 0.0 -> aviso = "Esta Trip\nainda não andou"
                    else -> {
                        aviso = null
                        // Começa no Não: um OK a mais, sem querer, não pode
                        // ser o que fecha a viagem.
                        noSim = false
                        fechando = visao.trip.id
                    }
                }
                return@collect
            }
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

    // A despedida toma a janela inteira, como nas janelas dos números e do
    // carro. A bola é um recorte do painel de dirigir; com o carro desligado
    // não há painel atrás para preservar, e o resumo espremido no círculo era
    // o que o motorista via como "só dentro da bola".
    if (comResumo && viagem != null) {
        DisposableEffect(Unit) {
            HistoricoDaDespedida.info("🎬 Janela da bola (menu) desenhando a despedida em tela cheia")
            onDispose {
                HistoricoDaDespedida.info("🎬 Janela da bola (menu) deixou de desenhar a despedida")
            }
        }
        DespedidaDaViagem(
            viagem.metrics,
            estado.live,
            tinta(ajustes, paleta),
            painel,
            Modifier.fillMaxSize().background(Color(ajustes.fundoDoMenu.argb)),
        )
        return
    }

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
            // O anel azul que o painel desenha, se o motorista não o desabilitou.
            if (ajustes.mostrarAnelAzul) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                ) {
                    val traco = size.minDimension * GROSSURA_DO_ANEL
                    drawCircle(
                        color = AZUL_DO_PAINEL,
                        radius = (size.minDimension - traco) / 2f,
                        style = Stroke(width = traco),
                    )
                }
            }

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
            Box(
                Modifier
                    // Dentro da bola, e não na tela: é o conteúdo que anda,
                    // a bola fica onde o painel a desenha.
                    .offset(x = empurraoDentro.x.dp, y = empurraoDentro.y.dp)
                    // Frações do círculo; a [SOBRA] guarda a borda para a
                    // moldura. Sem teto: havia tetos medidos para o conteúdo
                    // não passar da curva, e no painel o botão de aumentar
                    // parava de crescer no meio da faixa. Agora o estica vai
                    // de ponta a ponta, e o conteúdo pode passar do círculo se
                    // o motorista quiser — `required`, porque `fillMax` não
                    // passa do tamanho da caixa de fora.
                    .requiredSize(
                        width = lado * (if (deitado) ALTURA_UTIL else LARGURA_UTIL) * SOBRA * zoom.fator,
                        height = lado * (if (deitado) LARGURA_UTIL else ALTURA_UTIL) * SOBRA * zoom.fator,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Conteudo(
                    visao, visoes.size, indice, painel, estado.live, ajustes, cor,
                    apertado = true,
                    // Título e bolinhas vêm logo abaixo, fora do zoom.
                    comMoldura = false,
                )
            }

            // Fora da caixa do zoom, de propósito. Título e bolinhas são
            // moldura, não dado: dizem "em que visão você está", e o lugar
            // deles é a borda do círculo. Dentro do zoom, aumentar o número
            // engordava as bolinhas junto e empurrava o título para fora da
            // bola.
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

            // Por cima de tudo, dentro do círculo: a pergunta e o aviso tapam a
            // visão de propósito, para não haver dúvida de que o volante agora
            // responde a eles e não troca de visão.
            val nomeDaTrip = fechando?.let { id ->
                visoes.firstNotNullOfOrNull { (it as? Visao.DeTrip)?.trip?.takeIf { t -> t.id == id } }?.label
            }
            if (nomeDaTrip != null) {
                PerguntaNaBola(nomeDaTrip, noSim, lado.value, cor.cor, Color(ajustes.fundoDoMenu.argb))
            } else {
                aviso?.let { AvisoNaBola(it, lado.value, cor.cor, Color(ajustes.fundoDoMenu.argb)) }
            }
        }
    }
}

/** Quanto tempo a pergunta espera uma resposta antes de sumir sozinha. */
private const val PERGUNTA_ABERTA_MS = 10_000L

/** Quanto tempo um aviso curto fica na bola. */
private const val AVISO_MS = 3_000L

/**
 * "Fechar a viagem?" com Não e Sim, um embaixo do outro, para as setas de
 * cima e de baixo escolherem — é a mesma direção que elas já têm no painel.
 */
@Composable
private fun PerguntaNaBola(nome: String, noSim: Boolean, lado: Float, cor: Color, fundo: Color) {
    val letra = (lado * 0.075f).coerceIn(10f, 28f)
    Column(
        Modifier.fillMaxSize().background(fundo, CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Fechar a viagem?", color = cor, fontSize = letra.sp, fontWeight = FontWeight.Medium)
        Text(
            nome.uppercase(),
            color = cor.copy(alpha = 0.75f),
            fontSize = (letra * 0.8f).sp,
            maxLines = 1,
        )
        Spacer(Modifier.height((lado * 0.05f).dp))
        OpcaoNaBola("Não", escolhida = !noSim, letra, cor, fundo)
        Spacer(Modifier.height((lado * 0.025f).dp))
        OpcaoNaBola("Sim", escolhida = noSim, letra, cor, fundo)
    }
}

/** Uma opção: a escolhida vem em bloco cheio, para se ver de relance. */
@Composable
private fun OpcaoNaBola(texto: String, escolhida: Boolean, letra: Float, cor: Color, fundo: Color) {
    Text(
        texto,
        color = if (escolhida) fundo else cor.copy(alpha = 0.6f),
        fontSize = letra.sp,
        fontWeight = if (escolhida) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (escolhida) cor else Color.Transparent)
            .border(1.dp, cor.copy(alpha = if (escolhida) 1f else 0.4f), RoundedCornerShape(50))
            .padding(horizontal = (letra * 1.4f).dp, vertical = (letra * 0.3f).dp),
    )
}

@Composable
private fun AvisoNaBola(texto: String, lado: Float, cor: Color, fundo: Color) {
    Box(
        Modifier.fillMaxSize().background(fundo, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto,
            color = cor,
            fontSize = (lado * 0.07f).coerceIn(10f, 26f).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
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
                                // Sem empurrão aqui: na bola quem move o carrinho
                                // é a caixa de fora. Os dois juntos o moviam em
                                // dobro, e a régua só contava metade.
                                .medindoPeca("carrinho")
                        } else {
                            Modifier
                                .fillMaxHeight(ajustes.zoomDoCarroNaBola.fator.coerceIn(0.2f, 1f))
                                .aspectRatio(LARGURA_POR_ALTURA)
                                .offset(
                                    x = ajustes.empurraoDoCarroNaBola.x.dp,
                                    y = ajustes.empurraoDoCarroNaBola.y.dp
                                )
                        },
                        legenda = false,
                        // CORREÇÃO: usa afastamentoNaBola (específico) em vez de afastamentoDoCarroNaBola
                        // Sem o piso de 0,5 que havia aqui: abaixo de 50% o
                        // botão de aproximar mudava o número e nada na tela.
                        aproximacao = ajustes.afastamentoNaBola.util / 100f,
                        offsetX = ajustes.offsetPressoesNaBola.x,
                        offsetY = ajustes.offsetPressoesNaBola.y,
                        escalaDasPressoes = if (apertado) ajustes.zoomPressoesNaBola.fator else 1f,
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
private const val MARGEM_DA_BOLA = 0.080f

/**
 * Tamanho do título na bola, em fração do diâmetro.
 *
 * Fração da bola, e não da caixa do conteúdo, justamente porque o título não
 * cresce com o zoom: ele é moldura, e a moldura é do tamanho do círculo. O
 * número sai de casar com o que o [TITULO_NA_ALTURA] dava antes de o zoom do
 * conteúdo existir.
 */
private const val TITULO_NA_BOLA = 0.063f

/**
 * De quantos dados em diante a bola passa a mostrar duas colunas.
 *
 * Três é o limite de uma coluna só: até aí os números ficam empilhados, que é a
 * leitura mais rápida. Do quarto em diante eles se dividem — quatro em duas
 * filas de dois enche o círculo bem melhor do que quatro numa pilha, que obriga
 * cada número a encolher para caber na fatia de um quarto da altura.
 */
private const val MUITOS_ITENS = 3

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
private val TAMANHO_DA_BOLINHA = 12.dp

/** Espaço entre uma bolinha e a seguinte. */
private val ENTRE_BOLINHAS = 10.dp

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
