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
    val unidadeDePressao: String? = null,
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

    data class Pneu(val roda: Roda, val pressao: Double?)

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

    /** Ordem de `car.basic.seat_belt_warning`: motorista, passageiro, atrás. */
    enum class Assento(val rotulo: String, val curto: String) {
        MOTORISTA("Cinto do motorista", "Motorista"),
        PASSAGEIRO("Cinto do passageiro", "Passageiro"),
        TRASEIROS("Cintos traseiros", "Traseiros"),
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
            val rodas = lerPneus(pneus)
            return PainelDoVeiculo(
                aberturas = combinar(Abertura.entries, portas),
                cintos = combinar(Assento.entries, cintos),
                vidros = combinar(Vidro.entries, vidros),
                tetoSolarAberto = ligado(tetoSolar),
                pneus = rodas,
                unidadeDePressao = unidadeDePressao(rodas),
            )
        }

        private fun <T> acionados(lista: List<Estado<T>>) =
            lista.filter { it.acionado }.map { it.oQue }

        /**
         * Casa cada posição da lista com o item de mesmo índice.
         *
         * Qualquer coisa diferente de zero conta como acionado. O carro publica
         * `1` para aberto na maior parte das propriedades, mas há códigos
         * intermediários — vidro em movimento, por exemplo — e todos significam
         * "não está fechado", que é o que interessa mostrar.
         *
         * Sobra na lista é ignorada, e falta também: uma versão de firmware que
         * publique uma posição a mais não pode derrubar a tela inteira.
         */
        private fun <T> combinar(itens: List<T>, bruto: String?): List<Estado<T>> {
            val numeros = Unidades.lerNumeros(bruto) ?: return emptyList()
            return itens.take(numeros.size).mapIndexed { i, item ->
                Estado(item, numeros[i] != 0.0)
            }
        }

        /** Um valor solto, sem chaves: `"0"` é desligado, o resto é ligado. */
        private fun ligado(bruto: String?): Boolean? =
            bruto?.trim()?.toDoubleOrNull()?.let { it != 0.0 }

        private fun lerPneus(bruto: String?): List<Pneu> {
            val numeros = Unidades.lerNumeros(bruto) ?: return emptyList()
            return Roda.entries.take(numeros.size).mapIndexed { i, roda ->
                // Zero num pneu é sensor sem resposta, não pneu vazio: um pneu
                // com zero de pressão não roda. Melhor um traço na tela.
                Pneu(roda, numeros[i].takeIf { it > 0.0 })
            }
        }

        /**
         * Em que unidade as pressões estão.
         *
         * O carro publica um código em `car.basic.tpms_units`, mas ele ainda não
         * foi decifrado dentro do H6 — e chutar a tabela erraria calado. A ordem
         * de grandeza, ao contrário, é inequívoca: nenhum pneu de passeio tem 30
         * bar nem 2 kPa. O código cru continua indo para o diagnóstico, para que
         * um dia isto vire leitura de verdade.
         */
        private fun unidadeDePressao(pneus: List<Pneu>): String? {
            val maior = pneus.mapNotNull { it.pressao }.maxOrNull() ?: return null
            return when {
                maior >= 100.0 -> "kPa"
                maior >= 10.0 -> "psi"
                else -> "bar"
            }
        }
    }
}
