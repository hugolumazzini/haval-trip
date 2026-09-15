package br.com.hugolumazzini.havaltrip.telemetry

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * O inventário das imagens do carro que a própria central guarda.
 *
 * ## Por que existe
 *
 * O H6 desenhado no app veio das imagens da central, mas só as de cima: a
 * lataria e as peças que abrem. A tela de veículo da central mostra o carro em
 * outros ângulos, e ninguém sabe quantas vistas existem lá dentro nem com que
 * nomes — enquanto isso não se souber, não dá para decidir se a despedida pode
 * girar o carro de verdade ou se continua sendo uma foto de cima.
 *
 * A resposta está dentro do carro, e a coleta tem de caber num toque de botão
 * na garagem: por isso ela sai pelo relatório de diagnóstico que já existe, e
 * não por um cabo ligado num notebook.
 *
 * ## Como lê
 *
 * Todo aplicativo instalado é um arquivo `.apk`, que por dentro é um zip comum
 * e é legível pelos outros apps do aparelho. Em vez de pedir imagem por imagem
 * pelo nome — o que exigiria adivinhar os nomes —, aqui o zip é **aberto e
 * listado**. É o único jeito de descobrir o que existe sem saber de antemão o
 * que procurar.
 *
 * Nada é copiado, nada é modificado e nada sai do relatório além de nomes e
 * medidas: o inventário diz "existe uma imagem chamada tal, de tantos por
 * tantos pixels", e nunca a imagem em si.
 */
object ImagensDaCentral {

    /**
     * O app da central que guarda as artes do veículo.
     *
     * É de lá que o AutoPanel puxa as ilustrações de modo de condução, o que
     * torna este o primeiro lugar a procurar pelas outras vistas.
     */
    const val ALVO = "com.beantechs.vehiclecenter"

    /** Só imagens; o resto do `res/` é layout e configuração, que não interessa. */
    private val EXTENSOES = listOf(".png", ".webp", ".jpg", ".jpeg")

    /**
     * Quantas imagens são medidas uma a uma.
     *
     * Medir exige abrir cada arquivo, e um app de sistema tem milhares deles.
     * As maiores são medidas porque arte de carro é pesada; o restante entra
     * só como nome e tamanho, que já basta para reconhecer uma família.
     */
    private const val QUANTAS_MEDIR = 300

    /** Teto de linhas no relatório, para não estourar o site de paste. */
    private const val MAX_LINHAS = 900

    fun inventario(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("--- IMAGENS DO CARRO NA CENTRAL ---")
        sb.appendLine("(nomes e medidas; nenhuma imagem é copiada)")

        vizinhos(context).let { lista ->
            sb.appendLine("apps de veículo instalados: " + if (lista.isEmpty()) "(nenhum encontrado)" else lista.joinToString(", "))
        }

        val info = runCatching { context.packageManager.getApplicationInfo(ALVO, 0) }.getOrNull()
        if (info == null) {
            sb.appendLine("$ALVO não está instalado nesta central — nada a listar.")
            sb.appendLine()
            return sb.toString()
        }

        // Os `splits` entram junto: numa central que recebeu atualização parcial,
        // as imagens podem estar num arquivo separado do principal, e listar só
        // o principal devolveria "não tem nada" com cara de resposta.
        val arquivos = listOfNotNull(info.sourceDir) + (info.splitSourceDirs?.toList() ?: emptyList())
        sb.appendLine("$ALVO: ${arquivos.size} arquivo(s) de aplicativo")

        var restam = MAX_LINHAS
        arquivos.forEach { caminho ->
            runCatching { restam = listar(caminho, sb, restam) }
                .onFailure { sb.appendLine("não foi possível abrir $caminho: ${it.javaClass.simpleName} ${it.message}") }
        }
        if (restam <= 0) {
            sb.appendLine("(lista cortada em $MAX_LINHAS linhas — o resto sai por cabo, se precisar)")
        }
        nomes(context, sb)
        sb.appendLine(provaDeAcesso(context))
        sb.appendLine()
        return sb.toString()
    }

    /**
     * Quem mais no aparelho pode ter arte do veículo.
     *
     * Se as vistas não estiverem no [ALVO], a próxima pergunta é "em qual app,
     * então" — e essa lista responde sem exigir uma segunda viagem ao carro.
     */
    private fun vizinhos(context: Context): List<String> =
        runCatching {
            context.packageManager.getInstalledApplications(0)
                .map { it.packageName }
                .filter { p ->
                    p.contains("vehicle", true) || p.contains("carinfo", true) ||
                        p.contains("beantechs", true) || p.contains("autolink", true)
                }
                .sorted()
        }.getOrDefault(emptyList())

    /** Escreve o que couber e devolve quantas linhas ainda sobram. */
    private fun listar(caminho: String, sb: StringBuilder, limite: Int): Int {
        var restam = limite
        ZipFile(caminho).use { zip ->
            val imagens = zip.entries().asSequence()
                .filter { entrada -> EXTENSOES.any { entrada.name.endsWith(it, true) } }
                .toList()

            sb.appendLine("  imagens no total: ${imagens.size}")
            // O maior primeiro: arte de carro é a coisa mais pesada que um app
            // de central carrega, então o topo desta lista é quase certamente o
            // que se está procurando.
            imagens.sortedByDescending { it.size }.forEachIndexed { indice, entrada ->
                if (restam <= 0) return restam
                val medida = if (indice < QUANTAS_MEDIR) medir(zip, entrada) else null
                sb.appendLine(
                    "  ${entrada.name}  ${entrada.size / 1024} KB" + (medida?.let { "  $it" } ?: "")
                )
                restam--
            }
        }
        return restam
    }

    /**
     * Os nomes pelos quais as imagens podem ser **pedidas**.
     *
     * A lista do zip mostra os arquivos, e isso não basta: um app compilado com
     * encurtamento de recursos guarda a imagem como `res/N8.png`, e nenhum desses
     * nomes serve para pedir nada. O nome de verdade — o que vai no
     * `getIdentifier` — continua registrado na tabela de recursos, e é ele que
     * sai daqui.
     *
     * Não existe função que liste essa tabela, então os números de recurso são
     * percorridos um a um. Eles vêm em blocos contíguos, tipo por tipo, a partir
     * de zero; a varredura para quando encontra [FALHAS_ATE_DESISTIR] vazios
     * seguidos, que é o fim do bloco. Sem essa parada, seriam dezesseis milhões
     * de tentativas e a coleta não terminaria nunca.
     */
    private fun nomes(context: Context, sb: StringBuilder) {
        val recursos = runCatching { context.packageManager.getResourcesForApplication(ALVO) }
            .getOrElse {
                sb.appendLine("  não foi possível ler a tabela de recursos: ${it.javaClass.simpleName}")
                return
            }
        val achados = mutableListOf<String>()
        for (tipo in 1..MAX_TIPOS) {
            var vazios = 0
            for (entrada in 0..MAX_ENTRADAS) {
                if (achados.size >= MAX_NOMES) break
                val id = 0x7f000000 or (tipo shl 16) or entrada
                val nome = runCatching { recursos.getResourceName(id) }.getOrNull()
                if (nome == null) {
                    if (++vazios > FALHAS_ATE_DESISTIR) break
                    continue
                }
                vazios = 0
                // "pacote:tipo/nome" — só as imagens interessam.
                val tipoDoNome = nome.substringAfter(':').substringBefore('/')
                if (tipoDoNome == "drawable" || tipoDoNome == "mipmap") {
                    achados += nome.substringAfter('/')
                }
            }
        }
        sb.appendLine("  nomes de imagem registrados: ${achados.size}")
        achados.sorted().take(MAX_NOMES).forEach { sb.appendLine("  nome: $it") }
    }

    private const val MAX_TIPOS = 40
    private const val MAX_ENTRADAS = 0x1FFF
    private const val FALHAS_ATE_DESISTIR = 96
    private const val MAX_NOMES = 1_500

    /** Largura por altura, sem carregar a imagem na memória. */
    private fun medir(zip: ZipFile, entrada: ZipEntry): String? = runCatching {
        val opcoes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(entrada).use { BitmapFactory.decodeStream(it, null, opcoes) }
        if (opcoes.outWidth > 0) "${opcoes.outWidth}x${opcoes.outHeight}" else null
    }.getOrNull()

    /**
     * Confere, pelo caminho oficial, se dá mesmo para pegar uma imagem do outro
     * app em tempo de execução.
     *
     * Listar o zip prova que o arquivo existe; isto prova que o Android deixa
     * **usar** o que está lá dentro, que é outra pergunta. As duas respostas
     * juntas são o que decide se a animação pode contar com essas artes.
     */
    fun provaDeAcesso(context: Context, nome: String = "b01_malaysia_hev_drivemode0"): String =
        runCatching {
            val outro = context.createPackageContext(ALVO, Context.CONTEXT_IGNORE_SECURITY)
            val id = outro.resources.getIdentifier(nome, "drawable", ALVO)
            if (id == 0) {
                "acesso ao $ALVO: OK, mas não existe drawable chamado '$nome'"
            } else {
                val drawable = outro.resources.getDrawable(id, null)
                "acesso ao $ALVO: OK — '$nome' carregou com " +
                    "${drawable.intrinsicWidth}x${drawable.intrinsicHeight}"
            }
        }.getOrElse { erro ->
            val motivo = if (erro is PackageManager.NameNotFoundException) {
                "o pacote não foi encontrado"
            } else {
                "${erro.javaClass.simpleName}: ${erro.message}"
            }
            "acesso ao $ALVO: NÃO — $motivo"
        }
}
