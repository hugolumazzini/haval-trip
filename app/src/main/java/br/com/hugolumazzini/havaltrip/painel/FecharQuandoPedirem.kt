package br.com.hugolumazzini.havaltrip.painel

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Faz a janela se fechar quando a tela de configuração mandar recolhê-la.
 *
 * O caminho é em memória, e não por comando de sistema, porque as janelas do
 * painel vivem no mesmo processo da central — a `taskAffinity` do manifesto
 * separa *tarefas*, não processos. Ver [ProjetorDoPainel.pedidosDeFechar] para
 * por que não é um `am`.
 */
fun ComponentActivity.fecharQuandoPedirem(janela: JanelaDoPainel) {
    lifecycleScope.launch {
        ProjetorDoPainel.pedidosDeFechar.collect { pedida -> if (pedida == janela) finish() }
    }
}
