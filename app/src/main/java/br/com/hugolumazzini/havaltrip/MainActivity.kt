package br.com.hugolumazzini.havaltrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import br.com.hugolumazzini.havaltrip.ui.AppNav
import br.com.hugolumazzini.havaltrip.ui.theme.HavalTripTheme

class MainActivity : ComponentActivity() {

    private val vm: TripViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Se a central não avisou a inicialização — ou se o app foi instalado
        // com o carro já ligado —, abrir a tela é o segundo gatilho: daqui em
        // diante a contagem continua mesmo depois de fechá-la.
        ServicoDeBordo.garantir(this)
        setContent {
            HavalTripTheme {
                AppNav(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // A central pode ser cortada sem aviso depois daqui. A política de
        // gravação já cobre o caso normal; este flush é o cinto de segurança.
        vm.gravarAgora()
    }
}
