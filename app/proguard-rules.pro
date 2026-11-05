# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class dev.aiharness.** {
    *** Companion;
}
-keepclasseswithmembers class dev.aiharness.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Anthropic Java SDK: Jackson-based models are created reflectively.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn io.opentelemetry.**
-dontwarn io.swagger.**
-dontwarn com.github.victools.**
-dontwarn com.standardwebhooks.**
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Referenced only by the SDK's structured-output schema helper, which this app doesn't use.
-dontwarn java.lang.reflect.AnnotatedType
