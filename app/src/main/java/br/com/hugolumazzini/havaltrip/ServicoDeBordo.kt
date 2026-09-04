package br.com.hugolumazzini.havaltrip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * O que mantém o computador de bordo contando sem ninguém olhar.
 *
 * O Android desliga um processo sem tela quando precisa de memória, e a central
 * do H6 é apertada. Um serviço em primeiro plano é a única forma de dizer ao
 * sistema "isto aqui está trabalhando para a pessoa agora" e não ser morto no
 * meio da viagem — e ele não faz nada além de segurar o processo: quem conta é
 * o [MotorDeBordo], que ele apenas obriga a existir.
 *
 * O preço é a notificação: o Android não deixa um serviço desses ficar
 * invisível. Ela é criada no canal mais silencioso que existe — sem som, sem
 * vibração, sem aparecer na tela como aviso —, então fica só na gaveta.
 */
class ServicoDeBordo : Service() {

    override fun onCreate() {
        super.onCreate()
        // Do Android 12 em diante o sistema pode recusar a promoção a primeiro
        // plano dependendo de quem pediu o serviço. Recusa não é motivo para
        // derrubar o app: sem a promoção ele vira um serviço comum, que conta
        // igual e só fica mais sujeito a ser morto se faltar memória. A central
        // do H6 é Android 9, onde a recusa não existe.
        runCatching { startForeground(ID_DA_NOTIFICACAO, montarNotificacao()) }
        // Basta pedir: o motor começa a escutar o carro no próprio construtor.
        MotorDeBordo.de(this)
    }

    /**
     * `START_STICKY`: se o sistema matar o serviço por falta de memória, que o
     * recrie sozinho. Sem isso, uma limpeza de memória no meio do trajeto
     * pararia a contagem em silêncio, e o motorista só descobriria no fim.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Última chance de gravar: o que está em memória e não foi para o
        // arquivo vira quilômetro perdido.
        MotorDeBordo.de(this).gravarAgora()
        super.onDestroy()
    }

    private fun montarNotificacao(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL,
                "Contagem em segundo plano",
                // MIN é o mais discreto que o sistema aceita para um serviço em
                // primeiro plano: sem som, sem vibrar, sem saltar na tela.
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Mantém as viagens sendo contadas com o app fechado."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }

        val abrir = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0,
        )

        return NotificationCompat.Builder(this, CANAL)
            .setContentTitle("Haval Trip")
            .setContentText("Contando a viagem")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(abrir)
            .build()
    }

    companion object {

        private const val CANAL = "bordo"
        private const val ID_DA_NOTIFICACAO = 1

        /**
         * Garante o serviço rodando. Chamar duas vezes não faz mal: o Android
         * entrega no mesmo serviço em vez de criar outro.
         */
        fun garantir(context: Context) {
            val pedido = Intent(context, ServicoDeBordo::class.java)
            // Duas tentativas, e a segunda não é redundância. Do Android 12 em
            // diante só certos gatilhos podem acordar um serviço de primeiro
            // plano com o app fechado — a inicialização do sistema é um deles,
            // mas nem toda central manda o aviso padrão. Quando o sistema
            // recusa, o serviço comum ainda é aceito, e é melhor contar sem a
            // proteção contra ser morto do que não contar.
            runCatching { ContextCompat.startForegroundService(context, pedido) }
                .recoverCatching { context.startService(pedido) }
        }
    }
}
