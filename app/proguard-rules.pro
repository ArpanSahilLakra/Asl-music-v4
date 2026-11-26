# Fast R8 reachability tree-shaking without slow whole-program optimization or obfuscation
-dontobfuscate
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Standard Android entry points
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# WEB_REMIX Streaming - WebView JavaScript interfaces
-keepclassmembers class com.mochyy.mochymusic.utils.sabr.EjsNTransformSolver$SolverWebView {
    @android.webkit.JavascriptInterface public *;
}
-keepclassmembers class com.mochyy.mochymusic.utils.cipher.CipherWebView {
    @android.webkit.JavascriptInterface public *;
}
-keepclassmembers class com.mochyy.mochymusic.utils.potoken.PoTokenWebView {
    @android.webkit.JavascriptInterface public *;
}

# Keep Java Serialization classes used for queue persistence
-keep class com.mochyy.mochymusic.models.PersistQueue { *; }
-keep class com.mochyy.mochymusic.models.PersistPlayerState { *; }
-keep class com.mochyy.mochymusic.models.QueueData { *; }
-keep class com.mochyy.mochymusic.models.QueueType { *; }
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep JNI native method bridges
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep FfmpegFallbackRenderer
-keep class androidx.media3.decoder.ffmpeg.** { *; }

# Suppress optional transitive warnings
-dontwarn javax.servlet.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
-dontwarn java.beans.**
-dontwarn com.google.re2j.**
-dontwarn com.atilika.kuromoji.**
-dontwarn org.schabi.newpipe.**
-dontwarn org.mozilla.javascript.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn kotlin.reflect.**
-dontwarn io.ktor.**
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**
-keep class com.mochyy.mochymusic.ai.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.recaptcha.**
