package com.thinkfree.dexdex;

import java.io.File;

public interface Listener {
    /** called once or not */
    void prepareStarted();
    void prepareEnded();
    /** called per jar after prepareStarted() */
    void prepareItemStarted(String localJarOfDex);
    void prepareItemEnded(String localJarOfDex);
}