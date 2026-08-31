package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.hugolumazzini.havaltrip.domain.TripStatus
import br.com.hugolumazzini.havaltrip.ui.theme.Cores
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloNumeroGrande
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloNumeroMedio
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloRotulo

/** Cor que traduz o estado da Trip, usada no ponto ao lado do nome. */
fun corDoStatus(status: TripStatus): Color = when (status) {
    TripStatus.ACTIVE -> Cores.Confirmacao
    TripStatus.PAUSED -> Cores.Atencao
    TripStatus.STANDBY -> Cores.Destaque
    TripStatus.INACTIVE -> Cores.TextoApoio
}

/** Rótulo curto do estado, para quem não decora o significado das cores. */
fun textoDoStatus(status: TripStatus): String = when (status) {
    TripStatus.ACTIVE -> "contando"
    TripStatus.PAUSED -> "pausada"
    TripStatus.STANDBY -> "em espera"
    // Não é mais alcançável; fica pelo `when` exaustivo do Kotlin.
    TripStatus.INACTIVE -> "em espera"
}

/** Ponto colorido de estado. */
@Composable
fun PontoStatus(status: TripStatus, tamanho: Int = 10) {
    Box(
        Modifier
            .size(tamanho.dp)
            .clip(CircleShape)
            .background(corDoStatus(status))
    )
}

/**
 * Um dos quatro quadrantes do painel.
 *
 * Rótulo pequeno em cima, número enorme embaixo, unidade separada do número. A
 * unidade sai do tamanho grande de propósito: ela nunca muda, então competir em
 * peso com o valor só atrasaria a leitura.
 */
@Composable
fun Quadrante(
    rotulo: String,
    valor: String,
    unidade: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Cores.Superficie)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(rotulo, style = EstiloRotulo)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valor, style = EstiloNumeroGrande)
            Text(
                "  $unidade",
                style = EstiloRotulo.copy(fontSize = 14.sp),
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}

/** Célula compacta da faixa superior: dado do veículo, não da Trip. */
@Composable
fun CelulaVeiculo(rotulo: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(end = 24.dp)) {
        Text(rotulo, style = EstiloRotulo)
        Text(valor, style = EstiloNumeroMedio)
    }
}

/** Linha `rótulo … valor` da tela de detalhes. */
@Composable
fun LinhaDetalhe(rotulo: String, valor: String, destaque: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Quando falta largura, quem cede é o rótulo: "59 km/" numa linha e
        // "h" na de baixo não é um número que dê para ler de relance.
        Text(
            rotulo,
            color = Cores.TextoApoio,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f, fill = false),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
        Text(
            valor,
            color = if (destaque) Cores.Destaque else Cores.Texto,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            softWrap = false,
        )
    }
}

/**
 * Botão de ação do painel.
 *
 * Alvo generoso (56 dp de altura) porque é tocado com o carro andando: o padrão
 * de 40 dp do Material foi desenhado para um celular parado na mão.
 */
@Composable
fun BotaoAcao(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cor: Color = Cores.Campo,
    corTexto: Color = Cores.Texto,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (habilitado) cor else Cores.Superficie)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto,
            color = if (habilitado) corTexto else Cores.TextoApoio,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** Cartão neutro para agrupar conteúdo. */
@Composable
fun Cartao(
    modifier: Modifier = Modifier,
    selecionado: Boolean = false,
    conteudo: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selecionado) Cores.SuperficieSelecionada else Cores.Superficie)
            .border(
                width = if (selecionado) 1.dp else 0.dp,
                color = if (selecionado) Cores.Destaque else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(16.dp)
    ) { conteudo() }
}

/** Aviso de tela vazia, no lugar de um espaço em branco sem explicação. */
@Composable
fun Vazio(mensagem: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(mensagem, color = Cores.TextoApoio, textAlign = TextAlign.Center)
    }
}
