package com.tf.thinkdroid.dex;

import android.content.Context;
import android.os.Build;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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

    private static void appendDexListImplICS(String[] jarsOfDex, PathClassLoader pcl, File optDir) throws Exception {
        Class dplClass = Class.forName("dalvik.system.DexPathList");
        String dexPathList = DexDex.joinPaths(jarsOfDex);
        // DexPathList dpl = new DexPathList(dplClass, dexPathList,null,null);
        Constructor dplConstructor = dplClass.getConstructor(ClassLoader.class, String.class, String.class, File.class);
        Object dplObj = dplConstructor.newInstance(pcl, dexPathList, null, optDir);
        // pcl.pathList = dpl;
        Class bdclClass = Class.forName("dalvik.system.BaseDexClassLoader");
        // ICS+ - pathList
        Field fPathList = bdclClass.getDeclaredField("pathList");
        DexDex.forceSet(pcl, fPathList, dplObj);
    }
}