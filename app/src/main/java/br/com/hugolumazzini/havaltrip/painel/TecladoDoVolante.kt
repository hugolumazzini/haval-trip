package br.com.hugolumazzini.havaltrip.painel

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import com.beantechs.inputservice.IInputListener
import com.beantechs.inputservice.IInputService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "TecladoDoVolante"

/** Uma tecla do teclado do volante, já traduzida do código cru da central. */
enum class TeclaDoVolante {
    CIMA,
    BAIXO,
    ENTRA,
    VOLTA,
    ;

    companion object {
        /**
         * Os códigos que a central manda.
         *
         * Não são os do Android: 1024 não é `KEYCODE_DPAD_UP`. São os do
         * teclado do volante do H6, e só se descobrem observando o serviço de
         * teclas. Os que não estão aqui — os botões de mídia, o de voz — não
         * nos interessam e são ignorados.
         */
        fun de(codigo: Int): TeclaDoVolante? = when (codigo) {
            1024 -> CIMA
            1025 -> BAIXO
            1028 -> ENTRA
            1030 -> VOLTA
            else -> null
        }
    }
}

/**
 * As teclas do volante, para quem estiver mostrando algo no painel.
 *
 * ## Por que existe
 *
 * O painel não tem toque. Se a nossa janela vai ter mais de uma visão — o
 * carro, a viagem de hoje, a do mês —, a única forma de trocar entre elas é a
 * cruzinha do volante, que é como o motorista já navega no painel do carro.
 *
 * ## O que isto não faz
 *
 * Não rouba a tecla de ninguém. A central só nos **avisa** do que foi
 * apertado; quem manda no painel nativo continua sendo ele. Numa página que
 * não tem nada nativo — a bola vazia — não há disputa, porque não há o que
 * responder do outro lado. Numa que tem, o painel do carro vai reagir junto, e
 * é por isso que a escolha da página, na Configuração, importa.
 *
 * ## Por que um objeto único e não um por tela
 *
 * O registro é por processo e custa uma ligação de serviço. Duas telas
 * registrando dariam dois avisos para a mesma tecla, e um "desce dois" a cada
 * toque.
 */
object TecladoDoVolante {

    /**
     * Todas as teclas: é o que o `-1` quer dizer para este serviço.
     *
     * Pedir a lista exata seria mais educado, mas os códigos do volante não são
     * os do Android e uma lista errada nos deixaria surdos sem erro nenhum.
     * Filtrar depois, em [TeclaDoVolante.de], custa nada.
     */
    private val TODAS = intArrayOf(-1)

    private val _teclas = MutableSharedFlow<TeclaDoVolante>(extraBufferCapacity = 8)

    /** Cada toque, uma vez. Ver [SharedFlow]: quem chega depois não recebe o antigo. */
    val teclas: SharedFlow<TeclaDoVolante> = _teclas.asSharedFlow()

    private val _ligado = MutableStateFlow(false)

    /** Se as teclas estão mesmo chegando nesta central. */
    val ligado: StateFlow<Boolean> = _ligado.asStateFlow()

    private val _ultimoCodigo = MutableStateFlow<Int?>(null)

    /**
     * O último código cru que a central mandou, mesmo os que não entendemos.
     *
     * Sem isto, "apertei e não aconteceu nada" não se distingue de "chegou um
     * código que [TeclaDoVolante.de] joga fora", e as duas se consertam de
     * formas opostas. Aparece na Configuração exatamente por isso.
     */
    val ultimoCodigo: StateFlow<Int?> = _ultimoCodigo.asStateFlow()

    private val _ultimaAcao = MutableStateFlow<Int?>(null)

    /**
     * Se o último aviso foi uma tecla descendo (0) ou sendo solta (1).
     *
     * Distingue "o volante está mudo" de "o volante fala, e nós é que estávamos
     * descartando o que ele diz" — que foi o defeito. Ver [viDescida].
     */
    val ultimaAcao: StateFlow<Int?> = _ultimaAcao.asStateFlow()

    private val _quantasChegaram = MutableStateFlow(0)

    /** Quantas teclas a central já mandou desde que o app abriu. */
    val quantasChegaram: StateFlow<Int> = _quantasChegaram.asStateFlow()

    /**
     * Se esta central manda a descida da tecla, e não só o soltar.
     *
     * Um teclado comum manda o par — descer e soltar —, e reagir aos dois
     * andaria duas visões por toque; por isso a primeira versão ouvia só a
     * descida. No carro isso deixou o volante mudo: não dá para saber, de fora,
     * o que este serviço manda, e descartar o que ele mandava foi o mesmo que
     * não estar ouvindo.
     *
     * Agora quem decide é o que chega. Enquanto nenhuma descida tiver aparecido,
     * o soltar vale por toque; na primeira descida, o soltar passa a ser
     * ignorado para sempre e o par volta a contar uma vez só.
     */
    private var viDescida = false

    private val ouvinte = object : IInputListener.Stub() {
        override fun dispatchKeyEvent(evento: KeyEvent?) {
            if (evento == null) return
            // Anotado antes de qualquer filtro: o evento que este código
            // descarta é justamente o que precisa aparecer no diagnóstico
            // quando "apertei e não aconteceu nada".
            _ultimoCodigo.value = evento.keyCode
            _ultimaAcao.value = evento.action
            _quantasChegaram.value++
            Log.w(TAG, "tecla do volante: código ${evento.keyCode}, ação ${evento.action}")

            if (evento.action == KeyEvent.ACTION_DOWN) {
                viDescida = true
            } else if (viDescida || evento.action != KeyEvent.ACTION_UP) {
                return
            }
            TeclaDoVolante.de(evento.keyCode)?.let { _teclas.tryEmit(it) }
        }
    }

    private val conexao = object : ServiceConnection {
        override fun onServiceConnected(nome: ComponentName?, binder: IBinder?) {
            val servico = IInputService.Stub.asInterface(binder)
            _ligado.value = runCatching { servico.registerKeyEventListener(TODAS, ouvinte); true }
                .onFailure { Log.w(TAG, "o serviço de teclas recusou o registro", it) }
                .getOrDefault(false)
        }

        override fun onServiceDisconnected(nome: ComponentName?) {
            _ligado.value = false
        }
    }

    /**
     * Liga o aviso, se esta central tiver o serviço. Pode ser chamado à toa.
     *
     * Falha calada de propósito, como em [PaginaDoCluster]: numa central sem
     * esse serviço o app continua inteiro, só sem a cruzinha — e é isso que
     * [ligado] `= false` conta para a tela de configuração dizer ao motorista,
     * em vez de ele ficar apertando o volante sem entender.
     */
    fun acompanhar(context: Context) {
        if (_ligado.value) return
        val intencao = Intent("com.beantechs.inputservice.service_init")
            .setPackage("com.beantechs.inputservice")
        runCatching {
            context.applicationContext.bindService(intencao, conexao, Context.BIND_AUTO_CREATE)
        }.onFailure { Log.w(TAG, "não deu para ouvir o serviço de teclas", it) }

        // E o atalho de teste, no mesmo lugar: quem liga a escuta é quem tem de
        // ligar o faz-de-conta dela.
        escutarSimulacao(context)
    }

    /**
     * Finge que uma tecla do volante foi apertada.
     *
     * Existe porque o serviço de teclas só existe dentro do carro: no emulador
     * e no computador não há volante nenhum, e sem isto a única forma de ver as
     * visões trocarem seria projetar no painel de verdade a cada mudança.
     *
     * O caminho é o mesmo do toque real — [teclas] — de propósito: um atalho
     * que desviasse por fora testaria um caminho que ninguém usa.
     */
    fun simular(tecla: TeclaDoVolante) {
        _teclas.tryEmit(tecla)
    }

    /**
     * Aceita o faz-de-conta vindo do `adb`, para se poder simular o volante de
     * fora do carro:
     *
     * ```
     * adb shell am broadcast -a br.com.hugolumazzini.havaltrip.VOLANTE --es tecla BAIXO
     * ```
     *
     * Um aviso do sistema, e não uma porta de rede: só entra quem já tem shell
     * no aparelho, que a essa altura poderia fazer o que quisesse de qualquer
     * jeito. Registrado em código, e não no manifesto, porque do Android 8 em
     * diante o sistema não entrega avisos assim a receptor declarado.
     */
    private fun escutarSimulacao(context: Context) {
        if (registrado) return
        registrado = true
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(contexto: Context?, intencao: Intent?) {
                val nome = intencao?.getStringExtra("tecla") ?: return
                val tecla = TeclaDoVolante.entries.firstOrNull { it.name.equals(nome, true) }
                if (tecla == null) {
                    Log.w(TAG, "tecla simulada desconhecida: $nome")
                    return
                }
                Log.w(TAG, "tecla simulada: $tecla")
                simular(tecla)
            }
        }
        runCatching {
            context.applicationContext.registerReceiver(receptor, IntentFilter(ACAO_SIMULAR))
        }.onFailure { Log.w(TAG, "não deu para ouvir a simulação", it) }
    }

    private var registrado = false

    /** O aviso que faz as vezes do volante. Ver [escutarSimulacao]. */
    const val ACAO_SIMULAR = "br.com.hugolumazzini.havaltrip.VOLANTE"
}
