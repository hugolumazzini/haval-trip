package br.com.hugolumazzini.havaltrip.painel

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Se o carro acabou de ser desligado e a despedida é o que deve estar na tela.
 *
 * Mora aqui, e não dentro da tela, porque a tela é refeita: a janela do painel
 * pode ser fechada e aberta de novo, a view pode ser tirada e recolocada. O que
 * uma tela "lembra" some junto com ela; este estado não. Toda janela que
 * desenha a despedida consulta [fluxo], e por isso a mostra mesmo nascendo
 * depois de o carro já ter sido desligado.
 */
object EstadoDaDespedida {
    private const val TAG = "EstadoDaDespedida"

    private val _ativa = MutableStateFlow(false)
    val fluxo: StateFlow<Boolean> = _ativa.asStateFlow()

    /** Marcar ANTES de projetar, para nenhuma janela remover a despedida no meio do caminho. */
    fun ativar(origem: String) {
        val jaEstava = _ativa.getAndUpdate { true }
        Log.i(TAG, "👋 DESPEDIDA ATIVADA ($origem, já estava ativa: $jaEstava)")
        if (!jaEstava) HistoricoDaDespedida.ativou(origem)
    }

    /** O carro ligou de novo: as janelas voltam aos números de dirigir. */
    fun desativar(origem: String) {
        val estava = _ativa.getAndUpdate { false }
        if (estava) {
            Log.i(TAG, "🔌 DESPEDIDA DESATIVADA ($origem)")
            HistoricoDaDespedida.desativou(origem)
        }
    }

    fun estaAtiva(): Boolean = _ativa.value

    /**
     * O relógio da cena: o instante em que ela começa, em `SystemClock.uptimeMillis`.
     *
     * Mais de uma janela desenha a despedida — a do painel na hora em que o carro
     * desliga, a apresentação por cima dela um pouco depois, outra apresentação
     * se for preciso recolocá-la. Contando cada uma do seu próprio começo, a cena
     * recomeçava, e no painel do carro a despedida parecia começar duas vezes.
     * Contando todas deste instante, a que chega depois continua do mesmo quadro.
     *
     * E o instante é o da apresentação entrar na tela, não o do carro desligar:
     * montar a apresentação ocupa o app por meio segundo e congela a janela de
     * baixo, e uma cena já andando engasgava e saltava. Até lá a cena fica no
     * primeiro quadro, que é tela preta.
     */
    private val _inicioDaCena = MutableStateFlow<Long?>(null)
    val inicioDaCena: StateFlow<Long?> = _inicioDaCena.asStateFlow()

    /** O carro desligou e a cena ainda espera a apresentação para começar. */
    @Volatile
    var cenaEsperando = false
        private set

    /** Se a apresentação não vier (tela não achada, erro), a cena começa assim mesmo. */
    private const val ESPERA_MAXIMA_MS = 1_500L

    private val principal = Handler(Looper.getMainLooper())
    private val porSeguranca = Runnable { comecarCena() }

    fun carroDesligou() {
        // Um segundo aviso de "desligou" sem um "ligou" no meio é o mesmo
        // desligar (o simulador avisa por dois caminhos): zerar aqui faria a
        // cena recomeçar no meio.
        if (cenaEsperando || _inicioDaCena.value != null) return
        principal.removeCallbacks(porSeguranca)
        _inicioDaCena.value = null
        cenaEsperando = true
        principal.postDelayed(porSeguranca, ESPERA_MAXIMA_MS)
    }

    /** A apresentação entrou na tela, ou não vai entrar: a cena começa agora. */
    fun comecarCena() {
        if (!cenaEsperando) return
        cenaEsperando = false
        principal.removeCallbacks(porSeguranca)
        _inicioDaCena.value = SystemClock.uptimeMillis()
    }

    /** Ligado, não há cena em curso: uma conferência da despedida começa do zero. */
    fun carroLigou() {
        principal.removeCallbacks(porSeguranca)
        cenaEsperando = false
        _inicioDaCena.value = null
    }
}
