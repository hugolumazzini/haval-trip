package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.AFASTAMENTO_MAXIMO
import br.com.hugolumazzini.havaltrip.AFASTAMENTO_MINIMO
import br.com.hugolumazzini.havaltrip.AjustesDoCluster
import br.com.hugolumazzini.havaltrip.Cluster
import br.com.hugolumazzini.havaltrip.FundoDoCluster
import br.com.hugolumazzini.havaltrip.ItemDoCluster
import br.com.hugolumazzini.havaltrip.CorDoCluster
import br.com.hugolumazzini.havaltrip.LugarNoPainel
import br.com.hugolumazzini.havaltrip.RotuloDoCluster
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.painel.JanelaDoPainel
import br.com.hugolumazzini.havaltrip.domain.MedidaDoPainel
import br.com.hugolumazzini.havaltrip.domain.PaletaSport
import br.com.hugolumazzini.havaltrip.telemetry.PaletaDoImpulse
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
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
fun ClusterScreen(
    vm: TripViewModel,
    espiando: Boolean = false,
    /** Conferência do resumo de despedida sem precisar desligar o carro. */
    forcarDespedida: Boolean = false,
) {
    val estado by vm.state.collectAsStateWithLifecycle()
    val ajustes by Cluster.ajustes.collectAsStateWithLifecycle()
    val paleta by Cluster.paleta.collectAsStateWithLifecycle()
    // Só a despedida usa: é o carrinho dela que continua reagindo às portas
    // depois de desligar. Os números não têm desenho de carro.
    val painelDoCarro by vm.painelDoVeiculo.collectAsStateWithLifecycle()

    // A Trip escolhida na configuração; se ela foi apagada desde então, cai na
    // selecionada da central em vez de deixar o painel em branco.
    val trip = ajustes.tripId?.let { id -> estado.trips.find { it.id == id } }
        ?: estado.selectedTrip
        ?: return

    // O carro acabou de ser desligado: em vez dos números de dirigir, o resumo
    // da viagem que terminou. Não é uma tela a mais para o motorista escolher —
    // é a última imagem que vai ficar congelada no painel a noite toda, e por
    // isso ela se decide sozinha. Ver [Despedida].
    val despedindo = lembrarDespedida(estado.live.ignition) || forcarDespedida
    // Na conferência mostra mesmo sem viagem andada, senão quem acabou de
    // instalar o app abriria a prévia e não veria nada.
    val viagem = Despedida.viagemQueAcabou(estado.trips) ?: trip
    val despedida = despedindo && (forcarDespedida || Despedida.valeMostrar(viagem))

    // Se o resumo é de outra janela, esta some. Ver [Despedida.janelaDoResumo].
    // Na espiada não: lá o motorista pediu para ver esta tela, e devolver preto
    // seria responder ao toque dele com um defeito aparente.
    if (despedindo && !espiando &&
        Despedida.janelaDoResumo(ajustes) != JanelaDoPainel.NUMEROS
    ) {
        return
    }

    // A janela que o Impulse deu pode ser a tela inteira do painel: dar a ela
    // os extremos dos sliders é fácil, acertar 733x7 com o dedo não é. Por isso
    // o conteúdo se encaixa num pedaço dela, no canto escolhido na configuração
    // — o resto continua transparente, mostrando o painel do carro.
    BoxWithConstraints(Modifier.fillMaxSize().background(fundo(espiando))) {
        // A despedida não mora na caixa dimensionada: ela toma a janela inteira.
        // O bloco de números é pequeno de propósito, para não tapar o painel do
        // carro enquanto se dirige; quando o carro desliga não há mais painel
        // atrás para preservar, e o resumo é a única coisa na tela. Espremê-lo
        // no mesmo retângulo seria guardar espaço para ninguém.
        if (despedida) {
            DespedidaDaViagem(
                viagem.metrics,
                estado.live,
                tinta(ajustes, paleta),
                painelDoCarro,
                Modifier
                    .fillMaxSize()
                    .background(Color(ajustes.fundo.argb))
                    .medindo(
                        JanelaDoPainel.NUMEROS,
                        constraints.maxWidth,
                        constraints.maxHeight,
                        ajustes.fundo.argb,
                        false,
                    ),
            )
            return@BoxWithConstraints
        }

        val itens = ajustes.ItensSeguros.size

        // A fração pedida, antes de qualquer piso. É ela que decide o formato,
        // e não a caixa final: se o piso mudasse linha para coluna no meio do
        // caminho, o painel trocaria de desenho sozinho ao encolher a janela.
        // O estica multiplica os dois lados pelo mesmo fator: o bloco cresce em
        // proporção, sem deformar, e o tamanho escolhido continua sendo a base.
        val larguraPedida = maxWidth * ajustes.tamanho.largura * ajustes.zoomDosNumeros.fator
        val alturaPedida = maxHeight * ajustes.tamanho.altura * ajustes.zoomDosNumeros.fator
        val emLinha = larguraPedida > alturaPedida * 1.6f

        // As frações do "Tamanho" nasceram pensando num painel grande. Numa
        // janela que já é uma tira fina — o overlay do simulador tem 145 dp de
        // altura — 20% dela não comporta nem a letra mínima, e o número sumia
        // cortado, sobrando só o rótulo. Então a fração é uma intenção: se o
        // que ela pede não cabe o conteúdo, a caixa cresce até caber, no
        // limite da janela.
        val largura = larguraPedida
            .coerceAtLeast(MedidaDoPainel.larguraMinimaDaCaixa(emLinha, itens).dp)
            .coerceAtMost(maxWidth)
        val altura = alturaPedida
            .coerceAtLeast(MedidaDoPainel.alturaMinimaDaCaixa(emLinha, itens).dp)
            .coerceAtMost(maxHeight)

        Box(
            Modifier
                // Sempre do centro. Antes existiam nove cantos prontos *e* as
                // setas, e os dois brigavam: o mesmo empurrão dava num lugar
                // diferente conforme o canto, então mover o bloco exigia
                // entender a combinação dos dois. Com uma âncora só, a seta é a
                // única coisa que move — e alcança o painel inteiro, porque o
                // [Empurrao.LIMITE] cobre a distância do centro até a borda.
                .align(Alignment.Center)
                // Depois do `align`, e não antes: o empurrão é a correção sobre
                // a âncora, não uma âncora concorrente.
                .offset(x = ajustes.empurraoDosNumeros.x.dp, y = ajustes.empurraoDosNumeros.y.dp)
                .width(largura)
                .height(altura)
                // O fundo é do bloco, e não da janela: a janela é a tela
                // inteira do painel, e pintá-la inteira apagaria o carro em
                // vez de tapar só o pedaço que atrapalha.
                .background(Color(ajustes.fundo.argb))
                // Conta à central onde caiu. Ver `QuadroDeMedidas`.
                .medindo(
                    JanelaDoPainel.NUMEROS,
                    constraints.maxWidth,
                    constraints.maxHeight,
                    ajustes.fundo.argb,
                    false,
                ),
        ) {
            Painel(trip.metrics, estado.live, ajustes, emLinha, tinta(ajustes, paleta))
        }
    }
}

/** O canto escolhido, no formato que o Compose entende. */
fun LugarNoPainel.alinhamento(): Alignment = BiasAlignment(horizontal, vertical)

@Composable
internal fun Painel(
    m: TripMetrics,
    live: VehicleLive,
    ajustes: AjustesDoCluster,
    emLinha: Boolean,
    tinta: Tinta,
    colunas: Int = 1,
    /**
     * Como cada dado se identifica. O padrão é o rótulo escrito, que é o que
     * esta tela sempre fez; quem pede outra coisa é a bola, onde o espaço é
     * redondo e cada linha de texto sai do tamanho do número.
     */
    rotulo: RotuloDoCluster = RotuloDoCluster.TEXTO,
    /**
     * Quanta altura a coluna de dados usa, de 0 a 1. Menos que tudo aproxima os
     * dados entre si. Só a bola mexe nisto — ver
     * [AjustesDoCluster.afastamentoDoMenu].
     */
    afastamento: Float = 1f,
    /**
     * Nome desta coluna de dados na régua da central, ou `null` para não medir.
     * Ver [medindoPeca].
     */
    peca: String? = null,
    /**
     * Quais dados mostrar. O padrão é a lista da janela dos números, que é a
     * dona desta tela; a bola do painel manda a dela — ver
     * [AjustesDoCluster.itensDoMenu].
     */
    itens: List<ItemDoCluster> = ajustes.ItensSeguros,
) {

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(MedidaDoPainel.RESPIRO.dp),
    ) {
        // Cada dado recebe uma fatia igual, e o tamanho da letra sai do menor
        // lado dela. Sem a largura nessa conta, quatro itens com a letra no
        // "Maior" saíam pela borda e o número aparecia cortado no painel — que
        // é pior do que um número pequeno, porque parece um valor errado.
        // Empilhado, os dados podem vir em mais de uma coluna. Seis dados numa
        // coluna só dão uma fatia de um sexto da altura, e como o tamanho da
        // letra sai do menor lado da fatia, o número encolhe até não se ler de
        // relance — que é o único jeito de se ler algo dirigindo. Em duas
        // colunas a mesma fatia fica três vezes mais alta.
        val emColunas = if (emLinha) 1 else colunas.coerceAtLeast(1)
        val linhas = if (emLinha) 1 else (itens.size + emColunas - 1) / emColunas
        val altura: Dp = maxHeight / linhas
        val largura: Dp = if (emLinha) maxWidth / itens.size else maxWidth / emColunas

        val leituras = itens.map { it to it.leitura(m, live) }

        // Um tamanho só para todos, e é o do dado que mais aperta. Deixar cada
        // número achar o seu deixava a faixa desalinhada, com o valor mais
        // curto virando o mais gritante — o olho lê isso como "este aqui é o
        // importante", que não é o que se quer dizer.
        val tamanho = leituras.minOf { (_, leitura) ->
            MedidaDoPainel.tamanhoDaFonte(
                alturaDaFatia = altura.value,
                larguraDaFatia = largura.value,
                valor = leitura.first,
                unidade = leitura.second,
                emLinha = emLinha,
                escala = ajustes.escalaFonte,
            )
        }

        if (emLinha) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leituras.forEach { (item, leitura) ->
                    Bloco(item, leitura, tamanho, tinta, emLinha, rotulo, Modifier.weight(1f))
                }
            }
        } else {
            // Por linhas, e não por colunas: assim o segundo dado fica ao lado
            // do primeiro, e não seis posições abaixo dele. A ordem em que o
            // motorista escolheu os dados é a ordem de leitura, da esquerda
            // para a direita.
            // A coluna pode ocupar menos que a altura toda, centrada: é assim
            // que o afastamento aproxima os dados sem mexer no tamanho deles.
            // A fatia de onde sai a letra continua sendo a da altura inteira,
            // de propósito — encolher o número junto faria o botão de afastar
            // virar um segundo botão de tamanho.
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(
                        afastamento.coerceIn(
                            AFASTAMENTO_MINIMO / 100f,
                            AFASTAMENTO_MAXIMO / 100f,
                        ),
                    )
                    .align(Alignment.Center)
                    .then(peca?.let { Modifier.medindoPeca(it) } ?: Modifier),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                leituras.chunked(emColunas).forEach { fila ->
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        fila.forEach { (item, leitura) ->
                            Bloco(item, leitura, tamanho, tinta, emLinha, rotulo, Modifier.weight(1f))
                        }
                        // A última fila pode vir incompleta — cinco dados em
                        // duas colunas. O vazio segura o lugar para o dado
                        // sozinho não pular para o meio da bola.
                        repeat(emColunas - fila.size) { Spacer(Modifier.weight(1f)) }
                    }
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
    tinta: Tinta,
    emLinha: Boolean,
    comoSeIdentifica: RotuloDoCluster,
    modifier: Modifier = Modifier,
) {
    val (valor, unidade) = leitura
    val rotulo = MedidaDoPainel.tamanhoDoRotulo(numero)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // A linha do rótulo só existe no modo texto. Nos outros dois ela não
        // vira espaço em branco: some, e a altura que sobra é do número.
        if (comoSeIdentifica == RotuloDoCluster.TEXTO) {
            Text(
                item.rotulo,
                color = Cores.TextoApoio,
                fontSize = rotulo.sp,
                letterSpacing = (rotulo * 0.08f).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        // Lado a lado, a unidade ganha a sua própria linha embaixo do número —
        // é o desenho mais limpo. Empilhado, ela vai ao lado do número: uma
        // terceira linha por dado esbarrava no rótulo do dado seguinte.
        if (emLinha) {
            Numero(valor, numero, tinta)
            Text(unidade, color = Cores.TextoApoio, fontSize = rotulo.sp, maxLines = 1)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                if (comoSeIdentifica == RotuloDoCluster.ICONE) {
                    IconeDoItem(
                        item,
                        Cores.TextoApoio,
                        Modifier
                            // Na altura do corpo do número, e não na base dele.
                            // A linha toda é alinhada por baixo, que é o que a
                            // unidade quer — ela é texto e casa com a base da
                            // letra. O ícone não é texto: encostado no pé do
                            // número ele fica caído, parecendo outra coisa
                            // pendurada embaixo em vez da marca daquele dado.
                            // O recuo de baixo compensa a folga que a fonte
                            // guarda para as letras que descem.
                            .align(Alignment.CenterVertically)
                            .padding(end = (numero * 0.20f).dp, bottom = (numero * 0.06f).dp)
                            .size((numero * ICONE_NO_NUMERO).dp),
                    )
                }
                Numero(valor, numero, tinta)
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
private fun Numero(valor: String, tamanho: Float, tinta: Tinta) {
    Text(
        valor,
        color = tinta.cor,
        fontSize = tamanho.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        style = tinta.halo?.let { halo ->
            // O brilho do Analogico V2. O raio acompanha o tamanho da letra
            // porque um halo de medida fixa, que fica discreto num numero
            // grande, vira uma mancha borrada num numero pequeno.
            LocalTextStyle.current.copy(
                shadow = Shadow(color = halo, offset = Offset.Zero, blurRadius = tamanho * 0.5f),
            )
        } ?: LocalTextStyle.current,
    )
}

/**
 * Com que cor o numero e desenhado, e se leva brilho em volta.
 *
 * Duas coisas e nao uma porque o Impulse faz duas coisas diferentes: nas cores
 * escolhidas a mao o digito e da cor, e no Analogico V2 o digito e quase
 * branco e quem carrega a cor e o halo. Um campo so obrigaria a tela a
 * perguntar "que modo e este?" toda vez que fosse desenhar.
 */
internal data class Tinta(val cor: Color, val halo: Color? = null)

/**
 * A tinta que vale agora: a cor escolhida a mao, ou a paleta do Impulse.
 *
 * Quando a paleta nao pode ser lida — Shizuku sem permissao, Impulse ausente —
 * cai no branco sem halo. E o mesmo branco de sempre: quem escolheu "seguir o
 * Impulse" numa central que nao responde ve um numero legivel, e o porque
 * aparece na tela de configuracao, que e onde da para fazer algo a respeito.
 */
internal fun tinta(ajustes: AjustesDoCluster, paleta: PaletaDoImpulse.Resultado?): Tinta {
    if (ajustes.cor != CorDoCluster.DO_IMPULSE) return Tinta(Color(ajustes.cor.argb))
    val achada = (paleta as? PaletaDoImpulse.Resultado.Achou)?.paleta
        ?: return Tinta(Color(CorDoCluster.DO_IMPULSE.argb))
    return Tinta(
        cor = Color(PaletaSport.BRANCO_DO_ANALOGICO_V2),
        // A meia opacidade e o que o CSS do tema usa no brilho dos digitos:
        // a cor cheia em volta de cada numero fecharia o vao entre eles.
        halo = Color(achada.clara).copy(alpha = 0.55f),
    )
}

/**
 * Transparente no painel do carro — um retângulo escuro por cima pareceria um
 * app colado. Escuro só na conferência feita na central, onde atrás não há
 * painel nenhum e o texto claro sumiria sobre a tela de configuração.
 */
internal fun fundo(espiando: Boolean): Color =
    if (espiando) Color(0xFF0B0B0F) else Color.Transparent

/** Lado do ícone, em fração do tamanho do número ao lado dele. */
private const val ICONE_NO_NUMERO = 0.62f

/**
 * A marca de cada dado, desenhada à mão.
 *
 * À mão porque o conjunto pronto do Material não tem nada que sirva: os poucos
 * desenhos de carro que ele traz são de painel de oficina, e num quadrado de
 * meio centímetro eles viram uma mancha. Estes são traços grossos e abertos,
 * que é o que sobrevive à distância do painel.
 *
 * O que **não** está aqui, de propósito: a bomba de combustível para a média.
 * De relance, bomba no painel de um carro é uma coisa só — tanque acabando — e
 * um ícone que mente sobre a urgência é pior do que ícone nenhum.
 */
@Composable
private fun IconeDoItem(item: ItemDoCluster, cor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val l = size.minDimension
        val traco = l * 0.11f
        val risco = Stroke(width = traco, cap = StrokeCap.Round)
        val meio = Offset(l / 2f, l / 2f)

        fun linha(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
            cor, Offset(l * x1, l * y1), Offset(l * x2, l * y2), traco, StrokeCap.Round,
        )

        when (item) {
            // Estrada que se afunila no horizonte, com a faixa do meio: a
            // distância percorrida.
            ItemDoCluster.DISTANCIA -> {
                linha(0.18f, 0.94f, 0.38f, 0.10f)
                linha(0.82f, 0.94f, 0.62f, 0.10f)
                linha(0.50f, 0.80f, 0.50f, 0.58f)
                linha(0.50f, 0.42f, 0.50f, 0.24f)
            }

            // Gota sobre um traço: combustível por distância. A gota sozinha é
            // líquido; o traço embaixo é o chão que ela rende.
            ItemDoCluster.MEDIA, ItemDoCluster.LITROS -> {
                drawPath(
                    Path().apply {
                        moveTo(l * 0.5f, l * 0.08f)
                        cubicTo(l * 0.5f, l * 0.08f, l * 0.88f, l * 0.48f, l * 0.88f, l * 0.62f)
                        cubicTo(l * 0.88f, l * 0.83f, l * 0.71f, l * 0.94f, l * 0.5f, l * 0.94f)
                        cubicTo(l * 0.29f, l * 0.94f, l * 0.12f, l * 0.83f, l * 0.12f, l * 0.62f)
                        cubicTo(l * 0.12f, l * 0.48f, l * 0.5f, l * 0.08f, l * 0.5f, l * 0.08f)
                        close()
                    },
                    cor,
                    style = if (item == ItemDoCluster.LITROS) Fill else risco,
                )
            }

            // Relógio.
            ItemDoCluster.TEMPO -> {
                drawCircle(cor, radius = (l - traco) / 2f, center = meio, style = risco)
                linha(0.5f, 0.28f, 0.5f, 0.52f)
                linha(0.5f, 0.52f, 0.72f, 0.62f)
            }

            // Ponteiro de velocímetro: o arco do mostrador e a agulha. Na média
            // a agulha aponta para cima (meio do curso); na máxima, para o fim
            // da escala — é a mesma família de dado, com a diferença no ângulo.
            ItemDoCluster.VELOCIDADE_MEDIA, ItemDoCluster.VELOCIDADE_MAXIMA -> {
                drawArc(
                    cor,
                    startAngle = 160f,
                    sweepAngle = 220f,
                    useCenter = false,
                    topLeft = Offset(traco / 2f, traco / 2f),
                    size = Size(l - traco, l - traco),
                    style = risco,
                )
                if (item == ItemDoCluster.VELOCIDADE_MEDIA) {
                    linha(0.5f, 0.5f, 0.5f, 0.18f)
                } else {
                    linha(0.5f, 0.5f, 0.80f, 0.28f)
                }
            }

            // Raio: o consumo deste instante.
            ItemDoCluster.CONSUMO_AGORA -> {
                linha(0.60f, 0.06f, 0.28f, 0.54f)
                linha(0.28f, 0.54f, 0.54f, 0.54f)
                linha(0.54f, 0.54f, 0.40f, 0.94f)
            }

            // Seta longa para a direita: até onde ainda dá para ir.
            ItemDoCluster.AUTONOMIA -> {
                linha(0.08f, 0.5f, 0.88f, 0.5f)
                linha(0.62f, 0.26f, 0.90f, 0.5f)
                linha(0.62f, 0.74f, 0.90f, 0.5f)
            }

            // Os dois zeros do hodômetro, dentro da janelinha.
            ItemDoCluster.HODOMETRO -> {
                drawRoundRect(
                    cor,
                    topLeft = Offset(l * 0.06f, l * 0.26f),
                    size = Size(l * 0.88f, l * 0.48f),
                    cornerRadius = CornerRadius(l * 0.10f),
                    style = risco,
                )
                linha(0.30f, 0.40f, 0.30f, 0.60f)
                linha(0.52f, 0.40f, 0.52f, 0.60f)
                linha(0.72f, 0.40f, 0.72f, 0.60f)
            }
        }
    }
}
