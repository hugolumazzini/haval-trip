package br.com.hugolumazzini.havaltrip.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Só existe tema escuro. É um painel dirigido à noite tanto quanto de dia, e
 * uma tela que clareasse ao meio-dia ofuscaria o motorista no túnel seguinte.
 */
private val Escuro = darkColorScheme(
    primary = Cores.Destaque,
    onPrimary = Cores.Fundo,
    primaryContainer = Cores.SuperficieSelecionada,
    onPrimaryContainer = Cores.Destaque,
    secondary = Cores.Confirmacao,
    onSecondary = Cores.Fundo,
    background = Cores.Fundo,
    onBackground = Cores.Texto,
    surface = Cores.Superficie,
    onSurface = Cores.TextoCorrido,
    surfaceVariant = Cores.Campo,
    onSurfaceVariant = Cores.TextoApoio,
    surfaceContainer = Cores.Superficie,
    surfaceContainerHigh = Cores.Campo,
    outline = Cores.Contorno,
    outlineVariant = Cores.Contorno,
    error = Cores.Erro,
    onError = Cores.Texto,
    inverseSurface = Cores.Campo,
    inverseOnSurface = Cores.Texto,
)

@Composable
fun HavalTripTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Escuro,
        typography = TipografiaCarro,
        content = content,
    )
}
