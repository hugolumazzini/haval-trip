package br.com.hugolumazzini.havaltrip.telemetry

import android.content.Context

/**
 * Onde mora o token do GitHub.
 *
 * Fica só no aparelho, no armazenamento privado do app — nunca no código, nunca
 * no repositório. Um token é uma chave: se ele viajasse junto com o
 * código-fonte, que é público, qualquer pessoa escreveria no repositório de
 * relatórios. Por isso ele é digitado uma vez, dentro do carro, e fica.
 *
 * Não está cifrado. Numa central que ninguém mais usa, cifrar só protegeria
 * contra quem já tem acesso de root ao aparelho — e contra esse, a cifra
 * também cai, porque a chave dela moraria no mesmo lugar. O que de fato
 * protege é o token ser restrito a um repositório só, sem permissão nenhuma
 * fora dele.
 */
class Cofre(context: Context) {

    private val prefs = context.getSharedPreferences("haval-trip-cofre", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(CHAVE_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(valor) {
            prefs.edit().apply {
                if (valor.isNullOrBlank()) remove(CHAVE_TOKEN) else putString(CHAVE_TOKEN, valor.trim())
            }.apply()
        }

    val temToken: Boolean get() = token != null

    private companion object {
        const val CHAVE_TOKEN = "github_token"
    }
}
