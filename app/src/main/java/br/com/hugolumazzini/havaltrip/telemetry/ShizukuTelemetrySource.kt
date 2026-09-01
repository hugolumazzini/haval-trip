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

        val ouvinte = object : IListener.Stub() {
            override fun onDataChanged(chave: String?, valor: String?) {
                if (chave != null && valor != null) estado.registrar(chave, valor)
            }
        }

        fun conectar() {
            servico = abrirServico()?.also { s ->
                runCatching {
                    // A ordem importa: registrar o ouvinte antes de declarar as
                    // chaves evita a janela em que o carro muda um valor entre
                    // as duas chamadas e ninguém está escutando.
                    s.registerDataChangedListener(PACOTE, ouvinte)
                    s.addListenerKey(PACOTE, HavalTelemetrySource.CHAVES.toTypedArray())
                    lerTudoAgora(s)
                    _situacao.value = Situacao.Conectado
                }.onFailure {
                    Log.e(TAG, "Erro ao registrar no serviço do carro", it)
                    _situacao.value = Situacao.Falhou(it.message ?: "erro ao registrar")
                }
            }
        }

        val aoAutorizar = Shizuku.OnRequestPermissionResultListener { codigo, resultado ->
            if (codigo == PEDIDO_DE_PERMISSAO) {
                if (resultado == PackageManager.PERMISSION_GRANTED) conectar()
                else _situacao.value = Situacao.PrecisaAutorizar
            }
        }
        Shizuku.addRequestPermissionResultListener(aoAutorizar)

        // O Shizuku pode subir depois do app — na partida do carro a ordem não
        // é garantida. Sem esperar pelo binder, o app decidiria "sem Shizuku"
        // um segundo antes de ele ficar pronto e não tentaria de novo.
        val aoChegarBinder = Shizuku.OnBinderReceivedListener { garantirPermissaoEConectar(::conectar) }
        Shizuku.addBinderReceivedListenerSticky(aoChegarBinder)

        val aoMorrerBinder = Shizuku.OnBinderDeadListener {
            servico = null
            _situacao.value = Situacao.SemShizuku
        }
        Shizuku.addBinderDeadListener(aoMorrerBinder)

        launch {
            while (isActive) {
                delay(intervaloMs)
                // Uma releitura periódica cobre o valor que muda sem o serviço
                // avisar. Sai barato e evita um painel congelado numa chave
                // solitária que não dispara callback.
                servico?.let { s -> if (_situacao.value is Situacao.Conectado) lerTudoAgora(s) }
                trySend(estado.montarAmostra())
            }
        }

        awaitClose {
            runCatching { servico?.unRegisterDataChangedListener(PACOTE, ouvinte) }
            Shizuku.removeRequestPermissionResultListener(aoAutorizar)
            Shizuku.removeBinderReceivedListener(aoChegarBinder)
            Shizuku.removeBinderDeadListener(aoMorrerBinder)
        }
    }

    /** Pede a autorização se ainda não houver, e conecta assim que tiver. */
    private fun garantirPermissaoEConectar(conectar: () -> Unit) {
        when {
            !Shizuku.pingBinder() -> _situacao.value = Situacao.SemShizuku
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> conectar()
            else -> {
                _situacao.value = Situacao.PrecisaAutorizar
                runCatching { Shizuku.requestPermission(PEDIDO_DE_PERMISSAO) }
            }
        }
    }

    /**
     * Puxa o valor atual de todas as chaves de uma vez.
     *
     * O serviço só avisa quando um valor **muda**. Com o carro parado na
     * garagem nada mudaria, e a tela nasceria vazia sem nada estar errado.
     */
    private fun lerTudoAgora(servico: IIntelligentVehicleControlService) {
        runCatching {
            val chaves = HavalTelemetrySource.CHAVES.toTypedArray()
            servico.fetchDatas(chaves).forEachIndexed { i, valor ->
                if (valor != null) estado.registrar(chaves[i], valor)
            }
        }.onFailure { Log.w(TAG, "Não deu para ler tudo de uma vez", it) }
    }

    /** Pede o serviço ao sistema e o embrulha no privilégio do Shizuku. */
    private fun abrirServico(): IIntelligentVehicleControlService? = runCatching {
        val cru = binderDoSistema(SERVICO_DO_CARRO)
            ?: error("A central não tem o serviço de veículo da GWM")
        IIntelligentVehicleControlService.Stub.asInterface(ShizukuBinderWrapper(cru))
    }.onFailure {
        Log.e(TAG, "Erro ao abrir o serviço do carro", it)
        _situacao.value = Situacao.Falhou(it.message ?: "erro ao abrir o serviço")
    }.getOrNull()

    companion object {
        private const val PACOTE = "br.com.hugolumazzini.havaltrip"

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

        /** `true` se o Shizuku está instalado e rodando nesta central. */
        fun disponivel(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

        /** `true` se o dono já autorizou este app, em algum momento. */
        fun autorizado(): Boolean = runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }
}
