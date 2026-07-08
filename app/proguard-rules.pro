# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve as classes de modelo (Data Classes) para que GSON e Firebase funcionem
-keep class com.jack.meuholerite.model.** { *; }
-keep class com.jack.meuholerite.database.** { *; }

# Mantém as funções de extensão e utilitários da MainActivity
-keepclassmembers class com.jack.meuholerite.MainActivityKt { *; }

# Mantém os nomes dos campos das classes que são serializadas/desserializadas
-keepclassmembers class com.jack.meuholerite.model.** { <fields>; }
-keepclassmembers class com.jack.meuholerite.database.** { <fields>; }

# PDFBox-Android
-dontwarn com.tom_roush.pdfbox.filter.JPXFilter
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.tom_roush.pdfbox.rendering.PDFRenderer
-dontwarn com.tom_roush.pdfbox.pdmodel.font.PDType1Font

# GSON rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Firebase / Firestore
-keepattributes *Annotation*
-keepclassmembers class * {
  @com.google.firebase.firestore.PropertyName <fields>;
}
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.ktx.**
-dontwarn com.google.firebase.appcheck.**

# Google Play Services / Ads
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Required for core components
-keep class android.webkit.WebView
-dontwarn java.util.concurrent.Executors
-dontwarn java.util.concurrent.atomic.AtomicBoolean
-dontwarn java.nio.file.Files
-dontwarn java.nio.file.Path
-dontwarn java.nio.file.attribute.FileAttribute
-dontwarn org.apache.http.**
-dontwarn android.net.http.**
-dontwarn android.webkit.**

# Google API Client (Drive)
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
