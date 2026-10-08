package br.com.hugolumazzini.havaltrip.painel

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import android.app.Application
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.ui.ClusterCarroScreen
import br.com.hugolumazzini.havaltrip.ui.ClusterMenuScreen
import br.com.hugolumazzini.havaltrip.ui.ClusterScreen
import br.com.hugolumazzini.havaltrip.ui.theme.HavalTripTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Projeta conteúdo do Haval Trip no cluster usando WindowManager overlays.
 *
 * Baseado na engenharia reversa do haval-app-tool-multimidia v1.0.0.89.
 * Usa WindowManager.addView() com parâmetros otimizados para garantir que
 * o conteúdo fique sempre visível, por cima de outros apps (Impulse, AutoPanel).
 *
 * Diferente do ProjetorDoPainel que usa `am start --display`:
 * - WindowManager overlay garante z-order superior
 * - Watchdog reprojeta periodicamente para manter visibilidade
 * - Na despedida, remove overlays de outros apps primeiro
 */
class ClusterOverlayService : Service() {

    companion object {
        private const val TAG = "ClusterOverlayService"

        // Extras
        private const val EXTRA_JANELA = "janela"
        private const val EXTRA_DISPLAY_ID = "display_id"
        private const val EXTRA_DESPEDIDA = "despedida"
        private const val EXTRA_COMANDO = "comando"

        // Comandos
        private const val CMD_MOSTRAR = "mostrar"
        private const val CMD_ESCONDER = "esconder"
        private const val CMD_DESPEDIDA = "despedida"

        // Watchdog - reprojeta a cada 5 segundos para garantir que fica por cima
        private const val WATCHDOG_INTERVAL_MS = 5000L

        /**
         * Projeta uma janela do cluster usando overlay
         */
        fun projetar(context: Context, janela: JanelaDoPainel, displayId: Int) {
            val intent = Intent(context, ClusterOverlayService::class.java).apply {
                putExtra(EXTRA_COMANDO, CMD_MOSTRAR)
                putExtra(EXTRA_JANELA, janela.name)
                putExtra(EXTRA_DISPLAY_ID, displayId)
            }
            context.startService(intent)
        }

        /**
         * Remove overlay de uma janela
         */
        fun recolher(context: Context, janela: JanelaDoPainel) {
            val intent = Intent(context, ClusterOverlayService::class.java).apply {
                putExtra(EXTRA_COMANDO, CMD_ESCONDER)
                putExtra(EXTRA_JANELA, janela.name)
            }
            context.startService(intent)
        }

        /**
         * Projeta despedida (remove outros apps primeiro)
         */
        fun projetarDespedida(context: Context, janela: JanelaDoPainel, displayId: Int) {
            val intent = Intent(context, ClusterOverlayService::class.java).apply {
                putExtra(EXTRA_COMANDO, CMD_DESPEDIDA)
                putExtra(EXTRA_JANELA, janela.name)
                putExtra(EXTRA_DISPLAY_ID, displayId)
                putExtra(EXTRA_DESPEDIDA, true)
            }
            context.startService(intent)
        }
    }

    // TripViewModel criado manualmente (Service não tem ViewModelProvider)
    private lateinit var tripViewModel: TripViewModel

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watchdogJob: Job? = null

    // State
    private var janelaAtual: JanelaDoPainel? = null
    private var displayIdAtual: Int = -1
    private var despedidaAtiva = false

    // WindowManager e View
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val comando = intent?.getStringExtra(EXTRA_COMANDO)
        val janelaNome = intent?.getStringExtra(EXTRA_JANELA)
        val displayId = intent?.getIntExtra(EXTRA_DISPLAY_ID, -1) ?: -1
        val despedida = intent?.getBooleanExtra(EXTRA_DESPEDIDA, false) ?: false

        val janela = janelaNome?.let { nome ->
            JanelaDoPainel.entries.find { it.name == nome }
        }

        when (comando) {
            CMD_MOSTRAR -> {
                if (janela != null && displayId > 0) {
                    mostrarOverlay(janela, displayId, despedida = false)
                }
            }
            CMD_ESCONDER -> {
                if (janela != null) {
                    esconderOverlay()
                }
            }
            CMD_DESPEDIDA -> {
                if (janela != null && displayId > 0) {
                    mostrarDespedida(janela, displayId)
                }
            }
        }

        return START_STICKY
    }

    /**
     * Projeta despedida com prioridade total:
     * 1. Remove overlays de outros apps (Impulse, AutoPanel)
     * 2. Projeta overlay do Haval Trip
     */
    private fun mostrarDespedida(janela: JanelaDoPainel, displayId: Int) {
        Log.i(TAG, "🚗 DESPEDIDA - Limpando outros apps e projetando")

        // Remove outros apps do display ANTES de projetar
        removerOutrosAppsDoDisplay(displayId)

        // Pequena pausa para garantir limpeza
        Thread.sleep(200)

        // Projeta despedida
        mostrarOverlay(janela, displayId, despedida = true)
    }

    /**
     * Remove pilhas de outros apps do display (técnica do Impulse)
     */
    private fun removerOutrosAppsDoDisplay(displayId: Int) {
        try {
            val stackList = ShizukuShell.rodar("am stack list") ?: return
            val pilhasParaRemover = mutableListOf<Int>()

            for (linha in stackList.lines()) {
                val match = Regex("""Stack id=(\d+).*displayId=(\d+)""").find(linha)
                if (match != null) {
                    val stackId = match.groupValues[1].toIntOrNull()
                    val stackDisplayId = match.groupValues[2].toIntOrNull()

                    if (stackDisplayId == displayId && stackId != null) {
                        // Verifica se NÃO é nossa pilha
                        val ehNossa = linha.contains("br.com.hugolumazzini.havaltrip")
                        if (!ehNossa) {
                            pilhasParaRemover.add(stackId)
                        }
                    }
                }
            }

            // Remove pilhas de outros apps
            pilhasParaRemover.forEach { stackId ->
                try {
                    ShizukuShell.rodar("am stack remove $stackId")
                    Log.i(TAG, "✅ Removida pilha $stackId do display $displayId")
                } catch (e: Exception) {
                    Log.w(TAG, "Erro ao remover pilha $stackId", e)
                }
            }

            if (pilhasParaRemover.isNotEmpty()) {
                Log.i(TAG, "🗑️ ${pilhasParaRemover.size} pilha(s) de outros apps removidas")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover outros apps", e)
        }
    }

    @SuppressLint("WrongConstant")
    private fun mostrarOverlay(janela: JanelaDoPainel, displayId: Int, despedida: Boolean) {
        // Se já está mostrando a mesma janela, só garante visibilidade
        if (janelaAtual == janela && displayIdAtual == displayId && overlayView != null) {
            garantirVisibilidade()
            return
        }

        // Remove overlay anterior
        esconderOverlay()

        try {
            // Obtém display do cluster
            val display = obterDisplay(displayId)
            if (display == null) {
                return
            }

            // Cria contexto para o display
            val displayContext = criarContextoDoDisplay(display)

            windowManager = displayContext.getSystemService(WINDOW_SERVICE) as WindowManager

            // Cria ViewModel se ainda não existe
            if (!::tripViewModel.isInitialized) {
                tripViewModel = TripViewModel(applicationContext as Application)
            }

            // Cria ComposeView com conteúdo
            overlayView = ComposeView(displayContext).apply {
                setContent {
                    HavalTripTheme {
                        when (janela) {
                            JanelaDoPainel.NUMEROS -> ClusterScreen(
                                vm = tripViewModel,
                                espiando = false,
                                forcarDespedida = despedida
                            )
                            JanelaDoPainel.CARRO -> ClusterCarroScreen(
                                vm = tripViewModel,
                                espiando = false
                            )
                            JanelaDoPainel.MENU -> ClusterMenuScreen(
                                vm = tripViewModel,
                                espiando = false
                            )
                        }
                    }
                }
            }

            // Parâmetros da engenharia reversa
            val params = criarLayoutParams()

            windowManager?.addView(overlayView, params)

            janelaAtual = janela
            displayIdAtual = displayId
            despedidaAtiva = despedida

            Log.i(TAG, "✅ Overlay criado: $janela no display $displayId (despedida=$despedida)")

            // Inicia watchdog para garantir visibilidade (exceto na despedida)
            if (!despedida) {
                iniciarWatchdog()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao criar overlay", e)
        }
    }

    /**
     * Cria LayoutParams com valores descobertos na engenharia reversa
     */
    private fun criarLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            // Tamanho: MATCH_PARENT para ocupar toda área
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,

            // Type: TYPE_APPLICATION_OVERLAY (2038)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

            // Flags descobertos (792)
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or         // 8
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or     // 16
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or  // 256
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,    // 512

            // Format: TRANSLUCENT (-3)
            PixelFormat.TRANSLUCENT
        ).apply {
            // Gravity: TOP | START (cobre tudo)
            gravity = Gravity.TOP or Gravity.START

            // SystemUiVisibility: modo imersivo (4871)
            @Suppress("DEPRECATION")
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or              // 256
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or     // 512
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or          // 1024
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or            // 2
                View.SYSTEM_UI_FLAG_FULLSCREEN or                 // 4
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY              // 4096
            )

            // Layout in display cutout mode (1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            // Sem animações
            windowAnimations = 0
        }
    }

    /**
     * Watchdog que reprojeta periodicamente para garantir que fica por cima
     */
    private fun iniciarWatchdog() {
        pararWatchdog()

        watchdogJob = escopo.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                garantirVisibilidade()
            }
        }
    }

    private fun pararWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    /**
     * Garante que overlay está visível (traz para frente se necessário)
     */
    private fun garantirVisibilidade() {
        overlayView?.let { view ->
            windowManager?.let { wm ->
                try {
                    // Remove e readiciona para forçar z-order
                    val params = view.layoutParams as? WindowManager.LayoutParams
                    if (params != null) {
                        wm.removeView(view)
                        wm.addView(view, params)
                    }
                } catch (e: Exception) {
                    // Silencioso - não loga em produção
                }
            }
        }
    }

    private fun esconderOverlay() {
        pararWatchdog()

        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao remover overlay", e)
            } finally {
                overlayView = null
                windowManager = null
                janelaAtual = null
                displayIdAtual = -1
                despedidaAtiva = false
            }
        }
    }

    private fun obterDisplay(displayId: Int): Display? {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            dm.getDisplay(displayId)
        } else {
            @Suppress("DEPRECATION")
            dm.displays.find { it.displayId == displayId }
        }
    }

    private fun criarContextoDoDisplay(display: Display): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            createDisplayContext(display)
        } else {
            this
        }
    }

    override fun onDestroy() {
        esconderOverlay()
        super.onDestroy()
    }
}
