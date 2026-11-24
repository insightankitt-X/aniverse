# AniLab Local release rules.

# Extension API surface is loaded from installed extension APKs inside this process, so its
# members are only referenced reflectively / through the child class loader.
-keep class app.anilab.source.api.** { *; }
-keep interface app.anilab.source.api.** { *; }
-keep class app.anilab.source.runtime.ExtensionHost { *; }
-keep class app.anilab.source.runtime.ExtensionProvider { *; }

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
-keep,includedescriptorclasses class app.anilab.**$$serializer { *; }
-keepclassmembers class app.anilab.** {
    *** Companion;
}
-keepclasseswithmembers class app.anilab.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt entry points.
-keep,allowobfuscation @interface dagger.hilt.**

# Media3 ships its own consumer rules; these silence optional decoder references.
-dontwarn com.google.common.collect.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**

# Keep the install result receiver referenced only from a PendingIntent.
-keep class app.anilab.local.data.extensions.InstallResultReceiver { *; }

# No logging in release: strip android.util.Log calls entirely.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
