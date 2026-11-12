package br.com.hugolumazzini.havaltrip.painel

import android.annotation.SuppressLint
import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
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
import br.com.hugolumazzini.havaltrip.domain.JanelaDoSistema
import br.com.hugolumazzini.havaltrip.domain.JanelasDoSistema
import br.com.hugolumazzini.havaltrip.domain.PilhasDoSistema
import br.com.hugolumazzini.havaltrip.ui.ClusterCarroScreen
import br.com.hugolumazzini.havaltrip.ui.ClusterMenuScreen
import br.com.hugolumazzini.havaltrip.ui.ClusterScreen
import br.com.hugolumazzini.havaltrip.ui.theme.HavalTripTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * Põe a despedida **por cima de tudo** na tela do painel.
 *
 * As janelas do painel que o app abre são Activities em modo janela livre, e
 * qualquer outra janela projetada ali — a apresentação do mapa do AutoPanel,
 * por exemplo — fica acima delas. Uma janela de sobreposição (tipo 2038) fica
 * acima das duas, e é por isso que a despedida, quando o carro desliga, vem
 * para cá. Precisa da permissão de "desenhar sobre outros apps", que o app
 * pede ao Shizuku na hora ([garantirPermissaoDeSobreposicao]).
 *
 * Só serve à despedida. Enquanto se dirige, as janelas do painel continuam
 * sendo as Activities; nunca existiu overlay de direção funcionando no carro,
 * e ligá-lo agora só escureceria o painel com uma segunda cópia da bola.
 *
 * Os parâmetros da janela vêm da engenharia reversa do Impulse
 * (haval-app-tool-multimidia).
 */
class ClusterOverlayService : Service() {

    companion object {
        private const val TAG = "ClusterOverlayService"

        private const val EXTRA_JANELA = "janela"
        private const val EXTRA_DISPLAY_ID = "display_id"
        private const val EXTRA_COMANDO = "comando"

        private const val CMD_ESCONDER = "esconder"
        private const val CMD_DESPEDIDA = "despedida"

        /** Como a nossa janela aparece no `dumpsys`: `Window{… u0 HavalTripDespedida}`. */
        private const val TITULO = "HavalTripDespedida"

        /**
         * Quando o vigia confere a ordem, contando a partir da conferência
         * anterior: aos 0,7 s, 2 s, 5 s, 10 s, 20 s, 40 s e 60 s da despedida.
         */
        private val ESPERAS_DO_VIGIA_MS = listOf(700L, 1_300L, 3_000L, 5_000L, 10_000L, 20_000L, 20_000L)

        /** Mais que isso é briga com outro app que também se recoloca na frente. */
        private const val MAX_RECOLOCACOES = 5

        /** Remove o overlay, a não ser que a despedida esteja ativa. */
        fun recolher(context: Context, janela: JanelaDoPainel) {
            val intent = Intent(context, ClusterOverlayService::class.java).apply {
                putExtra(EXTRA_COMANDO, CMD_ESCONDER)
                putExtra(EXTRA_JANELA, janela.name)
            }
            context.startService(intent)
        }

        /** Mostra a despedida por cima de tudo na [displayId] e limpa a tela de outros apps. */
        fun projetarDespedida(context: Context, janela: JanelaDoPainel, displayId: Int) {
            Log.i(TAG, "👋 projetarDespedida: $janela em display $displayId")
            val intent = Intent(context, ClusterOverlayService::class.java).apply {
                putExtra(EXTRA_COMANDO, CMD_DESPEDIDA)
                putExtra(EXTRA_JANELA, janela.name)
                putExtra(EXTRA_DISPLAY_ID, displayId)
            }
            context.startService(intent)
        }
    }

    // TripViewModel criado manualmente (Service não tem ViewModelProvider)
    private lateinit var tripViewModel: TripViewModel

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var janelaAtual: JanelaDoPainel? = null
    private var displayIdAtual: Int = -1
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var donoDoOverlay: DonoDoOverlay? = null
    private var vigia: Job? = null

    override fun onCreate() {
        super.onCreate()
        HistoricoDaDespedida.iniciar(applicationContext)

        // O carro ligou de novo: a despedida sai de cima do painel. Não depende
        // de ninguém mandar recolher, porque o overlay não tem toque e não
        // haveria como tirá-lo à mão.
        escopo.launch {
            EstadoDaDespedida.fluxo.collect { ativa -> if (!ativa) esconderOverlay() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val comando = intent?.getStringExtra(EXTRA_COMANDO)
        val displayId = intent?.getIntExtra(EXTRA_DISPLAY_ID, -1) ?: -1
        val janela = intent?.getStringExtra(EXTRA_JANELA)?.let { nome ->
            JanelaDoPainel.entries.find { it.name == nome }
        }

        when (comando) {
            CMD_DESPEDIDA -> if (janela != null && displayId > 0) mostrarDespedida(janela, displayId)
            CMD_ESCONDER -> if (janela != null) {
                if (EstadoDaDespedida.estaAtiva()) {
                    HistoricoDaDespedida.tentouRemover(janela.toString())
                    HistoricoDaDespedida.bloqueou(janela.toString())
                } else {
                    esconderOverlay()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun mostrarDespedida(janela: JanelaDoPainel, displayId: Int) {
        escopo.launch {
            val permitido = withContext(Dispatchers.IO) { garantirPermissaoDeSobreposicao() }
            // O carro pode ter ligado de novo enquanto o Shizuku respondia.
            if (permitido && EstadoDaDespedida.estaAtiva()) mostrarOverlay(janela, displayId)
            // Depois de mostrar, e não antes: cada décimo de segundo gasto em
            // shell antes é imagem que o painel pode congelar sem a despedida.
            withContext(Dispatchers.IO) { removerOutrosAppsDoDisplay(displayId) }
        }
    }

    /**
     * O app não tem a permissão de desenhar sobre outros apps, e a central não
     * tem tela onde o motorista a conceda. O Shizuku, que já roda os `am` do
     * painel, concede por ele. Desfazer: `appops set <pacote> SYSTEM_ALERT_WINDOW default`.
     */
    private fun garantirPermissaoDeSobreposicao(): Boolean {
        if (Settings.canDrawOverlays(this)) return true
        val resposta = ShizukuShell.rodar("appops set $packageName SYSTEM_ALERT_WINDOW allow")
        val concedida = Settings.canDrawOverlays(this)
        HistoricoDaDespedida.info(
            if (concedida) "🔐 Permissão de sobreposição concedida via Shizuku"
            else "🔐 Permissão de sobreposição NEGADA (Shizuku respondeu: ${resposta?.trim()?.take(120) ?: "nada"})",
        )
        return concedida
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
    private fun mostrarOverlay(janela: JanelaDoPainel, displayId: Int) {
        // Quem decide se a janela mostra os números ou a despedida é o
        // [EstadoDaDespedida], que a própria tela observa; refazê-la não muda nada.
        if (overlayView != null && janelaAtual == janela && displayIdAtual == displayId) return
        esconderOverlay()

        val display = obterDisplay(displayId)
        if (display == null) {
            Log.w(TAG, "Tela $displayId não encontrada: sem overlay")
            HistoricoDaDespedida.info("📽️ Tela $displayId não encontrada pelo app: sem overlay (a despedida fica por conta da janela do painel)")
            return
        }

        val dono = DonoDoOverlay()
        try {
            // Contexto da tela do painel: sem ele a janela nasceria na tela da central.
            val contextoDaTela = createDisplayContext(display)
            val wm = contextoDaTela.getSystemService(WINDOW_SERVICE) as WindowManager

            if (!::tripViewModel.isInitialized) {
                tripViewModel = TripViewModel(applicationContext as Application)
            }

            val view = ComposeView(contextoDaTela).apply {
                setViewTreeLifecycleOwner(dono)
                setViewTreeSavedStateRegistryOwner(dono)
                setViewTreeViewModelStoreOwner(dono)
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        HistoricoDaDespedida.info("📽️ Overlay da despedida anexado à janela")
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        HistoricoDaDespedida.info("📽️ Overlay da despedida desanexado da janela")
                    }
                })
                setContent {
                    HavalTripTheme {
                        when (janela) {
                            JanelaDoPainel.NUMEROS -> ClusterScreen(vm = tripViewModel, espiando = false)
                            JanelaDoPainel.CARRO -> ClusterCarroScreen(vm = tripViewModel, espiando = false)
                            JanelaDoPainel.MENU -> ClusterMenuScreen(vm = tripViewModel, espiando = false)
                        }
                    }
                }
            }

            wm.addView(view, criarLayoutParams())

            // Só depois de a janela existir: se o addView falhar, nada fica guardado.
            windowManager = wm
            overlayView = view
            donoDoOverlay = dono
            janelaAtual = janela
            displayIdAtual = displayId

            Log.i(TAG, "✅ Overlay criado: $janela no display $displayId")
            HistoricoDaDespedida.projetou("Overlay da despedida adicionado na tela $displayId ($janela)")
            vigiarAFrente(displayId)
        } catch (e: Exception) {
            dono.encerrar()
            Log.e(TAG, "❌ Erro ao criar overlay", e)
            HistoricoDaDespedida.erro("Falha ao criar overlay: ${e.message}")
        }
    }

    /**
     * Confere, no primeiro minuto, se a janela de outro app ficou na frente da
     * despedida, e a recoloca por cima quando ficou.
     *
     * Entre janelas do mesmo tipo fica na frente a colocada por último — no
     * carro, a do mapa do AutoPanel chegava depois da nossa e a tapava. Não dá
     * para impedir que ela seja recolocada; dá para conferir e voltar à frente.
     * Contra uma janela de nível mais alto não há o que fazer, e o histórico diz.
     */
    private fun vigiarAFrente(displayId: Int) {
        vigia?.cancel()
        vigia = escopo.launch {
            var recolocadas = 0
            var primeira = true
            for (espera in ESPERAS_DO_VIGIA_MS) {
                delay(espera)
                if (overlayView == null || !EstadoDaDespedida.estaAtiva()) return@launch

                val janelas = withContext(Dispatchers.IO) { lerJanelas() }
                if (janelas == null) {
                    HistoricoDaDespedida.info("🔎 Não deu para ler as janelas da tela $displayId (o Shizuku não respondeu ao dumpsys)")
                    return@launch
                }
                if (primeira) {
                    primeira = false
                    val daTela = janelas.filter { it.tela == displayId && it.visivel }
                    HistoricoDaDespedida.info(
                        "🔎 Janelas visíveis na tela $displayId, de cima para baixo: " +
                            daTela.joinToString(" | ") { it.descrever() }.take(700),
                    )
                }

                val naFrente = JanelasDoSistema.naFrenteDaNossa(janelas, displayId, TITULO, packageName)
                if (naFrente == null) {
                    HistoricoDaDespedida.info("🔎 A despedida não aparece na lista de janelas da tela $displayId")
                    return@launch
                }
                if (naFrente.isEmpty()) continue

                if (recolocadas >= MAX_RECOLOCACOES) {
                    HistoricoDaDespedida.erro(
                        "Desisti: ${naFrente.joinToString { it.dono }} voltou para a frente da despedida depois de $recolocadas recolocações",
                    )
                    return@launch
                }
                recolocadas++
                HistoricoDaDespedida.info(
                    "⬆️ Despedida estava atrás de ${naFrente.joinToString { it.descrever() }}; recolocando na frente ($recolocadas/$MAX_RECOLOCACOES)",
                )
                if (!trazerParaFrente()) return@launch

                delay(500)
                val depois = withContext(Dispatchers.IO) { lerJanelas() }
                    ?.let { JanelasDoSistema.naFrenteDaNossa(it, displayId, TITULO, packageName) }
                when {
                    depois == null -> HistoricoDaDespedida.info("⬆️ Depois de recolocar: não deu para conferir a ordem")
                    depois.isEmpty() -> HistoricoDaDespedida.info("✅ Depois de recolocar: a despedida está na frente de tudo")
                    depois.map { it.id } == naFrente.map { it.id } -> {
                        HistoricoDaDespedida.erro(
                            "Mesmo recolocada, a despedida continua atrás de ${depois.joinToString { it.descrever() }}: janela de nível mais alto",
                        )
                        return@launch
                    }
                    else -> HistoricoDaDespedida.info(
                        "⬆️ Depois de recolocar: passou à frente de parte; ainda atrás de ${depois.joinToString { it.descrever() }}",
                    )
                }
            }
        }
    }

    private fun lerJanelas(): List<JanelaDoSistema>? =
        ShizukuShell.rodar("dumpsys window windows | grep -E '${JanelasDoSistema.FILTRO}'")
            ?.let { JanelasDoSistema.listar(it) }
            ?.takeIf { it.isNotEmpty() }

    /** Tira e põe de novo a mesma janela: a recolocada vai para a frente das do seu nível. */
    private fun trazerParaFrente(): Boolean {
        val view = overlayView ?: return false
        val wm = windowManager ?: return false
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return false
        try {
            wm.removeViewImmediate(view)
        } catch (e: Exception) {
            HistoricoDaDespedida.erro("Falha ao tirar a despedida para recolocar: ${e.message}")
            return false
        }
        return try {
            wm.addView(view, params)
            true
        } catch (e: Exception) {
            HistoricoDaDespedida.erro("Falha ao recolocar a despedida: ${e.message}")
            esquecerOverlay()
            false
        }
    }

    /** Parâmetros descobertos na engenharia reversa do Impulse. */
    private fun criarLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or         // 8
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or     // 16
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or  // 256
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,    // 512
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = TITULO

            @Suppress("DEPRECATION")
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            windowAnimations = 0
        }
    }

    private fun esconderOverlay() {
        val view = overlayView ?: return
        Log.i(TAG, "🗑️ Removendo overlay $janelaAtual (despedida ativa: ${EstadoDaDespedida.estaAtiva()})")
        if (EstadoDaDespedida.estaAtiva()) {
            HistoricoDaDespedida.erro("Overlay $janelaAtual removido com a despedida ativa")
        }

        try {
            windowManager?.removeView(view)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Erro ao remover overlay", e)
        } finally {
            esquecerOverlay()
        }
    }

    private fun esquecerOverlay() {
        vigia?.cancel()
        vigia = null
        overlayView = null
        windowManager = null
        janelaAtual = null
        displayIdAtual = -1
        donoDoOverlay?.encerrar()
        donoDoOverlay = null
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

    override fun onDestroy() {
        esconderOverlay()
        super.onDestroy()
    }
}
