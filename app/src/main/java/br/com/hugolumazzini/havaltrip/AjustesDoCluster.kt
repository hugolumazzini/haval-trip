package br.com.hugolumazzini.havaltrip

import android.content.Context
import br.com.hugolumazzini.havaltrip.domain.TripMetrics
import br.com.hugolumazzini.havaltrip.domain.VehicleLive
import br.com.hugolumazzini.havaltrip.format.TripFormat
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

/** Cor do texto no painel, das poucas que se leem bem sobre o fundo do carro. */
enum class CorDoCluster(val rotulo: String, val argb: Long) {
    BRANCO("Branco", 0xFFF5F5F5),
    AZUL("Azul", 0xFF4A9EFF),
    VERDE("Verde", 0xFF34C759),
    AMBAR("Âmbar", 0xFFFFB020),
    VERMELHO("Vermelho", 0xFFFF453A),
}

/**
 * Um dos nove cantos da janela.
 *
 * Existe para o motorista não ter de acertar posição com sliders de pixel na
 * tela do Impulse: lá ele dá ao app a tela inteira do painel (0,0 até
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
}

/**
 * Quanto da janela o conteúdo ocupa, em frações de largura e altura.
 *
 * Frações, e não pixels, porque o mesmo ajuste tem de servir tanto para quem
 * deu a tela inteira ao app quanto para quem deu um retângulo pequeno.
 */
enum class TamanhoNoPainel(val rotulo: String, val largura: Float, val altura: Float) {
    PEQUENO("Pequeno", 0.24f, 0.18f),
    MEDIO("Médio", 0.34f, 0.24f),
    GRANDE("Grande", 0.46f, 0.32f),
    FAIXA("Faixa larga", 0.70f, 0.20f),
    TUDO("A janela toda", 1f, 1f),
}

/** Quanto da janela o desenho do carro ocupa, em fração da altura. */
enum class TamanhoDoCarro(val rotulo: String, val fracao: Float) {
    PEQUENO("Pequeno", 0.35f),
    MEDIO("Médio", 0.55f),
    GRANDE("Grande", 0.80f),
    TUDO("A janela toda", 1f),
}

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
    val lugar: LugarNoPainel = LugarNoPainel.CIMA_CENTRO,
    val tamanho: TamanhoNoPainel = TamanhoNoPainel.FAIXA,
    val lugarDoCarro: LugarNoPainel = LugarNoPainel.MEIO_CENTRO,
    val tamanhoDoCarro: TamanhoDoCarro = TamanhoDoCarro.MEDIO,
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
    private const val LUGAR = "lugar"
    private const val TAMANHO = "tamanho"
    private const val LUGAR_CARRO = "lugarDoCarro"
    private const val TAMANHO_CARRO = "tamanhoDoCarro"

    private lateinit var prefs: android.content.SharedPreferences

    private val _ajustes = MutableStateFlow(AjustesDoCluster())
    val ajustes: StateFlow<AjustesDoCluster> = _ajustes.asStateFlow()

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
        )
    }

    private fun gravar(novo: AjustesDoCluster) {
        _ajustes.value = novo
        prefs.edit()
            .putString(TRIP, novo.tripId)
            .putString(ITENS, novo.itens.joinToString(",") { it.name })
            .putFloat(ESCALA, novo.escalaFonte)
            .putString(COR, novo.cor.name)
            .putString(LUGAR, novo.lugar.name)
            .putString(TAMANHO, novo.tamanho.name)
            .putString(LUGAR_CARRO, novo.lugarDoCarro.name)
            .putString(TAMANHO_CARRO, novo.tamanhoDoCarro.name)
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

    fun usarLugar(lugar: LugarNoPainel) = gravar(_ajustes.value.copy(lugar = lugar))

    fun usarTamanho(tamanho: TamanhoNoPainel) = gravar(_ajustes.value.copy(tamanho = tamanho))

    fun usarLugarDoCarro(lugar: LugarNoPainel) = gravar(_ajustes.value.copy(lugarDoCarro = lugar))

    fun usarTamanhoDoCarro(tamanho: TamanhoDoCarro) =
        gravar(_ajustes.value.copy(tamanhoDoCarro = tamanho))
}
