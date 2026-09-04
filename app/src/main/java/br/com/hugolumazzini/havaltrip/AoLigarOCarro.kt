package br.com.hugolumazzini.havaltrip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Liga a contagem junto com o carro.
 *
 * Na central do H6 não existe "ligar a multimídia": ela nasce quando o carro
 * ganha energia e morre quando o carro é desligado. Então o aviso de que o
 * sistema terminou de iniciar *é* o aviso de que a viagem começou — é o mais
 * perto de "girou a chave" que um aplicativo consegue chegar sem depender do
 * Shizuku já estar autorizado.
 *
 * Não abre tela nenhuma, de propósito: quem liga o carro pode querer o rádio ou
 * o mapa, e um computador de bordo que se põe na frente toda manhã vira algo
 * que se desinstala. Ele conta calado; a tela é para quando você quiser olhar.
 *
 * `QUICKBOOT_POWERON` está na lista porque muita central não desliga de fato —
 * hiberna —, e nessas o Android manda esse aviso no lugar do de inicialização.
 */
class AoLigarOCarro : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            // Depois de uma atualização pela loja, o serviço antigo foi morto
            // junto com o app velho. Sem isto, a contagem só voltaria na
            // próxima vez que o carro fosse ligado.
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // Envolvido porque isto roda durante a inicialização da central:
            // uma exceção aqui não é um app que não conta, é um "o aplicativo
            // parou" na cara de quem acabou de ligar o carro.
            -> runCatching { ServicoDeBordo.garantir(context) }
        }
    }
}
