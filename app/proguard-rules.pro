# Smarterz ProGuard rules

# Keep Gson model classes
-keepclassmembers class com.smarterz.app.** {
    <fields>;
}
-keep class com.smarterz.app.Models* { *; }
-keep class com.smarterz.app.MediaItem { *; }
-keep class com.smarterz.app.TmdbSearchResponse { *; }
-keep class com.smarterz.app.TmdbSearchResult { *; }
-keep class com.smarterz.app.TmdbMovieDetail { *; }
-keep class com.smarterz.app.TmdbTvDetail { *; }
-keep class com.smarterz.app.TmdbSeason { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# WebView JavaScript interface (not used but keep for safety)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
