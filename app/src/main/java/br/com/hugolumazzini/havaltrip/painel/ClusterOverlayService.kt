package br.com.hugolumazzini.havaltrip.painel

import android.app.Application
import android.app.Presentation
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.ViewTreeObserver
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
 * Uma View fora de qualquer Activity não tem ciclo de vida; o Compose se recusa
 * a desenhar sem um e derruba o app inteiro.
 */
private class DonoDaDespedida : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
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
 * A despedida como apresentação na tela do painel.
 *
 * Apresentação, e não sobreposição, por causa do histórico no carro: nesta
 * central as apresentações ficam na camada 341000 e as sobreposições na
 * 121000. O mapa do AutoPanel é uma apresentação, e nenhuma sobreposição passa
 * por cima dele; outra apresentação, colocada depois, passa.
 *
 * A cena só começa a andar quando esta apresentação vai desenhar o primeiro
 * quadro, e não recomeça quando ela é recolocada ([EstadoDaDespedida.inicioDaCena]).
 */
private class ApresentacaoDaDespedida(
    contexto: Context,
    display: Display,
    private val janela: JanelaDoPainel,
    private val vm: TripViewModel,
    private val titulo: String,
) : Presentation(contexto, display) {

    private val dono = DonoDaDespedida()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            // Os mesmos do AutoPanel: sem foco, sem toque, tela acesa, tela inteira.
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            )
            setFormat(PixelFormat.TRANSLUCENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setWindowAnimations(0)
            // Para ser achada no `dumpsys`: `Window{… u0 HavalTripDespedida}`.
            attributes = attributes.apply { title = titulo }
        }
        setContentView(
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(dono)
                setViewTreeSavedStateRegistryOwner(dono)
                setViewTreeViewModelStoreOwner(dono)
                setContent {
                    HavalTripTheme {
                        when (janela) {
                            JanelaDoPainel.NUMEROS -> ClusterScreen(vm = vm, espiando = false)
                            JanelaDoPainel.CARRO -> ClusterCarroScreen(vm = vm, espiando = false)
                            JanelaDoPainel.MENU -> ClusterMenuScreen(vm = vm, espiando = false)
                        }
                    }
                }
            },
        )
        // Pronta para o primeiro quadro: só então a cena começa a andar.
        window?.decorView?.viewTreeObserver?.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    window?.decorView?.viewTreeObserver?.removeOnPreDrawListener(this)
                    EstadoDaDespedida.comecarCena()
                    return true
                }
            },
        )
    }

    override fun onStop() {
        dono.encerrar()
        super.onStop()
    }
}

/**
 * Põe a despedida **por cima de tudo** na tela do painel quando o carro desliga.
 *
 * As janelas do painel que o app abre são Activities, e qualquer apresentação
 * de outro app na mesma tela — o mapa do AutoPanel — fica acima delas. Por isso
 * a despedida também vira uma apresentação, aqui, e um vigia a recoloca na
 * frente se outra apresentação for colocada depois dela.
 *
 * Só serve à despedida. Enquanto se dirige, as janelas do painel continuam
 * sendo as Activities.
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

        /** Retira a despedida, a não ser que ela esteja ativa. */
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

    private var apresentacao: ApresentacaoDaDespedida? = null
    private var janelaAtual: JanelaDoPainel? = null
    private var displayIdAtual: Int = -1
    private var vigia: Job? = null

    override fun onCreate() {
        super.onCreate()
        HistoricoDaDespedida.iniciar(applicationContext)

        // O carro ligou de novo: a despedida sai de cima do painel. Não depende
        // de ninguém mandar recolher, porque ela não tem toque e não haveria
        // como tirá-la à mão.
        escopo.launch {
            EstadoDaDespedida.fluxo.collect { ativa -> if (!ativa) esconder() }
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
                    esconder()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun mostrarDespedida(janela: JanelaDoPainel, displayId: Int) {
        if (!EstadoDaDespedida.estaAtiva()) return
        if (apresentacao != null && janelaAtual == janela && displayIdAtual == displayId) return
        if (mostrar(janela, displayId, recolocando = false)) vigiarAFrente(displayId)
        // Depois de mostrar, e não antes: cada décimo de segundo gasto em shell
        // antes é imagem que o painel pode congelar sem a despedida.
        escopo.launch(Dispatchers.IO) { removerOutrosAppsDoDisplay(displayId) }
    }

    /**
     * Mostra uma apresentação nova e só então retira a anterior, se houver.
     * Nessa ordem a tela nunca fica um quadro sem a despedida.
     */
    private fun mostrar(janela: JanelaDoPainel, displayId: Int, recolocando: Boolean): Boolean {
        val display = obterDisplay(displayId)
        if (display == null) {
            Log.w(TAG, "Tela $displayId não encontrada")
            HistoricoDaDespedida.erro("Tela $displayId não encontrada pelo app: a despedida fica só na janela do painel")
            EstadoDaDespedida.comecarCena()
            return false
        }
        if (!::tripViewModel.isInitialized) {
            tripViewModel = TripViewModel(applicationContext as Application)
        }

        val nova = ApresentacaoDaDespedida(this, display, janela, tripViewModel, TITULO)
        try {
            nova.show()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao mostrar a despedida", e)
            HistoricoDaDespedida.erro("Falha ao mostrar a despedida na tela $displayId: ${e.message}")
            EstadoDaDespedida.comecarCena()
            return false
        }

        val anterior = apresentacao
        apresentacao = nova
        janelaAtual = janela
        displayIdAtual = displayId
        anterior?.let { runCatching { it.dismiss() } }

        if (!recolocando) {
            Log.i(TAG, "✅ Despedida mostrada: $janela na tela $displayId")
            HistoricoDaDespedida.projetou("Despedida mostrada como apresentação na tela $displayId ($janela)")
        }
        return true
    }

    /**
     * Confere, no primeiro minuto, se a janela de outro app ficou na frente da
     * despedida, e a recoloca por cima quando ficou.
     *
     * Entre janelas da mesma camada fica na frente a colocada por último. Não dá
     * para impedir que outro app recoloque a dele; dá para conferir e voltar à
     * frente. Contra uma janela de camada mais alta não há o que fazer, e o
     * histórico diz.
     */
    private fun vigiarAFrente(displayId: Int) {
        vigia?.cancel()
        vigia = escopo.launch {
            var recolocadas = 0
            var primeira = true
            for (espera in ESPERAS_DO_VIGIA_MS) {
                delay(espera)
                if (apresentacao == null || !EstadoDaDespedida.estaAtiva()) return@launch

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
                val janela = janelaAtual ?: return@launch
                if (!mostrar(janela, displayId, recolocando = true)) return@launch

                delay(500)
                val depois = withContext(Dispatchers.IO) { lerJanelas() }
                    ?.let { JanelasDoSistema.naFrenteDaNossa(it, displayId, TITULO, packageName) }
                when {
                    depois == null -> HistoricoDaDespedida.info("⬆️ Depois de recolocar: não deu para conferir a ordem")
                    depois.isEmpty() -> HistoricoDaDespedida.info("✅ Depois de recolocar: a despedida está na frente de tudo")
                    depois.map { it.id } == naFrente.map { it.id } -> {
                        HistoricoDaDespedida.erro(
                            "Mesmo recolocada, a despedida continua atrás de ${depois.joinToString { it.descrever() }}: janela de camada mais alta",
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
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao limpar a tela $displayId", e)
            HistoricoDaDespedida.erro("Limpeza da tela $displayId falhou: ${e.message}")
        }
    }

    private fun esconder() {
        val atual = apresentacao ?: return
        Log.i(TAG, "🗑️ Retirando a despedida (ativa: ${EstadoDaDespedida.estaAtiva()})")
        if (EstadoDaDespedida.estaAtiva()) {
            HistoricoDaDespedida.erro("Despedida retirada com a despedida ainda ativa")
        }
        vigia?.cancel()
        vigia = null
        apresentacao = null
        janelaAtual = null
        displayIdAtual = -1
        runCatching { atual.dismiss() }.onFailure { Log.w(TAG, "⚠️ Erro ao retirar a despedida", it) }
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
        esconder()
        super.onDestroy()
    }
}
