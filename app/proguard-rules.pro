# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.bocchi.iconeditor.**$$serializer { *; }
-keepclassmembers class com.bocchi.iconeditor.** {
    *** Companion;
}
-keepclasseswithmembers class com.bocchi.iconeditor.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep model classes for serialization
-keep class com.bocchi.iconeditor.model.** { <fields>; }

# Miuix blur / RuntimeShader
-keep class top.yukonga.miuix.kmp.blur.** { *; }
-keep class top.yukonga.miuix.kmp.shader.** { *; }
