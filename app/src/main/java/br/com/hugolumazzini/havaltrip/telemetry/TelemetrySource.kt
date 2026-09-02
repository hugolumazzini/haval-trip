package br.com.hugolumazzini.havaltrip.telemetry

import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.TelemetrySample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * De onde vêm as amostras.
 *
 * O contrato é só este: um fluxo de [TelemetrySample]. Quando a leitura real do
 * barramento do H6 estiver disponível, ela entra como outra implementação e
 * nada acima daqui muda — nem o [br.com.hugolumazzini.havaltrip.engine.TripManager],
 * nem a tela.
 */
interface TelemetrySource {
    fun samples(): Flow<TelemetrySample>
}

/**
 * Fonte simulada, para desenvolver e demonstrar sem o carro ligado.
 *
 * Não é um modelo de veículo: é o suficiente para a tela ter números plausíveis
 * se mexendo. Ela alterna trechos de estrada, cidade e parada, e obedece ao
 * [ignicao] que a interface controla — com a ignição desligada, emite amostras
 * paradas para o gerenciador enxergar o corte.
 */
class SimulatedTelemetrySource(
    private val intervaloMs: Long = 1000L,
    /** Onde o hodômetro retoma. Quem cria passa o valor do último snapshot. */
    private var odometroKm: Double = 48_213.4,
    private var tanqueL: Double = 42.0,
    /**
     * Para onde vão as propriedades cruas do veículo — portas, cintos, pneus.
     *
     * Opcional porque o simulador nasceu antes delas e os testes não precisam.
     * Sem isto, a lateral da tela ficaria em "sem leitura do carro" na bancada,
     * e não haveria como conferir o desenho sem estar sentado no H6.
     */
    private val estado: EstadoDoCarro? = null,
) : TelemetrySource {

    /** Ligado/desligado, controlado pelo botão de ignição da tela. */
    var ignicao: IgnitionState = IgnitionState.OFF

    private val aleatorio = Random(7)
    private var velocidadeKmh = 0.0
    private var aceleracaoKmhPorS = 0.0
    private var alvoKmh = 0.0
    private var segundosNoTrecho = 0

    override fun samples(): Flow<TelemetrySample> = flow {
        while (true) {
            delay(intervaloMs)
            avancar(intervaloMs / 1000.0)
            publicarEstadoDoVeiculo()
            emit(
                TelemetrySample(
                    timestampMs = System.currentTimeMillis(),
                    speedKmh = velocidadeKmh,
                    fuelRateLph = injecaoLph(),
                    odometerTotalKm = odometroKm,
                    fuelLevelL = tanqueL,
                    ignition = ignicao,
                )
            )
        }
    }

    /**
     * O carro de mentira também tem portas.
     *
     * A porta do motorista abre com o carro parado e fecha quando ele anda —
     * que é o comportamento de quem entra e sai. Serve para conferir na bancada
     * que o aviso acende e apaga; num carro de verdade, quem manda é o sensor.
     */
    private fun publicarEstadoDoVeiculo() {
        val alvo = estado ?: return
        val motoristaAberta = if (velocidadeKmh < 0.5 && segundosNoTrecho % 20 < 8) 1 else 0
        alvo.registrar(HavalTelemetrySource.CHAVE_PORTAS, "{$motoristaAberta,0,0,0,0,0}")
        alvo.registrar(HavalTelemetrySource.CHAVE_CINTOS, "{0,0,0}")
        alvo.registrar(HavalTelemetrySource.CHAVE_VIDROS, "{0,0,0,0}")
        alvo.registrar(HavalTelemetrySource.CHAVE_TETO_SOLAR, "0")
        alvo.registrar(HavalTelemetrySource.CHAVE_PNEUS, "{232,230,228,231}")
    }

    private fun avancar(deltaS: Double) {
        if (ignicao == IgnitionState.OFF) {
            velocidadeKmh = 0.0
            alvoKmh = 0.0
            return
        }

        if (segundosNoTrecho <= 0) {
            // Sorteia o próximo trecho: parada, cidade ou estrada.
            alvoKmh = when (aleatorio.nextInt(10)) {
                in 0..2 -> 0.0
                in 3..6 -> aleatorio.nextDouble(25.0, 60.0)
                else -> aleatorio.nextDouble(80.0, 110.0)
            }
            segundosNoTrecho = aleatorio.nextInt(15, 60)
        }
        segundosNoTrecho--

        // Aceleração limitada: um salto de 0 a 100 numa amostra seria irreal e
        // faria a velocidade máxima da Trip registrar picos que não existiram.
        val passo = 6.0 * deltaS
        val anterior = velocidadeKmh
        velocidadeKmh += min(abs(alvoKmh - velocidadeKmh), passo) * if (alvoKmh > velocidadeKmh) 1 else -1
        velocidadeKmh = max(0.0, velocidadeKmh)
        aceleracaoKmhPorS = (velocidadeKmh - anterior) / deltaS

        odometroKm += velocidadeKmh * (deltaS / 3600.0)
        tanqueL = max(0.0, tanqueL - injecaoLph() * (deltaS / 3600.0))
    }

    /**
     * Quanto o motor bebe, em litros por hora.
     *
     * Não é linear na velocidade de propósito. Uma reta faria o km/L melhorar
     * para sempre — quanto mais rápido, melhor, sem teto — e a autonomia na
     * tela só subiria, o que nenhum carro faz. Aqui há quatro parcelas:
     *
     * - marcha lenta, que corre mesmo parado no semáforo;
     * - atrito e rolamento, proporcionais à velocidade;
     * - arrasto do ar, que cresce com o **cubo** da velocidade e é o que faz o
     *   consumo piorar depois dos 90 km/h;
     * - aceleração, que é onde a cidade perde para a estrada.
     *
     * O resultado tem o melhor rendimento por volta dos 60 km/h e piora nas
     * duas pontas, como um SUV de verdade.
     */
    private fun injecaoLph(): Double {
        if (ignicao == IgnitionState.OFF) return 0.0
        val v = velocidadeKmh
        val marchaLenta = 0.8
        val rolamento = 0.055 * v
        val arrasto = 3.2e-6 * v * v * v
        val esforco = 0.35 * max(0.0, aceleracaoKmhPorS)
        return marchaLenta + rolamento + arrasto + esforco
    }
}
