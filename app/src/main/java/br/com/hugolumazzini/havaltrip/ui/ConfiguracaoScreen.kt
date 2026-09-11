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
import androidx.compose.runtime.LaunchedEffect
import br.com.hugolumazzini.havaltrip.CorDoCluster
import br.com.hugolumazzini.havaltrip.telemetry.PaletaDoImpulse
import br.com.hugolumazzini.havaltrip.FundoDoCluster
import br.com.hugolumazzini.havaltrip.ClusterActivity
import br.com.hugolumazzini.havaltrip.ESPIANDO
import br.com.hugolumazzini.havaltrip.ClusterCarroActivity
import br.com.hugolumazzini.havaltrip.ClusterMenuActivity
import br.com.hugolumazzini.havaltrip.AjustesDoCluster
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
import br.com.hugolumazzini.havaltrip.painel.TecladoDoVolante
import br.com.hugolumazzini.havaltrip.painel.ProjetorDoPainel
import br.com.hugolumazzini.havaltrip.painel.ShizukuShell
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.com.hugolumazzini.havaltrip.Empurrao
import br.com.hugolumazzini.havaltrip.Zoom
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
    val paleta by Cluster.paleta.collectAsStateWithLifecycle()
    // Sem isto, quem troca a paleta no volante e volta aqui continuaria vendo
    // a resposta antiga: a leitura so acontece quando alguem a pede.
    LaunchedEffect(Unit) { Cluster.atualizarPaleta() }
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
            "O resumo que aparece no painel atrás do volante. Escolha a tela aqui " +
                "embaixo e o app se coloca lá sozinho, sem passar pelo Impulse; a " +
                "posição dentro da tela se escolhe mais abaixo, e o que sobra fica " +
                "transparente.",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.TextoApoio,
        )

        Spacer(Modifier.height(14.dp))
        Projecao(JanelaDoPainel.NUMEROS, ajustes.telaDosNumeros)

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
                OpcaoColorida(cor.rotulo, ajustes.cor == cor, Color(corDaAmostra(cor, paleta))) {
                    Cluster.usarCor(cor)
                }
            }
        }
        if (ajustes.cor == CorDoCluster.DO_IMPULSE) {
            Spacer(Modifier.height(6.dp))
            Text(recadoDaPaleta(paleta), style = MaterialTheme.typography.bodySmall, color = Cores.TextoApoio)
        }

        Spacer(Modifier.height(14.dp))
        Text("Fundo do bloco", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(4.dp))
        Text(
            "Transparente deixa o painel do carro aparecer por baixo. Escolha um dos " +
                "opacos quando o bloco cair em cima de algo que o painel já desenha ali — " +
                "número sobre número não se lê.",
            style = MaterialTheme.typography.bodySmall,
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
        Text("Em que canto do painel", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(4.dp))
        Text(
            "Lugares prontos, para não ter de acertar pixel com o dedo nos " +
                "sliders do Impulse.",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LugarNoPainel.Cantos.forEach { lugar ->
                Opcao(lugar.rotulo, ajustes.lugar == lugar) { Cluster.usarLugar(lugar) }
            }
            // A faixa da navegação fica junto dos cantos porque é isso que ela
            // é para quem escolhe: mais um lugar. Que ela também acerte o
            // tamanho é detalhe de implementação, não uma segunda decisão.
            Opcao(
                LugarNoPainel.FAIXA_NAVEGACAO.rotulo,
                ajustes.lugar == LugarNoPainel.FAIXA_NAVEGACAO &&
                    ajustes.tamanho == TamanhoNoPainel.FAIXA_DA_NAVEGACAO,
            ) { Cluster.usarFaixaDaNavegacao() }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "\"Faixa da navegação\" é a tarja larga logo abaixo dos ícones do topo, " +
                "onde o painel do carro mostra as setas quando há rota. Ela já vem com " +
                "o tamanho certo.",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.TextoApoio,
        )

        Spacer(Modifier.height(14.dp))
        Text("Quanto espaço ocupa", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
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
        BotaoAcao("Ver como fica", onClick = { espiar(ClusterActivity::class.java) })

        Spacer(Modifier.height(18.dp))
        Text("O carro no painel", style = MaterialTheme.typography.titleMedium, color = Cores.Texto)
        Spacer(Modifier.height(4.dp))
        Text(
            "O desenho visto de cima, com portas, cintos e pressão dos pneus, numa " +
                "janela separada. São duas janelas justamente para cada uma poder " +
                "ficar num lugar diferente — e até em telas diferentes: os números " +
                "no painel e o carro na bola do ar, ao mesmo tempo.",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.TextoApoio,
        )

        Spacer(Modifier.height(14.dp))
        Projecao(JanelaDoPainel.CARRO, ajustes.telaDoCarro)

        Spacer(Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LugarNoPainel.Cantos.forEach { lugar ->
                Opcao(lugar.rotulo, ajustes.lugarDoCarro == lugar) { Cluster.usarLugarDoCarro(lugar) }
            }
            // Mesmo arranjo da faixa da navegação: um chip só, que já acerta o
            // tamanho junto, para não existir o meio-termo que precisa de aviso.
            Opcao(
                LugarNoPainel.BOLA_DO_AC.rotulo,
                ajustes.lugarDoCarro == LugarNoPainel.BOLA_DO_AC &&
                    ajustes.tamanhoDoCarro == TamanhoDoCarro.BOLA_DO_AC,
            ) { Cluster.usarBolaDoAr() }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "\"Bola do ar\" é o recorte redondo que o Impulse abre na tela do ar-" +
                "condicionado, encostado à direita. Para essa opção, aponte esta " +
                "janela para a tela 1 inteira (1920 x 860), e não para o painel.",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.TextoApoio,
        )
        Spacer(Modifier.height(10.dp))
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
        Text("Fundo do carro", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
        Spacer(Modifier.height(4.dp))
        Text(
            "Na bola do ar o fundo opaco é redondo, do tamanho da bola: é ele que " +
                "tapa a tela do ar-condicionado por baixo. Transparente deixa os dois " +
                "desenhos aparecerem um sobre o outro.",
            style = MaterialTheme.typography.bodySmall,
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
        PaginaDoPainel(ajustes.paginaDoCarro)

        Spacer(Modifier.height(14.dp))
        AjusteFino(JanelaDoPainel.CARRO, ajustes.empurraoDoCarro, ajustes.zoomDoCarro)

        Spacer(Modifier.height(12.dp))
        BotaoAcao("Ver como fica o carro", onClick = { espiar(ClusterCarroActivity::class.java) })

        Spacer(Modifier.height(18.dp))
        PaginaComVisoes(ajustes, ::espiar)
    }
}

/**
 * A terceira janela: uma página do painel inteira, com visões que se trocam no
 * volante.
 *
 * ## Solto e página, e por que os dois existem
 *
 * "Solto" é o que as duas seções acima fazem: janelinhas fixas num canto do
 * painel, por cima de qualquer página. Serve para quem quer o número sempre à
 * vista.
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

    Text("Página com visões", style = MaterialTheme.typography.titleMedium, color = Cores.Texto)
    Spacer(Modifier.height(4.dp))
    Text(
        "Uma página inteira do painel, em vez de janelinhas soltas. As páginas do " +
            "painel se trocam com as setas para os lados, no volante; dentro desta, " +
            "para cima e para baixo trocam a visão: primeiro o carro, depois um " +
            "contador para cada Trip. Aponte esta janela para a tela do painel " +
            "inteira e escolha em qual página ela aparece — de preferência a bola " +
            "que ficou vazia.",
        style = MaterialTheme.typography.bodySmall,
        color = Cores.TextoApoio,
    )

    Spacer(Modifier.height(14.dp))
    Projecao(JanelaDoPainel.MENU, ajustes.telaDoMenu)

    Spacer(Modifier.height(14.dp))
    Text("Página do painel", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
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
        style = MaterialTheme.typography.bodySmall,
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
    Text("Formato", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
    Spacer(Modifier.height(4.dp))
    Text(
        "A bola que sobrou é um recorte redondo pequeno, não a tela toda. " +
            "Encaixado, tudo cabe dentro dela; no painel inteiro, o conteúdo se " +
            "espalha — e aí só serve se a página que você escolheu não tiver esse " +
            "recorte.",
        style = MaterialTheme.typography.bodySmall,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(8.dp))
    FlowRowSimples {
        Opcao("Encaixar na bola", ajustes.menuNaBola) { Cluster.encaixarMenuNaBola(true) }
        Opcao("Painel inteiro", !ajustes.menuNaBola) { Cluster.encaixarMenuNaBola(false) }
    }

    Spacer(Modifier.height(10.dp))
    Text(
        if (ouvindoTeclas) {
            "A cruzinha do volante está sendo lida: para cima e para baixo trocam a " +
                "visão enquanto esta página estiver na frente."
        } else {
            "A cruzinha do volante não está sendo lida nesta central. A página ainda " +
                "funciona, mas fica travada na primeira visão."
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (ouvindoTeclas) Cores.TextoApoio else Cores.Atencao,
    )

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

    Text("Página do painel", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
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
        style = MaterialTheme.typography.bodySmall,
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
    Text(aviso, style = MaterialTheme.typography.bodySmall, color = Cores.Atencao)
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
 * O ajuste fino de posição da janela, em setas.
 *
 * Por que existe: os lugares prontos acertam o grosso e erram o fio. A faixa da
 * navegação foi medida numa foto do painel, e no carro o bloco caiu sobre a
 * estrada desenhada em vez de na tarja vazia acima dela. Sem isto, cada
 * tentativa de acerto seria um número trocado no código e um APK novo — e quem
 * enxerga o resultado está sentado no carro, não na frente do editor.
 *
 * Setas e não slider: o erro a corrigir é de alguns pixels, e o alvo de um
 * slider com o dedo, num carro, é muito maior do que isso. A seta dupla anda
 * [Empurrao.SALTO] para atravessar a tela sem quarenta toques; a simples anda
 * [Empurrao.PASSO], que é o pixel fino.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AjusteFino(janela: JanelaDoPainel, empurrao: Empurrao, zoom: Zoom) {
    Text("Ajuste fino da posição", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
    Spacer(Modifier.height(4.dp))
    Text(
        "Empurra a janela a partir do lugar escolhido acima, para acertar o que " +
            "o lugar pronto errou por pouco. Use o \"Ver como fica\" para conferir.",
        style = MaterialTheme.typography.bodySmall,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Seta("▲▲", "sobe muito") { Cluster.empurrar(janela, 0, -Empurrao.SALTO) }
        Seta("▲", "sobe pouco") { Cluster.empurrar(janela, 0, -Empurrao.PASSO) }
        Seta("▼", "desce pouco") { Cluster.empurrar(janela, 0, Empurrao.PASSO) }
        Seta("▼▼", "desce muito") { Cluster.empurrar(janela, 0, Empurrao.SALTO) }
        Seta("◀◀", "esquerda muito") { Cluster.empurrar(janela, -Empurrao.SALTO, 0) }
        Seta("◀", "esquerda pouco") { Cluster.empurrar(janela, -Empurrao.PASSO, 0) }
        Seta("▶", "direita pouco") { Cluster.empurrar(janela, Empurrao.PASSO, 0) }
        Seta("▶▶", "direita muito") { Cluster.empurrar(janela, Empurrao.SALTO, 0) }
        BotaoAcao(
            "Desfazer",
            onClick = { Cluster.centralizar(janela) },
            habilitado = !empurrao.centrado,
        )
    }
    Spacer(Modifier.height(6.dp))
    // O número aparece sempre, inclusive zerado: sem ele não há como saber se o
    // toque pegou, já que quem confere está olhando para a outra tela.
    Text(
        if (empurrao.centrado) "No lugar, sem empurrão."
        else "Empurrado ${rumo(empurrao.x, "para a direita", "para a esquerda")} e " +
            "${rumo(empurrao.y, "para baixo", "para cima")}.",
        style = MaterialTheme.typography.bodySmall,
        color = Cores.TextoApoio,
    )

    Spacer(Modifier.height(14.dp))
    Text("Ajuste fino do tamanho", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
    Spacer(Modifier.height(4.dp))
    Text(
        "Estica ou encolhe a partir do tamanho escolhido acima, em proporção — " +
            "os dois lados crescem juntos, então o bloco não deforma. Os tamanhos " +
            "prontos são degraus largos; isto é o que fica entre um e outro.",
        style = MaterialTheme.typography.bodySmall,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Seta("−−", "bem menor") { Cluster.ampliar(janela, -Zoom.SALTO) }
        Seta("−", "um pouco menor") { Cluster.ampliar(janela, -Zoom.PASSO) }
        Seta("+", "um pouco maior") { Cluster.ampliar(janela, Zoom.PASSO) }
        Seta("++", "bem maior") { Cluster.ampliar(janela, Zoom.SALTO) }
        BotaoAcao(
            "Desfazer",
            onClick = { Cluster.tamanhoNatural(janela) },
            habilitado = !zoom.natural,
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        if (zoom.natural) "No tamanho escolhido."
        else "${zoom.porcento}% do tamanho escolhido.",
        style = MaterialTheme.typography.bodySmall,
        color = Cores.TextoApoio,
    )

    Spacer(Modifier.height(14.dp))
    Text("Medidas desta janela", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
    Spacer(Modifier.height(4.dp))
    Text(
        "O que a janela mediu de si mesma. Com ela projetada no painel, muda a " +
            "cada toque nas setas; com ela fechada, é a última leitura, e não " +
            "acompanha os ajustes. Depois de acertar a posição, me mande estas " +
            "linhas: com elas eu corrijo as posições prontas no código, e quem " +
            "vier depois não precisa ajustar nada.",
        style = MaterialTheme.typography.bodySmall,
        color = Cores.TextoApoio,
    )
    Spacer(Modifier.height(8.dp))
    QuadroDeMedidas(janela)
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

    Text("Em que tela", style = MaterialTheme.typography.bodyMedium, color = Cores.TextoCorrido)
    Spacer(Modifier.height(4.dp))
    Text(
        "No H6 o painel de instrumentos costuma ser a tela 3. \"Nenhuma\" deixa a " +
            "janela desligada — é o que usar se preferir continuar configurando " +
            "esta janela pelo Impulse. A central aparece na lista só para teste: " +
            "projetar nela abre a janela por cima desta tela, e \"Recolher\" " +
            "devolve — serve para confirmar que a janela funciona quando o painel " +
            "não aceita.",
        style = MaterialTheme.typography.bodySmall,
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
        telas.forEach { tela ->
            Opcao(tela.descricao, escolhida == tela.id) { Cluster.usarTela(janela, tela.id) }
        }
    }

    if (telas.isEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Esta central não está mostrando nenhuma tela além da grande. No carro " +
                "isso costuma significar painel apagado — tente de novo com o carro ligado.",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.TextoApoio,
        )
    }

    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BotaoAcao(
            texto = "Projetar ${janela.rotulo.lowercase()}",
            habilitado = escolhida != null,
            onClick = {
                // Fora da thread principal: abre um processo pelo Shizuku e
                // espera por ele. Na principal, isso congelaria a tela.
                escopo.launch(Dispatchers.IO) {
                    // `insistir`: quem toca no botão quer a janela lá e à vista.
                    // Sem isso, com ela já projetada o toque não mexeria na
                    // ordem — e é justamente a ordem que decide quem aparece.
                    ProjetorDoPainel.projetar(
                        contexto,
                        janela,
                        escolhida ?: return@launch,
                        insistir = true,
                    )
                    // Uma tentativa pode revelar que o Shizuku caiu no meio-tempo.
                    releituras++
                }
            },
        )
        BotaoAcao("Recolher", onClick = { ProjetorDoPainel.recolher(janela) })
    }

    Spacer(Modifier.height(6.dp))
    Text(
        "Se o painel do Impulse aparecer por cima desta janela: os dois apps usam o " +
            "mesmo caminho, e quem projeta por último fica em cima. Toque em " +
            "\"Projetar\" de novo para trazer a nossa para a frente. Na partida do " +
            "carro isso é feito sozinho, meio minuto depois de ligar — que é o tempo " +
            "de o Impulse terminar de subir.",
        style = MaterialTheme.typography.bodySmall,
        color = Cores.TextoApoio,
    )

    val recado = recadoDaProjecao(situacao, resultados[janela])
    if (recado != null) {
        Spacer(Modifier.height(6.dp))
        Text(recado, style = MaterialTheme.typography.bodySmall, color = Cores.TextoApoio)
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
