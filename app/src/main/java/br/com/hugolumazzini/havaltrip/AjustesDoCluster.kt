package br.com.hugolumazzini.havaltrip

import android.content.Context
import br.com.hugolumazzini.havaltrip.domain.TripMetrics
import br.com.hugolumazzini.havaltrip.domain.VehicleLive
import br.com.hugolumazzini.havaltrip.format.TripFormat
import br.com.hugolumazzini.havaltrip.painel.JanelaDoPainel
import br.com.hugolumazzini.havaltrip.painel.ProjetorDoPainel
import br.com.hugolumazzini.havaltrip.telemetry.PaletaDoImpulse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Um dado que pode ir para o painel de instrumentos.
 *
 * Cada um sabe se apresentar sozinho — rótulo, valor e unidade — porque quem
 * desenha no cluster não pode ter uma lista de `when` paralela: bastaria
 * alguém acrescentar um item aqui e esquecer lá para a tela mostrar um espaço
 * em branco sem explicação.
 *
 * Os rótulos são curtos ao ponto de caberem num retângulo de dois dedos de
 * altura. "MÉDIA" e não "CONSUMO MÉDIO": no painel não há espaço para prosa, e
 * o motorista não vai ler duas palavras de relance a 80 km/h.
 */
enum class ItemDoCluster(val rotulo: String, val descricao: String) {
    DISTANCIA("VIAGEM", "Distância da viagem"),
    MEDIA("MÉDIA", "Consumo médio (km/L)"),
    TEMPO("TEMPO", "Tempo de viagem"),
    VELOCIDADE_MEDIA("VEL. MÉD.", "Velocidade média"),
    VELOCIDADE_MAXIMA("MÁXIMA", "Velocidade máxima da viagem"),
    LITROS("GASTO", "Litros queimados na viagem"),
    CONSUMO_AGORA("AGORA", "Consumo instantâneo"),
    AUTONOMIA("AUTONOMIA", "Autonomia estimada"),
    HODOMETRO("ODÔMETRO", "Hodômetro do carro");

    /** O número já formatado, e a unidade separada dele. */
    fun leitura(m: TripMetrics, live: VehicleLive): Pair<String, String> = when (this) {
        DISTANCIA -> TripFormat.decimal(m.distanceKm, 1) to "km"
        MEDIA -> TripFormat.decimal(m.avgFuelConsumptionKml, 1) to "km/L"
        TEMPO -> TripFormat.duracao(m.totalTimeS) to TripFormat.unidadeDuracao(m.totalTimeS)
        VELOCIDADE_MEDIA -> TripFormat.decimal(m.avgSpeedKmh, 0) to "km/h"
        VELOCIDADE_MAXIMA -> TripFormat.decimal(m.maxSpeedKmh, 0) to "km/h"
        LITROS -> TripFormat.decimal(m.fuelLitres, 1) to "L"
        CONSUMO_AGORA -> TripFormat.decimal(live.instantFuelConsumptionKml, 1) to "km/L"
        AUTONOMIA -> TripFormat.decimal(live.autonomyDteKm, 0) to "km"
        HODOMETRO -> TripFormat.decimal(live.odometerTotalKm, 0) to "km"
    }
}

/**
 * Cor do texto no painel, das poucas que se leem bem sobre o fundo do carro.
 *
 * [DO_IMPULSE] não é uma cor: é "use a que estiver no Impulse". Existe porque
 * trocar de paleta lá e ter de vir trocar aqui também é o tipo de ajuste que
 * se esquece, e aí o resumo de viagem fica vermelho no meio de um cluster
 * azul. O `argb` dela é o branco de sempre, que é onde a tela cai quando não
 * consegue descobrir a paleta.
 */
enum class CorDoCluster(val rotulo: String, val argb: Long) {
    BRANCO("Branco", 0xFFF5F5F5),
    AZUL("Azul", 0xFF4A9EFF),
    VERDE("Verde", 0xFF34C759),
    AMBAR("Âmbar", 0xFFFFB020),
    VERMELHO("Vermelho", 0xFFFF453A),
    DO_IMPULSE("Seguir o Impulse", 0xFFF5F5F5),
}

/**
 * O que fica atrás dos números, no retângulo do bloco.
 *
 * Transparente é o padrão e o que faz a janela parecer parte do carro: o
 * painel continua aparecendo em volta e por baixo. Mas o painel do H6 desenha
 * coisas próprias onde o bloco cai, e aí número por cima de número não se lê —
 * é para esses casos que existem os fundos opacos, que tapam o que está atrás
 * em vez de disputar espaço com ele.
 *
 * O retângulo é reto, sem canto arredondado, justamente porque a função dele é
 * cobrir: canto arredondado deixaria vazar as quinas do que se quer esconder.
 */
enum class FundoDoCluster(val rotulo: String, val argb: Long) {
    TRANSPARENTE("Transparente", 0x00000000),
    ESCURO("Escuro", 0xCC000000),
    PRETO("Preto sólido", 0xFF000000),
}

/**
 * Um dos nove cantos da janela, mais a faixa da navegação.
 *
 * Existe para o motorista não ter de acertar posição com sliders de pixel na
 * tela do Impulse: lá ele dá a tela inteira do painel ao app (0,0 até
 * 1920x720, os sliders nos extremos, que é fácil), e a posição de verdade se
 * escolhe aqui, num botão. Como a janela é transparente, o que sobra em volta
 * continua sendo o painel do carro.
 *
 * Os valores são o "viés" que o Compose usa: -1 é encostado no começo, 0 é o
 * meio, 1 é encostado no fim.
 */
enum class LugarNoPainel(val rotulo: String, val horizontal: Float, val vertical: Float) {
    CIMA_ESQUERDA("Cima, esquerda", -1f, -1f),
    CIMA_CENTRO("Cima, centro", 0f, -1f),
    CIMA_DIREITA("Cima, direita", 1f, -1f),
    MEIO_ESQUERDA("Meio, esquerda", -1f, 0f),
    MEIO_CENTRO("Meio, centro", 0f, 0f),
    MEIO_DIREITA("Meio, direita", 1f, 0f),
    BAIXO_ESQUERDA("Baixo, esquerda", -1f, 1f),
    BAIXO_CENTRO("Baixo, centro", 0f, 1f),
    BAIXO_DIREITA("Baixo, direita", 1f, 1f),

    /**
     * A faixa que o painel do carro reserva para as instruções de navegação.
     *
     * Não é um dos nove cantos: fica entre "cima" e "meio", logo abaixo dos
     * ícones do topo e acima do horizonte da estrada desenhada. Ver
     * [TamanhoNoPainel.FAIXA_DA_NAVEGACAO] para de onde saem os números.
     */
    FAIXA_NAVEGACAO("Faixa da navegação", 0f, -0.36f),

    /**
     * Onde fica a bola do ar-condicionado do Impulse, no display 1.
     *
     * Encostada à direita e centrada na vertical: o recorte redondo vai de
     * x=1404 a 1856 numa tela de 1920 de largura, então das 734 unidades de
     * folga horizontal ele já gastou 1404 — daí o viés de 0.913, quase no
     * canto mas não nele. Ver [TamanhoDoCarro.BOLA_DO_AC] para as medidas.
     */
    BOLA_DO_AC("Bola do ar", 0.913f, 0f);

    companion object {
        /**
         * Só os nove cantos.
         *
         * Os dois lugares especiais são de janelas diferentes — a faixa é do
         * bloco de números, a bola é do carro —, e cada lista mostra só o seu.
         * Um nome que não explica nada no contexto errado é opção a mais para
         * confundir.
         */
        val Cantos: List<LugarNoPainel> get() = entries - FAIXA_NAVEGACAO - BOLA_DO_AC
    }
}

/**
 * Quanto da janela o conteúdo ocupa, em frações de largura e altura.
 *
 * Frações, e não pixels, porque o mesmo ajuste tem de servir tanto para quem
 * deu a tela inteira ao app quanto para quem deu um retângulo pequeno.
 *
 * [FAIXA_DA_NAVEGACAO] é o único que não é um tamanho escolhido a olho: ver o
 * comentário dele.
 */
enum class TamanhoNoPainel(val rotulo: String, val largura: Float, val altura: Float) {
    PEQUENO("Pequeno", 0.24f, 0.18f),
    MEDIO("Médio", 0.34f, 0.24f),
    GRANDE("Grande", 0.46f, 0.32f),
    FAIXA("Faixa larga", 0.70f, 0.20f),

    /**
     * A faixa que o painel do carro reserva para as instruções de navegação.
     *
     * É a tarja larga logo abaixo da linha de ícones do topo, onde o painel
     * nativo do H6 mostra a seta e o "vire à direita em 200 m" quando há rota.
     * Sem rota ela fica vazia, e é o maior pedaço de painel que o carro deixa
     * livre — daí valer um tamanho pronto em vez de acerto no olho.
     *
     * Ao contrário das outras frações daqui, estas não saem de código nem de
     * arquivo de tema: o painel nativo é da ROM do carro, e não há onde ler as
     * medidas dele. Saíram de uma foto do painel, medidas contra a área útil da
     * tela e convertidas para o quadro de 1920x720 — cerca de 1063x174 a partir
     * de x=411, y=175. É uma boa aproximação, não um valor exato: se ficar
     * torto no carro, é aqui que se corrige.
     *
     * Não aparece na lista de tamanhos: quem o escolhe é o botão da faixa, que
     * grava junto o [LugarNoPainel.FAIXA_NAVEGACAO]. Sozinho ele seria a medida
     * da faixa numa altura qualquer, que não é lugar nenhum.
     */
    FAIXA_DA_NAVEGACAO("Faixa da navegação", 0.554f, 0.242f),

    TUDO("A janela toda", 1f, 1f);

    companion object {
        /** Os tamanhos que a lista oferece. Ver [FAIXA_DA_NAVEGACAO]. */
        val Escolhiveis: List<TamanhoNoPainel> get() = entries - FAIXA_DA_NAVEGACAO
    }
}

/** Quanto da janela o desenho do carro ocupa, em fração da altura. */
enum class TamanhoDoCarro(val rotulo: String, val fracao: Float) {
    PEQUENO("Pequeno", 0.35f),
    MEDIO("Médio", 0.55f),
    GRANDE("Grande", 0.80f),
    TUDO("A janela toda", 1f),

    /**
     * Do tamanho da bola do ar-condicionado do Impulse.
     *
     * O Impulse recorta um pedaço redondo do display 1 para a tela dele de
     * A/C: um `FrameLayout` clipado em oval, centro em x=1630 y=430 e raio 226
     * — 452x452, de x=1404 a 1856 e de y=204 a 656, num display de 1920x860.
     * Repare que ele não é centrado: fica encostado à direita.
     *
     * 452/860 é a fração de altura que reproduz esse círculo. O desenho do
     * carro é alto e estreito, então cabe folgado na largura.
     *
     * Não aparece na lista de tamanhos: quem o escolhe é o chip "Bola do ar",
     * que grava junto o [LugarNoPainel.BOLA_DO_AC].
     */
    BOLA_DO_AC("Bola do ar", 0.526f);

    companion object {
        /** Os tamanhos que a lista oferece. Ver [BOLA_DO_AC]. */
        val Escolhiveis: List<TamanhoDoCarro> get() = entries - BOLA_DO_AC
    }
}

/**
 * O empurrãozinho final, em dp, sobre o lugar escolhido.
 *
 * Os nove cantos e os dois lugares prontos acertam o grosso, mas não o fio: a
 * faixa da navegação, por exemplo, saiu de uma foto medida a régua, e no carro
 * ela caiu em cima da estrada desenhada em vez de na tarja vazia. Sem este
 * ajuste, corrigir isso seria mudar um número no código e gerar um APK novo a
 * cada tentativa — e quem vê o resultado é quem está sentado no carro, não quem
 * escreve o código.
 *
 * Em dp e por setas, não por slider: o alvo é de poucos pixels, e slider com o
 * dedo num carro não acerta poucos pixels. A seta simples anda [PASSO] e a
 * dupla anda [SALTO], que é o que evita quarenta toques para atravessar o
 * painel sem tirar de quem ajusta a chance de parar no pixel certo.
 */
data class Empurrao(val x: Int = 0, val y: Int = 0) {

    /** Somado e já contido no limite, para o bloco nunca sair da janela. */
    fun mais(dx: Int, dy: Int) = Empurrao(
        x = (x + dx).coerceIn(-LIMITE, LIMITE),
        y = (y + dy).coerceIn(-LIMITE, LIMITE),
    )

    val centrado: Boolean get() = x == 0 && y == 0

    companion object {
        /** Quanto a seta simples anda por toque, em dp. */
        const val PASSO = 6

        /** Quanto a seta dupla anda por toque, em dp. */
        const val SALTO = 30

        /**
         * Até onde o empurrão vai, em dp para cada lado.
         *
         * 240 é mais da metade da altura do painel: passa do ponto em que o
         * bloco ainda estaria visível, e portanto nunca é o limite que atrapalha
         * — só existe para uma preferência gravada errada não jogar a janela
         * para fora da tela sem o motorista ter como trazê-la de volta.
         */
        const val LIMITE = 240
    }
}

/**
 * Quanto o tamanho escolhido é esticado, em porcento.
 *
 * Os tamanhos prontos são degraus largos — de "Médio" para "Grande" o bloco
 * salta um terço —, e no painel de verdade o que falta é quase sempre menos que
 * um degrau. Isto multiplica o tamanho escolhido em vez de substituí-lo: os dois
 * lados crescem juntos, na mesma proporção, então o bloco não deforma e as
 * predefinições continuam sendo o ponto de partida que a régua sabe relatar.
 *
 * Em porcento inteiro, e não em fração, porque é o que se grava e se lê sem
 * arredondamento acumulado a cada toque.
 */
@JvmInline
value class Zoom(val porcento: Int = 100) {

    /** Somado e já contido nos limites, para o bloco nunca sumir nem estourar. */
    fun mais(delta: Int) = Zoom((porcento + delta).coerceIn(MINIMO, MAXIMO))

    /** O multiplicador que o layout usa. */
    val fator: Float get() = porcento / 100f

    val natural: Boolean get() = porcento == PADRAO

    companion object {
        const val PADRAO = 100

        /** Quanto o botão simples anda, em pontos percentuais. */
        const val PASSO = 5

        /** Quanto o botão duplo anda. */
        const val SALTO = 25

        /**
         * Até onde vai o estica-e-encolhe.
         *
         * Um quarto do tamanho escolhido ainda se lê; menos que isso seria um
         * borrão que o motorista não teria como desfazer sem adivinhar. O teto
         * de três vezes é onde o maior dos tamanhos prontos já passa da tela —
         * daí para cima só se ganharia recorte.
         */
        const val MINIMO = 25
        const val MAXIMO = 300
    }
}

/**
 * Onde uma janela do painel realmente caiu, em pixels.
 *
 * Não é ajuste: é o que a janela mediu de si mesma depois de desenhada. Existe
 * porque as posições prontas — a faixa da navegação, a bola do ar — saíram de
 * fotos medidas contra a área útil da tela, e no carro erraram. Não dá para
 * corrigi-las às cegas: o painel é da ROM do carro, não há de onde ler as
 * medidas certas, e cada palpite custa um APK novo.
 *
 * Então quem mede é a própria janela, rodando no painel de verdade, e o número
 * atravessa até a central pelo [Cluster.medidas]. Ver `linhasDaMedida` para
 * como ele se lê.
 */
data class MedidaDaJanela(
    val janelaLargura: Int,
    val janelaAltura: Int,
    val x: Int,
    val y: Int,
    val largura: Int,
    val altura: Int,
    /**
     * A cor que a janela pediu para o fundo, na hora de desenhar.
     *
     * Vem da janela e não da configuração de propósito: as duas deveriam dizer
     * a mesma coisa, e quando não dizem é exatamente isso que se precisa saber.
     * Um fundo escolhido preto que chega aqui como transparente é um ajuste que
     * não atravessou; um que chega preto e mesmo assim não tapa nada na tela é
     * outro problema, em outro lugar. Sem este número os dois casos se parecem.
     */
    val fundoArgb: Long,
    /** Se o fundo saiu redondo, como manda a bola do ar, ou reto. */
    val fundoRedondo: Boolean,
)

/**
 * O que o motorista escolheu para o painel de instrumentos.
 *
 * @param tripId qual contador vai para o painel. `null` significa "o que
 *   estiver selecionado na central" — é o padrão porque acompanha quem troca
 *   de Trip na tela grande sem ter de mexer aqui também.
 * @param itens quais dados aparecem, na ordem em que aparecem. Vazio nunca:
 *   ver [ItensSeguros].
 * @param escalaFonte multiplicador sobre o tamanho que a tela calcularia
 *   sozinha a partir do retângulo. 1.0 é o automático.
 * @param telaDosNumeros em que tela do carro o bloco de números é projetado, ou
 *   `null` para não projetar nada sozinho. Ver [ProjetorDoPainel].
 * @param telaDoCarro o mesmo, para a janela do desenho do carro. São dois
 *   campos independentes de propósito: o caso que motivou tudo isto é justamente
 *   o bloco no painel (tela 3) e o carro na bola do ar (tela 1), ao mesmo tempo.
 * @param empurraoDosNumeros deslocamento fino do bloco de números, em dp, a
 *   partir do lugar escolhido. Ver [Empurrao].
 * @param empurraoDoCarro o mesmo, para a janela do carro.
 * @param paginaDoCarro em qual página do carrossel de bolas do painel a janela
 *   do carro aparece. `null` é "em todas", que é como sempre foi. Ver
 *   [br.com.hugolumazzini.havaltrip.painel.PaginaDoCluster].
 */
data class AjustesDoCluster(
    val tripId: String? = null,
    val itens: List<ItemDoCluster> = listOf(
        ItemDoCluster.DISTANCIA,
        ItemDoCluster.MEDIA,
        ItemDoCluster.TEMPO,
    ),
    val escalaFonte: Float = 1.0f,
    val cor: CorDoCluster = CorDoCluster.BRANCO,
    val fundo: FundoDoCluster = FundoDoCluster.TRANSPARENTE,
    val lugar: LugarNoPainel = LugarNoPainel.CIMA_CENTRO,
    val tamanho: TamanhoNoPainel = TamanhoNoPainel.FAIXA,
    val lugarDoCarro: LugarNoPainel = LugarNoPainel.MEIO_CENTRO,
    val tamanhoDoCarro: TamanhoDoCarro = TamanhoDoCarro.MEDIO,
    val fundoDoCarro: FundoDoCluster = FundoDoCluster.TRANSPARENTE,
    val telaDosNumeros: Int? = null,
    val telaDoCarro: Int? = null,
    val empurraoDosNumeros: Empurrao = Empurrao(),
    val empurraoDoCarro: Empurrao = Empurrao(),
    val zoomDosNumeros: Zoom = Zoom(),
    val zoomDoCarro: Zoom = Zoom(),
    val paginaDoCarro: Int? = null,
    val telaDoMenu: Int? = null,
    val paginaDoMenu: Int? = null,
    val empurraoDoMenu: Empurrao = Empurrao(),
    val zoomDoMenu: Zoom = Zoom(),
    /**
     * Se a página se encaixa no recorte redondo da bola do ar.
     *
     * Ligado por padrão porque é o caso que existe: a página que sobra no
     * carrossel é justamente a da bola vazia. Desligado, a visão usa o painel
     * inteiro — que só serve a quem projetar numa tela sem esse recorte.
     */
    val menuNaBola: Boolean = true,
) {
    /**
     * A lista que a tela do painel usa de fato.
     *
     * Desmarcar tudo é um estado legítimo na configuração — dá para chegar
     * nele item a item —, mas no painel resultaria numa janela vazia que
     * pareceria o app travado. Neste caso mostra a distância, que é o motivo
     * de existir do resumo.
     */
    val ItensSeguros: List<ItemDoCluster>
        get() = itens.ifEmpty { listOf(ItemDoCluster.DISTANCIA) }
}

/**
 * Onde esses ajustes moram.
 *
 * Ficam em `SharedPreferences`, e não no arquivo das Trips, porque não são
 * dados de viagem: são preferência de tela, não podem ser perdidos numa
 * zeragem nem viajar num relatório de diagnóstico. E ficam num único objeto do
 * processo porque quem escreve (a tela de configuração, na central) e quem lê
 * (a tela do painel) são duas Activities distintas — sem o [StateFlow]
 * compartilhado, mudar a cor só teria efeito no próximo `am start`.
 */
object Cluster {

    private const val ARQUIVO = "cluster"
    private const val TRIP = "tripId"
    private const val ITENS = "itens"
    private const val ESCALA = "escalaFonte"
    private const val COR = "cor"
    private const val FUNDO = "fundo"
    private const val LUGAR = "lugar"
    private const val TAMANHO = "tamanho"
    private const val LUGAR_CARRO = "lugarDoCarro"
    private const val TAMANHO_CARRO = "tamanhoDoCarro"
    private const val FUNDO_CARRO = "fundoDoCarro"
    private const val TELA_NUMEROS = "telaDosNumeros"
    private const val TELA_CARRO = "telaDoCarro"
    private const val EMPURRAO_NUMEROS_X = "empurraoDosNumerosX"
    private const val EMPURRAO_NUMEROS_Y = "empurraoDosNumerosY"
    private const val EMPURRAO_CARRO_X = "empurraoDoCarroX"
    private const val EMPURRAO_CARRO_Y = "empurraoDoCarroY"
    private const val ZOOM_NUMEROS = "zoomDosNumeros"
    private const val ZOOM_CARRO = "zoomDoCarro"
    private const val PAGINA_CARRO = "paginaDoCarro"
    private const val TELA_MENU = "telaDoMenu"
    private const val PAGINA_MENU = "paginaDoMenu"
    private const val EMPURRAO_MENU_X = "empurraoDoMenuX"
    private const val EMPURRAO_MENU_Y = "empurraoDoMenuY"
    private const val ZOOM_MENU = "zoomDoMenu"
    private const val MENU_NA_BOLA = "menuNaBola"

    /**
     * O que se grava no lugar de "nenhuma tela".
     *
     * `SharedPreferences` não guarda inteiro nulo, e 0 não serve de sentinela:
     * 0 é a tela da central, um valor legítimo. -1 não é tela nenhuma.
     */
    private const val SEM_TELA = -1

    /** O mesmo truque para "em qualquer página": 0 é a página padrão do carro. */
    private const val QUALQUER_PAGINA = -1

    private lateinit var prefs: android.content.SharedPreferences

    private val _ajustes = MutableStateFlow(AjustesDoCluster())
    val ajustes: StateFlow<AjustesDoCluster> = _ajustes.asStateFlow()

    /**
     * O que cada janela do painel mediu de si mesma, da última vez que apareceu.
     *
     * Aqui e não dentro da janela porque quem precisa ler é a central: a janela
     * projetada tem dois dedos de altura e fica do outro lado do carro, e texto
     * de régua nela sairia minúsculo. A janela mede e conta; a tela de
     * Configuração mostra em tamanho de gente.
     *
     * Na memória e não nas preferências, de propósito: é uma leitura do que
     * está na tela agora, e um valor gravado sobreviveria à janela que o
     * produziu — a central mostraria com confiança a medida de um ajuste que já
     * mudou. Vazio quer dizer "essa janela ainda não apareceu", que é uma
     * resposta honesta e é o que a tela diz.
     *
     * As duas Activities do painel e a central são o mesmo processo, então isto
     * atravessa sem precisar de arquivo nem de aviso.
     */
    private val _medidas = MutableStateFlow<Map<JanelaDoPainel, MedidaDaJanela>>(emptyMap())
    val medidas: StateFlow<Map<JanelaDoPainel, MedidaDaJanela>> = _medidas.asStateFlow()

    /**
     * A janela conta onde caiu.
     *
     * Ignora a repetição porque quem chama é o `onGloballyPositioned`, que
     * dispara a cada quadro em que algo se move: sem isto, cada animação do
     * painel viraria uma recomposição da central.
     */
    fun anotarMedida(janela: JanelaDoPainel, medida: MedidaDaJanela) {
        if (_medidas.value[janela] == medida) return
        _medidas.value = _medidas.value + (janela to medida)
    }

    /**
     * A paleta lida do Impulse, ou o motivo de não ter dado.
     *
     * `null` é "ainda não olhei". Fica aqui, e não dentro da tela, porque as
     * duas janelas do painel e a de configuração precisam da mesma resposta, e
     * a leitura abre um processo pelo Shizuku — repetir isso por tela seria
     * três `cat` para saber a mesma coisa.
     */
    private val _paleta = MutableStateFlow<PaletaDoImpulse.Resultado?>(null)
    val paleta: StateFlow<PaletaDoImpulse.Resultado?> = _paleta.asStateFlow()

    /**
     * Vai perguntar ao Impulse qual é a paleta.
     *
     * Fora da thread principal por causa do processo; e sempre que uma tela
     * aparece, porque o motorista pode ter trocado a paleta no volante entre
     * uma abertura e outra — não há aviso nenhum quando isso acontece.
     */
    fun atualizarPaleta() {
        CoroutineScope(Dispatchers.IO).launch { _paleta.value = PaletaDoImpulse.ler() }
    }

    /** Idempotente: as duas telas chamam, e quem chegar primeiro carrega. */
    @Synchronized
    fun iniciar(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
        _ajustes.value = ler()
    }

    private fun ler(): AjustesDoCluster {
        val padrao = AjustesDoCluster()
        val nomes = prefs.getString(ITENS, null)
        return AjustesDoCluster(
            tripId = prefs.getString(TRIP, null),
            // Um item removido do enum numa versão futura vira lixo aqui; o
            // `mapNotNull` o descarta em vez de derrubar a tela do painel.
            itens = nomes?.split(",")
                ?.mapNotNull { nome -> ItemDoCluster.entries.find { it.name == nome } }
                ?: padrao.itens,
            escalaFonte = prefs.getFloat(ESCALA, padrao.escalaFonte),
            cor = prefs.getString(COR, null)
                ?.let { nome -> CorDoCluster.entries.find { it.name == nome } }
                ?: padrao.cor,
            fundo = prefs.getString(FUNDO, null)
                ?.let { nome -> FundoDoCluster.entries.find { it.name == nome } }
                ?: padrao.fundo,
            lugar = prefs.getString(LUGAR, null)
                ?.let { nome -> LugarNoPainel.entries.find { it.name == nome } }
                ?: padrao.lugar,
            tamanho = prefs.getString(TAMANHO, null)
                ?.let { nome -> TamanhoNoPainel.entries.find { it.name == nome } }
                ?: padrao.tamanho,
            lugarDoCarro = prefs.getString(LUGAR_CARRO, null)
                ?.let { nome -> LugarNoPainel.entries.find { it.name == nome } }
                ?: padrao.lugarDoCarro,
            tamanhoDoCarro = prefs.getString(TAMANHO_CARRO, null)
                ?.let { nome -> TamanhoDoCarro.entries.find { it.name == nome } }
                ?: padrao.tamanhoDoCarro,
            fundoDoCarro = prefs.getString(FUNDO_CARRO, null)
                ?.let { nome -> FundoDoCluster.entries.find { it.name == nome } }
                ?: padrao.fundoDoCarro,
            telaDosNumeros = prefs.getInt(TELA_NUMEROS, SEM_TELA).takeIf { it != SEM_TELA },
            telaDoCarro = prefs.getInt(TELA_CARRO, SEM_TELA).takeIf { it != SEM_TELA },
            // Passa pelo `mais` de propósito: é ele que contém no limite, e
            // assim uma preferência adulterada não some com a janela.
            empurraoDosNumeros = Empurrao()
                .mais(prefs.getInt(EMPURRAO_NUMEROS_X, 0), prefs.getInt(EMPURRAO_NUMEROS_Y, 0)),
            empurraoDoCarro = Empurrao()
                .mais(prefs.getInt(EMPURRAO_CARRO_X, 0), prefs.getInt(EMPURRAO_CARRO_Y, 0)),
            // Pelo `mais` de propósito, como os empurrões: é ele que contém nos
            // limites, e um valor gravado errado não some com o bloco.
            zoomDosNumeros = Zoom(0).mais(prefs.getInt(ZOOM_NUMEROS, Zoom.PADRAO)),
            zoomDoCarro = Zoom(0).mais(prefs.getInt(ZOOM_CARRO, Zoom.PADRAO)),
            paginaDoCarro = prefs.getInt(PAGINA_CARRO, QUALQUER_PAGINA)
                .takeIf { it != QUALQUER_PAGINA },
            telaDoMenu = prefs.getInt(TELA_MENU, SEM_TELA).takeIf { it != SEM_TELA },
            paginaDoMenu = prefs.getInt(PAGINA_MENU, QUALQUER_PAGINA)
                .takeIf { it != QUALQUER_PAGINA },
            empurraoDoMenu = Empurrao()
                .mais(prefs.getInt(EMPURRAO_MENU_X, 0), prefs.getInt(EMPURRAO_MENU_Y, 0)),
            zoomDoMenu = Zoom(0).mais(prefs.getInt(ZOOM_MENU, Zoom.PADRAO)),
            menuNaBola = prefs.getBoolean(MENU_NA_BOLA, true),
        )
    }

    private fun gravar(novo: AjustesDoCluster) {
        _ajustes.value = novo
        prefs.edit()
            .putString(TRIP, novo.tripId)
            .putString(ITENS, novo.itens.joinToString(",") { it.name })
            .putFloat(ESCALA, novo.escalaFonte)
            .putString(COR, novo.cor.name)
            .putString(FUNDO, novo.fundo.name)
            .putString(LUGAR, novo.lugar.name)
            .putString(TAMANHO, novo.tamanho.name)
            .putString(LUGAR_CARRO, novo.lugarDoCarro.name)
            .putString(TAMANHO_CARRO, novo.tamanhoDoCarro.name)
            .putString(FUNDO_CARRO, novo.fundoDoCarro.name)
            .putInt(TELA_NUMEROS, novo.telaDosNumeros ?: SEM_TELA)
            .putInt(TELA_CARRO, novo.telaDoCarro ?: SEM_TELA)
            .putInt(EMPURRAO_NUMEROS_X, novo.empurraoDosNumeros.x)
            .putInt(EMPURRAO_NUMEROS_Y, novo.empurraoDosNumeros.y)
            .putInt(EMPURRAO_CARRO_X, novo.empurraoDoCarro.x)
            .putInt(EMPURRAO_CARRO_Y, novo.empurraoDoCarro.y)
            .putInt(ZOOM_NUMEROS, novo.zoomDosNumeros.porcento)
            .putInt(ZOOM_CARRO, novo.zoomDoCarro.porcento)
            .putInt(PAGINA_CARRO, novo.paginaDoCarro ?: QUALQUER_PAGINA)
            .putInt(TELA_MENU, novo.telaDoMenu ?: SEM_TELA)
            .putInt(PAGINA_MENU, novo.paginaDoMenu ?: QUALQUER_PAGINA)
            .putInt(EMPURRAO_MENU_X, novo.empurraoDoMenu.x)
            .putInt(EMPURRAO_MENU_Y, novo.empurraoDoMenu.y)
            .putInt(ZOOM_MENU, novo.zoomDoMenu.porcento)
            .putBoolean(MENU_NA_BOLA, novo.menuNaBola)
            .apply()
    }

    fun usarTrip(tripId: String?) = gravar(_ajustes.value.copy(tripId = tripId))

    /**
     * Liga ou desliga um item. Quem entra vai para o fim da lista, para a ordem
     * na tela ser a ordem em que o motorista escolheu — previsível, e sob o
     * controle dele.
     */
    fun alternarItem(item: ItemDoCluster) {
        val atuais = _ajustes.value.itens
        gravar(_ajustes.value.copy(itens = if (item in atuais) atuais - item else atuais + item))
    }

    fun usarEscala(escala: Float) = gravar(_ajustes.value.copy(escalaFonte = escala))

    fun usarCor(cor: CorDoCluster) = gravar(_ajustes.value.copy(cor = cor))

    fun usarFundo(fundo: FundoDoCluster) = gravar(_ajustes.value.copy(fundo = fundo))

    fun usarLugar(lugar: LugarNoPainel) = gravar(_ajustes.value.copy(lugar = lugar))

    fun usarTamanho(tamanho: TamanhoNoPainel) = gravar(_ajustes.value.copy(tamanho = tamanho))

    /**
     * Põe o bloco na faixa da navegação: lugar e tamanho de uma vez.
     *
     * Os dois são um par — a medida da faixa só faz sentido naquela altura —, e
     * deixar o motorista escolher um sem o outro só criava um estado errado
     * para a tela ter de avisar depois. Um botão que já acerta os dois não tem
     * o que avisar.
     */
    fun usarFaixaDaNavegacao() = gravar(
        _ajustes.value.copy(
            lugar = LugarNoPainel.FAIXA_NAVEGACAO,
            tamanho = TamanhoNoPainel.FAIXA_DA_NAVEGACAO,
        ),
    )

    fun usarLugarDoCarro(lugar: LugarNoPainel) = gravar(_ajustes.value.copy(lugarDoCarro = lugar))

    fun usarTamanhoDoCarro(tamanho: TamanhoDoCarro) =
        gravar(_ajustes.value.copy(tamanhoDoCarro = tamanho))

    fun usarFundoDoCarro(fundo: FundoDoCluster) =
        gravar(_ajustes.value.copy(fundoDoCarro = fundo))

    /**
     * Em qual página do carrossel do painel a janela do carro aparece.
     *
     * `null` é "em todas", que é o comportamento antigo e continua sendo o
     * padrão: prender a janela a uma página numa central onde o aviso de página
     * não chega faria o carro sumir para sempre, sem o motorista saber por quê.
     */
    fun usarPaginaDoCarro(pagina: Int?) = gravar(_ajustes.value.copy(paginaDoCarro = pagina))

    /** O mesmo, para a janela do menu. Ver [usarPaginaDoCarro]. */
    fun usarPaginaDoMenu(pagina: Int?) = gravar(_ajustes.value.copy(paginaDoMenu = pagina))

    /** Encaixar a página no recorte redondo da bola, ou usar o painel inteiro. */
    fun encaixarMenuNaBola(sim: Boolean) = gravar(_ajustes.value.copy(menuNaBola = sim))

    /**
     * Empurra uma das janelas alguns dp a partir do lugar escolhido.
     *
     * Some com o lugar? Não: o lugar continua sendo o ponto de partida, e o
     * empurrão é sempre relativo a ele. Trocar de canto depois de ajustar o fio
     * mantém o fio, que é o que se espera de um "ajuste fino".
     */
    fun empurrar(janela: JanelaDoPainel, dx: Int, dy: Int) = gravar(
        when (janela) {
            JanelaDoPainel.NUMEROS ->
                _ajustes.value.copy(empurraoDosNumeros = _ajustes.value.empurraoDosNumeros.mais(dx, dy))
            JanelaDoPainel.CARRO ->
                _ajustes.value.copy(empurraoDoCarro = _ajustes.value.empurraoDoCarro.mais(dx, dy))
            JanelaDoPainel.MENU ->
                _ajustes.value.copy(empurraoDoMenu = _ajustes.value.empurraoDoMenu.mais(dx, dy))
        },
    )

    /**
     * Estica ou encolhe uma janela, em pontos percentuais sobre o tamanho
     * escolhido.
     *
     * Multiplica o tamanho pronto em vez de trocá-lo, pelo mesmo motivo do
     * [empurrar]: o tamanho continua sendo o ponto de partida, e trocar de
     * tamanho depois de esticar mantém o estica.
     */
    fun ampliar(janela: JanelaDoPainel, delta: Int) = gravar(
        when (janela) {
            JanelaDoPainel.NUMEROS ->
                _ajustes.value.copy(zoomDosNumeros = _ajustes.value.zoomDosNumeros.mais(delta))
            JanelaDoPainel.CARRO ->
                _ajustes.value.copy(zoomDoCarro = _ajustes.value.zoomDoCarro.mais(delta))
            JanelaDoPainel.MENU ->
                _ajustes.value.copy(zoomDoMenu = _ajustes.value.zoomDoMenu.mais(delta))
        },
    )

    /** Volta a janela ao tamanho escolhido, sem estica. */
    fun tamanhoNatural(janela: JanelaDoPainel) = gravar(
        when (janela) {
            JanelaDoPainel.NUMEROS -> _ajustes.value.copy(zoomDosNumeros = Zoom())
            JanelaDoPainel.CARRO -> _ajustes.value.copy(zoomDoCarro = Zoom())
            JanelaDoPainel.MENU -> _ajustes.value.copy(zoomDoMenu = Zoom())
        },
    )

    /** Desfaz o ajuste fino de uma janela. */
    fun centralizar(janela: JanelaDoPainel) = gravar(
        when (janela) {
            JanelaDoPainel.NUMEROS -> _ajustes.value.copy(empurraoDosNumeros = Empurrao())
            JanelaDoPainel.CARRO -> _ajustes.value.copy(empurraoDoCarro = Empurrao())
            JanelaDoPainel.MENU -> _ajustes.value.copy(empurraoDoMenu = Empurrao())
        },
    )

    /**
     * Põe o carro na bola do ar-condicionado: lugar e tamanho de uma vez, pelo
     * mesmo motivo de [usarFaixaDaNavegacao].
     */
    fun usarBolaDoAr() = gravar(
        _ajustes.value.copy(
            lugarDoCarro = LugarNoPainel.BOLA_DO_AC,
            tamanhoDoCarro = TamanhoDoCarro.BOLA_DO_AC,
        ),
    )

    /** Em que tela cada janela é projetada. `null` é "não projeta sozinha". */
    fun usarTela(janela: JanelaDoPainel, tela: Int?) = gravar(
        when (janela) {
            JanelaDoPainel.NUMEROS -> _ajustes.value.copy(telaDosNumeros = tela)
            JanelaDoPainel.CARRO -> _ajustes.value.copy(telaDoCarro = tela)
            JanelaDoPainel.MENU -> _ajustes.value.copy(telaDoMenu = tela)
        },
    )

    /**
     * As telas escolhidas, do jeito que o [ProjetorDoPainel] pede na partida.
     *
     * Uma função e não um valor: quem projeta corre num aviso do Shizuku que
     * pode chegar minutos depois, e tem de ler a escolha que vale naquela hora,
     * não a que valia quando o serviço subiu.
     */
    fun telasEscolhidas(): Map<JanelaDoPainel, Int?> = mapOf(
        JanelaDoPainel.NUMEROS to _ajustes.value.telaDosNumeros,
        JanelaDoPainel.CARRO to _ajustes.value.telaDoCarro,
        JanelaDoPainel.MENU to _ajustes.value.telaDoMenu,
    )
}
