package br.com.hugolumazzini.havaltrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.hugolumazzini.havaltrip.AjustesDoCluster
import br.com.hugolumazzini.havaltrip.Cluster
import br.com.hugolumazzini.havaltrip.FundoDoCluster
import br.com.hugolumazzini.havaltrip.ItemDoCluster
import br.com.hugolumazzini.havaltrip.CorDoCluster
import br.com.hugolumazzini.havaltrip.LugarNoPainel
import br.com.hugolumazzini.havaltrip.TripViewModel
import br.com.hugolumazzini.havaltrip.painel.JanelaDoPainel
import br.com.hugolumazzini.havaltrip.domain.MedidaDoPainel
import br.com.hugolumazzini.havaltrip.domain.PaletaSport
import br.com.hugolumazzini.havaltrip.telemetry.PaletaDoImpulse
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import br.com.hugolumazzini.havaltrip.domain.TripMetrics
import br.com.hugolumazzini.havaltrip.domain.VehicleLive
import br.com.hugolumazzini.havaltrip.ui.theme.Cores

/**
 * O resumo de viagem que aparece no painel de instrumentos.
 *
 * Três diferenças em relação à tela da central, todas ditadas por onde ela
 * mora:
 *
 * 1. **Fundo transparente.** O painel já tem desenho próprio embaixo; um
 *    retângulo escuro por cima pareceria um app colado, não parte do carro.
 * 2. **Tamanho desconhecido.** Quem define largura e altura é o motorista, na
 *    tela "Telas" do Impulse. Então nada aqui tem medida fixa: o layout vira
 *    linha ou coluna conforme o formato do retângulo, e o tamanho da letra sai
 *    de uma conta sobre o espaço disponível.
 * 3. **Nada de tocar.** O painel não tem toque. Não há botão nenhum — só
 *    números, e o motorista zera a viagem na central.
 *
 * O que aparece, de qual contador, em que tamanho e em que cor vem de
 * [Cluster], escolhido na tela de configuração da central.
 */
@Composable
fun ClusterScreen(vm: TripViewModel, espiando: Boolean = false) {
    val estado by vm.state.collectAsStateWithLifecycle()
    val ajustes by Cluster.ajustes.collectAsStateWithLifecycle()
    val paleta by Cluster.paleta.collectAsStateWithLifecycle()

    // A Trip escolhida na configuração; se ela foi apagada desde então, cai na
    // selecionada da central em vez de deixar o painel em branco.
    val trip = ajustes.tripId?.let { id -> estado.trips.find { it.id == id } }
        ?: estado.selectedTrip
        ?: return

    // A janela que o Impulse deu pode ser a tela inteira do painel: dar a ela
    // os extremos dos sliders é fácil, acertar 733x7 com o dedo não é. Por isso
    // o conteúdo se encaixa num pedaço dela, no canto escolhido na configuração
    // — o resto continua transparente, mostrando o painel do carro.
    BoxWithConstraints(Modifier.fillMaxSize().background(fundo(espiando))) {
        val itens = ajustes.ItensSeguros.size

        // A fração pedida, antes de qualquer piso. É ela que decide o formato,
        // e não a caixa final: se o piso mudasse linha para coluna no meio do
        // caminho, o painel trocaria de desenho sozinho ao encolher a janela.
        // O estica multiplica os dois lados pelo mesmo fator: o bloco cresce em
        // proporção, sem deformar, e o tamanho escolhido continua sendo a base.
        val larguraPedida = maxWidth * ajustes.tamanho.largura * ajustes.zoomDosNumeros.fator
        val alturaPedida = maxHeight * ajustes.tamanho.altura * ajustes.zoomDosNumeros.fator
        val emLinha = larguraPedida > alturaPedida * 1.6f

        // As frações do "Tamanho" nasceram pensando num painel grande. Numa
        // janela que já é uma tira fina — o overlay do simulador tem 145 dp de
        // altura — 20% dela não comporta nem a letra mínima, e o número sumia
        // cortado, sobrando só o rótulo. Então a fração é uma intenção: se o
        // que ela pede não cabe o conteúdo, a caixa cresce até caber, no
        // limite da janela.
        val largura = larguraPedida
            .coerceAtLeast(MedidaDoPainel.larguraMinimaDaCaixa(emLinha, itens).dp)
            .coerceAtMost(maxWidth)
        val altura = alturaPedida
            .coerceAtLeast(MedidaDoPainel.alturaMinimaDaCaixa(emLinha, itens).dp)
            .coerceAtMost(maxHeight)

        Box(
            Modifier
                .align(ajustes.lugar.alinhamento())
                // Depois do `align`, e não antes: o empurrão é a correção sobre
                // o lugar escolhido, não um lugar concorrente.
                .offset(x = ajustes.empurraoDosNumeros.x.dp, y = ajustes.empurraoDosNumeros.y.dp)
                .width(largura)
                .height(altura)
                // O fundo é do bloco, e não da janela: a janela é a tela
                // inteira do painel, e pintá-la inteira apagaria o carro em
                // vez de tapar só o pedaço que atrapalha.
                .background(Color(ajustes.fundo.argb))
                // Conta à central onde caiu. Ver `QuadroDeMedidas`.
                .medindo(
                    JanelaDoPainel.NUMEROS,
                    constraints.maxWidth,
                    constraints.maxHeight,
                    ajustes.fundo.argb,
                    false,
                ),
        ) {
            Painel(trip.metrics, estado.live, ajustes, emLinha, tinta(ajustes, paleta))
        }
    }
}

/** O canto escolhido, no formato que o Compose entende. */
fun LugarNoPainel.alinhamento(): Alignment = BiasAlignment(horizontal, vertical)

@Composable
private fun Painel(
    m: TripMetrics,
    live: VehicleLive,
    ajustes: AjustesDoCluster,
    emLinha: Boolean,
    tinta: Tinta,
) {
    val itens = ajustes.ItensSeguros

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(MedidaDoPainel.RESPIRO.dp),
    ) {
        // Cada dado recebe uma fatia igual, e o tamanho da letra sai do menor
        // lado dela. Sem a largura nessa conta, quatro itens com a letra no
        // "Maior" saíam pela borda e o número aparecia cortado no painel — que
        // é pior do que um número pequeno, porque parece um valor errado.
        val altura: Dp = if (emLinha) maxHeight else maxHeight / itens.size
        val largura: Dp = if (emLinha) maxWidth / itens.size else maxWidth

        val leituras = itens.map { it to it.leitura(m, live) }

        // Um tamanho só para todos, e é o do dado que mais aperta. Deixar cada
        // número achar o seu deixava a faixa desalinhada, com o valor mais
        // curto virando o mais gritante — o olho lê isso como "este aqui é o
        // importante", que não é o que se quer dizer.
        val tamanho = leituras.minOf { (_, leitura) ->
            MedidaDoPainel.tamanhoDaFonte(
                alturaDaFatia = altura.value,
                larguraDaFatia = largura.value,
                valor = leitura.first,
                unidade = leitura.second,
                emLinha = emLinha,
                escala = ajustes.escalaFonte,
            )
        }

        if (emLinha) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leituras.forEach { (item, leitura) ->
                    Bloco(item, leitura, tamanho, tinta, emLinha, Modifier.weight(1f))
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                leituras.forEach { (item, leitura) ->
                    Bloco(item, leitura, tamanho, tinta, emLinha, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Bloco(
    item: ItemDoCluster,
    leitura: Pair<String, String>,
    numero: Float,
    tinta: Tinta,
    emLinha: Boolean,
    modifier: Modifier = Modifier,
) {
    val (valor, unidade) = leitura
    val rotulo = MedidaDoPainel.tamanhoDoRotulo(numero)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            item.rotulo,
            color = Cores.TextoApoio,
            fontSize = rotulo.sp,
            letterSpacing = (rotulo * 0.08f).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        // Lado a lado, a unidade ganha a sua própria linha embaixo do número —
        // é o desenho mais limpo. Empilhado, ela vai ao lado do número: uma
        // terceira linha por dado esbarrava no rótulo do dado seguinte.
        if (emLinha) {
            Numero(valor, numero, tinta)
            Text(unidade, color = Cores.TextoApoio, fontSize = rotulo.sp, maxLines = 1)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Numero(valor, numero, tinta)
                Text(
                    " $unidade",
                    color = Cores.TextoApoio,
                    fontSize = rotulo.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = (numero * 0.12f).dp),
                )
            }
        }
    }
}

@Composable
private fun Numero(valor: String, tamanho: Float, tinta: Tinta) {
    Text(
        valor,
        color = tinta.cor,
        fontSize = tamanho.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        style = tinta.halo?.let { halo ->
            // O brilho do Analogico V2. O raio acompanha o tamanho da letra
            // porque um halo de medida fixa, que fica discreto num numero
            // grande, vira uma mancha borrada num numero pequeno.
            LocalTextStyle.current.copy(
                shadow = Shadow(color = halo, offset = Offset.Zero, blurRadius = tamanho * 0.5f),
            )
        } ?: LocalTextStyle.current,
    )
}

/**
 * Com que cor o numero e desenhado, e se leva brilho em volta.
 *
 * Duas coisas e nao uma porque o Impulse faz duas coisas diferentes: nas cores
 * escolhidas a mao o digito e da cor, e no Analogico V2 o digito e quase
 * branco e quem carrega a cor e o halo. Um campo so obrigaria a tela a
 * perguntar "que modo e este?" toda vez que fosse desenhar.
 */
internal data class Tinta(val cor: Color, val halo: Color? = null)

/**
 * A tinta que vale agora: a cor escolhida a mao, ou a paleta do Impulse.
 *
 * Quando a paleta nao pode ser lida — Shizuku sem permissao, Impulse ausente —
 * cai no branco sem halo. E o mesmo branco de sempre: quem escolheu "seguir o
 * Impulse" numa central que nao responde ve um numero legivel, e o porque
 * aparece na tela de configuracao, que e onde da para fazer algo a respeito.
 */
internal fun tinta(ajustes: AjustesDoCluster, paleta: PaletaDoImpulse.Resultado?): Tinta {
    if (ajustes.cor != CorDoCluster.DO_IMPULSE) return Tinta(Color(ajustes.cor.argb))
    val achada = (paleta as? PaletaDoImpulse.Resultado.Achou)?.paleta
        ?: return Tinta(Color(CorDoCluster.DO_IMPULSE.argb))
    return Tinta(
        cor = Color(PaletaSport.BRANCO_DO_ANALOGICO_V2),
        // A meia opacidade e o que o CSS do tema usa no brilho dos digitos:
        // a cor cheia em volta de cada numero fecharia o vao entre eles.
        halo = Color(achada.clara).copy(alpha = 0.55f),
    )
}

/**
 * Transparente no painel do carro — um retângulo escuro por cima pareceria um
 * app colado. Escuro só na conferência feita na central, onde atrás não há
 * painel nenhum e o texto claro sumiria sobre a tela de configuração.
 */
internal fun fundo(espiando: Boolean): Color =
    if (espiando) Color(0xFF0B0B0F) else Color.Transparent
