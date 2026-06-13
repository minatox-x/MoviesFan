# MoviesFan ProGuard rules

# ─── Keep all TMDB data model classes and their fields for Gson ───────────────
-keep class com.smarterz.app.MediaItem { *; }
-keep class com.smarterz.app.TmdbSearchResponse { *; }
-keep class com.smarterz.app.TmdbSearchResult { *; }
-keep class com.smarterz.app.TmdbMovieDetail { *; }
-keep class com.smarterz.app.TmdbTvDetail { *; }
-keep class com.smarterz.app.TmdbGenre { *; }
-keep class com.smarterz.app.TmdbLanguage { *; }
-keep class com.smarterz.app.TmdbCountry { *; }
-keep class com.smarterz.app.TmdbNetwork { *; }
-keep class com.smarterz.app.TmdbSeason { *; }
-keep class com.smarterz.app.TmdbCastMember { *; }
-keep class com.smarterz.app.TmdbCreditsResponse { *; }
-keep class com.smarterz.app.TmdbVideo { *; }
-keep class com.smarterz.app.TmdbVideosResponse { *; }
-keep class com.smarterz.app.TmdbDiscoverResponse { *; }
-keep class com.smarterz.app.TmdbDiscoverItem { *; }

# ─── Gson ─────────────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ─── OkHttp ───────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ─── Glide ────────────────────────────────────────────────────────────────────
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.**

# ─── Kotlin & Coroutines ──────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ─── WebView ──────────────────────────────────────────────────────────────────
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ─── Prevent stripping anonymous TypeToken subclasses used by Gson ────────────
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
