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
import android.content.res.AssetManager;
import android.os.*;
import android.util.Log;
import android.widget.Toast;
import com.tf.thinkdroid.dex.FrameworkHack;
import dalvik.system.PathClassLoader;

import java.io.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;

/**
 * Easy class loading for multi-dex Android application
 *
 * @author Alan Goo
 */
public class DexDex {
    public static final String DIR_DUBDEX = "dexdex";
    private static final String TAG = "DexDex";
    private static final int SDK_INT_ICS = 14;
    private static final int BUF_SIZE = 8 * 1024;
    private static final int WHAT_FINISH = 9900;

    private DexDex() {
        // do not create an instance
    }

    private static boolean shouldInit(File apkFile, File dexDir, String[] names) {
        long apkDate = apkFile.lastModified();
        // APK upgrade case
        if (apkDate > dexDir.lastModified()) return true;
        // clean install (or crash during install) case
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            File dexJar = new File(dexDir, name);
            if (dexJar.exists()) {
                if (dexJar.lastModified() < apkDate) return true;
            } else {
                return true;
            }
        }
        return false;
    }

    /** find and append all JARs */
    public static void addAllJARsInAssets(final Context cxt, final Listener listener) {
        try {
            String[] files = cxt.getAssets().list("");
            ArrayList<String> jarList = new ArrayList<String>();
            for(int i=0;i<files.length;i++) {
                String jar = files[i];
                if(jar.endsWith(".jar")) {
                    jarList.add(jar);
                }
            }
            String[] arrJars = new String[jarList.size()];
            jarList.toArray(arrJars);
            addDexesInAssets(cxt, arrJars, listener);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * MUST be called on Main Thread
     * @param names array of file names in 'assets' directory
     */
    public static void addDexesInAssets(final Context cxt, final String[] names, final Listener listener) {
        final File dexDir = cxt.getDir(DIR_DUBDEX, Context.MODE_PRIVATE); // this API creates the directory if not exist
        File apkFile = new File(cxt.getApplicationInfo().sourceDir);
        // should copy subdex JARs to dexDir?
        final boolean shouldInit = shouldInit(apkFile, dexDir, names);
        if(shouldInit) {
            try {
                // swap MessageQueue (dirty code collection)
                final Looper mainLooper = Looper.getMainLooper();
                final MessageQueue mq = Looper.myQueue();
                final Handler handler = new Handler(mainLooper);

                Runnable longLoadRunnable = new Runnable() {
                    @Override
                    public void run() {
                        copyToInternal(cxt, dexDir, names, listener, handler);
                        appendToClassPath(cxt, dexDir, names);
                        Message msgFinish = Message.obtain(handler, WHAT_FINISH);
                        handler.sendMessage(msgFinish);
                    }
                };
                Thread t = new Thread(longLoadRunnable, "DexDex Thread");
                t.start();

                // keep old messages and postpone until all classes are loaded
                Message orgMessages = FrameworkHack.getMessages(mq);

                FrameworkHack.setMessages(mq, null);

                // something ing...
                if(listener==null) {
                    Log.d(TAG, "listener null");
                    Toast.makeText(cxt, "DexOpting...", Toast.LENGTH_LONG).show();
                } else {
                    listener.prepareStarted(handler);
                }

                DexDex.loopByHand(mq);  // right here waiting...

                // restore original events to be dispatched
                FrameworkHack.appendMessages(mq, orgMessages);

                if(listener!=null) {
                    listener.prepareEnded(handler);
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } else {
            appendToClassPath(cxt, dexDir, names);
        }
    }

    /** like Looper.loop() */
    private static void loopByHand(MessageQueue q) {
        while(true) {
            Message msg = FrameworkHack.messageQueueNext(q);
            if(msg.what==WHAT_FINISH) {
                return;
            }
            if(msg==null) return;   // quit() called
            msg.getTarget().dispatchMessage(msg);
            msg.recycle();
        }
    }

    private static void dumpMessage(Message msg) {
        long curr = SystemClock.uptimeMillis();
        long diff = curr - msg.getWhen();
        long timestamp = System.currentTimeMillis() + diff;
        String when = new Date(timestamp).toString();
        String target = msg.getTarget().toString();
        String strObj = msg.obj==null ? "null" : msg.obj.toString();
        String msgStr = String.format("target:%s|obj:%s|what:%d|arg1:%d|arg2:%d|%s", target, strObj, msg.what, msg.arg1, msg.arg2, when);
        Log.d(TAG, msgStr);
    }

    private static void dumpMessages(Message msg) {
        try {
            Field fieldNext = Message.class.getDeclaredField("next");
            fieldNext.setAccessible(true);
            Log.d(TAG, "dumpMessages0-0-0-0-0 begins-----------");
            while(msg!=null) {
                dumpMessage(msg);
                msg = (Message) fieldNext.get(msg);
            }
            Log.d(TAG, "dumpMessages0-0-0-0-0 ends-----------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void appendToClassPath(Context cxt, File dexDir, String[] names) {
        String strDexDir = dexDir.getAbsolutePath();
        String[] jarsOfDex = new String[names.length];
        for (int i = 0; i < jarsOfDex.length; i++) {
            jarsOfDex[i] = strDexDir + '/' + names[i];
        }

        PathClassLoader pcl = (PathClassLoader) cxt.getClassLoader();
        // do something dangerous
        try {
            if (Build.VERSION.SDK_INT < SDK_INT_ICS) {
                FrameworkHack.appendDexListImplUnderICS(jarsOfDex, pcl, dexDir);
            } else {    // ICS+
                ArrayList<File> jarFiles = DexDex.strings2Files(jarsOfDex);
                FrameworkHack.appendDexListImplICS(jarFiles, pcl, dexDir);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void copyToInternal(Context cxt, File destDir, String[] names, Listener listener, Handler handler) {
        String strDestDir = destDir.getAbsolutePath();
        AssetManager assets = cxt.getAssets();
        byte[] buf = new byte[BUF_SIZE];
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            String destPath = strDestDir + '/' + name;
            if(listener!=null) {
                listener.prepareItemStarted(name, handler);
            }

            try {
                BufferedInputStream bis = new BufferedInputStream(assets.open(name));
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destPath));
                int len;
                while ((len = bis.read(buf, 0, BUF_SIZE)) > 0) {
                    bos.write(buf, 0, len);
                }
                bis.close();
                bos.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }

            if(listener!=null) {
                listener.prepareItemEnded(name, handler);
            }
        }
    }

    private static ArrayList<File> strings2Files(String[] paths) {
        ArrayList<File> result = new ArrayList<File>(paths.length);
        int size = paths.length;
        for (int i = 0; i < size; i++) {
            result.add(new File(paths[i]));
        }
        return result;
    }
}