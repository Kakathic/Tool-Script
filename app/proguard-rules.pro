# Giữ metadata cho kotlinx.serialization (model TOML)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.tool.script.config.** {
    *** Companion;
}
-keepclasseswithmembers class com.tool.script.config.** {
    kotlinx.serialization.KSerializer serializer(...);
}
