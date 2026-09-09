package br.com.hugolumazzini.havaltrip.domain

/**
 * Decide se o carro está **ligado para andar** a partir do que ele publica.
 *
 * Existe separado porque a primeira versão errava: bastava ligar a multimídia
 * — que é o que acontece ao abrir a porta, sem chave nenhuma — para o app
 * começar a contar viagem. O critério era "qualquer coisa diferente de zero é
 * ligado", e o modo de energia em ACC (só o rádio e a tela) é diferente de
 * zero.
 *
 * A regra agora procura prova de que o carro está pronto para se mover, e não
 * só energizado. Num híbrido isso não pode depender do motor a combustão:
 * parado num semáforo em modo elétrico o motor está desligado, e pausar a
 * viagem a cada sinal vermelho seria pior que o erro original.
 */
object LeituraDaIgnicao {

    /**
     * O menor valor de `car.basic.power_mode` que já é "chave em ON".
     *
     * A convenção do Android Automotive é 0 desligado, 1 ACC, 2 ON. É o único
     * pedaço desta regra que depende de o H6 seguir a convenção — por isso ele
     * é o último critério consultado, e nunca o único: se o carro estiver
     * andando, ou pronto para andar, isso decide antes.
     */
    const val MODO_ENERGIA_LIGADO = 2

    /**
     * @param prontoParaAndar `car.basic.driving_ready_state` — a melhor prova
     *   que existe num híbrido, porque é ela que acende o "READY" do painel.
     * @param motor `car.basic.engine_state`.
     * @param rotacao `car.basic.engine_speed`, em rpm.
     * @param modoEnergia `car.basic.power_mode`.
     * @param velocidadeKmh a velocidade do próprio carro: um carro que se move
     *   está ligado, qualquer que seja o código publicado nas outras chaves.
     */
    fun ler(
        prontoParaAndar: String?,
        motor: String?,
        rotacao: String?,
        modoEnergia: String?,
        velocidadeKmh: Double,
    ): IgnitionState {
        val ligado = when {
            numero(prontoParaAndar)?.let { it > 0.0 } == true -> true
            numero(rotacao)?.let { it > 0.0 } == true -> true
            numero(motor)?.let { it > 0.0 } == true -> true
            velocidadeKmh >= TripMetrics.MOVING_THRESHOLD_KMH -> true
            numero(modoEnergia)?.let { it >= MODO_ENERGIA_LIGADO } == true -> true
            else -> false
        }
        return if (ligado) IgnitionState.ON else IgnitionState.OFF
    }

    private fun numero(valor: String?): Double? = valor?.trim()?.toDoubleOrNull()
}
