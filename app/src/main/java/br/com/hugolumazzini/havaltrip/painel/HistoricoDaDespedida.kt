package br.com.hugolumazzini.havaltrip.painel

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Histórico de eventos da despedida para diagnóstico visual no app.
 *
 * Como não há como ver logs ADB no carro, este histórico pode ser
 * mostrado numa tela de diagnóstico dentro do próprio app.
 */
object HistoricoDaDespedida {

    data class Evento(
        val hora: String,
        val tipo: TipoEvento,
        val descricao: String
    )

    enum class TipoEvento {
        ATIVACAO,
        DESATIVACAO,
        TENTATIVA_REMOCAO,
        BLOQUEIO,
        PROJECAO,
        ERRO
    }

    private val eventos = mutableListOf<Evento>()
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private const val MAX_EVENTOS = 50

    /**
     * Registra ativação da despedida
     */
    fun ativou(chamador: String = obterChamador()) {
        adicionar(
            TipoEvento.ATIVACAO,
            "✅ Despedida ATIVADA por $chamador"
        )
    }

    /**
     * Registra desativação da despedida
     */
    fun desativou(chamador: String = obterChamador()) {
        adicionar(
            TipoEvento.DESATIVACAO,
            "❌ Despedida DESATIVADA por $chamador"
        )
    }

    /**
     * Registra tentativa de remoção de overlay
     */
    fun tentouRemover(janela: String, chamador: String = obterChamador()) {
        adicionar(
            TipoEvento.TENTATIVA_REMOCAO,
            "⚠️ $chamador tentou remover $janela"
        )
    }

    /**
     * Registra bloqueio de remoção
     */
    fun bloqueou(janela: String, chamador: String = obterChamador()) {
        adicionar(
            TipoEvento.BLOQUEIO,
            "🛡️ BLOQUEADO: $chamador NÃO removeu $janela (despedida ativa)"
        )
    }

    /**
     * Registra projeção da despedida
     */
    fun projetou() {
        adicionar(
            TipoEvento.PROJECAO,
            "📽️ Despedida PROJETADA em tela cheia"
        )
    }

    /**
     * Registra erro
     */
    fun erro(mensagem: String) {
        adicionar(
            TipoEvento.ERRO,
            "💥 ERRO: $mensagem"
        )
    }

    /**
     * Obtém todos os eventos (mais recente primeiro)
     */
    fun obterEventos(): List<Evento> {
        return eventos.reversed()
    }

    /**
     * Limpa histórico
     */
    fun limpar() {
        eventos.clear()
        adicionar(TipoEvento.ATIVACAO, "🧹 Histórico limpo")
    }

    private fun adicionar(tipo: TipoEvento, descricao: String) {
        val agora = LocalDateTime.now()
        val evento = Evento(
            hora = agora.format(formatter),
            tipo = tipo,
            descricao = descricao
        )

        synchronized(eventos) {
            eventos.add(evento)
            // Mantém apenas os últimos N eventos
            if (eventos.size > MAX_EVENTOS) {
                eventos.removeAt(0)
            }
        }
    }

    /**
     * Tenta descobrir quem chamou via stack trace
     */
    private fun obterChamador(): String {
        val stack = Thread.currentThread().stackTrace
        // Pula: getStackTrace, obterChamador, função que chamou obterChamador
        val index = 3
        return if (index < stack.size) {
            val frame = stack[index]
            val classe = frame.className.substringAfterLast('.')
            val metodo = frame.methodName
            "$classe.$metodo"
        } else {
            "desconhecido"
        }
    }
}
