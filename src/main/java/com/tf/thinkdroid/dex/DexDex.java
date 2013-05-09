package com.tf.thinkdroid.dex;

import android.content.Context;
import android.os.Build;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.ZipFile;

/**
 * Easy class loading for multi-dex Android application
 * @author Alan Goo
 */
public class DexDex {
    private static final int SDK_INT_ICS = 14;

    private DexDex() {
        // do not create an instance
    }

    private static String joinPaths(String[] paths) {
        if(paths==null) return "";
        StringBuilder buf = new StringBuilder();
        for(int i=0;i<paths.length;i++) {
            buf.append(paths[i]);
            buf.append(':');
        }
        return buf.toString();
    }

    public static void appendDexList(Context cxt, String[] jarsOfDex) {
        File optDir = cxt.getDir("dex", Context.MODE_PRIVATE);
        PathClassLoader pcl = (PathClassLoader) cxt.getClassLoader();
        // do something dangerous
        try {
            if(Build.VERSION.SDK_INT<SDK_INT_ICS) {
                appendDexListImplUnderICS(jarsOfDex, pcl, optDir);
            } else {    // ICS+
                appendDexListImplICS(jarsOfDex, pcl, optDir);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /** dalvik do not have security manager */
    private static void forceSet(Object obj, Field f, Object val) throws IllegalAccessException {
        f.setAccessible(true);
        f.set(obj, val);
    }

    // https://android.googlesource.com/platform/dalvik/+/android-1.6_r1/libcore/dalvik/src/main/java/dalvik/system/PathClassLoader.java
    private static void appendDexListImplUnderICS(String[] jarsOfDex, PathClassLoader pcl, File optDir) throws Exception {
        String path = DexDex.joinPaths(jarsOfDex);
        String[] paths = jarsOfDex;
        Class pclClass = pcl.getClass();
        Field fPath = pclClass.getDeclaredField("path");
        DexDex.forceSet(pcl, fPath, path);
        Field fmPaths = pclClass.getDeclaredField("mPaths");
        DexDex.forceSet(pcl, fmPaths, paths);
        boolean wantDex = System.getProperty("android.vm.dexfile", "").equals("true");
        File[] files = new File[paths.length];
        ZipFile[] zips = new ZipFile[paths.length];
        DexFile[] dexs = new DexFile[paths.length];
        for(int i=0;i<paths.length;i++) {
            File pathFile = new File(paths[i]);
            files[i] = pathFile;
            zips[i] = new ZipFile(pathFile);
            if(wantDex) {
                File outFile = new File(optDir, pathFile.getName());
                dexs[i] = DexFile.loadDex(pathFile.getAbsolutePath(), outFile.getAbsolutePath(), 0);
            }
        }
        Field fmFiles = pclClass.getDeclaredField("mFiles");
        DexDex.forceSet(pcl, fmFiles, files);
        Field fmZips = pclClass.getDeclaredField("mZips");
        DexDex.forceSet(pcl, fmZips, zips);
        Field fmDexs = pclClass.getDeclaredField("mDexs");
        DexDex.forceSet(pcl, fmDexs, dexs);
    }

    // https://android.googlesource.com/platform/libcore/+/master/dalvik/src/main/java/dalvik/system/BaseDexClassLoader.java
    private static void appendDexListImplICS(String[] jarsOfDex, PathClassLoader pcl, File optDir) throws Exception {
        // to save original values
        Class bdclClass = Class.forName("dalvik.system.BaseDexClassLoader");
        // ICS+ - pathList
        Field fPathList = bdclClass.getDeclaredField("pathList");
        fPathList.setAccessible(true);
        Object dplObj = fPathList.get(pcl);
        // to call DexPathList.makeDexElements() for additional jar(apk)s
        ArrayList<File> jarFiles = DexDex.strings2Files(jarsOfDex);
        Class dplClass = dplObj.getClass();
        Field fDexElements = dplClass.getDeclaredField("dexElements");
        fDexElements.setAccessible(true);
        Object objOrgDexElements = fDexElements.get(dplObj);
        int orgDexCount = Array.getLength(objOrgDexElements);
        Class clazzElement = Class.forName("dalvik.system.DexPathList$Element");
        // create new merged array
        Object newDexElemArray = Array.newInstance(clazzElement, orgDexCount + jarsOfDex.length);
        System.arraycopy(objOrgDexElements,0, newDexElemArray, 0, orgDexCount);
        Method mMakeDexElements = dplClass.getDeclaredMethod("makeDexElements", ArrayList.class, File.class);
        mMakeDexElements.setAccessible(true);
        Object elemsToAdd = mMakeDexElements.invoke(null, jarFiles, optDir);
        for(int i=0;i<jarsOfDex.length;i++) {
            int pos = orgDexCount+i;
            Object elemToAdd = Array.get(elemsToAdd, i);
            Array.set(newDexElemArray, pos, elemToAdd);
        }
        DexDex.forceSet(dplObj, fDexElements, newDexElemArray);
    }

    private static ArrayList<File> strings2Files(String[] paths) {
        ArrayList<File> result = new ArrayList<File>(paths.length);
        int size = paths.length;
        for(int i=0;i<size;i++) {
            result.add(new File(paths[i]));
        }
        return result;
    }
}