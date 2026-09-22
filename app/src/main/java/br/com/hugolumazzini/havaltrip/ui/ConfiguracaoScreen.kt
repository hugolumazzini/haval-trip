package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.content.Context
import android.content.Intent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.Atualizador
import br.com.hugolumazzini.havaltrip.Cluster
import androidx.compose.runtime.LaunchedEffect
import br.com.hugolumazzini.havaltrip.CorDoCluster
import br.com.hugolumazzini.havaltrip.telemetry.PaletaDoImpulse
import br.com.hugolumazzini.havaltrip.FundoDoCluster
import br.com.hugolumazzini.havaltrip.MAXIMO_DE_ITENS
import br.com.hugolumazzini.havaltrip.RotuloDoCluster
import br.com.hugolumazzini.havaltrip.ClusterActivity
import br.com.hugolumazzini.havaltrip.DESPEDIDA
import br.com.hugolumazzini.havaltrip.ESPIANDO
import br.com.hugolumazzini.havaltrip.ClusterCarroActivity
import br.com.hugolumazzini.havaltrip.ClusterMenuActivity
import br.com.hugolumazzini.havaltrip.AjustesDoCluster
import br.com.hugolumazzini.havaltrip.AlvoNaBola
import br.com.hugolumazzini.havaltrip.FormatoDoCarro
import br.com.hugolumazzini.havaltrip.AFASTAMENTO_MINIMO
import br.com.hugolumazzini.havaltrip.AFASTAMENTO_MAXIMO
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import br.com.hugolumazzini.havaltrip.painel.JanelaDoPainel
import br.com.hugolumazzini.havaltrip.painel.PaginaDoCluster
import br.com.hugolumazzini.havaltrip.painel.TeclaDoVolante
import br.com.hugolumazzini.havaltrip.painel.TecladoDoVolante
import br.com.hugolumazzini.havaltrip.painel.ProjetorDoPainel
import br.com.hugolumazzini.havaltrip.painel.ShizukuShell
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.com.hugolumazzini.havaltrip.Empurrao
import br.com.hugolumazzini.havaltrip.Zoom
import br.com.hugolumazzini.havaltrip.ItemDoCluster
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
 * Os assuntos da configuração, um por aba.
 *
 * A tela era uma rolagem só, com tudo dentro: para trocar a cor dos números era
 * preciso passar por contadores, zeragem e projeção. Dentro do carro isso é
 * pior do que parece — cada rolagem longa é um tempo de olho fora da estrada.
 * Separar por assunto faz cada ajuste caber numa tela sem rolar (ou quase), e o
 * caminho até ele vira um toque só.
 */
private enum class AbaDaConfiguracao(val rotulo: String) {
    // A ordem é a do uso, não a da implementação: o que o motorista abre todo
    // dia (zerar contador) vem primeiro, depois o que ele abre de vez em quando
    // (versão), e por último o que se acerta uma vez e não se mexe mais.
    CONTADORES("Contadores"),
    VERSAO("Versão"),
    NUMEROS("Números"),
    CARRO("Carro"),
    DESPEDIDA("Despedida"),
    PAGINA("Integrar ao painel"),
}

/**
 * Ajustes do computador de bordo, divididos por assunto.
 *
 * Nada aqui apaga número nenhum — esconder um contador o congela com o que ele
 * já mediu, e voltar a mostrá-lo o traz inteiro de volta.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ConfiguracaoScreen(vm: TripViewModel, estado: TripState) {
    // `rememberSaveable` para a aba sobreviver ao giro de tela e à volta de
    // outra tela: reabrir sempre em "Números" faria perder o lugar a cada
    // espiada em "Ver como fica".
    var aba by rememberSaveable { mutableStateOf(AbaDaConfiguracao.CONTADORES) }

    Column(Modifier.fillMaxWidth()) {
        Text("CONFIGURAÇÃO", style = EstiloRotulo)
        Spacer(Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AbaDaConfiguracao.entries.forEach { qual ->
                Opcao(qual.rotulo, aba == qual) { aba = qual }
            }
        }

        Spacer(Modifier.height(14.dp))

        // A rolagem vive aqui dentro, e não em volta das abas: as abas ficam
        // paradas no topo enquanto o conteúdo rola, que é o que faz a troca de
        // assunto continuar a um toque de distância.
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Cartao(Modifier.fillMaxWidth()) {
                when (aba) {
                    AbaDaConfiguracao.NUMEROS -> NumerosNoPainel(estado)
                    AbaDaConfiguracao.CARRO -> CarroNoPainel()
                    AbaDaConfiguracao.PAGINA -> PaginaComVisoesNaAba()
                    AbaDaConfiguracao.DESPEDIDA -> DespedidaNoPainel()
                    AbaDaConfiguracao.CONTADORES -> Contadores(vm, estado)
                    AbaDaConfiguracao.VERSAO -> SobreEAtualizacao(vm)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

/** Contadores manuais e zeragem automática: as duas decisões do histórico. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun Contadores(vm: TripViewModel, estado: TripState) {
    Column {
        Text("Contadores manuais", style = MaterialTheme.typography.titleLarge, color = Cores.Texto)
        Spacer(Modifier.height(4.dp))
        Text(
            "Quantos contadores aparecem na lateral, fora a Viagem atual. " +
                "Os que saem da lista param de contar, mas guardam o que já mediram.",
            style = MaterialTheme.typography.bodyMedium,
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

        Spacer(Modifier.height(22.dp))
        ZeragemAutomatica(vm, estado)
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
        Text("Versão", style = MaterialTheme.typography.titleLarge, color = Cores.Texto)
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
            style = MaterialTheme.typography.bodyMedium,
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
            style = MaterialTheme.typography.bodyMedium,
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
            Text("na hora", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoApoio)
            Text("3 h", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoApoio)
        }
    }
}

/** Botão de escolha única, no tamanho de dedo que a central pede. */
@Composable
private fun Opcao(
    texto: String,
    marcada: Boolean,
    // Apagada quando o toque não faria nada — hoje só a lista de dados, que tem
    // teto. Uma pastilha que aceita o dedo e não muda nada é lida como defeito;
    // apagada, ela explica sozinha que o lugar acabou.
    habilitada: Boolean = true,
    onClick: () -> Unit,
) {
    OpcaoColorida(texto, marcada, Cores.Destaque, habilitada, onClick)
}

/**
 * A mesma pastilha, com a cor do texto marcado escolhida por quem chama.
 *
 * Existe para a escolha de cor do painel poder se mostrar na própria cor: uma
 * lista de nomes ("Âmbar", "Verde") todos escritos em azul obrigaria o
 * motorista a tocar para descobrir o que cada um faz.
 */
@Composable
private fun OpcaoColorida(
    texto: String,
    marcada: Boolean,
    corMarcada: Color,
    habilitada: Boolean = true,
    onClick: () -> Unit,
) {
    // Folga generosa de propósito: a pastilha é o alvo do dedo em movimento,
    // dentro de um carro, e o toque que erra custa uma segunda olhada para a
    // tela. Vale gastar espaço aqui — a tela é larga e sobra.
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (marcada) Cores.SuperficieSelecionada else Cores.Campo)
            .clickable(enabled = habilitada, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                marcada -> corMarcada
                habilitada -> Cores.TextoCorrido
                else -> Cores.TextoCorrido.copy(alpha = 0.35f)
            },
        )
    }
}

/**
 * Abre a janela do painel aqui na central, para conferir o ajuste sem depender
 * do Impulse nem do carro ligado. É a mesma tela, com o mesmo tamanho
 * (1920 x 720): o que aparecer aqui é o que vai aparecer lá. Para sair, Voltar.
 */
private fun espiar(contexto: Context, atividade: Class<*>) {
    contexto.startActivity(Intent(contexto, atividade).putExtra(ESPIANDO, true))
}

/**
 * Os números da viagem no painel de instrumentos.
 *
 * Quem coloca a janela lá é o Impulse, na tela "Telas" dele — ele é que decide
 * onde e de que tamanho. Aqui se decide só o conteúdo: qual contador, quais
 * números, que tamanho de letra e que cor. A separação não é escolha minha, é
 * como o mecanismo funciona, e por isso está escrita na tela: sem essa frase, o
 * motorista configuraria tudo aqui e ficaria esperando algo aparecer sozinho.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NumerosNoPainel(estado: TripState) {
    val ajustes by Cluster.ajustes.collectAsStateWithLifecycle()
    val paleta by Cluster.paleta.collectAsStateWithLifecycle()
    // Sem isto, quem troca a paleta no volante e volta aqui continuaria vendo
    // a resposta antiga: a leitura so acontece quando alguem a pede.
    LaunchedEffect(Unit) { Cluster.atualizarPaleta() }
    val contexto = LocalContext.current

    Column {
        Text("Números no painel", style = MaterialTheme.typography.titleLarge, color = Cores.Texto)
        Spacer(Modifier.height(4.dp))
        Text(
            "O resumo que aparece no painel atrás do volante. Escolha a tela aqui " +
                "embaixo e o app se coloca lá sozinho, sem passar pelo Impulse; a " +
                "posição dentro da tela se escolhe mais abaixo, e o que sobra fica " +
                "transparente.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoApoio,
        )

        Spacer(Modifier.height(14.dp))
        Projecao(JanelaDoPainel.NUMEROS, ajustes.telaDosNumeros)

        Spacer(Modifier.height(14.dp))
        Text("Qual contador", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
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
        Text("Quais informações", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(4.dp))
        Text(
            "Aparecem na ordem em que você marcar, até $MAXIMO_DE_ITENS. Três cabem " +
                "bem numa faixa; acima disso, dê mais espaço à janela no Impulse.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ItemDoCluster.entries.forEach { item ->
                Opcao(
                    item.descricao,
                    item in ajustes.itens,
                    habilitada = item in ajustes.itens ||
                        ajustes.itens.size < MAXIMO_DE_ITENS,
                ) { Cluster.alternarItem(item) }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Tamanho da letra", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(4.dp))
        Text(
            "Normal já se ajusta sozinho ao tamanho da janela. As outras opções " +
                "só puxam esse cálculo para cima ou para baixo.",
            style = MaterialTheme.typography.bodyMedium,
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
        Text("Cor dos números", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CorDoCluster.entries.filter { it != CorDoCluster.DO_IMPULSE }.forEach { cor ->
                OpcaoColorida(cor.rotulo, ajustes.cor == cor, Color(corDaAmostra(cor, paleta))) {
                    Cluster.usarCor(cor)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Fundo do bloco", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(4.dp))
        Text(
            "Transparente deixa o painel do carro aparecer por baixo. Escolha um dos " +
                "opacos quando o bloco cair em cima de algo que o painel já desenha ali — " +
                "número sobre número não se lê.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FundoDoCluster.entries.forEach { fundo ->
                Opcao(fundo.rotulo, ajustes.fundo == fundo) { Cluster.usarFundo(fundo) }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Quanto espaço ocupa", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TamanhoNoPainel.Escolhiveis.forEach { tamanho ->
                Opcao(tamanho.rotulo, ajustes.tamanho == tamanho) { Cluster.usarTamanho(tamanho) }
            }
        }

        Spacer(Modifier.height(14.dp))
        AjusteFino(JanelaDoPainel.NUMEROS, ajustes.empurraoDosNumeros, ajustes.zoomDosNumeros)

        Spacer(Modifier.height(12.dp))
        BotaoAcao("Ver como fica", onClick = { espiar(contexto, ClusterActivity::class.java) })
    }
}

/**
 * O resumo que aparece ao desligar o carro.
 *
 * Aba própria porque não é ajuste de nenhuma das três janelas: qual delas mostra
 * o resumo é decisão do app ([Despedida.janelaDoResumo]), e o que o motorista
 * escolhe aqui — quais números aparecem — vale para a que for.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DespedidaNoPainel() {
    val ajustes by Cluster.ajustes.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    Column {
        Text("Despedida", style = MaterialTheme.typography.titleLarge, color = Cores.Texto)
        Spacer(Modifier.height(4.dp))
        Text(
            "Quando o carro é desligado, uma das janelas troca sozinha para um resumo " +
                "da viagem que acabou, ao lado do desenho do carro. Ele fica " +
                "congelado no painel, que é o que o painel faz com a última imagem.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "O que o resumo mostra",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoCorrido,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Lista própria, separada da aba dos números: dirigindo interessa o que está " +
                "acontecendo agora, e ao desligar interessa como foi a viagem toda. " +
                "Até quatro ficam lado a lado; daí em diante o resumo usa duas filas.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ItemDoCluster.entries.forEach { item ->
                Opcao(item.descricao, item in ajustes.itensDaDespedida) {
                    Cluster.alternarItemDaDespedida(item)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        BotaoAcao("Ver a despedida", onClick = {
            contexto.startActivity(
                Intent(contexto, ClusterActivity::class.java)
                    .putExtra(ESPIANDO, true)
                    .putExtra(DESPEDIDA, true),
            )
        })
    }
}

/**
 * O desenho do carro visto de cima, na sua própria janela.
 *
 * Janela separada da dos números justamente para cada uma poder ficar num lugar
 * diferente — e até em telas diferentes: os números no painel e o carro na bola
 * do ar, ao mesmo tempo.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CarroNoPainel() {
    val ajustes by Cluster.ajustes.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    Column {
        Text("Carro no painel", style = MaterialTheme.typography.titleLarge, color = Cores.Texto)
        Spacer(Modifier.height(4.dp))
        Text(
            "O desenho visto de cima, com portas, cintos e pressão dos pneus. " +
                "Para ocupar a bola redonda do painel, use a aba \"Página com " +
                "visões\": é a janela que troca de visão pela cruzinha do volante. " +
                "Aqui o carro fica solto, fixo por cima de qualquer página.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoApoio,
        )

        Spacer(Modifier.height(14.dp))
        Projecao(JanelaDoPainel.CARRO, ajustes.telaDoCarro)

        Spacer(Modifier.height(14.dp))
        Text("Tamanho do carro", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TamanhoDoCarro.Escolhiveis.forEach { tamanho ->
                Opcao(tamanho.rotulo, ajustes.tamanhoDoCarro == tamanho) {
                    Cluster.usarTamanhoDoCarro(tamanho)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Fundo do carro", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(4.dp))
        Text(
            "Na bola do ar o fundo opaco é redondo, do tamanho da bola: é ele que " +
                "tapa a tela do ar-condicionado por baixo. Transparente deixa os dois " +
                "desenhos aparecerem um sobre o outro.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FundoDoCluster.entries.forEach { fundo ->
                Opcao(fundo.rotulo, ajustes.fundoDoCarro == fundo) { Cluster.usarFundoDoCarro(fundo) }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Formato do fundo", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FormatoDoCarro.entries.forEach { formato ->
                Opcao(formato.rotulo, ajustes.formatoDoCarro == formato) {
                    Cluster.usarFormatoDoCarro(formato)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Tamanho do conteúdo dentro da bola", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(4.dp))
        Text(
            "Aproxima ou espalha o conteúdo dentro da bola, sem mexer no tamanho dele.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(8.dp))
        FlowRowSimples {
            Seta("−−", "bem mais juntos") { Cluster.afastarNaBola(-Zoom.SALTO) }
            Seta("−", "um pouco mais juntos") { Cluster.afastarNaBola(-Zoom.PASSO) }
            Seta("+", "um pouco mais separados") { Cluster.afastarNaBola(Zoom.PASSO) }
            Seta("++", "bem mais separados") { Cluster.afastarNaBola(Zoom.SALTO) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${ajustes.afastamentoNaBola.porcento.coerceAtMost(100)}% da altura.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoApoio,
        )

        Spacer(Modifier.height(14.dp))
        Text("Distância dos pneus e temperatura", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(4.dp))
        Text(
            "Aproxima ou afasta os números de pressão e temperatura do carro.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(8.dp))
        FlowRowSimples {
            Seta("−", "mais perto") { Cluster.afastarDoCarro(-5) }
            Seta("+", "mais longe") { Cluster.afastarDoCarro(5) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${ajustes.afastamentoDoCarro}% da proximidade máxima.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoApoio,
        )

        Spacer(Modifier.height(14.dp))
        AjusteFino(JanelaDoPainel.CARRO, ajustes.empurraoDoCarro, ajustes.zoomDoCarro)

        Spacer(Modifier.height(12.dp))
        BotaoAcao("Ver como fica o carro", onClick = { espiar(contexto, ClusterCarroActivity::class.java) })
    }
}

/** A aba da terceira janela. Ver [PaginaComVisoes]. */
@Composable
private fun PaginaComVisoesNaAba() {
    val ajustes by Cluster.ajustes.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    Column { PaginaComVisoes(ajustes) { espiar(contexto, it) } }
}

/**
 * A terceira janela: uma página do painel inteira, com visões que se trocam no
 * volante.
 *
 * ## Solto e página, e por que os dois existem
 *
 * "Solto" é o que as abas "Números" e "Carro" fazem: janelinhas fixas no painel,
 * por cima de qualquer página. Serve para quem quer o número sempre à vista.
 *
 * "Página" é outra ideia: uma coisa de cada vez, do tamanho do painel, e nada
 * quando o motorista está noutra página. Serve para quem tem uma bola sobrando
 * — a que o Impulse deixou vazia — e quer usá-la como o carro usa as dele.
 *
 * As duas podem estar ligadas ao mesmo tempo. Não se atrapalham: são janelas
 * diferentes, com telas e páginas próprias.
 */
@Composable
private fun PaginaComVisoes(ajustes: AjustesDoCluster, espiar: (Class<*>) -> Unit) {
    val agora by PaginaDoCluster.pagina.collectAsStateWithLifecycle()
    val ouvindoPagina by PaginaDoCluster.ligado.collectAsStateWithLifecycle()
    val ouvindoTeclas by TecladoDoVolante.ligado.collectAsStateWithLifecycle()

    Text("Integrar ao painel", style = MaterialTheme.typography.titleLarge, color = Cores.Texto)
    Spacer(Modifier.height(4.dp))
    Text(
        "O computador de bordo ocupando a bola vazia do painel, como se fosse uma " +
            "página do próprio carro. As páginas se trocam com as setas para os " +
            "lados, no volante; dentro desta, para cima e para baixo trocam a " +
            "visão: primeiro o carro, depois um contador para cada Trip.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )

    Spacer(Modifier.height(14.dp))
    BolaDeUmaVez(agora, ouvindoPagina)

    Spacer(Modifier.height(14.dp))
    Projecao(JanelaDoPainel.MENU, ajustes.telaDoMenu)

    Spacer(Modifier.height(14.dp))
    Text("Página do painel", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
    Spacer(Modifier.height(4.dp))
    Text(
        when {
            !ouvindoPagina ->
                "Esta central não avisa quando a página do painel muda, então esta " +
                    "janela ficaria por cima de todas elas. Nesse caso, prefira o " +
                    "carro solto."
            agora == null ->
                "Gire o carrossel do painel uma vez, no volante: o número da página " +
                    "aparece aqui."
            else ->
                "O painel está na página $agora. Pare na bola vazia e toque em " +
                    "\"só nesta página\"."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(8.dp))
    FlowRowSimples {
        Opcao("Em todas as páginas", ajustes.paginaDoMenu == null) { Cluster.usarPaginaDoMenu(null) }
        val alvo = agora
        if (ouvindoPagina && alvo != null) {
            Opcao("Só na página $alvo", ajustes.paginaDoMenu == alvo) {
                Cluster.usarPaginaDoMenu(alvo)
            }
        }
        val presa = ajustes.paginaDoMenu
        if (presa != null && presa != agora) {
            Opcao("Só na página $presa", true) { Cluster.usarPaginaDoMenu(presa) }
        }
    }
    AvisoDaPagina(ajustes.paginaDoMenu)

    Spacer(Modifier.height(14.dp))
    Text("Fundo", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
    Spacer(Modifier.height(4.dp))
    Text(
        "O que fica atrás da bola. Preto apaga o que o painel desenha ali; " +
            "transparente deixa o desenho do painel aparecer por baixo dos números.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(8.dp))
    FlowRowSimples {
        FundoDoCluster.entries.forEach { fundo ->
            Opcao(fundo.rotulo, ajustes.fundoDoMenu == fundo) { Cluster.usarFundoDoMenu(fundo) }
        }
    }

    // Lista própria, e não a da aba "Números": são duas janelas com espaços
    // diferentes, e quem escolhe autonomia e velocidade máxima nos números não
    // está escolhendo isso aqui. Quem já usava a lista única começa com ela
    // repetida nas duas, e daí cada uma segue seu caminho.
    Spacer(Modifier.height(14.dp))
    Text(
        "Quais informações",
        style = MaterialTheme.typography.titleMedium,
        color = Cores.TextoCorrido,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Os dados de cada contador nesta página, na ordem em que você marcar, até " +
            "$MAXIMO_DE_ITENS. É uma escolha só desta página: a aba \"Números\" tem a " +
            "dela. Na bola, até três ficam numa coluna; do quarto em diante eles se " +
            "dividem em duas.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(8.dp))
    FlowRowSimples {
        ItemDoCluster.entries.forEach { item ->
            Opcao(
                item.descricao,
                item in ajustes.itensDoMenu,
                habilitada = item in ajustes.itensDoMenu ||
                    ajustes.itensDoMenu.size < MAXIMO_DE_ITENS,
            ) { Cluster.alternarItemDoMenu(item) }
        }
    }

    Spacer(Modifier.height(14.dp))
    Text(
        "Como cada dado se identifica",
        style = MaterialTheme.typography.titleMedium,
        color = Cores.TextoCorrido,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "A palavra em cima do número custa uma linha de altura, e a unidade " +
            "já diz quase a mesma coisa. Troque entre os três e veja no painel " +
            "qual se lê de relance — é a única comparação que vale.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(8.dp))
    FlowRowSimples {
        RotuloDoCluster.entries.forEach { r ->
            Opcao(r.rotulo, ajustes.rotuloDoMenu == r) { Cluster.usarRotuloDoMenu(r) }
        }
    }

    // Calibração, não ajuste de motorista. Não há mais escolha de formato: esta
    // página é sempre a bola que o Impulse deixa vazia, e oferecer "painel
    // inteiro" era oferecer um modo que o app não usa. O que fica aqui é só a
    // medida, enquanto ela não estiver acertada de vez: quem mede é quem está
    // sentado no carro, e a régua da janela é o único jeito de saber onde a bola
    // caiu. Quando o número estiver fechado, isto sai e vira constante, como
    // [BolaDoPainel] já é.
    Spacer(Modifier.height(18.dp))
    Text("Calibração", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
    Spacer(Modifier.height(4.dp))
    Text(
        "Temporário: serve para acertar onde a bola cai neste painel e me contar o " +
            "resultado. Vai virar padrão do app.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(14.dp))
    AjusteFinoDaBola(ajustes)

    Spacer(Modifier.height(10.dp))
    Text(
        if (ouvindoTeclas) {
            "A cruzinha do volante está sendo lida: para cima e para baixo trocam a " +
                "visão enquanto esta página estiver na frente."
        } else {
            "A cruzinha do volante não está sendo lida nesta central. A página ainda " +
                "funciona, mas fica travada na primeira visão."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (ouvindoTeclas) Cores.TextoApoio else Cores.Atencao,
    )

    // O contador das teclas cruas. Sem ele, "apertei e não aconteceu nada" pode
    // ser o registro que falhou, a central que não avisa ninguém, ou um código
    // diferente do que esperamos — três consertos diferentes, e nenhum jeito de
    // saber qual. Com ele, o volante vira um teste que se faz sentado no carro.
    if (ouvindoTeclas) {
        val quantas by TecladoDoVolante.quantasChegaram.collectAsStateWithLifecycle()
        val codigo by TecladoDoVolante.ultimoCodigo.collectAsStateWithLifecycle()
        val acao by TecladoDoVolante.ultimaAcao.collectAsStateWithLifecycle()
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                quantas == 0 ->
                    "Nenhuma tecla chegou ainda. Aperte a cruzinha do volante: se este " +
                        "número não mexer, a central não nos manda as teclas."
                else ->
                    "Teclas recebidas: $quantas. A última veio com o código $codigo" +
                        acao?.let { " (${if (it == 0) "descendo" else "soltando"})" }.orEmpty() +
                        if (TeclaDoVolante.de(codigo ?: -1) == null) {
                            " — que este app ainda não conhece. Me mande esse número."
                        } else {
                            "."
                        }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoApoio,
        )
    }

    Spacer(Modifier.height(12.dp))
    BotaoAcao("Ver como fica a página", onClick = { espiar(ClusterMenuActivity::class.java) })
}

/**
 * Em qual página do carrossel do painel o carro aparece.
 *
 * ## Por que isto é preciso
 *
 * O painel do H6 tem um carrossel de páginas — a padrão do carro, a de mídia, a
 * do telefone —, e a nossa janela é projetada por cima de todas: ela não sabe
 * que a página mudou e continua lá. Quem quer ocupar a bola que o Impulse deixa
 * vazia precisa exatamente do contrário — aparecer numa página só, e sumir nas
 * outras, como o ar-condicionado dele some.
 *
 * ## Por que o número é lido aqui, e não escolhido numa lista
 *
 * Porque nós não sabemos qual número é qual bola. A central manda um inteiro
 * quando a página troca, e o único ponto firme é que a página padrão do carro é
 * a 0. Então em vez de adivinhar nomes, esta tela mostra o número que está
 * chegando **agora**: o motorista gira o carrossel até a bola que quer, olha o
 * número aqui e toca em "prender". É a régua de novo — quem tem a informação
 * está sentado no carro.
 */
@Composable
private fun PaginaDoPainel(escolhida: Int?) {
    val agora by PaginaDoCluster.pagina.collectAsStateWithLifecycle()
    val ouvindo by PaginaDoCluster.ligado.collectAsStateWithLifecycle()

    Text("Página do painel", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
    Spacer(Modifier.height(4.dp))
    Text(
        when {
            !ouvindo ->
                "Esta central não está avisando quando a página do painel muda, " +
                    "então o carro aparece em todas elas. Não há o que ajustar aqui."
            agora == null ->
                "Gire o carrossel do painel uma vez, no volante: assim que a página " +
                    "mudar, o número dela aparece aqui."
            else ->
                "O painel está na página $agora. Gire o carrossel até a bola em que " +
                    "você quer o carro e toque em \"só nesta página\"."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(8.dp))
    FlowRowSimples {
        Opcao("Em todas as páginas", escolhida == null) { Cluster.usarPaginaDoCarro(null) }
        val alvo = agora
        if (ouvindo && alvo != null) {
            Opcao("Só na página $alvo", escolhida == alvo) { Cluster.usarPaginaDoCarro(alvo) }
        }
        // A escolha já gravada continua visível mesmo quando o painel está
        // noutra página; sem isto, quem prendeu o carro na página 2 e girou o
        // carrossel veria "Em todas" como única opção e acharia que perdeu o
        // ajuste.
        if (escolhida != null && escolhida != agora) {
            Opcao("Só na página $escolhida", true) { Cluster.usarPaginaDoCarro(escolhida) }
        }
    }
    AvisoDaPagina(escolhida)
}

/**
 * O aviso de que a página escolhida já tem dono.
 *
 * Aviso, e não impedimento: quem não usa o Impulse pode querer a página dele, e
 * a numeração pode não ser a mesma em toda central. Ver
 * [PaginaDoCluster.aviso].
 */
@Composable
private fun AvisoDaPagina(pagina: Int?) {
    val aviso = PaginaDoCluster.aviso(pagina) ?: return
    Spacer(Modifier.height(6.dp))
    Text(aviso, style = MaterialTheme.typography.bodyMedium, color = Cores.Atencao)
}

/** Uma fila de chips que quebra a linha. Só para não repetir os dois espaçamentos. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FlowRowSimples(conteudo: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = conteudo,
    )
}

/**
 * Um teclado de setas que empurra alguma coisa, com o estado escrito embaixo.
 *
 * Separado do [AjusteFino] porque agora há mais de uma coisa para empurrar na
 * mesma tela: a bola no painel e o conteúdo dentro dela. Duas cópias do mesmo
 * teclado envelheceriam diferente.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SetasDePosicao(
    titulo: String,
    texto: String,
    empurrao: Empurrao,
    empurrar: (Int, Int) -> Unit,
    desfazer: () -> Unit,
) {
    Text(titulo, style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
    Spacer(Modifier.height(4.dp))
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = Cores.TextoApoio)
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Seta("▲▲", "sobe muito") { empurrar(0, -Empurrao.SALTO) }
        Seta("▲", "sobe pouco") { empurrar(0, -Empurrao.PASSO) }
        Seta("▼", "desce pouco") { empurrar(0, Empurrao.PASSO) }
        Seta("▼▼", "desce muito") { empurrar(0, Empurrao.SALTO) }
        Seta("◀◀", "esquerda muito") { empurrar(-Empurrao.SALTO, 0) }
        Seta("◀", "esquerda pouco") { empurrar(-Empurrao.PASSO, 0) }
        Seta("▶", "direita pouco") { empurrar(Empurrao.PASSO, 0) }
        Seta("▶▶", "direita muito") { empurrar(Empurrao.SALTO, 0) }
        BotaoAcao("Desfazer", onClick = desfazer, habilitado = !empurrao.centrado)
    }
    Spacer(Modifier.height(6.dp))
    // O número aparece sempre, inclusive zerado: sem ele não há como saber se o
    // toque pegou, já que quem confere está olhando para a outra tela.
    Text(
        if (empurrao.centrado) "No lugar, sem empurrão."
        else "Empurrado ${rumo(empurrao.x, "para a direita", "para a esquerda")} e " +
            "${rumo(empurrao.y, "para baixo", "para cima")}.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
}

/**
 * A posição da janela, em setas — e agora o único jeito de movê-la.
 *
 * Antes dividia o trabalho com nove cantos prontos, e os dois se atrapalhavam: o
 * mesmo empurrão dava num lugar diferente conforme o canto, então mover o bloco
 * exigia entender a combinação. Os cantos saíram; ficou uma âncora só, o centro,
 * e estas setas, que agora alcançam o painel inteiro ([Empurrao.LIMITE]).
 *
 * O que os cantos nunca deram e isto dá: o pixel. A faixa da navegação foi
 * medida numa foto do painel, e no carro o bloco caiu sobre a estrada desenhada
 * em vez de na tarja vazia acima dela. Sem isto, cada tentativa de acerto seria
 * um número trocado no código e um APK novo — e quem enxerga o resultado está
 * sentado no carro, não na frente do editor.
 *
 * Setas e não slider: o erro a corrigir é de alguns pixels, e o alvo de um
 * slider com o dedo, num carro, é muito maior do que isso. A seta dupla anda
 * [Empurrao.SALTO] para atravessar a tela sem quarenta toques; a simples anda
 * [Empurrao.PASSO], que é o pixel fino.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AjusteFino(
    janela: JanelaDoPainel,
    empurrao: Empurrao,
    zoom: Zoom,
) {
    SetasDePosicao(
        titulo = "Ajuste fino da posição",
        texto = "As setas movem a janela pelo painel a partir do centro, e alcançam " +
            "qualquer lugar dele: a dupla atravessa, a simples acerta o fio. " +
            "\"Desfazer\" traz de volta ao centro. Use \"Ver como fica\" para conferir.",
        empurrao = empurrao,
        empurrar = { dx, dy -> Cluster.empurrar(janela, dx, dy) },
        desfazer = { Cluster.centralizar(janela) },
    )

    Spacer(Modifier.height(14.dp))
    SetasDeTamanho(
        titulo = "Ajuste fino do tamanho",
        texto = "Estica ou encolhe a partir do tamanho escolhido acima, em proporção — " +
            "os dois lados crescem juntos, então o bloco não deforma. Os tamanhos " +
            "prontos são degraus largos; isto é o que fica entre um e outro.",
        zoom = zoom,
        ampliar = { delta -> Cluster.ampliar(janela, delta) },
        desfazer = { Cluster.tamanhoNatural(janela) },
    )

    Spacer(Modifier.height(14.dp))
    Text("Medidas desta janela", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
    Spacer(Modifier.height(4.dp))
    Text(
        "O que a janela mediu de si mesma. Com ela projetada no painel, muda a " +
            "cada toque nas setas; com ela fechada, é a última leitura, e não " +
            "acompanha os ajustes. Depois de acertar a posição, me mande estas " +
            "linhas: com elas eu corrijo as posições prontas no código, e quem " +
            "vier depois não precisa ajustar nada.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(8.dp))
    QuadroDeMedidas(janela)
}

/**
 * As setas de esticar, com o tamanho atual escrito embaixo.
 *
 * Separado pelo mesmo motivo do [SetasDePosicao]: agora há mais de uma coisa
 * para esticar na mesma tela — o carro na bola e os dados na bola —, e duas
 * cópias do mesmo teclado envelheceriam diferente.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SetasDeTamanho(
    titulo: String,
    texto: String,
    zoom: Zoom,
    ampliar: (Int) -> Unit,
    desfazer: () -> Unit,
) {
    Text(titulo, style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
    Spacer(Modifier.height(4.dp))
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = Cores.TextoApoio)
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Seta("−−", "bem menor") { ampliar(-Zoom.SALTO) }
        Seta("−", "um pouco menor") { ampliar(-Zoom.PASSO) }
        Seta("+", "um pouco maior") { ampliar(Zoom.PASSO) }
        Seta("++", "bem maior") { ampliar(Zoom.SALTO) }
        BotaoAcao("Desfazer", onClick = desfazer, habilitado = !zoom.natural)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        if (zoom.natural) "No tamanho escolhido." else "${zoom.porcento}% do tamanho escolhido.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
}

/**
 * O ajuste fino da bola do painel: a bola, o carro dentro dela e os dados.
 *
 * Três acertos, e não um. A bola é medida do carro — ela tem de encaixar no
 * círculo que o painel desenha embaixo. Dentro dela moram dois desenhos de
 * formatos opostos, o carrinho largo e a coluna de números alta, e o tamanho
 * que deixa um no ponto deixa o outro sobrando: por isso cada um tem a sua
 * posição e o seu tamanho. Ver [AlvoNaBola].
 */
@Composable
private fun AjusteFinoDaBola(ajustes: AjustesDoCluster) {
    SetasDePosicao(
        titulo = "Ajuste fino da posição",
        texto = "Move a bola inteira pelo painel, para encaixá-la na que o carro " +
            "desenha embaixo. A dupla atravessa, a simples acerta o fio. Isto vale " +
            "para as duas visões: a bola é uma só.",
        empurrao = ajustes.empurraoDoMenu,
        empurrar = { dx, dy -> Cluster.empurrar(JanelaDoPainel.MENU, dx, dy) },
        desfazer = { Cluster.centralizar(JanelaDoPainel.MENU) },
    )

    Spacer(Modifier.height(18.dp))
    Text("O carro na bola", style = MaterialTheme.typography.titleLarge, color = Cores.Texto)
    Spacer(Modifier.height(4.dp))
    Text(
        "Vale só para a visão do carro. Gire a cruzinha do volante até ela aparecer " +
            "no painel antes de mexer aqui.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(10.dp))
    SetasDePosicao(
        titulo = "Posição do carro",
        texto = "Move o desenho dentro da bola, sem tirar a bola do lugar.",
        empurrao = ajustes.empurraoDoCarroNaBola,
        empurrar = { dx, dy -> Cluster.empurrarNaBola(AlvoNaBola.CARRO, dx, dy) },
        desfazer = { Cluster.centralizarNaBola(AlvoNaBola.CARRO) },
    )
    Spacer(Modifier.height(14.dp))
    SetasDeTamanho(
        titulo = "Tamanho do carro",
        texto = "Estica ou encolhe o desenho. A bola em si não muda: ela é a do " +
            "painel, e tem de continuar encaixada na que o carro desenha embaixo.",
        zoom = ajustes.zoomDoCarroNaBola,
        ampliar = { delta -> Cluster.ampliarNaBola(AlvoNaBola.CARRO, delta) },
        desfazer = { Cluster.tamanhoNaturalNaBola(AlvoNaBola.CARRO) },
    )

    Spacer(Modifier.height(18.dp))
    Text("Os dados na bola", style = MaterialTheme.typography.titleLarge, color = Cores.Texto)
    Spacer(Modifier.height(4.dp))
    Text(
        "Vale para as visões dos contadores. Pare o volante numa delas antes de " +
            "mexer aqui.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(10.dp))
    SetasDePosicao(
        titulo = "Posição dos dados",
        texto = "Move os números, o título e as bolinhas dentro da bola, sem tirar a " +
            "bola do lugar.",
        empurrao = ajustes.empurraoDentroDoMenu,
        empurrar = { dx, dy -> Cluster.empurrarNaBola(AlvoNaBola.DADOS, dx, dy) },
        desfazer = { Cluster.centralizarNaBola(AlvoNaBola.DADOS) },
    )
    Spacer(Modifier.height(14.dp))
    SetasDeTamanho(
        titulo = "Tamanho dos dados",
        texto = "Estica ou encolhe os números dentro da bola. A bola em si não muda.",
        zoom = ajustes.zoomDoMenu,
        ampliar = { delta -> Cluster.ampliarNaBola(AlvoNaBola.DADOS, delta) },
        desfazer = { Cluster.tamanhoNaturalNaBola(AlvoNaBola.DADOS) },
    )

    Spacer(Modifier.height(14.dp))
    Text(
        "Afastamento entre os dados",
        style = MaterialTheme.typography.titleMedium,
        color = Cores.TextoCorrido,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Aproxima ou espalha os dados na altura da bola, sem mexer no tamanho " +
            "deles — é o botão para quando está tudo grudado ou esparramado demais. " +
            "Espalhar mais que a bola inteira não dá: o que passasse disso seria cortado.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(8.dp))
    FlowRowSimples {
        Seta("−−", "bem mais juntos") { Cluster.afastarNoMenu(-Zoom.SALTO) }
        Seta("−", "um pouco mais juntos") { Cluster.afastarNoMenu(-Zoom.PASSO) }
        Seta("+", "um pouco mais separados") { Cluster.afastarNoMenu(Zoom.PASSO) }
        Seta("++", "bem mais separados") { Cluster.afastarNoMenu(Zoom.SALTO) }
        BotaoAcao(
            "Desfazer",
            onClick = { Cluster.afastamentoNatural() },
            habilitado = !ajustes.afastamentoDoMenu.natural,
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        if (ajustes.afastamentoDoMenu.natural) {
            "Espalhados pela altura toda."
        } else {
            "${ajustes.afastamentoDoMenu.porcento.coerceAtMost(100)}% da altura."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )

    Spacer(Modifier.height(14.dp))
    Text("Medidas desta janela", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
    Spacer(Modifier.height(4.dp))
    Text(
        "O que a janela mediu de si mesma. Com ela projetada no painel, muda a " +
            "cada toque nas setas. Depois de acertar, me mande estas linhas.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(8.dp))
    QuadroDeMedidas(JanelaDoPainel.MENU)
}

/**
 * "12 dp para a direita" em vez de "x = 12".
 *
 * O sinal do eixo não quer dizer nada para quem está ajustando: negativo é
 * esquerda numa linha e cima na outra, e obrigar a lembrar disso é transformar
 * uma conferência de relance em tradução mental.
 */
private fun rumo(valor: Int, positivo: String, negativo: String): String =
    if (valor == 0) "nada" else "${kotlin.math.abs(valor)} dp ${if (valor > 0) positivo else negativo}"

/** Um passo do ajuste fino. Alvo grande porque é tocado com o carro parado, mas às pressas. */
@Composable
private fun Seta(simbolo: String, descricao: String, onClick: () -> Unit) {
    BotaoAcao(simbolo, onClick = onClick, modifier = Modifier.semantics { contentDescription = descricao })
}

/**
 * "Usar esta bola", num botão só: grava tela, página e encaixe e já projeta.
 *
 * O atalho para o caminho inteiro que as seções abaixo fazem peça por peça.
 * Elas continuam aí para quem quiser corrigir uma coisa isolada — mas não são
 * mais o único jeito, que era o problema. Ver [Cluster.usarBolaDoPainel].
 *
 * A tela é escolhida sozinha: a maior que não seja a central. O painel é a
 * única outra tela grande do H6, e pedir esse número a quem só quer a bola
 * funcionando é devolver o ajuste em três passos pela porta dos fundos.
 */
@Composable
private fun BolaDeUmaVez(pagina: Int?, ouvindoPagina: Boolean) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val telas = remember { ProjetorDoPainel.telas(contexto) }
    val painel = telas.filterNot { it.central }.maxByOrNull { it.largura * it.altura }

    val impedimento = when {
        !ouvindoPagina -> "Esta central não avisa em que página o painel está, " +
            "então não dá para prender a janela a uma bola. Use as opções abaixo."
        pagina == null -> "Gire o carrossel do painel uma vez, no volante, para " +
            "o app saber em que bola você está."
        painel == null -> "Nenhuma tela além da central está aparecendo. No carro " +
            "isso costuma ser painel apagado — tente com o carro ligado."
        else -> null
    }

    Text(
        "O caminho curto",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoCorrido,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        impedimento ?: "Pare o carrossel na bola que você quer — o painel está na " +
            "página $pagina — e toque no botão. A janela vai para a ${painel?.descricao?.lowercase()}, " +
            "encaixada no recorte redondo, e só aparece nessa bola. As setas do " +
            "volante passam a trocar as visões enquanto ela estiver na frente.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(8.dp))
    BotaoAcao(
        texto = if (pagina == null) "Usar esta bola" else "Usar a bola da página $pagina",
        habilitado = impedimento == null,
        onClick = {
            val alvo = painel ?: return@BotaoAcao
            val qual = pagina ?: return@BotaoAcao
            Cluster.usarBolaDoPainel(qual, alvo.id)
            // Igual ao botão de projetar: fala com o Shizuku e espera por ele.
            escopo.launch(Dispatchers.IO) {
                ProjetorDoPainel.projetar(contexto, JanelaDoPainel.MENU, alvo.id, insistir = true)
            }
        },
    )
}

/**
 * Em que tela do carro esta janela fica, e o botão que a manda para lá.
 *
 * Substitui a configuração que antes se fazia na tela "Telas" do Impulse. O que
 * o Impulse tinha e um app comum não é o Shizuku — e o Shizuku a gente também
 * tem. Ver [ProjetorDoPainel] para o porquê de valer a pena sair de lá.
 *
 * A lista de telas é lida a cada abertura porque ela muda: numa central sem o
 * carro ligado o painel pode nem estar aceso, e a mesma lista de dois segundos
 * atrás mentiria.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun Projecao(janela: JanelaDoPainel, escolhida: Int?) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val resultados by ProjetorDoPainel.resultados.collectAsStateWithLifecycle()

    /**
     * Reler as telas e o estado do Shizuku é barato e precisa acontecer de
     * novo: quem chega aqui, descobre que falta iniciar o Shizuku, vai lá e
     * volta, encontraria a mesma resposta velha e concluiria que não adiantou.
     * O contador força a releitura sem precisar de um botão "atualizar".
     */
    var releituras by remember { mutableIntStateOf(0) }
    val telas = remember(releituras) { ProjetorDoPainel.telas(contexto) }
    val situacao = remember(releituras) { ShizukuShell.situacao() }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val olheiro = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) releituras++
        }
        lifecycle.lifecycle.addObserver(olheiro)
        onDispose { lifecycle.lifecycle.removeObserver(olheiro) }
    }

    Text("Em que tela", style = MaterialTheme.typography.titleMedium, color = Cores.TextoCorrido)
    Spacer(Modifier.height(4.dp))
    Text(
        "No H6 o painel de instrumentos costuma ser a tela 3. \"Nenhuma\" deixa a " +
            "janela desligada — é o que usar se preferir continuar configurando " +
            "esta janela pelo Impulse. A central aparece na lista só para teste: " +
            "projetar nela abre a janela por cima desta tela, e \"Recolher\" " +
            "devolve — serve para confirmar que a janela funciona quando o painel " +
            "não aceita.",
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Opcao("Nenhuma", escolhida == null) {
            Cluster.usarTela(janela, null)
            ProjetorDoPainel.recolher(janela)
        }
        telas.filter { it.id !in listOf(0, 2) }.forEach { tela ->
            Opcao(tela.descricao, escolhida == tela.id) {
                Cluster.usarTela(janela, tela.id)
                escopo.launch(Dispatchers.IO) {
                    ProjetorDoPainel.projetar(
                        contexto,
                        janela,
                        tela.id,
                        insistir = true,
                    )
                    releituras++
                }
            }
        }
    }

    if (telas.isEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Esta central não está mostrando nenhuma tela além da grande. No carro " +
                "isso costuma significar painel apagado — tente de novo com o carro ligado.",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.TextoApoio,
        )
    }

    val recado = recadoDaProjecao(situacao, resultados[janela])
    if (recado != null) {
        Spacer(Modifier.height(6.dp))
        Text(recado, style = MaterialTheme.typography.bodyMedium, color = Cores.TextoApoio)
    }
    if (situacao == ShizukuShell.Situacao.PRECISA_AUTORIZAR) {
        Spacer(Modifier.height(8.dp))
        BotaoAcao("Autorizar no Shizuku", onClick = ShizukuShell::pedirPermissao)
    }
}

/**
 * O que dizer sobre a projeção — inclusive, e principalmente, quando não deu.
 *
 * O estado do Shizuku vem antes do resultado da última tentativa: sem ele nada
 * vai funcionar, e mostrar "não achei a pilha" para quem só precisa iniciar o
 * Shizuku manda a pessoa investigar a coisa errada.
 */
private fun recadoDaProjecao(
    situacao: ShizukuShell.Situacao,
    resultado: ProjetorDoPainel.Resultado?,
): String? = when {
    situacao == ShizukuShell.Situacao.SEM_SHIZUKU ->
        "O Shizuku não está rodando nesta central. É ele quem tem permissão de mexer " +
            "nas telas — o Impulse também depende dele para isso. Abra o app do " +
            "Shizuku e inicie-o."
    situacao == ShizukuShell.Situacao.PRECISA_AUTORIZAR ->
        "Falta autorizar o Haval Trip no Shizuku. É uma vez só."
    resultado is ProjetorDoPainel.Resultado.Projetando -> "Mandando a janela para a tela…"
    resultado is ProjetorDoPainel.Resultado.Projetada ->
        "Projetada na tela ${resultado.tela}. Ela volta sozinha a cada partida do carro."
    resultado is ProjetorDoPainel.Resultado.Falhou -> "Não deu: ${resultado.motivo}."
    else -> null
}

/** Os tamanhos de letra oferecidos, como multiplicador do cálculo automático. */
private val ESCALAS = listOf(
    "Menor" to 0.75f,
    "Normal" to 1.0f,
    "Maior" to 1.3f,
    "Enorme" to 1.6f,
)

/**
 * A cor que o botao mostra na amostra.
 *
 * "Seguir o Impulse" nao tem cor propria, entao a amostra mostra a paleta que
 * esta valendo agora — e assim o botao ja responde "qual e ela?" sem o
 * motorista ter de selecionar para descobrir.
 */
private fun corDaAmostra(cor: CorDoCluster, paleta: PaletaDoImpulse.Resultado?): Long {
    if (cor != CorDoCluster.DO_IMPULSE) return cor.argb
    return (paleta as? PaletaDoImpulse.Resultado.Achou)?.paleta?.clara ?: cor.argb
}

/** O que dizer sobre a leitura da paleta, inclusive quando ela nao deu certo. */
private fun recadoDaPaleta(paleta: PaletaDoImpulse.Resultado?): String = when (paleta) {
    null -> "Perguntando ao Impulse qual paleta esta ativa…"
    is PaletaDoImpulse.Resultado.Achou ->
        "Paleta do Impulse agora: ${paleta.paleta.rotulo}. Os numeros ficam quase brancos " +
            "com um brilho nessa cor, como no modo Analogico V2 do tema Sport Colors."
    PaletaDoImpulse.Resultado.SemShizuku ->
        "Precisa da permissao do Shizuku para ler a paleta do Impulse. Enquanto nao " +
            "tiver, os numeros ficam brancos."
    is PaletaDoImpulse.Resultado.NaoDeuParaLer ->
        "Nao deu para saber a paleta (${paleta.motivo}); os numeros ficam brancos. " +
            "Escolha uma cor da lista se preferir fixar."
}
