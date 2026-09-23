package br.com.hugolumazzini.havaltrip

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.hugolumazzini.havaltrip.atualizacao.Atualizacao
import br.com.hugolumazzini.havaltrip.atualizacao.VersaoPublicada
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo
import br.com.hugolumazzini.havaltrip.domain.TipoCombustivel
import br.com.hugolumazzini.havaltrip.domain.TripRecord
import br.com.hugolumazzini.havaltrip.engine.TripState
import br.com.hugolumazzini.havaltrip.services.TripComparison
import br.com.hugolumazzini.havaltrip.services.TripComparisonResult
import br.com.hugolumazzini.havaltrip.telemetry.ColetaDeEnergia
import br.com.hugolumazzini.havaltrip.telemetry.GravadorDeMudancas
import br.com.hugolumazzini.havaltrip.telemetry.HavalTelemetrySource
import br.com.hugolumazzini.havaltrip.telemetry.IdentidadeDoCarro
import br.com.hugolumazzini.havaltrip.telemetry.ImagensDaCentral
import br.com.hugolumazzini.havaltrip.telemetry.Relatorio
import br.com.hugolumazzini.havaltrip.telemetry.ShizukuTelemetrySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Qual tela está em foco. Navegação simples: são cinco, e nenhuma aninha. */
sealed interface Tela {
    data object Painel : Tela
    data class Detalhes(val tripId: String) : Tela
    data object Historico : Tela
    data object Diagnostico : Tela
    data object Configuracao : Tela
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
 * Em que pé está o gravador de mudanças do Diagnóstico.
 *
 * Três estados porque o gesto acontece **entre** dois toques: marcar, ir lá
 * fazer a coisa no carro, voltar e comparar. Ver [GravadorDeMudancas].
 */
sealed interface Gravador {
    data object Parado : Gravador
    data class Marcado(val quantas: Int) : Gravador
    data class Comparado(val mudancas: List<GravadorDeMudancas.Mudanca>) : Gravador
}

/** Em que pé está a busca por uma versão nova do app. */
sealed interface Atualizador {
    data object Parado : Atualizador
    data object Procurando : Atualizador

    /** O catálogo respondeu e não há nada novo. */
    data class EmDia(val versao: String) : Atualizador

    /** Existe versão nova, esperando a decisão de baixar. */
    data class Disponivel(val versao: VersaoPublicada) : Atualizador

    /** [progresso] vai de 0 a 1, ou é `null` quando o servidor não diz o tamanho. */
    data class Baixando(val versao: VersaoPublicada, val progresso: Float?) : Atualizador

    /** Baixado e conferido: o instalador do sistema está com ele. */
    data class Instalando(val versao: VersaoPublicada) : Atualizador

    data class Falhou(val motivo: String) : Atualizador
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

    /**
     * Quem realmente conta. O ViewModel não guarda mais estado de viagem: ele
     * nasce e morre com a tela, e a contagem não pode.
     */
    private val motor = MotorDeBordo.de(app)

    val diario get() = motor.diario
    val shisukuInstalado get() = motor.shisukuInstalado
    val painelDoVeiculo: StateFlow<PainelDoVeiculo> get() = motor.painelDoVeiculo
    val situacaoShizuku: StateFlow<ShizukuTelemetrySource.Situacao> get() = motor.situacaoShizuku
    val fonte: StateFlow<Fonte> get() = motor.fonte
    val fonteReal: StateFlow<Boolean> get() = motor.fonteReal
    val state: StateFlow<TripState> get() = motor.state

    private val _envio = MutableStateFlow<Envio>(Envio.Parado)
    val envio: StateFlow<Envio> = _envio.asStateFlow()

    private val _gravador = MutableStateFlow<Gravador>(Gravador.Parado)
    val gravador: StateFlow<Gravador> = _gravador.asStateFlow()

    /** O retrato tirado no "marcar", guardado até o "comparar". */
    private var retratoAnterior: GravadorDeMudancas.Retrato? = null

    private val _tela = MutableStateFlow<Tela>(Tela.Painel)
    val tela: StateFlow<Tela> = _tela.asStateFlow()

    private val _modoHistorico = MutableStateFlow<ModoHistorico>(ModoHistorico.Vendo())
    val modoHistorico: StateFlow<ModoHistorico> = _modoHistorico.asStateFlow()

    fun usarFonte(nova: Fonte) = motor.usarFonte(nova)

    /** Passa para a próxima fonte. Um botão só, porque a barra é estreita. */
    fun proximaFonte() = motor.proximaFonte()

    /** Só faz sentido no simulador; no carro quem gira a chave é a chave. */
    fun alternarIgnicao() = motor.alternarIgnicao()

    // ------------------------------------------------------------- Trips

    fun selecionar(tripId: String) = motor.selecionar(tripId)

    fun pausar(tripId: String) = motor.pausar(tripId)

    fun retomar(tripId: String) = motor.retomar(tripId)

    fun zerar(tripId: String) = motor.zerar(tripId)

    fun arquivar(tripId: String) {
        motor.arquivar(tripId)
    }

    /** Grava antes de a central cortar energia. Chamado pelo `onStop` da tela. */
    fun gravarAgora() = motor.gravarAgora()

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

    fun abrirConfiguracao() { _tela.value = Tela.Configuracao }

    // ------------------------------------------------------------- ajustes

    fun definirContadoresManuais(quantos: Int) = motor.definirContadoresManuais(quantos)

    fun definirZeragemAutomatica(segundos: Double?) = motor.definirZeragemAutomatica(segundos)

    // ------------------------------------------------------------- atualização

    /** Nome e código da versão instalada, para a tela de configuração. */
    val versaoInstalada: Pair<String, Long> = Atualizacao.versaoInstalada(app)

    private val _atualizador = MutableStateFlow<Atualizador>(Atualizador.Parado)
    val atualizador: StateFlow<Atualizador> = _atualizador.asStateFlow()

    /** Pergunta ao catálogo da loja se existe versão mais nova que a instalada. */
    fun procurarAtualizacao() {
        if (_atualizador.value is Atualizador.Procurando) return
        _atualizador.value = Atualizador.Procurando
        viewModelScope.launch {
            _atualizador.value = runCatching { Atualizacao.consultar(getApplication()) }.fold(
                onSuccess = { publicada ->
                    if (publicada.versionCode > versaoInstalada.second) {
                        Atualizador.Disponivel(publicada)
                    } else {
                        Atualizador.EmDia(versaoInstalada.first)
                    }
                },
                onFailure = { Atualizador.Falhou(it.message ?: it::class.java.simpleName) },
            )
        }
    }

    /**
     * Baixa a versão nova e entrega ao instalador do sistema.
     *
     * Dando certo, este processo morre no meio da instalação — por isso não há
     * estado de "pronto": o próximo estado é o app já sendo o novo.
     */
    fun baixarEInstalar(versao: VersaoPublicada) {
        if (_atualizador.value is Atualizador.Baixando) return
        _atualizador.value = Atualizador.Baixando(versao, 0f)
        viewModelScope.launch {
            _atualizador.value = runCatching {
                val apk = Atualizacao.baixar(getApplication(), versao) { fracao ->
                    _atualizador.value = Atualizador.Baixando(versao, fracao.takeIf { it >= 0f })
                }
                Atualizacao.instalar(getApplication(), apk)
                Atualizador.Instalando(versao)
            }.getOrElse { Atualizador.Falhou(it.message ?: it::class.java.simpleName) }
        }
    }

    // ------------------------------------------------------------- diagnóstico

    /**
     * Reemite tudo, seja qual for a fonte.
     *
     * Na linha direta isso também é o gatilho do pedido de autorização do
     * Shizuku, que é o que destrava a leitura na primeira vez.
     */
    fun pedirTudoAoCarro() = motor.pedirTudoAoCarro()

    fun vereditoDaIgnicao() = motor.vereditoDaIgnicao()

    val ultimaMudancaMs: Long get() = motor.ultimaMudancaMs

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
            // Extrai as mudanças do gravador se houver
            val mudancas = (_gravador.value as? Gravador.Comparado)?.mudancas ?: emptyList()

            // Duas versões do mesmo relatório: a gravada leva a fita inteira,
            // porque no aparelho não há limite de tamanho, e a enviada leva só
            // o que os sites de paste aceitam. Se o envio falhar, o arquivo
            // completo continua no carro para ser puxado depois.
            fun montar(maxEventos: Int) = Relatorio.montar(
                diario = diario,
                estado = state.value,
                fonte = fonte.value,
                shisuku = shisukuInstalado,
                maxEventos = maxEventos,
                mudancasDoGravador = mudancas,
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

    /** O estado da medição de energia, para a tela de diagnóstico. */
    val coletaDeEnergia: StateFlow<ColetaDeEnergia.Estado> = motor.coletaDeEnergia.estado

    /** `true` se a medição de energia está armada. Ver [ColetaDeEnergia]. */
    val coletaLigada: Boolean get() = motor.coletaDeEnergia.ligada

    fun ligarColetaDeEnergia(valor: Boolean) = motor.coletaDeEnergia.ligar(valor)

    /**
     * Manda a coleta fechada pelo mesmo link público dos outros relatórios.
     *
     * O texto leva as contas prontas e a fita rareada para uma amostra por
     * segundo: a integração já aconteceu dentro do carro, a quatro por segundo,
     * e mandar tudo só estouraria o tamanho do envio.
     */
    fun enviarColetaDeEnergia() {
        if (_envio.value is Envio.Enviando) return
        _envio.value = Envio.Enviando
        viewModelScope.launch {
            val contexto = getApplication<Application>()
            val texto = withContext(Dispatchers.IO) { motor.coletaDeEnergia.relato() }
            val arquivo = runCatching { Relatorio.salvar(contexto, texto) }.getOrNull()
            _envio.value = Relatorio.enviar(texto).fold(
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

    /**
     * Tira o retrato "antes" das propriedades suspeitas de carregar avisos.
     *
     * Depois disto o motorista vai fazer o gesto no carro — tirar a chave,
     * abrir a porta — e voltar para apertar [compararMudancas].
     */
    fun marcarMudancas() {
        viewModelScope.launch {
            val retrato = withContext(Dispatchers.IO) { GravadorDeMudancas.retratar() }
            retratoAnterior = retrato
            _gravador.value = Gravador.Marcado(retrato.valores.size)
        }
    }

    /** Tira o retrato "depois" e mostra o que mudou. */
    fun compararMudancas() {
        val antes = retratoAnterior ?: return
        viewModelScope.launch {
            val depois = withContext(Dispatchers.IO) { GravadorDeMudancas.retratar() }
            _gravador.value = Gravador.Comparado(GravadorDeMudancas.comparar(antes, depois))
        }
    }

    /** Descarta o retrato e some com a lista, para começar outro gesto. */
    fun esquecerMudancas() {
        retratoAnterior = null
        _gravador.value = Gravador.Parado
    }

    /**
     * Pergunta à central se ela sabe dizer que carro é este, e manda o que achou.
     *
     * Mesmo formato do inventário de imagens, e pela mesma razão: é uma
     * pergunta de uma vez só. A resposta decide se dá para escolher o desenho
     * pelo modelo sozinho ou se o motorista vai ter de escolher na mão. Ver
     * [IdentidadeDoCarro].
     */
    fun enviarSondaDeIdentidade() {
        if (_envio.value is Envio.Enviando) return
        _envio.value = Envio.Enviando
        viewModelScope.launch {
            val contexto = getApplication<Application>()
            val texto = withContext(Dispatchers.IO) {
                buildString {
                    runCatching { append(IdentidadeDoCarro.relato(contexto)) }
                        .onFailure { appendLine("a sonda falhou: ${it.javaClass.name}: ${it.message}") }
                }
            }
            val arquivo = runCatching { Relatorio.salvar(contexto, texto) }.getOrNull()
            _envio.value = Relatorio.enviar(texto).fold(
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

    /**
     * Levanta o que a central guarda de arte do veículo e manda pelo mesmo link.
     *
     * Botão separado do relatório de telemetria, e não uma seção a mais nele,
     * porque são perguntas de prazos diferentes: a telemetria se coleta a cada
     * viagem, o inventário se coleta **uma vez** e a resposta vale para sempre.
     * Misturar os dois engordaria todo diagnóstico futuro com uma lista que já
     * foi lida. Ver [ImagensDaCentral].
     */
    fun enviarInventarioDeImagens() {
        if (_envio.value is Envio.Enviando) return
        _envio.value = Envio.Enviando
        viewModelScope.launch {
            val contexto = getApplication<Application>()
            // Fora da thread da tela: abrir um APK de sistema e medir centenas
            // de imagens leva segundos, e travar o painel enquanto isso, dentro
            // do carro, pareceria o app ter morrido.
            val texto = withContext(Dispatchers.IO) {
                buildString {
                    appendLine("=== HAVAL TRIP — inventário de imagens da central ===")
                    appendLine("Central: ${Build.MANUFACTURER} ${Build.MODEL} — Android ${Build.VERSION.RELEASE}")
                    appendLine()
                    // Se a leitura falhar, o que interessa é justamente o
                    // motivo: um relatório que chega dizendo "faltou memória"
                    // resolve o problema, e um app que fecha na cara de quem
                    // está na garagem não diz nada e ainda perde a viagem.
                    // Por isso `Throwable`, e não `Exception`.
                    runCatching { append(ImagensDaCentral.inventario(contexto)) }
                        .onFailure { appendLine("a leitura falhou: ${it.javaClass.name}: ${it.message}") }
                }
            }
            val arquivo = runCatching { Relatorio.salvar(contexto, texto) }.getOrNull()
            _envio.value = Relatorio.enviar(texto).fold(
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
        motor.renomearRegistro(recordId, label)
    }

    /** Exclui e volta para a lista: o painel de detalhes ficaria órfão. */
    fun excluirRegistro(recordId: String) {
        motor.excluirRegistro(recordId)
        _modoHistorico.value = ModoHistorico.Vendo()
    }

    fun atualizarPrecoRegistro(recordId: String, precoDolitro: Double, tipoCombustivel: TipoCombustivel? = null) {
        motor.atualizarPrecoRegistro(recordId, precoDolitro, tipoCombustivel)
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
