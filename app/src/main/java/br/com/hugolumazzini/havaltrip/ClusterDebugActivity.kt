package br.com.hugolumazzini.havaltrip

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.Fonte
import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.ui.ClusterScreen
import br.com.hugolumazzini.havaltrip.ui.theme.HavalTripTheme

/**
 * Activity de debug para testar o cluster no emulador.
 *
 * Mostra o ClusterScreen em fullscreen e permite forçar a despedida
 * sem precisar do display físico do cluster.
 *
 * Uso: adb shell am start -n br.com.hugolumazzini.havaltrip/.ClusterDebugActivity
 */
class ClusterDebugActivity : ComponentActivity() {

    private val vm: TripViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cluster.iniciar(this)
        Cluster.atualizarPaleta()
        ServicoDeBordo.garantir(this)

        setContent {
            HavalTripTheme {
                var forcarDespedida by remember { mutableStateOf(false) }

                val motor = MotorDeBordo.de(application)
                val fonte by motor.fonte.collectAsStateWithLifecycle()
                val estado by vm.state.collectAsStateWithLifecycle()
                val ignicao = estado.live.ignition

                LaunchedEffect(Unit) {
                    Log.i("ClusterDebug", "🔧 Iniciando debug cluster")
                    // Força fonte SIMULADOR para poder alternar ignição
                    if (fonte != Fonte.SIMULADOR) {
                        Log.i("ClusterDebug", "📡 Mudando fonte de $fonte para SIMULADOR")
                        motor.usarFonte(Fonte.SIMULADOR)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Cluster em fullscreen com fundo escuro (simula painel do carro)
                    ClusterScreen(
                        vm = vm,
                        espiando = true, // fundo escuro
                        forcarDespedida = forcarDespedida
                    )

                    // Controles de debug no topo
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(8.dp)
                    ) {
                        Text(
                            "🔧 DEBUG CLUSTER",
                            color = Color.White,
                            fontSize = 12.sp
                        )

                        Text(
                            "Fonte: $fonte | Ignição: ${if (ignicao == IgnitionState.ON) "🟢 ON" else "🔴 OFF"}",
                            color = Color.White,
                            fontSize = 10.sp
                        )

                        val tripAtual = estado.trips.firstOrNull { it.isAutomatic }
                        val distanciaKm = tripAtual?.metrics?.distanceKm ?: 0.0
                        val despedidaVaiAparecer = distanciaKm >= 0.1

                        Text(
                            "Distância: %.1f km ${if (despedidaVaiAparecer) "✅" else "⚠️ (mín 0.1)"}".format(distanciaKm),
                            color = if (despedidaVaiAparecer) Color.Green else Color.Yellow,
                            fontSize = 10.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                Log.i("ClusterDebug", "⚡ Botão alternar ignição clicado")
                                vm.alternarIgnicao()
                            }) {
                                Text(if (ignicao == IgnitionState.ON) "⚡ Desligar" else "⚡ Ligar")
                            }

                            Button(onClick = {
                                forcarDespedida = !forcarDespedida
                                Log.i("ClusterDebug", "👋 Forçar despedida: $forcarDespedida")
                            }) {
                                Text(if (forcarDespedida) "✅ Despedida" else "❌ Despedida")
                            }
                        }

                        if (!despedidaVaiAparecer && ignicao == IgnitionState.OFF) {
                            Text(
                                "⚠️ Despedida só aparece após 0.1 km",
                                color = Color.Yellow,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
