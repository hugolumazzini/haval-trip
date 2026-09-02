package br.com.hugolumazzini.havaltrip.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import br.com.hugolumazzini.havaltrip.R

/** Michroma para a marca e os rótulos de seção. Só tem um peso: nunca negrita. */
val Michroma = FontFamily(Font(R.font.michroma_regular, FontWeight.Normal))

/** IBM Plex Sans para o resto: legibilidade alta em tela pequena e de relance. */
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

/** A marca "HAVAL TRIP" no alto da barra lateral. */
val EstiloMarca = TextStyle(
    fontFamily = Michroma,
    fontSize = 19.sp,
    letterSpacing = 0.02.em,
    color = Cores.Texto,
)

/** Rótulo de seção e de quadrante: maiúsculas espaçadas, em cinza de apoio. */
val EstiloRotulo = TextStyle(
    fontFamily = Michroma,
    fontSize = 12.sp,
    letterSpacing = 0.16.em,
    color = Cores.TextoApoio,
)

/**
 * O número de cada quadrante do painel.
 *
 * Tamanho grande e tabular de propósito: é lido de relance, com o carro em
 * movimento. Dígito de largura fixa impede que o valor "dance" na tela a cada
 * atualização — movimento periférico rouba atenção de quem está dirigindo.
 */
val EstiloNumeroGrande = TextStyle(
    fontFamily = PlexSans,
    fontSize = 62.sp,
    fontWeight = FontWeight.SemiBold,
    color = Cores.Texto,
)

/** Número de apoio, na faixa superior e nos detalhes. */
val EstiloNumeroMedio = TextStyle(
    fontFamily = PlexSans,
    fontSize = 22.sp,
    fontWeight = FontWeight.SemiBold,
    color = Cores.Texto,
)

/** Tela de carro: tudo um degrau maior que o padrão do Material. */
val TipografiaCarro = Typography(
    headlineSmall = TextStyle(fontFamily = PlexSans, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = PlexSans, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = PlexSans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = PlexSans, fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = PlexSans, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = PlexSans, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = PlexSans, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = PlexSans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = PlexSans, fontSize = 13.sp, fontWeight = FontWeight.Medium),
)
