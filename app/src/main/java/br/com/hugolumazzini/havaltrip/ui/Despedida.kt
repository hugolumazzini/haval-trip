package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.hugolumazzini.havaltrip.AjustesDoCluster
import br.com.hugolumazzini.havaltrip.ItemDoCluster
import br.com.hugolumazzini.havaltrip.R
import br.com.hugolumazzini.havaltrip.painel.JanelaDoPainel
import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.Trip
import br.com.hugolumazzini.havaltrip.domain.TripMetrics
import br.com.hugolumazzini.havaltrip.domain.VehicleLive
import br.com.hugolumazzini.havaltrip.ui.theme.Cores

/**
 * O resumo que aparece no painel quando o carro é desligado.
 *
 * ## Por que existe
 *
 * A central apaga, mas a última imagem projetada no painel fica lá, parada, até
 * o carro ligar de novo. Isso não foi planejado — é como o painel se comporta —,
 * só que a imagem que ficava era a tela de sempre, desenhada para quem está
 * dirigindo. Se ela vai ficar congelada de qualquer jeito, mais vale que seja
 * uma tela feita para ser a última: o que rendeu a viagem que acabou de
 * terminar, para quem está descendo do carro.
 *
 * ## Quando aparece, e quando não
 *
 * Só na **transição** de ligado para desligado, e só se a viagem tiver andado
 * alguma coisa — ver [DISTANCIA_MINIMA_KM]. Abrir a chave, ligar o rádio e
 * desligar não é viagem, e um resumo de "0,0 km" no painel é ruído. Também não
 * aparece quando o app abre com o carro já desligado: aí não houve despedida
 * nenhuma, houve um app reiniciando.
 *
 * ## A animação
 *
 * O H6 entra por baixo, gira um quarto de volta e para deitado à esquerda; o
 * resumo surge ao lado dele, com os números subindo do zero. Tudo isso **uma
 * vez**, em pouco mais de dois segundos e meio, e depois nada mais se mexe: o
 * desenho tem de ficar bom **congelado**, porque congelado é como ele vai
 * passar a noite. Uma animação em laço deixaria o painel parado num frame do
 * meio do caminho — o carro de esguelha, o número pela metade.
 *
 * Quando a janela é pequena demais para o carro caber, o resumo sai sozinho,
 * centralizado. Ver [CABE_O_CARRO_LARGURA].
 */
object Despedida {

    /** Abaixo disto não foi viagem, foi ligar e desligar o carro. */
    const val DISTANCIA_MINIMA_KM = 0.3

    /** Quanto tempo os números levam para subir do zero até o valor real. */
    const val SUBIDA_MS = 1300

    /** A cena inteira, do carro entrando ao último número parando. */
    const val CENA_MS = 2600

    /**
     * A partir de que janela a cena com o carro vale a pena.
     *
     * Numa tira estreita o carro deitado ficaria do tamanho de um comprimido e
     * roubaria metade do espaço dos números, que são o assunto. Abaixo disto o
     * resumo aparece sozinho, que é a versão que sempre coube.
     */
    val CABE_O_CARRO_LARGURA = 440.dp
    val CABE_O_CARRO_ALTURA = 160.dp

    /**
     * O que o resumo mostra, nesta ordem.
     *
     * Os quatro que respondem "como foi essa viagem": quanto andei, quanto
     * durou, quanto rendeu e quanto custou. Autonomia e hodômetro ficam de fora
     * de propósito — são sobre o carro, não sobre a viagem que terminou.
     */
    val ITENS = listOf(
        ItemDoCluster.DISTANCIA,
        ItemDoCluster.TEMPO,
        ItemDoCluster.MEDIA,
        ItemDoCluster.LITROS,
    )

    /**
     * A Trip que representa a viagem que acabou.
     *
     * É a automática — a que se zera sozinha entre uma viagem e outra —, porque
     * é a única que responde "esse trecho agora". A Trip do mês diria quanto se
     * andou em trinta dias, que não é despedida de nada. Sem uma automática,
     * não há resumo: é melhor não mostrar do que mostrar o número errado com
     * cara de certo.
     */
    fun viagemQueAcabou(trips: List<Trip>): Trip? = trips.firstOrNull { it.isAutomatic }

    /** Se vale a pena se despedir desta viagem. */
    fun valeMostrar(trip: Trip?): Boolean =
        trip != null && trip.metrics.distanceKm >= DISTANCIA_MINIMA_KM

    /**
     * Qual das janelas mostra o resumo — e, por consequência, quais se apagam.
     *
     * Quando o carro desliga, o painel congela **tudo** o que estiver na tela.
     * Com três janelas projetadas, o que ficava gravado ali a noite inteira era
     * o resumo com o desenho do carro e a bola de visões por cima, cada um
     * pedindo atenção e nenhum fazendo sentido — foi o que a foto no carro
     * mostrou. Não é problema de camada: é que uma despedida com companhia não
     * é uma despedida.
     *
     * Então uma janela fica com a cena e as outras somem. A escolhida é a
     * primeira desta ordem que estiver projetada: os números, que é a janela
     * feita para o resumo; a página, que é grande o bastante para ele; e o
     * carro, que é a que sobra. Nenhuma projetada quer dizer que só a espiada
     * na central está em jogo, e aí a dos números responde.
     */
    fun janelaDoResumo(ajustes: AjustesDoCluster): JanelaDoPainel = when {
        ajustes.telaDosNumeros != null -> JanelaDoPainel.NUMEROS
        ajustes.telaDoMenu != null -> JanelaDoPainel.MENU
        ajustes.telaDoCarro != null -> JanelaDoPainel.CARRO
        else -> JanelaDoPainel.NUMEROS
    }
}

/**
 * Lembra se o carro foi desligado **enquanto esta tela estava viva**.
 *
 * É o que separa "o motorista acabou de desligar" de "o app abriu com o carro
 * parado na garagem". Sem isso, reiniciar o aparelho com o carro desligado —
 * que acontece — encheria o painel de uma despedida de uma viagem que terminou
 * horas atrás.
 */
@Composable
fun lembrarDespedida(ignicao: IgnitionState): Boolean {
    val anterior = remember { mutableStateOf(ignicao) }
    var despedindo by remember { mutableStateOf(false) }

    // Num efeito, e não solto no corpo: mexer em estado durante a composição é
    // o tipo de coisa que funciona na leitura e não funciona na tela — foi o
    // que aconteceu na primeira tentativa, com a ignição caindo no emulador e o
    // painel seguindo com os números de dirigir.
    LaunchedEffect(ignicao) {
        if (anterior.value == IgnitionState.ON && ignicao == IgnitionState.OFF) {
            despedindo = true
        } else if (ignicao == IgnitionState.ON) {
            // Ligou de novo: a despedida sai e a tela de dirigir volta.
            despedindo = false
        }
        anterior.value = ignicao
    }
    return despedindo
}

/**
 * Um pedaço da cena, esticado de volta para 0 a 1.
 *
 * É o que permite escrever a coreografia como uma linha do tempo — "o giro vai
 * de 34% a 72% da cena" — em vez de espalhar quatro relógios independentes que
 * precisariam ser mantidos em sincronia à mão.
 */
private fun trecho(cena: Float, inicio: Float, fim: Float): Float =
    ((cena - inicio) / (fim - inicio)).coerceIn(0f, 1f)

/**
 * O desenho do resumo. [live] entra só porque os itens sabem formatar a partir
 * dele; nenhum dado ao vivo aparece aqui — o carro está desligado.
 */
@Composable
internal fun DespedidaDaViagem(
    metricas: TripMetrics,
    live: VehicleLive,
    tinta: Tinta,
    modifier: Modifier = Modifier,
) {
    // Um relógio só para a cena inteira, andando em ritmo constante; quem dá
    // peso a cada movimento é o `easing` de cada trecho. Com um relógio por
    // peça, uma travadinha em qualquer um deles desencontraria o carro dos
    // números, e desencontro numa cena de dois segundos se lê como defeito.
    val alvo = remember { mutableFloatStateOf(0f) }
    val cena by animateFloatAsState(
        targetValue = alvo.floatValue,
        animationSpec = tween(Despedida.CENA_MS, easing = LinearEasing),
        label = "cena",
    )
    LaunchedEffect(Unit) { alvo.floatValue = 1f }

    BoxWithConstraints(modifier.fillMaxSize().padding(6.dp)) {
        val comCarro = maxWidth >= Despedida.CABE_O_CARRO_LARGURA &&
            maxHeight >= Despedida.CABE_O_CARRO_ALTURA

        if (comCarro) CarroSeDespedindo(cena)

        // Sem carro não há por que esperar: o resumo é a cena inteira e entra
        // logo. Com carro, ele só começa quando o H6 já está virando, para que
        // o olho acompanhe uma coisa de cada vez.
        val comeco = if (comCarro) 0.52f else 0f
        val entrada = trecho(cena, comeco, comeco + 0.22f)
        val subida = FastOutSlowInEasing.transform(
            trecho(cena, comeco + 0.06f, comeco + 0.06f + Despedida.SUBIDA_MS / Despedida.CENA_MS.toFloat()),
        )

        // As métricas a caminho do valor final. Interpolar aqui, e não formatar
        // à mão, é o que deixa o número da despedida sair com as mesmas casas
        // decimais e a mesma unidade do número de sempre — inclusive o tempo,
        // que troca de "min" para "h" conforme cresce.
        val emCurso = metricas.copy(
            distanceKm = metricas.distanceKm * subida,
            movingTimeS = metricas.movingTimeS * subida,
            idleTimeS = metricas.idleTimeS * subida,
            fuelLitres = metricas.fuelLitres * subida,
            maxSpeedKmh = metricas.maxSpeedKmh * subida,
        )

        // Com o carro deitado à esquerda, o resumo mora na faixa da direita;
        // sozinho, ele fica com a janela toda.
        val largura = if (comCarro) maxWidth * 0.46f else maxWidth
        ResumoDaViagem(
            emCurso,
            live,
            tinta,
            entrada,
            largura,
            maxHeight,
            Modifier
                .align(if (comCarro) Alignment.CenterEnd else Alignment.Center)
                .width(largura)
                .fillMaxHeight(),
        )
    }
}

/**
 * O H6 entrando em cena: sobe pela frente, gira um quarto de volta e
 * encosta à esquerda, onde para de vez.
 *
 * Monta o carro fechado a partir das mesmas camadas do diagrama — ver
 * [CARRO_INTEIRO] —, sem nada aceso.
 *
 * O giro é no plano, e não em perspectiva, porque o desenho é visto de cima —
 * é literalmente o carro manobrando na vaga.
 */
@Composable
private fun BoxWithConstraintsScope.CarroSeDespedindo(cena: Float) {
    // O quadro do desenho tem 794 x 720, mas o carro dentro dele é estreito e
    // comprido: ocupa cerca de 73% do lado na vertical. Deitado, é esse 73% que
    // vira comprimento, e é por ele que o tamanho é escolhido — o resto do
    // quadro é transparente e pode passar da borda sem que se veja nada.
    val lado = minOf(maxHeight * 1.45f, maxWidth * 0.62f)

    val entrada = FastOutSlowInEasing.transform(trecho(cena, 0f, 0.40f))
    val giro = FastOutSlowInEasing.transform(trecho(cena, 0.34f, 0.72f))
    val lateral = FastOutSlowInEasing.transform(trecho(cena, 0.44f, 0.88f))

    val densidade = LocalDensity.current
    val deBaixo = with(densidade) { (maxHeight * 0.9f).toPx() }
    // Para a esquerda: o carro fica com o nariz apontado para fora da tela e o
    // resumo ocupa o lado que ele desocupou.
    val paraEsquerda = with(densidade) { -(maxWidth * 0.24f).toPx() }

    Box(
        Modifier
            .align(Alignment.Center)
            .size(lado)
            .graphicsLayer {
                alpha = entrada
                translationY = (1f - entrada) * deBaixo
                translationX = lateral * paraEsquerda
                rotationZ = -90f * giro
                // Chega um pouco pequeno, como quem vem de longe, e assenta no
                // tamanho final junto com o fim da entrada.
                val perto = 0.86f + 0.14f * entrada
                scaleX = perto
                scaleY = perto
            },
        contentAlignment = Alignment.Center,
    ) {
        // A base e, por cima, as quatro portas fechadas. Não é firula: a
        // imagem de baixo tem os vãos das portas vazios, porque no diagrama
        // toda porta é sempre desenhada por cima, aberta ou fechada. Sozinha,
        // ela mostra um H6 sem portas.
        CARRO_INTEIRO.forEach { camada ->
            Image(
                painter = painterResource(camada),
                // Decorativo: o que precisa ser lido está nos números ao lado.
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * O H6 fechado, de cima: a lataria e as quatro portas.
 *
 * Sem vidro aberto, sem cinto, sem luz acesa — nada que queira dizer alguma
 * coisa. Na despedida o carro é cenário, e um alerta vermelho no meio de um
 * "boa viagem" mandaria o motorista procurar um problema que não existe.
 */
private val CARRO_INTEIRO = listOf(
    R.drawable.carro_h6,
    R.drawable.carro_porta_dianteira_esquerda_fechada,
    R.drawable.carro_porta_dianteira_direita_fechada,
    R.drawable.carro_porta_traseira_esquerda_fechada,
    R.drawable.carro_porta_traseira_direita_fechada,
)

/** Os quatro números, com o título em cima. */
@Composable
private fun ResumoDaViagem(
    metricas: TripMetrics,
    live: VehicleLive,
    tinta: Tinta,
    entrada: Float,
    largura: Dp,
    altura: Dp,
    modifier: Modifier = Modifier,
) {
    // Lado a lado quando a faixa é bem mais larga que alta; em duas colunas
    // quando é mais quadrada. A mesma regra do resto do painel, pela mesma
    // razão: quem escolhe o formato da janela é o motorista, e nenhuma medida
    // daqui pode ser fixa.
    val emLinha = largura > altura * 2.2f
    val linhas = if (emLinha) 1 else 2
    val alturaDaFatia = altura.value / (linhas + 1f)
    val numero = (alturaDaFatia * 0.62f).coerceIn(11f, 56f)
    val rotulo = (numero * 0.36f).coerceAtLeast(7f)

    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "VIAGEM ENCERRADA",
            color = Cores.TextoApoio,
            fontSize = rotulo.sp,
            letterSpacing = (rotulo * 0.22f).sp,
            maxLines = 1,
            modifier = Modifier
                .alpha(entrada)
                // Sobe dois dedos ao entrar e para. Ver a nota sobre congelar
                // em [Despedida].
                .offset(y = ((1f - entrada) * 6f).dp),
        )

        val pares = Despedida.ITENS.map { it to it.leitura(metricas, live) }
        val filas = if (emLinha) listOf(pares) else pares.chunked(2)

        filas.forEach { fila ->
            Row(
                Modifier.fillMaxWidth().padding(top = (rotulo * 0.5f).dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                fila.forEach { (item, leitura) ->
                    ItemDaDespedida(
                        item.rotulo,
                        leitura,
                        numero,
                        rotulo,
                        tinta,
                        entrada,
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemDaDespedida(
    rotulo: String,
    leitura: Pair<String, String>,
    numero: Float,
    tamanhoDoRotulo: Float,
    tinta: Tinta,
    entrada: Float,
    modifier: Modifier = Modifier,
) {
    val (valor, unidade) = leitura
    Column(modifier.alpha(entrada), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            rotulo,
            color = Cores.TextoApoio,
            fontSize = tamanhoDoRotulo.sp,
            letterSpacing = (tamanhoDoRotulo * 0.08f).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                valor,
                color = tinta.cor,
                fontSize = numero.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                style = tinta.halo?.let { halo ->
                    LocalTextStyle.current.copy(
                        shadow = Shadow(halo, Offset.Zero, blurRadius = numero * 0.5f),
                    )
                } ?: LocalTextStyle.current,
            )
            Text(
                " $unidade",
                color = Cores.TextoApoio,
                fontSize = tamanhoDoRotulo.sp,
                maxLines = 1,
                modifier = Modifier.padding(bottom = (numero * 0.12f).dp),
            )
        }
    }
}
