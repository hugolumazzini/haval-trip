package br.com.hugolumazzini.havaltrip.telemetry

/**
 * Como transformar o que o carro publica no que o motor de cálculo espera.
 *
 * Vive no núcleo, e não junto das fontes, porque é aritmética pura: não depende
 * de Android nem de qual ponte trouxe o valor. É o que permite testá-la sem
 * carro e sem emulador — e estas conversões já erraram uma vez.
 *
 * O formato do consumo instantâneo não é palpite: foi lido do código do
 * Impulse, que decodifica a mesma propriedade para o painel do cluster
 * (`InstrumentProjector2.kt`).
 */
object Unidades {

    /** Capacidade do tanque do H6, em litros. Usada para virar % em litros. */
    const val TANQUE_L = 61.0

    /**
     * O consumo instantâneo, do jeito que o H6 publica: `{unidade, valor}`.
     *
     * O primeiro número não é o consumo — é **em que unidade o segundo está**.
     * O carro troca de unidade sozinho conforme a situação, porque parado não
     * existe "litro por quilômetro" (seria divisão por zero):
     *
     * | unidade | o valor está em | quando |
     * |---------|-----------------|--------|
     * | `1`     | L/100 km        | andando |
     * | `4`     | L/hora          | parado, motor em marcha lenta |
     *
     * Qualquer outro código é desconhecido e não vira litro nenhum — melhor não
     * computar combustível do que somar numa unidade que não sabemos qual é.
     */
    data class ConsumoInstantaneo(val unidade: Int, val valor: Double) {
        companion object {
            const val POR_DISTANCIA = 1
            const val EM_MARCHA_LENTA = 4
        }
    }

    /**
     * A lista de números de uma propriedade composta do carro.
     *
     * O H6 publica grandezas de várias partes como `{a,b,c}` — o consumo
     * instantâneo é `{unidade,valor}`, as portas são um número por abertura, os
     * pneus um por roda. A ordem é sempre a mesma e é ela que dá o significado:
     * não vêm nomes, vêm posições.
     *
     * `null` quando não é esse formato, e não lista vazia: "o carro não falou
     * nessa língua" precisa ser distinguível de "falou e está tudo zerado".
     */
    fun lerNumeros(bruto: String?): List<Double>? {
        val texto = bruto?.trim() ?: return null
        if (!texto.startsWith("{") || !texto.endsWith("}")) return null
        val partes = texto.substring(1, texto.length - 1)
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (partes.isEmpty()) return null
        return partes.map { it.toDoubleOrNull() ?: return null }
    }

    /** Lê o par `{a,b}`. `null` se vier em qualquer outro formato. */
    fun lerConsumoInstantaneo(bruto: String?): ConsumoInstantaneo? {
        val numeros = lerNumeros(bruto) ?: return null
        if (numeros.size < 2) return null
        return ConsumoInstantaneo(numeros[0].toInt(), numeros[1])
    }

    /**
     * Litros por hora, que é o que o motor de cálculo integra.
     *
     * Aqui é onde a marcha lenta deixa de se perder: quando o carro está parado
     * com o motor ligado ele mesmo passa a publicar em L/h, e esse gasto — que
     * a conversão por distância zeraria — entra na conta.
     *
     * Zero é resposta legítima e comum num híbrido: com o motor a combustão
     * desligado o carro anda sem queimar nada.
     */
    fun litrosPorHora(consumo: ConsumoInstantaneo?, velocidadeKmh: Double): Double {
        if (consumo == null || consumo.valor <= 0.0) return 0.0
        return when (consumo.unidade) {
            // L/100 km × km/h ÷ 100 = L/h.
            ConsumoInstantaneo.POR_DISTANCIA -> consumo.valor * velocidadeKmh / 100.0
            ConsumoInstantaneo.EM_MARCHA_LENTA -> consumo.valor
            else -> 0.0
        }
    }

    /**
     * Litros no tanque a partir do percentual.
     *
     * É aproximação e não tem como não ser: a bóia não é linear e o carro só
     * publica a porcentagem. Serve para a autonomia ter uma base; não serve
     * para dizer quantos litros cabem ainda no abastecimento.
     */
    fun litrosNoTanque(percentual: Double?): Double {
        if (percentual == null) return 0.0
        return (percentual.coerceIn(0.0, 100.0) / 100.0) * TANQUE_L
    }
}
