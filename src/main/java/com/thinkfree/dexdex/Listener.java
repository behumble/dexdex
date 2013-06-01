package com.thinkfree.dexdex;

import android.os.Handler;

public interface Listener {
    /** called once or not */
    void prepareStarted(Handler handler);
    void prepareEnded(Handler handler);
    /** called per jar after prepareStarted() */
    void prepareItemStarted(String localJarOfDex, Handler handler);
    void prepareItemEnded(String localJarOfDex, Handler handler);
}