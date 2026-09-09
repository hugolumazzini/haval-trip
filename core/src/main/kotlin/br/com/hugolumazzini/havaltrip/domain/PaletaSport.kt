package br.com.hugolumazzini.havaltrip.domain

/**
 * As sete paletas do tema "Sport Colors" do Impulse.
 *
 * Os nomes são exatamente como o Impulse os grava na preferência
 * `currentClusterColor`, e as cores são exatamente as do CSS dele. Isso é uma
 * cópia de um detalhe de outro app, então vive aqui, num só lugar e com teste:
 * quando o Impulse mudar um nome, o teste é o que vai dizer onde consertar, em
 * vez de o motorista descobrir pelo número branco no painel.
 *
 * [primaria] é a cor de assinatura — ponteiro, anéis, a amostra no menu do
 * Impulse. [clara] é a variante legível sobre fundo escuro, e é dela que sai o
 * brilho em volta dos nossos números: a primária do Dark Blue é escura demais
 * para se ver sobre o painel.
 */
enum class PaletaSport(val rotulo: String, val primaria: Long, val clara: Long) {
    RED_SPORT("Red Sport", 0xFFE33149, 0xFFFF766F),
    RED_GT("Red GT", 0xFFFF2020, 0xFFFF6B6B),
    OCEAN_BLUE("Ocean Blue", 0xFF22B8FF, 0xFF7ED9FF),
    GREEN("Green", 0xFF00C878, 0xFF63F0AE),
    DARK_BLUE("Dark Blue", 0xFF164A8A, 0xFF4F8FD4),
    AMBER_GOLD("Amber Gold", 0xFFFF9F1C, 0xFFFFD166),
    PURPLE_GT("Purple GT", 0xFF9B5CFF, 0xFFC7A0FF);

    companion object {

        /**
         * A cor do número no modo Analógico V2: um branco levemente frio.
         *
         * No Impulse o dígito não é pintado com a cor da paleta — ele é quase
         * branco, e quem carrega a cor é o brilho em volta. Copiar isso é o
         * que faz o nosso bloco parecer o mesmo painel, e não um app colado
         * por cima com a cor aproximada.
         */
        const val BRANCO_DO_ANALOGICO_V2 = 0xFFF9FCFF

        /** A chave que o Impulse grava quando alguém escolhe a paleta. */
        const val CHAVE = "currentClusterColor"

        /**
         * A paleta descrita num arquivo de `SharedPreferences` do Impulse, ou
         * `null` se ele não disser — arquivo sem a chave, ou com um nome que
         * este app ainda não conhece.
         *
         * Ler XML com expressão regular seria temerário num arquivo qualquer;
         * aqui é aceitável porque o formato não é livre: quem escreve é o
         * próprio Android, sempre na mesma forma, numa linha por chave.
         */
        fun doXml(xml: String): PaletaSport? {
            val nome = Regex("""<string name="$CHAVE">([^<]*)</string>""")
                .find(xml)?.groupValues?.get(1)?.trim()?.ifBlank { null }
                ?: return null
            return entries.find { it.rotulo == nome }
        }
    }
}
