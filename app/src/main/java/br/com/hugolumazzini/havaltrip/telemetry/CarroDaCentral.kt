package br.com.hugolumazzini.havaltrip.telemetry

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * O H6 de verdade, em volta inteira, emprestado da própria central.
 *
 * ## Por que existe
 *
 * O carro desenhado no app é uma vista de cima feita à mão. A tela de veículo
 * da central tem outra coisa: **113 fotos do mesmo carro girando**, uma volta
 * completa, na variante certa de motor e de teto. O inventário de imagens
 * provou que elas existem e que dá para carregá-las — ver [ImagensDaCentral].
 * Este objeto é só o passo seguinte: pegá-las.
 *
 * Nada é copiado para dentro do app. As imagens continuam sendo da central e
 * são lidas de lá em tempo de execução, como o próprio app de veículo faz.
 *
 * ## O cuidado com a memória
 *
 * Cada quadro tem por volta de 700x600, o que dá 1,7 MB **aberto na memória**
 * — as 113 fotos inteiras seriam quase 200 MB e derrubariam o app na hora. Por
 * isso duas reduções: só [QUADROS_NA_VOLTA] quadros da volta entram (o olho
 * não distingue 113 passos em dois segundos) e cada um entra pela metade do
 * tamanho, que ainda é o dobro do que a janela do painel mostra.
 */
object CarroDaCentral {

    /** O app da central que guarda as artes; o mesmo do inventário. */
    const val PACOTE = ImagensDaCentral.ALVO

    /** Quantas fotos formam a volta completa, do jeito que a central as numera. */
    const val QUADROS_NO_CARRO = 113

    /**
     * Quantas dessas fotos a animação usa de fato.
     *
     * Uma volta em pouco mais de dois segundos com 24 passos já sai lisa o
     * bastante, e custa um sexto da memória de carregar tudo. Ver a nota sobre
     * memória em [CarroDaCentral].
     */
    const val QUADROS_NA_VOLTA = 24

    /**
     * A variante do carro, do jeito que a central nomeia as artes.
     *
     * Uma central traz **todas** as variantes instaladas, não só a do carro em
     * que ela está: procurar "a primeira que existir" — que é o que o AutoPanel
     * faz — devolve um PHEV para quem tem um HEV. Como a central não conta qual
     * é o carro, quem decide é o motorista; [AUTO] só chuta na ordem desta
     * lista, que começa pela combinação mais comum por aqui.
     */
    enum class Familia(val rotulo: String, val prefixo: String) {
        AUTO("Descobrir sozinho", ""),
        HEV_TETO("HEV com teto solar", "b01_malaysia_hev_skylight_"),
        HEV("HEV", "b01_malaysia_hev_"),
        PHEV_TETO("PHEV com teto solar", "b01_malaysia_phev_skylight_"),
        PHEV("PHEV", "b01_malaysia_phev_"),
        B03_PHEV_TETO("PHEV novo, com teto solar", "b03_malaysia_phev_skylight_"),
        B03_PHEV("PHEV novo", "b03_malaysia_phev_"),
    }

    /** As variantes que esta central realmente tem. */
    fun familiasPresentes(context: Context): List<Familia> {
        val recursos = runCatching { context.packageManager.getResourcesForApplication(PACOTE) }
            .getOrNull() ?: return emptyList()
        return Familia.entries.filter { familia ->
            familia != Familia.AUTO &&
                recursos.getIdentifier(nomeDoQuadro(familia, 0), "drawable", PACOTE) != 0
        }
    }

    /** Qual variante usar de fato, resolvendo o [Familia.AUTO]. */
    fun escolher(context: Context, pedida: Familia): Familia? =
        if (pedida != Familia.AUTO) {
            pedida.takeIf { it in familiasPresentes(context) }
        } else {
            familiasPresentes(context).firstOrNull()
        }

    /** `b01_malaysia_hev_skylight_00042` — cinco dígitos, sempre. */
    private fun nomeDoQuadro(familia: Familia, quadro: Int): String =
        familia.prefixo + quadro.toString().padStart(5, '0')

    /**
     * A volta inteira, pronta para desenhar, ou `null` se esta central não tem
     * a variante pedida.
     *
     * Demora — são duas dúzias de arquivos grandes —, então **não pode ser
     * chamado na thread da tela**. Quem chama é uma corrotina de fundo, e a
     * animação só troca de carro quando a lista chega.
     */
    fun volta(context: Context, familia: Familia): List<ImageBitmap>? {
        val outro = runCatching {
            context.createPackageContext(PACOTE, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return null
        val recursos = outro.resources

        val quadros = (0 until QUADROS_NA_VOLTA).mapNotNull { passo ->
            // Espalhados pela volta inteira: pular de quatro em quatro é o que
            // mantém o giro contínuo em vez de mostrar um quarto do carro.
            val quadro = passo * QUADROS_NO_CARRO / QUADROS_NA_VOLTA
            val id = recursos.getIdentifier(nomeDoQuadro(familia, quadro), "drawable", PACOTE)
            if (id == 0) return@mapNotNull null
            runCatching {
                val opcoes = BitmapFactory.Options().apply {
                    // Metade do lado, um quarto da memória. Ver [CarroDaCentral].
                    inSampleSize = 2
                    // Sem isto o Android reescalaria cada foto para a densidade
                    // da tela do app, que não tem nada a ver com a da central.
                    inScaled = false
                }
                recursos.openRawResource(id).use { fonte ->
                    BitmapFactory.decodeStream(fonte, null, opcoes)
                }?.asImageBitmap()
            }.getOrNull()
        }
        // Meia volta já é uma volta capenga: ou vem inteira ou o desenho de
        // sempre continua sendo melhor do que um carro que trava no meio do giro.
        return quadros.takeIf { it.size == QUADROS_NA_VOLTA }
    }
}
