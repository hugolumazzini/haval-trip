package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.TripViewModel

/**
 * O mesmo carro da tela grande, sozinho numa janela do painel.
 *
 * Reaproveita o [Diagrama] em vez de redesenhar: portas abertas, cintos,
 * pressões e o alerta piscando são uma peça só, e ter duas cópias faria uma
 * delas envelhecer sem ninguém perceber.
 *
 * Sem o cartão "VEÍCULO" em volta e sem a lista de avisos: aqui o retângulo é
 * pequeno, o fundo tem de ser transparente para casar com o painel, e o aviso
 * escrito exigiria um espaço que o desenho aproveita melhor. Quem quiser o
 * texto tem a tela da central.
 */
@Composable
fun ClusterCarroScreen(vm: TripViewModel) {
    val painel by vm.painelDoVeiculo.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(4.dp),
    ) {
        Diagrama(painel, Modifier.fillMaxSize())
    }
}
