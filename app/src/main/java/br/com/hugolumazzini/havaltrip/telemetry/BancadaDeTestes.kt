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
 * Só existe em build de debug. Não é um recurso escondido do app publicado: um
 * broadcast aberto que aceita mentir sobre o estado do carro é exatamente o que
 * não pode existir num aparelho dentro do veículo.
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
    fun ligar(context: Context, estado: EstadoDoCarro): (() -> Unit)? {
        if (!BuildConfig.DEBUG) return null

        val receptor = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
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
