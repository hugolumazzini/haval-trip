package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.hugolumazzini.havaltrip.R
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo.Abertura
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo.Assento
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo.Roda
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo.Vidro
import br.com.hugolumazzini.havaltrip.format.TripFormat
import br.com.hugolumazzini.havaltrip.ui.theme.Cores
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloRotulo

/**
 * A faixa lateral da tela de viagem: o estado do carro, não o da viagem.
 *
 * Existe fixa, e não só quando há aviso, por dois motivos. O primeiro é que a
 * pressão dos pneus é informação permanente, não alerta. O segundo é que uma
 * faixa que aparece e some empurraria os quatro números para o lado no instante
 * exato em que algo pede atenção — a tela mudaria de forma bem quando o
 * motorista precisa achar o que mudou.
 */
@Composable
fun LateralDoVeiculo(painel: PainelDoVeiculo, modifier: Modifier = Modifier) {
    Column(
        modifier
            // Quem manda na largura é quem chama, por peso: na tela de viagem a
            // faixa vale um terço da linha. Uma largura fixa aqui dentro
            // venceria o peso de fora e o carro ficaria minúsculo justamente nas
            // telas largas, que é onde há espaço de sobra.
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Cores.Superficie)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text("VEÍCULO", style = EstiloRotulo)
        Spacer(Modifier.height(10.dp))

        // O desenho fica com a sobra de altura, e os avisos com o que pedirem.
        // Ao contrário: um carro de proporção fixa cresceria até empurrar os
        // avisos para fora da faixa — que é justamente a parte que não pode
        // sumir quando algo está aberto.
        Diagrama(painel, Modifier.weight(1f))

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Cores.Contorno))
        Spacer(Modifier.height(10.dp))

        Avisos(painel)
    }
}

/**
 * O carro visto de cima, com as pressões nos quatro cantos.
 *
 * Juntar as duas coisas num desenho só não é enfeite: pneu e porta são ambos
 * "onde no carro", e ler `DE 32` ao lado da roda dianteira esquerda dispensa
 * decorar a sigla.
 */
@Composable
fun Diagrama(
    painel: PainelDoVeiculo,
    modifier: Modifier = Modifier,
    legenda: Boolean = true,
) {
    val pneus = painel.pneus.associateBy { it.roda }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // O corpo da pressão sai da altura do desenho, e não é mais fixo em 26.
        // Fixo, o número nascia do mesmo tamanho na faixa da central e na janela do
        // painel, que tem um terço da altura — e ali ficava ilegível a um braço de
        // distância. Preso à altura, ele acompanha inclusive o botão de + e − do
        // ajuste fino, que é o que o motorista tem na mão para resolver isso.
        val corpo = (maxHeight.value * PRESSAO_NA_ALTURA).coerceIn(13f, 34f).sp

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                // Manda a altura disponível, não a largura: assim o carro encolhe
                // para caber e nunca invade o espaço dos avisos.
                CarroEmCamadas(painel, Modifier.fillMaxHeight().aspectRatio(VITRINE))

                // As pressões por cima, cada uma na altura da sua roda. Ficam ao
                // lado do pneu desenhado, e não numa lista em cima e outra
                // embaixo, porque assim não é preciso decorar sigla nenhuma para
                // saber de que roda é o número.
                // Não a faixa inteira: os números encostados nas bordas do cartão
                // ficavam longe demais das rodas a que se referem. Sobra recortada
                // dos dois lados até quase tocar a lataria — mas só até "quase",
                // porque as portas abertas avançam para fora do contorno do carro e
                // passariam por baixo do número se ele avançasse mais.
                Column(
                    Modifier.fillMaxHeight(0.80f).fillMaxWidth(APROXIMACAO),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    LinhaDePneus(painel, pneus, Roda.DIANTEIRA_ESQ, Roda.DIANTEIRA_DIR, corpo)
                    LinhaDePneus(painel, pneus, Roda.TRASEIRA_ESQ, Roda.TRASEIRA_DIR, corpo)
                }
            }
            // A legenda da unidade fica de fora na janela do painel: lá o espaço é
            // curto e a unidade não muda de uma leitura para a outra, então a linha
            // só tirava altura do desenho.
            if (legenda) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "pressão em ${painel.unidadeDePressao.rotulo}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Cores.TextoApoio,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        }
}

/**
 * Corpo da pressão como fração da altura do desenho, em dp.
 *
 * O 0,11 sai do que já estava certo: na faixa da central o desenho tem cerca de
 * 240 dp de altura e a pressão em 26 sp era do tamanho pedido. Os limites
 * impedem os dois extremos — número maior que o carro numa janela minúscula, e
 * número gigante numa tela cheia.
 */
private const val PRESSAO_NA_ALTURA = 0.11f

@Composable
private fun LinhaDePneus(
    painel: PainelDoVeiculo,
    pneus: Map<Roda, PainelDoVeiculo.Pneu>,
    esquerda: Roda,
    direita: Roda,
    corpo: TextUnit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ValorDePneu(painel, pneus[esquerda], Alignment.Start, corpo)
        // Alinhado à direita para que seja a borda interna — a que fica virada
        // para o carro — a encostar na roda, e não a de fora. Sem isto, "9,9" e
        // "33,9" parariam a distâncias diferentes do pneu.
        ValorDePneu(painel, pneus[direita], Alignment.End, corpo)
    }
}

@Composable
private fun ValorDePneu(
    painel: PainelDoVeiculo,
    pneu: PainelDoVeiculo.Pneu?,
    lado: Alignment.Horizontal,
    corpo: TextUnit,
) {
    // Traço, e não zero, quando o sensor não respondeu: um pneu que marca zero
    // seria motivo para parar o carro, e essa não é a informação.
    val pressao = pneu?.pressao(painel.unidadeDePressao)
    val murcho = pneu != null && pneu.roda in painel.pneusMurchos
    Column(horizontalAlignment = lado) {
        Text(
            pressao?.let { TripFormat.decimal(it, 1) } ?: "—",
            // Corpo bem maior que o do rótulo em volta: a pressão é um número
            // que se confere de relance, sentado ao volante e a um braço de
            // distância da tela. No `titleMedium` padrão ela ficava do tamanho
            // do texto de apoio e obrigava a aproximar o rosto.
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = corpo,
                lineHeight = corpo * 1.15f,
            ),
            // O próprio número muda de cor: o pneu não tem peça no desenho para
            // acender, e é aqui que o olho já está quando procura a pressão.
            color = when {
                murcho -> Cores.Atencao
                pressao == null -> Cores.TextoApoio
                else -> Cores.Texto
            },
        )
        // A temperatura em corpo miúdo: é o que explica a pressão ter subido
        // sozinha depois de meia hora de estrada, e não se lê de relance.
        pneu?.temperaturaC?.let {
            Text(
                "${TripFormat.decimal(it, 0)}°",
                // A temperatura acompanha a pressão pela metade: ela é apoio, e
                // nas janelas pequenas do painel um corpo fixo passaria a
                // competir com o número que interessa.
                style = MaterialTheme.typography.bodySmall.copy(fontSize = corpo * 0.5f),
                color = Cores.TextoApoio,
            )
        }
    }
}

/**
 * Onde fica cada assento dentro do quadro de 794 × 720, em fração de 0 a 1.
 *
 * Medido no `carro_h6.png`: o teto solar panorâmico do H6 é de vidro, e por ele
 * os quatro bancos aparecem na vista de cima. O do meio atrás não aparece — é
 * banco corrido —, então fica entre os dois de fora, que é onde a pessoa senta.
 *
 * Números à mão são o preço de não haver camada pronta para cinto em nenhum dos
 * dois conjuntos de imagem. Se um dia vier uma, isto sai inteiro: uma peça que
 * acende nunca sai do lugar, uma coordenada decorada sai.
 */
private val ASSENTO = mapOf(
    Assento.MOTORISTA to Pair(0.476f, 0.521f),
    Assento.PASSAGEIRO to Pair(0.529f, 0.521f),
    Assento.TRASEIRO_ESQ to Pair(0.472f, 0.642f),
    Assento.TRASEIRO_CENTRO to Pair(0.502f, 0.642f),
    Assento.TRASEIRO_DIR to Pair(0.533f, 0.642f),
)

/** O tamanho da marca do cinto, em fração do quadro. Cabe dentro de um banco. */
private const val MARCA_LARGURA = 0.030f
private const val MARCA_ALTURA = 0.060f

/**
 * Acende o assento de quem está sem cinto, em cima do desenho do carro.
 *
 * Era a única coisa que continuava só em texto, e texto numa faixa estreita não
 * responde à pergunta que se faz de verdade — *quem* está sem cinto. Com o carro
 * ali do lado, apontar o banco responde sem ler nada.
 *
 * Só o banco em falta acende. Marcar os cinco de verde quando está tudo certo
 * encheria o desenho de cor no estado normal, e aí a cor pararia de significar
 * "olhe aqui".
 *
 * Vale um aviso sobre o dado: o carro publica `seat_belt_warning`, que é o
 * *alerta*, não a fivela. Banco vazio e banco com cinto afivelado chegam iguais,
 * em zero. Isto acende quando o carro reclama — nem antes, nem no lugar dele.
 */
@Composable
private fun Cintos(semCinto: List<Assento>, modifier: Modifier = Modifier) {
    if (semCinto.isEmpty()) return
    Canvas(modifier) {
        // Refaz à mão a conta que o `ContentScale.Crop` faz nas imagens: a
        // altura manda, e a sobra de largura sai centrada para fora. Sem repetir
        // isso, a marca ficaria no lugar certo do *quadro* e no lugar errado da
        // *tela* — deslocada exatamente pelo tanto que o recorte come.
        val escala = size.height / 720f
        val larguraDesenhada = 794f * escala
        val esquerda = (size.width - larguraDesenhada) / 2f
        val l = MARCA_LARGURA * larguraDesenhada
        val a = MARCA_ALTURA * size.height

        semCinto.forEach { assento ->
            val (fx, fy) = ASSENTO[assento] ?: return@forEach
            val centroX = esquerda + fx * larguraDesenhada
            val centroY = fy * size.height
            drawRoundRect(
                color = Cores.Atencao,
                topLeft = Offset(centroX - l / 2f, centroY - a / 2f),
                size = Size(l, a),
                cornerRadius = CornerRadius(l * 0.35f),
                // Translúcido: por baixo está o banco desenhado, e apagá-lo
                // deixaria uma pastilha vermelha flutuando sem dizer onde é.
                alpha = 0.75f,
            )
        }
    }
}

/**
 * Onde cada luz acende, em fração do quadro de 794 × 720, com a inclinação da
 * peça em graus.
 *
 * Não são números calculados: foram marcados clicando em cima do próprio
 * desenho, com a ferramenta em `ferramentas/marcar-luzes`. As duas tentativas
 * anteriores partiram de simetria — um deslocamento a partir do meio do carro,
 * o mesmo dos dois lados — e as duas erraram, porque o H6 visto de cima não é
 * simétrico no quadro e as quinas dianteiras e traseiras caem em ângulos
 * diferentes. Aqui cada peça tem o lugar dela e ponto.
 *
 * Se o conjunto de imagens do carro um dia mudar de enquadramento, esta tabela
 * inteira sai do lugar junto — é o mesmo risco que [CAMADA_DA_PORTA] já corre,
 * e a mesma ferramenta refaz.
 */
private data class PontoDeLuz(val x: Float, val y: Float, val angulo: Float)

private val FAROL = listOf(
    PontoDeLuz(0.402f, 0.117f, -40f),
    PontoDeLuz(0.596f, 0.118f, 32f),
)

private val SETA_DIANTEIRA = listOf(
    PontoDeLuz(0.367f, 0.162f, -73f),
    PontoDeLuz(0.627f, 0.157f, 67f),
)

/**
 * A seta de trás, que existe no carro e faltava no desenho.
 *
 * Acende junto com a da frente, sempre: no H6 é o mesmo circuito, e mostrar só
 * a dianteira faria o desenho contradizer quem estivesse olhando o carro.
 */
private val SETA_TRASEIRA = listOf(
    PontoDeLuz(0.373f, 0.822f, 60f),
    PontoDeLuz(0.621f, 0.827f, -69f),
)

private val LANTERNA = listOf(
    PontoDeLuz(0.422f, 0.865f, 13f),
    PontoDeLuz(0.574f, 0.868f, 162f),
)

private val NEBLINA = listOf(
    PontoDeLuz(0.440f, 0.089f, -16f),
    PontoDeLuz(0.555f, 0.092f, -168f),
)

/** A cápsula de luz: comprida no sentido do carro e fina. */
private const val LUZ_COMPRIMENTO = 0.055f
private const val LUZ_ESPESSURA = 0.016f

/**
 * Meio segundo aceso, meio apagado.
 *
 * O ritmo é nosso, não o do carro: seguimos a alavanca da seta, que fica firme,
 * e não a lâmpada — ver `CHAVE_SETA_ESQ`. Uma tela que piscasse pelo broadcast
 * ficaria acesa quando o carro estivesse apagado toda vez que um aviso se
 * atrasasse, e o motorista não tem como saber qual dos dois mentiu.
 */
private const val PISCADA_MS = 500

/** Amarelo de seta. Não é o âmbar de aviso: seta ligada não é problema nenhum. */
private val AMARELO_DA_SETA = Color(0xFFFFB300)

/**
 * Azul-claro de xênon, e não branco.
 *
 * O farol de verdade é branco, mas o carro do desenho também é — e luz branca
 * sobre lataria branca não aparece de jeito nenhum, com halo ou sem. O azul
 * frio é a licença mínima que torna a peça visível e continua se lendo como
 * farol, que é justamente o tom que os faróis de LED puxam à noite.
 */
private val AZUL_DO_FAROL = Color(0xFF8FD0FF)

/** Amarelo quente do neblina, que é a cor com que ele acende de verdade. */
private val AMARELO_DO_NEBLINA = Color(0xFFFFE082)

/** Vermelho de lanterna traseira — a luz de posição de trás, não freio. */
private val VERMELHO_DA_LANTERNA = Color(0xFFFF3B2F)

/**
 * Farol, neblina, lanternas e setas por cima do carro desenhado.
 *
 * Não há camada pronta para nenhum deles nos conjuntos de imagem da central,
 * então são cápsulas desenhadas à mão sobre as coordenadas de [FAROL] e
 * companhia — como já era o caso dos cintos, e com a mesma ressalva: se um dia
 * vier a camada, isto sai.
 *
 * Cada luz acende **na peça em que ela existe no carro**. É o que dispensa
 * legenda: o olho reconhece a posição antes de reconhecer a cor.
 *
 * Nada disto acende quando a propriedade não chegou. Ver [PainelDoVeiculo.Luzes].
 */
@Composable
private fun Luzes(luzes: PainelDoVeiculo.Luzes, modifier: Modifier = Modifier) {
    if (!luzes.algumaLeitura) return

    // Uma piscada só para os quatro cantos: com relógios separados, o
    // pisca-alerta acenderia as setas em contratempo, e no carro elas acendem
    // juntas.
    val relogio = rememberInfiniteTransition(label = "seta")
    val acesa by relogio.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // `keyframes` com dois degraus, e não um `tween`: a seta do carro
            // liga e desliga, não some devagar. Um esmaecimento suave seria
            // outra coisa acontecendo na tela.
            animation = keyframes {
                durationMillis = PISCADA_MS * 2
                1f at 0
                1f at PISCADA_MS - 1
                0f at PISCADA_MS
            },
        ),
        label = "piscada",
    )

    Canvas(modifier) {
        // A mesma conta do `ContentScale.Crop` refeita à mão, como em [Cintos]:
        // a altura manda e a sobra de largura sai centrada para fora.
        val escala = size.height / 720f
        val largura = 794f * escala
        val x0 = (size.width - largura) / 2f

        val comprimento = LUZ_COMPRIMENTO * size.height
        val espessura = LUZ_ESPESSURA * largura

        /**
         * Uma peça acesa: a cápsula e o halo em volta.
         *
         * O halo é metade do efeito. Sem ele a cápsula é um adesivo colorido
         * colado na lataria; com ele o desenho tem o borrão que uma lâmpada faz
         * em volta de si, e é isso que se lê como "está ligada".
         */
        fun acender(ponto: PontoDeLuz, cor: Color, forca: Float, tamanho: Float = 1f) {
            if (forca <= 0f) return
            val centro = Offset(x0 + ponto.x * largura, ponto.y * size.height)
            val comp = comprimento * tamanho
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(cor.copy(alpha = 0.55f), Color.Transparent),
                    center = centro,
                    radius = comp,
                ),
                radius = comp,
                center = centro,
                alpha = forca,
            )
            rotate(degrees = ponto.angulo, pivot = centro) {
                drawRoundRect(
                    color = cor,
                    topLeft = Offset(centro.x - comp / 2f, centro.y - espessura / 2f),
                    size = Size(comp, espessura),
                    cornerRadius = CornerRadius(espessura / 2f),
                    alpha = forca,
                )
            }
        }

        // O farol. O alto desenha a mesma peça maior, que é a única diferença
        // entre os dois que dá para mostrar numa vista de cima.
        if (luzes.baixo == true || luzes.alto == true) {
            val tamanho = if (luzes.alto == true) 1.45f else 1.1f
            FAROL.forEach { acender(it, AZUL_DO_FAROL, forca = 1f, tamanho = tamanho) }
        }

        if (luzes.neblinaDianteira == true) {
            NEBLINA.forEach { acender(it, AMARELO_DO_NEBLINA, forca = 1f) }
        }

        // A lanterna traseira acompanha o farol, porque é assim no carro: não há
        // como andar de farol aceso e lanterna apagada. Ela não tem propriedade
        // própria — o que existe é a luz de posição, e é dela que o vermelho de
        // trás sai quando só o "meia-luz" está ligado. Ver `Luzes.tras`.
        //
        // O neblina de trás não ganha peça própria porque no H6 ele fica dentro
        // da mesma lanterna: acende a mesma luz, mais forte e maior.
        if (luzes.tras || luzes.neblinaTraseira == true) {
            val neblina = luzes.neblinaTraseira == true
            LANTERNA.forEach {
                acender(
                    it,
                    VERMELHO_DA_LANTERNA,
                    forca = if (neblina) 1f else 0.9f,
                    tamanho = if (neblina) 1.3f else 1f,
                )
            }
        }

        // E as setas por último, piscando: no carro elas dividem o bloco óptico
        // com o farol, e é por cima dele que aparecem.
        if (luzes.esquerdaAcesa) {
            acender(SETA_DIANTEIRA[0], AMARELO_DA_SETA, acesa)
            acender(SETA_TRASEIRA[0], AMARELO_DA_SETA, acesa)
        }
        if (luzes.direitaAcesa) {
            acender(SETA_DIANTEIRA[1], AMARELO_DA_SETA, acesa)
            acender(SETA_TRASEIRA[1], AMARELO_DA_SETA, acesa)
        }
    }
}

/**
 * Quanto da faixa as pressões ocupam. Menos que 1 aproxima os números do carro;
 * o limite é a porta aberta, que sai da silhueta e não pode ficar por baixo do
 * texto.
 */
private const val APROXIMACAO = 0.84f

/** A proporção do quadro em que todas as camadas foram desenhadas: 794 × 720. */
private const val QUADRO = 794f / 720f

/**
 * A proporção da janela por onde o carro aparece — bem mais estreita que o quadro.
 *
 * O carro ocupa só a faixa central das imagens: 233 dos 794 pixels de largura,
 * contra 580 dos 720 de altura. Mostrado no quadro inteiro, ele fica pequeno no
 * meio de duas margens vazias, e as pressões terminam a meia tela de distância
 * das rodas a que se referem. Aqui as camadas são desenhadas na altura cheia e o
 * que sobra para os lados é cortado — o carro cresce e os números encostam nele.
 *
 * Cortar pelos dois lados por igual só funciona porque o carro está centrado no
 * quadro: o centro dele cai em 0,4994 da largura, medido no canal de
 * transparência do `carro_h6.png`.
 */
private const val VITRINE = 0.50f

/**
 * As portas que têm uma camada para acender, e qual.
 *
 * As portas e o porta-malas vêm de `pecas_e_variacoes_carro_png`, que é o
 * conjunto realinhado; os vidros e o teto solar vêm do conjunto antigo, o
 * `haval_h6`, que é o único que os tem. Misturar os dois é seguro porque o
 * carro ocupa exatamente o mesmo lugar nos dois: nas duas bases o desenho vai
 * de 280 a 513 na horizontal e de 59 a 639 na vertical, medido no canal de
 * transparência. Se um conjunto novo vier fora desse enquadramento, as peças
 * passam a acender ao lado do carro em vez de em cima dele.
 *
 * O capô não está aqui porque nenhum dos dois conjuntos traz camada para ele.
 * Ele continua avisado, mas em texto: ver [Avisos].
 *
 * Cada porta tem **duas** imagens, e as duas são obrigatórias: a base é o carro
 * com as quatro portas recortadas, não um carro fechado. Onde entra a porta
 * dianteira esquerda, 55% dos pixels da base estão transparentes. Desenhar só a
 * versão aberta — que foi o primeiro erro — deixava um rombo na lataria toda vez
 * que a porta estava fechada, retrovisor e tudo.
 */
private val CAMADA_DA_PORTA = mapOf(
    Abertura.PORTA_MOTORISTA to (
        R.drawable.carro_porta_dianteira_esquerda_fechada to
            R.drawable.carro_porta_dianteira_esquerda_aberta
        ),
    Abertura.PORTA_PASSAGEIRO to (
        R.drawable.carro_porta_dianteira_direita_fechada to
            R.drawable.carro_porta_dianteira_direita_aberta
        ),
    Abertura.PORTA_TRASEIRA_ESQ to (
        R.drawable.carro_porta_traseira_esquerda_fechada to
            R.drawable.carro_porta_traseira_esquerda_aberta
        ),
    Abertura.PORTA_TRASEIRA_DIR to (
        R.drawable.carro_porta_traseira_direita_fechada to
            R.drawable.carro_porta_traseira_direita_aberta
        ),
)

private val CAMADA_DO_VIDRO = mapOf(
    Vidro.MOTORISTA to R.drawable.carro_vidro_dianteiro_esquerdo_aberto,
    Vidro.PASSAGEIRO to R.drawable.carro_vidro_dianteiro_direito_aberto,
    Vidro.TRASEIRO_ESQ to R.drawable.carro_vidro_traseiro_esquerdo_aberto,
    Vidro.TRASEIRO_DIR to R.drawable.carro_vidro_traseiro_direito_aberto,
)

/**
 * O H6 visto de cima, montado empilhando as imagens do próprio carro.
 *
 * São os PNGs que a central usa na sua tela de veículo: um fundo com o carro
 * fechado e, por cima, uma camada transparente por peça aberta, já com o brilho
 * vermelho que o painel do H6 usa. Todas foram desenhadas no mesmo quadro de
 * 794 × 720, então empilhá-las alinhadas basta — não há posição a calcular.
 *
 * Isto substituiu uma silhueta desenhada à mão com traços e polígonos. A troca
 * não é só estética: o desenho à mão precisava que cada porta fosse remarcada em
 * coordenadas de 0 a 1, e uma porta ligeiramente fora do lugar mostrava o carro
 * errado sem que nada quebrasse. Aqui a peça que acende é, literalmente, a peça.
 *
 * Volante à esquerda, como o carro chega ao Brasil: a porta do motorista é a
 * dianteira esquerda, que nas imagens é a de cima à esquerda.
 */
@Composable
private fun CarroEmCamadas(painel: PainelDoVeiculo, modifier: Modifier = Modifier) {
    @Composable
    fun camada(recurso: Int) = Image(
        painter = painterResource(recurso),
        // Sem descrição: o carro inteiro é decorativo para quem não enxerga a
        // tela. O que importa está escrito em [Avisos], que é texto de verdade.
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        // `Crop`, e não `Fit`: preenche a altura e deixa a sobra de largura cair
        // para fora, centrada. Com `Fit` a imagem encolheria até o quadro
        // inteiro caber na vitrine estreita, que é o contrário do que se quer —
        // o carro ficaria menor ainda do que estava.
        contentScale = ContentScale.Crop,
    )

    Box(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        camada(R.drawable.carro_h6)

        // O teto solar antes das portas: é a camada mais interna do carro, e
        // uma porta aberta desenhada por baixo dele ficaria cortada.
        if (painel.tetoSolarAberto == true) camada(R.drawable.carro_teto_solar_aberto)

        // As quatro portas, sempre — a fechada ou a aberta, nunca nenhuma.
        // Percorrer o mapa, e não a lista de abertas, é o que garante isso: a
        // lista de abertas não sabe da existência das que estão fechadas, e foi
        // por segui-la que o carro apareceu sem portas.
        val abertas = painel.abertas.toSet()
        CAMADA_DA_PORTA.forEach { (porta, imagens) ->
            val (fechada, aberta) = imagens
            camada(if (porta in abertas) aberta else fechada)
        }

        // O porta-malas só quando aberto: a tampa fechada já vem desenhada na
        // base, ao contrário das portas.
        if (Abertura.PORTA_MALAS in abertas) camada(R.drawable.carro_porta_malas)

        // Os vidros **depois** das portas, e é isso que os faz aparecer. A faixa
        // vermelha do vidro aberto corre pela borda da porta; desenhada antes,
        // ficava debaixo da camada da porta fechada, que é opaca ali. Vidro
        // aberto e porta fechada é justamente a combinação mais comum das duas.
        painel.vidrosAbertos.forEach { vidro -> CAMADA_DO_VIDRO[vidro]?.let { camada(it) } }

        // E os cintos por cima de tudo: são os únicos que ficam dentro do carro,
        // e nenhuma peça pode passar na frente deles.
        Cintos(painel.semCinto, Modifier.fillMaxSize())

        // As luzes por último: o feixe do farol sai para fora da lataria e a
        // seta encosta na borda, então qualquer camada desenhada depois passaria
        // por cima justamente da parte que interessa.
        Luzes(painel.luzes, Modifier.fillMaxSize())
    }
}

/**
 * O que sobra depois do desenho.
 *
 * Porta, vidro, capô, porta-malas e teto solar **não** entram aqui: eles acendem
 * na peça correspondente do carrinho, que é onde o motorista procura, e repetir
 * cada um como linha de texto enchia a faixa de quatro linhas iguais dizendo o
 * que o desenho já dizia.
 *
 * O cinto também saiu do texto: o banco de quem está sem ele acende no desenho.
 * Uma linha escrita "Cinto traseiro esq." obriga a traduzir a frase em lugar; o
 * banco aceso já é o lugar.
 */
@Composable
private fun Avisos(painel: PainelDoVeiculo, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        when {
            // Sem nenhuma propriedade lida, calar é mais honesto que tranquilizar:
            // o app não tem como garantir porta fechada que nunca leu.
            !painel.algumaLeitura ->
                Linha("Sem leitura do carro", Cores.TextoApoio, Cores.TextoApoio)

            painel.tudoCerto -> Linha("Tudo certo", Cores.Confirmacao)

            else -> {
                // O pneu: o número já está em âmbar logo acima, mas
                // sozinho ele só diz que está baixo comparado a quê. A linha
                // nomeia a roda, que é o que decide de que lado do carro
                // agachar no posto.
                painel.pneusMurchos.forEach { Linha("Pneu ${it.nome}", Cores.Atencao) }
                // O capô é o único que precisa de linha própria: não existe
                // camada para ele no conjunto de imagens, então o desenho fica
                // idêntico com o capô aberto ou fechado.
                if (Abertura.CAPO in painel.abertas) {
                    Linha(Abertura.CAPO.rotulo, Cores.Atencao)
                }
                // Há algo aberto ou alguém sem cinto: o desenho está mostrando
                // qual e quem. Uma palavra basta para o olho ir até lá.
                //
                // O capô não conta: ele já ganhou a própria linha acima, e
                // mandar olhar um desenho que não mudou seria mandar procurar
                // o que não está lá.
                val noDesenho = painel.abertas.any { it != Abertura.CAPO } ||
                    painel.vidrosAbertos.isNotEmpty() || painel.tetoSolarAberto == true ||
                    painel.semCinto.isNotEmpty()
                if (noDesenho) {
                    Linha("Veja o carro acima", Cores.Atencao, Cores.TextoApoio)
                }
            }
        }
    }
}

@Composable
private fun Linha(
    texto: String,
    cor: Color,
    corTexto: Color = Cores.TextoCorrido,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(end = 8.dp)
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(cor),
        )
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = corTexto)
    }
}
