package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.ModoHistorico
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.domain.TripRecord
import br.com.hugolumazzini.havaltrip.engine.TripState
import br.com.hugolumazzini.havaltrip.format.TripFormat
import br.com.hugolumazzini.havaltrip.services.ComparisonLine
import br.com.hugolumazzini.havaltrip.ui.theme.Cores
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloRotulo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val formatoData = SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR"))

/**
 * Histórico à esquerda, a viagem escolhida à direita.
 *
 * Ler uma viagem é o motivo comum de abrir esta tela — "quanto deu a ida à
 * praia?" — e por isso é o que um toque faz. Comparar duas é a pergunta mais
 * rara, então vira uma ação a partir da viagem aberta, e não o modo padrão.
 */
@Composable
fun HistoricoScreen(vm: TripViewModel, estado: TripState) {
    val modo by vm.modoHistorico.collectAsStateWithLifecycle()
    val comparando = modo is ModoHistorico.Comparando
    val emFoco = vm.registroEmFoco(modo, estado.history)

    /** `null` = nenhum diálogo aberto. Estado da tela, não do módulo. */
    var renomeando by remember { mutableStateOf<TripRecord?>(null) }
    var excluindo by remember { mutableStateOf<TripRecord?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("HISTÓRICO", style = EstiloRotulo)
                Text(
                    if (comparando) "Escolha a segunda viagem para comparar"
                    else "Toque numa viagem para ver os números dela",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (comparando) Cores.Destaque else Cores.TextoApoio,
                )
            }
            if (comparando) BotaoAcao("Sair da comparação", vm::sairDaComparacao)
            else BotaoAcao("Voltar ao painel", vm::voltarAoPainel)
        }

        Spacer(Modifier.height(16.dp))

        if (estado.history.isEmpty()) {
            Vazio("Nenhuma viagem arquivada ainda.\nFeche uma viagem no painel para ela aparecer aqui.")
            return
        }

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(
                Modifier.width(320.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(estado.history.reversed(), key = { it.recordId }) { registro ->
                    ItemHistorico(
                        registro = registro,
                        posicao = posicaoNaComparacao(modo, registro.recordId),
                        selecionado = registro.recordId == emFoco?.recordId ||
                            (modo as? ModoHistorico.Comparando)?.bId == registro.recordId,
                        onClick = { vm.tocarNoRegistro(registro.recordId) },
                    )
                }
            }

            Column(Modifier.weight(1f).fillMaxHeight()) {
                val comparacao = vm.comparar(modo, estado.history)
                when {
                    comparacao != null -> TabelaComparacao(comparacao.a, comparacao.b, comparacao.lines)
                    comparando -> Vazio("Toque na segunda viagem, na lista ao lado.")
                    emFoco != null -> DetalhesDaViagem(
                        registro = emFoco,
                        onComparar = { vm.compararComOutra(emFoco.recordId) },
                        onRenomear = { renomeando = emFoco },
                        onExcluir = { excluindo = emFoco },
                    )
                    else -> Vazio("Escolha uma viagem na lista ao lado.")
                }
            }
        }
    }

    renomeando?.let { registro ->
        DialogoRenomear(
            registro = registro,
            onConfirmar = { vm.renomearRegistro(registro.recordId, it); renomeando = null },
            onCancelar = { renomeando = null },
        )
    }

    excluindo?.let { registro ->
        // Excluir é a única ação irreversível da tela, e o histórico não tem
        // "desfazer": vale a pergunta antes.
        AlertDialog(
            onDismissRequest = { excluindo = null },
            containerColor = Cores.Superficie,
            title = { Text("Excluir “${registro.label}”?", color = Cores.Texto) },
            text = {
                Text(
                    "Os números desta viagem somem para sempre. O hodômetro do carro " +
                        "e os contadores do painel não mudam.",
                    color = Cores.TextoApoio,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.excluirRegistro(registro.recordId); excluindo = null }) {
                    Text("Excluir", color = Cores.Erro)
                }
            },
            dismissButton = {
                TextButton(onClick = { excluindo = null }) { Text("Cancelar", color = Cores.Texto) }
            },
        )
    }
}

/** Em que coluna da comparação este registro caiu, ou -1 se em nenhuma. */
private fun posicaoNaComparacao(modo: ModoHistorico, recordId: String): Int {
    val m = modo as? ModoHistorico.Comparando ?: return -1
    return when (recordId) {
        m.aId -> 0
        m.bId -> 1
        else -> -1
    }
}

@Composable
private fun ItemHistorico(
    registro: TripRecord,
    posicao: Int,
    selecionado: Boolean,
    onClick: () -> Unit,
) {
    Cartao(Modifier.fillMaxWidth().clickable(onClick = onClick), selecionado = selecionado) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    // Na comparação o número diz qual coluna a viagem ocupa;
                    // sem ele o percentual inverte de sinal sem explicação.
                    if (posicao >= 0) "${posicao + 1} · ${registro.label}" else registro.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selecionado) Cores.Destaque else Cores.Texto,
                )
                Text(
                    // A data fica sempre visível: é o que separa duas Trip A.
                    // O "auto" diz que ninguém arquivou: a viagem se fechou.
                    formatoData.format(Date(registro.savedAtMs)) +
                        if (registro.automatic) "  auto" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.TextoApoio,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${TripFormat.km(registro.metrics.distanceKm)}  •  " +
                    "${TripFormat.kml(registro.metrics.avgFuelConsumptionKml)}  •  " +
                    TripFormat.litros(registro.metrics.fuelLitres),
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.TextoApoio,
            )
        }
    }
}

/**
 * A viagem inteira numa tela só: percurso, tempo e combustível lado a lado.
 *
 * Mesmo desenho da tela de detalhes de uma Trip viva, de propósito — é o mesmo
 * conjunto de números, e mudar a ordem faria o motorista reaprender a ler.
 */
@Composable
private fun DetalhesDaViagem(
    registro: TripRecord,
    onComparar: () -> Unit,
    onRenomear: () -> Unit,
    onExcluir: () -> Unit,
) {
    val m = registro.metrics
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    registro.label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Cores.Texto,
                )
                Text(
                    formatoData.format(Date(registro.savedAtMs)) +
                        if (registro.automatic) "  •  fechada sozinha" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.TextoApoio,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // As ações ficam no topo, junto do nome da viagem: é o nome que elas
        // mexem, e no rodapé elas caíam abaixo da dobra numa tela de 600 px.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotaoAcao("Renomear", onRenomear)
            BotaoAcao("Comparar com outra", onComparar)
            BotaoAcao("Excluir", onExcluir, corTexto = Cores.Erro)
        }

        Spacer(Modifier.height(12.dp))

        Cartao(Modifier.fillMaxWidth()) {
            Column {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("PERCURSO", style = EstiloRotulo)
                        LinhaDetalhe("Distância", TripFormat.km(m.distanceKm), destaque = true)
                        LinhaDetalhe("Velocidade média", TripFormat.kmh(m.avgSpeedKmh))
                        LinhaDetalhe("Média andando", TripFormat.kmh(m.avgMovingSpeedKmh))
                        LinhaDetalhe("Máxima", TripFormat.kmh(m.maxSpeedKmh))
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text("TEMPO E CONSUMO", style = EstiloRotulo)
                        LinhaDetalhe("Tempo total", TripFormat.duracao(m.totalTimeS), destaque = true)
                        LinhaDetalhe("Em movimento", TripFormat.duracao(m.movingTimeS))
                        LinhaDetalhe("Parado, motor ligado", TripFormat.duracao(m.idleTimeS))
                        HorizontalDivider(color = Cores.Contorno)
                        LinhaDetalhe("Consumo médio", TripFormat.kml(m.avgFuelConsumptionKml))
                        LinhaDetalhe("Combustível", TripFormat.litros(m.fuelLitres))
                    }
                }
                HorizontalDivider(color = Cores.Contorno)
                // Largura inteira: os dois hodômetros só valem lidos como um
                // intervalo, e o par não cabe em meia tela sem quebrar no meio.
                LinhaDetalhe(
                    "Hodômetro do carro",
                    "${TripFormat.decimal(registro.odometerStartKm)} → " +
                        TripFormat.km(registro.odometerEndKm),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Renomear a viagem, não o contador: "Trip A" é o contador que mediu, "Praia
 * de janeiro" é a viagem que ele mediu daquela vez.
 */
@Composable
private fun DialogoRenomear(
    registro: TripRecord,
    onConfirmar: (String) -> Unit,
    onCancelar: () -> Unit,
) {
    var texto by remember(registro.recordId) { mutableStateOf(registro.label) }
    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = Cores.Superficie,
        title = { Text("Nome desta viagem", color = Cores.Texto) },
        text = {
            Column {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Só muda o nome desta viagem no histórico. O contador " +
                        "“${registro.tripId}” continua com o nome dele.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.TextoApoio,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(texto) },
                enabled = texto.isNotBlank(),
            ) { Text("Salvar", color = Cores.Destaque) }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar", color = Cores.Texto) }
        },
    )
}

@Composable
private fun TabelaComparacao(a: TripRecord, b: TripRecord, linhas: List<ComparisonLine>) {
    Cartao(Modifier.fillMaxSize()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Cabecalho("MÉTRICA", 1.6f, TextAlign.Start)
                // Numeradas na ordem em que foram tocadas: duas Trip A
                // arquivadas em dias diferentes têm o mesmo nome, e sem o
                // número não dá para saber qual coluna é qual.
                Cabecalho("1 · ${a.label.uppercase()}", 1f, TextAlign.End)
                Cabecalho("2 · ${b.label.uppercase()}", 1f, TextAlign.End)
                Cabecalho("Δ %", 0.8f, TextAlign.End)
            }
            HorizontalDivider(color = Cores.Contorno)

            linhas.forEach { linha ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Celula(linha.label, 1.6f, TextAlign.Start, Cores.TextoApoio)
                    Celula(valorFormatado(linha, linha.a), 1f, TextAlign.End, corDoLado(linha, 1))
                    Celula(valorFormatado(linha, linha.b), 1f, TextAlign.End, corDoLado(linha, 2))
                    Celula(
                        TripFormat.percentual(linha.deltaPercent),
                        0.8f,
                        TextAlign.End,
                        when (linha.winner) {
                            1, 2 -> Cores.TextoCorrido
                            else -> Cores.TextoApoio
                        },
                    )
                }
                HorizontalDivider(color = Cores.Contorno)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Em verde, a viagem melhor em cada linha. O percentual é a variação " +
                    "da viagem 2 em relação à viagem 1.",
                style = MaterialTheme.typography.bodySmall,
                color = Cores.TextoApoio,
            )
        }
    }
}

/** Verde para o lado vencedor da linha; cinza claro para o outro. */
private fun corDoLado(linha: ComparisonLine, lado: Int) =
    if (linha.winner == lado) Cores.Confirmacao else Cores.TextoCorrido

private fun valorFormatado(linha: ComparisonLine, valor: Double?): String = when (linha.unit) {
    "km" -> TripFormat.km(valor)
    "km/L" -> TripFormat.kml(valor)
    "km/h" -> TripFormat.kmh(valor)
    "s" -> TripFormat.duracao(valor)
    "%" -> valor?.let { "${TripFormat.decimal(it, 0)}%" } ?: TripFormat.AUSENTE
    "L" -> TripFormat.litros(valor)
    else -> TripFormat.decimal(valor, 2)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cabecalho(
    texto: String,
    peso: Float,
    alinhamento: TextAlign,
) {
    Text(
        texto,
        style = EstiloRotulo,
        textAlign = alinhamento,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = Modifier.weight(peso),
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Celula(
    texto: String,
    peso: Float,
    alinhamento: TextAlign,
    cor: androidx.compose.ui.graphics.Color,
) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyLarge,
        color = cor,
        textAlign = alinhamento,
        maxLines = 1,
        modifier = Modifier.weight(peso),
    )
}
