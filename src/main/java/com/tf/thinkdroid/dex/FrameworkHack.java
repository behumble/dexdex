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
import java.util.zip.ZipFile;

/**
 * Collection of dirty codes. don't tell android team this mess.
 * @author Alan Goo
 */
public class FrameworkHack {
    private static Method METHOD_MESSAGE_QUEUE_NEXT;
    private static Field FIELD_MESSAGE_QUEUE_MESSAGES;

    static {
        try {
            METHOD_MESSAGE_QUEUE_NEXT = MessageQueue.class.getDeclaredMethod("next");
            METHOD_MESSAGE_QUEUE_NEXT.setAccessible(true);
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

    /**
     * dalvik do not have security manager
     */
    private static void forceSet(Object obj, Field f, Object val) throws IllegalAccessException {
        f.setAccessible(true);
        f.set(obj, val);
    }

    private static Object forceGetFirst(Object obj, Field fArray) throws IllegalAccessException {
        fArray.setAccessible(true);
        Object[] vArray = (Object[]) fArray.get(obj);
        return vArray[0];
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
    public static void appendDexListImplUnderICS(String[] jarPathsToAppend, PathClassLoader pcl, File optDir) throws Exception {
        int oldSize = 1;    // gonna assume the original path had single entry for simplicity
        Class pclClass = pcl.getClass();
        Field fPath = pclClass.getDeclaredField("path");
        fPath.setAccessible(true);
        String orgPath = fPath.get(pcl).toString();
        String pathToAdd = joinPaths(jarPathsToAppend);
        String path = orgPath + ':' + pathToAdd;
        forceSet(pcl, fPath, path);

        boolean wantDex = System.getProperty("android.vm.dexfile", "").equals("true");
        File[] files = new File[oldSize + jarPathsToAppend.length];
        ZipFile[] zips = new ZipFile[oldSize + jarPathsToAppend.length];
        DexFile[] dexs = new DexFile[oldSize + jarPathsToAppend.length];

        Field fmPaths = pclClass.getDeclaredField("mPaths");
        String[] newMPaths = new String[oldSize+jarPathsToAppend.length];
        // set originals
        newMPaths[0] = (String) forceGetFirst(pcl, fmPaths);
        forceSet(pcl, fmPaths, newMPaths);
        Field fmFiles = pclClass.getDeclaredField("mFiles");
        files[0] = (File) forceGetFirst(pcl, fmFiles);
        Field fmZips = pclClass.getDeclaredField("mZips");
        zips[0] = (ZipFile) forceGetFirst(pcl, fmZips);
        Field fmDexs = pclClass.getDeclaredField("mDexs");
        dexs[0] = (DexFile) forceGetFirst(pcl, fmDexs);

        for (int i = 0; i < jarPathsToAppend.length; i++) {
            newMPaths[oldSize+i] = jarPathsToAppend[i];
            File pathFile = new File(jarPathsToAppend[i]);
            files[oldSize+i] = pathFile;
            zips[oldSize+i] = new ZipFile(pathFile);
            if (wantDex) {
                String outDexName = pathFile.getName()+".dex";
                File outFile = new File(optDir, outDexName);
                dexs[oldSize+i] = DexFile.loadDex(pathFile.getAbsolutePath(), outFile.getAbsolutePath(), 0);
            }
        }
        forceSet(pcl, fmFiles, files);
        forceSet(pcl, fmZips, zips);
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
    }
}