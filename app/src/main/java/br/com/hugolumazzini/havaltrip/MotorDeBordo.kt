package br.com.hugolumazzini.havaltrip

import android.app.Application
import android.content.Context
import android.util.Log
import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo
import br.com.hugolumazzini.havaltrip.domain.TipoCombustivel
import br.com.hugolumazzini.havaltrip.engine.TripManager
import br.com.hugolumazzini.havaltrip.engine.TripState
import br.com.hugolumazzini.havaltrip.painel.JanelaDoPainel
import br.com.hugolumazzini.havaltrip.painel.ProjetorDoPainel
import br.com.hugolumazzini.havaltrip.storage.FileTripStorage
import br.com.hugolumazzini.havaltrip.telemetry.BancadaDeTestes
import br.com.hugolumazzini.havaltrip.telemetry.ColetaDeEnergia
import br.com.hugolumazzini.havaltrip.telemetry.DiarioDeCampo
import br.com.hugolumazzini.havaltrip.telemetry.EstadoDoCarro
import br.com.hugolumazzini.havaltrip.telemetry.HavalTelemetrySource
import br.com.hugolumazzini.havaltrip.telemetry.ShizukuTelemetrySource
import br.com.hugolumazzini.havaltrip.telemetry.SimulatedTelemetrySource
import br.com.hugolumazzini.havaltrip.telemetry.TelemetrySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * O que conta os quilômetros. Um só, vivo enquanto o processo estiver de pé.
 *
 * Ficava dentro do [TripViewModel], e por isso nascia e morria com a tela: o
 * app só contava enquanto estivesse aberto. Quem dirige não abre um aplicativo
 * antes de dar a partida — e a viagem que ele não viu é viagem perdida, que é o
 * único erro que um computador de bordo não pode cometer.
 *
 * Agora o motor é do processo, e o [ServicoDeBordo] é quem mantém o processo
 * de pé. A tela virou uma janela para ele: abrir e fechar a tela não interrompe
 * nem reinicia contagem nenhuma.
 *
 * Não é um `object` de arquivo por um motivo prático: precisa do `Application`
 * para achar a pasta de gravação e para falar com a central. Daí o [de].
 */
class MotorDeBordo private constructor(private val app: Application) {

    /**
     * Escopo próprio, com [SupervisorJob], porque não há mais dono de ciclo de
     * vida acima: a coleta não pode parar porque a tela saiu, e uma falha numa
     * fonte não pode derrubar as outras.
     */
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        odometroKm = manager.state.value.live.odometerTotalKm.takeIf { it > 0.0 } ?: 48_213.4,
        estado = estadoDoCarro,
    )

    private val carro = HavalTelemetrySource(context = app, estado = estadoDoCarro)

    private val linhaDireta = ShizukuTelemetrySource(estado = estadoDoCarro)

    val state: StateFlow<TripState> = manager.state

    /** A decisão da ignição com o critério que a tomou, para o diagnóstico. */
    fun vereditoDaIgnicao() = estadoDoCarro.vereditoDaIgnicao()

    /** Quando o carro disse algo diferente pela última vez. Ver `EstadoDoCarro`. */
    val ultimaMudancaMs: Long get() = estadoDoCarro.ultimaMudancaMs

    val painelDoVeiculo: StateFlow<PainelDoVeiculo> = diario.atual
        .map { estadoDoCarro.painelDoVeiculo() }
        .distinctUntilChanged()
        .stateIn(escopo, SharingStarted.Eagerly, PainelDoVeiculo())

    /** `true` se o veículo é PHEV (plug-in hybrid), `false` se for HEV puro ou ICE. */
    val isPHEV: StateFlow<Boolean> = diario.atual
        .map { estadoDoCarro.isPHEV() }
        .distinctUntilChanged()
        .stateIn(escopo, SharingStarted.Eagerly, false)

    val situacaoShizuku: StateFlow<ShizukuTelemetrySource.Situacao> = linhaDireta.situacao

    /**
     * A medição de energia elétrica, para quem tem PHEV. Desligada por padrão.
     *
     * Mora aqui, e não na tela, porque ela acompanha a ignição: quem vai rodar
     * a coleta é outra pessoa, no carro dela, e não se pede a alguém que abra
     * um aplicativo antes de dar a partida. Ver [ColetaDeEnergia].
     */
    val coletaDeEnergia = ColetaDeEnergia(app, escopo)

    private val _fonte = MutableStateFlow(Fonte.SIMULADOR)  // Começa com SIMULADOR, escolhe depois

    val fonte: StateFlow<Fonte> = _fonte.asStateFlow()

    val fonteReal: StateFlow<Boolean> = _fonte
        .map { it != Fonte.SIMULADOR }
        .stateIn(escopo, SharingStarted.Eagerly, false)

    private var escuta: Job? = null

    init {
        br.com.hugolumazzini.havaltrip.painel.HistoricoDaDespedida.iniciar(app)
        Log.i("MotorDeBordo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i("MotorDeBordo", "🚀 INICIANDO MOTOR DE BORDO")
        Log.i("MotorDeBordo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        val disponivel = ShizukuTelemetrySource.disponivel()
        val autorizado = ShizukuTelemetrySource.autorizado()
        Log.i("MotorDeBordo", "📡 Shizuku disponível: $disponivel")
        Log.i("MotorDeBordo", "🔑 Shizuku autorizado: $autorizado")
        Log.i("MotorDeBordo", "📦 SHISUKU instalado: $shisukuInstalado")

        // A chave do carro não gira sozinha quando o processo é recriado. Sem
        // isto, o simulador voltaria a "desligado" e o módulo veria um corte de
        // ignição que nunca houve — e, passados 5 min, zeraria a Viagem atual.
        simulador.ignicao = manager.state.value.live.ignition

        // Escolhe fonte inicial
        escolherFonteInicial()
        escutar()
        BancadaDeTestes.ligar(app, estadoDoCarro) { _fonte.value == Fonte.SIMULADOR }

        // WATCHDOG: Re-avalia fonte periodicamente para detectar quando Shizuku ficar disponível
        escopo.launch {
            delay(2000) // Aguarda 2s para sistema estabilizar no boot
            Log.i("MotorDeBordo", "🔍 Iniciando watchdog de re-avaliação de fonte (a cada 5s)")

            while (true) {
                delay(5000) // Verifica a cada 5 segundos
                reavaliarFonte()
            }
        }

        Log.i("MotorDeBordo", "✅ Motor de Bordo inicializado")
    }

    /**
     * Escolhe a fonte inicial de telemetria.
     *
     * PRIORIDADE:
     * 1. SHIZUKU (se disponível E autorizado)
     * 2. SHIZUKU (se disponível mas não autorizado - vai pedir)
     * 3. SHISUKU (fallback se Shizuku não disponível)
     * 4. SIMULADOR (se nada mais funcionar)
     */
    private fun escolherFonteInicial() {
        val nova = when {
            ShizukuTelemetrySource.disponivel() -> {
                Log.i("MotorDeBordo", "✅ Shizuku disponível - escolhendo SHIZUKU como fonte")
                Fonte.SHIZUKU
            }
            shisukuInstalado -> {
                Log.i("MotorDeBordo", "⚠️ Shizuku NÃO disponível - usando SHISUKU como fallback")
                Fonte.SHISUKU
            }
            else -> {
                Log.i("MotorDeBordo", "⚠️ Nem Shizuku nem SHISUKU disponíveis - usando SIMULADOR")
                Fonte.SIMULADOR
            }
        }

        Log.i("MotorDeBordo", "🎯 Fonte escolhida: $nova")
        _fonte.value = nova
    }

    /**
     * Re-avalia a fonte de telemetria periodicamente.
     *
     * Se Shizuku ficar disponível e não estamos usando ele, troca automaticamente!
     * Isso resolve o problema de Shizuku não estar pronto no boot.
     */
    private fun reavaliarFonte() {
        val fonteAtual = _fonte.value
        val disponivel = ShizukuTelemetrySource.disponivel()
        val autorizado = ShizukuTelemetrySource.autorizado()

        // Se Shizuku ficou disponível E não estamos usando ele, TROCA!
        if (disponivel && fonteAtual != Fonte.SHIZUKU) {
            Log.w("MotorDeBordo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.w("MotorDeBordo", "⚡ SHIZUKU FICOU DISPONÍVEL!")
            Log.w("MotorDeBordo", "   Fonte atual: $fonteAtual")
            Log.w("MotorDeBordo", "   Autorizado: $autorizado")
            Log.w("MotorDeBordo", "   Trocando para SHIZUKU...")
            Log.w("MotorDeBordo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            usarFonte(Fonte.SHIZUKU)
        }

        // Se Shizuku morreu e estamos usando ele, volta para fallback
        if (!disponivel && fonteAtual == Fonte.SHIZUKU) {
            Log.e("MotorDeBordo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.e("MotorDeBordo", "💀 SHIZUKU PAROU DE RESPONDER!")
            Log.e("MotorDeBordo", "   Voltando para fallback...")
            Log.e("MotorDeBordo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            val fallback = if (shisukuInstalado) Fonte.SHISUKU else Fonte.SIMULADOR
            usarFonte(fallback)
        }
    }

    private fun escutar() {
        escuta?.cancel()

        val fonteAtual = _fonte.value
        Log.i("MotorDeBordo", "👂 Iniciando escuta com fonte: $fonteAtual")

        val fonte: TelemetrySource = when (fonteAtual) {
            Fonte.SHIZUKU -> {
                Log.i("MotorDeBordo", "   Usando linhaDireta (ShizukuTelemetrySource)")
                linhaDireta
            }
            Fonte.SHISUKU -> {
                Log.i("MotorDeBordo", "   Usando carro (HavalTelemetrySource)")
                carro
            }
            Fonte.SIMULADOR -> {
                Log.i("MotorDeBordo", "   Usando simulador (SimulatedTelemetrySource)")
                simulador
            }
        }

        escuta = escopo.launch {
            var anterior = manager.state.value.live.ignition
            var contador = 0
            fonte.samples().collect { amostra ->
                contador++
                if (contador == 1) {
                    Log.i("MotorDeBordo", "📊 Primeira amostra recebida de $fonteAtual!")
                } else if (contador % 60 == 0) {
                    // Log a cada 60 amostras (~1 minuto)
                    Log.d("MotorDeBordo", "💚 $contador amostras recebidas de $fonteAtual")
                }

                if (amostra.ignition != anterior) {
                    Log.i("MotorDeBordo", "🔑 Mudança de ignição: $anterior → ${amostra.ignition}")
                    anterior = amostra.ignition
                    manager.handleIgnitionChange(amostra.ignition)
                    // A coleta de energia começa e termina com a chave, sem
                    // ninguém apertar nada. Ela mesma se cala se estiver
                    // desligada, que é o caso de todo mundo menos quem está
                    // medindo um PHEV.
                    if (amostra.ignition == IgnitionState.ON) {
                        coletaDeEnergia.comecar()
                        // DESATIVA despedida quando carro ligar de novo
                        br.com.hugolumazzini.havaltrip.painel.EstadoDaDespedida.desativar("ignição ligou")
                    } else {
                        br.com.hugolumazzini.havaltrip.painel.HistoricoDaDespedida.info("🔑 Ignição desligou (fonte: $fonteAtual)")
                        coletaDeEnergia.terminar()
                        // DESPEDIDA: quando o carro desligar, projeta o resumo em tela cheia
                        // usando ClusterOverlayService, que garante que cobre o painel inteiro
                        // mesmo que a janela configurada seja pequena (ex: bolinha).
                        acionarDespedida()
                    }
                }
                // Rede de segurança: a despedida fica por cima de tudo no painel
                // e não tem toque. Se o aviso de "ligou" se perder e o carro sair
                // andando, ela não pode ficar tapando o painel de quem dirige.
                if (amostra.speedKmh > VELOCIDADE_QUE_ENCERRA_A_DESPEDIDA_KMH &&
                    br.com.hugolumazzini.havaltrip.painel.EstadoDaDespedida.estaAtiva()
                ) {
                    br.com.hugolumazzini.havaltrip.painel.EstadoDaDespedida.desativar(
                        "carro em movimento (${"%.0f".format(amostra.speedKmh)} km/h)",
                    )
                }
                manager.processTelemetry(amostra)
            }
        }
    }

    /**
     * Projeta a despedida em tela cheia no painel quando o carro desligar.
     *
     * Usa ClusterOverlayService em vez da Activity normal porque:
     * 1. A janela configurada pode ser pequena (ex: bolinha do cluster)
     * 2. A despedida precisa cobrir o painel inteiro para ficar legível
     * 3. O overlay garante que fica por cima de tudo (Impulse, AutoPanel, etc.)
     */
    private fun acionarDespedida() {
        escopo.launch {
            // Aguarda um pouco para garantir que o estado está estável
            delay(500)

            val ajustes = Cluster.ajustes.value
            val trips = manager.state.value.trips
            val viagem = br.com.hugolumazzini.havaltrip.ui.Despedida.viagemQueAcabou(trips)

            // Só mostra se valer a pena (viagem andou pelo menos 0.001km)
            if (viagem != null && br.com.hugolumazzini.havaltrip.ui.Despedida.valeMostrar(viagem)) {
                // Determina qual janela deve mostrar (mesma lógica da ClusterScreen)
                val janela = br.com.hugolumazzini.havaltrip.ui.Despedida.janelaDoResumo(ajustes)

                // Display 3 = cluster do H6
                val displayId = 3

                Log.i("MotorDeBordo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.i("MotorDeBordo", "👋 ACIONANDO DESPEDIDA")
                Log.i("MotorDeBordo", "   Janela: $janela")
                Log.i("MotorDeBordo", "   Display: $displayId")
                Log.i("MotorDeBordo", "   Viagem: ${viagem.metrics.distanceKm}km")
                Log.i("MotorDeBordo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // MARCA DESPEDIDA COMO ATIVA ANTES de projetar
                // Isso previne que ClusterActivity.onDestroy() remova o overlay
                br.com.hugolumazzini.havaltrip.painel.EstadoDaDespedida.ativar("ignição desligou, viagem de ${"%.3f".format(viagem.metrics.distanceKm)} km")

                // Projeta despedida em tela cheia via overlay
                br.com.hugolumazzini.havaltrip.painel.ClusterOverlayService.projetarDespedida(
                    app,
                    janela,
                    displayId
                )

                Log.i("MotorDeBordo", "✅ Despedida projetada com sucesso")
            } else {
                Log.d("MotorDeBordo", "⏭️ Despedida não vale mostrar: viagem=${viagem?.metrics?.distanceKm}km")
                br.com.hugolumazzini.havaltrip.painel.HistoricoDaDespedida.info(
                    "⏭️ Ignição desligou, mas a despedida foi pulada: " +
                        if (viagem == null) "não há viagem automática"
                        else "viagem de ${"%.3f".format(viagem.metrics.distanceKm)} km (mínimo ${br.com.hugolumazzini.havaltrip.ui.Despedida.DISTANCIA_MINIMA_KM} km)",
                )
            }
        }
    }

    fun usarFonte(nova: Fonte) {
        if (_fonte.value == nova) {
            Log.d("MotorDeBordo", "   Fonte já é $nova, nada a fazer")
            return
        }

        Log.i("MotorDeBordo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i("MotorDeBordo", "🔄 MUDANÇA DE FONTE")
        Log.i("MotorDeBordo", "   De: ${_fonte.value}")
        Log.i("MotorDeBordo", "   Para: $nova")
        Log.i("MotorDeBordo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        _fonte.value = nova
        if (nova == Fonte.SIMULADOR) simulador.ignicao = state.value.live.ignition
        escutar()

        Log.i("MotorDeBordo", "✅ Fonte alterada com sucesso para $nova")
    }

    fun proximaFonte() {
        val todas = Fonte.entries
        usarFonte(todas[(todas.indexOf(_fonte.value) + 1) % todas.size])
    }

    fun alternarIgnicao() {
        if (_fonte.value != Fonte.SIMULADOR) return
        val novo = if (state.value.live.ignition == IgnitionState.ON) IgnitionState.OFF else IgnitionState.ON
        simulador.ignicao = novo
        manager.handleIgnitionChange(novo)
        // Também inicia/termina a coleta de energia quando a ignição muda
        if (novo == IgnitionState.ON) {
            coletaDeEnergia.comecar()
            // DESATIVA despedida quando carro ligar de novo
            br.com.hugolumazzini.havaltrip.painel.EstadoDaDespedida.desativar("ignição ligou (simulador)")
        } else {
            br.com.hugolumazzini.havaltrip.painel.HistoricoDaDespedida.info("🔑 Ignição desligou (simulador)")
            coletaDeEnergia.terminar()
            // Aciona despedida também no simulador
            acionarDespedida()
        }
    }

    fun selecionar(tripId: String) = manager.selectTrip(tripId)
    fun pausar(tripId: String) = manager.pauseTrip(tripId)
    fun retomar(tripId: String) = manager.resumeTrip(tripId)
    fun zerar(tripId: String) {
        // Se houver uma coleta de energia Pronta, inclui o kWh na Trip zerada
        val kwh = (coletaDeEnergia.estado.value as? ColetaDeEnergia.Estado.Pronta)?.resumo?.kwhIntegrado
        manager.resetTrip(tripId, kwh)
    }
    fun arquivar(tripId: String) = manager.saveToHistory(tripId)
    fun gravarAgora() = manager.flush()
    fun renomearRegistro(recordId: String, label: String) = manager.renameRecord(recordId, label)
    fun excluirRegistro(recordId: String) = manager.deleteRecord(recordId)
    fun atualizarPrecoRegistro(
        recordId: String,
        precoDolitro: Double,
        tipoCombustivel: TipoCombustivel? = null,
        precoKwh: Double? = null
    ) = manager.updateRecordPrice(recordId, precoDolitro, tipoCombustivel, precoKwh)
    fun definirContadoresManuais(quantos: Int) = manager.definirContadoresManuais(quantos)
    fun definirZeragemAutomatica(segundos: Double?) = manager.definirZeragemAutomatica(segundos)

    /** Reemite tudo; na linha direta é também o gatilho da autorização do Shizuku. */
    fun pedirTudoAoCarro() {
        HavalTelemetrySource.pedirTudo(app)
        if (_fonte.value == Fonte.SHIZUKU) escutar()
    }

    companion object {

        /** Acima disto o carro não está estacionado, e a despedida sai do painel. */
        private const val VELOCIDADE_QUE_ENCERRA_A_DESPEDIDA_KMH = 5.0

        @Volatile
        private var instancia: MotorDeBordo? = null

        /**
         * O motor deste processo, criando-o na primeira chamada.
         *
         * Único de propósito: duas instâncias contariam a mesma viagem duas
         * vezes e gravariam por cima uma da outra no mesmo arquivo. Como tanto
         * o serviço quanto a tela podem ser o primeiro a chegar — depende de a
         * central ter ligado sozinha ou de alguém ter tocado no ícone —, a
         * criação é sincronizada.
         */
        fun de(context: Context): MotorDeBordo {
            instancia?.let { return it }
            return synchronized(this) {
                instancia ?: MotorDeBordo(context.applicationContext as Application).also {
                    instancia = it
                }
            }
        }
    }
}
