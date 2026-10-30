package br.com.hugolumazzini.havaltrip.telemetry

import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import br.com.hugolumazzini.havaltrip.domain.TelemetrySample
import com.beantechs.intelligentvehiclecontrol.IIntelligentVehicleControlService
import com.beantechs.intelligentvehiclecontrol.sdk.IListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

private const val TAG = "ShizukuTelemetry"

/** Nome do serviço de veículo da GWM dentro da central. */
private const val SERVICO_DO_CARRO = "com.beantechs.intelligentvehiclecontrol"

/** Código do nosso pedido de permissão; qualquer número serve, só precisa bater. */
private const val PEDIDO_DE_PERMISSAO = 4321

/**
 * Leitura do H6 pela linha direta, sem intermediário.
 *
 * REVISÃO COMPLETA v1.0.24: Corrigidos todos os problemas de intermitência.
 *
 * A central expõe um serviço interno que sabe todos os valores do carro, mas só
 * atende quem tem privilégio de sistema. O [Shizuku] é exatamente a ponte para
 * isso: o dono autoriza uma vez, e o app passa a falar com o serviço como se
 * fosse o próprio sistema. É o que o HavalShisuku e o Impulse fazem.
 *
 * A diferença que isso faz aqui é decisiva: **nós** dizemos quais chaves
 * queremos monitorar ([HavalTelemetrySource.CHAVES]). Pela outra fonte, a
 * [HavalTelemetrySource], a lista quem escolhe é o HavalShisuku — e a lista
 * padrão dele não inclui tanque, autonomia nem consumo médio, que é metade do
 * que um computador de bordo precisa.
 */
class ShizukuTelemetrySource(
    private val estado: EstadoDoCarro,
    private val intervaloMs: Long = 1000L,
) : TelemetrySource {

    private val _situacao = MutableStateFlow<Situacao>(Situacao.Verificando)

    /** Em que pé está a linha direta, para a tela dizer o que fazer a respeito. */
    val situacao: StateFlow<Situacao> = _situacao.asStateFlow()

    /**
     * O que impede (ou não) a leitura direta.
     *
     * São estados separados porque cada um tem um conserto diferente, e dentro
     * do carro adivinhar qual é sai caro: instalar o Shizuku, iniciá-lo, tocar
     * em "permitir", ou nada — já está funcionando.
     */
    sealed interface Situacao {
        data object Verificando : Situacao
        data object SemShizuku : Situacao
        data object PrecisaAutorizar : Situacao
        data object Conectado : Situacao
        data class Falhou(val motivo: String) : Situacao
    }

    override fun samples(): Flow<TelemetrySample> = callbackFlow {
        var servico: IIntelligentVehicleControlService? = null
        var listenerRegistrado = false
        var ultimaLeituraMs = 0L
        var contadorDeCallbacks = 0L
        var ultimoCallbackMs = 0L

        val ouvinte = object : IListener.Stub() {
            override fun onDataChanged(chave: String?, valor: String?) {
                if (chave != null && valor != null) {
                    estado.registrar(chave, valor)
                    contadorDeCallbacks++
                    ultimoCallbackMs = System.currentTimeMillis()
                }
            }
        }

        fun desconectar() {
            if (listenerRegistrado) {
                runCatching {
                    servico?.unRegisterDataChangedListener(PACOTE, ouvinte)
                    Log.d(TAG, "🔌 Listener desregistrado")
                }.onFailure {
                    Log.w(TAG, "⚠️ Erro ao desregistrar listener (esperado se serviço morreu)", it)
                }
                listenerRegistrado = false
            }
            servico = null
        }

        fun conectar(): Boolean {
            desconectar()

            Log.d(TAG, "🔄 Tentando conectar ao serviço do carro...")
            servico = abrirServico() ?: run {
                Log.e(TAG, "❌ Falha ao abrir serviço do carro")
                return false
            }

            return runCatching {
                // A ordem importa: registrar o ouvinte antes de declarar as
                // chaves evita a janela em que o carro muda um valor entre
                // as duas chamadas e ninguém está escutando.
                servico!!.registerDataChangedListener(PACOTE, ouvinte)
                listenerRegistrado = true
                Log.d(TAG, "✅ Listener registrado")

                servico!!.addListenerKey(PACOTE, HavalTelemetrySource.CHAVES.toTypedArray())
                Log.d(TAG, "✅ ${HavalTelemetrySource.CHAVES.size} chaves declaradas")

                lerTudoAgora(servico!!)
                ultimaLeituraMs = System.currentTimeMillis()
                ultimoCallbackMs = System.currentTimeMillis()
                contadorDeCallbacks = 0

                _situacao.value = Situacao.Conectado
                Log.i(TAG, "🎉 CONECTADO ao serviço do carro via Shizuku")
                true
            }.onFailure {
                Log.e(TAG, "❌ Erro ao registrar no serviço do carro", it)
                _situacao.value = Situacao.Falhou(it.message ?: "erro ao registrar")
                desconectar()
                false
            }.getOrDefault(false)
        }

        val aoAutorizar = Shizuku.OnRequestPermissionResultListener { codigo, resultado ->
            if (codigo == PEDIDO_DE_PERMISSAO) {
                Log.d(TAG, "🔑 Resposta de autorização: ${if (resultado == PackageManager.PERMISSION_GRANTED) "CONCEDIDA" else "NEGADA"}")
                if (resultado == PackageManager.PERMISSION_GRANTED) {
                    conectar()
                } else {
                    _situacao.value = Situacao.PrecisaAutorizar
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(aoAutorizar)

        // O Shizuku pode subir depois do app — na partida do carro a ordem não
        // é garantida. Sem esperar pelo binder, o app decidiria "sem Shizuku"
        // um segundo antes de ele ficar pronto e não tentaria de novo.
        val aoChegarBinder = Shizuku.OnBinderReceivedListener {
            Log.d(TAG, "📡 Binder do Shizuku chegou")
            garantirPermissaoEConectar(::conectar)
        }
        Shizuku.addBinderReceivedListenerSticky(aoChegarBinder)

        val aoMorrerBinder = Shizuku.OnBinderDeadListener {
            Log.w(TAG, "💀 Binder do Shizuku morreu!")
            desconectar()
            _situacao.value = Situacao.SemShizuku
        }
        Shizuku.addBinderDeadListener(aoMorrerBinder)

        // Tentativa inicial de conexão
        launch {
            delay(500) // Pequeno delay para dar tempo do Shizuku inicializar
            garantirPermissaoEConectar(::conectar)
        }

        launch {
            var ultimaTentativaReconexao = 0L
            var ciclosSemCallback = 0

            while (isActive) {
                delay(intervaloMs)
                val agora = System.currentTimeMillis()
                val conectado = _situacao.value is Situacao.Conectado

                // Leitura periódica para pegar valores que não disparam callbacks
                val leu = if (conectado && servico != null) {
                    val sucesso = lerTudoAgora(servico!!)
                    if (sucesso) {
                        ultimaLeituraMs = agora
                    }
                    sucesso
                } else false

                // WATCHDOG INTELIGENTE: Detecta conexão morta de verdade
                // Não reconecta se o carro só está parado (sem mudanças é normal)
                val tempoSemCallback = agora - ultimoCallbackMs
                val tempoSemLeitura = agora - ultimaLeituraMs
                val leituraFalhando = conectado && !leu && tempoSemLeitura > FALHA_LEITURA_MS

                // Se está conectado mas não recebe callbacks, incrementa contador
                if (conectado && tempoSemCallback > INTERVALO_SEM_CALLBACK_MS) {
                    ciclosSemCallback++
                } else if (contadorDeCallbacks > 0) {
                    // Recebeu callback, resetou
                    ciclosSemCallback = 0
                }

                // Detecta conexão morta: leitura falhando OU muitos ciclos sem callback
                val conexaoMorta = leituraFalhando || ciclosSemCallback > MAX_CICLOS_SEM_CALLBACK

                val precisaReconectar = !conectado || conexaoMorta
                val podeReconectar = agora - ultimaTentativaReconexao >= INTERVALO_RECONEXAO_MS

                if (precisaReconectar && podeReconectar) {
                    if (conexaoMorta) {
                        Log.w(TAG, "⚠️ Conexão morta detectada (leitura=${!leu}, ciclosSemCallback=$ciclosSemCallback)")
                    }

                    ultimaTentativaReconexao = agora
                    ciclosSemCallback = 0

                    when {
                        !Shizuku.pingBinder() -> {
                            Log.w(TAG, "❌ Shizuku não responde")
                            _situacao.value = Situacao.SemShizuku
                            desconectar()
                        }
                        !autorizado() -> {
                            Log.w(TAG, "🔑 Sem autorização do Shizuku, pedindo...")
                            _situacao.value = Situacao.PrecisaAutorizar
                            runCatching {
                                Shizuku.requestPermission(PEDIDO_DE_PERMISSAO)
                            }.onFailure {
                                Log.w(TAG, "⚠️ Falha ao pedir autorização", it)
                            }
                        }
                        else -> {
                            Log.i(TAG, "🔄 Reconectando...")
                            if (!conectar()) {
                                // Se falhou ao reconectar, aumenta intervalo gradualmente
                                delay(INTERVALO_APOS_FALHA_MS)
                            }
                        }
                    }
                }

                // Log periódico de saúde (a cada 30 segundos quando conectado)
                if (conectado && agora % 30_000 < intervaloMs) {
                    Log.d(TAG, "💚 Saúde: callbacks=$contadorDeCallbacks, última_leitura=${tempoSemLeitura}ms atrás, último_callback=${tempoSemCallback}ms atrás")
                }

                estado.publicarFita()
                // Enquanto nada tiver chegado, não há amostra a dar: ver [EstadoDoCarro.mudo].
                if (!estado.mudo()) {
                    trySend(estado.montarAmostra())
                }
            }
        }

        awaitClose {
            Log.d(TAG, "🛑 Encerrando flow do Shizuku")
            desconectar()
            Shizuku.removeRequestPermissionResultListener(aoAutorizar)
            Shizuku.removeBinderReceivedListener(aoChegarBinder)
            Shizuku.removeBinderDeadListener(aoMorrerBinder)
        }
    }

    /** Pede a autorização se ainda não houver, e conecta assim que tiver. */
    private fun garantirPermissaoEConectar(conectar: () -> Boolean) {
        when {
            !Shizuku.pingBinder() -> {
                Log.d(TAG, "📡 Shizuku não disponível")
                _situacao.value = Situacao.SemShizuku
            }
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "✅ Já temos autorização, conectando...")
                conectar()
            }
            else -> {
                Log.d(TAG, "🔑 Sem autorização, pedindo...")
                _situacao.value = Situacao.PrecisaAutorizar
                runCatching {
                    Shizuku.requestPermission(PEDIDO_DE_PERMISSAO)
                }.onFailure {
                    Log.w(TAG, "⚠️ Falha ao pedir autorização", it)
                }
            }
        }
    }

    /**
     * Puxa o valor atual de todas as chaves de uma vez.
     *
     * O serviço só avisa quando um valor **muda**. Com o carro parado na
     * garagem nada mudaria, e a tela nasceria vazia sem nada estar errado.
     */
    private fun lerTudoAgora(servico: IIntelligentVehicleControlService): Boolean =
        runCatching {
            val chaves = HavalTelemetrySource.CHAVES.toTypedArray()
            val valores = servico.fetchDatas(chaves)
            valores.forEachIndexed { i, valor ->
                if (valor != null) estado.registrar(chaves[i], valor)
            }
            true
        }.onFailure {
            Log.w(TAG, "⚠️ Falha ao ler dados (servico morreu?)", it)
        }.getOrDefault(false)

    /** Pede o serviço ao sistema e o embrulha no privilégio do Shizuku. */
    private fun abrirServico(): IIntelligentVehicleControlService? = runCatching {
        val cru = binderDoSistema(SERVICO_DO_CARRO)
            ?: error("A central não tem o serviço de veículo da GWM")
        IIntelligentVehicleControlService.Stub.asInterface(ShizukuBinderWrapper(cru))
    }.onFailure {
        Log.e(TAG, "❌ Erro ao abrir o serviço do carro", it)
        _situacao.value = Situacao.Falhou(it.message ?: "erro ao abrir o serviço")
    }.getOrNull()

    companion object {
        private const val PACOTE = "br.com.hugolumazzini.havaltrip"

        /** Se fetchDatas falha por mais de 10s, serviço está morto. */
        private const val FALHA_LEITURA_MS = 10_000L

        /** Intervalo para considerar "sem callbacks" (30s é normal com carro parado). */
        private const val INTERVALO_SEM_CALLBACK_MS = 30_000L

        /** Máximo de ciclos sem callback antes de reconectar (5 ciclos = 5s). */
        private const val MAX_CICLOS_SEM_CALLBACK = 180  // 180s = 3min

        /** Intervalo mínimo entre reconexões (evita loop). */
        private const val INTERVALO_RECONEXAO_MS = 5_000L

        /** Delay adicional após falha ao reconectar. */
        private const val INTERVALO_APOS_FALHA_MS = 3_000L

        /**
         * `android.os.ServiceManager` por reflexão.
         *
         * A classe é interna do Android e não faz parte do SDK público, então
         * não há como chamá-la direto. É o mesmo caminho que o HavalShisuku
         * usa; sem ele não existe como alcançar um serviço que não é do SDK.
         */
        private fun binderDoSistema(nome: String): IBinder? = runCatching {
            val classe = Class.forName("android.os.ServiceManager")
            classe.getMethod("getService", String::class.java).invoke(null, nome) as? IBinder
        }.getOrNull()

        /**
         * Uma linha avulsa com o serviço do carro, só para perguntar e sair.
         *
         * Separada da fonte de telemetria de propósito: aqui não se registra
         * ouvinte nem se declara chave nenhuma, então nada do que for lido por
         * este caminho entra no monitoramento contínuo do app. É o que a
         * [IdentidadeDoCarro] precisa — uma pergunta de uma vez só.
         */
        internal fun servicoDoCarro(): IIntelligentVehicleControlService? = runCatching {
            if (!autorizado()) return null
            val cru = binderDoSistema(SERVICO_DO_CARRO) ?: return null
            IIntelligentVehicleControlService.Stub.asInterface(ShizukuBinderWrapper(cru))
        }.getOrNull()

        /**
         * `true` se o Shizuku está instalado e rodando.
         *
         * NÃO exige autorização - só verifica se o processo do Shizuku está ativo.
         * A autorização será pedida automaticamente quando tentar usar (MotorDeBordo.init).
         * Isso permite que o app escolha Shizuku como fonte ANTES de ter autorização,
         * e assim possa pedir autorização (se ficar esperando autorização para escolher,
         * nunca pede = catch-22).
         */
        fun disponivel(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

        /** `true` se o dono já autorizou este app, em algum momento. */
        fun autorizado(): Boolean = runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }
}
