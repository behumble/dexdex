dexdex
======
dexdex makes [custom class loading in dalvik](http://android-developers.blogspot.kr/2011/07/custom-class-loading-in-dalvik.html) easily.

it just appends additional JARs(of classes.dex) to the system classpath instead of creating child class loader.

with dexdex you don't need

 - to introduce additional interface
 - to copy additional JARs in assets folder to internal storage

How to use
----------

1) put all JARs (of classes.dex) to ${project.home}/assets

2) define your [Application](http://developer.android.com/reference/android/app/Application.html) class then specify it as [AndroidManifest.xml](http://developer.android.com/guide/topics/manifest/manifest-element.html)'s android:name attribute.

3) call [addAllJARsInAssets()](https://github.com/behumble/dexdex/blob/master/src/main/java/com/thinkfree/dexdex/DexDex.java#L46)
```
public class MyApplication extends Application {
    @Override
    public void onCreate() {
    	DexDex.addAllJARsInAssets(this, new SimpleProgressShower());
    	super.onCreate();
    }
}
```

4) add 'android.permission.SYSTEM_ALERT_WINDOW' permission to AndroidManifest.xml
```
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
```
the permission is required only when using [SimpleProgressShower](https://github.com/behumble/dexdex/blob/master/src/main/java/com/thinkfree/dexdex/SimpleProgressShower.java). you can write own [Listener](https://github.com/behumble/dexdex/blob/master/src/main/java/com/thinkfree/dexdex/Listener.java) implementation without this permission.

Maven
-----
```
<dependency>
    <groupId>com.thinkfree.android</groupId>
    <artifactId>dexdex</artifactId>
    <version>0.1.4</version>
</dependency>
```
 
Platform
--------
All versions of android (until now-Jellybean).

but it's not upon official android API so newer versions of android can cause problems.

References
----------
- [Custom Class Loading in Dalvik (Android developer blog)](http://android-developers.blogspot.kr/2011/07/custom-class-loading-in-dalvik.html)
- [Under the Hood: Dalvik patch for Facebook for Android](https://www.facebook.com/notes/facebook-engineering/under-the-hood-dalvik-patch-for-facebook-for-android/10151345597798920)

License
-------
    Copyright 2013 ThinkFree

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
