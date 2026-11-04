package br.com.hugolumazzini.havaltrip.painel

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Estado global da despedida.
 *
 * Previne race conditions entre MotorDeBordo, ClusterActivity e ClusterOverlayService.
 * Quando despedida está ativa, ClusterActivity NÃO deve remover overlays.
 */
object EstadoDaDespedida {
    private const val TAG = "EstadoDaDespedida"

    private val ativa = AtomicBoolean(false)

    /**
     * Marca despedida como ativa.
     * Deve ser chamado ANTES de projetar a despedida via overlay.
     */
    fun ativar() {
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "👋 DESPEDIDA ATIVADA")
        Log.i(TAG, "   ClusterActivity NÃO deve remover overlays")
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        ativa.set(true)
    }

    /**
     * Desativa despedida.
     * Deve ser chamado quando ignição voltar a ON.
     */
    fun desativar() {
        if (ativa.get()) {
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "🔌 DESPEDIDA DESATIVADA")
            Log.i(TAG, "   ClusterActivity pode remover overlays normalmente")
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            ativa.set(false)
        }
    }

    /**
     * Verifica se despedida está ativa.
     */
    fun estaAtiva(): Boolean = ativa.get()
}
