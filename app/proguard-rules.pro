-keep class shiro.refraction.RefractionApp { *; }

-keep class shiro.refraction.data.local.ProfileEntity { *; }
-keep class shiro.refraction.data.local.RefractionDatabase { *; }
-keep class shiro.refraction.data.local.ProfileDao { *; }
-keepclassmembers class shiro.refraction.data.local.ProfileDao { *; }
-keep class shiro.refraction.data.model.Profile { *; }

-keepclassmembers class * {
    @androidx.room.* <fields>;
}
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

-dontwarn javax.annotation.**
-keep class javax.annotation.** { *; }

-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
