package com.beantechs.inputservice;

/**
 * Não usamos este aviso — a telemetria vem por outro serviço.
 *
 * Está aqui porque o [IInputService] o menciona, e porque tirar um método da
 * interface embaralharia a numeração dos que vêm depois: o AIDL numera as
 * chamadas pela ordem em que aparecem.
 */
interface IDataChangedListener {
    void onDataChanged(in String key, in String value);
}
