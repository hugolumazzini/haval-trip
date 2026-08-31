package br.com.hugolumazzini.havaltrip.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A mesma paleta da Haval APK Store, que por sua vez veio medida da interface
 * do Impulse. Os três convivem na tela da central; um computador de bordo com
 * cores próprias pareceria app de outro carro.
 */
object Cores {
    /** Fundo do painel de conteúdo — o tom mais escuro da tela. */
    val Fundo = Color(0xFF0A0A0C)

    /** Barra lateral: um degrau acima do fundo, levemente azulada. */
    val Lateral = Color(0xFF0D0E12)

    /** Cartões e blocos de conteúdo. */
    val Superficie = Color(0xFF12141A)

    /** Item selecionado na lateral: azul bem rebaixado. */
    val SuperficieSelecionada = Color(0xFF152233)

    /** Campos de texto e áreas de entrada. */
    val Campo = Color(0xFF262A33)

    /** Azul de destaque: seleção, links e ação primária. */
    val Destaque = Color(0xFF4A9EFF)

    /** Verde de confirmação: Trip contando. */
    val Confirmacao = Color(0xFF34C759)

    /** Âmbar de atenção: Trip pausada ou em espera. */
    val Atencao = Color(0xFFFFB020)

    /** Vermelho de erro, no mesmo registro do iOS/Impulse. */
    val Erro = Color(0xFFFF453A)

    /** Texto de maior peso: números grandes e títulos. */
    val Texto = Color(0xFFF5F5F5)

    /** Texto corrido. */
    val TextoCorrido = Color(0xFFE4E4E5)

    /** Texto de apoio, rótulos de seção e unidades. */
    val TextoApoio = Color(0xFF8A93A3)

    /** Divisores e contornos discretos. */
    val Contorno = Color(0xFF262A33)
}
