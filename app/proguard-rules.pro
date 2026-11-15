# ML Kit, CameraX and Compose ship their own consumer rules.
# Settings are stored by stable keys rather than enum names, so no extra keep rules are needed.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# LiteRT-LM is driven from native code through JNI.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
-dontwarn com.google.auto.value.**
-dontwarn javax.annotation.**

# jsch loads its ciphers, key exchanges and signatures by class name.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn com.sun.jna.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.bouncycastle.**

# PS5 Remote Play: classes, fields and methods are looked up by name from native code.
-keep class com.ozkan.altyazi.remote.chiaki.** { *; }
