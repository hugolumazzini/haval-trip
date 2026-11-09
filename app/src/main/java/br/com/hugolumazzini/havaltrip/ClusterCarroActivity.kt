package br.com.hugolumazzini.havaltrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import br.com.hugolumazzini.havaltrip.painel.JanelaDoPainel
import br.com.hugolumazzini.havaltrip.painel.fecharQuandoPedirem
import br.com.hugolumazzini.havaltrip.painel.registrarCicloDaJanela
import br.com.hugolumazzini.havaltrip.ui.ClusterCarroScreen
import br.com.hugolumazzini.havaltrip.ui.theme.HavalTripTheme

/**
 * O carro visto de cima, para o painel de instrumentos.
 *
 * É uma atividade separada da [ClusterActivity] de propósito: o Impulse
 * posiciona uma janela por atividade, então só com duas é possível pôr os
 * números num canto do painel e o desenho em outro. Fosse tudo numa tela só,
 * eles teriam de ficar grudados.
 */
class ClusterCarroActivity : ComponentActivity() {

    private val vm: TripViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cluster.iniciar(this)
        // Perguntada aqui, e nao uma vez so na partida, porque a paleta pode
        // ter mudado no volante desde a ultima vez que esta janela abriu.
        Cluster.atualizarPaleta()
        ServicoDeBordo.garantir(this)
        val espiando = intent.getBooleanExtra(ESPIANDO, false)
        if (!espiando) {
            fecharQuandoPedirem(JanelaDoPainel.CARRO)
            registrarCicloDaJanela("criada")
        }
        setContent {
            HavalTripTheme {
                ClusterCarroScreen(vm, espiando = espiando)
            }
        }
    }

    override fun onDestroy() {
        // Para ClusterOverlayService quando Activity é destruída
        // MAS NÃO se despedida estiver ativa! Senão remove a despedida
        val espiando = intent.getBooleanExtra(ESPIANDO, false)
        if (!espiando) registrarCicloDaJanela("destruída")
        if (!espiando && !br.com.hugolumazzini.havaltrip.painel.EstadoDaDespedida.estaAtiva()) {
            android.util.Log.d("ClusterCarroActivity", "🔌 onDestroy: removendo overlay (despedida NÃO ativa)")
            br.com.hugolumazzini.havaltrip.painel.ClusterOverlayService.recolher(
                this,
                JanelaDoPainel.CARRO
            )
        } else if (!espiando) {
            android.util.Log.w("ClusterCarroActivity", "⚠️ onDestroy: NÃO removendo overlay (despedida ATIVA)")
        }
        super.onDestroy()
    }
}
