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

    private val _fonte = MutableStateFlow(
        when {
            // PRIORIDADE 1: HavalShisuku service (mais confiável, usado pelo Impulse)
            shisukuInstalado -> Fonte.SHISUKU
            // PRIORIDADE 2: Linha direta via Shizuku (fallback)
            ShizukuTelemetrySource.disponivel() -> Fonte.SHIZUKU
            // PRIORIDADE 3: Simulador (emulador/teste)
            else -> Fonte.SIMULADOR
        }
    )

    val fonte: StateFlow<Fonte> = _fonte.asStateFlow()

    val fonteReal: StateFlow<Boolean> = _fonte
        .map { it != Fonte.SIMULADOR }
        .stateIn(escopo, SharingStarted.Eagerly, _fonte.value != Fonte.SIMULADOR)

    private var escuta: Job? = null

    init {
        // A chave do carro não gira sozinha quando o processo é recriado. Sem
        // isto, o simulador voltaria a "desligado" e o módulo veria um corte de
        // ignição que nunca houve — e, passados 5 min, zeraria a Viagem atual.
        simulador.ignicao = manager.state.value.live.ignition
        escutar()
        BancadaDeTestes.ligar(app, estadoDoCarro) { _fonte.value == Fonte.SIMULADOR }
    }

    private fun escutar() {
        escuta?.cancel()
        val fonte: TelemetrySource = when (_fonte.value) {
            Fonte.SHIZUKU -> linhaDireta
            Fonte.SHISUKU -> carro
            Fonte.SIMULADOR -> simulador
        }
        escuta = escopo.launch {
            var anterior = manager.state.value.live.ignition
            fonte.samples().collect { amostra ->
                if (amostra.ignition != anterior) {
                    anterior = amostra.ignition
                    manager.handleIgnitionChange(amostra.ignition)
                    // A coleta de energia começa e termina com a chave, sem
                    // ninguém apertar nada. Ela mesma se cala se estiver
                    // desligada, que é o caso de todo mundo menos quem está
                    // medindo um PHEV.
                    if (amostra.ignition == IgnitionState.ON) {
                        coletaDeEnergia.comecar()
                    } else {
                        coletaDeEnergia.terminar()
                        // PRIORIDADE TOTAL DA DESPEDIDA: ao desligar, força a janela
                        // de despedida para a frente IMEDIATAMENTE, sobrepondo qualquer
                        // outra janela (incluindo do Impulse)
                        forcarDespedidaNaFrente()
                    }
                }
                manager.processTelemetry(amostra)
            }
        }
    }

    fun usarFonte(nova: Fonte) {
        if (_fonte.value == nova) return
        _fonte.value = nova
        if (nova == Fonte.SIMULADOR) simulador.ignicao = state.value.live.ignition
        escutar()
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
        } else {
            coletaDeEnergia.terminar()
            // PRIORIDADE TOTAL DA DESPEDIDA ao desligar
            forcarDespedidaNaFrente()
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

    /**
     * Garante que a despedida apareça ao desligar o carro com PRIORIDADE TOTAL.
     *
     * Usa ClusterOverlayService (técnica do haval-app-tool-multimidia) para:
     * 1. Remover overlays de outros apps (Impulse, AutoPanel)
     * 2. Projetar despedida via WindowManager overlay
     * 3. Garantir visibilidade absoluta
     */
    private fun forcarDespedidaNaFrente() {
        escopo.launch(Dispatchers.Default) {
            try {
                Log.i("MotorDeBordo", "🚗 Ignição OFF - DESPEDIDA COM PRIORIDADE TOTAL")

                val ajustes = Cluster.ajustes.value
                Log.d("MotorDeBordo", "Telas: numeros=${ajustes.telaDosNumeros}, menu=${ajustes.telaDoMenu}, carro=${ajustes.telaDoCarro}")

                // Determina qual janela vai mostrar a despedida
                val janelaComDespedida = when {
                    ajustes.telaDosNumeros != null -> JanelaDoPainel.NUMEROS to ajustes.telaDosNumeros
                    ajustes.telaDoMenu != null -> JanelaDoPainel.MENU to ajustes.telaDoMenu
                    ajustes.telaDoCarro != null -> JanelaDoPainel.CARRO to ajustes.telaDoCarro
                    else -> null
                }

                if (janelaComDespedida == null) {
                    Log.w("MotorDeBordo", "❌ Nenhuma tela configurada - despedida não será exibida")
                    return@launch
                }

                val (janela, telaId) = janelaComDespedida

                // Recolhe as outras janelas do Haval Trip (palco limpo)
                JanelaDoPainel.entries.forEach { outraJanela ->
                    if (outraJanela != janela) {
                        ProjetorDoPainel.recolher(outraJanela)
                    }
                }

                // Pequeno delay para composable processar ignição OFF
                delay(100)

                // DESPEDIDA COM PRIORIDADE TOTAL via ClusterOverlayService:
                // - Remove outros apps (Impulse, AutoPanel) do display
                // - Projeta overlay WindowManager com parâmetros da engenharia reversa
                // - Garante que NADA fica por cima
                Log.i("MotorDeBordo", "🎯 Projetando despedida com remoção de outros apps")
                br.com.hugolumazzini.havaltrip.painel.ClusterOverlayService.projetarDespedida(
                    app,
                    janela,
                    telaId
                )
                Log.i("MotorDeBordo", "✅ Despedida projetada com prioridade total!")

            } catch (e: Exception) {
                Log.e("MotorDeBordo", "❌ Erro ao mostrar despedida", e)
            }
        }
    }

    companion object {

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
