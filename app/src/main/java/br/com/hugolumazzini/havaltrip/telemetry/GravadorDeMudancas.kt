package br.com.hugolumazzini.havaltrip.telemetry

/**
 * Descobre **qual propriedade do carro carrega um aviso**, comparando dois
 * retratos tirados antes e depois de um gesto.
 *
 * ## Por que existe
 *
 * O carro sabe quando a chave saiu — ele avisa no painel. Mas nenhuma das 676
 * propriedades do catálogo se chama "chave presente", e adivinhar nome não
 * levou a lugar nenhum. O caminho que sobra é medir: tira-se um retrato de um
 * punhado de propriedades suspeitas, faz-se o gesto no carro (remover a chave,
 * abrir a porta, sair de perto) e tira-se outro. O que mudou responde.
 *
 * ## Por que ele não vasculha a central
 *
 * A sonda de identidade ([IdentidadeDoCarro]) descobre nomes lendo o `dex` de
 * todos os aplicativos da GWM instalados, na hora, dentro do carro — e isso
 * **travou a central** no teste. Aqui a lista de nomes é fixa e já vem pronta:
 * foi extraída fora do carro, do catálogo do AutoPanel. Perguntar o valor de
 * 128 propriedades é barato; folhear dezenas de megabytes de APK não é.
 *
 * ## Nada disto fica ligado
 *
 * É ferramenta de investigação, não recurso: só roda quando alguém aperta o
 * botão no Diagnóstico, e nenhuma propriedade daqui entra em cálculo de viagem.
 */
object GravadorDeMudancas {

    /**
     * As suspeitas: tudo que tem cara de aviso, luz de painel ou fechadura.
     *
     * Vem do catálogo de propriedades do AutoPanel, filtrado por nome. Chassi,
     * placa e número de série ficaram de fora na origem — esta lista é lida em
     * voz alta numa tela e pode acabar num relatório público.
     *
     * As mais promissoras para o aviso de chave são
     * `car.ipk_setting.alarm_his_info_list` (o histórico de alertas do painel),
     * `car.ipk_info.warning_tts_notify` (o aviso que o carro fala) e
     * `car.ipk_light.global_alarm_indicator`.
     */
    val CANDIDATAS: List<String> = listOf(
        "car.basic.coolant_temp_warning",
        "car.basic.door_control_action",
        "car.basic.door_lock_status",
        "car.basic.door_status",
        "car.basic.engine_oil_low_pressure_warning",
        "car.basic.fatigue_warning",
        "car.basic.maintenance_warning",
        "car.basic.maintenance_warning_mileage",
        "car.basic.oil_low_warning",
        "car.basic.password_free_login_notify",
        "car.basic.seat_belt_warning",
        "car.basic.steering_reset_remind_enable",
        "car.basic.tirepress_warning",
        "car.basic.tiretemp_warning",
        "car.basic.tpms_status",
        "car.basic.tpms_units",
        "car.basic.tpms_warning",
        "car.basic.window_close_by_lock_vehicle",
        "car.child_mode.door_window_lock_state",
        "car.child_mode.lock_state",
        "car.child_mode.lock_state_lamp",
        "car.comfort_setting.auto_open_back_door",
        "car.comfort_setting.reduce_seat_belt_slack",
        "car.comfort_setting.seat_belt_vibration_warning",
        "car.configure.differential_lock",
        "car.configure.door_open_warning",
        "car.configure.door_unlock_mode",
        "car.configure.drowsiness_warning_system",
        "car.configure.elec_steering_col_lock",
        "car.configure.electric_sound_reminder",
        "car.configure.enhanced_assist_system_vibration_reminder",
        "car.configure.forward_cross_warning_and_brake_system",
        "car.configure.front_seat_belt",
        "car.configure.haptic_alarm_of_enhanced_assist_system",
        "car.configure.haptic_reminder_of_lane_support_system",
        "car.configure.lane_assist_system_vibration_reminder",
        "car.configure.mobile_bluetooth_key",
        "car.configure.onekey_inner_cycle_mode",
        "car.configure.rear_collision_warning",
        "car.configure.rear_door_opening",
        "car.configure.rotating_speed_alarm_threshold",
        "car.configure.seat_belt_warning",
        "car.configure.shift_unlock_key",
        "car.configure.super_lock",
        "car.configure.tpms",
        "car.configure.traffic_sign_warning",
        "car.dms.frs.notify_delte_state",
        "car.dms.frs.notify_enroll_state",
        "car.dms.frs.notify_identify_result",
        "car.dms.frs.notify_identify_state",
        "car.dms.infraredblocking_glasses_detect",
        "car.door_lock_setting.initiative_to_enter",
        "car.door_lock_setting.initiative_to_exit",
        "car.door_lock_setting.locked_by_speed",
        "car.door_lock_setting.locked_remind",
        "car.door_lock_setting.super_lock_enable",
        "car.door_lock_setting.unlock_by_flameout",
        "car.door_lock_setting.unlock_mode",
        "car.ev.setting.gmode_notify",
        "car.ev.setting.vehicle_to_vehicle_discharge_notify",
        "car.ev_info.batt_heat_runaway_notify",
        "car.ev_info.energy_output_warning",
        "car.fragrance.channel_timeout_alarm",
        "car.hvac.panel_display_notify",
        "car.ipk.setting.engine_speed_ui_state",
        "car.ipk.setting.engine_speeding_limit_alarm",
        "car.ipk_info.aeb_ja_brk_trig",
        "car.ipk_info.aeb_ja_trig",
        "car.ipk_info.aeb_ped_trig",
        "car.ipk_info.bsd_lca_warning_reqleft",
        "car.ipk_info.bsd_lca_warning_reqright",
        "car.ipk_info.dow_warning_reqleft",
        "car.ipk_info.dow_warning_reqright",
        "car.ipk_info.fcta_warning",
        "car.ipk_info.fctb_trig",
        "car.ipk_info.fcw_warning",
        "car.ipk_info.warning_tts_notify",
        "car.ipk_light.abs_indicator",
        "car.ipk_light.aeb_veh_trig",
        "car.ipk_light.airbag",
        "car.ipk_light.ass_indicator",
        "car.ipk_light.auto_parking_indicator",
        "car.ipk_light.battery_charge_indicator",
        "car.ipk_light.battery_cut_off_indicator",
        "car.ipk_light.brake_energe_recycle",
        "car.ipk_light.braking_system_indicator",
        "car.ipk_light.bsd_indicator",
        "car.ipk_light.cco_indicator",
        "car.ipk_light.charge_system_indicator",
        "car.ipk_light.door_warning",
        "car.ipk_light.engine_malfunction",
        "car.ipk_light.engine_oil_low_pressure_warning",
        "car.ipk_light.engine_service",
        "car.ipk_light.engine_water_temp_high",
        "car.ipk_light.epark_indicator",
        "car.ipk_light.eps_alert",
        "car.ipk_light.eps_off",
        "car.ipk_light.esp_indicator",
        "car.ipk_light.fcw_indicator",
        "car.ipk_light.front_differential_lock_indicator",
        "car.ipk_light.fuel_low",
        "car.ipk_light.global_alarm_indicator",
        "car.ipk_light.hybrid_power_system",
        "car.ipk_light.ldw_indicator",
        "car.ipk_light.limit_120",
        "car.ipk_light.lka_indicator",
        "car.ipk_light.motor_malfunction",
        "car.ipk_light.parking_alarm_indicator",
        "car.ipk_light.power_battery_alarm_indicator",
        "car.ipk_light.power_battery_low",
        "car.ipk_light.rear_differential_lock_indicator",
        "car.ipk_light.reduce_power_indicator",
        "car.ipk_light.seat_belt_warning_indicator",
        "car.ipk_light.sound_prompt_system",
        "car.ipk_light.tab_indicator",
        "car.ipk_light.tpms_warning",
        "car.ipk_light.transmission_over_heat",
        "car.ipk_light.vcu_ready_state",
        "car.ipk_setting.alarm_his_info_list",
        "car.ipk_setting.brightness_config",
        "car.ipk_setting.brightness_range",
        "car.ipk_setting.cast_screen_ready_state",
        "car.ipk_setting.drive_info_reset",
        "car.ipk_setting.drive_info_reset_state",
        "car.ipk_setting.mode_config",
        "car.ipk_setting.theme_config",
        "car.parking_setting.radar_alarm_sound_config",
        "car.parking_setting.radar_alarm_sound_enable",
    )

    /** Um retrato: o valor de cada candidata num instante. */
    data class Retrato(val emMs: Long, val valores: Map<String, String>)

    /** Uma propriedade que mudou entre os dois retratos. */
    data class Mudanca(val chave: String, val antes: String?, val depois: String?)

    /**
     * Pergunta o valor de cada candidata ao serviço do carro.
     *
     * Em lotes pequenos, e não de uma vez: uma transação de binder tem teto de
     * tamanho, e estourá-lo devolveria nada em vez da maior parte. Quem chama
     * roda isto fora da thread da tela.
     */
    fun retratar(): Retrato {
        val servico = ShizukuTelemetrySource.servicoDoCarro()
            ?: return Retrato(System.currentTimeMillis(), emptyMap())
        val valores = mutableMapOf<String, String>()
        CANDIDATAS.chunked(TAMANHO_DO_LOTE).forEach { lote ->
            runCatching {
                val vetor = lote.toTypedArray()
                servico.fetchDatas(vetor).forEachIndexed { i, valor ->
                    if (valor != null) valores[vetor[i]] = valor
                }
            }
        }
        return Retrato(System.currentTimeMillis(), valores)
    }

    /**
     * O que mudou de um retrato para o outro.
     *
     * Propriedade que sumiu ou apareceu também conta: "deixou de responder" é
     * informação tanto quanto um número diferente.
     */
    fun comparar(antes: Retrato, depois: Retrato): List<Mudanca> =
        (antes.valores.keys + depois.valores.keys).sorted().mapNotNull { chave ->
            val a = antes.valores[chave]
            val b = depois.valores[chave]
            if (a == b) null else Mudanca(chave, a, b)
        }

    /**
     * Lotes de vinte. É pequeno de propósito: a central é modesta, e esta
     * ferramenta roda com o motorista esperando na frente da tela.
     */
    private const val TAMANHO_DO_LOTE = 20
}
