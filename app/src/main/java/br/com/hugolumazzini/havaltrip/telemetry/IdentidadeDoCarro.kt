package br.com.hugolumazzini.havaltrip.telemetry

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

/**
 * Descobre se o carro sabe dizer **que carro ele é**.
 *
 * ## Por que isto existe
 *
 * O desenho do carro na tela é um H6 genérico. Para trocar o desenho conforme o
 * modelo, alguém tem de dizer qual é — e a primeira pergunta é se a própria
 * central responde. O app de arte da central (AutoPanel) **não** responde: ele
 * não lê atributo nenhum, escolhe a primeira família de imagens que existir, e
 * como esta central traz todas as seis, o resultado dela num H6 HEV é a arte de
 * um PHEV da Malásia. Ou seja: copiar o critério dele não serve.
 *
 * Resta perguntar ao serviço de veículo. Ele atende por chaves de texto no
 * formato `car.<seção>.<nome>`, e não existe lista publicada delas. Então a
 * sonda faz duas coisas, nesta ordem:
 *
 * 1. **Acha os nomes.** Lê o próprio APK dos aplicativos da GWM instalados na
 *    central e cata de dentro do `dex` todo texto com a cara de uma chave. É o
 *    único inventário que existe: as chaves estão escritas no código de quem as
 *    usa.
 * 2. **Lê as que parecem identidade.** Das encontradas, pede o valor atual só
 *    daquelas cujo nome sugere modelo, versão, motorização ou equipamento.
 *
 * ## O que ela nunca faz
 *
 * Não lê, não guarda e não envia `car.basic.vin_code` nem nada com cara de
 * chassi, placa, IMEI ou número de série. Isso identifica o carro e o dono, não
 * o modelo, e o relatório desta sonda vai para um endereço público. O filtro
 * [ehPessoal] é aplicado depois do filtro de interesse, de propósito: é a
 * última palavra, e uma chave nova de nome esquisito fica de fora por padrão.
 */
object IdentidadeDoCarro {

    /** Pacotes da central que valem folhear atrás de nomes de chave. */
    private val PREFIXOS_DE_PACOTE = listOf(
        "com.beantechs",
        "com.autolink",
        "com.gwm",
        "com.haval",
    )

    /**
     * Uma chave é `car.` + seção + nome, tudo em minúsculas com `_`.
     *
     * O limite de tamanho evita que um texto longo qualquer que comece com
     * `car.` entre na lista; nenhuma chave real do serviço chega perto disso.
     */
    private val FORMATO_DE_CHAVE = Regex("""car\.[a-z0-9_]{2,24}\.[a-z0-9_]{2,40}""")

    /** Palavras que fazem uma chave ser candidata a dizer o modelo. */
    private val PALAVRAS_DE_INTERESSE = listOf(
        "model", "type", "config", "version", "variant", "series", "grade",
        "market", "region", "country", "brand", "project", "platform",
        "powertrain", "power_type", "energy", "fuel_type", "hybrid", "phev",
        "engine", "gearbox", "drive", "awd",
        "sunroof", "skylight", "roof", "door_count", "seat",
    )

    /**
     * Palavras que tiram a chave da lista mesmo que ela tenha passado acima.
     *
     * Identificam o exemplar ou o dono, e não o modelo. Ver o cabeçalho.
     */
    private val PALAVRAS_PESSOAIS = listOf(
        "vin", "chassis", "chassi", "plate", "license", "imei", "iccid",
        "sn", "serial", "uuid", "account", "user", "phone", "owner", "gps",
        "latitude", "longitude", "location",
    )

    private fun ehPessoal(chave: String): Boolean {
        val pedacos = chave.split('.', '_')
        return PALAVRAS_PESSOAIS.any { it in pedacos } ||
            PALAVRAS_PESSOAIS.any { it.length > 3 && it in chave }
    }

    private fun interessa(chave: String): Boolean =
        PALAVRAS_DE_INTERESSE.any { it in chave } && !ehPessoal(chave)

    /** Todos os nomes de chave escritos dentro dos apps da GWM desta central. */
    fun chavesConhecidas(context: Context): Map<String, List<String>> {
        val pm = context.packageManager
        val apps = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
        val achados = sortedMapOf<String, MutableList<String>>()
        apps.filter { app -> PREFIXOS_DE_PACOTE.any { app.packageName.startsWith(it) } }
            .forEach { app ->
                val arquivo = app.sourceDir?.let(::File) ?: return@forEach
                if (!arquivo.canRead()) return@forEach
                runCatching { chavesNoApk(arquivo) }.getOrDefault(emptySet()).forEach { chave ->
                    achados.getOrPut(chave) { mutableListOf() }.add(app.packageName)
                }
            }
        return achados
    }

    /**
     * Cata os nomes dentro de um APK.
     *
     * Lê os `dex` como bytes e procura o padrão direto no texto, sem carregar
     * classe nenhuma: o objetivo é ler nomes, não executar código de terceiro.
     * O `ISO-8859-1` é de propósito — mapeia byte a caractere sem tentar
     * interpretar UTF-8, então nada se perde no meio de um `dex` binário.
     */
    private fun chavesNoApk(arquivo: File): Set<String> = ZipFile(arquivo).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.endsWith(".dex") }
            .flatMap { entrada ->
                val texto = zip.getInputStream(entrada).use { it.readBytes() }
                    .toString(Charsets.ISO_8859_1)
                FORMATO_DE_CHAVE.findAll(texto).map { it.value }
            }
            .toSet()
    }

    /**
     * O relatório da sonda, pronto para o mesmo link público dos outros.
     *
     * Roda fora da thread da tela por conta de quem chama: folhear vários APKs
     * de sistema leva segundos.
     */
    fun relato(context: Context): String = buildString {
        appendLine("=== HAVAL TRIP — sonda de identidade do veículo ===")
        appendLine("(nenhuma chave de chassi, placa ou localização é lida ou enviada)")
        appendLine()

        val todas = chavesConhecidas(context)
        appendLine("--- CHAVES ENCONTRADAS NOS APPS DA CENTRAL: ${todas.size} ---")
        if (todas.isEmpty()) {
            appendLine("(nenhuma — ou os APKs não são legíveis por este app)")
            return@buildString
        }

        val candidatas = todas.keys.filter(::interessa)
        appendLine()
        appendLine("--- CANDIDATAS A DIZER O MODELO: ${candidatas.size} ---")
        val valores = lerValores(candidatas)
        candidatas.forEach { chave ->
            val valor = valores[chave]
            appendLine("$chave = ${valor ?: "(sem resposta)"}   [${todas[chave]?.joinToString()}]")
        }

        appendLine()
        appendLine("--- TODAS AS CHAVES VISTAS (só os nomes) ---")
        todas.keys.filterNot(::ehPessoal).forEach { appendLine(it) }
        val escondidas = todas.keys.count(::ehPessoal)
        if (escondidas > 0) {
            appendLine("($escondidas nome(s) omitido(s) por identificarem o carro ou o dono)")
        }
    }

    /**
     * Pergunta o valor atual de cada candidata ao serviço do carro.
     *
     * Em lotes porque `fetchDatas` de uma lista longa demais pode estourar o
     * limite de uma transação de binder, e aí não viria resposta nenhuma — em
     * vez de vir a maior parte.
     */
    private fun lerValores(chaves: List<String>): Map<String, String> {
        if (chaves.isEmpty()) return emptyMap()
        val servico = ShizukuTelemetrySource.servicoDoCarro() ?: return emptyMap()
        val resposta = mutableMapOf<String, String>()
        chaves.chunked(50).forEach { lote ->
            runCatching {
                val vetor = lote.toTypedArray()
                servico.fetchDatas(vetor).forEachIndexed { i, valor ->
                    if (valor != null) resposta[vetor[i]] = valor
                }
            }
        }
        return resposta
    }

    /** Só para a tela dizer se adianta apertar o botão. */
    fun podeSondar(context: Context): Boolean =
        ShizukuTelemetrySource.autorizado() &&
            runCatching {
                context.packageManager.getInstalledApplications(0)
                    .any { app -> PREFIXOS_DE_PACOTE.any { app.packageName.startsWith(it) } }
            }.getOrDefault(false)
}
