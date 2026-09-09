package br.com.hugolumazzini.havaltrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
        ServicoDeBordo.garantir(this)
        setContent {
            HavalTripTheme {
                ClusterCarroScreen(vm)
            }
        }
    }
}
