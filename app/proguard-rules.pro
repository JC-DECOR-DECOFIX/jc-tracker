# Retrofit / Gson: o APK de debug não ofusca. Regras mínimas se um release for gerado depois.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers class br.com.jcdecor.tracker.data.remote.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
