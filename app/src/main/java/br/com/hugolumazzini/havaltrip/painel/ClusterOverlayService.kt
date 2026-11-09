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
import br.com.hugolumazzini.havaltrip.domain.PilhasDoSistema
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
 * Uma View solta no WindowManager, fora de qualquer Activity, não tem ciclo de
 * vida; o Compose se recusa a desenhar sem um e derruba o app inteiro.
 */
private class DonoDoOverlay : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registro = LifecycleRegistry(this)
    private val controlador = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registro
    override val savedStateRegistry: SavedStateRegistry get() = controlador.savedStateRegistry

    init {
        controlador.performAttach()
        controlador.performRestore(null)
        registro.currentState = Lifecycle.State.RESUMED
    }

    fun encerrar() {
        registro.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

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
            Log.i(TAG, "👋 projetarDespedida: $janela em display $displayId")
            val intent = Intent(context, ClusterOverlayService::class.java).apply {
                putExtra(EXTRA_COMANDO, CMD_DESPEDIDA)
                putExtra(EXTRA_JANELA, janela.name)
                putExtra(EXTRA_DISPLAY_ID, displayId)
                putExtra(EXTRA_DESPEDIDA, true)
            }
            context.startService(intent)
            Log.d(TAG, "✅ Intent de despedida enviado")
        }
    }

    // TripViewModel criado manualmente (Service não tem ViewModelProvider)
    private lateinit var tripViewModel: TripViewModel

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watchdogJob: Job? = null

    // State
    private var janelaAtual: JanelaDoPainel? = null
    private var displayIdAtual: Int = -1

    // WindowManager e View
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var donoDoOverlay: DonoDoOverlay? = null

    override fun onCreate() {
        super.onCreate()
        HistoricoDaDespedida.iniciar(applicationContext)

        // O vigia traz a view para a frente refazendo-a, o que reinicia a
        // animação. Na despedida isso arruinaria a imagem parada; quando o
        // carro liga de novo, o overlay (que já voltou aos números sozinho,
        // pelo mesmo estado) volta a ser vigiado.
        escopo.launch {
            EstadoDaDespedida.fluxo.collect { ativa ->
                if (ativa) pararWatchdog() else if (overlayView != null) iniciarWatchdog()
            }
        }
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
                    // PROTEÇÃO EXTRA: Verifica se despedida está ativa
                    // MESMO QUE alguém chame recolher(), NÃO remove se despedida ativa
                    if (EstadoDaDespedida.estaAtiva()) {
                        Log.w(TAG, "⚠️ CMD_ESCONDER BLOQUEADO - Despedida está ativa!")
                        Log.w(TAG, "   Janela: $janela")
                        Log.w(TAG, "   Ignorando pedido de remoção")

                        // Registra bloqueio no histórico visual
                        HistoricoDaDespedida.tentouRemover(janela.toString())
                        HistoricoDaDespedida.bloqueou(janela.toString())
                    } else {
                        esconderOverlay()
                    }
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
        Log.i(TAG, "🚗 DESPEDIDA - projetando $janela no display $displayId")

        // Primeiro a despedida, depois a limpeza: o painel congela a última
        // imagem pouco depois de o carro desligar, e cada décimo de segundo
        // gasto em shell antes de mostrar é imagem que não chega a existir.
        mostrarOverlay(janela, displayId, despedida = true)
        escopo.launch(Dispatchers.IO) { removerOutrosAppsDoDisplay(displayId) }
    }

    /**
     * Tira da tela do painel as janelas de outros apps, para a despedida ser a
     * única coisa nela. Nunca as nossas: ver [PilhasDoSistema].
     */
    private fun removerOutrosAppsDoDisplay(displayId: Int) {
        try {
            val saida = ShizukuShell.rodar("am stack list")
            if (saida == null) {
                HistoricoDaDespedida.info("🧹 Limpeza da tela $displayId pulada: o Shizuku não respondeu ao 'am stack list'")
                return
            }

            val daTela = PilhasDoSistema.listar(saida, packageName).filter { it.tela == displayId }
            val remover = daTela.filter { !it.nossa }.map { it.id }
            val nossas = daTela.count { it.nossa }

            remover.forEach { id ->
                runCatching { ShizukuShell.rodar("am stack remove $id") }
                    .onFailure { Log.w(TAG, "Não deu para remover a pilha $id", it) }
            }

            val resumo = "🧹 Limpeza da tela $displayId: ${daTela.size} pilha(s), " +
                "$nossas nossa(s) preservada(s), ${remover.size} de outros apps removida(s) $remover"
            Log.i(TAG, resumo)
            HistoricoDaDespedida.info(resumo)
            if (daTela.isEmpty()) {
                HistoricoDaDespedida.info("🧹 Nenhuma pilha na tela $displayId. Saída do comando: ${saida.take(300)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao limpar a tela $displayId", e)
            HistoricoDaDespedida.erro("Limpeza da tela $displayId falhou: ${e.message}")
        }
    }

    @SuppressLint("WrongConstant")
    private fun mostrarOverlay(janela: JanelaDoPainel, displayId: Int, despedida: Boolean) {
        // Se já está mostrando a mesma janela, não há o que refazer: quem decide
        // se ela mostra os números ou a despedida é o [EstadoDaDespedida], que a
        // própria tela observa.
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
                Log.w(TAG, "Tela $displayId não encontrada: sem overlay")
                if (despedida) {
                    HistoricoDaDespedida.info("📽️ Tela $displayId não encontrada pelo app: sem overlay (a despedida fica por conta da janela do painel)")
                }
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
            val dono = DonoDoOverlay()
            donoDoOverlay = dono
            overlayView = ComposeView(displayContext).apply {
                setViewTreeLifecycleOwner(dono)
                setViewTreeSavedStateRegistryOwner(dono)
                setViewTreeViewModelStoreOwner(dono)
                if (despedida) {
                    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            HistoricoDaDespedida.info("📽️ Overlay da despedida anexado à janela")
                        }

                        override fun onViewDetachedFromWindow(v: View) {
                            HistoricoDaDespedida.info("📽️ Overlay da despedida desanexado da janela")
                        }
                    })
                }
                setContent {
                    HavalTripTheme {
                        when (janela) {
                            JanelaDoPainel.NUMEROS -> ClusterScreen(
                                vm = tripViewModel,
                                espiando = false,
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

            Log.i(TAG, "✅ Overlay criado: $janela no display $displayId (despedida=$despedida)")
            if (despedida) {
                HistoricoDaDespedida.projetou("Overlay da despedida adicionado na tela $displayId ($janela)")
            }

            // O vigia refaz a view de tempos em tempos para trazê-la à frente, e
            // refazer reinicia a animação. Os números aguentam isso; a despedida
            // é uma imagem que deve ficar parada a noite toda, então não.
            if (!EstadoDaDespedida.estaAtiva()) iniciarWatchdog()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao criar overlay", e)

            // REGISTRA NO HISTÓRICO VISUAL!
            if (despedida) {
                HistoricoDaDespedida.erro("Falha ao criar overlay: ${e.message}")
            }
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

        Log.i(TAG, "🐕 Watchdog iniciado (intervalo: ${WATCHDOG_INTERVAL_MS}ms)")
        var contador = 0

        watchdogJob = escopo.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                contador++
                if (contador % 12 == 0) {  // Log a cada 60s (12 x 5s)
                    Log.d(TAG, "🐕 Watchdog ativo (${contador * WATCHDOG_INTERVAL_MS / 1000}s), despedida=${EstadoDaDespedida.estaAtiva()}")
                }
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
        if (EstadoDaDespedida.estaAtiva()) return
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
                    // SE DER ERRO, o overlay foi REMOVIDO mas NÃO readicionado!
                    // Isso faz a despedida sumir sem deixar rastro!
                    Log.e(TAG, "❌ ERRO em garantirVisibilidade(): overlay REMOVIDO mas não readicionado!", e)

                    // REGISTRA NO HISTÓRICO VISUAL!
                    if (EstadoDaDespedida.estaAtiva()) {
                        HistoricoDaDespedida.erro("garantirVisibilidade FALHOU: ${e.message}")
                    }

                    // Limpa estado porque view foi removida mas não está mais válida
                    overlayView = null
                    windowManager = null
                    janelaAtual = null
                    displayIdAtual = -1
                    donoDoOverlay?.encerrar()
                    donoDoOverlay = null
                }
            }
        }
    }

    private fun esconderOverlay() {
        val view = overlayView ?: return
        Log.i(TAG, "🗑️ Removendo overlay $janelaAtual (despedida ativa: ${EstadoDaDespedida.estaAtiva()})")
        if (EstadoDaDespedida.estaAtiva()) {
            HistoricoDaDespedida.erro("Overlay $janelaAtual removido com a despedida ativa")
        }

        pararWatchdog()
        try {
            windowManager?.removeView(view)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Erro ao remover overlay", e)
        } finally {
            overlayView = null
            windowManager = null
            janelaAtual = null
            displayIdAtual = -1
            donoDoOverlay?.encerrar()
            donoDoOverlay = null
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

    // Antes do Android 11 isto devolvia o próprio serviço, ligado à tela da
    // central: no carro (Android 9) o overlay nascia na tela errada.
    private fun criarContextoDoDisplay(display: Display): Context = createDisplayContext(display)

    override fun onDestroy() {
        esconderOverlay()
        super.onDestroy()
    }
}
