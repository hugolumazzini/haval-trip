package br.com.hugolumazzini.havaltrip.atualizacao

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
 * O catálogo é o mesmo arquivo que alimenta a Haval APK Store, lido direto do
 * repositório: assim não existe uma segunda lista para manter em dia, e publicar
 * pela loja é o que faz a atualização aparecer aqui.
 *
 * Tudo por HTTPS. Numa rede de estacionamento, uma resposta em texto claro pode
 * ser reescrita no caminho — e o que se instala passaria a ser escolha de quem
 * está na rede, não sua.
 */
object Atualizacao {

    private const val CATALOGO =
        "https://raw.githubusercontent.com/hugolumazzini/haval-apk-store/main/catalog.json"

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

    /** Lê o catálogo e devolve a entrada deste app, ou `null` se ele não estiver lá. */
    suspend fun consultar(context: Context): VersaoPublicada = withContext(Dispatchers.IO) {
        val texto = baixarTexto(CATALOGO)
        val apps = JSONObject(texto).getJSONArray("apps")
        for (i in 0 until apps.length()) {
            val app = apps.getJSONObject(i)
            if (app.optString("packageName") != context.packageName) continue
            return@withContext VersaoPublicada(
                versionName = app.optString("versionName"),
                versionCode = app.optLong("versionCode"),
                apkUrl = app.optString("apkUrl"),
                sha256 = app.optString("sha256"),
                sizeBytes = app.optLong("sizeBytes"),
            )
        }
        throw IllegalStateException("o catálogo da loja não tem uma entrada para este app")
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

        val impressao = sha256(destino)
        if (!impressao.equals(versao.sha256, ignoreCase = true)) {
            destino.delete()
            throw IllegalStateException("o arquivo baixado não confere com o publicado")
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
        instalador.openSession(id).use { sessao ->
            sessao.openWrite("base.apk", 0, apk.length()).use { saida ->
                apk.inputStream().use { entrada -> entrada.copyTo(saida, 64 * 1024) }
                sessao.fsync(saida)
            }
            // O aviso do sistema vai para a Activity que já está na frente; não
            // há resultado a tratar aqui porque, dando certo, este processo
            // morre junto com a versão antiga.
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val aviso = PendingIntent.getBroadcast(
                context,
                id,
                Intent("br.com.hugolumazzini.havaltrip.INSTALACAO"),
                flags,
            )
            sessao.commit(aviso.intentSender)
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
