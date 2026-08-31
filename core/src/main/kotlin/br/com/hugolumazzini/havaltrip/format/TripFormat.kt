package br.com.hugolumazzini.havaltrip.format

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Formatação compartilhada entre o painel da central e o demo do terminal.
 *
 * Fica no núcleo para os dois mostrarem exatamente o mesmo número: um painel
 * que arredonda diferente do relatório vira dúvida sobre qual está certo.
 *
 * O traço "—" para valor ausente é deliberado: um `0,0 km/L` no lugar de "ainda
 * não dá para saber" é uma informação errada, não uma informação neutra.
 */
object TripFormat {

    const val AUSENTE = "—"

    private val ptBr = Locale.forLanguageTag("pt-BR")

    fun decimal(valor: Double?, casas: Int = 1): String =
        valor?.let { String.format(ptBr, "%.${casas}f", it) } ?: AUSENTE

    fun km(valor: Double?) = valor?.let { "${decimal(it, 1)} km" } ?: AUSENTE

    fun kml(valor: Double?) = valor?.let { "${decimal(it, 1)} km/L" } ?: AUSENTE

    fun kmh(valor: Double?) = valor?.let { "${decimal(it, 0)} km/h" } ?: AUSENTE

    fun litros(valor: Double?) = valor?.let { "${decimal(it, 2)} L" } ?: AUSENTE

    fun percentual(valor: Double?, casas: Int = 1) =
        valor?.let { "${if (it > 0) "+" else ""}${decimal(it, casas)}%" } ?: AUSENTE

    /**
     * Duração em `h:mm` acima de uma hora, `mm:ss` abaixo. Segundo a segundo só
     * importa nos primeiros minutos; depois disso vira ruído piscando na tela.
     */
    fun duracao(segundos: Double?): String {
        if (segundos == null) return AUSENTE
        val total = segundos.roundToLong().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(ptBr, "%d:%02d", h, m) else String.format(ptBr, "%02d:%02d", m, s)
    }

    /** Rótulo da unidade que acompanha [duracao], para o painel. */
    fun unidadeDuracao(segundos: Double?): String =
        if (segundos != null && segundos >= 3600) "h" else "min"
}
