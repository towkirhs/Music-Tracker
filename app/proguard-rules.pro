# Room and Compose ship their own keep rules.
# Services, tile and receivers are referenced from the manifest, which AGP keeps automatically.

# whisper.cpp JNI entry points
-keep class com.musictracker.lyrics.Whisper { native <methods>; }

# NewPipe Extractor (built-in player): Rhino runs YouTube's player JavaScript by reflection.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.slf4j.**
-dontwarn com.google.re2j.**
-dontwarn org.jspecify.annotations.**
-dontwarn javax.annotation.**
