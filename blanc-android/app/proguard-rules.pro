# ProGuard Rules for Blanc Android

# Keep application class
-keep class me.bnfy.blanc.BlancApplication { *; }

# Keep Room entities and DAOs
-keep class me.bnfy.blanc.storage.** { *; }

# Keep Adblock engine
-keep class me.bnfy.blanc.adblock.** { *; }

# Keep Tab management
-keep class me.bnfy.blanc.tab.** { *; }

# Keep Bridge protocol
-keep class me.bnfy.blanc.bridge.** { *; }

# Keep Download service
-keep class me.bnfy.blanc.download.** { *; }

# Keep Utility and Suggestion models
-keep class me.bnfy.blanc.util.** { *; }

# Keep WebView related
-keep class android.webkit.** { *; }
-keep class androidx.webkit.** { *; }

# Keep Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.flow.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# Keep Navigation
-keep class androidx.navigation.** { *; }

# Keep Biometric
-keep class androidx.biometric.** { *; }

# Keep Security Crypto
-keep class androidx.security.crypto.** { *; }

# Keep WorkManager
-keep class androidx.work.** { *; }

# Keep Media3
-keep class androidx.media3.** { *; }

# Keep Coil
-keep class coil.** { *; }

# Keep Timber
-keep class timber.log.** { *; }

# Keep Kotlinx Serialization
-keep class kotlinx.serialization.** { *; }

# Keep OkHttp/Okio (transitive)
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep WebView JavaScript Interface
-keepclassmembers class me.bnfy.blanc.bridge.BlancBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep JavaScript Interface annotations
-keep @android.webkit.JavascriptInterface class *

# Prevent obfuscation of enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable creators
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep SafeParcelable
-keep class * implements android.os.Parcelable { *; }

# Suppress warnings for common issues
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn java.nio.file.*
-dontwarn java.lang.module.*
-dontwarn javax.annotation.**
-dontwarn kotlin.reflect.jvm.internal.**
-dontwarn org.jetbrains.kotlin.**
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.**
-dontwarn okio.**
-dontwarn okhttp3.**