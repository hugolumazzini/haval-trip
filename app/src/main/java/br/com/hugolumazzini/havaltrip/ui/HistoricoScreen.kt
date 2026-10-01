package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.Cluster
import br.com.hugolumazzini.havaltrip.ModoHistorico
import br.com.hugolumazzini.havaltrip.domain.TipoCombustivel
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.domain.TripRecord
import br.com.hugolumazzini.havaltrip.engine.TripState
import br.com.hugolumazzini.havaltrip.format.TripFormat
import br.com.hugolumazzini.havaltrip.services.ComparisonLine
import br.com.hugolumazzini.havaltrip.ui.theme.Cores
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloRotulo
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.compose.component.shapeComponent
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.chart.column.ColumnChart
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.FloatEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val formatoData = SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR"))

private enum class AbaHistorico(val rotulo: String) {
    VIAGENS("Todas as viagens"),
    GRAFICOS("Gráficos"),
}

/**
 * Histórico à esquerda, a viagem escolhida à direita.
 *
 * Ler uma viagem é o motivo comum de abrir esta tela — "quanto deu a ida à
 * praia?" — e por isso é o que um toque faz. Comparar duas é a pergunta mais
 * rara, então vira uma ação a partir da viagem aberta, e não o modo padrão.
 */
@Composable
fun HistoricoScreen(vm: TripViewModel, estado: TripState) {
    var aba by remember { mutableStateOf(AbaHistorico.VIAGENS) }

    val modo by vm.modoHistorico.collectAsStateWithLifecycle()
    val comparando = modo is ModoHistorico.Comparando
    val emFoco = vm.registroEmFoco(modo, estado.history)
    val ajustes by Cluster.ajustes.collectAsStateWithLifecycle()

    /** `null` = nenhum diálogo aberto. Estado da tela, não do módulo. */
    var renomeando by remember { mutableStateOf<TripRecord?>(null) }
    var excluindo by remember { mutableStateOf<TripRecord?>(null) }
    var editandoPreco by remember { mutableStateOf<TripRecord?>(null) }

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

        // Abas de navegação
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AbaHistorico.entries.forEach { abaPossivel ->
                OpcaoAbas(
                    texto = abaPossivel.rotulo,
                    marcada = aba == abaPossivel,
                    habilitada = true,
                    onClick = { aba = abaPossivel },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (estado.history.isEmpty()) {
            Vazio("Nenhuma viagem arquivada ainda.\nFeche uma viagem no painel para ela aparecer aqui.")
            return
        }

        when (aba) {
            AbaHistorico.VIAGENS -> TelaViagensHistorico(
                vm = vm,
                estado = estado,
                modo = modo,
                emFoco = emFoco,
                comparando = comparando,
                onRenomear = { renomeando = it },
                onEditarPreco = { editandoPreco = it },
                onExcluir = { excluindo = it },
            )
            AbaHistorico.GRAFICOS -> TelaGraficos(estado)
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

    editandoPreco?.let { registro ->
        var digitos by remember(registro.recordId) { mutableStateOf(registro.precoDolitroCombustivel?.let { "%d".format((it * 1000).toLong()) } ?: "") }
        var tipoCombustivel by remember(registro.recordId) { mutableStateOf(registro.tipoCombustivel ?: TipoCombustivel.GASOLINA_COMUM) }
        val digitsOnly = digitos.filter { it.isDigit() }
        val precoFormatado = when {
            digitsOnly.isEmpty() -> ""
            digitsOnly.length <= 3 -> digitsOnly
            else -> digitsOnly.dropLast(3) + "," + digitsOnly.takeLast(3)
        }
        val preco = digitsOnly.toLongOrNull()?.toDouble()?.div(1000) ?: 0.0
        val custoExato = preco * registro.metrics.fuelLitres
        val custoArredondado = kotlin.math.ceil(custoExato * 100) / 100
        AlertDialog(
            onDismissRequest = { editandoPreco = null },
            containerColor = Cores.Superficie,
            title = { Text("Preço do combustível", color = Cores.Texto) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tipo:", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
                    TipoCombustivel.entries.forEach { tipo ->
                        Row(
                            modifier = Modifier.clickable { tipoCombustivel = tipo }.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(
                                    if (tipoCombustivel == tipo) Cores.Destaque else Cores.Campo
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(tipo.rotulo, color = Cores.TextoCorrido)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Valor:", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("R$ ", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = precoFormatado,
                            onValueChange = { novoValor ->
                                digitos = novoValor.filter { it.isDigit() }.take(7)
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Text("/ L", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Valor com 3 casas decimais", style = MaterialTheme.typography.bodySmall, color = Cores.TextoApoio)
                    if (preco > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("Estimativa: ${TripFormat.reais(custoArredondado)}", style = MaterialTheme.typography.bodySmall, color = Cores.Destaque)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.atualizarPrecoRegistro(registro.recordId, preco, tipoCombustivel); editandoPreco = null
                    },
                ) { Text("Salvar", color = Cores.Destaque) }
            },
            dismissButton = {
                TextButton(onClick = { editandoPreco = null }) { Text("Cancelar", color = Cores.Texto) }
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
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
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
            Spacer(Modifier.height(12.dp))
            val linha = "${TripFormat.km(registro.metrics.distanceKm)}  •  " +
                    "${TripFormat.kml(registro.metrics.avgFuelConsumptionKml)}  •  " +
                    TripFormat.litros(registro.metrics.fuelLitres)
            Text(
                if (registro.custoBR != null) "$linha  •  ${TripFormat.reais(registro.custoBR)}" else linha,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
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
    onEditarPreco: () -> Unit,
    onExcluir: () -> Unit,
) {
    val m = registro.metrics
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    registro.label,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
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
            BotaoAcao("Preço combustível", onEditarPreco)
            BotaoAcao("Comparar com outra", onComparar)
            BotaoAcao("Excluir", onExcluir, corTexto = Cores.Erro)
        }

        Spacer(Modifier.height(12.dp))

        Cartao(Modifier.fillMaxWidth().weight(1f)) {
            Row {
                // Coluna 1: Percurso + Hodômetro
                Column(Modifier.weight(1f)) {
                    Text(
                        "PERCURSO",
                        style = EstiloRotulo.copy(fontSize = 14.sp),
                    )
                    LinhaDetalheViagem("Distância", TripFormat.km(m.distanceKm), destaque = true)
                    LinhaDetalheViagem("Velocidade média", TripFormat.kmh(m.avgSpeedKmh))
                    LinhaDetalheViagem("Média andando", TripFormat.kmh(m.avgMovingSpeedKmh))
                    LinhaDetalheViagem("Máxima", TripFormat.kmh(m.maxSpeedKmh))
                    HorizontalDivider(color = Cores.Contorno, modifier = Modifier.padding(vertical = 8.dp))
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(
                            "Hodômetro",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp),
                            color = Cores.TextoApoio,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${TripFormat.decimal(registro.odometerStartKm)} → ${TripFormat.km(registro.odometerEndKm)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp),
                            color = Cores.Texto,
                        )
                    }
                }

                Spacer(Modifier.width(48.dp))

                // Coluna 2: Tempo e Consumo
                Column(Modifier.weight(1f)) {
                    Text(
                        "TEMPO E CONSUMO",
                        style = EstiloRotulo.copy(fontSize = 14.sp),
                    )
                    LinhaDetalheViagem("Tempo total", TripFormat.duracao(m.totalTimeS), destaque = true)
                    LinhaDetalheViagem("Em movimento", TripFormat.duracao(m.movingTimeS))
                    LinhaDetalheViagem("Parado, motor ligado", TripFormat.duracao(m.idleTimeS))
                    HorizontalDivider(color = Cores.Contorno, modifier = Modifier.padding(vertical = 8.dp))
                    LinhaDetalheViagem("Consumo médio", TripFormat.kml(m.avgFuelConsumptionKml))
                    LinhaDetalheViagem("Combustível", TripFormat.litros(m.fuelLitres))
                }

                Spacer(Modifier.width(48.dp))

                // Coluna 3: Custo
                Column(Modifier.weight(1f)) {
                    Text(
                        "CUSTO",
                        style = EstiloRotulo.copy(fontSize = 14.sp),
                    )
                    if (registro.custoBR != null) {
                        LinhaDetalheViagem("Tipo", registro.tipoCombustivel?.rotulo ?: "—")
                        LinhaDetalheViagem("Valor", TripFormat.reais(registro.precoDolitroCombustivel!!) + " / L", destaque = true)
                        LinhaDetalheViagem("Custo da viagem", TripFormat.reais(registro.custoBR), destaque = true)
                    } else {
                        Text(
                            "Sem informações de custo",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp),
                            color = Cores.TextoApoio,
                        )
                    }
                }
            }
        }
    }
}

/** Linha de detalhe customizada para a tela de viagem com fontes maiores */
@Composable
private fun LinhaDetalheViagem(rotulo: String, valor: String, destaque: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            rotulo,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp),
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            valor,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = if (destaque) 28.sp else 24.sp
            ),
            color = if (destaque) Cores.Destaque else Cores.Texto,
        )
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

@Composable
private fun TelaViagensHistorico(
    vm: TripViewModel,
    estado: TripState,
    modo: ModoHistorico,
    emFoco: TripRecord?,
    comparando: Boolean,
    onRenomear: (TripRecord) -> Unit,
    onEditarPreco: (TripRecord) -> Unit,
    onExcluir: (TripRecord) -> Unit,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Lado esquerdo: Lista
        Column(Modifier.width(320.dp).fillMaxHeight()) {
            // Lista de viagens
            LazyColumn(
                Modifier.fillMaxSize(),
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
        }

        // Lado direito: Detalhes (alinhado com o topo das abas)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val comparacao = vm.comparar(modo, estado.history)
            when {
                comparacao != null -> TabelaComparacao(comparacao.a, comparacao.b, comparacao.lines)
                comparando -> Vazio("Toque na segunda viagem, na lista ao lado.")
                emFoco != null -> DetalhesDaViagem(
                    registro = emFoco,
                    onComparar = { vm.compararComOutra(emFoco.recordId) },
                    onRenomear = { onRenomear(emFoco) },
                    onEditarPreco = { onEditarPreco(emFoco) },
                    onExcluir = { onExcluir(emFoco) },
                )
                else -> Vazio("Escolha uma viagem na lista ao lado.")
            }
        }
    }
}

private enum class PeriodoGrafico(val rotulo: String) {
    DIA("Dia"),
    SEMANA("Semana"),
    MES("Mês"),
}

@Composable
private fun OpcaoAbas(
    texto: String,
    marcada: Boolean,
    habilitada: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (marcada) Cores.SuperficieSelecionada else Cores.Campo)
            .clickable(enabled = habilitada, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                marcada -> Cores.Destaque
                habilitada -> Cores.TextoCorrido
                else -> Cores.TextoCorrido.copy(alpha = 0.35f)
            },
        )
    }
}

private data class DadosPeriodo(
    val label: String,
    val distancia: Double,
    val consumo: Double,
    val combustivel: Double,
)

private fun agruparPorDia(history: List<TripRecord>): List<DadosPeriodo> {
    return history
        .groupBy { registro ->
            val cal = Calendar.getInstance().apply { timeInMillis = registro.savedAtMs }
            "%02d/%02d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
        }
        .map { (data, viagens) ->
            DadosPeriodo(
                label = data,
                distancia = viagens.sumOf { it.metrics.distanceKm },
                consumo = if (viagens.isNotEmpty()) viagens.mapNotNull { it.metrics.avgFuelConsumptionKml }.average() else 0.0,
                combustivel = viagens.sumOf { it.metrics.fuelLitres },
            )
        }
        .sortedBy { it.label }
}

private fun agruparPorSemana(history: List<TripRecord>): List<DadosPeriodo> {
    return history
        .groupBy { registro ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = registro.savedAtMs
                // Voltar para o domingo da semana
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            }
            // Usar o timestamp do domingo como chave de agrupamento
            cal.timeInMillis
        }
        .map { (domingoMs, viagens) ->
            val inicio = Calendar.getInstance().apply { timeInMillis = domingoMs }
            val fim = Calendar.getInstance().apply {
                timeInMillis = domingoMs
                add(Calendar.DAY_OF_YEAR, 6)
            }
            val label = "%02d/%02d a %02d/%02d".format(
                inicio.get(Calendar.DAY_OF_MONTH),
                inicio.get(Calendar.MONTH) + 1,
                fim.get(Calendar.DAY_OF_MONTH),
                fim.get(Calendar.MONTH) + 1,
            )
            DadosPeriodo(
                label = label,
                distancia = viagens.sumOf { it.metrics.distanceKm },
                consumo = if (viagens.isNotEmpty()) viagens.mapNotNull { it.metrics.avgFuelConsumptionKml }.average() else 0.0,
                combustivel = viagens.sumOf { it.metrics.fuelLitres },
            )
        }
        .sortedBy { it.label }
}

private fun agruparPorMes(history: List<TripRecord>): List<DadosPeriodo> {
    return history
        .groupBy { registro ->
            val cal = Calendar.getInstance().apply { timeInMillis = registro.savedAtMs }
            "%02d/%04d".format(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
        }
        .map { (mes, viagens) ->
            DadosPeriodo(
                label = mes,
                distancia = viagens.sumOf { it.metrics.distanceKm },
                consumo = if (viagens.isNotEmpty()) viagens.mapNotNull { it.metrics.avgFuelConsumptionKml }.average() else 0.0,
                combustivel = viagens.sumOf { it.metrics.fuelLitres },
            )
        }
        .sortedBy { it.label }
}

@Composable
private fun TelaGraficos(estado: TripState) {
    var periodo by remember { mutableStateOf(PeriodoGrafico.DIA) }

    if (estado.history.isEmpty()) {
        Vazio("Nenhuma viagem arquivada ainda.\nFeche uma viagem no painel para ela aparecer aqui.")
        return
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Lado esquerdo: Seletores de período (320dp - igual à lista de viagens)
        Column(
            Modifier.width(320.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeriodoGrafico.entries.forEach { p ->
                Cartao(
                    Modifier.fillMaxWidth().clickable { periodo = p },
                    selecionado = periodo == p,
                ) {
                    Text(
                        p.rotulo,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (periodo == p) Cores.Destaque else Cores.Texto,
                    )
                }
            }
        }

        // Lado direito: Gráficos (50/50 da altura)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val dados = when (periodo) {
                PeriodoGrafico.DIA -> agruparPorDia(estado.history)
                PeriodoGrafico.SEMANA -> agruparPorSemana(estado.history)
                PeriodoGrafico.MES -> agruparPorMes(estado.history)
            }

            if (dados.isEmpty()) {
                Vazio("Nenhuma viagem neste período")
            } else {
                // Gráfico de Distância - 50% da altura
                Cartao(Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.fillMaxSize()) {
                        Text(
                            "Distância (km)",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                            color = Cores.Texto,
                        )
                        Spacer(Modifier.height(16.dp))
                        GraficoDistancia(dados)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Gráfico de Consumo - 50% da altura
                Cartao(Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.fillMaxSize()) {
                        Text(
                            "Consumo Médio (km/L)",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                            color = Cores.Texto,
                        )
                        Spacer(Modifier.height(16.dp))
                        GraficoConsumo(dados)
                    }
                }
            }
        }
    }
}

@Composable
private fun GraficoDistancia(dados: List<DadosPeriodo>) {
    if (dados.isEmpty()) return

    val entries = dados.mapIndexed { index, d ->
        FloatEntry(index.toFloat(), d.distancia.toFloat())
    }
    val chartEntryModel = entryModelOf(entries)
    val labels = dados.map { it.label }

    ProvideChartStyle {
        Chart(
            chart = columnChart(
                columns = listOf(
                    LineComponent(
                        color = Cores.Destaque.toArgb(),
                        thicknessDp = 16f,
                        shape = Shapes.roundedCornerShape(
                            topLeftPercent = 8,
                            topRightPercent = 8,
                        ),
                    ),
                ),
            ),
            model = chartEntryModel,
            startAxis = rememberStartAxis(
                guideline = null,
                titleComponent = null,
            ),
            bottomAxis = rememberBottomAxis(
                guideline = null,
                valueFormatter = { value, _ ->
                    labels.getOrNull(value.toInt()) ?: ""
                },
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun GraficoConsumo(dados: List<DadosPeriodo>) {
    if (dados.isEmpty()) return

    val entries = dados.mapIndexed { index, d ->
        FloatEntry(index.toFloat(), d.consumo.toFloat())
    }
    val chartEntryModel = entryModelOf(entries)
    val labels = dados.map { it.label }

    ProvideChartStyle {
        Chart(
            chart = columnChart(
                columns = listOf(
                    LineComponent(
                        color = Cores.Destaque.toArgb(),
                        thicknessDp = 16f,
                        shape = Shapes.roundedCornerShape(
                            topLeftPercent = 8,
                            topRightPercent = 8,
                        ),
                    ),
                ),
            ),
            model = chartEntryModel,
            startAxis = rememberStartAxis(
                guideline = null,
                titleComponent = null,
            ),
            bottomAxis = rememberBottomAxis(
                guideline = null,
                valueFormatter = { value, _ ->
                    labels.getOrNull(value.toInt()) ?: ""
                },
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun GraficoCombustivel(dados: List<DadosPeriodo>) {
    val maxCombustivel = (dados.maxOfOrNull { it.combustivel } ?: 1.0).toFloat()
    Canvas(Modifier.fillMaxWidth().height(250.dp)) {
        val barWidth = size.width / (dados.size * 1.5f)
        val spacing = barWidth * 0.5f
        val totalBarWidth = barWidth + spacing
        val maxHeight = size.height * 0.8f

        dados.forEachIndexed { index, d ->
            val x = (index * totalBarWidth + spacing).toFloat()
            val barHeight = ((d.combustivel / maxCombustivel) * maxHeight).toFloat()
            val y = (size.height - barHeight - 20f).toFloat()

            drawRect(
                color = Cores.Destaque,
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
            )
        }
    }
}
