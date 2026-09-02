package br.com.hugolumazzini.havaltrip.telemetry

import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.PainelDoVeiculo
import br.com.hugolumazzini.havaltrip.domain.TelemetrySample
import java.util.concurrent.ConcurrentHashMap

/**
 * O último valor de cada chave, e como virar uma amostra a partir deles.
 *
 * Existe separado das fontes porque **como** o valor chega — pela linha direta
 * do Shizuku ou pelo broadcast do HavalShisuku — não muda nada no que ele
 * significa. Duas cópias dessa conversão seriam duas chances de a resposta do
 * teste no carro ser aplicada só na metade do app.
 *
 * O mapa é concorrente porque quem escreve é o callback do serviço do carro,
 * numa thread do Binder, e quem lê é o laço que monta amostras.
 */
class EstadoDoCarro(private val diario: DiarioDeCampo) {

    private val cache = ConcurrentHashMap<String, String>()

    /** Guarda o valor cru e espelha no diário de campo. */
    fun registrar(chave: String, valor: String) {
        cache[chave] = valor
        diario.registrar(chave, valor)
    }

    /**
     * O consumo instantâneo já decodificado, para quem precisa saber em que
     * unidade o carro está falando — a tela de diagnóstico, principalmente.
     */
    fun consumoInstantaneo(): Unidades.ConsumoInstantaneo? =
        Unidades.lerConsumoInstantaneo(cache[HavalTelemetrySource.CHAVE_CONSUMO_INSTANTANEO])

    /**
     * Deixa a fita da tela em dia.
     *
     * As fontes chamam isto a cada volta do laço porque a publicação da fita é
     * espaçada para não pesar: sem uma batida de fora, um carro parado — que
     * publica pouco — deixaria a tela congelada no último evento até alguém
     * acelerar, e o diagnóstico pareceria travado sem estar.
     */
    fun publicarFita() = diario.publicarFita()

    /**
     * O estado físico do carro — portas, cintos, pneus — para a lateral da tela.
     *
     * Fica fora de [montarAmostra] porque não é amostra: nada disso entra no
     * cálculo da viagem, e misturar as duas coisas obrigaria o núcleo a carregar
     * uma porta aberta por dentro de cada integração de combustível.
     */
    fun painelDoVeiculo(): PainelDoVeiculo = PainelDoVeiculo.ler(
        portas = cache[HavalTelemetrySource.CHAVE_PORTAS],
        cintos = cache[HavalTelemetrySource.CHAVE_CINTOS],
        vidros = cache[HavalTelemetrySource.CHAVE_VIDROS],
        tetoSolar = cache[HavalTelemetrySource.CHAVE_TETO_SOLAR],
        pneus = cache[HavalTelemetrySource.CHAVE_PNEUS],
        unidadePneus = cache[HavalTelemetrySource.CHAVE_UNIDADE_PNEUS],
    )

    fun montarAmostra(): TelemetrySample {
        val velocidade = numero(HavalTelemetrySource.CHAVE_VELOCIDADE) ?: 0.0
        val percentual = numero(HavalTelemetrySource.CHAVE_TANQUE_PERCENTUAL)

        return TelemetrySample(
            timestampMs = System.currentTimeMillis(),
            speedKmh = velocidade,
            fuelRateLph = Unidades.litrosPorHora(consumoInstantaneo(), velocidade),
            odometerTotalKm = numero(HavalTelemetrySource.CHAVE_HODOMETRO) ?: 0.0,
            fuelLevelL = Unidades.litrosNoTanque(percentual),
            ignition = ignicao(),
        )
    }

    /**
     * Ignição a partir do estado do motor, com o modo de energia como reserva.
     *
     * O mapeamento dos valores ainda não foi confirmado no carro — por isso o
     * critério é "qualquer coisa diferente de 0 é ligado", que erra no máximo
     * para o lado seguro, e o valor cru vai inteiro para o diagnóstico.
     */
    private fun ignicao(): IgnitionState {
        val motor = cache[HavalTelemetrySource.CHAVE_MOTOR]
        val energia = cache[HavalTelemetrySource.CHAVE_MODO_ENERGIA]
        val ligado = when {
            motor != null -> motor != "0"
            energia != null -> energia != "0"
            else -> false
        }
        return if (ligado) IgnitionState.ON else IgnitionState.OFF
    }

    private fun numero(chave: String): Double? = cache[chave]?.trim()?.toDoubleOrNull()
}
