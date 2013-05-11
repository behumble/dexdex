package com.tf.thinkdroid.dex;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

import java.io.*;
import java.lang.reflect.Array;
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
    public static final String DIR_DUBDEX = "subdex";
    private static final int BUF_SIZE = 8 * 1024;

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

    private static boolean shouldInit(File apkFile, File dexDir, String[] names) {
        long apkDate = apkFile.lastModified();
        // APK upgrade case
        if(apkDate>dexDir.lastModified()) return true;
        // clean install (or crash during install) case
        for(int i=0;i<names.length;i++) {
            String name = names[i];
            File dexJar = new File(dexDir, name);
            if(dexJar.exists()) {
                if(dexJar.lastModified()<apkDate) return true;
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * @param names array of file names in 'assets' directory
     */
    public static void validateSubDexList(Context cxt, String[] names) {
        File dexDir = cxt.getDir(DIR_DUBDEX, Context.MODE_PRIVATE); // this API creates the directory if not exist
        File apkFile = new File(cxt.getApplicationInfo().sourceDir);
        // should copy subdex JARs to dexDir?
        boolean shouldInit = shouldInit(apkFile, dexDir, names);
        System.out.println("[DexDex.validateSubDexList] shouldInit : "+shouldInit);

        String strDexDir = dexDir.getAbsolutePath();
        String[] jarsOfDex = new String[names.length];
        for(int i=0;i<jarsOfDex.length;i++) {
            jarsOfDex[i] = strDexDir+'/'+names[i];
        }

        if(shouldInit) {
            copyToInternal(cxt, dexDir, names);
        }

        PathClassLoader pcl = (PathClassLoader) cxt.getClassLoader();
        // do something dangerous
        try {
            if(Build.VERSION.SDK_INT<SDK_INT_ICS) {
                appendDexListImplUnderICS(jarsOfDex, pcl, dexDir);
            } else {    // ICS+
                appendDexListImplICS(jarsOfDex, pcl, dexDir);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void copyToInternal(Context cxt, File destDir, String[] names) {
        String strDestDir = destDir.getAbsolutePath();
        AssetManager assets = cxt.getAssets();
        byte[] buf = new byte[BUF_SIZE];
        for(int i=0;i<names.length;i++) {
            String name = names[i];
            String destPath = strDestDir+'/'+name;
            try {
                BufferedInputStream bis = new BufferedInputStream(assets.open(name));
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destPath));
                int len;
                while((len=bis.read(buf, 0, BUF_SIZE))>0) {
                    bos.write(buf, 0, len);
                }
                bis.close();
                bos.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    /** dalvik do not have security manager */
    private static void forceSet(Object obj, Field f, Object val) throws IllegalAccessException {
        f.setAccessible(true);
        f.set(obj, val);
    }

    // https://android.googlesource.com/platform/dalvik/+/android-1.6_r1/libcore/dalvik/src/main/java/dalvik/system/PathClassLoader.java
    private static void appendDexListImplUnderICS(String[] jarsOfDex, PathClassLoader pcl, File optDir) throws Exception {
        Class pclClass = pcl.getClass();
        Field fPath = pclClass.getDeclaredField("path");
        fPath.setAccessible(true);
        String orgPath = fPath.get(pcl).toString();
        String pathToAdd = DexDex.joinPaths(jarsOfDex);
        String path = orgPath+':'+pathToAdd;
        String[] paths = jarsOfDex;
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
        System.out.println("DexDex.appendDexListImplICS : "+ Arrays.deepToString((Object[]) newDexElemArray));
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