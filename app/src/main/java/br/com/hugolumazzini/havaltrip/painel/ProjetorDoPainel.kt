package br.com.hugolumazzini.havaltrip.painel

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

private const val TAG = "ProjetorDoPainel"

/** Nosso pacote, escrito uma vez só. */
private const val PACOTE = "br.com.hugolumazzini.havaltrip"

/**
 * Uma das nossas janelas que pode ir para outra tela do carro.
 *
 * São duas Activities separadas, cada uma com a sua `taskAffinity`, e é isso
 * que permite as duas estarem em telas diferentes ao mesmo tempo — uma tarefa
 * só pode ocupar uma tela de cada vez.
 */
enum class JanelaDoPainel(val rotulo: String, val activity: String) {
    NUMEROS("Bloco de números", "$PACOTE.ClusterActivity"),
    CARRO("Carro visto de cima", "$PACOTE.ClusterCarroActivity"),
}

/** Uma tela do carro, do jeito que o Android a enxerga. */
data class TelaDoCarro(val id: Int, val nome: String, val largura: Int, val altura: Int) {
    val descricao: String get() = "Tela $id — $largura x $altura"
}

/**
 * Põe as nossas janelas nas telas do carro, sem passar pelo Impulse.
 *
 * ## Por que isto existe
 *
 * Até aqui quem colocava o Haval Trip no painel era a tela "Telas" do Impulse.
 * Isso amarrava o app a uma versão de preview de outro projeto — na versão
 * estável do Impulse esse menu nem existe — e, pior, o cadastro dele é indexado
 * por pacote: `saveConfig` substitui a entrada que tiver o mesmo `packageName`
 * em vez de acrescentar outra. Ou seja, por aquele caminho as nossas duas
 * janelas nunca poderiam estar configuradas ao mesmo tempo.
 *
 * ## Por que dá para fazer sozinho
 *
 * O Impulse não tem poder nenhum sobre displays. Ele roda `am start … --display
 * N --windowingMode 5` pelo Shizuku, que executa como o usuário `shell` — o
 * mesmo do adb. Qualquer app autorizado no Shizuku pode rodar o mesmo comando.
 * Os comandos aqui são os mesmos do `DisplayAppLauncher.launchApp` dele, na
 * mesma ordem, porque essa sequência já está provada em milhares de centrais.
 *
 * ## O que continua não sendo nosso
 *
 * O Shizuku. Se ele não estiver instalado, ou não estiver iniciado, ou não
 * tiver autorizado este app, não há projeção — e isso tem de aparecer na tela
 * com o conserto junto, e não falhar calado. Ver [ShizukuShell.Situacao].
 */
object ProjetorDoPainel {

    /** Como foi a última tentativa de projetar, por janela. */
    sealed interface Resultado {
        data object Nunca : Resultado
        data object Projetando : Resultado
        data class Projetada(val tela: Int) : Resultado
        data class Falhou(val motivo: String) : Resultado
    }

    private val _resultados =
        MutableStateFlow<Map<JanelaDoPainel, Resultado>>(emptyMap())
    val resultados: StateFlow<Map<JanelaDoPainel, Resultado>> = _resultados.asStateFlow()

    /**
     * Pedidos de fechar uma janela.
     *
     * Não há comando `am` para encerrar uma Activity, e derrubar a pilha inteira
     * com `am stack remove` arriscaria levar junto o que não é nosso. Como as
     * duas janelas vivem no mesmo processo da central — `taskAffinity` separa
     * tarefas, não processos —, um aviso em memória chega nelas e cada uma se
     * fecha sozinha. É o caminho mais curto e o único sem risco de dano
     * colateral.
     */
    private val _pedidosDeFechar = MutableSharedFlow<JanelaDoPainel>(extraBufferCapacity = 4)
    val pedidosDeFechar: SharedFlow<JanelaDoPainel> = _pedidosDeFechar.asSharedFlow()

    /**
     * As telas em que dá para projetar, tirando a da central.
     *
     * A tela 0 fica de fora porque é onde o app já está: mandar uma janela do
     * painel para lá seria abrir a mesma coisa por cima da tela grande.
     *
     * Não precisa de Shizuku — o `DisplayManager` é API pública. Só *usar* a
     * tela precisa de privilégio.
     */
    fun telas(context: Context): List<TelaDoCarro> = runCatching {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.displays
            .filter { it.displayId != 0 }
            .map { TelaDoCarro(it.displayId, it.name ?: "sem nome", it.mode.physicalWidth, it.mode.physicalHeight) }
            .sortedBy { it.id }
    }.onFailure { Log.w(TAG, "não deu para listar as telas", it) }.getOrDefault(emptyList())

    /**
     * Manda a janela para a tela, ocupando-a inteira.
     *
     * Inteira de propósito: a posição fina — canto, faixa da navegação, bola do
     * ar — é decidida *dentro* da janela, pelos ajustes do app, sobre um fundo
     * transparente. É a mesma recomendação que a tela de configuração já dava
     * para quem usava o Impulse ("dê ao app a tela inteira"), agora automática.
     * Uma medida a menos para o motorista acertar no dedo.
     *
     * Chamar de fora da thread principal.
     */
    fun projetar(context: Context, janela: JanelaDoPainel, telaId: Int): Resultado {
        marcar(janela, Resultado.Projetando)

        val situacao = ShizukuShell.situacao()
        if (situacao != ShizukuShell.Situacao.PRONTO) {
            return marcar(janela, Resultado.Falhou(explicar(situacao)))
        }

        val tela = telas(context).find { it.id == telaId }
            ?: return marcar(janela, Resultado.Falhou("a tela $telaId não existe nesta central"))

        // A ordem é a do Impulse. Nunca um `force-stop` antes: o pacote é o
        // nosso, e matá-lo levaria junto a outra janela, o serviço que conta a
        // viagem e o que ainda não foi gravado em disco.
        val jaEstaLa = pilhaDaJanela(janela, telaId)
        if (jaEstaLa == null) {
            ShizukuShell.rodar(
                "am start -n $PACOTE/${janela.activity} --display $telaId --windowingMode 5",
            ) ?: return marcar(janela, Resultado.Falhou("o Shizuku recusou o comando"))
            // O `am start` volta antes de a janela existir na pilha; sem esta
            // pausa o redimensionamento logo abaixo não acha o que redimensionar.
            Thread.sleep(400)
        }

        val pilha = pilhaDaJanela(janela, telaId)
            ?: return marcar(
                janela,
                Resultado.Falhou("a janela abriu, mas o sistema não a colocou na tela $telaId"),
            )

        ShizukuShell.rodar("am stack resize $pilha 0 0 ${tela.largura} ${tela.altura}")
        return marcar(janela, Resultado.Projetada(telaId))
    }

    /** Tira a janela da tela do carro. Ver [pedidosDeFechar]. */
    fun recolher(janela: JanelaDoPainel) {
        _pedidosDeFechar.tryEmit(janela)
        marcar(janela, Resultado.Nunca)
    }

    /**
     * Reprojeta o que estiver configurado, assim que o Shizuku permitir.
     *
     * Na partida do carro a ordem de inicialização não é garantida: o nosso
     * serviço pode subir antes do Shizuku. Decidir "sem Shizuku" um segundo
     * antes de ele ficar pronto, e nunca mais tentar, deixaria o painel vazio
     * numa viagem inteira — daí esperar pelo aviso de binder em vez de olhar
     * uma vez só. O `Sticky` cobre o caso oposto, de o Shizuku já estar pronto
     * quando chegamos.
     */
    fun projetarNaPartida(context: Context, escolhas: () -> Map<JanelaDoPainel, Int?>) {
        val aplicacao = context.applicationContext
        val aoChegarBinder = Shizuku.OnBinderReceivedListener {
            Thread {
                escolhas().forEach { (janela, tela) ->
                    if (tela != null) runCatching { projetar(aplicacao, janela, tela) }
                }
            }.start()
        }
        runCatching { Shizuku.addBinderReceivedListenerSticky(aoChegarBinder) }
            .onFailure { Log.w(TAG, "não deu para esperar pelo Shizuku", it) }
    }

    /**
     * O id da pilha em que esta janela está, naquela tela, ou `null`.
     *
     * A busca é pela **Activity**, e não pelo pacote como no Impulse. Tem de
     * ser: as nossas duas janelas são do mesmo pacote, e casar por pacote
     * confundiria uma com a outra — exatamente o defeito que nos tirou do
     * cadastro dele.
     *
     * O `am stack list` escreve a tarefa como `taskId=42: pacote/.Activity`,
     * com o nome ora abreviado, ora inteiro, conforme como ela foi iniciada.
     * Daí aceitar as duas formas.
     */
    private fun pilhaDaJanela(janela: JanelaDoPainel, telaId: Int): Int? {
        val saida = ShizukuShell.rodar("am stack list") ?: return null
        val curto = janela.activity.removePrefix(PACOTE)
        val alvos = listOf("$PACOTE/${janela.activity}", "$PACOTE/$curto")

        var pilha: Int? = null
        var tela: Int? = null
        for (linha in saida.lines()) {
            Regex("""Stack id=(\d+).*displayId=(\d+)""").find(linha)?.let { m ->
                pilha = m.groupValues[1].toIntOrNull()
                tela = m.groupValues[2].toIntOrNull()
            }
            if (tela == telaId && pilha != null && alvos.any { it in linha }) return pilha
        }
        return null
    }

    private fun marcar(janela: JanelaDoPainel, resultado: Resultado): Resultado {
        _resultados.value = _resultados.value + (janela to resultado)
        return resultado
    }

    private fun explicar(situacao: ShizukuShell.Situacao): String = when (situacao) {
        ShizukuShell.Situacao.SEM_SHIZUKU ->
            "o Shizuku não está rodando nesta central — abra o app do Shizuku e inicie-o"
        ShizukuShell.Situacao.PRECISA_AUTORIZAR ->
            "falta autorizar o Haval Trip no Shizuku"
        ShizukuShell.Situacao.PRONTO -> ""
    }
}
