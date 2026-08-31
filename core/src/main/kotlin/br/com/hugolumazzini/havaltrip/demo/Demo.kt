package br.com.hugolumazzini.havaltrip.demo

import br.com.hugolumazzini.havaltrip.domain.IgnitionState
import br.com.hugolumazzini.havaltrip.domain.TelemetrySample
import br.com.hugolumazzini.havaltrip.domain.Trip
import br.com.hugolumazzini.havaltrip.domain.TripRecord
import br.com.hugolumazzini.havaltrip.engine.TripManager
import br.com.hugolumazzini.havaltrip.format.TripFormat
import br.com.hugolumazzini.havaltrip.services.TripComparison
import br.com.hugolumazzini.havaltrip.storage.FileTripStorage
import br.com.hugolumazzini.havaltrip.storage.SnapshotPolicy
import java.io.File
import kotlin.math.max

/**
 * Simulação de condução passo a passo no terminal.
 *
 *     ./gradlew :core:demo
 *
 * Não é teste — é a maneira de *ver* o módulo funcionando: duas Trips contando
 * ao mesmo tempo, uma delas zerada no meio do caminho, a ignição desligando e
 * voltando, e no fim a comparação de duas viagens arquivadas.
 */
/** Um trecho de condução a velocidade constante. */
private data class Trecho(
    val nome: String,
    val duracaoS: Int,
    val velocidadeKmh: Double,
) {
    /** Litros por hora neste trecho, pela curva de [injecaoLph]. */
    val injecaoLph: Double get() = injecaoLph(velocidadeKmh)
}

/**
 * Quanto o motor bebe a uma dada velocidade, em litros por hora.
 *
 * Marcha lenta, atrito proporcional à velocidade e arrasto do ar, que cresce
 * com o cubo dela. É o termo cúbico que faz o rendimento ter um ponto ótimo por
 * volta dos 60 km/h e piorar na rodovia — sem ele o consumo melhoraria para
 * sempre e a autonomia na tela só subiria, o que nenhum carro faz.
 */
private fun injecaoLph(velocidadeKmh: Double): Double =
    0.8 + 0.055 * velocidadeKmh + 3.2e-6 * velocidadeKmh * velocidadeKmh * velocidadeKmh

fun main() {
    val pasta = File(System.getProperty("java.io.tmpdir"), "haval-trip-demo").apply {
        deleteRecursively()
        mkdirs()
    }

    var agora = 0L
    // Grava a cada 1 km ou 60 s: no demo, cinco minutos de ignição nunca
    // chegariam, e a gravação é justamente uma das coisas a demonstrar.
    val manager = TripManager(
        storage = FileTripStorage(pasta),
        policy = SnapshotPolicy(everyKm = 1.0, everyMs = 60_000L),
        clock = { agora },
    )

    var odometro = 48_213.4
    var tanque = 41.0

    /** Roda um trecho segundo a segundo, alimentando o manager. */
    fun dirigir(trecho: Trecho, ignicao: IgnitionState = IgnitionState.ON) {
        repeat(trecho.duracaoS) {
            agora += 1000
            val litrosNoSegundo = trecho.injecaoLph / 3600.0
            odometro += trecho.velocidadeKmh / 3600.0
            tanque = max(0.0, tanque - litrosNoSegundo)
            manager.processTelemetry(
                TelemetrySample(
                    timestampMs = agora,
                    speedKmh = trecho.velocidadeKmh,
                    fuelRateLph = trecho.injecaoLph,
                    odometerTotalKm = odometro,
                    fuelLevelL = tanque,
                    ignition = ignicao,
                )
            )
        }
    }

    cabecalho("HAVAL TRIP — SIMULAÇÃO DE CONDUÇÃO")
    passo("Chave na ignição")
    manager.handleIgnitionChange(IgnitionState.ON, agora)

    passo("Todos os contadores já estão contando — ninguém precisou iniciá-los")
    println("   " + manager.state.value.trips.joinToString { "${it.label}=${it.status}" })

    val ida = listOf(
        Trecho("Saída da garagem", 40, 12.0),
        Trecho("Rua do bairro", 180, 34.0),
        Trecho("Semáforo", 75, 0.0),
        Trecho("Avenida", 300, 52.0),
        Trecho("Congestionamento", 240, 0.0),
        Trecho("Rodovia", 900, 98.0),
    )
    ida.forEach {
        dirigir(it)
        linhaTrecho(it, manager)
    }

    passo("Motorista zera a Trip A na entrada da cidade — a B não pode sentir nada")
    val bAntes = manager.state.value.trip("B")!!.metrics.distanceKm
    manager.resetTrip("A")
    val bDepois = manager.state.value.trip("B")!!.metrics.distanceKm
    println("   Trip A → ${TripFormat.km(0.0)}   |   Trip B: ${TripFormat.km(bAntes)} → ${TripFormat.km(bDepois)}")

    listOf(
        Trecho("Marginal", 420, 61.0),
        Trecho("Trânsito parado", 300, 0.0),
        Trecho("Rua de destino", 150, 28.0),
    ).forEach {
        dirigir(it)
        linhaTrecho(it, manager)
    }

    passo("Chave desligada — snapshot gravado no ato")
    manager.handleIgnitionChange(IgnitionState.OFF, agora)
    println("   Estado das Trips: " + manager.state.value.trips.joinToString { "${it.label}=${it.status}" })
    println("   Arquivo em disco: ${File(pasta, "trips.json").length()} bytes")

    val autoAntes = manager.state.value.trip("AUTO")!!.metrics.distanceKm

    passo("Carro parado por 8 horas")
    agora += 8 * 3600 * 1000

    passo("Central religada — o módulo relê o disco")
    val restaurado = TripManager(
        storage = FileTripStorage(pasta),
        policy = SnapshotPolicy(everyKm = 1.0, everyMs = 60_000L),
        clock = { agora },
    )
    restaurado.handleIgnitionChange(IgnitionState.ON, agora)
    println("   Trip A restaurada: ${TripFormat.km(restaurado.state.value.trip("A")!!.metrics.distanceKm)}")
    println("   Trip B restaurada: ${TripFormat.km(restaurado.state.value.trip("B")!!.metrics.distanceKm)}")
    println("   Tempo com o carro desligado não entrou na conta: " +
        TripFormat.duracao(restaurado.state.value.trip("A")!!.metrics.totalTimeS))

    passo("Mais de 5 min de chave fora: a Trip automática fechou a viagem sozinha")
    println("   Viagem atual: ${TripFormat.km(autoAntes)} → " +
        TripFormat.km(restaurado.state.value.trip("AUTO")!!.metrics.distanceKm))
    val arquivadaSozinha = restaurado.state.value.history.last()
    println("   Foi para o histórico em vez de sumir: " +
        "${TripFormat.km(arquivadaSozinha.metrics.distanceKm)} " +
        "(${TripFormat.kml(arquivadaSozinha.metrics.avgFuelConsumptionKml)}), " +
        "arquivamento automático = ${arquivadaSozinha.automatic}")

    passo("Volta para casa, agora pela rodovia")
    val volta = listOf(
        Trecho("Saída", 60, 20.0),
        Trecho("Rodovia", 1500, 104.0),
        Trecho("Pedágio", 90, 0.0),
        Trecho("Rua de casa", 200, 30.0),
    )
    // Reaproveita o laço acima com o manager restaurado.
    volta.forEach { trecho ->
        repeat(trecho.duracaoS) {
            agora += 1000
            odometro += trecho.velocidadeKmh / 3600.0
            tanque = max(0.0, tanque - trecho.injecaoLph / 3600.0)
            restaurado.processTelemetry(
                TelemetrySample(agora, trecho.velocidadeKmh, trecho.injecaoLph, odometro, tanque, IgnitionState.ON)
            )
        }
        linhaTrecho(trecho, restaurado)
    }

    cabecalho("PAINEL — TRIP A")
    painel(restaurado.state.value.trip("A")!!)
    detalhes(restaurado.state.value.trip("A")!!, restaurado.state.value.live.odometerTotalKm)

    cabecalho("PAINEL — TRIP B")
    painel(restaurado.state.value.trip("B")!!)

    passo("Fechando as duas viagens no histórico — a contagem não para")
    val cidade = restaurado.saveToHistory("A")!!
    val mensal = restaurado.saveToHistory("B")!!
    println("   Trip A depois de arquivada: ${restaurado.state.value.trip("A")!!.status}")

    cabecalho("COMPARAÇÃO — ${cidade.label} × ${mensal.label}")
    comparacao(cidade, mensal)

    println()
    println("Snapshot final: ${File(pasta, "trips.json").absolutePath}")
}

// ------------------------------------------------------------------ impressão

private const val LARGURA = 66

private fun cabecalho(titulo: String) {
    println()
    println("═".repeat(LARGURA))
    println("  $titulo")
    println("═".repeat(LARGURA))
}

private fun passo(texto: String) {
    println()
    println("▸ $texto")
}

private fun linhaTrecho(trecho: Trecho, manager: TripManager) {
    val estado = manager.state.value
    val a = estado.trip("A")!!.metrics
    println(
        "   %-22s %5s  →  A: %9s  %8s   |  DTE %s".format(
            trecho.nome,
            TripFormat.duracao(trecho.duracaoS.toDouble()),
            TripFormat.km(a.distanceKm),
            TripFormat.kml(a.avgFuelConsumptionKml),
            TripFormat.km(estado.live.autonomyDteKm),
        )
    )
}

/** Os quatro quadrantes do painel, na mesma ordem em que aparecem na tela. */
private fun painel(trip: Trip) {
    val m = trip.metrics
    val celulas = listOf(
        "DISTÂNCIA" to TripFormat.km(m.distanceKm),
        "CONSUMO MÉDIO" to TripFormat.kml(m.avgFuelConsumptionKml),
        "VELOCIDADE MÉDIA" to TripFormat.kmh(m.avgSpeedKmh),
        "TEMPO TOTAL" to TripFormat.duracao(m.totalTimeS),
    )
    val largura = (LARGURA - 3) / 2
    println("┌" + "─".repeat(largura) + "┬" + "─".repeat(largura) + "┐")
    celulas.chunked(2).forEachIndexed { indice, par ->
        println("│" + par.joinToString("│") { it.first.center(largura) } + "│")
        println("│" + par.joinToString("│") { it.second.center(largura) } + "│")
        val separador = if (indice == 0) "├" + "─".repeat(largura) + "┼" + "─".repeat(largura) + "┤"
        else "└" + "─".repeat(largura) + "┴" + "─".repeat(largura) + "┘"
        println(separador)
    }
}

private fun detalhes(trip: Trip, odometroAtual: Double) {
    val m = trip.metrics
    println()
    println("   Hodômetro       ${TripFormat.km(trip.odometerStartKm)} → ${TripFormat.km(odometroAtual)}")
    println("   Em movimento    ${TripFormat.duracao(m.movingTimeS)}")
    println("   Parado          ${TripFormat.duracao(m.idleTimeS)}  (${TripFormat.decimal(m.idleRatio?.times(100), 0)}% do tempo)")
    println("   Vel. em mov.    ${TripFormat.kmh(m.avgMovingSpeedKmh)}   |   máxima ${TripFormat.kmh(m.maxSpeedKmh)}")
    println("   Combustível     ${TripFormat.litros(m.fuelLitres)}")
}

private fun comparacao(a: TripRecord, b: TripRecord) {
    val resultado = TripComparison.compare(a, b)
    println("   %-20s %12s %12s %10s %8s".format("MÉTRICA", a.label, b.label, "DIFERENÇA", "%"))
    println("   " + "─".repeat(LARGURA - 3))
    resultado.lines.forEach { l ->
        val marca = when (l.winner) {
            1 -> "◀"
            2 -> "▶"
            else -> " "
        }
        println(
            "   %-20s %12s %12s %10s %8s %s".format(
                l.label,
                TripFormat.decimal(l.a, 2),
                TripFormat.decimal(l.b, 2),
                TripFormat.decimal(l.delta, 2),
                TripFormat.percentual(l.deltaPercent, 1),
                marca,
            )
        )
    }
    println("   ◀ / ▶ apontam a viagem melhor na métrica da linha.")
}

private fun String.center(largura: Int): String {
    if (length >= largura) return substring(0, largura)
    val esquerda = (largura - length) / 2
    return " ".repeat(esquerda) + this + " ".repeat(largura - length - esquerda)
}
