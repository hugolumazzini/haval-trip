package br.com.hugolumazzini.havaltrip

import br.com.hugolumazzini.havaltrip.domain.PilhasDoSistema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A limpeza da despedida não pode, em hipótese alguma, apagar as nossas janelas. */
class PilhasDoSistemaTest {

    private val pacote = "br.com.hugolumazzini.havaltrip"

    // Formato do Android 9: cabeçalho numa linha, tarefas nas seguintes.
    private val saida = """
        Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0
          configuration={1.0 ?mcc?mnc [pt_BR] ldltr sw720dp w1920dp h720dp}
            taskId=2: com.android.launcher3/com.android.launcher3.Launcher bounds=[0,0][1920,720] userId=0 visible=true topActivity=ComponentInfo{com.android.launcher3/com.android.launcher3.Launcher}
        Stack id=5 bounds=[0,0][1080,600] displayId=3 userId=0
          configuration={1.0 ?mcc?mnc [pt_BR] ldltr sw540dp w1080dp h600dp}
            taskId=17: br.com.hugolumazzini.havaltrip/.ClusterActivity bounds=[0,0][1080,600] userId=0 visible=true topActivity=ComponentInfo{br.com.hugolumazzini.havaltrip/br.com.hugolumazzini.havaltrip.ClusterActivity}
        Stack id=6 bounds=[0,0][1080,600] displayId=3 userId=0
          configuration={1.0 ?mcc?mnc [pt_BR] ldltr sw540dp w1080dp h600dp}
            taskId=18: br.com.redesurftank.havalshisuku/.PainelActivity bounds=[0,0][1080,600] userId=0 visible=true topActivity=ComponentInfo{br.com.redesurftank.havalshisuku/.PainelActivity}
        Stack id=7 bounds=[0,0][1080,600] displayId=3 userId=0
          configuration={1.0 ?mcc?mnc [pt_BR] ldltr sw540dp w1080dp h600dp}
            taskId=19: dev.leonardovin.havalbottombar/.BarActivity bounds=[0,0][1080,600] userId=0 visible=true topActivity=ComponentInfo{dev.leonardovin.havalbottombar/.BarActivity}
    """.trimIndent()

    @Test
    fun `nao remove a pilha do proprio app, mesmo com o nome so na linha da tarefa`() {
        val remover = PilhasDoSistema.deOutrosApps(saida, telaId = 3, pacote = pacote)
        assertTrue("a pilha 5 é nossa e não pode ser removida", 5 !in remover)
    }

    @Test
    fun `remove as pilhas de outros apps na tela do painel`() {
        assertEquals(listOf(6, 7), PilhasDoSistema.deOutrosApps(saida, telaId = 3, pacote = pacote))
    }

    @Test
    fun `nao mexe em pilhas de outras telas`() {
        val remover = PilhasDoSistema.deOutrosApps(saida, telaId = 3, pacote = pacote)
        assertTrue("a pilha 0 é da central", 0 !in remover)
    }

    @Test
    fun `duas janelas nossas na mesma tela ficam as duas`() {
        val duas = """
            Stack id=5 bounds=[0,0][1080,600] displayId=3 userId=0
                taskId=17: $pacote/.ClusterActivity bounds=[0,0][1080,600]
            Stack id=8 bounds=[0,0][1080,600] displayId=3 userId=0
                taskId=20: $pacote/.ClusterCarroActivity bounds=[0,0][1080,600]
        """.trimIndent()
        assertTrue(PilhasDoSistema.deOutrosApps(duas, telaId = 3, pacote = pacote).isEmpty())
    }

    @Test
    fun `a ultima pilha da lista tambem e lida`() {
        val pilhas = PilhasDoSistema.listar(saida, pacote)
        assertEquals(listOf(0, 5, 6, 7), pilhas.map { it.id })
        assertEquals(listOf(false, true, false, false), pilhas.map { it.nossa })
    }

    @Test
    fun `saida vazia ou em formato desconhecido nao remove nada`() {
        assertTrue(PilhasDoSistema.deOutrosApps("", 3, pacote).isEmpty())
        assertTrue(PilhasDoSistema.deOutrosApps("Unknown command: stack", 3, pacote).isEmpty())
        assertTrue(PilhasDoSistema.deOutrosApps("Error: Failed to get stacks", 3, pacote).isEmpty())
    }
}
