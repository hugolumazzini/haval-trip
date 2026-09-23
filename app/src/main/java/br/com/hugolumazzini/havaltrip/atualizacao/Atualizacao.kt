package br.com.hugolumazzini.havaltrip.atualizacao

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * A versão publicada no catálogo da loja.
 *
 * @param sha256 impressão digital do APK publicado. É o que separa "baixei a
 *   atualização" de "baixei o que veio pelo fio": sem conferir isto, qualquer
 *   coisa entregue no lugar do arquivo certo seria instalada como se fosse ela.
 */
data class VersaoPublicada(
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
)

/**
 * Busca, baixa e instala a versão nova do próprio app.
 *
 * Consulta as releases do próprio repositório no GitHub, independente de
 * qualquer outro projeto. Cada release pode incluir um arquivo `release.json`
 * com metadados (versionCode, sha256, tamanho). Sem esta informação extra,
 * ainda funciona com a versão do APK como fallback.
 *
 * Tudo por HTTPS. Numa rede de estacionamento, uma resposta em texto claro pode
 * ser reescrita no caminho — e o que se instala passaria a ser escolha de quem
 * está na rede, não sua.
 */
object Atualizacao {

    private const val ACAO_INSTALACAO = "br.com.hugolumazzini.havaltrip.INSTALACAO"

    private const val RELEASES =
        "https://api.github.com/repos/hugolumazzini/haval-trip/releases"

    /** O que está instalado agora, lido do sistema e não de uma constante. */
    fun versaoInstalada(context: Context): Pair<String, Long> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val codigo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return (info.versionName ?: "?") to codigo
    }

    /** Consulta a release mais recente no GitHub e devolve os metadados. */
    suspend fun consultar(context: Context): VersaoPublicada = withContext(Dispatchers.IO) {
        val texto = baixarTexto(RELEASES)

        // A API retorna um array de releases
        val releasesArray = try {
            org.json.JSONArray(texto)
        } catch (e: Exception) {
            // Se falhar ao parsear como array, tenta como objeto
            val obj = JSONObject(texto)
            if (obj.has("message")) throw IllegalStateException(obj.optString("message"))
            throw e
        }

        if (releasesArray.length() == 0) {
            throw IllegalStateException("nenhuma release encontrada no repositório")
        }

        // Pega a primeira release (mais recente)
        val release = releasesArray.getJSONObject(0)

        val tag = release.optString("tag_name")
            .removePrefix("v")
            .takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("release sem tag_name")

        // Procura o APK nos assets
        val apkAsset = release.optJSONArray("assets")?.let { assets ->
            (0 until assets.length()).mapNotNull { i ->
                assets.optJSONObject(i)?.takeIf {
                    it.optString("name") == "app-release.apk"
                }
            }.firstOrNull()
        } ?: throw IllegalStateException("release $tag não tem app-release.apk")

        val apkUrl = apkAsset.optString("browser_download_url")
            .takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("ativo APK não tem URL de download")

        val apkSize = apkAsset.optLong("size")

        // Tenta ler o arquivo de metadados release.json dos assets
        val metadados = release.optJSONArray("assets")?.let { assets ->
            (0 until assets.length()).mapNotNull { i ->
                assets.optJSONObject(i)?.takeIf {
                    it.optString("name") == "release.json"
                }
            }.firstOrNull()
        }?.let { releaseJsonAsset ->
            val url = releaseJsonAsset.optString("browser_download_url")
            if (url.isNotEmpty()) {
                try {
                    JSONObject(baixarTexto(url))
                } catch (e: Exception) {
                    null
                }
            } else null
        }

        // Usa metadados se disponível, senão usa defaults
        val versionCode = metadados?.optLong("versionCode")
            ?: tag.substringAfterLast(".").toLongOrNull()
            ?: 1L
        val sha256 = metadados?.optString("sha256")
            ?: "" // Sem SHA256 vai pular a validação, mas é melhor que nada
        val sizeBytes = metadados?.optLong("sizeBytes") ?: apkSize

        return@withContext VersaoPublicada(
            versionName = tag,
            versionCode = versionCode,
            apkUrl = apkUrl,
            sha256 = sha256,
            sizeBytes = sizeBytes,
        )
    }

    /**
     * Baixa o APK, confere a impressão digital e devolve o arquivo.
     *
     * [aoProgredir] recebe de 0 a 1, ou -1 quando o servidor não diz o tamanho.
     * Um arquivo com hash diferente do publicado é apagado na hora: guardá-lo
     * seria deixar no aparelho um instalador que já se sabe não ser o nosso.
     */
    suspend fun baixar(
        context: Context,
        versao: VersaoPublicada,
        aoProgredir: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val pasta = File(context.cacheDir, "apks").apply { mkdirs() }
        val destino = File(pasta, "haval-trip-${versao.versionCode}.apk")
        if (destino.exists()) destino.delete()

        val conexao = (URL(versao.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            if (conexao.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conexao.responseCode} ao baixar o APK")
            }
            val total = conexao.contentLength.toLong().takeIf { it > 0 } ?: versao.sizeBytes
            var lidos = 0L
            val buffer = ByteArray(64 * 1024)
            conexao.inputStream.use { entrada ->
                destino.outputStream().use { saida ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = entrada.read(buffer)
                        if (n == -1) break
                        saida.write(buffer, 0, n)
                        lidos += n
                        aoProgredir(if (total > 0) lidos.toFloat() / total else -1f)
                    }
                }
            }
        } finally {
            conexao.disconnect()
        }

        // Valida SHA256 se disponível
        if (versao.sha256.isNotEmpty()) {
            val impressao = sha256(destino)
            if (!impressao.equals(versao.sha256, ignoreCase = true)) {
                destino.delete()
                throw IllegalStateException("o arquivo baixado não confere com o publicado")
            }
        }
        destino
    }

    /**
     * Entrega o APK ao instalador do Android.
     *
     * Duas tentativas. A boa é a sessão do `PackageInstaller`, em que o sistema
     * lê o arquivo direto do nosso cache. Se a ROM da central recusar — e as
     * dessas centrais recusam coisas —, cai no diálogo clássico, que instala
     * igual e só não devolve resposta nenhuma para o app.
     */
    suspend fun instalar(context: Context, apk: File) = withContext(Dispatchers.IO) {
        runCatching { porSessao(context, apk) }
            .recoverCatching { porDialogo(context, apk) }
            .getOrThrow()
    }

    private fun porSessao(context: Context, apk: File) {
        val instalador = context.packageManager.packageInstaller
        val parametros = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setSize(apk.length())
        }
        val id = instalador.createSession(parametros)
        ouvirOInstalador(context, apk)
        instalador.openSession(id).use { sessao ->
            sessao.openWrite("base.apk", 0, apk.length()).use { saida ->
                apk.inputStream().use { entrada -> entrada.copyTo(saida, 64 * 1024) }
                sessao.fsync(saida)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val aviso = PendingIntent.getBroadcast(
                context,
                id,
                Intent(ACAO_INSTALACAO).setPackage(context.packageName),
                flags,
            )
            sessao.commit(aviso.intentSender)
        }
    }

    /**
     * Escuta o que o sistema responde à sessão e leva o pedido de confirmação à tela.
     *
     * Sem isto, `commit()` volta sem erro nenhum e não acontece mais nada: o
     * Android manda um aviso de "preciso que a pessoa confirme" para o nosso
     * `PendingIntent`, e se ninguém abrir a tela que vem dentro dele a
     * instalação fica parada para sempre, calada. Foi exatamente o que
     * aconteceu na primeira tentativa no emulador.
     *
     * Se o sistema recusar a sessão, aqui é o único lugar que fica sabendo —
     * `commit()` já voltou —, então a queda para o diálogo clássico também
     * mora aqui.
     */
    private fun ouvirOInstalador(context: Context, apk: File) {
        val app = context.applicationContext
        val ouvinte = object : BroadcastReceiver() {
            override fun onReceive(quem: Context, intent: Intent) {
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val tela: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        }
                        tela?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        tela?.let { app.startActivity(it) }
                    }
                    // Sucesso mata este processo junto com a versão antiga;
                    // qualquer outro desfecho ainda tem o diálogo clássico.
                    PackageInstaller.STATUS_SUCCESS -> runCatching { app.unregisterReceiver(this) }
                    else -> {
                        runCatching { app.unregisterReceiver(this) }
                        runCatching { porDialogo(app, apk) }
                    }
                }
            }
        }
        val filtro = IntentFilter(ACAO_INSTALACAO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(ouvinte, filtro, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(ouvinte, filtro)
        }
    }

    private fun porDialogo(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun baixarTexto(endereco: String): String {
        val conexao = (URL(endereco).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            // O GitHub guarda o arquivo cru em cache agressivo; sem isto, o
            // catálogo recém-publicado pode demorar a aparecer no carro.
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (conexao.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conexao.responseCode} ao ler o catálogo")
            }
            return conexao.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conexao.disconnect()
        }
    }

    private fun sha256(arquivo: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        arquivo.inputStream().use { entrada ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = entrada.read(buffer)
                if (n == -1) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
