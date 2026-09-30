# ProGuard rules for Haval Trip

# Manter AIDL interfaces (serviço do carro da GWM)
-keep class com.beantechs.intelligentvehiclecontrol.** { *; }
-keep interface com.beantechs.intelligentvehiclecontrol.** { *; }

# Manter Shizuku
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }

# Manter ServiceManager (usado via reflection em ShizukuTelemetrySource)
-keep class android.os.ServiceManager { *; }
-keepclassmembers class android.os.ServiceManager {
    public static android.os.IBinder getService(java.lang.String);
}

# Manter BroadcastReceivers
-keep public class * extends android.content.BroadcastReceiver

# Manter Services
-keep public class * extends android.app.Service

# Manter Activities
-keep public class * extends android.app.Activity
-keep public class * extends androidx.activity.ComponentActivity

# Serialização/Parcelable (se usado)
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlinx Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# R8 full mode
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Remove logs em produção (exceto warnings e errors)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
