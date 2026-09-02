package br.com.hugolumazzini.havaltrip.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import br.com.hugolumazzini.havaltrip.BuildConfig

/**
 * Uma entrada de valores à mão, pelo `adb`, para conferir a tela sem o carro.
 *
 * Porta aberta, cinto solto e pneu vazio são estados que não dá para produzir
 * na mesa: o simulador publica um carro inteiro e saudável, e esperar acontecer
 * de verdade significa testar dirigindo — que é o pior lugar para descobrir que
 * o aviso não aparece.
 *
 * Exemplo, com o app aberto no emulador:
 *
 * ```
 * adb shell am broadcast -p br.com.hugolumazzini.havaltrip \
 *   -a br.com.hugolumazzini.havaltrip.BANCADA \
 *   -e chave car.basic.door_status -e valor '{1,0,0,0,0,0}'
 * ```
 *
 * O valor fica **travado**: o simulador para de mexer nessa chave até vir um
 * `BANCADA_LIMPAR`. Sem travar, o próximo segundo apagaria a porta aberta.
 *
 * Duas travas, e as duas são necessárias. A primeira é o build: em release nada
 * é registrado. A segunda é a fonte: mesmo em debug, a bancada só aceita valor
 * enquanto o app está no simulador. É essa que importa de verdade, porque o APK
 * que vai para a central é justamente o de debug — sem ela, qualquer aplicativo
 * instalado no carro poderia apagar um aviso de porta aberta.
 */
object BancadaDeTestes {

    const val ACAO = "br.com.hugolumazzini.havaltrip.BANCADA"
    const val ACAO_LIMPAR = "br.com.hugolumazzini.havaltrip.BANCADA_LIMPAR"

    /**
     * Liga a bancada, se este for um build de debug.
     *
     * Devolve o que fazer para desligá-la — `null` em release, onde nada foi
     * registrado.
     */
    fun ligar(
        context: Context,
        estado: EstadoDoCarro,
        /** Se o app está no simulador. Falso no carro, e aí a bancada é inerte. */
        noSimulador: () -> Boolean,
    ): (() -> Unit)? {
        if (!BuildConfig.DEBUG) return null

        val receptor = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (!noSimulador()) return
                when (intent?.action) {
                    ACAO -> {
                        val chave = intent.getStringExtra("chave") ?: return
                        val valor = intent.getStringExtra("valor") ?: return
                        estado.travar(chave, valor)
                    }
                    ACAO_LIMPAR -> estado.destravar()
                }
            }
        }

        val filtro = IntentFilter().apply {
            addAction(ACAO)
            addAction(ACAO_LIMPAR)
        }
        ContextCompat.registerReceiver(context, receptor, filtro, ContextCompat.RECEIVER_EXPORTED)
        return { runCatching { context.unregisterReceiver(receptor) } }
    }
}
