package br.com.hugolumazzini.havaltrip.telemetry

import br.com.hugolumazzini.havaltrip.domain.PaletaSport
import br.com.hugolumazzini.havaltrip.painel.ShizukuShell

/**
 * Qual paleta de cores está escolhida no Impulse, para o nosso bloco seguir.
 *
 * O tema "Sport Colors" do Impulse tem sete paletas, e quem troca de paleta lá
 * espera que o painel inteiro acompanhe — inclusive o nosso pedaço dele. Sem
 * isto, trocar para Ocean Blue no Impulse deixaria o resumo de viagem vermelho
 * no meio de um cluster azul.
 *
 * O Impulse não conta a ninguém qual paleta está ativa: não há provider nem
 * broadcast de tema (o único público, `com.haval.vehicle.EVENT_CHANGED`, é só
 * telemetria do carro). O que existe é o arquivo de preferências dele, e é o
 * que se lê aqui — pelo Shizuku, que é o mesmo caminho privilegiado que o
 * próprio Impulse usa.
 *
 * Isso torna a leitura um palpite educado sobre um detalhe interno de outro
 * app: se ele mudar o nome da chave, ou se o Shizuku da central não tiver
 * privilégio para ler a pasta, não dá para saber a paleta. Nesse caso a função
 * devolve `null` e a tela cai na cor manual, dizendo o motivo — melhor um
 * número branco com uma explicação do que um número invisível.
 */
object PaletaDoImpulse {

    /**
     * O arquivo de preferências do Impulse.
     *
     * Fica em `user_de` — o armazenamento "protegido por dispositivo", que é
     * legível antes de alguém desbloquear a tela — e não no `/data/data` de
     * sempre, porque o Impulse precisa dessas preferências durante a partida
     * do carro, antes de qualquer desbloqueio.
     */
    private const val PREFS =
        "/data/user_de/0/br.com.redesurftank.havalshisuku/shared_prefs/haval_prefs.xml"

    /** Por que não deu para saber a paleta, quando não deu. */
    sealed interface Resultado {
        data class Achou(val paleta: PaletaSport) : Resultado
        data object SemShizuku : Resultado
        data class NaoDeuParaLer(val motivo: String) : Resultado
    }

    /**
     * Lê a paleta ativa. Chamada de fora da thread principal: abre um processo.
     */
    fun ler(): Resultado {
        if (!ShizukuTelemetrySource.disponivel() || !ShizukuTelemetrySource.autorizado()) {
            return Resultado.SemShizuku
        }
        val xml = catDoArquivo() ?: return Resultado.NaoDeuParaLer(
            "o Shizuku da central não conseguiu ler as preferências do Impulse",
        )
        val paleta = PaletaSport.doXml(xml)
            ?: return Resultado.NaoDeuParaLer("o Impulse não gravou uma paleta conhecida")
        return Resultado.Achou(paleta)
    }

    /**
     * O conteúdo do arquivo, lido por um `cat` com o privilégio do Shizuku: a
     * pasta do Impulse não é legível por um app comum, e não há caminho público
     * para ela.
     */
    private fun catDoArquivo(): String? = ShizukuShell.rodar("cat $PREFS")?.ifBlank { null }
}
