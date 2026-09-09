package br.com.hugolumazzini.havaltrip.painel

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

private const val TAG = "ShizukuShell"

/** Código do nosso pedido de permissão; qualquer número serve, só precisa bater. */
private const val PEDIDO_DE_PERMISSAO = 4322

/**
 * Rodar um comando com o privilégio do Shizuku.
 *
 * É a única coisa que o Impulse tem e um app comum não: ele não possui poder
 * nenhum sobre as telas do carro por si só — tudo o que faz na tela "Telas"
 * passa por uma função de três linhas que entrega um `sh -c` ao Shizuku, e o
 * Shizuku executa como o usuário `shell`, o mesmo do adb. É esse usuário que
 * pode mandar uma janela para o painel de instrumentos.
 *
 * Ou seja: a dependência real nunca foi do Impulse, foi do Shizuku. Este
 * arquivo é o que nos tira do meio dele — ver [ProjetorDoPainel].
 */
object ShizukuShell {

    /**
     * Em que pé está a linha privilegiada.
     *
     * São três estados separados porque cada um tem um conserto diferente, e
     * dentro do carro adivinhar qual é sai caro: instalar/iniciar o Shizuku,
     * tocar em "permitir", ou nada — já dá para usar.
     */
    enum class Situacao { SEM_SHIZUKU, PRECISA_AUTORIZAR, PRONTO }

    fun situacao(): Situacao = runCatching {
        when {
            !Shizuku.pingBinder() -> Situacao.SEM_SHIZUKU
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Situacao.PRONTO
            else -> Situacao.PRECISA_AUTORIZAR
        }
    }.getOrDefault(Situacao.SEM_SHIZUKU)

    /** Abre o diálogo do próprio Shizuku. Sem efeito se ele não estiver rodando. */
    fun pedirPermissao() {
        runCatching { Shizuku.requestPermission(PEDIDO_DE_PERMISSAO) }
            .onFailure { Log.w(TAG, "não deu para pedir a permissão do Shizuku", it) }
    }

    /**
     * Roda o comando e devolve a saída, ou `null` se nem deu para executar.
     *
     * Chamar de fora da thread principal: abre um processo e espera por ele.
     *
     * `newProcess` é API interna da biblioteca do Shizuku — daí a reflexão. É o
     * que existe: não há caminho público para rodar comando privilegiado, e é
     * exatamente por aqui que o Impulse passa (ele usa o binder do serviço
     * direto, que é a mesma porta por outro corredor).
     *
     * O `2>&1` junta o erro à saída de propósito: quando um `am start` falha,
     * a explicação vem pela saída de erro, e perdê-la deixaria só um silêncio
     * impossível de diagnosticar dentro do carro.
     */
    fun rodar(comando: String): String? = runCatching {
        val metodo = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }

        val processo = metodo.invoke(null, arrayOf("sh", "-c", "$comando 2>&1"), null, null)
            as java.lang.Process

        val saida = processo.inputStream.bufferedReader().use { it.readText() }
        processo.waitFor()
        Log.d(TAG, "$comando -> $saida")
        saida
    }.onFailure { Log.w(TAG, "não deu para rodar: $comando", it) }.getOrNull()
}
