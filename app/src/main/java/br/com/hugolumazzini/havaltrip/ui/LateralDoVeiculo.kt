package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo.Abertura
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo.Roda
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

        Diagrama(painel)

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Cores.Contorno))
        Spacer(Modifier.height(10.dp))

        Avisos(painel, Modifier.weight(1f))
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
private fun Diagrama(painel: PainelDoVeiculo) {
    val pressoes = painel.pneus.associate { it.roda to it.pressao }
    val abertas = painel.abertas.toSet()

    Column(Modifier.fillMaxWidth()) {
        LinhaDePneus(
            painel, pressoes, Roda.DIANTEIRA_ESQ, Roda.DIANTEIRA_DIR,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp, vertical = 4.dp)
                .aspectRatio(0.62f),
        ) {
            Canvas(Modifier.fillMaxSize()) { desenharCarro(abertas) }
        }
        LinhaDePneus(
            painel, pressoes, Roda.TRASEIRA_ESQ, Roda.TRASEIRA_DIR,
        )
        val unidade = painel.unidadeDePressao
        if (unidade != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "pressão em $unidade",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = Cores.TextoApoio,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LinhaDePneus(
    painel: PainelDoVeiculo,
    pressoes: Map<Roda, Double?>,
    esquerda: Roda,
    direita: Roda,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ValorDePneu(painel, pressoes, esquerda)
        ValorDePneu(painel, pressoes, direita)
    }
}

@Composable
private fun ValorDePneu(painel: PainelDoVeiculo, pressoes: Map<Roda, Double?>, roda: Roda) {
    // Traço, e não zero, quando o sensor não respondeu: um pneu que marca zero
    // seria motivo para parar o carro, e essa não é a informação.
    val valor = pressoes[roda]
    Text(
        valor?.let { TripFormat.decimal(it, if (it >= 100.0) 0 else 1) } ?: "—",
        style = MaterialTheme.typography.titleMedium,
        color = if (valor == null) Cores.TextoApoio else Cores.Texto,
    )
}

/**
 * O contorno do carro e as seis aberturas.
 *
 * Desenhado à mão em vez de virar um arquivo de vetor porque cada traço muda de
 * cor conforme o carro: um `.xml` estático precisaria de doze versões, e uma
 * imagem por estado é o tipo de coisa que sai do lugar quando alguém mexe.
 *
 * Volante à esquerda: a porta do motorista é a dianteira esquerda. É assim que
 * o carro chega ao Brasil, e é o que faz o desenho bater com quem está sentado
 * olhando para ele.
 */
private fun DrawScope.desenharCarro(abertas: Set<Abertura>) {
    val fechado = Cores.Contorno
    val aberto = Cores.Atencao
    fun cor(a: Abertura) = if (a in abertas) aberto else fechado

    val traco = size.minDimension * 0.055f
    val corpo = Stroke(width = traco)
    val raio = size.minDimension * 0.22f

    // Carroceria.
    drawRoundRect(
        color = Cores.Contorno,
        topLeft = Offset(traco / 2, traco / 2),
        size = Size(size.width - traco, size.height - traco),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(raio, raio),
        style = corpo,
    )

    // Capô e porta-malas: barras horizontais nas pontas.
    fun barraHorizontal(y: Float, a: Abertura) = drawLine(
        color = cor(a),
        start = Offset(size.width * 0.26f, y),
        end = Offset(size.width * 0.74f, y),
        strokeWidth = traco * 1.6f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
    barraHorizontal(size.height * 0.10f, Abertura.CAPO)
    barraHorizontal(size.height * 0.90f, Abertura.PORTA_MALAS)

    // As quatro portas: barras verticais nas laterais.
    fun barraVertical(x: Float, centroY: Float, a: Abertura) = drawLine(
        color = cor(a),
        start = Offset(x, centroY - size.height * 0.10f),
        end = Offset(x, centroY + size.height * 0.10f),
        strokeWidth = traco * 1.6f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
    barraVertical(traco / 2, size.height * 0.37f, Abertura.PORTA_MOTORISTA)
    barraVertical(size.width - traco / 2, size.height * 0.37f, Abertura.PORTA_PASSAGEIRO)
    barraVertical(traco / 2, size.height * 0.65f, Abertura.PORTA_TRASEIRA_ESQ)
    barraVertical(size.width - traco / 2, size.height * 0.65f, Abertura.PORTA_TRASEIRA_DIR)

    // O para-brisa dá a dianteira do carro. Sem ele o desenho é simétrico e não
    // se sabe qual ponta é a frente — e aí "porta traseira" não quer dizer nada.
    drawLine(
        color = Cores.Contorno,
        start = Offset(size.width * 0.20f, size.height * 0.24f),
        end = Offset(size.width * 0.80f, size.height * 0.24f),
        strokeWidth = traco * 0.7f,
    )
}

@Composable
private fun Avisos(painel: PainelDoVeiculo, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        when {
            // Sem nenhuma propriedade lida, calar é mais honesto que tranquilizar:
            // o app não tem como garantir porta fechada que nunca leu.
            !painel.algumaLeitura ->
                Linha("Sem leitura do carro", Cores.TextoApoio, Cores.TextoApoio)

            painel.tudoCerto -> Linha("Tudo certo", Cores.Confirmacao)

            else -> painel.avisos.forEach { Linha(it, Cores.Atencao) }
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
