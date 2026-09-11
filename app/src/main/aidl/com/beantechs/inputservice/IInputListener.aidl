package com.beantechs.inputservice;

import android.view.KeyEvent;

/** Por onde a central conta que uma tecla do volante foi apertada. */
interface IInputListener {
    void dispatchKeyEvent(in KeyEvent keyEvent);
}
