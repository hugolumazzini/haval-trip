package com.beantechs.inputservice;

import android.view.KeyEvent;
import android.content.ComponentName;
import com.beantechs.inputservice.IDataChangedListener;
import com.beantechs.inputservice.IInputListener;

/**
 * O serviço de teclas da central: é dele que vêm os botões do volante.
 *
 * Só usamos [registerKeyEventListener] e o par dele. Os outros métodos estão
 * declarados porque a ordem é o contrato — o AIDL numera as chamadas pela
 * posição, e uma linha a menos aqui faria "registrar" virar outra coisa do
 * lado de lá.
 */
interface IInputService {
    void registerKeyEventListener(in int[] keyCodes, in IInputListener listener);
    void unregisterKeyEventListener(in int[] keyCodes, in IInputListener listener);
    void registerGlobalButtonListener(in int[] buttonCodes, in ComponentName component);
    void unregisterGlobalButtonListener(in int[] buttonCodes, in ComponentName component);
    void registerDataListener(in String[] dataTypes, in IDataChangedListener listener);
    void unregisterDataListener(in String[] dataTypes, in IDataChangedListener listener);
    void injectKeyEvent(in KeyEvent event, int injectMode);
    void enterScene(int sceneId);
    void exitScene(int sceneId);
}
