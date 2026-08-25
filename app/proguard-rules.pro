# ==============================================================================
# NEX V1 PROGUARD / R8 RELEASE RULES
# ==============================================================================

# Keep line numbers and file names for useful crash stack traces in release builds
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# 1. Native C++ / JNI Interface & AIManager
# ------------------------------------------------------------------------------
# Preserve all native methods across the entire codebase
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve AIManager and all inner callbacks/interfaces used across JNI boundary
-keep class com.k7sunny.nexv1.AIManager {
    public *;
    private *;
    native *;
}
-keep class com.k7sunny.nexv1.AIManager$* {
    public *;
    private *;
}
-keep interface com.k7sunny.nexv1.AIManager$ResponseCallback { *; }
-keep interface com.k7sunny.nexv1.AIManager$MemoryCallback { *; }
-keep interface com.k7sunny.nexv1.AIManager$TitleCallback { *; }

# ------------------------------------------------------------------------------
# 2. Room Database & DAOs
# ------------------------------------------------------------------------------
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class com.k7sunny.nexv1.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <methods>;
}
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------------------
# 3. Data Models & Entities
# ------------------------------------------------------------------------------
-keep class com.k7sunny.nexv1.Message { *; }
-keep class com.k7sunny.nexv1.ChatSession { *; }
-keep class com.k7sunny.nexv1.Memory { *; }
-keep class com.k7sunny.nexv1.ModelItem { *; }
-keep class com.k7sunny.nexv1.DocumentHelper$DocumentInfo { *; }

# ------------------------------------------------------------------------------
# 4. Markdown Rendering (Markwon & Prism4j)
# ------------------------------------------------------------------------------
-keep class io.noties.markwon.** { *; }
-keep interface io.noties.markwon.** { *; }
-keep class io.noties.prism4j.** { *; }
-dontwarn io.noties.markwon.**

# ------------------------------------------------------------------------------
# 5. Android Material & Support Components
# ------------------------------------------------------------------------------
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-keep class androidx.appcompat.widget.** { *; }
-keep class androidx.recyclerview.widget.** { *; }

# ------------------------------------------------------------------------------
# 6. General Safety & Reflection Guards
# ------------------------------------------------------------------------------
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**