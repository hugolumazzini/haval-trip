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
            var ultimaTentativaMs = 0L
            while (isActive) {
                delay(intervaloMs)
                val conectado = _situacao.value is Situacao.Conectado
                // Uma releitura periódica cobre o valor que muda sem o serviço
                // avisar. Sai barato e evita um painel congelado numa chave
                // solitária que não dispara callback.
                val leu = servico?.takeIf { conectado }?.let { lerTudoAgora(it) } ?: false

                // O vigia. Dois jeitos de a linha morrer sem ninguém perceber:
                // o serviço do carro reinicia e o nosso ponteiro fica apontando
                // para um morto — aí `fetchDatas` estoura e `leu` é falso —, ou
                // ele continua respondendo mas com o ouvinte perdido, e então
                // nada mais muda de valor, para sempre. Foi este segundo caso no
                // carro: o painel congelou e nem fechar o app resolvia, porque o
                // serviço de bordo mantém o processo de pé e a reconexão só
                // acontecia ao criar o processo.
                val agora = System.currentTimeMillis()
                val parado = agora - estado.ultimaMudancaMs > SEM_NOVIDADE_MS
                val precisaReconectar = !conectado || !leu || parado
                if (precisaReconectar && agora - ultimaTentativaMs >= ESPERA_ENTRE_TENTATIVAS_MS) {
                    ultimaTentativaMs = agora
                    if (parado && conectado) {
                        Log.w(TAG, "Sem novidade do carro há ${SEM_NOVIDADE_MS / 1000}s: religando")
                    }
                    // Larga o ouvinte antigo antes: registrar duas vezes deixa o
                    // serviço mandando em dobro, e é o registro velho — o que
                    // não funciona mais — que ficaria valendo.
                    runCatching { servico?.unRegisterDataChangedListener(PACOTE, ouvinte) }
                    servico = null
                    // Só religa; não pede autorização de novo. O vigia roda
                    // sozinho a viagem inteira, e pedir permissão em laço
                    // encheria a tela do motorista de caixas de diálogo.
                    when {
                        !Shizuku.pingBinder() -> _situacao.value = Situacao.SemShizuku
                        !autorizado() -> _situacao.value = Situacao.PrecisaAutorizar
                        else -> conectar()
                    }
                }

                estado.publicarFita()
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
    private fun lerTudoAgora(servico: IIntelligentVehicleControlService): Boolean =
        runCatching {
            val chaves = HavalTelemetrySource.CHAVES.toTypedArray()
            servico.fetchDatas(chaves).forEachIndexed { i, valor ->
                if (valor != null) estado.registrar(chaves[i], valor)
            }
            true
        }.onFailure { Log.w(TAG, "Não deu para ler tudo de uma vez", it) }.getOrDefault(false)

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
         * Quanto tempo sem nenhum valor **mudar** já é suspeita de linha morta.
         *
         * Um minuto e meio é folgado de propósito: o carro parado e desligado
         * também fica sem novidade, e a religada dele não custa nada — desfaz e
         * refaz o registro do ouvinte. O que não se pode é ficar do outro lado,
         * congelado a viagem inteira, que foi o que aconteceu.
         */
        private const val SEM_NOVIDADE_MS = 90_000L

        /** Piso entre duas religadas, para o vigia não virar um laço de reconexão. */
        private const val ESPERA_ENTRE_TENTATIVAS_MS = 60_000L

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
