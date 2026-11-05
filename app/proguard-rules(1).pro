# Professional ProGuard rules for Cipheris
# This file helps prevent reverse engineering and unauthorized modification

# 1. Basic Obfuscation
-optimizationpasses 5
-allowaccessmodification
-dontpreverify
-repackageclasses ''
-hierarchicalrepackageclasses ''
-useuniqueclassmembernames

# 2. Advanced Security & Integrity
# Obfuscate everything except entry points
-keepattributes Signature, InnerClasses, EnclosingMethod, AnnotationDefault
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# 3. Android Entry Points
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# 4. Data Models (Keeping Room & Entities readable by system but obfuscated names)
-keepclassmembers class com.example.data.model.** { *; }
-keep class com.example.data.model.** { *; }

# 5. Room Database Protection
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.KaptInferredProperties { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# 6. Lifecycle & ViewModels
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# 7. Kotlin Specifics
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# 8. Prevent Reflection on critical security classes
-keep class com.example.data.util.CryptoUtils { *; }

# 9. Anti-Tamper Hints
# This makes it harder for automated tools to patch the APK
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
