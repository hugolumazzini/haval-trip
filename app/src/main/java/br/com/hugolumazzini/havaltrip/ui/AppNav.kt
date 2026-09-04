package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.Tela
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.Trip
import br.com.hugolumazzini.havaltrip.ui.theme.Cores
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloMarca
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloRotulo

/**
 * Estrutura da tela: barra lateral fixa à esquerda, conteúdo à direita.
 *
 * A central é paisagem e larga; empilhar tudo em coluna desperdiçaria a
 * largura e obrigaria a rolar. Com a lateral fixa, trocar de Trip é um toque
 * só, sem sair de onde se está.
 */
@Composable
fun AppNav(vm: TripViewModel) {
    val estado by vm.state.collectAsStateWithLifecycle()
    val tela by vm.tela.collectAsStateWithLifecycle()
    val fonteReal by vm.fonteReal.collectAsStateWithLifecycle()

    // A central desenha barra de status e de navegação por cima da janela em
    // modo edge-to-edge; sem este recuo, a fileira de botões fica embaixo da
    // barra do sistema e vira alvo que não responde.
    Row(
        Modifier
            .fillMaxSize()
            .background(Cores.Fundo)
            .safeDrawingPadding()
    ) {
        BarraLateral(
            trips = estado.trips,
            selecionada = estado.selectedTrip?.id,
            ignicao = estado.live.ignition,
            emHistorico = tela is Tela.Historico,
            emDiagnostico = tela is Tela.Diagnostico,
            emConfiguracao = tela is Tela.Configuracao,
            // No carro a chave é física: um botão que fingisse girá-la mentiria.
            ignicaoSimulada = !fonteReal,
            onTrip = { vm.selecionar(it); vm.voltarAoPainel() },
            onHistorico = vm::abrirHistorico,
            onDiagnostico = vm::abrirDiagnostico,
            onConfiguracao = vm::abrirConfiguracao,
            onIgnicao = vm::alternarIgnicao,
        )

        Box(Modifier.fillMaxSize().padding(20.dp)) {
            when (val atual = tela) {
                is Tela.Painel -> PainelScreen(vm, estado)
                is Tela.Detalhes -> DetalhesScreen(vm, estado, atual.tripId)
                is Tela.Historico -> HistoricoScreen(vm, estado)
                is Tela.Diagnostico -> DiagnosticoScreen(vm)
                is Tela.Configuracao -> ConfiguracaoScreen(vm, estado)
            }
        }
    }
}

@Composable
private fun BarraLateral(
    trips: List<Trip>,
    selecionada: String?,
    ignicao: IgnitionState,
    emHistorico: Boolean,
    emDiagnostico: Boolean,
    emConfiguracao: Boolean,
    ignicaoSimulada: Boolean,
    onTrip: (String) -> Unit,
    onHistorico: () -> Unit,
    onDiagnostico: () -> Unit,
    onConfiguracao: () -> Unit,
    onIgnicao: () -> Unit,
) {
    Column(
        Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(Cores.Lateral)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text("HAVAL", style = EstiloMarca)
        Text("TRIP", style = EstiloMarca.copy(color = Cores.Destaque))

        // A lista rola e o botão de ignição fica preso embaixo. Com seis ou
        // sete contadores numa tela de 600 px de altura a lista transborda, e
        // é justamente a ignição que sumiria — a última coisa que pode sumir.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(20.dp))
            Text("CONTADORES", style = EstiloRotulo)
            Spacer(Modifier.height(6.dp))

            trips.forEach { trip ->
                ItemLateral(
                    titulo = trip.label,
                    apoio = if (trip.isAutomatic) {
                        "automática • ${textoDoStatus(trip.status)}"
                    } else {
                        textoDoStatus(trip.status)
                    },
                    selecionado = !emHistorico && !emDiagnostico && !emConfiguracao &&
                        trip.id == selecionada,
                    ponto = { PontoStatus(trip.status) },
                    onClick = { onTrip(trip.id) },
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("VIAGENS", style = EstiloRotulo)
            Spacer(Modifier.height(6.dp))
            ItemLateral(
                titulo = "Histórico",
                apoio = "ver e comparar",
                selecionado = emHistorico,
                ponto = null,
                onClick = onHistorico,
            )

            Spacer(Modifier.height(16.dp))
            Text("SISTEMA", style = EstiloRotulo)
            Spacer(Modifier.height(6.dp))
            ItemLateral(
                titulo = "Diagnóstico",
                apoio = if (ignicaoSimulada) "simulador ligado" else "dados crus do carro",
                selecionado = emDiagnostico,
                ponto = null,
                onClick = onDiagnostico,
            )
            ItemLateral(
                titulo = "Configuração",
                apoio = "ajustes e versão",
                selecionado = emConfiguracao,
                ponto = null,
                onClick = onConfiguracao,
            )
            Spacer(Modifier.height(12.dp))
        }

        // Só aparece sem carro na escuta: aí a ignição é simulada e o botão é o
        // único jeito de exercitar a máquina de estados na bancada.
        if (ignicaoSimulada) BotaoAcao(
            texto = if (ignicao == IgnitionState.ON) "Desligar ignição" else "Ligar ignição",
            onClick = onIgnicao,
            modifier = Modifier.fillMaxWidth(),
            cor = if (ignicao == IgnitionState.ON) Cores.Campo else Cores.SuperficieSelecionada,
            corTexto = if (ignicao == IgnitionState.ON) Cores.Texto else Cores.Destaque,
        )
    }
}

@Composable
private fun ItemLateral(
    titulo: String,
    apoio: String,
    selecionado: Boolean,
    ponto: (@Composable () -> Unit)?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selecionado) Cores.SuperficieSelecionada else Cores.Lateral)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ponto?.invoke()
        Column {
            Text(
                titulo,
                style = MaterialTheme.typography.titleSmall,
                color = if (selecionado) Cores.Destaque else Cores.TextoCorrido,
            )
            Text(apoio, style = MaterialTheme.typography.bodySmall, color = Cores.TextoApoio)
        }
    }
}
