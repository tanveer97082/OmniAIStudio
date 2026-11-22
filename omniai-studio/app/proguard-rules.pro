# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.omniai.studio.data.remote.**$$serializer { *; }
-keepclassmembers class com.omniai.studio.data.remote.** {
    *** Companion;
}

# Room Entities & DAOs
-keep class com.omniai.studio.data.local.** { *; }

# Media3 & OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class androidx.media3.** { *; }
