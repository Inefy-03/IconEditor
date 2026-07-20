# R8 shrinking and obfuscation remain enabled for Release. The whole-program
# optimizer produced a launch-time Runnable NPE that does not occur in Debug.
-dontoptimize

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

# apksig discovers its ASN.1 / PKCS#7 models and fields through runtime
# annotations. Keep that reflection contract while still allowing R8 to
# obfuscate the implementation.
-keep,allowobfuscation @com.android.apksig.internal.asn1.Asn1Class class * { *; }

# Firebase discovers ML Kit component registrars by class name from manifest
# metadata, then instantiates them through a no-argument constructor.
-keepclassmembers class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}

# Miuix blur / RuntimeShader
-keep class top.yukonga.miuix.kmp.blur.** { *; }
-keep class top.yukonga.miuix.kmp.shader.** { *; }
