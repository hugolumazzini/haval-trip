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
     * Valores de `car.basic.engine_state` que significam "desligado".
     *
     * Vêm do código do Impulse (`ServiceManager.isMainScreenOn`), que trata
     * `-1` e `15` como carro dormindo. Sem esta lista o `15` seria lido como
     * motor girando, que é o erro antigo com outra roupa.
     */
    val MOTOR_DESLIGADO = setOf(-1.0, 0.0, 15.0)

    /**
     * @param prontoParaAndar `car.basic.driving_ready_state` — a melhor prova
     *   que existe num híbrido, porque é ela que acende o "READY" do painel.
     *   O Impulse trata `-1` e `0` como carro desligado; qualquer outro valor
     *   é carro de pé.
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
    ): IgnitionState = explicar(prontoParaAndar, motor, rotacao, modoEnergia, velocidadeKmh).estado

    /**
     * A mesma decisão, dizendo **quem** a tomou.
     *
     * Existe por um defeito relatado no carro: com a central ligada e a chave
     * fora, a viagem começava a contar tempo. São cinco critérios, e sem saber
     * qual deles disse "ligado" o conserto vira adivinhação — mexer no critério
     * errado quebraria o híbrido parado no semáforo, que é o caso que esta regra
     * nasceu para proteger.
     *
     * O veredito e o motivo saem juntos da mesma passagem, e não de duas contas
     * parecidas: uma explicação que discorda do que o app fez seria pior que
     * nenhuma.
     */
    fun explicar(
        prontoParaAndar: String?,
        motor: String?,
        rotacao: String?,
        modoEnergia: String?,
        velocidadeKmh: Double,
    ): Veredito {
        val criterio = when {
            numero(prontoParaAndar)?.let { it > 0.0 } == true -> Criterio.PRONTO_PARA_ANDAR
            numero(rotacao)?.let { it > 0.0 } == true -> Criterio.ROTACAO
            numero(motor)?.let { it !in MOTOR_DESLIGADO } == true -> Criterio.MOTOR
            velocidadeKmh >= TripMetrics.MOVING_THRESHOLD_KMH -> Criterio.VELOCIDADE
            numero(modoEnergia)?.let { it >= MODO_ENERGIA_LIGADO } == true -> Criterio.MODO_ENERGIA
            else -> Criterio.NENHUM
        }
        return Veredito(
            estado = if (criterio == Criterio.NENHUM) IgnitionState.OFF else IgnitionState.ON,
            criterio = criterio,
            prontoParaAndar = prontoParaAndar,
            motor = motor,
            rotacao = rotacao,
            modoEnergia = modoEnergia,
            velocidadeKmh = velocidadeKmh,
        )
    }

    /** Qual dos critérios decidiu. [NENHUM] é o carro desligado. */
    enum class Criterio(val rotulo: String) {
        PRONTO_PARA_ANDAR("driving_ready_state > 0"),
        ROTACAO("engine_speed > 0"),
        MOTOR("engine_state fora de -1, 0 e 15"),
        VELOCIDADE("o carro está se movendo"),
        MODO_ENERGIA("power_mode >= $MODO_ENERGIA_LIGADO"),
        NENHUM("nenhum critério deu ligado"),
    }

    /** A decisão com os valores que a produziram, para a tela de diagnóstico. */
    data class Veredito(
        val estado: IgnitionState,
        val criterio: Criterio,
        val prontoParaAndar: String?,
        val motor: String?,
        val rotacao: String?,
        val modoEnergia: String?,
        val velocidadeKmh: Double,
    )

    private fun numero(valor: String?): Double? = valor?.trim()?.toDoubleOrNull()
}
