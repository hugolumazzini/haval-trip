package br.com.hugolumazzini.havaltrip.telemetry

import br.com.hugolumazzini.havaltrip.domain.IgnitionState
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

    fun montarAmostra(): TelemetrySample {
        val velocidade = numero(HavalTelemetrySource.CHAVE_VELOCIDADE) ?: 0.0
        val consumoBruto = numero(HavalTelemetrySource.CHAVE_CONSUMO_INSTANTANEO)
        val percentual = numero(HavalTelemetrySource.CHAVE_TANQUE_PERCENTUAL)

        return TelemetrySample(
            timestampMs = System.currentTimeMillis(),
            speedKmh = velocidade,
            fuelRateLph = Unidades.litrosPorHora(consumoBruto, velocidade, diario.interpretacao.value),
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
