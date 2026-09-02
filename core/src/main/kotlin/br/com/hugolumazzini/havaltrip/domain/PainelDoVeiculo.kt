package br.com.hugolumazzini.havaltrip.domain

import br.com.hugolumazzini.havaltrip.telemetry.Unidades

/**
 * O estado físico do carro que não entra em conta nenhuma: o que está aberto, o
 * que está solto, quanto tem de ar nos pneus.
 *
 * Vive no núcleo, longe do Android, porque é leitura pura — e porque a ordem
 * das posições dentro de cada propriedade é a única coisa que dá sentido aos
 * números. Uma troca de ordem não quebra nada visível: só passa a mostrar a
 * porta errada. Por isso tem teste.
 *
 * Nada disso alimenta o motor de cálculo: uma porta aberta não muda o consumo
 * medido. É informação para o motorista ver de relance.
 */
data class PainelDoVeiculo(
    val aberturas: List<Estado<Abertura>> = emptyList(),
    val cintos: List<Estado<Assento>> = emptyList(),
    val vidros: List<Estado<Vidro>> = emptyList(),
    val tetoSolarAberto: Boolean? = null,
    val pneus: List<Pneu> = emptyList(),
    val unidadeDePressao: Unidade = Unidade.BAR,
) {
    /** O que está aberto agora. Vazio é o estado normal do carro andando. */
    val abertas: List<Abertura> get() = acionados(aberturas)

    /** Os assentos que o carro está reclamando de cinto. */
    val semCinto: List<Assento> get() = acionados(cintos)

    val vidrosAbertos: List<Vidro> get() = acionados(vidros)

    /**
     * Tudo que merece uma linha na lateral, já em texto, na ordem de urgência.
     *
     * Os rótulos não dizem "aberta": só entram na lista quando estão, e a
     * palavra repetida em cinco linhas rouba a largura de que os nomes
     * precisam numa faixa estreita.
     *
     * Cinto vem antes de porta e porta antes de vidro porque é essa a ordem em
     * que importam a quem está dirigindo: um cinto solto é o carro andando com
     * gente desprotegida; um vidro aberto, na pior das hipóteses, é chuva.
     */
    val avisos: List<String>
        get() = semCinto.map { it.rotulo } +
            abertas.map { it.rotulo } +
            vidrosAbertos.map { it.rotulo } +
            (if (tetoSolarAberto == true) listOf("Teto solar") else emptyList())

    /** `true` quando não há nada a avisar — e há dado suficiente para afirmar. */
    val tudoCerto: Boolean get() = avisos.isEmpty() && algumaLeitura

    /**
     * Se alguma propriedade chegou.
     *
     * Separa "está tudo fechado" de "não sabemos nada" — que na tela são
     * mensagens diferentes, e confundi-las faria o app garantir uma porta
     * fechada que ele nunca leu.
     */
    val algumaLeitura: Boolean
        get() = aberturas.isNotEmpty() || cintos.isNotEmpty() ||
            vidros.isNotEmpty() || tetoSolarAberto != null || pneus.isNotEmpty()

    /** Um item e se ele está acionado (aberto, no caso das portas). */
    data class Estado<T>(val oQue: T, val acionado: Boolean)

    /**
     * Um pneu: pressão em bar e temperatura em grau, do jeito que o carro manda.
     *
     * A pressão fica guardada em bar mesmo quando a tela mostra psi. Converter
     * só na hora de desenhar evita que uma troca de unidade no painel do carro
     * vire uma conversão dobrada em algum canto do app.
     */
    data class Pneu(val roda: Roda, val pressaoBar: Double?, val temperaturaC: Double? = null) {
        /** A pressão na unidade pedida. `null` continua `null`. */
        fun pressao(unidade: Unidade): Double? = pressaoBar?.times(unidade.porBar)
    }

    /**
     * Em que unidade mostrar a pressão.
     *
     * Não é escolha nossa: o carro publica em `car.basic.tpms_units` qual
     * unidade o motorista escolheu no painel, e a lateral tem que concordar com
     * o que o cluster mostra a um palmo de distância.
     */
    enum class Unidade(val rotulo: String, val porBar: Double) {
        BAR("bar", 1.0),
        PSI("psi", 14.5038),
        KPA("kPa", 100.0),
    }

    /**
     * As aberturas, **na ordem em que o carro as publica**.
     *
     * A ordem não é escolha nossa: é a que o HavalShisuku mostra para
     * `car.basic.door_status`, lida na central do H6.
     */
    enum class Abertura(val rotulo: String, val curto: String) {
        PORTA_MOTORISTA("Porta do motorista", "Motorista"),
        PORTA_PASSAGEIRO("Porta do passageiro", "Passageiro"),
        PORTA_TRASEIRA_ESQ("Porta traseira esq.", "Tras. esq."),
        PORTA_TRASEIRA_DIR("Porta traseira dir.", "Tras. dir."),
        CAPO("Capô", "Capô"),
        PORTA_MALAS("Porta-malas", "Porta-malas"),
    }

    /**
     * Ordem de `car.basic.seat_belt_warning`.
     *
     * São **cinco** posições, uma por lugar do carro — e não três, como o app
     * assumiu até o primeiro teste no H6. O banco traseiro é reportado assento
     * a assento, inclusive o do meio.
     */
    enum class Assento(val rotulo: String, val curto: String) {
        MOTORISTA("Cinto do motorista", "Motorista"),
        PASSAGEIRO("Cinto do passageiro", "Passageiro"),
        TRASEIRO_ESQ("Cinto traseiro esq.", "Tras. esq."),
        TRASEIRO_CENTRO("Cinto traseiro centro", "Tras. centro"),
        TRASEIRO_DIR("Cinto traseiro dir.", "Tras. dir."),
    }

    /** Ordem de `car.basic.window_status`, a mesma das portas. */
    enum class Vidro(val rotulo: String) {
        MOTORISTA("Vidro do motorista"),
        PASSAGEIRO("Vidro do passageiro"),
        TRASEIRO_ESQ("Vidro traseiro esq."),
        TRASEIRO_DIR("Vidro traseiro dir."),
    }

    /** Ordem de `car.basic.tpms_status`, igual à das portas: frente, depois trás. */
    enum class Roda(val rotulo: String) {
        DIANTEIRA_ESQ("DE"),
        DIANTEIRA_DIR("DD"),
        TRASEIRA_ESQ("TE"),
        TRASEIRA_DIR("TD"),
    }

    companion object {

        /**
         * Monta o painel a partir dos valores crus.
         *
         * Recebe os textos, e não um mapa de chaves, porque o núcleo não conhece
         * os nomes das propriedades da GWM — quem os conhece é a camada que fala
         * com a central. Assim esta função é testável escrevendo `"{0,0,1,0}"`.
         *
         * Propriedade que não chegou vira lista vazia, não zeros: dizer "está
         * tudo fechado" sem ter lido nada seria inventar uma garantia.
         */
        fun ler(
            portas: String? = null,
            cintos: String? = null,
            vidros: String? = null,
            tetoSolar: String? = null,
            pneus: String? = null,
            unidadePneus: String? = null,
        ): PainelDoVeiculo {
            return PainelDoVeiculo(
                aberturas = combinar(Abertura.entries, portas),
                cintos = combinar(Assento.entries, cintos),
                // O vidro é o único que fala ao contrário: fechado é `1`.
                vidros = combinar(Vidro.entries, vidros, fechado = 1.0),
                tetoSolarAberto = ligado(tetoSolar),
                pneus = lerPneus(pneus),
                unidadeDePressao = unidadeDePressao(unidadePneus),
            )
        }

        private fun <T> acionados(lista: List<Estado<T>>) =
            lista.filter { it.acionado }.map { it.oQue }

        /**
         * Casa cada posição da lista com o item de mesmo índice.
         *
         * Qualquer coisa diferente de [fechado] conta como acionado, porque há
         * códigos intermediários — vidro em movimento aparece como `3` — e
         * todos significam "não está fechado", que é o que interessa mostrar.
         *
         * O valor de repouso muda conforme a propriedade, e isso não é detalhe:
         * porta e cinto descansam em `0`, mas o vidro fechado publica `1`. Com
         * a regra única de "diferente de zero é aberto", o H6 parado de janelas
         * fechadas acusava os quatro vidros abertos — foi o que apareceu no
         * primeiro teste no carro.
         *
         * Sobra na lista é ignorada, e falta também: uma versão de firmware que
         * publique uma posição a mais não pode derrubar a tela inteira.
         */
        private fun <T> combinar(
            itens: List<T>,
            bruto: String?,
            fechado: Double = 0.0,
        ): List<Estado<T>> {
            val numeros = Unidades.lerNumeros(bruto) ?: return emptyList()
            return itens.take(numeros.size).mapIndexed { i, item ->
                Estado(item, numeros[i] != fechado)
            }
        }

        /** Um valor solto, sem chaves: `"0"` é desligado, o resto é ligado. */
        private fun ligado(bruto: String?): Boolean? =
            bruto?.trim()?.toDoubleOrNull()?.let { it != 0.0 }

        /**
         * Os quatro pneus a partir de `car.basic.tpms_status`.
         *
         * A propriedade traz **oito** números, não quatro: é um par por roda,
         * pressão em bar e temperatura em grau. Lida como quatro pressões — que
         * foi o erro da primeira versão — o app mostrava `2,4 / 32,0` de cada
         * lado: a pressão do dianteiro esquerdo e a *temperatura* dele no lugar
         * do dianteiro direito.
         *
         * O formato antigo, de só quatro números, continua aceito: nenhum outro
         * H6 foi lido ainda, e derrubar a tela por causa de um firmware
         * diferente seria pior que mostrar a pressão sem a temperatura.
         */
        private fun lerPneus(bruto: String?): List<Pneu> {
            val numeros = Unidades.lerNumeros(bruto) ?: return emptyList()
            val comTemperatura = numeros.size >= Roda.entries.size * 2

            // Zero num pneu é sensor sem resposta, não pneu vazio: um pneu com
            // zero de pressão não roda. Melhor um traço na tela.
            fun valor(n: Double?) = n?.takeIf { it > 0.0 }

            return Roda.entries.mapIndexedNotNull { i, roda ->
                if (comTemperatura) {
                    Pneu(roda, valor(numeros.getOrNull(i * 2)), numeros.getOrNull(i * 2 + 1))
                } else {
                    if (i >= numeros.size) null else Pneu(roda, valor(numeros[i]))
                }
            }
        }

        /**
         * Em que unidade mostrar a pressão, pelo código de `car.basic.tpms_units`.
         *
         * O código `1` é psi: no H6 lido em 02/09/2026 ele vinha `1` com o
         * cluster mostrando `34.1 psi` para uma pressão publicada como `2,297`
         * — que é a mesma coisa em bar. Os outros códigos ninguém viu ainda, e
         * por isso o padrão é bar, que é a unidade em que o carro **publica**:
         * na dúvida, mostrar o número cru é melhor que convertê-lo errado.
         */
        private fun unidadeDePressao(codigo: String?): Unidade =
            when (codigo?.trim()?.toDoubleOrNull()?.toInt()) {
                1 -> Unidade.PSI
                else -> Unidade.BAR
            }
    }
}
