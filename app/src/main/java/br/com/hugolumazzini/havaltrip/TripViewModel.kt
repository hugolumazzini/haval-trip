package br.com.hugolumazzini.havaltrip

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo
import br.com.hugolumazzini.havaltrip.domain.TripRecord
import br.com.hugolumazzini.havaltrip.engine.TripManager
import br.com.hugolumazzini.havaltrip.engine.TripState
import br.com.hugolumazzini.havaltrip.services.TripComparison
import br.com.hugolumazzini.havaltrip.services.TripComparisonResult
import br.com.hugolumazzini.havaltrip.storage.FileTripStorage
import br.com.hugolumazzini.havaltrip.telemetry.DiarioDeCampo
import br.com.hugolumazzini.havaltrip.telemetry.EstadoDoCarro
import br.com.hugolumazzini.havaltrip.telemetry.HavalTelemetrySource
import br.com.hugolumazzini.havaltrip.telemetry.Relatorio
import br.com.hugolumazzini.havaltrip.telemetry.ShizukuTelemetrySource
import br.com.hugolumazzini.havaltrip.telemetry.SimulatedTelemetrySource
import br.com.hugolumazzini.havaltrip.telemetry.TelemetrySource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Qual tela está em foco. Navegação simples: são quatro, e nenhuma aninha. */
sealed interface Tela {
    data object Painel : Tela
    data class Detalhes(val tripId: String) : Tela
    data object Historico : Tela
    data object Diagnostico : Tela
}

/**
 * De onde vêm os números.
 *
 * São três porque as três falham por motivos diferentes, e o motorista precisa
 * poder trocar sem sair do carro. A [SHIZUKU] é a linha direta com o serviço da
 * central e a única em que **nós** escolhemos as chaves; a [SHISUKU] depende de
 * um app intermediário e da lista que ele resolve monitorar; o [SIMULADOR]
 * inventa tudo, e existe para a bancada.
 */
enum class Fonte(val rotulo: String) {
    SHIZUKU("carro (direto)"),
    SHISUKU("carro (via Shisuku)"),
    SIMULADOR("simulador"),
}

/** Em que pé está o envio do relatório de diagnóstico. */
sealed interface Envio {
    data object Parado : Envio
    data object Enviando : Envio
    data class Pronto(val endereco: String) : Envio
    data class Falhou(val motivo: String, val arquivo: String) : Envio
}

/**
 * O que a tela de histórico está fazendo agora.
 *
 * Ver uma viagem é o estado normal; comparar é um desvio com começo e fim. São
 * dois modos e não um só com "seleção de zero a dois" porque um toque na lista
 * precisa significar coisas diferentes em cada um — abrir, ou escolher o par.
 */
sealed interface ModoHistorico {
    /** Lendo uma viagem (ou nenhuma, quando `recordId` é `null`). */
    data class Vendo(val recordId: String? = null) : ModoHistorico

    /** Comparando: [aId] já escolhida, esperando [bId]. */
    data class Comparando(val aId: String, val bId: String? = null) : ModoHistorico
}

/**
 * Cola entre a interface e o núcleo. Não calcula nada: encaminha comandos para
 * o [TripManager] e repassa o estado que ele publica.
 */
class TripViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = TripManager(
        storage = FileTripStorage(File(app.filesDir, "trip")),
    )

    /** Tudo que chegou cru do carro, para a tela de diagnóstico e o relatório. */
    val diario = DiarioDeCampo()

    /** `true` quando existe a ponte do HavalShisuku nesta central. */
    val shisukuInstalado = HavalTelemetrySource.shisukuInstalado(app)

    /** O último valor de cada chave, compartilhado por todas as fontes. */
    private val estadoDoCarro = EstadoDoCarro(diario)

    private val simulador = SimulatedTelemetrySource(
        // O simulador continua de onde o snapshot parou. Um hodômetro que volta
        // ao valor de fábrica a cada abertura do app deixaria a tela de
        // detalhes dizendo que a Trip andou 3 km sem o hodômetro sair do lugar.
        odometroKm = manager.state.value.live.odometerTotalKm.takeIf { it > 0.0 } ?: 48_213.4,
        estado = estadoDoCarro,
    )

    private val carro = HavalTelemetrySource(context = app, estado = estadoDoCarro)

    private val linhaDireta = ShizukuTelemetrySource(estado = estadoDoCarro)

    /**
     * Portas, cintos e pneus, recalculados a cada valor que chega.
     *
     * Sai do diário e não do gerenciador de viagens porque não é conta: é o
     * estado físico do carro, que a tela mostra e ninguém integra. O
     * [distinctUntilChanged] é o que segura a recomposição — o carro publica
     * dezenas de valores por segundo e quase nenhum deles mexe numa porta.
     */
    val painelDoVeiculo: StateFlow<PainelDoVeiculo> = diario.atual
        .map { estadoDoCarro.painelDoVeiculo() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, PainelDoVeiculo())

    /** Como está a linha direta, para a tela dizer o que falta fazer. */
    val situacaoShizuku: StateFlow<ShizukuTelemetrySource.Situacao> = linhaDireta.situacao

    // A preferida é a linha direta, e não por elegância: é a única em que a
    // lista de chaves é nossa. Pela ponte do Shisuku, tanque, autonomia e
    // consumo médio só chegam se alguém marcar caixinhas lá dentro.
    private val _fonte = MutableStateFlow(
        when {
            ShizukuTelemetrySource.disponivel() -> Fonte.SHIZUKU
            shisukuInstalado -> Fonte.SHISUKU
            else -> Fonte.SIMULADOR
        }
    )

    val fonte: StateFlow<Fonte> = _fonte.asStateFlow()

    /** `true` quando os números vêm do carro, e não do simulador. */
    val fonteReal: StateFlow<Boolean> = _fonte
        .map { it != Fonte.SIMULADOR }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _fonte.value != Fonte.SIMULADOR)

    private var escuta: Job? = null

    val state: StateFlow<TripState> = manager.state

    private val _envio = MutableStateFlow<Envio>(Envio.Parado)
    val envio: StateFlow<Envio> = _envio.asStateFlow()

    private val _tela = MutableStateFlow<Tela>(Tela.Painel)
    val tela: StateFlow<Tela> = _tela.asStateFlow()

    private val _modoHistorico = MutableStateFlow<ModoHistorico>(ModoHistorico.Vendo())
    val modoHistorico: StateFlow<ModoHistorico> = _modoHistorico.asStateFlow()

    init {
        // A chave do carro não gira sozinha quando o app é recriado. Sem isto,
        // o simulador voltaria a "desligado" e o módulo veria um corte de
        // ignição que nunca houve — e, passados 5 min, zeraria a Viagem atual
        // por causa de uma parada que só existiu na memória do aplicativo.
        simulador.ignicao = manager.state.value.live.ignition
        escutar()
    }

    private fun escutar() {
        escuta?.cancel()
        val fonte: TelemetrySource = when (_fonte.value) {
            Fonte.SHIZUKU -> linhaDireta
            Fonte.SHISUKU -> carro
            Fonte.SIMULADOR -> simulador
        }
        escuta = viewModelScope.launch {
            var anterior = manager.state.value.live.ignition
            fonte.samples().collect { amostra ->
                // Com a fonte real, a virada da chave chega dentro da amostra.
                // O gerenciador precisa dela pelo caminho próprio: é o que
                // arquiva a viagem e carimba o horário do desligamento.
                if (amostra.ignition != anterior) {
                    anterior = amostra.ignition
                    manager.handleIgnitionChange(amostra.ignition)
                }
                manager.processTelemetry(amostra)
            }
        }
    }

    /**
     * Troca entre o carro e o simulador.
     *
     * Existe porque a central pode ter o HavalShisuku instalado e mesmo assim
     * não haver carro dizendo nada — numa bancada, ou com a ignição desligada.
     * A escolha é manual de propósito: um fallback automático transformaria
     * "o carro está parado" em números inventados, que é o pior erro possível
     * num aparelho que serve para medir.
     */
    fun usarFonte(nova: Fonte) {
        if (_fonte.value == nova) return
        _fonte.value = nova
        // Entrando no simulador, ele parte da ignição que está valendo. Sem
        // isso o módulo veria um corte de chave que nunca houve.
        if (nova == Fonte.SIMULADOR) simulador.ignicao = state.value.live.ignition
        escutar()
    }

    /** Passa para a próxima fonte. Um botão só, porque a barra é estreita. */
    fun proximaFonte() {
        val todas = Fonte.entries
        usarFonte(todas[(todas.indexOf(_fonte.value) + 1) % todas.size])
    }

    // ------------------------------------------------------------- ignição

    /** Só faz sentido no simulador; no carro quem gira a chave é a chave. */
    fun alternarIgnicao() {
        if (_fonte.value != Fonte.SIMULADOR) return
        val novo = if (state.value.live.ignition == IgnitionState.ON) IgnitionState.OFF else IgnitionState.ON
        simulador.ignicao = novo
        manager.handleIgnitionChange(novo)
    }

    // ------------------------------------------------------------- Trips

    fun selecionar(tripId: String) = manager.selectTrip(tripId)

    fun pausar(tripId: String) = manager.pauseTrip(tripId)

    fun retomar(tripId: String) = manager.resumeTrip(tripId)

    fun zerar(tripId: String) = manager.resetTrip(tripId)

    fun arquivar(tripId: String) {
        manager.saveToHistory(tripId)
    }

    /** Grava antes de a central cortar energia. Chamado pelo `onStop` da tela. */
    fun gravarAgora() = manager.flush()

    // ------------------------------------------------------------- navegação

    fun abrirDetalhes(tripId: String) { _tela.value = Tela.Detalhes(tripId) }

    fun abrirHistorico() {
        // Entra sempre limpo. Voltar ao histórico e reencontrar uma comparação
        // pela metade, montada minutos antes, é confuso sem nenhum ganho.
        _modoHistorico.value = ModoHistorico.Vendo()
        _tela.value = Tela.Historico
    }

    fun voltarAoPainel() { _tela.value = Tela.Painel }

    fun abrirDiagnostico() {
        // Pede ao Shisuku que reemita tudo ao abrir: assim a tela já nasce
        // preenchida, em vez de esperar cada valor mudar sozinho.
        HavalTelemetrySource.pedirTudo(getApplication())
        _envio.value = Envio.Parado
        _tela.value = Tela.Diagnostico
    }

    // ------------------------------------------------------------- diagnóstico

    /**
     * Reemite tudo, seja qual for a fonte.
     *
     * Na linha direta isso também é o gatilho do pedido de autorização do
     * Shizuku, que é o que destrava a leitura na primeira vez.
     */
    fun pedirTudoAoCarro() {
        HavalTelemetrySource.pedirTudo(getApplication())
        if (_fonte.value == Fonte.SHIZUKU) escutar()
    }

    /** Todo problema de dado se resolve lá, não aqui. */
    fun abrirShisuku() = HavalTelemetrySource.abrirShisuku(getApplication())


    /**
     * Grava o relatório e sobe para um endereço público de leitura.
     *
     * A gravação vem primeiro de propósito: dentro do carro pode não haver
     * internet, e perder a coleta de uma viagem inteira porque o Wi-Fi não
     * pegou seria o pior desfecho possível para um teste que exige dirigir.
     */
    fun enviarRelatorio() {
        if (_envio.value is Envio.Enviando) return
        _envio.value = Envio.Enviando
        viewModelScope.launch {
            // Duas versões do mesmo relatório: a gravada leva a fita inteira,
            // porque no aparelho não há limite de tamanho, e a enviada leva só
            // o que os sites de paste aceitam. Se o envio falhar, o arquivo
            // completo continua no carro para ser puxado depois.
            fun montar(maxEventos: Int) = Relatorio.montar(
                diario = diario,
                estado = state.value,
                fonte = _fonte.value,
                shisuku = shisukuInstalado,
                maxEventos = maxEventos,
            )
            val completo = montar(Int.MAX_VALUE)
            val arquivo = runCatching { Relatorio.salvar(getApplication(), completo) }.getOrNull()
            _envio.value = Relatorio.enviar(montar(Relatorio.MAX_EVENTOS_ENVIADOS)).fold(
                onSuccess = { Envio.Pronto(it) },
                onFailure = {
                    Envio.Falhou(
                        motivo = it.message ?: it::class.java.simpleName,
                        arquivo = arquivo?.absolutePath ?: "não foi possível gravar",
                    )
                },
            )
        }
    }

    // ------------------------------------------------------------- histórico

    /** Um toque na lista: abre a viagem, ou escolhe o par se estiver comparando. */
    fun tocarNoRegistro(recordId: String) {
        _modoHistorico.value = when (val modo = _modoHistorico.value) {
            is ModoHistorico.Vendo -> ModoHistorico.Vendo(recordId)
            // Tocar de novo na primeira desfaz a escolha em vez de comparar a
            // viagem com ela mesma, que não diria nada.
            is ModoHistorico.Comparando ->
                if (recordId == modo.aId) modo.copy(bId = null) else modo.copy(bId = recordId)
        }
    }

    /** Sai da leitura de uma viagem e entra no modo de comparar, com ela já escolhida. */
    fun compararComOutra(recordId: String) {
        _modoHistorico.value = ModoHistorico.Comparando(aId = recordId)
    }

    /** Volta da comparação para a leitura da viagem que a originou. */
    fun sairDaComparacao() {
        val modo = _modoHistorico.value
        _modoHistorico.value =
            ModoHistorico.Vendo((modo as? ModoHistorico.Comparando)?.aId)
    }

    fun renomearRegistro(recordId: String, label: String) {
        manager.renameRecord(recordId, label)
    }

    /** Exclui e volta para a lista: o painel de detalhes ficaria órfão. */
    fun excluirRegistro(recordId: String) {
        manager.deleteRecord(recordId)
        _modoHistorico.value = ModoHistorico.Vendo()
    }

    /**
     * A comparação pronta, ou `null` enquanto não houver duas escolhidas.
     *
     * Recebe modo e histórico por parâmetro em vez de lê-los de dentro: a tela
     * já observa os dois, e um getter que os lesse por fora do Compose não
     * dispararia recomposição quando mudassem.
     */
    fun comparar(modo: ModoHistorico, historico: List<TripRecord>): TripComparisonResult? {
        if (modo !is ModoHistorico.Comparando) return null
        val a = historico.firstOrNull { it.recordId == modo.aId } ?: return null
        val b = historico.firstOrNull { it.recordId == modo.bId } ?: return null
        return TripComparison.compare(a, b)
    }

    /** O registro em foco, seja o que está sendo lido ou o lado 1 da comparação. */
    fun registroEmFoco(modo: ModoHistorico, historico: List<TripRecord>): TripRecord? {
        val id = when (modo) {
            is ModoHistorico.Vendo -> modo.recordId
            is ModoHistorico.Comparando -> modo.aId
        } ?: return null
        return historico.firstOrNull { it.recordId == id }
    }
}
