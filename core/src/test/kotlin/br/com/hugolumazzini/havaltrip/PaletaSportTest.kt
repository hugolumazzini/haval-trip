package br.com.hugolumazzini.havaltrip

import br.com.hugolumazzini.havaltrip.domain.PaletaSport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletaSportTest {

    /** Um arquivo como o Android escreve, com a chave no meio de outras. */
    private fun prefs(valor: String) = """
        <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
        <map>
            <boolean name="bottomBarEnabled" value="true" />
            <string name="${PaletaSport.CHAVE}">$valor</string>
            <int name="overscanTop" value="62" />
        </map>
    """.trimIndent()

    @Test
    fun `le a paleta escolhida no Impulse`() {
        assertEquals(PaletaSport.OCEAN_BLUE, PaletaSport.doXml(prefs("Ocean Blue")))
        assertEquals(PaletaSport.RED_SPORT, PaletaSport.doXml(prefs("Red Sport")))
        assertEquals(PaletaSport.PURPLE_GT, PaletaSport.doXml(prefs("Purple GT")))
    }

    @Test
    fun `as sete paletas do tema estao todas aqui`() {
        // O tema Sport Colors oferece sete no menu do volante. Se o Impulse
        // acrescentar uma oitava, quem escolher ela no volante cai no branco —
        // e este teste é onde a conta não vai fechar.
        assertEquals(7, PaletaSport.entries.size)
    }

    @Test
    fun `nome que nao existe nao vira paleta`() {
        // Vale mais devolver "não sei" do que adivinhar a mais parecida: um
        // palpite errado pinta o painel de uma cor que ninguém escolheu.
        assertNull(PaletaSport.doXml(prefs("Turquesa")))
    }

    @Test
    fun `arquivo sem a chave nao vira paleta`() {
        assertNull(PaletaSport.doXml("<map><int name=\"x\" value=\"1\" /></map>"))
        assertNull(PaletaSport.doXml(prefs("")))
        assertNull(PaletaSport.doXml(""))
    }

    @Test
    fun `a cor clara e sempre mais clara que a primaria`() {
        // É a razão de a `clara` existir: o brilho em volta do número tem de
        // aparecer sobre o fundo escuro do painel. A primária do Dark Blue,
        // por exemplo, é escura demais para esse papel.
        PaletaSport.entries.forEach { p ->
            assertTrue("${p.rotulo}: clara não é mais clara", luz(p.clara) > luz(p.primaria))
        }
    }

    @Test
    fun `toda paleta e opaca`() {
        // Um alfa esquecido viraria um número invisível no painel.
        PaletaSport.entries.forEach { p ->
            assertEquals("${p.rotulo}: clara", 0xFF, ((p.clara shr 24) and 0xFF).toInt())
            assertEquals("${p.rotulo}: primaria", 0xFF, ((p.primaria shr 24) and 0xFF).toInt())
        }
    }

    private fun luz(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toDouble()
        val g = ((argb shr 8) and 0xFF).toDouble()
        val b = (argb and 0xFF).toDouble()
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
