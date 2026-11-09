package br.com.hugolumazzini.havaltrip.painel

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
}
