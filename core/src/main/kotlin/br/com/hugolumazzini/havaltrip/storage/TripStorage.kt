package br.com.hugolumazzini.havaltrip.storage

import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.Trip
import br.com.hugolumazzini.havaltrip.domain.TripRecord
import br.com.hugolumazzini.havaltrip.engine.ConsumptionAverage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Retrato completo do módulo num instante. É a unidade de gravação: ou o
 * arquivo tem um snapshot inteiro e coerente, ou tem o anterior inteiro. Nunca
 * metade dos dois.
 */
@Serializable
data class TripSnapshot(
    val version: Int = SCHEMA_VERSION,
    val trips: List<Trip> = emptyList(),
    val history: List<TripRecord> = emptyList(),
    val consumptionAverage: ConsumptionAverage = ConsumptionAverage(),
    val ignition: IgnitionState = IgnitionState.OFF,
    val odometerTotalKm: Double = 0.0,
    val lastSampleMs: Long? = null,
    /**
     * Instante em que a ignição foi desligada, ou `null` com o carro ligado.
     * Fica aqui e não só na memória porque a central desliga junto com a chave:
     * ao voltar, é a diferença entre este carimbo e o relógio de agora que diz
     * se a Trip automática deve começar viagem nova.
     */
    val ignitionOffSinceMs: Long? = null,
    /**
     * Quantos contadores manuais o motorista quer ver, de 1 a 4.
     *
     * Os que sobram continuam gravados com tudo o que mediram: esconder um
     * contador não pode apagar a viagem que ele estava contando, porque quem
     * reduz de quatro para dois às vezes só quer a tela mais limpa hoje.
     */
    val contadoresManuais: Int = MAX_CONTADORES_MANUAIS,
    val savedAtMs: Long = 0L,
) {
    companion object {
        /** 2: saiu o preço do combustível, entrou [ignitionOffSinceMs]. */
        const val SCHEMA_VERSION = 2

        /**
         * O teto de contadores manuais: A, B, C e D.
         *
         * Quatro porque é quanto cabe na coluna da esquerda sem rolagem na tela
         * da central, e porque acima disso ninguém lembra para que serve cada
         * um.
         */
        const val MAX_CONTADORES_MANUAIS = 4
    }
}

/** Porta de persistência. O motor não sabe se por trás tem arquivo ou banco. */
interface TripStorage {
    /** Último snapshot íntegro, ou `null` se não houver nada aproveitável. */
    fun load(): TripSnapshot?

    /** Grava o snapshot de forma atômica. */
    fun save(snapshot: TripSnapshot)

    /**
     * Carimba que o app estava vivo neste instante.
     *
     * É de propósito mais barato que [save]: guarda só um número. Serve para
     * medir quanto tempo o app ficou fora do ar — reiniciar a central leva
     * pouco mais de um minuto, o carro dormindo leva horas, e sem este carimbo
     * as duas coisas ficam indistinguíveis.
     */
    fun marcarVivo(atMs: Long) {}

    /** Último instante carimbado por [marcarVivo], ou `null` se não houver. */
    fun ultimoSinalDeVidaMs(): Long? = null
}

/** Guarda tudo na memória. Serve aos testes e ao demo. */
class InMemoryTripStorage(private var atual: TripSnapshot? = null) : TripStorage {
    var saveCount: Int = 0
        private set

    override fun load(): TripSnapshot? = atual

    override fun save(snapshot: TripSnapshot) {
        atual = snapshot
        saveCount++
    }

    private var vivoMs: Long? = null

    override fun marcarVivo(atMs: Long) {
        vivoMs = atMs
    }

    override fun ultimoSinalDeVidaMs(): Long? = vivoMs
}

/**
 * Persistência em JSON com troca atômica de arquivo.
 *
 * O problema real: a central perde energia junto com a ignição, no meio de
 * qualquer coisa. Escrever por cima do arquivo bom é apostar que a queda não
 * acontece durante a escrita — e ela acontece justamente aí, porque é quando
 * mais se grava.
 *
 * Por isso o ciclo é: escreve inteiro num temporário, força o conteúdo ao
 * disco (`fd.sync()`), promove o arquivo bom atual a cópia de segurança e só
 * então renomeia o temporário por cima. `rename` no mesmo sistema de arquivos é
 * atômico: em qualquer ponto da queda existe um arquivo íntegro — o novo ou o
 * antigo. O `.bak` cobre o caso de o próprio arquivo bom ter sido corrompido
 * por falha do armazenamento, não da escrita.
 */
class FileTripStorage(private val directory: File) : TripStorage {

    private val arquivo = File(directory, NOME)
    private val temporario = File(directory, "$NOME.tmp")
    private val copia = File(directory, "$NOME.bak")

    // Sem `prettyPrint`: ninguém lê este arquivo a olho, e a formatação o
    // deixava um terço maior — um terço a mais escrito na flash a cada
    // gravação. `coerceInputValues` faz um valor de enum desconhecido (de uma
    // versão mais nova, por exemplo) cair no padrão em vez de invalidar o
    // arquivo inteiro; e um NaN que escape do saneamento grava em vez de
    // impedir toda gravação dali em diante.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        allowSpecialFloatingPointValues = true
    }

    override fun load(): TripSnapshot? {
        ler(arquivo)?.let { return it }
        ler(copia)?.let { return it }
        // Nenhum dos dois serve, mas algum existe com conteúdo. Começar do zero
        // por cima deles apagaria na próxima gravação tudo o que talvez ainda
        // dê para recuperar à mão; guardados à parte, nada se perde.
        val carimbo = System.currentTimeMillis()
        listOf(arquivo, copia)
            .filter { it.exists() && it.length() > 0L }
            .forEach { it.renameTo(File(directory, "${it.name}.corrompido-$carimbo")) }
        return null
    }

    private fun ler(alvo: File): TripSnapshot? = runCatching {
        if (!alvo.exists() || alvo.length() == 0L) return null
        json.decodeFromString(TripSnapshot.serializer(), alvo.readText())
    }.getOrNull()

    override fun save(snapshot: TripSnapshot) {
        directory.mkdirs()
        val texto = json.encodeToString(TripSnapshot.serializer(), snapshot)

        FileOutputStream(temporario).use { saida ->
            saida.write(texto.toByteArray())
            saida.flush()
            // Sem isto o dado fica no cache do sistema e o rename pode chegar
            // ao disco antes do conteúdo: arquivo novo, vazio por dentro.
            saida.fd.sync()
        }

        if (arquivo.exists()) {
            copia.delete()
            arquivo.renameTo(copia)
        }
        if (!temporario.renameTo(arquivo)) {
            // Renomeação falhou: recupera a cópia para não ficar sem nada.
            if (!arquivo.exists() && copia.exists()) copia.renameTo(arquivo)
            error("Não foi possível promover o snapshot temporário a definitivo.")
        }
    }

    /**
     * O sinal de vida é um arquivo à parte, com um número dentro.
     *
     * Não entra no snapshot porque ele é reescrito por inteiro e com `sync()`:
     * carimbar a cada minuto ali seria gravar o histórico inteiro sessenta
     * vezes por hora na flash da central. Aqui são poucos bytes, e perder o
     * carimbo numa queda de energia não custa nada — na pior das hipóteses o
     * app volta a usar a hora da última gravação, que é o comportamento antigo.
     */
    override fun marcarVivo(atMs: Long) {
        runCatching {
            directory.mkdirs()
            File(directory, VIVO).writeText(atMs.toString())
        }
    }

    override fun ultimoSinalDeVidaMs(): Long? =
        runCatching { File(directory, VIVO).readText().trim().toLong() }.getOrNull()

    companion object {
        const val NOME = "trips.json"
        const val VIVO = "vivo.txt"
    }
}

/**
 * Grava numa thread própria, fora de quem chama.
 *
 * Gravar é escrever o histórico inteiro e esperar a flash confirmar (`sync`):
 * de dez a duzentos milissegundos numa central. Na thread principal, isso
 * congelava a tela e as janelas projetadas no painel a cada quilômetro.
 *
 * Só a gravação mais recente importa: se duas chegam enquanto uma está sendo
 * escrita, a do meio é descartada, porque a última já contém tudo o que ela
 * tinha. O snapshot é imutável, então entregá-lo a outra thread é seguro.
 */
class GravacaoEmSegundoPlano(
    private val base: TripStorage,
    private val aoFalhar: (Throwable) -> Unit = {},
) : TripStorage {

    private val executor = Executors.newSingleThreadExecutor { tarefa ->
        Thread(tarefa, "gravacao-das-trips").apply { isDaemon = true }
    }
    private val pendente = AtomicReference<TripSnapshot?>(null)

    override fun load(): TripSnapshot? = base.load()

    override fun save(snapshot: TripSnapshot) {
        // Só agenda se não havia nada esperando: a tarefa já agendada vai
        // pegar este snapshot, que é o mais novo.
        if (pendente.getAndSet(snapshot) == null) executor.execute(::gravarPendente)
    }

    private fun gravarPendente() {
        val snapshot = pendente.getAndSet(null) ?: return
        runCatching { base.save(snapshot) }.onFailure(aoFalhar)
    }

    override fun marcarVivo(atMs: Long) {
        executor.execute { runCatching { base.marcarVivo(atMs) } }
    }

    override fun ultimoSinalDeVidaMs(): Long? = base.ultimoSinalDeVidaMs()

    /** Espera o que já foi pedido chegar ao disco. Para testes e para sair. */
    fun aguardar(limiteMs: Long = 2_000L) {
        runCatching { executor.submit {}.get(limiteMs, TimeUnit.MILLISECONDS) }
    }
}

/**
 * Decide *quando* gravar. Ficou separado do [TripStorage] porque a política é
 * uma regra de produto ("a cada 1 km, a cada 5 min, e sempre ao desligar") e a
 * gravação é infraestrutura; trocar uma não deveria mexer na outra.
 *
 * Gravar a cada amostra desgastaria a memória flash da central sem ganho: um
 * quilômetro de dado perdido não muda nada para o motorista, um armazenamento
 * queimado muda tudo.
 */
class SnapshotPolicy(
    private val everyKm: Double = 1.0,
    private val everyMs: Long = 5 * 60 * 1000L,
) {
    private var ultimaDistanciaKm = 0.0
    private var ultimoMs: Long? = null

    /** Sincroniza os marcos com um snapshot recém-carregado ou recém-salvo. */
    fun mark(totalDistanceKm: Double, nowMs: Long) {
        ultimaDistanciaKm = totalDistanceKm
        ultimoMs = nowMs
    }

    /** `true` se já é hora de gravar. */
    fun shouldSave(totalDistanceKm: Double, nowMs: Long): Boolean {
        val porDistancia = totalDistanceKm - ultimaDistanciaKm >= everyKm
        val porTempo = ultimoMs?.let { nowMs - it >= everyMs } ?: true
        return porDistancia || porTempo
    }
}
