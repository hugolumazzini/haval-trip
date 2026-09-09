package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.content.Intent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.Atualizador
import br.com.hugolumazzini.havaltrip.Cluster
import br.com.hugolumazzini.havaltrip.CorDoCluster
import br.com.hugolumazzini.havaltrip.ClusterActivity
import br.com.hugolumazzini.havaltrip.ESPIANDO
import br.com.hugolumazzini.havaltrip.ClusterCarroActivity
import br.com.hugolumazzini.havaltrip.ItemDoCluster
import br.com.hugolumazzini.havaltrip.LugarNoPainel
import br.com.hugolumazzini.havaltrip.TamanhoDoCarro
import br.com.hugolumazzini.havaltrip.TamanhoNoPainel
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.engine.TripState
import br.com.hugolumazzini.havaltrip.storage.TripSnapshot
import br.com.hugolumazzini.havaltrip.ui.theme.Cores
import br.com.hugolumazzini.havaltrip.ui.theme.EstiloRotulo

/**
 * Paradas em que a barra de zeragem encaixa, em minutos, de "na hora" a 3 h.
 *
 * A barra anda por estas paradas em vez de deslizar contínua porque ninguém
 * quer "17 minutos": os tempos que importam são redondos, e num toque dentro do
 * carro acertar 15 num deslize livre seria sorte. Elas são mais juntas embaixo
 * de propósito — a diferença entre 5 e 10 minutos muda o que conta como uma
 * viagem só, e a diferença entre 2 h e 2 h 30 não muda nada.
 */
private val PARADAS = listOf(0, 1, 2, 5, 10, 15, 20, 30, 45, 60, 90, 120, 150, 180)

/** O rótulo de cada parada, na linguagem de quem está lendo o painel. */
fun rotuloDoTempo(minutos: Int): String = when {
    minutos == 0 -> "na hora de desligar"
    minutos < 60 -> "$minutos min"
    minutos % 60 == 0 -> "${minutos / 60} h"
    else -> "${minutos / 60} h ${minutos % 60} min"
}

/** A parada mais próxima do que está gravado — o que a barra mostra ao abrir. */
private fun paradaMaisProxima(segundos: Double?): Int {
    val minutos = ((segundos ?: 0.0) / 60.0)
    return PARADAS.indices.minBy { kotlin.math.abs(PARADAS[it] - minutos) }
}

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

        Cartao(Modifier.fillMaxWidth()) { ZeragemAutomatica(vm, estado) }

        Spacer(Modifier.height(14.dp))

        Cartao(Modifier.fillMaxWidth()) { PainelDeInstrumentos(estado) }

        Spacer(Modifier.height(14.dp))

        Cartao(Modifier.fillMaxWidth()) { SobreEAtualizacao(vm) }
    }
}

/**
 * A versão instalada e o caminho para a próxima.
 *
 * A versão fica escrita mesmo sem ninguém procurar atualização: quando algo
 * estranho acontece no carro, a primeira pergunta é sempre "qual versão é essa?",
 * e ter de descobrir isso pelas configurações do Android, dirigindo, é ruim.
 */
@Composable
private fun SobreEAtualizacao(vm: TripViewModel) {
    val (nome, codigo) = vm.versaoInstalada
    val situacao by vm.atualizador.collectAsStateWithLifecycle()

    Column {
        Text("Versão", style = MaterialTheme.typography.titleMedium, color = Cores.Texto)
        Spacer(Modifier.height(4.dp))
        Text(
            "$nome (código $codigo)",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoCorrido,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "A verificação consulta o catálogo da Haval APK Store. Precisa de internet — " +
                "no carro, o Wi‑Fi do celular resolve.",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(12.dp))

        when (val atual = situacao) {
            is Atualizador.Procurando -> Text(
                "Procurando…",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.TextoApoio,
            )

            is Atualizador.EmDia -> {
                Text(
                    "Você já está na versão mais nova.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.Confirmacao,
                )
                Spacer(Modifier.height(10.dp))
                BotaoAcao("Verificar atualização", vm::procurarAtualizacao)
            }

            is Atualizador.Disponivel -> {
                Text(
                    "Versão ${atual.versao.versionName} disponível — " +
                        "${megabytes(atual.versao.sizeBytes)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.Destaque,
                )
                Spacer(Modifier.height(10.dp))
                BotaoAcao(
                    texto = "Baixar e instalar",
                    onClick = { vm.baixarEInstalar(atual.versao) },
                    cor = Cores.SuperficieSelecionada,
                    corTexto = Cores.Destaque,
                )
            }

            is Atualizador.Baixando -> {
                Text(
                    atual.progresso
                        ?.let { "Baixando… ${(it * 100).toInt()}%" }
                        ?: "Baixando…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.TextoCorrido,
                )
                Spacer(Modifier.height(8.dp))
                // Sem porcentagem quando o servidor não diz o tamanho: uma barra
                // que anda sozinha sem saber para onde mentiria sobre o quanto falta.
                atual.progresso?.let {
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                        color = Cores.Destaque,
                        trackColor = Cores.Campo,
                    )
                }
            }

            is Atualizador.Instalando -> Text(
                "O instalador do Android assumiu daqui. Confirme na tela dele; " +
                    "as viagens ficam guardadas.",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.TextoCorrido,
            )

            is Atualizador.Falhou -> {
                Text(
                    "Não deu certo: ${atual.motivo}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.Atencao,
                )
                Spacer(Modifier.height(10.dp))
                BotaoAcao("Tentar de novo", vm::procurarAtualizacao)
            }

            is Atualizador.Parado -> BotaoAcao("Verificar atualização", vm::procurarAtualizacao)
        }
    }
}

private fun megabytes(bytes: Long): String =
    "%.1f MB".format(bytes / 1_048_576.0).replace('.', ',')

/**
 * A barra do tempo de zeragem.
 *
 * Guarda a posição do dedo num estado próprio e só grava quando o dedo sai:
 * escrever no arquivo a cada pixel arrastado seria dezenas de gravações para um
 * ajuste só, e no cartão da central isso se sente.
 */
@Composable
private fun ZeragemAutomatica(vm: TripViewModel, estado: TripState) {
    val gravado = paradaMaisProxima(estado.zeragemAutomaticaS)
    // A chave amarra o rascunho ao valor gravado: quando ele muda por fora
    // (outra tela, o app reabrindo), a barra recomeça do valor novo em vez de
    // ficar presa onde o dedo largou da última vez.
    var parada by remember(gravado) { mutableFloatStateOf(gravado.toFloat()) }
    val minutos = PARADAS[parada.toInt().coerceIn(PARADAS.indices)]

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "Zerar a Viagem atual",
                style = MaterialTheme.typography.titleMedium,
                color = Cores.Texto,
                modifier = Modifier.weight(1f),
            )
            Text(
                rotuloDoTempo(minutos),
                style = MaterialTheme.typography.titleMedium,
                color = Cores.Destaque,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Com a chave fora por mais que este tempo, a Viagem atual é arquivada " +
                "no histórico e recomeça do zero. Abaixo dele, o trajeto continua o mesmo — " +
                "é o que faz uma parada rápida não virar duas viagens.",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(4.dp))
        // Numa tela de 1900 px a barra inteira ficaria com dez centímetros por
        // parada: precisa, mas exige atravessar o painel com o dedo.
        Slider(
            modifier = Modifier.widthIn(max = 700.dp),
            value = parada,
            onValueChange = { parada = it },
            onValueChangeFinished = { vm.definirZeragemAutomatica(minutos * 60.0) },
            valueRange = 0f..(PARADAS.size - 1).toFloat(),
            // Uma parada a menos que os pontos: `steps` conta só os do meio.
            steps = PARADAS.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = Cores.Destaque,
                activeTrackColor = Cores.Destaque,
                inactiveTrackColor = Cores.Campo,
                // Os tiquinhos das paradas somem: com catorze deles a barra
                // vira um pente e ninguém lê o número por cima disso.
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Row(Modifier.widthIn(max = 700.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("na hora", style = MaterialTheme.typography.bodySmall, color = Cores.TextoApoio)
            Text("3 h", style = MaterialTheme.typography.bodySmall, color = Cores.TextoApoio)
        }
    }
}

/** Botão de escolha única, no tamanho de dedo que a central pede. */
@Composable
private fun Opcao(texto: String, marcada: Boolean, onClick: () -> Unit) {
    OpcaoColorida(texto, marcada, Cores.Destaque, onClick)
}

/**
 * A mesma pastilha, com a cor do texto marcado escolhida por quem chama.
 *
 * Existe para a escolha de cor do painel poder se mostrar na própria cor: uma
 * lista de nomes ("Âmbar", "Verde") todos escritos em azul obrigaria o
 * motorista a tocar para descobrir o que cada um faz.
 */
@Composable
private fun OpcaoColorida(texto: String, marcada: Boolean, corMarcada: Color, onClick: () -> Unit) {
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
            color = if (marcada) corMarcada else Cores.TextoCorrido,
        )
    }
}

/**
 * O que o Haval Trip mostra no painel de instrumentos.
 *
 * Quem coloca a janela lá é o Impulse, na tela "Telas" dele — ele é que decide
 * onde e de que tamanho. Aqui se decide só o conteúdo: qual contador, quais
 * números, que tamanho de letra e que cor. A separação não é escolha minha, é
 * como o mecanismo funciona, e por isso está escrita na tela: sem essa frase, o
 * motorista configuraria tudo aqui e ficaria esperando algo aparecer sozinho.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PainelDeInstrumentos(estado: TripState) {
    val ajustes by Cluster.ajustes.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    /**
     * Abre a janela do painel aqui na central, para conferir o ajuste sem
     * depender do Impulse nem do carro ligado. É a mesma tela, com o mesmo
     * tamanho de tela (1920 x 720): o que aparecer aqui é o que vai aparecer
     * lá. Para sair, o botão Voltar.
     */
    fun espiar(atividade: Class<*>) {
        contexto.startActivity(Intent(contexto, atividade).putExtra(ESPIANDO, true))
    }

    Column {
        Text("Painel de instrumentos", style = MaterialTheme.typography.titleMedium, color = Cores.Texto)
        Spacer(Modifier.height(4.dp))
        Text(
            "O resumo que aparece no painel atrás do volante. No Impulse, em Telas: " +
                "pacote br.com.hugolumazzini.havaltrip, atividade " +
                "br.com.hugolumazzini.havaltrip.ClusterActivity, tela 3. Lá, dê ao app " +
                "a tela inteira — sliders no zero e na ponta, 1920 x 720. A posição " +
                "de verdade se escolhe aqui embaixo, e o que sobra fica transparente.",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.TextoApoio,
        )

        Spacer(Modifier.height(14.dp))
        Text("Qual contador", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // "O da tela" é o padrão porque acompanha quem troca de contador na
            // central, sem obrigar a vir aqui de novo.
            Opcao("O da tela", ajustes.tripId == null) { Cluster.usarTrip(null) }
            estado.trips.forEach { trip ->
                Opcao(trip.label, ajustes.tripId == trip.id) { Cluster.usarTrip(trip.id) }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Quais informações", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(4.dp))
        Text(
            "Aparecem na ordem em que você marcar. Três cabem bem numa faixa; " +
                "acima disso, dê mais espaço à janela no Impulse.",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ItemDoCluster.entries.forEach { item ->
                Opcao(item.descricao, item in ajustes.itens) { Cluster.alternarItem(item) }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Tamanho da letra", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(4.dp))
        Text(
            "Normal já se ajusta sozinho ao tamanho da janela. As outras opções " +
                "só puxam esse cálculo para cima ou para baixo.",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ESCALAS.forEach { (rotulo, escala) ->
                Opcao(rotulo, kotlin.math.abs(ajustes.escalaFonte - escala) < 0.01f) {
                    Cluster.usarEscala(escala)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Cor dos números", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CorDoCluster.entries.forEach { cor ->
                OpcaoColorida(cor.rotulo, ajustes.cor == cor, Color(cor.argb)) { Cluster.usarCor(cor) }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Em que canto do painel", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(4.dp))
        Text(
            "Nove lugares prontos, para não ter de acertar pixel com o dedo nos " +
                "sliders do Impulse.",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LugarNoPainel.entries.forEach { lugar ->
                Opcao(lugar.rotulo, ajustes.lugar == lugar) { Cluster.usarLugar(lugar) }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Quanto espaço ocupa", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TamanhoNoPainel.entries.forEach { tamanho ->
                Opcao(tamanho.rotulo, ajustes.tamanho == tamanho) { Cluster.usarTamanho(tamanho) }
            }
        }

        Spacer(Modifier.height(12.dp))
        BotaoAcao("Ver como fica", onClick = { espiar(ClusterActivity::class.java) })

        Spacer(Modifier.height(18.dp))
        Text("O carro no painel", style = MaterialTheme.typography.titleMedium, color = Cores.Texto)
        Spacer(Modifier.height(4.dp))
        Text(
            "O desenho visto de cima, com portas, cintos e pressão dos pneus, numa " +
                "janela separada — atividade br.com.hugolumazzini.havaltrip." +
                "ClusterCarroActivity, também na tela 3 e também com a tela inteira. " +
                "São duas janelas justamente para cada uma ficar num canto diferente.",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LugarNoPainel.entries.forEach { lugar ->
                Opcao(lugar.rotulo, ajustes.lugarDoCarro == lugar) { Cluster.usarLugarDoCarro(lugar) }
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TamanhoDoCarro.entries.forEach { tamanho ->
                Opcao(tamanho.rotulo, ajustes.tamanhoDoCarro == tamanho) {
                    Cluster.usarTamanhoDoCarro(tamanho)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        BotaoAcao("Ver como fica o carro", onClick = { espiar(ClusterCarroActivity::class.java) })
    }
}

/** Os tamanhos de letra oferecidos, como multiplicador do cálculo automático. */
private val ESCALAS = listOf(
    "Menor" to 0.75f,
    "Normal" to 1.0f,
    "Maior" to 1.3f,
    "Enorme" to 1.6f,
)
