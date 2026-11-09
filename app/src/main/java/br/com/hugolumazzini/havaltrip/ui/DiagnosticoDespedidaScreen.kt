package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.hugolumazzini.havaltrip.painel.EstadoDaDespedida
import br.com.hugolumazzini.havaltrip.painel.HistoricoDaDespedida
import kotlinx.coroutines.delay

/**
 * Tela de diagnóstico da despedida.
 *
 * Mostra histórico de eventos para debug sem precisar de ADB.
 * Útil para testar no carro.
 */
@Composable
fun DiagnosticoDespedidaScreen() {
    var eventos by remember { mutableStateOf(HistoricoDaDespedida.obterEventos()) }
    var despedidaAtiva by remember { mutableStateOf(EstadoDaDespedida.estaAtiva()) }

    // Atualiza a cada segundo
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            eventos = HistoricoDaDespedida.obterEventos()
            despedidaAtiva = EstadoDaDespedida.estaAtiva()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Cabeçalho
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🔍 Diagnóstico da Despedida",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            // Indicador de estado atual
            Box(
                modifier = Modifier
                    .background(
                        if (despedidaAtiva) Color(0xFF00FF00) else Color(0xFF666666),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    if (despedidaAtiva) "ATIVA" else "INATIVA",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        HorizontalDivider(color = Color.Gray, thickness = 1.dp)

        Spacer(modifier = Modifier.height(16.dp))

        // Botão para limpar histórico
        Button(
            onClick = {
                HistoricoDaDespedida.limpar()
                eventos = HistoricoDaDespedida.obterEventos()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF444444)
            )
        ) {
            Text("🗑️ Limpar Histórico", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lista de eventos
        if (eventos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nenhum evento registrado",
                    color = Color.Gray,
                    fontSize = 18.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(eventos) { evento ->
                    EventoCard(evento)
                }
            }
        }
    }
}

@Composable
private fun EventoCard(evento: HistoricoDaDespedida.Evento) {
    val cor = when (evento.tipo) {
        HistoricoDaDespedida.TipoEvento.ATIVACAO -> Color(0xFF00FF00)
        HistoricoDaDespedida.TipoEvento.DESATIVACAO -> Color(0xFFFF6666)
        HistoricoDaDespedida.TipoEvento.BLOQUEIO -> Color(0xFF00AAFF)
        HistoricoDaDespedida.TipoEvento.TENTATIVA_REMOCAO -> Color(0xFFFFAA00)
        HistoricoDaDespedida.TipoEvento.PROJECAO -> Color(0xFFAA00FF)
        HistoricoDaDespedida.TipoEvento.ERRO -> Color(0xFFFF0000)
        HistoricoDaDespedida.TipoEvento.INFO -> Color(0xFFDDDDDD)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF222222)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hora
            Text(
                evento.hora,
                color = Color.Gray,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.alignByBaseline()
            )

            // Descrição
            Text(
                evento.descricao,
                color = cor,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline()
            )
        }
    }
}
