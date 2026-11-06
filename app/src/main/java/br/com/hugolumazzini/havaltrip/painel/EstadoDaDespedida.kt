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
        val jaEstava = ativa.getAndSet(true)
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "👋 DESPEDIDA ATIVADA")
        Log.i(TAG, "   Já estava ativa: $jaEstava")
        Log.i(TAG, "   TODAS as Activities devem respeitar esta flag!")

        // Stack trace para saber QUEM ativou
        val stackTrace = Thread.currentThread().stackTrace
        Log.i(TAG, "   Ativado por:")
        for (i in 3..minOf(5, stackTrace.lastIndex)) {
            Log.i(TAG, "      ${stackTrace[i]}")
        }
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Registra no histórico visual
        HistoricoDaDespedida.ativou()
    }

    /**
     * Desativa despedida.
     * Deve ser chamado quando ignição voltar a ON.
     */
    fun desativar() {
        val estavaAtiva = ativa.getAndSet(false)
        if (estavaAtiva) {
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "🔌 DESPEDIDA DESATIVADA")
            Log.i(TAG, "   Activities podem remover overlays normalmente")

            // Stack trace para saber QUEM desativou
            val stackTrace = Thread.currentThread().stackTrace
            Log.i(TAG, "   Desativado por:")
            for (i in 3..minOf(5, stackTrace.lastIndex)) {
                Log.i(TAG, "      ${stackTrace[i]}")
            }
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // Registra no histórico visual
            HistoricoDaDespedida.desativou()
        }
    }

    /**
     * Verifica se despedida está ativa.
     */
    fun estaAtiva(): Boolean = ativa.get()
}
