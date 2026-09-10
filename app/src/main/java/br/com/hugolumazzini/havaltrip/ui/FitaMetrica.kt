package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.Cluster
import br.com.hugolumazzini.havaltrip.MedidaDaJanela
import br.com.hugolumazzini.havaltrip.painel.JanelaDoPainel
import kotlin.math.roundToInt

/**
 * A régua: o que a janela do painel mediu de si mesma, lido na central.
 *
 * Por que na central e não sobre a janela: a janela projetada tem dois dedos de
 * altura e fica do outro lado do carro — texto de régua nela sai minúsculo, e
 * ainda cobre um pedaço do painel para ser lido. Aqui é grande, dá para conferir
 * com o carro parado, e não atrapalha nada.
 *
 * As três linhas servem a propósitos diferentes:
 *
 * - **os pixels** dizem qual é o tamanho real da tela do painel, que pode não
 *   ser os 1920x720 que eu supus;
 * - **as frações** são literalmente os números de `TamanhoNoPainel` — o que se
 *   lê aqui é o que se escreve lá;
 * - **o viés** é o de `LugarNoPainel`, na mesma escala de -1 a 1.
 *
 * Ou seja: o motorista acerta a posição com as setas, lê os três valores e me
 * diz. Eu troco as constantes e a predefinição nasce certa para todo mundo, sem
 * ninguém ter de repetir o ajuste.
 */
@Composable
fun QuadroDeMedidas(janela: JanelaDoPainel) {
    val medidas by Cluster.medidas.collectAsStateWithLifecycle()

    Box(
        Modifier
            .background(Color(0xFF0B0B0B), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column {
            linhasDaMedida(medidas[janela]).forEach { linha ->
                Text(
                    linha,
                    color = Color(0xFF7CFF9E),
                    fontSize = 14.sp,
                    // Monoespaçada para as linhas ficarem em colunas: estes
                    // números existem para serem copiados, não lidos de relance.
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * As linhas da régua, separadas do desenho para poderem ser conferidas.
 *
 * `null` é a janela que ainda não apareceu nesta sessão — no primeiro quadro,
 * ou porque nunca foi projetada. Dizer isso é melhor do que mostrar zeros, que
 * pareceriam uma medida ruim em vez de medida nenhuma.
 */
internal fun linhasDaMedida(medida: MedidaDaJanela?): List<String> {
    if (medida == null || medida.janelaLargura <= 0 || medida.janelaAltura <= 0) {
        return listOf(
            "sem medida ainda",
            "abra \"Ver como fica\" ou projete no painel",
        )
    }
    return listOf(
        "janela ${medida.janelaLargura}x${medida.janelaAltura} px",
        "bloco x=${medida.x} y=${medida.y} ${medida.largura}x${medida.altura} px",
        "fracao ${dec(medida.largura, medida.janelaLargura)} x " +
            dec(medida.altura, medida.janelaAltura),
        "vies ${vies(medida.x, medida.largura, medida.janelaLargura)} , " +
            vies(medida.y, medida.altura, medida.janelaAltura),
        "fundo ${fundo(medida.fundoArgb)}, " + if (medida.fundoRedondo) "redondo" else "reto",
    )
}

/**
 * A cor do fundo em hexadecimal, com o nome do que ela faz.
 *
 * O que interessa nela é a primeira dupla — a opacidade. `00` é fundo que não
 * tapa nada, `FF` tapa tudo. Escrever isso por extenso ao lado evita a leitura
 * errada de quem vê `#00000000` e conclui "está preto".
 */
private fun fundo(argb: Long): String {
    val hex = String.format(java.util.Locale.US, "#%08X", argb)
    val opacidade = (argb ushr 24).toInt()
    return when {
        opacidade == 0 -> "$hex nao tapa"
        opacidade < 255 -> "$hex tapa em parte"
        else -> "$hex tapa tudo"
    }
}

/** A fração da janela que o bloco ocupa, com as três casas de `TamanhoNoPainel`. */
private fun dec(parte: Int, todo: Int): String = fmt(parte.toDouble() / todo)

/**
 * O viés do Compose: -1 encostado no começo, 0 no meio, 1 no fim.
 *
 * A folga é o que sobra da janela depois do bloco. Sem folga o bloco preenche a
 * janela e não há viés nenhum a relatar — qualquer valor daria no mesmo lugar,
 * e inventar um número aí só induziria a erro na hora de copiar.
 */
private fun vies(inicio: Int, tamanho: Int, janela: Int): String {
    val folga = janela - tamanho
    if (folga <= 0) return "—"
    return fmt(2.0 * inicio / folga - 1.0)
}

/** Sempre com sinal e três casas, para as linhas não dançarem entre um quadro e outro. */
private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%+.3f", v)

/**
 * A janela conta ao [Cluster] onde este bloco caiu, em pixels.
 *
 * Um `Modifier` e não um cálculo porque o que interessa é onde o bloco *foi
 * parar* depois de alinhamento, empurrão e pisos de tamanho — refazer essa conta
 * à parte seria escrever uma segunda verdade, que é justamente o erro que a
 * régua existe para desfazer.
 *
 * Mede sempre, sem botão para ligar: a medida não custa nada e não aparece na
 * janela: quem a mostra é a central. Um interruptor aqui só criaria o caso de
 * chegar na central e não ter número por ter esquecido de ligá-lo.
 *
 * `boundsInRoot` e não `boundsInWindow`, e isto custou uma leitura errada antes
 * de ficar claro: com a régua na conferência da central, o viés saiu -0,263 onde
 * o código pede -0,360. Os 24 px de diferença eram a barra de status.
 * `boundsInWindow` conta a partir da janela, mas o tamanho com que a fração se
 * compara é o do conteúdo, que começa abaixo das barras do sistema — misturar as
 * duas origens dá um número que não serve para copiar. Na janela projetada no
 * painel não há barra nenhuma e as duas coincidem; foi só na conferência que o
 * erro apareceu, que é justamente para o que ela serve.
 */
fun Modifier.medindo(
    janela: JanelaDoPainel,
    janelaLargura: Int,
    janelaAltura: Int,
    fundoArgb: Long,
    fundoRedondo: Boolean,
): Modifier =
    this.onGloballyPositioned {
        val bloco = it.boundsInRoot()
        Cluster.anotarMedida(
            janela,
            MedidaDaJanela(
                janelaLargura = janelaLargura,
                janelaAltura = janelaAltura,
                x = bloco.left.roundToInt(),
                y = bloco.top.roundToInt(),
                largura = bloco.width.roundToInt(),
                altura = bloco.height.roundToInt(),
                fundoArgb = fundoArgb,
                fundoRedondo = fundoRedondo,
            ),
        )
    }
