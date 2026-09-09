package br.com.hugolumazzini.havaltrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import br.com.hugolumazzini.havaltrip.painel.JanelaDoPainel
import br.com.hugolumazzini.havaltrip.painel.fecharQuandoPedirem
import br.com.hugolumazzini.havaltrip.ui.ClusterScreen
import br.com.hugolumazzini.havaltrip.ui.theme.HavalTripTheme

/**
 * A cara do Haval Trip no painel de instrumentos.
 *
 * Não desenha no cluster por conta própria: quem coloca esta tela lá é o
 * Impulse, na configuração "Telas", que roda um `am start … --display 3
 * --windowingMode 5` via Shizuku — janela livre, num retângulo que o motorista
 * escolhe. Por isso ela é `exported`: o comando vem de fora do app.
 *
 * Consequência prática do modo janela livre: o tamanho é imprevisível, então o
 * desenho não pode supor largura nenhuma — ver [ClusterScreen].
 *
 * Usa o mesmo [MotorDeBordo] da tela grande (o [TripViewModel] o obtém como
 * instância única), e é isso que garante que o número do painel e o número da
 * central sejam o mesmo número, e não duas contagens paralelas.
 */
class ClusterActivity : ComponentActivity() {

    private val vm: TripViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sem `enableEdgeToEdge`: aqui não há barra de status nem de navegação
        // para desviar, e a janela é um retângulo qualquer no meio do painel.
        Cluster.iniciar(this)
        // Perguntada aqui, e nao uma vez so na partida, porque a paleta pode
        // ter mudado no volante desde a ultima vez que esta janela abriu.
        Cluster.atualizarPaleta()
        ServicoDeBordo.garantir(this)
        val espiando = intent.getBooleanExtra(ESPIANDO, false)
        // Espiando é a conferência aqui na central, com o botão Voltar à mão;
        // recolher é para a janela que está lá no painel, fora de alcance.
        if (!espiando) fecharQuandoPedirem(JanelaDoPainel.NUMEROS)
        setContent {
            HavalTripTheme {
                ClusterScreen(vm, espiando = espiando)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.gravarAgora()
    }
}

/**
 * Extra que a tela de configuração manda ao abrir uma das janelas do painel só
 * para conferir o ajuste: pinta um fundo escuro, já que na central não há
 * painel de carro atrás para aparecer pela transparência.
 */
const val ESPIANDO = "espiando"
