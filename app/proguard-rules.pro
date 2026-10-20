# SQLCipher
-keep class net.zetetic.database.** { *; }
# PDFBox-Android optional JPEG2000 decoder
-dontwarn com.gemalto.jp2.**
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.randwise.app.** { *** Companion; }
-keepclasseswithmembers class com.randwise.app.** { kotlinx.serialization.KSerializer serializer(...); }
