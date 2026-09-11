package br.com.hugolumazzini.havaltrip.painel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.autolink.cluster.ClusterMsgData
import com.autolink.clusterservice.IClusterCallback
import com.autolink.clusterservice.IClusterService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "PaginaDoCluster"

/**
 * Em qual das "bolas" do painel o motorista está.
 *
 * ## Por que precisamos disso
 *
 * O painel do H6 tem um carrossel de páginas — a bola padrão do carro, a de
 * mídia e a do telefone — e quem projeta uma janela lá em cima não é avisado
 * de nada: a janela fica onde está, por cima de todas elas. Foi o que o carro
 * mostrou. Se quisermos ocupar justamente a bola que o Impulse deixa vazia,
 * temos de sumir quando a página muda, como o ar-condicionado dele some.
 *
 * ## De onde vem o aviso
 *
 * Da própria central. O serviço `com.autolink.clusterservice` aceita um
 * registro de retorno de chamada e, toda vez que a página troca, manda a
 * mensagem de número [MSG_PAGINA] com o número da página dentro. Não precisa de
 * Shizuku: é um serviço comum, ligado por `bindService`, ao contrário do
 * serviço de telemetria da GWM.
 *
 * ## O que ainda não sabemos
 *
 * Qual número é qual página. O único ponto firme é que a página padrão do carro
 * é a **0** — é a que o Impulse exige que *não* esteja na frente para mostrar o
 * ar dele. Os outros números têm de ser lidos no carro, e é para isso que
 * [pagina] aparece na tela de diagnóstico: o motorista gira o carrossel e anota
 * o número da bola que quer usar.
 */
object PaginaDoCluster {

    /** Mensagem em que o serviço conta que a página do carrossel mudou. */
    private const val MSG_PAGINA = 133

    /** A página padrão do carro, a que o carrossel mostra quando ninguém mexeu. */
    const val PAGINA_PADRAO = 0

    /**
     * A página em que o Impulse desenha o ar-condicionado dele.
     *
     * Sai do código dele, não de palpite: o recorte redondo aparece em toda
     * página diferente de 0, mas o conteúdo — a tela de A/C — só na 1. É por
     * isso que a bola das outras páginas fica redonda e vazia, que é
     * exatamente a que sobra para nós.
     */
    const val PAGINA_DO_AR_DO_IMPULSE = 1

    /**
     * Se ocupar esta página vai brigar com alguém.
     *
     * Não impede nada: quem não usa o Impulse pode muito bem querer a página 1,
     * e uma central com outra versão dele pode numerar diferente. É um aviso,
     * e o motorista decide.
     */
    fun aviso(pagina: Int?): String? = when (pagina) {
        null -> null
        PAGINA_PADRAO ->
            "A página 0 é a padrão do carro — a que mostra o desenho da estrada. " +
                "Ocupá-la tapa o painel nativo."
        PAGINA_DO_AR_DO_IMPULSE ->
            "A página 1 é onde o Impulse desenha o ar-condicionado dele. Se você " +
                "usa o Impulse, os dois vão disputar o mesmo lugar."
        else -> null
    }

    private val _pagina = MutableStateFlow<Int?>(null)

    /** A página atual, ou `null` enquanto o serviço não contou nenhuma. */
    val pagina: StateFlow<Int?> = _pagina.asStateFlow()

    private val _ligado = MutableStateFlow(false)

    /** Se o aviso de página está mesmo chegando nesta central. */
    val ligado: StateFlow<Boolean> = _ligado.asStateFlow()

    private val ouvinte = object : IClusterCallback.Stub() {
        override fun callbackMsg(msgId: Int, dados: ClusterMsgData?) {
            if (msgId == MSG_PAGINA && dados != null) {
                _pagina.value = dados.intValue
                Log.w(TAG, "página do painel mudou para ${dados.intValue}")
            }
        }
    }

    private val conexao = object : ServiceConnection {
        override fun onServiceConnected(nome: ComponentName?, binder: IBinder?) {
            val servico = IClusterService.Stub.asInterface(binder)
            _ligado.value = runCatching { servico.registerCallback(ouvinte); true }
                .onFailure { Log.w(TAG, "o serviço do painel recusou o registro", it) }
                .getOrDefault(false)
        }

        override fun onServiceDisconnected(nome: ComponentName?) {
            // Sem `_pagina.value = null`: perder a ligação não muda a página que
            // o motorista está vendo, e apagar o valor faria a janela aparecer
            // ou sumir por causa de um problema nosso, não de um gesto dele.
            _ligado.value = false
        }
    }

    /**
     * Liga o aviso, se esta central tiver o serviço. Pode ser chamado à toa.
     *
     * Não falhar alto é de propósito: numa central sem esse serviço o app tem de
     * continuar funcionando igual, só sem saber a página — e é o que
     * [ligado] `= false` diz para quem pergunta.
     */
    fun acompanhar(context: Context) {
        if (_ligado.value) return
        val intencao = Intent().setComponent(
            ComponentName(
                "com.autolink.clusterservice",
                "com.autolink.clusterservice.ClusterService",
            ),
        )
        runCatching {
            context.applicationContext.bindService(intencao, conexao, Context.BIND_AUTO_CREATE)
        }.onFailure { Log.w(TAG, "não deu para ouvir o serviço do painel", it) }
    }
}
