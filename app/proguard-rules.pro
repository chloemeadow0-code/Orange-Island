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
