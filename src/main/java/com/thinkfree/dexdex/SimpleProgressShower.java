/*
 * Copyright 2013 ThinkFree
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thinkfree.dexdex;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;

/**
 * it provides responsiveness while installing sub-dex files.
 * [[CAUTION]] it requires <a href='http://developer.android.com/reference/android/Manifest.permission.html#SYSTEM_ALERT_WINDOW'>SYSTEM_ALERT_WINDOW</a> permission
 *
 * @author Alan Goo
 */
public class SimpleProgressShower implements Listener {
    private static final String TAG = SimpleProgressShower.class.getSimpleName();
    private Context cxt;
    private View installSplash;

    public SimpleProgressShower(Context cxt) {
        this.cxt = cxt;
    }

    /**
     * called once or not
     */
    @Override
    public void prepareStarted(Handler handler) {
        Log.d(TAG, "prepareStarted on " + Thread.currentThread());
        handler.post(new Runnable() {
            @Override
            public void run() {
                ProgressBar progressBar = new ProgressBar(cxt);
                progressBar.setIndeterminate(true);
                installSplash = progressBar;
                // require android.permission.SYSTEM_ALERT_WINDOW
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT);
                WindowManager wm = (WindowManager)cxt.getSystemService("window");
                wm.addView(progressBar, params);
            }
        });
    }

    @Override
    public void prepareEnded(Handler handler) {
        Log.d(TAG, "prepareEnded on "+Thread.currentThread());
        if(installSplash!=null) {
            WindowManager wm = (WindowManager)cxt.getSystemService("window");
            wm.removeView(installSplash);
            installSplash = null;
        }
    }

    /**
     * called per jar after prepareStarted()
     */
    @Override
    public void prepareItemStarted(String localJarOfDex, Handler handler) {
        if(Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "prepareItemStarted("+localJarOfDex+") on "+ Thread.currentThread());
        }
    }

    @Override
    public void prepareItemEnded(String localJarOfDex, Handler handler) {
        if(Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "prepareItemEnded("+localJarOfDex+") on "+ Thread.currentThread());
        }
    }
}