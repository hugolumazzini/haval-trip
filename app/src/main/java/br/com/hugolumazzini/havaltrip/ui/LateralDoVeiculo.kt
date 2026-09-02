package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo.Abertura
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
            .width(190.dp)
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
private fun Diagrama(painel: PainelDoVeiculo, modifier: Modifier = Modifier) {
    val pneus = painel.pneus.associateBy { it.roda }

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            // Proporção real do H6: 1,89 m de largura por 4,65 m de
            // comprimento. Manda a altura disponível, não a largura: assim o
            // carro encolhe para caber e nunca invade o espaço dos avisos.
            Canvas(Modifier.fillMaxHeight().aspectRatio(0.405f)) { desenharCarro(painel) }

            // As pressões por cima, cada uma na altura da sua roda. Ficam ao
            // lado do pneu desenhado, e não numa lista em cima e outra
            // embaixo, porque assim não é preciso decorar sigla nenhuma para
            // saber de que roda é o número.
            Column(
                Modifier.fillMaxHeight(0.80f).fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                LinhaDePneus(painel, pneus, Roda.DIANTEIRA_ESQ, Roda.DIANTEIRA_DIR)
                LinhaDePneus(painel, pneus, Roda.TRASEIRA_ESQ, Roda.TRASEIRA_DIR)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "pressão em ${painel.unidadeDePressao.rotulo}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = Cores.TextoApoio,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LinhaDePneus(
    painel: PainelDoVeiculo,
    pneus: Map<Roda, PainelDoVeiculo.Pneu>,
    esquerda: Roda,
    direita: Roda,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ValorDePneu(painel, pneus[esquerda])
        ValorDePneu(painel, pneus[direita])
    }
}

@Composable
private fun ValorDePneu(painel: PainelDoVeiculo, pneu: PainelDoVeiculo.Pneu?) {
    // Traço, e não zero, quando o sensor não respondeu: um pneu que marca zero
    // seria motivo para parar o carro, e essa não é a informação.
    val pressao = pneu?.pressao(painel.unidadeDePressao)
    Column {
        Text(
            pressao?.let { TripFormat.decimal(it, 1) } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            color = if (pressao == null) Cores.TextoApoio else Cores.Texto,
        )
        // A temperatura em corpo miúdo: é o que explica a pressão ter subido
        // sozinha depois de meia hora de estrada, e não se lê de relance.
        pneu?.temperaturaC?.let {
            Text(
                "${TripFormat.decimal(it, 0)}°",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = Cores.TextoApoio,
            )
        }
    }
}

/**
 * O H6 visto de cima, com as seis aberturas como painéis que acendem.
 *
 * Desenhado à mão em vez de virar um arquivo de vetor porque cada painel muda de
 * cor conforme o carro: um `.xml` estático precisaria de dezenas de versões, e
 * uma imagem por estado é o tipo de coisa que sai do lugar quando alguém mexe.
 *
 * As proporções são as do H6 de verdade — 4,65 m por 1,89 m, cabine recuada,
 * capô longo — porque é o que faz o motorista reconhecer o próprio carro em vez
 * de ver um retângulo genérico. Todas as coordenadas são frações do quadro, de
 * 0 a 1, para o desenho servir em qualquer tamanho: `y = 0` é o para-choque
 * dianteiro, `y = 1` é o traseiro, `x = 0` é o lado do motorista.
 *
 * Volante à esquerda: a porta do motorista é a dianteira esquerda. É assim que
 * o carro chega ao Brasil, e é o que faz o desenho bater com quem está sentado
 * olhando para ele.
 */
private fun DrawScope.desenharCarro(painel: PainelDoVeiculo) {
    val abertas = painel.abertas.toSet()
    val vidrosAbertos = painel.vidrosAbertos.toSet()
    val traco = size.minDimension * 0.030f
    val fino = Stroke(width = traco * 0.8f)
    val contorno = Stroke(width = traco * 1.4f)

    fun ponto(x: Float, y: Float) = Offset(size.width * x, size.height * y)

    /** Um polígono em coordenadas de 0 a 1, já fechado. */
    fun forma(vararg pares: Pair<Float, Float>) = Path().apply {
        pares.forEachIndexed { i, (x, y) ->
            val p = ponto(x, y)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }

    /**
     * Desenha um painel: contorno sempre, e por dentro um preenchimento âmbar
     * translúcido quando está aberto.
     *
     * O preenchimento é translúcido de propósito. Cor chapada apagaria o traço
     * que dá a forma da porta, e o aviso ficaria uma mancha sem lugar; assim a
     * peça continua reconhecível e ainda assim salta aos olhos.
     */
    fun peca(caminho: Path, aberto: Boolean) {
        if (aberto) drawPath(caminho, Cores.Atencao.copy(alpha = 0.55f))
        drawPath(caminho, if (aberto) Cores.Atencao else Cores.Contorno, style = fino)
    }

    fun painel(caminho: Path, abertura: Abertura?) =
        peca(caminho, abertura != null && abertura in abertas)

    // Carroceria: uma silhueta simétrica, com o bico mais estreito que a
    // traseira, como num SUV. As curvas evitam o ar de caixa do desenho antigo.
    val carroceria = Path().apply {
        moveTo(ponto(0.50f, 0.005f).x, ponto(0.50f, 0.005f).y)
        fun curva(c1: Pair<Float, Float>, c2: Pair<Float, Float>, fim: Pair<Float, Float>) {
            cubicTo(
                ponto(c1.first, c1.second).x, ponto(c1.first, c1.second).y,
                ponto(c2.first, c2.second).x, ponto(c2.first, c2.second).y,
                ponto(fim.first, fim.second).x, ponto(fim.first, fim.second).y,
            )
        }
        curva(0.24f to 0.010f, 0.09f to 0.045f, 0.065f to 0.13f)   // canto dianteiro esq.
        curva(0.035f to 0.28f, 0.035f to 0.70f, 0.065f to 0.87f)   // flanco esquerdo
        curva(0.085f to 0.965f, 0.24f to 0.995f, 0.50f to 0.995f)  // canto traseiro esq.
        curva(0.76f to 0.995f, 0.915f to 0.965f, 0.935f to 0.87f)  // canto traseiro dir.
        curva(0.965f to 0.70f, 0.965f to 0.28f, 0.935f to 0.13f)   // flanco direito
        curva(0.91f to 0.045f, 0.76f to 0.010f, 0.50f to 0.005f)   // canto dianteiro dir.
        close()
    }
    // A silhueta em cinza mais claro que os painéis internos: é ela que precisa
    // ser lida como carro de longe; as divisões são detalhe de segunda leitura.
    drawPath(carroceria, Cores.TextoApoio, style = contorno)

    // Capô: da grade até a base do para-brisa.
    painel(
        forma(0.13f to 0.055f, 0.87f to 0.055f, 0.84f to 0.30f, 0.16f to 0.30f),
        Abertura.CAPO,
    )

    // Para-brisa. É ele que diz qual ponta é a frente — sem isso o desenho fica
    // simétrico e "porta traseira" deixa de querer dizer alguma coisa.
    painel(forma(0.16f to 0.305f, 0.84f to 0.305f, 0.77f to 0.42f, 0.23f to 0.42f), null)

    // Teto, com o teto solar por dentro. O H6 tem teto panorâmico, e é o retângulo
    // interno que faz o desenho parecer o carro certo e não um carro qualquer.
    painel(forma(0.22f to 0.425f, 0.78f to 0.425f, 0.78f to 0.79f, 0.22f to 0.79f), null)
    peca(
        forma(0.29f to 0.465f, 0.71f to 0.465f, 0.71f to 0.70f, 0.29f to 0.70f),
        painel.tetoSolarAberto == true,
    )

    // Vidro traseiro e tampa do porta-malas.
    painel(forma(0.24f to 0.795f, 0.76f to 0.795f, 0.81f to 0.885f, 0.19f to 0.885f), null)
    painel(
        forma(0.19f to 0.89f, 0.81f to 0.89f, 0.78f to 0.965f, 0.22f to 0.965f),
        Abertura.PORTA_MALAS,
    )

    /**
     * Uma porta e o vidro dela: a faixa entre o flanco e a lateral do teto,
     * com uma tira mais estreita por dentro representando o vidro.
     *
     * Os dois no mesmo lugar porque no carro são a mesma peça, e porque foi
     * exatamente isso que faltou no primeiro teste: o vidro aberto virava linha
     * de texto e não aparecia no desenho, onde o motorista procura.
     */
    fun porta(esquerda: Boolean, deY: Float, ateY: Float, qual: Abertura, vidro: Vidro) {
        val foraX = if (esquerda) 0.045f else 0.955f
        val dentroX = if (esquerda) 0.215f else 0.785f
        val vidroX = if (esquerda) 0.145f else 0.855f
        painel(forma(foraX to deY, dentroX to deY, dentroX to ateY, foraX to ateY), qual)
        peca(
            forma(vidroX to deY + 0.02f, dentroX to deY + 0.02f, dentroX to ateY - 0.02f, vidroX to ateY - 0.02f),
            vidro in vidrosAbertos,
        )
    }
    porta(true, 0.44f, 0.615f, Abertura.PORTA_MOTORISTA, Vidro.MOTORISTA)
    porta(true, 0.62f, 0.785f, Abertura.PORTA_TRASEIRA_ESQ, Vidro.TRASEIRO_ESQ)
    porta(false, 0.44f, 0.615f, Abertura.PORTA_PASSAGEIRO, Vidro.PASSAGEIRO)
    porta(false, 0.62f, 0.785f, Abertura.PORTA_TRASEIRA_DIR, Vidro.TRASEIRO_DIR)

    // As rodas, por fora da carroceria. Existem para as pressões terem onde
    // encostar: sem elas os números ficariam soltos ao lado de um contorno.
    fun roda(x: Float, y: Float) = drawLine(
        color = Cores.Contorno,
        start = ponto(x, y - 0.055f),
        end = ponto(x, y + 0.055f),
        strokeWidth = traco * 2.6f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
    roda(0.025f, 0.30f)
    roda(0.975f, 0.30f)
    roda(0.025f, 0.775f)
    roda(0.975f, 0.775f)

    // Retrovisores: dois riscos para fora, na altura do para-brisa. Custam dois
    // traços e são o detalhe que faz a silhueta ser lida como carro na hora.
    fun retrovisor(deX: Float, paraX: Float) = drawLine(
        color = Cores.Contorno,
        start = ponto(deX, 0.395f),
        end = ponto(paraX, 0.36f),
        strokeWidth = traco * 1.2f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
    retrovisor(0.05f, 0.005f)
    retrovisor(0.95f, 0.995f)
}

/**
 * O que sobra depois do desenho.
 *
 * Porta, vidro, capô, porta-malas e teto solar **não** entram aqui: eles acendem
 * na peça correspondente do carrinho, que é onde o motorista procura, e repetir
 * cada um como linha de texto enchia a faixa de quatro linhas iguais dizendo o
 * que o desenho já dizia.
 *
 * Cinto continua em texto porque assento não tem como ser desenhado nesta
 * escala sem virar mancha — e porque é o único aviso que fala de gente.
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
                painel.semCinto.forEach { Linha(it.rotulo, Cores.Atencao) }
                // Sem cinto solto, ainda assim há algo aberto: o desenho está
                // mostrando qual. Uma palavra basta para o olho ir até lá.
                if (painel.semCinto.isEmpty()) {
                    Linha("Veja o carro ao lado", Cores.Atencao, Cores.TextoApoio)
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
