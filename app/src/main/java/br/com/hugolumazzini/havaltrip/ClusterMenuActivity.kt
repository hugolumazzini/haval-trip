package br.com.hugolumazzini.havaltrip

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import br.com.hugolumazzini.havaltrip.painel.JanelaDoPainel
import br.com.hugolumazzini.havaltrip.painel.TeclaDoVolante
import br.com.hugolumazzini.havaltrip.painel.TecladoDoVolante
import br.com.hugolumazzini.havaltrip.painel.fecharQuandoPedirem
import br.com.hugolumazzini.havaltrip.ui.ClusterMenuScreen
import br.com.hugolumazzini.havaltrip.ui.theme.HavalTripTheme

/**
 * A página do painel com várias visões.
 *
 * Terceira janela, com `taskAffinity` própria pelo mesmo motivo das outras
 * duas: uma tarefa só pode estar num display de cada vez, e sem afinidade
 * separada abrir esta arrastaria as outras junto.
 *
 * Ver [br.com.hugolumazzini.havaltrip.ui.ClusterMenuScreen] para o que ela
 * mostra e por quê.
 */
class ClusterMenuActivity : ComponentActivity() {

    private val vm: TripViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cluster.iniciar(this)
        Cluster.atualizarPaleta()
        ServicoDeBordo.garantir(this)
        val espiando = intent.getBooleanExtra(ESPIANDO, false)
        if (!espiando) fecharQuandoPedirem(JanelaDoPainel.MENU)
        setContent {
            HavalTripTheme {
                ClusterMenuScreen(vm, espiando = espiando)
            }
        }
    }

    /**
     * A cruzinha de um teclado comum, para poder testar sem volante.
     *
     * No carro isto não é o caminho: lá as teclas vêm pelo serviço da central,
     * com códigos próprios, e a janela do painel nem tem o foco do sistema. Mas
     * numa central com teclado, ou no emulador com
     * `adb shell input keyevent 20`, é o jeito mais direto de ver a página
     * andar — e cai no mesmo lugar que o volante cairia.
     */
    override fun onKeyDown(codigo: Int, evento: KeyEvent?): Boolean {
        val tecla = when (codigo) {
            KeyEvent.KEYCODE_DPAD_UP -> TeclaDoVolante.CIMA
            KeyEvent.KEYCODE_DPAD_DOWN -> TeclaDoVolante.BAIXO
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> TeclaDoVolante.ENTRA
            else -> return super.onKeyDown(codigo, evento)
        }
        TecladoDoVolante.simular(tecla)
        return true
    }
}
