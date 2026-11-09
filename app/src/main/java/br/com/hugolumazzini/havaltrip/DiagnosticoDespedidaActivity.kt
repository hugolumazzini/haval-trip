package br.com.hugolumazzini.havaltrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.hugolumazzini.havaltrip.painel.HistoricoDaDespedida
import br.com.hugolumazzini.havaltrip.ui.DiagnosticoDespedidaScreen
import br.com.hugolumazzini.havaltrip.ui.theme.HavalTripTheme

/**
 * Activity de diagnóstico da despedida.
 *
 * Mostra histórico de eventos da despedida para debug sem ADB.
 * Útil para testar no carro e ver o que está acontecendo.
 */
class DiagnosticoDespedidaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HistoricoDaDespedida.iniciar(applicationContext)
        setContent {
            HavalTripTheme {
                DiagnosticoDespedidaScreen()
            }
        }
    }
}
