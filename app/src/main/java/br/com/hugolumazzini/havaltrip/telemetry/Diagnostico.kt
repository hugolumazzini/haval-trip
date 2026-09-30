package br.com.hugolumazzini.havaltrip.telemetry

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val TAG = "Diagnostico"

/**
 * Informações sobre o estado do sistema, para a tela de diagnóstico.
 *
 * Nasceu da questão "por que o Shizuku não sobe automaticamente ao ligar o carro?",
 * que tem várias respostas possíveis. Este objeto reúne o que precisa investigar.
 */
object Diagnostico {

    /** Estado completo do sistema, para mostrar na tela. */
    data class EstadoDoSistema(
        val temRoot: Boolean,
        val shizukuInstalado: Boolean,
        val shizukuRodando: Boolean,
        val havalTripAutorizado: Boolean,
        val impulseInstalado: Boolean,
    )

    /**
     * Verifica se a central tem root disponível.
     *
     * Root é o que garante que o Shizuku sempre consegue iniciar - sem ele,
     * depende de wireless debugging que pode desligar após reboot.
     */
    suspend fun verificarRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su -c echo test")
            val exitCode = process.waitFor()
            val temRoot = exitCode == 0
            Log.i(TAG, if (temRoot) "Root disponível" else "Root não disponível")
            temRoot
        } catch (e: Exception) {
            Log.w(TAG, "Verificação de root falhou: ${e.message}")
            false
        }
    }

    /**
     * Coleta o estado completo do sistema.
     *
     * Chamada da tela de diagnóstico para mostrar tudo de uma vez.
     */
    suspend fun verificarSistema(context: Context): EstadoDoSistema {
        val temRoot = verificarRoot()

        val shizukuInstalado = try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        val shizukuRodando = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }

        val havalTripAutorizado = try {
            shizukuRodando && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }

        val impulseInstalado = try {
            context.packageManager.getPackageInfo("br.com.redesurftank.havalshisuku", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        return EstadoDoSistema(
            temRoot = temRoot,
            shizukuInstalado = shizukuInstalado,
            shizukuRodando = shizukuRodando,
            havalTripAutorizado = havalTripAutorizado,
            impulseInstalado = impulseInstalado,
        )
    }

    /**
     * Tenta forçar o Shizuku a iniciar.
     *
     * Usa várias estratégias em sequência: root, service, broadcast e launch.
     * Retorna true se conseguiu fazer o Shizuku rodar.
     */
    suspend fun tentarIniciarShizuku(context: Context): Boolean = withContext(Dispatchers.Default) {
        Log.i(TAG, "Tentando forçar início do Shizuku...")

        // 1. Verifica se já está rodando
        if (Shizuku.pingBinder()) {
            Log.i(TAG, "Shizuku já está rodando")
            return@withContext true
        }

        // 2. Tenta via root (mais confiável)
        if (tentarViaRoot()) {
            delay(2000)
            if (Shizuku.pingBinder()) {
                Log.i(TAG, "✅ Shizuku iniciado via root")
                return@withContext true
            }
        }

        // 3. Tenta via Service
        if (tentarViaService(context)) {
            delay(2000)
            if (Shizuku.pingBinder()) {
                Log.i(TAG, "✅ Shizuku iniciado via Service")
                return@withContext true
            }
        }

        // 4. Tenta via Broadcast
        if (tentarViaBroadcast(context)) {
            delay(2000)
            if (Shizuku.pingBinder()) {
                Log.i(TAG, "✅ Shizuku iniciado via Broadcast")
                return@withContext true
            }
        }

        // 5. Última tentativa: abrir o app
        if (abrirAppShizuku(context)) {
            delay(3000)
            if (Shizuku.pingBinder()) {
                Log.i(TAG, "✅ Shizuku iniciado via Launch")
                return@withContext true
            }
        }

        Log.e(TAG, "❌ Todas tentativas falharam")
        false
    }

    /** Tenta forçar Shizuku via root executando o script de start. */
    private fun tentarViaRoot(): Boolean {
        return try {
            val scriptPath = "/data/user/0/moe.shizuku.privileged.api/start.sh"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh $scriptPath"))
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Log.i(TAG, "Script de start executado via root")
                true
            } else {
                Log.w(TAG, "Script retornou código $exitCode")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tentativa via root falhou: ${e.message}")
            false
        }
    }

    /** Tenta iniciar o serviço Shizuku diretamente. */
    private fun tentarViaService(context: Context): Boolean {
        return try {
            val intent = android.content.Intent()
            intent.setClassName(
                "moe.shizuku.privileged.api",
                "moe.shizuku.server.ShizukuService"
            )
            ContextCompat.startForegroundService(context, intent)
            Log.i(TAG, "Tentativa de iniciar ShizukuService")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Tentativa via Service falhou: ${e.message}")
            false
        }
    }

    /** Tenta enviar broadcast de start para o Shizuku. */
    private fun tentarViaBroadcast(context: Context): Boolean {
        return try {
            val intent = android.content.Intent("moe.shizuku.privileged.api.intent.action.REQUEST_START")
            intent.setPackage("moe.shizuku.privileged.api")
            context.sendBroadcast(intent)
            Log.i(TAG, "Broadcast de start enviado")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Tentativa via Broadcast falhou: ${e.message}")
            false
        }
    }

    /** Abre o app do Shizuku (força inicialização). */
    private fun abrirAppShizuku(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            intent?.let { context.startActivity(it) }
            Log.i(TAG, "App do Shizuku aberto")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Tentativa de abrir app falhou: ${e.message}")
            false
        }
    }
}
