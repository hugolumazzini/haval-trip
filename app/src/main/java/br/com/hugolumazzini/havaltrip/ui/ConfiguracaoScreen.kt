package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.engine.TripState
import br.com.hugolumazzini.havaltrip.storage.TripSnapshot
import br.com.hugolumazzini.havaltrip.ui.theme.Cores
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloRotulo

/**
 * Opções de zeragem automática da Viagem atual.
 *
 * São tempos redondos e não um campo livre porque a pergunta real é curta —
 * "uma parada no posto conta como a mesma viagem?" — e digitar segundos numa
 * tela sensível ao toque, dentro do carro, é pior resposta do que escolher.
 */
private val TEMPOS = listOf<Pair<String, Double?>>(
    "2 min" to 120.0,
    "5 min" to 300.0,
    "15 min" to 900.0,
    "30 min" to 1_800.0,
    "1 h" to 3_600.0,
    "4 h" to 14_400.0,
    "nunca" to null,
)

/**
 * Ajustes do computador de bordo.
 *
 * Só duas decisões, e as duas são de gosto: quantos contadores manuais o
 * motorista quer ver na lateral, e quanto tempo de carro parado encerra a
 * viagem atual. Nada aqui apaga número nenhum — esconder um contador o congela
 * com o que ele já mediu, e voltar a mostrá-lo o traz inteiro de volta.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ConfiguracaoScreen(vm: TripViewModel, estado: TripState) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text("CONFIGURAÇÃO", style = EstiloRotulo)
        Spacer(Modifier.height(14.dp))

        Cartao(Modifier.fillMaxWidth()) {
            Column {
                Text("Contadores manuais", style = MaterialTheme.typography.titleMedium, color = Cores.Texto)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Quantos contadores aparecem na lateral, fora a Viagem atual. " +
                        "Os que saem da lista param de contar, mas guardam o que já mediram.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.TextoApoio,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    (1..TripSnapshot.MAX_CONTADORES_MANUAIS).forEach { quantos ->
                        Opcao(
                            texto = quantos.toString(),
                            marcada = estado.contadoresManuais == quantos,
                            onClick = { vm.definirContadoresManuais(quantos) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Cartao(Modifier.fillMaxWidth()) {
            Column {
                Text("Zerar a Viagem atual", style = MaterialTheme.typography.titleMedium, color = Cores.Texto)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Com a chave fora por mais que este tempo, a Viagem atual é arquivada " +
                        "no histórico e recomeça do zero. Abaixo dele, o trajeto continua o mesmo — " +
                        "é o que faz uma parada rápida não virar duas viagens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.TextoApoio,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TEMPOS.forEach { (rotulo, segundos) ->
                        Opcao(
                            texto = rotulo,
                            marcada = mesmoTempo(estado.zeragemAutomaticaS, segundos),
                            onClick = { vm.definirZeragemAutomatica(segundos) },
                        )
                    }
                }
            }
        }
    }
}

/** Compara com folga: o valor gravado é `Double` e veio de outra sessão. */
private fun mesmoTempo(atual: Double?, opcao: Double?): Boolean = when {
    atual == null || opcao == null -> atual == null && opcao == null
    else -> kotlin.math.abs(atual - opcao) < 0.5
}

/** Botão de escolha única, no tamanho de dedo que a central pede. */
@Composable
private fun Opcao(texto: String, marcada: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (marcada) Cores.SuperficieSelecionada else Cores.Campo)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.titleSmall,
            color = if (marcada) Cores.Destaque else Cores.TextoCorrido,
        )
    }
}
