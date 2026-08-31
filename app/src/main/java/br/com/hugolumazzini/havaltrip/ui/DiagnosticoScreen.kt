package br.com.hugolumazzini.havaltrip.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.Envio
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.telemetry.Interpretacao
import br.com.hugolumazzini.havaltrip.ui.theme.Cores
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloRotulo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val hora = SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("pt-BR"))

/**
 * O que o carro está dizendo, cru, sem conversão nenhuma.
 *
 * Esta tela é ferramenta de campo, não parte do produto: existe para o primeiro
 * teste no H6 responder de que jeito cada grandeza é publicada — em que unidade,
 * com que código, de quanto em quanto tempo. Sem ela, "a distância está errada"
 * não distingue leitura errada de conversão errada.
 */
@Composable
fun DiagnosticoScreen(vm: TripViewModel) {
    val leituras by vm.diario.atual.collectAsStateWithLifecycle()
    val fita by vm.diario.fita.collectAsStateWithLifecycle()
    val interpretacao by vm.diario.interpretacao.collectAsStateWithLifecycle()
    val envio by vm.envio.collectAsStateWithLifecycle()
    val fonteReal by vm.fonteReal.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("DIAGNÓSTICO DA TELEMETRIA", style = EstiloRotulo)
                Text(
                    when {
                        !vm.shisukuInstalado ->
                            "HavalShisuku não encontrado nesta central — sem ele não chega nada do carro"
                        !fonteReal ->
                            "Simulador ligado: os números da tela são inventados"
                        leituras.isEmpty() ->
                            "Ponte encontrada, mas nada chegou ainda. Ligue o carro e ande um pouco."
                        else ->
                            "${leituras.size} chaves recebidas do carro"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (fonteReal && leituras.isNotEmpty()) Cores.Confirmacao else Cores.Atencao,
                )
            }
            BotaoAcao("Voltar ao painel", vm::voltarAoPainel)
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotaoAcao("Pedir tudo ao carro", vm::pedirTudoAoCarro)
            BotaoAcao(
                texto = if (fonteReal) "Fonte: carro" else "Fonte: simulador",
                onClick = { vm.usarFonteReal(!fonteReal) },
                cor = if (fonteReal) Cores.Campo else Cores.SuperficieSelecionada,
                corTexto = if (fonteReal) Cores.Texto else Cores.Atencao,
            )
            BotaoAcao(
                texto = when (envio) {
                    is Envio.Enviando -> "Enviando…"
                    else -> "Gerar e enviar relatório"
                },
                onClick = vm::enviarRelatorio,
                habilitado = envio !is Envio.Enviando,
                cor = Cores.SuperficieSelecionada,
                corTexto = Cores.Destaque,
            )
            // A conversão do consumo é a única incógnita que muda os números na
            // hora: dá para virar a chave dentro do carro e ver qual das duas
            // leituras bate com o painel de fábrica.
            BotaoAcao(
                texto = "Consumo: ${interpretacao.rotulo}",
                onClick = {
                    vm.interpretarConsumoComo(
                        if (interpretacao == Interpretacao.LITROS_POR_100KM) {
                            Interpretacao.LITROS_POR_HORA
                        } else {
                            Interpretacao.LITROS_POR_100KM
                        }
                    )
                },
            )
        }

        if (envio !is Envio.Parado) {
            Spacer(Modifier.height(10.dp))
            ResultadoDoEnvio(envio, contexto, onTentarDeNovo = vm::enviarRelatorio)
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Cartao(Modifier.weight(1f).fillMaxHeight()) {
                Column {
                    Text("VALOR ATUAL DE CADA CHAVE", style = EstiloRotulo)
                    Spacer(Modifier.height(6.dp))
                    if (leituras.isEmpty()) {
                        Text(
                            "Nada recebido.\n\nSe o HavalShisuku estiver instalado, confira se ele " +
                                "está rodando; ele é quem publica os dados do carro.",
                            color = Cores.TextoApoio,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        LazyColumn {
                            items(leituras.keys.sorted()) { chave ->
                                val leitura = leituras.getValue(chave)
                                LinhaCrua(
                                    chave = chave.removePrefix("car.basic."),
                                    valor = leitura.valor,
                                    apoio = "${leitura.vezes}x · ${hora.format(Date(leitura.emMs))}",
                                )
                            }
                        }
                    }
                }
            }

            Cartao(Modifier.weight(1f).fillMaxHeight()) {
                Column {
                    Text("FITA DOS ÚLTIMOS EVENTOS", style = EstiloRotulo)
                    Text(
                        "É o movimento do número que revela a unidade, não o valor parado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cores.TextoApoio,
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(reverseLayout = true) {
                        items(fita) { evento ->
                            Text(
                                "${hora.format(Date(evento.emMs))}  " +
                                    "${evento.chave.removePrefix("car.basic.")} = ${evento.valor}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = Cores.TextoCorrido,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * O endereço do relatório, grande e copiável.
 *
 * Ele é lido de dentro do carro, provavelmente com o celular na mão — daí o
 * tamanho e o botão de copiar, em vez de um texto pequeno de status.
 */
@Composable
private fun ResultadoDoEnvio(envio: Envio, contexto: Context, onTentarDeNovo: () -> Unit) {
    when (envio) {
        is Envio.Pronto -> Cartao(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("RELATÓRIO ENVIADO", style = EstiloRotulo)
                    Text(
                        envio.endereco,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Cores.Destaque,
                    )
                    Text(
                        "Abra este endereço e mande para mim. Fica no ar por 30 dias.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cores.TextoApoio,
                    )
                }
                BotaoAcao("Copiar", { copiar(contexto, envio.endereco) })
            }
        }

        is Envio.Falhou -> Cartao(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NÃO DEU PARA ENVIAR", style = EstiloRotulo)
                    Text(envio.motivo, color = Cores.Atencao)
                    // O arquivo local é a rede de segurança: sem internet no
                    // carro, a coleta não se perde — sai depois, por cabo.
                    Text(
                        "O relatório ficou gravado em ${envio.arquivo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cores.TextoApoio,
                    )
                }
                // Uma segunda tentativa vale a pena antes de o carro sair do
                // alcance do Wi-Fi: a falha mais provável aqui é rede, não bug.
                BotaoAcao("Tentar de novo", onTentarDeNovo)
            }
        }

        else -> Unit
    }
}

private fun copiar(contexto: Context, texto: String) {
    val area = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    area?.setPrimaryClip(ClipData.newPlainText("Haval Trip", texto))
}

@Composable
private fun LinhaCrua(chave: String, valor: String, apoio: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                chave,
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.TextoApoio,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                valor,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = Cores.Texto,
                softWrap = false,
            )
        }
        Text(apoio, style = MaterialTheme.typography.bodySmall, color = Cores.Contorno)
        HorizontalDivider(color = Cores.Contorno)
    }
}
