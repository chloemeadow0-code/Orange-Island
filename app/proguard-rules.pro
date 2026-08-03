# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.orangeisland.app.**$serializer { *; }
-keepclassmembers class com.orangeisland.app.** { *** Companion; }
-keepclasseswithmembers class com.orangeisland.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# DataStore
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { <fields>; }

# JSch (SSH/SFTP)
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Compose
-dontwarn androidx.compose.**

# quickjs-kt (JS engine for plugin sandbox)
-keep class com.dokar.quickjs.** { *; }
-dontwarn com.dokar.quickjs.**

# Supabase Kotlin + ktor (auth + postgrest)
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.github.jan.supabase.**
-dontwarn io.ktor.**

# WorkManager workers (reflection instantiated by the framework)
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# JNI bridge classes
-keep class com.orangeisland.app.api.LlamaEngine { native <methods>; }
-keep class com.orangeisland.app.api.LlamaChatEngine { native <methods>; }
-keep class com.orangeisland.app.sandbox.ProotNative { native <methods>; }
-keep class com.orangeisland.app.api.NativeChatCallback { *; }
-keep class com.orangeisland.app.api.ChatTemplateMessage { *; }

# Flavor-specific classes loaded by reflection
-keep class com.orangeisland.app.sandbox.SandboxManagerFactory { *; }
-keep class com.orangeisland.app.sandbox.PlaySandboxManagerFactory {
    public <init>();
}
-keep class com.orangeisland.app.sandbox.FdroidSandboxManagerFactory {
    public <init>(android.content.Context);
}
-keep class com.orangeisland.app.workflow.geofence.PlayGeofenceProvider {
    public <init>(android.content.Context);
}
-keep class com.orangeisland.app.workflow.geofence.FdroidGeofenceProvider {
    public <init>();
}
-keep class com.orangeisland.app.workflow.trigger.GeofenceProvider { *; }

# Android manifest components (Application, activities, services, receivers, providers)
-keep public class com.orangeisland.app.OrangeIslandApplication { <init>(); }
-keep public class com.orangeisland.app.MainActivity { <init>(); }
-keep public class com.orangeisland.app.tool.device.AppLockMaskActivity { <init>(); }
-keep public class com.orangeisland.app.sandbox.SandboxDocumentsProvider { <init>(); }
-keep public class com.orangeisland.app.service.* { <init>(); }
-keep public class com.orangeisland.app.tool.device.* { <init>(); }
-keep public class com.orangeisland.app.pet.* { <init>(); }
-keep public class com.orangeisland.app.workflow.* { <init>(); }

# Coil / Media3 / MCP / Commons Compress (keep public API, suppress warnings)
-keep public class coil.** { public *; }
-dontwarn coil.**
-keep public class androidx.media3.** { public *; }
-dontwarn androidx.media3.**
-keep class io.modelcontextprotocol.** { *; }
-dontwarn io.modelcontextprotocol.**
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

# Room entities and DAOs (the app data model package)
-keep class com.orangeisland.app.data.local.** { *; }
-keep class com.orangeisland.app.model.** { *; }

# Compose runtime metadata (stability, runtime annotations)
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations, AnnotationDefault
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.runtime.**
