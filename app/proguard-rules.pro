# AniVerse release rules.

# Extension API surface is loaded from installed extension APKs inside this process, so its
# members are only referenced reflectively / through the child class loader.
-keep class app.aniverse.source.api.** { *; }
-keep interface app.aniverse.source.api.** { *; }
-keep class app.aniverse.source.runtime.ExtensionHost { *; }
-keep class app.aniverse.source.runtime.ExtensionProvider { *; }

# Aniyomi-format extensions link against these host classes at runtime; they are only reachable
# from third-party bytecode, so R8 must not strip or rename a single member.
-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }
-keep class keiyoushi.** { *; }
-keep interface keiyoushi.** { *; }
-keep class uy.kohesive.injekt.** { *; }
-keep interface uy.kohesive.injekt.** { *; }
-dontwarn org.jsoup.**
-dontwarn fi.iki.elonen.**
-dontwarn app.cash.quickjs.**

# Room generates implementations by name.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# kotlinx.serialization keeps generated serializers via this rule set.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.aniverse.**$$serializer { *; }
-keepclassmembers class app.aniverse.** {
    *** Companion;
}
-keepclasseswithmembers class app.aniverse.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt entry points.
-keep,allowobfuscation @interface dagger.hilt.**

# Media3 ships its own consumer rules; these silence optional decoder references.
-dontwarn com.google.common.collect.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**

# Keep the install result receiver referenced only from a PendingIntent.
-keep class app.aniverse.local.data.extensions.InstallResultReceiver { *; }

# No logging in release: strip android.util.Log calls entirely.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
