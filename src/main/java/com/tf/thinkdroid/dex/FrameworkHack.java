package com.tf.thinkdroid.dex;

import android.os.Message;
import android.os.MessageQueue;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.ZipFile;

/**
 * Collection of dirty codes
 * @author Alan Goo
 */
public class FrameworkHack {
    private static Method METHOD_MESSAGE_QUEUE_NEXT;
    private static Method METHOD_MESSAGE_QUEUE_ENQUEUE;
    private static Field FIELD_MESSAGE_QUEUE_MESSAGES;

    static {
        try {
            METHOD_MESSAGE_QUEUE_NEXT = MessageQueue.class.getDeclaredMethod("next");
            METHOD_MESSAGE_QUEUE_NEXT.setAccessible(true);
            METHOD_MESSAGE_QUEUE_ENQUEUE = MessageQueue.class.getDeclaredMethod("enqueueMessage", Message.class, long.class);
            METHOD_MESSAGE_QUEUE_ENQUEUE.setAccessible(true);
            FIELD_MESSAGE_QUEUE_MESSAGES = MessageQueue.class.getDeclaredField("mMessages");
            FIELD_MESSAGE_QUEUE_MESSAGES.setAccessible(true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private FrameworkHack() {
        // do not create an instance
    }

    public static Message messageQueueNext(MessageQueue q) {
        try {
            Object oMsg = METHOD_MESSAGE_QUEUE_NEXT.invoke(q);
            return (Message) oMsg;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Message getMessages(MessageQueue q) {
        try {
            return (Message) FIELD_MESSAGE_QUEUE_MESSAGES.get(q);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void setMessages(MessageQueue q, Message messages) {
        try {
            FIELD_MESSAGE_QUEUE_MESSAGES.set(q, messages);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void enqeue(MessageQueue q, Message msg) {
        try {
            METHOD_MESSAGE_QUEUE_ENQUEUE.invoke(q, msg, 0);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * dalvik do not have security manager
     */
    private static void forceSet(Object obj, Field f, Object val) throws IllegalAccessException {
        f.setAccessible(true);
        f.set(obj, val);
    }

    private static String joinPaths(String[] paths) {
        if (paths == null) return "";
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < paths.length; i++) {
            buf.append(paths[i]);
            buf.append(':');
        }
        return buf.toString();
    }

    // https://android.googlesource.com/platform/dalvik/+/android-1.6_r1/libcore/dalvik/src/main/java/dalvik/system/PathClassLoader.java
    public static void appendDexListImplUnderICS(String[] jarsOfDex, PathClassLoader pcl, File optDir) throws Exception {
        Class pclClass = pcl.getClass();
        Field fPath = pclClass.getDeclaredField("path");
        fPath.setAccessible(true);
        String orgPath = fPath.get(pcl).toString();
        String pathToAdd = joinPaths(jarsOfDex);
        String path = orgPath + ':' + pathToAdd;
        String[] paths = jarsOfDex;
        forceSet(pcl, fPath, path);
        Field fmPaths = pclClass.getDeclaredField("mPaths");
        forceSet(pcl, fmPaths, paths);
        boolean wantDex = System.getProperty("android.vm.dexfile", "").equals("true");
        File[] files = new File[paths.length];
        ZipFile[] zips = new ZipFile[paths.length];
        DexFile[] dexs = new DexFile[paths.length];
        for (int i = 0; i < paths.length; i++) {
            File pathFile = new File(paths[i]);
            files[i] = pathFile;
            zips[i] = new ZipFile(pathFile);
            if (wantDex) {
                File outFile = new File(optDir, pathFile.getName());
                dexs[i] = DexFile.loadDex(pathFile.getAbsolutePath(), outFile.getAbsolutePath(), 0);
            }
        }
        Field fmFiles = pclClass.getDeclaredField("mFiles");
        forceSet(pcl, fmFiles, files);
        Field fmZips = pclClass.getDeclaredField("mZips");
        forceSet(pcl, fmZips, zips);
        Field fmDexs = pclClass.getDeclaredField("mDexs");
        forceSet(pcl, fmDexs, dexs);
    }

    // https://android.googlesource.com/platform/libcore/+/master/libdvm/src/main/java/dalvik/system/BaseDexClassLoader.java
    // https://android.googlesource.com/platform/libcore/+/master/dalvik/src/main/java/dalvik/system/BaseDexClassLoader.java
    public static void appendDexListImplICS(ArrayList<File> jarFiles, PathClassLoader pcl, File optDir) throws Exception {
        // to save original values
        Class bdclClass = Class.forName("dalvik.system.BaseDexClassLoader");
        // ICS+ - pathList
        Field fPathList = bdclClass.getDeclaredField("pathList");
        fPathList.setAccessible(true);
        Object dplObj = fPathList.get(pcl);
        // to call DexPathList.makeDexElements() for additional jar(apk)s
        Class dplClass = dplObj.getClass();
        Field fDexElements = dplClass.getDeclaredField("dexElements");
        fDexElements.setAccessible(true);
        Object objOrgDexElements = fDexElements.get(dplObj);
        int orgDexCount = Array.getLength(objOrgDexElements);
        Class clazzElement = Class.forName("dalvik.system.DexPathList$Element");
        // create new merged array
        int jarCount = jarFiles.size();
        Object newDexElemArray = Array.newInstance(clazzElement, orgDexCount + jarCount);
        System.arraycopy(objOrgDexElements, 0, newDexElemArray, 0, orgDexCount);
        Method mMakeDexElements = dplClass.getDeclaredMethod("makeDexElements", ArrayList.class, File.class);
        mMakeDexElements.setAccessible(true);
        Object elemsToAdd = mMakeDexElements.invoke(null, jarFiles, optDir);
        for (int i = 0; i < jarCount; i++) {
            int pos = orgDexCount + i;
            Object elemToAdd = Array.get(elemsToAdd, i);
            Array.set(newDexElemArray, pos, elemToAdd);
        }
        forceSet(dplObj, fDexElements, newDexElemArray);
        System.out.println("DexDex.appendDexListImplICS : " + Arrays.deepToString((Object[]) newDexElemArray));
    }
}