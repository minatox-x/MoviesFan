package com.smarterz.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// ─── Data Models ─────────────────────────────────────────────────────────────

data class MediaItem(
    val id: Int,
    val type: String,
    val title: String,
    val detail: String,
    val poster: String?,
    val season: Int = 1,
    val episode: Int = 1
)

data class TmdbSearchResponse(
    val results: List<TmdbSearchResult>?,
    @SerializedName("total_pages") val totalPages: Int = 1
)

data class TmdbSearchResult(
    val id: Int,
    val title: String?,
    val name: String?,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("poster_path") val posterPath: String?
) {
    val displayTitle get() = title ?: name ?: "Unknown"
    val posterUrl get() = posterPath?.let { "https://image.tmdb.org/t/p/w300$it" }
}

data class TmdbMovieDetail(
    val id: Int,
    val title: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    val overview: String?,
    val tagline: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("vote_count") val voteCount: Int?,
    @SerializedName("release_date") val releaseDate: String?,
    val genres: List<TmdbGenre>?,
    val runtime: Int?,
    val status: String?,
    @SerializedName("spoken_languages") val spokenLanguages: List<TmdbLanguage>?,
    @SerializedName("production_countries") val productionCountries: List<TmdbCountry>?,
    @SerializedName("imdb_id") val imdbId: String?
) {
    val displayTitle get() = title ?: "Unknown"
    val posterUrl get() = posterPath?.let { "https://image.tmdb.org/t/p/w400$it" }
    val backdropUrl get() = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
    val year get() = releaseDate?.take(4) ?: ""
    val runtimeFormatted get() = runtime?.let { "${it / 60}h ${it % 60}m" } ?: ""
}

data class TmdbTvDetail(
    val id: Int,
    val name: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    val overview: String?,
    val tagline: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("vote_count") val voteCount: Int?,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int?,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("last_air_date") val lastAirDate: String?,
    val genres: List<TmdbGenre>?,
    val seasons: List<TmdbSeason>?,
    val status: String?,
    val networks: List<TmdbNetwork>?,
    @SerializedName("spoken_languages") val spokenLanguages: List<TmdbLanguage>?
) {
    val displayTitle get() = name ?: "Unknown"
    val posterUrl get() = posterPath?.let { "https://image.tmdb.org/t/p/w400$it" }
    val backdropUrl get() = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
    val year get() = firstAirDate?.take(4) ?: ""
}

data class TmdbGenre(val id: Int, val name: String)
data class TmdbLanguage(val name: String?, @SerializedName("english_name") val englishName: String?)
data class TmdbCountry(val name: String?)
data class TmdbNetwork(val id: Int, val name: String?, @SerializedName("logo_path") val logoPath: String?)

data class TmdbSeason(
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("episode_count") val episodeCount: Int,
    val name: String?
)

data class TmdbCastMember(
    val id: Int,
    val name: String?,
    val character: String?,
    @SerializedName("profile_path") val profilePath: String?,
    val order: Int
) {
    val profileUrl get() = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
}

data class TmdbCreditsResponse(
    val cast: List<TmdbCastMember>?
)

data class TmdbVideo(
    val key: String?,
    val type: String?,
    val site: String?
)

data class TmdbVideosResponse(
    val results: List<TmdbVideo>?
)

data class TmdbDiscoverResponse(
    val results: List<TmdbDiscoverItem>?,
    @SerializedName("total_pages") val totalPages: Int = 1
)

data class TmdbDiscoverItem(
    val id: Int,
    val title: String?,
    val name: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    val popularity: Double?,
    @SerializedName("media_type") val mediaType: String?
) {
    val displayTitle get() = title ?: name ?: "Unknown"
    val posterUrl get() = posterPath?.let { "https://image.tmdb.org/t/p/w300$it" }
    val year get() = (releaseDate ?: firstAirDate)?.take(4) ?: ""
}

// ─── Storage ──────────────────────────────────────────────────────────────────

class RecentStorage(context: Context) {
    private val prefs = context.getSharedPreferences("moviesfan_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val KEY = "recent_v5"

    fun getAll(): List<MediaItem> = try {
        val type = object : TypeToken<List<MediaItem>>() {}.type
        gson.fromJson(prefs.getString(KEY, "[]"), type) ?: emptyList()
    } catch (e: Exception) { emptyList() }

    fun add(item: MediaItem) {
        val list = getAll().filter { !(it.id == item.id && it.type == item.type) }.toMutableList()
        list.add(0, item)
        if (list.size > 20) list.removeAt(list.size - 1)
        prefs.edit().putString(KEY, gson.toJson(list)).apply()
    }

    fun remove(id: Int, type: String) {
        prefs.edit().putString(KEY, gson.toJson(
            getAll().filter { !(it.id == id && it.type == type) }
        )).apply()
    }
}

// ─── API ──────────────────────────────────────────────────────────────────────

class TmdbApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val BASE = "https://proxy-api-server-woz1.onrender.com/v1/tmdb/3"
    private val KEY = "374ed57246cdd0d51e7f9c7eb9e682f0"

    fun search(query: String, page: Int = 1): TmdbSearchResponse? =
        fetch("$BASE/search/multi?api_key=$KEY&query=${enc(query)}&page=$page") {
            gson.fromJson(it, TmdbSearchResponse::class.java)
        }

    fun movie(id: Int): TmdbMovieDetail? =
        fetch("$BASE/movie/$id?api_key=$KEY&append_to_response=genres,spoken_languages,production_countries") {
            gson.fromJson(it, TmdbMovieDetail::class.java)
        }

    fun tv(id: Int): TmdbTvDetail? =
        fetch("$BASE/tv/$id?api_key=$KEY&append_to_response=genres,spoken_languages") {
            gson.fromJson(it, TmdbTvDetail::class.java)
        }

    fun movieCredits(id: Int): TmdbCreditsResponse? =
        fetch("$BASE/movie/$id/credits?api_key=$KEY") {
            gson.fromJson(it, TmdbCreditsResponse::class.java)
        }

    fun tvCredits(id: Int): TmdbCreditsResponse? =
        fetch("$BASE/tv/$id/credits?api_key=$KEY") {
            gson.fromJson(it, TmdbCreditsResponse::class.java)
        }

    fun movieVideos(id: Int): TmdbVideosResponse? =
        fetch("$BASE/movie/$id/videos?api_key=$KEY") {
            gson.fromJson(it, TmdbVideosResponse::class.java)
        }

    fun tvVideos(id: Int): TmdbVideosResponse? =
        fetch("$BASE/tv/$id/videos?api_key=$KEY") {
            gson.fromJson(it, TmdbVideosResponse::class.java)
        }

    fun movieRecommendations(id: Int): TmdbDiscoverResponse? =
        fetch("$BASE/movie/$id/recommendations?api_key=$KEY") {
            gson.fromJson(it, TmdbDiscoverResponse::class.java)
        }

    fun tvRecommendations(id: Int): TmdbDiscoverResponse? =
        fetch("$BASE/tv/$id/recommendations?api_key=$KEY") {
            gson.fromJson(it, TmdbDiscoverResponse::class.java)
        }

    fun discoverMovies(page: Int = 1, sortBy: String = "popularity.desc",
                       genreId: String = "", year: String = "",
                       language: String = "", networkId: String = ""): TmdbDiscoverResponse? {
        var url = "$BASE/discover/movie?api_key=$KEY&page=$page&sort_by=$sortBy&region=IN&watch_region=IN&include_adult=false"
        if (genreId.isNotEmpty()) url += "&with_genres=$genreId"
        if (year.isNotEmpty()) url += "&primary_release_year=$year"
        if (language.isNotEmpty()) url += "&with_original_language=$language"
        if (networkId.isNotEmpty()) url += "&with_watch_providers=$networkId"
        return fetch(url) { gson.fromJson(it, TmdbDiscoverResponse::class.java) }
    }

    fun discoverTv(page: Int = 1, sortBy: String = "popularity.desc",
                   genreId: String = "", year: String = "",
                   language: String = "", networkId: String = ""): TmdbDiscoverResponse? {
        var url = "$BASE/discover/tv?api_key=$KEY&page=$page&sort_by=$sortBy&region=IN&watch_region=IN&include_adult=false"
        if (genreId.isNotEmpty()) url += "&with_genres=$genreId"
        if (year.isNotEmpty()) url += "&first_air_date_year=$year"
        if (language.isNotEmpty()) url += "&with_original_language=$language"
        if (networkId.isNotEmpty()) url += "&with_watch_providers=$networkId"
        return fetch(url) { gson.fromJson(it, TmdbDiscoverResponse::class.java) }
    }

    fun popularMovies(page: Int = 1): TmdbDiscoverResponse? =
        fetch("$BASE/movie/popular?api_key=$KEY&page=$page&region=IN") {
            gson.fromJson(it, TmdbDiscoverResponse::class.java)
        }

    fun trendingTv(page: Int = 1): TmdbDiscoverResponse? =
        fetch("$BASE/trending/tv/week?api_key=$KEY&page=$page&region=IN") {
            gson.fromJson(it, TmdbDiscoverResponse::class.java)
        }

    fun topRatedMovies(page: Int = 1): TmdbDiscoverResponse? =
        fetch("$BASE/movie/top_rated?api_key=$KEY&page=$page") {
            gson.fromJson(it, TmdbDiscoverResponse::class.java)
        }

    fun upcomingMovies(page: Int = 1): TmdbDiscoverResponse? =
        fetch("$BASE/movie/upcoming?api_key=$KEY&page=$page&region=IN") {
            gson.fromJson(it, TmdbDiscoverResponse::class.java)
        }

    fun nowPlayingMovies(page: Int = 1): TmdbDiscoverResponse? =
        fetch("$BASE/movie/now_playing?api_key=$KEY&page=$page&region=IN") {
            gson.fromJson(it, TmdbDiscoverResponse::class.java)
        }

    fun movieGenres(): List<TmdbGenre> =
        fetch("$BASE/genre/movie/list?api_key=$KEY") { body ->
            val obj = gson.fromJson(body, com.google.gson.JsonObject::class.java)
            val type = object : TypeToken<List<TmdbGenre>>() {}.type
            gson.fromJson(obj.getAsJsonArray("genres"), type)
        } ?: emptyList()

    fun tvGenres(): List<TmdbGenre> =
        fetch("$BASE/genre/tv/list?api_key=$KEY") { body ->
            val obj = gson.fromJson(body, com.google.gson.JsonObject::class.java)
            val type = object : TypeToken<List<TmdbGenre>>() {}.type
            gson.fromJson(obj.getAsJsonArray("genres"), type)
        } ?: emptyList()

    private fun <T> fetch(url: String, parse: (String) -> T): T? = try {
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        if (resp.isSuccessful) resp.body?.string()?.let { parse(it) } else null
    } catch (e: Exception) { null }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

// ─── WebViewClient ─────────────────────────────────────────────────────────────

class SmartWebViewClient(
    private val onPageReady: () -> Unit,
    private val onError: ((String) -> Unit)? = null
) : WebViewClient() {

    private val ESCAPE_SCHEMES = setOf(
        "intent", "android-app", "market", "tel", "sms",
        "mailto", "whatsapp", "tg", "viber", "fb", "twitter"
    )

    private val SPOOF_JS = """
        (function() {
            // ── Guard: run once per window, re-run on each inject ─────────────
            // We intentionally allow re-injection (no early-return guard) so that
            // iframes and dynamic pages always get the full patch applied.

            // ═══════════════════════════════════════════════════════════════════
            // SECTION 1 — NAVIGATOR / FINGERPRINT SPOOFING
            // ═══════════════════════════════════════════════════════════════════
            try {
                Object.defineProperty(navigator, 'webdriver', { get: function() { return false; } });
            } catch(e) {}

            // ═══════════════════════════════════════════════════════════════════
            // SECTION 2 — VIEWPORT
            // ═══════════════════════════════════════════════════════════════════
            try {
                var vp = document.querySelector('meta[name=viewport]');
                var vc = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                if (vp) { vp.setAttribute('content', vc); }
                else {
                    var m = document.createElement('meta');
                    m.name = 'viewport'; m.content = vc;
                    (document.head || document.documentElement).appendChild(m);
                }
            } catch(e) {}

            // ═══════════════════════════════════════════════════════════════════
            // SECTION 2.5 — REMOVE NATIVE TAP HIGHLIGHT
            // ═══════════════════════════════════════════════════════════════════
            try {
                var style = document.createElement('style');
                style.innerHTML = '* { -webkit-tap-highlight-color: transparent !important; }';
                (document.head || document.documentElement).appendChild(style);
            } catch(e) {}

            // ═══════════════════════════════════════════════════════════════════
            // SECTION 3 — TOTAL NATIVE POPUP & HIGHLIGHT KILLER
            // ═══════════════════════════════════════════════════════════════════
            try {
                // 1. Kill the blue tap highlight globally in the main document
                var style = document.createElement('style');
                style.innerHTML = '* { -webkit-tap-highlight-color: transparent !important; outline: none !important; }';
                (document.head || document.documentElement).appendChild(style);

                // 2. Absolute kill of window.open
                // We no longer allow ANY exceptions. This stops Chromium from dropping fullscreen.
                if (!window.__sz_open_patched) {
                    window.__sz_open_patched = true;
                    window.open = function() {
                        console.log('[SZ] Blocked window.open entirely');
                        return { closed: true, close: function(){} };
                    };
                }
                
                // 3. Intercept all clicks to prevent <a target="_blank"> from dropping fullscreen
                if (!window.__sz_click_patched) {
                    window.__sz_click_patched = true;
                    document.addEventListener('click', function(e) {
                        var el = e.target;
                        while (el && el.tagName) {
                            if (el.tagName.toUpperCase() === 'A') {
                                var target = el.getAttribute('target');
                                if (target === '_blank' || target === '_new') {
                                    e.preventDefault();
                                    console.log('[SZ] Blocked _blank link click');
                                }
                                break;
                            }
                            el = el.parentElement;
                        }
                    }, { capture: true });
                }
            } catch(e) {}
            // ═══════════════════════════════════════════════════════════════════
            // SECTION 4 — IRON-LOCK exitFullscreen / webkitExitFullscreen
            //
            // This is the PRIMARY fix for the black-screen issue.
            // Ads call document.exitFullscreen() or video.webkitExitFullscreen()
            // to pull the player out of fullscreen, leaving a black void.
            // We make ALL exitFullscreen calls completely silent no-ops.
            // The only real exit is via the user pressing Back (handled in Kotlin).
            // ═══════════════════════════════════════════════════════════════════
            try {
                // ── 4a. document-level exit (standard + webkit) ───────────────
                var noop = function() { return Promise.resolve(); };
                var noopVoid = function() {};

                Object.defineProperty(document, 'exitFullscreen', {
                    get: function() { return noop; },
                    set: function() {},
                    configurable: true
                });
                Object.defineProperty(document, 'webkitExitFullscreen', {
                    get: function() { return noopVoid; },
                    set: function() {},
                    configurable: true
                });
                Object.defineProperty(document, 'mozCancelFullScreen', {
                    get: function() { return noopVoid; },
                    set: function() {},
                    configurable: true
                });
                Object.defineProperty(document, 'msExitFullscreen', {
                    get: function() { return noopVoid; },
                    set: function() {},
                    configurable: true
                });

                // ── 4b. Element prototype (video, div, iframe etc.) ───────────
                ['exitFullscreen','webkitExitFullscreen','mozCancelFullScreen','msExitFullscreen'].forEach(function(fn) {
                    try {
                        Object.defineProperty(HTMLElement.prototype, fn, {
                            get: function() { return fn === 'exitFullscreen' ? noop : noopVoid; },
                            set: function() {},
                            configurable: true
                        });
                    } catch(e2) {}
                });

                // ── 4c. Spoof fullscreen state so ads think they're NOT in FS ─
                // If ads check document.fullscreenElement before calling exit,
                // returning null makes them think exit is unnecessary.
                try {
                    Object.defineProperty(document, 'fullscreenElement', {
                        get: function() { return null; },
                        configurable: true
                    });
                    Object.defineProperty(document, 'webkitFullscreenElement', {
                        get: function() { return null; },
                        configurable: true
                    });
                } catch(e3) {}

                // ── 4d. Intercept fullscreenchange events from ads ─────────────
                // Some ad SDKs listen to fullscreenchange and react by calling
                // exitFullscreen. We intercept and kill those events.
                try {
                    var _origAEL = EventTarget.prototype.addEventListener;
                    var BLOCK_FS_EVENTS = { 'fullscreenchange': true, 'webkitfullscreenchange': true,
                                            'mozfullscreenchange': true, 'MSFullscreenChange': true,
                                            'fullscreenerror': true, 'webkitfullscreenerror': true };
                    EventTarget.prototype.addEventListener = function(type, listener, opts) {
                        if (BLOCK_FS_EVENTS[type]) {
                            // Allow the player's own listeners (on document or body)
                            // but block listeners registered by iframes/ad scripts
                            var el = this;
                            if (el !== document && el !== document.body && el !== window) {
                                console.log('[SZ] Blocked FS event listener:', type, 'on', el);
                                return;
                            }
                        }
                        return _origAEL.call(this, type, listener, opts);
                    };
                } catch(e4) {}

            } catch(e) {}

            // ═══════════════════════════════════════════════════════════════════
            // SECTION 5 — LOCK requestFullscreen (prevent ad iframes from
            //             grabbing fullscreen and then exiting it to go black)
            // ═══════════════════════════════════════════════════════════════════
            try {
                if (!Element.prototype.__sz_fs_patched) {
                    Element.prototype.__sz_fs_patched = true;
                    var _origRFS = Element.prototype.requestFullscreen ||
                                   Element.prototype.webkitRequestFullscreen ||
                                   Element.prototype.mozRequestFullScreen;

                    var _patchFS = function() {
                        var el = this;
                        var tag = (el.tagName || '').toLowerCase();
                        var id  = (el.id || '').toLowerCase();
                        var cls = (el.className || '').toLowerCase();

                        // Allow: video elements, known player containers
                        var isPlayerEl = tag === 'video'
                            || id.indexOf('player') !== -1
                            || id.indexOf('video') !== -1
                            || cls.indexOf('player') !== -1
                            || cls.indexOf('video-js') !== -1
                            || cls.indexOf('jw-') !== -1
                            || cls.indexOf('plyr') !== -1;

                        // Block: ad iframes, small divs, anchor elements
                        var isAdEl = tag === 'a'
                            || tag === 'span'
                            || (tag === 'iframe' && !isPlayerEl)
                            || (tag === 'div' && !isPlayerEl
                                && el.children.length === 0);

                        if (isAdEl) {
                            console.log('[SZ] Blocked requestFullscreen on ad element:', tag, id);
                            return Promise.reject(new Error('[SZ] Blocked'));
                        }

                        return _origRFS ? _origRFS.call(el) : Promise.resolve();
                    };

                    try { Element.prototype.requestFullscreen = _patchFS; } catch(e) {}
                    try { Element.prototype.webkitRequestFullscreen = _patchFS; } catch(e) {}
                }
            } catch(e) {}

            // ═══════════════════════════════════════════════════════════════════
            // SECTION 6 — AD OVERLAY ELIMINATOR
            //
            // Detects and nukes clickable overlay elements (the kind that open
            // ad tabs when you tap the video). Uses the same logic that works
            // in normal mode — now also patched into iframes via Section 7.
            // ═══════════════════════════════════════════════════════════════════
            function isAdOverlay(el) {
                try {
                    if (!el || !el.tagName) return false;
                    var tag = el.tagName.toUpperCase();

                    // Never kill actual player elements
                    if (tag === 'VIDEO' || tag === 'CANVAS') return false;
                    if (el.querySelector && el.querySelector('video, canvas')) return false;

                    var style = window.getComputedStyle(el);
                    var pos = style.position;
                    var display = style.display;
                    if (display === 'none') return false;
                    if (pos !== 'absolute' && pos !== 'fixed') return false;

                    var rect = el.getBoundingClientRect();
                    var sw = window.innerWidth  || document.documentElement.clientWidth  || 1;
                    var sh = window.innerHeight || document.documentElement.clientHeight || 1;

                    // Must cover at least 30% of screen in each dimension
                    if (rect.width < sw * 0.3 || rect.height < sh * 0.3) return false;

                    // High z-index + transparent/semi-transparent = almost certainly ad overlay
                    var zi = parseInt(style.zIndex) || 0;
                    var bg = style.backgroundColor;
                    var isTransparent = bg === 'transparent' || bg === 'rgba(0, 0, 0, 0)'
                                     || bg.indexOf('rgba') !== -1;

                    if (tag === 'A') {
                        var href = (el.getAttribute('href') || '').trim();
                        if (!href || href === '#' || href.startsWith('javascript')) return false;
                        // A large absolute/fixed anchor with an external href = ad
                        return true;
                    }

                    // Check onclick for navigation
                    var oc = el.getAttribute('onclick') || '';
                    if (oc && (oc.indexOf('location') !== -1 || oc.indexOf('open') !== -1
                            || oc.indexOf('href') !== -1)) return true;

                    // Large transparent/high-z overlay div with no media children
                    if ((isTransparent || zi > 100) && tag === 'DIV') {
                        var hasMedia = el.querySelector('video, iframe, canvas, img[src*="player"]');
                        if (!hasMedia) return true;
                    }

                    return false;
                } catch(e) { return false; }
            }

            function eliminateOverlay(el) {
                try {
                    // For anchor tags — remove entirely so no redirect possible
                    if (el.tagName && el.tagName.toUpperCase() === 'A') {
                        el.remove();
                        return;
                    }
                    // For divs — make untouchable but keep them (removing may break layout)
                    el.style.setProperty('pointer-events', 'none', 'important');
                    el.style.setProperty('z-index', '-9999', 'important');
                    el.style.setProperty('opacity', '0', 'important');
                    // Kill any onclick
                    el.onclick = null;
                    el.removeAttribute('onclick');
                } catch(e) {}
            }

            function scanAndEliminate(root) {
                try {
                    var sel = 'a[href], div[onclick], span[onclick],'
                            + 'div[style*="position:fixed"], div[style*="position: fixed"],'
                            + 'div[style*="position:absolute"], div[style*="position: absolute"],'
                            + 'a[style*="position"]';
                    var candidates = (root || document).querySelectorAll(sel);
                    for (var i = 0; i < candidates.length; i++) {
                        if (isAdOverlay(candidates[i])) {
                            eliminateOverlay(candidates[i]);
                        }
                    }
                } catch(e) {}
            }

            // Run immediately + on DOM ready
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', function() { scanAndEliminate(document); });
            } else {
                scanAndEliminate(document);
            }

            // ── Mutation observer — catches dynamically injected ad overlays ──
            try {
                if (!window.__sz_observer) {
                    window.__sz_observer = new MutationObserver(function(mutations) {
                        for (var mi = 0; mi < mutations.length; mi++) {
                            var added = mutations[mi].addedNodes;
                            for (var ni = 0; ni < added.length; ni++) {
                                var node = added[ni];
                                if (node.nodeType !== 1) continue;
                                if (isAdOverlay(node)) {
                                    eliminateOverlay(node);
                                } else if (node.querySelectorAll) {
                                    scanAndEliminate(node);
                                }
                            }
                            // Also check attribute mutations (style changes that make an
                            // existing div become an overlay)
                            if (mutations[mi].type === 'attributes') {
                                var target = mutations[mi].target;
                                if (target && isAdOverlay(target)) eliminateOverlay(target);
                            }
                        }
                    });
                    window.__sz_observer.observe(document.documentElement, {
                        childList:  true,
                        subtree:    true,
                        attributes: true,
                        attributeFilter: ['style', 'class', 'onclick', 'href']
                    });
                }
            } catch(e) {}

            // ── Touch/click capture — last-resort ad-tap blocker ─────────────
            try {
                if (!window.__sz_touch_patched) {
                    window.__sz_touch_patched = true;
                    function blockIfOverlay(e) {
                        var node = e.target;
                        for (var i = 0; i < 8; i++) {
                            if (!node || node === document.body || node === document.documentElement) break;
                            if (isAdOverlay(node)) {
                                e.preventDefault();
                                e.stopImmediatePropagation();
                                eliminateOverlay(node);
                                return;
                            }
                            node = node.parentElement;
                        }
                    }
                    document.addEventListener('click',      blockIfOverlay, { capture: true, passive: false });
                    document.addEventListener('touchstart', blockIfOverlay, { capture: true, passive: false });
                    document.addEventListener('touchend',   blockIfOverlay, { capture: true, passive: false });
                    document.addEventListener('mousedown',  blockIfOverlay, { capture: true, passive: false });
                }
            } catch(e) {}

            // ═══════════════════════════════════════════════════════════════════
            // SECTION 7 — INJECT INTO ALL IFRAMES
            // ═══════════════════════════════════════════════════════════════════
            try {
                function patchIframe(iframe) {
                    try {
                        var iw = iframe.contentWindow;
                        var id2 = iframe.contentDocument;
                        if (!iw || !id2) return;

                        // 1. Inject the CSS into the iframe to kill the blue highlight there too
                        try {
                            var s = id2.createElement('style');
                            s.innerHTML = '* { -webkit-tap-highlight-color: transparent !important; outline: none !important; }';
                            (id2.head || id2.documentElement).appendChild(s);
                        } catch(e){}

                        // 2. Complete window.open block inside iframe
                        try { iw.open = function() { return { closed: true, close: function(){} }; }; } catch(e){}

                        // 3. Block _blank clicks inside iframe
                        try {
                            id2.addEventListener('click', function(e) {
                                var el = e.target;
                                while (el && el.tagName) {
                                    if (el.tagName.toUpperCase() === 'A') {
                                        var target = el.getAttribute('target');
                                        if (target === '_blank' || target === '_new') {
                                            e.preventDefault();
                                        }
                                        break;
                                    }
                                    el = el.parentElement;
                                }
                            }, { capture: true });
                        } catch(e){}

                        // 4. Apply same exitFullscreen lock inside the iframe
                        var n = function() { return Promise.resolve(); };
                        var nv = function() {};
                        try { Object.defineProperty(id2, 'exitFullscreen',        { get: function(){ return n;  }, configurable: true }); } catch(e){}
                        try { Object.defineProperty(id2, 'webkitExitFullscreen',  { get: function(){ return nv; }, configurable: true }); } catch(e){}
                        try { Object.defineProperty(iw,  'exitFullscreen',        { get: function(){ return n;  }, configurable: true }); } catch(e){}
                        try { Object.defineProperty(iw,  'webkitExitFullscreen',  { get: function(){ return nv; }, configurable: true }); } catch(e){}
                    } catch(e) {}
                }
            // ═══════════════════════════════════════════════════════════════════
            // SECTION 8 — LOCATION / HISTORY HIJACK PREVENTION
            //
            // Some ads change window.location.href directly.
            // We block any navigation to non-player domains.
            // ═══════════════════════════════════════════════════════════════════
            try {
                if (!window.__sz_location_patched) {
                    window.__sz_location_patched = true;
                    var PLAYER_DOMAINS = ['vidsrc', 'cloudnestra', 'vidplay', 'filemoon',
                                          'vidcloud', 'megacloud', 'rabbitstream', 'embed'];
                    function isPlayerUrl(url) {
                        if (!url) return false;
                        var u = String(url).toLowerCase();
                        if (u.startsWith('blob:') || u.startsWith('data:') || u === '#') return true;
                        for (var i = 0; i < PLAYER_DOMAINS.length; i++) {
                            if (u.indexOf(PLAYER_DOMAINS[i]) !== -1) return true;
                        }
                        return false;
                    }
                    // Intercept history.pushState / replaceState
                    var _origPush    = history.pushState;
                    var _origReplace = history.replaceState;
                    history.pushState = function(state, title, url) {
                        if (url && !isPlayerUrl(url)) {
                            console.log('[SZ] Blocked history.pushState:', url);
                            return;
                        }
                        return _origPush.apply(history, arguments);
                    };
                    history.replaceState = function(state, title, url) {
                        if (url && !isPlayerUrl(url)) {
                            console.log('[SZ] Blocked history.replaceState:', url);
                            return;
                        }
                        return _origReplace.apply(history, arguments);
                    };
                }
            } catch(e) {}

        })();
    """.trimIndent()

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? = null

    private fun isBlockedRootSite(url: android.net.Uri): Boolean {
        val scheme = url.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = url.host?.lowercase()?.removePrefix("www.") ?: return false
        if (host == "vidsrcme.ru") {
            val path = url.path?.trimEnd('/') ?: ""
            return !path.startsWith("/embed")
        }
        return false
    }

        // 🚨 STRICT WHITELIST: Only these domains are allowed to navigate
    private val ALLOWED_DOMAINS = listOf(
        "vidsrcme.ru", "vidsrc.to", "vidsrc.xyz", "vidsrc.net", "vidsrc.in", 
        "vidsrc.pm", "vidsrc.rip", "cloudnestra", "vidplay", "filemoon", 
        "megacloud", "rabbitstream", "embed", "youtube"
    )

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url ?: return true
        val scheme = url.scheme?.lowercase() ?: ""
        
        // Always block escape attempts (intents, market, etc.)
        if (scheme in ESCAPE_SCHEMES) return true
        if (scheme == "data" || scheme == "blob") return false

        val host = url.host?.lowercase() ?: ""
        
        // If the host does NOT contain one of our allowed domains, kill it instantly.
        val isAllowed = ALLOWED_DOMAINS.any { host.contains(it) }
        if (!isAllowed) {
            android.util.Log.d("SZ_AdBlock", "Blocked unauthorized navigation to: $url")
            return true // Returning true means "I handled it, do nothing" (Blocked)
        }

        // Allow the authorized domain
        return false 
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return true
        if (url.startsWith("about:") || url.startsWith("data:") || url.startsWith("blob:")) return false
        
        val parsed = try { Uri.parse(url) } catch (e: Exception) { return true }
        val scheme = parsed.scheme?.lowercase() ?: return true
        
        if (scheme in ESCAPE_SCHEMES) return true

        val host = parsed.host?.lowercase() ?: ""
        
        // If the host does NOT contain one of our allowed domains, kill it instantly.
        val isAllowed = ALLOWED_DOMAINS.any { host.contains(it) }
        if (!isAllowed) {
            android.util.Log.d("SZ_AdBlock", "Blocked unauthorized navigation to: $url")
            return true // Blocked
        }
        
        return false // Allow
    }


    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.evaluateJavascript(SPOOF_JS, null)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.evaluateJavascript(SPOOF_JS, null)
        if (url != null && url != "about:blank" && view?.url == url) {
            onPageReady()
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            val code = error?.errorCode ?: -1
            val desc = error?.description?.toString() ?: "Unknown error"
            if (code != -2) onError?.invoke("Player error ($code): $desc")
        }
    }

    @Suppress("DEPRECATION")
    override fun onReceivedError(
        view: WebView?, errorCode: Int, description: String?, failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        if (errorCode != -2) onError?.invoke("Player error ($errorCode): ${description ?: "Unknown error"}")
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 500) {
            onError?.invoke("Player failed to load (HTTP ${errorResponse?.statusCode}). Please try again.")
        }
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?) = true
}

// ─── SmartChromeClient ────────────────────────────────────────────────────────
//
// Design principle: treat fullscreen EXACTLY like normal mode.
//
// In normal mode, when an ad overlay is tapped the JS layer suppresses it.
// In fullscreen, the problem is at a LOWER level: the ad calls the browser's
// native exitFullscreen() API, which triggers onHideCustomView() in Java/Kotlin
// BEFORE any JS can react.  The screen goes black because:
//   1. onHideCustomView removes the video view from fullscreenContainer
//   2. The playerModal is still INVISIBLE (we hid it on fullscreen enter)
//   3. Result: black screen + normal mode restored
//
// Fix strategy:
//   • NEVER actually hide the custom view unless the user explicitly pressed Back.
//   • Track user intent via a dedicated flag set ONLY from the back-press handler.
//   • Any onHideCustomView call that arrives WITHOUT that flag = ad-driven = ignore.
//   • We don't even need the re-entry hack anymore — we just refuse to hide.
//   • The JS layer (Section 4 of SPOOF_JS) kills exitFullscreen at the JS level
//     before it even reaches here; this Kotlin guard is the second line of defence.
//
class SmartChromeClient(
    private val fullscreenContainer: FrameLayout,
    private val onFullscreenEnter: () -> Unit,
    private val onFullscreenExit: () -> Unit
) : WebChromeClient() {

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    // Only true when the USER deliberately exits fullscreen (back press / close btn).
    // Any onHideCustomView() arriving while this is false is treated as spurious (ad).
    @Volatile private var userRequestedExit = false

    private val POPUP_ALLOWED_HOSTS = setOf(
        "vidsrc.me", "vidsrc.to", "vidsrc.xyz",
        "vidsrc.net", "vidsrc.in", "vidsrc.pm", "vidsrc.rip",
        "cloudnestra.com", "vidplay.online", "filemoon.sx"
    )

    // ── Called by back-press / close button ONLY ──────────────────────────────
    fun requestUserExit() {
        userRequestedExit = true
        onHideCustomView()
    }

    // ── Kotlin-side: enter fullscreen ─────────────────────────────────────────
    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view == null) return

        // If already in fullscreen, quietly replace (shouldn't normally happen)
        if (customView != null) {
            fullscreenContainer.removeView(customView)
            customViewCallback?.onCustomViewHidden()
        }

        customView = view
        customViewCallback = callback
        userRequestedExit = false

        fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        fullscreenContainer.visibility = View.VISIBLE
        onFullscreenEnter()
    }

    // ── Kotlin-side: exit fullscreen ──────────────────────────────────────────
    override fun onHideCustomView() {
    val view = customView ?: return

    // You MUST remove the view. When Chromium calls this, the video
    // surface is already dead. Keeping it on screen causes the black void.
    fullscreenContainer.removeView(view)
    fullscreenContainer.visibility = View.GONE
    
    customViewCallback?.onCustomViewHidden()
    
    customView = null
    customViewCallback = null
    userRequestedExit = false
    
    onFullscreenExit()
}

    fun isFullscreen(): Boolean = customView != null

    // Called only from back-press / close button
    fun exitFullscreenIfNeeded(): Boolean {
        return if (customView != null) {
            requestUserExit()
            true
        } else false
    }

    // ── Popup / new window: only let player domains through ───────────────────
    override fun onCreateWindow(
    view: WebView?,
    isDialog: Boolean,
    isUserGesture: Boolean,
    resultMsg: android.os.Message?
): Boolean {
    // Returning false outright denies the creation of the new window.
    // Because no window is created, Chromium's security trigger never fires,
    // and it never forces the player out of fullscreen.
    return false
}

    // Silence all JS dialogs (ads love these)
    override fun onJsAlert(v: WebView?, u: String?, m: String?, r: JsResult?): Boolean      { r?.cancel(); return true }
    override fun onJsConfirm(v: WebView?, u: String?, m: String?, r: JsResult?): Boolean    { r?.cancel(); return true }
    override fun onJsPrompt(v: WebView?, u: String?, m: String?, d: String?, r: JsPromptResult?): Boolean { r?.cancel(); return true }
    override fun onJsBeforeUnload(v: WebView?, u: String?, m: String?, r: JsResult?): Boolean { r?.cancel(); return true }
}

// ─── Adapters ─────────────────────────────────────────────────────────────────

class MediaAdapter(
    private var items: List<MediaItem>,
    private val showRemove: Boolean,
    private val onClick: (MediaItem) -> Unit,
    private val onRemove: ((MediaItem) -> Unit)? = null
) : RecyclerView.Adapter<MediaAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val poster: ImageView = v.findViewById(R.id.cardPoster)
        val title: TextView = v.findViewById(R.id.cardTitle)
        val detail: TextView = v.findViewById(R.id.cardDetail)
        val typeBadge: TextView = v.findViewById(R.id.cardTypeBadge)
        val removeBtn: ImageButton = v.findViewById(R.id.removeBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.title.text = item.title
        h.detail.text = if (showRemove && item.type == "tv" && item.season > 0) {
            "S${item.season} · E${item.episode}"
        } else {
            item.detail
        }
        h.typeBadge.text = if (item.type == "tv") "TV" else "MOVIE"
        h.typeBadge.setBackgroundColor(
            if (item.type == "tv") Color.parseColor("#1a6fd4") else Color.parseColor("#c0392b")
        )
        h.removeBtn.visibility = if (showRemove) View.VISIBLE else View.GONE
        if (!item.poster.isNullOrEmpty()) {
            Glide.with(h.poster.context)
                .load(item.poster)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.poster_placeholder)
                .error(R.drawable.poster_placeholder)
                .centerCrop()
                .into(h.poster)
        } else {
            h.poster.setImageResource(R.drawable.poster_placeholder)
        }
        h.itemView.setOnClickListener { onClick(item) }
        h.removeBtn.setOnClickListener { onRemove?.invoke(item) }
    }

    override fun getItemCount() = items.size
    fun update(newItems: List<MediaItem>) { items = newItems; notifyDataSetChanged() }
}

class DiscoverAdapter(
    private var items: List<TmdbDiscoverItem>,
    private val mediaType: String,
    private val onClick: (TmdbDiscoverItem) -> Unit,
    private val useGridLayout: Boolean = true
) : RecyclerView.Adapter<DiscoverAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val poster: ImageView = v.findViewById(R.id.cardPoster)
        val title: TextView = v.findViewById(R.id.cardTitle)
        val detail: TextView = v.findViewById(R.id.cardDetail)
        val typeBadge: TextView = v.findViewById(R.id.cardTypeBadge)
        val removeBtn: ImageButton = v.findViewById(R.id.removeBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layoutRes = if (useGridLayout) R.layout.item_grid_card else R.layout.item_media_card
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(layoutRes, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.title.text = item.displayTitle
        h.detail.text = item.year.ifEmpty { "—" }
        val type = item.mediaType ?: mediaType
        h.typeBadge.text = if (type == "tv") "TV" else "MOVIE"
        h.typeBadge.setBackgroundColor(
            if (type == "tv") Color.parseColor("#1a6fd4") else Color.parseColor("#c0392b")
        )
        h.removeBtn.visibility = View.GONE
        if (!item.posterUrl.isNullOrEmpty()) {
            Glide.with(h.poster.context)
                .load(item.posterUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.poster_placeholder)
                .error(R.drawable.poster_placeholder)
                .centerCrop()
                .into(h.poster)
        } else {
            h.poster.setImageResource(R.drawable.poster_placeholder)
        }
        h.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
    fun update(newItems: List<TmdbDiscoverItem>) { items = newItems; notifyDataSetChanged() }
}

// ─── MainActivity ─────────────────────────────────────────────────────────────

class MainActivity : AppCompatActivity() {

    // ── Sections / Navigation ─────────────────────────────────────────────────
    private lateinit var homeSection: ScrollView
    private lateinit var moviesSection: LinearLayout
    private lateinit var seriesSection: LinearLayout
    private lateinit var discoverSection: LinearLayout
    private lateinit var searchSection: LinearLayout
    private lateinit var detailSection: ScrollView

    // ── Bottom Navigation ─────────────────────────────────────────────────────
    private lateinit var navHome: LinearLayout
    private lateinit var navDiscover: LinearLayout
    private lateinit var navMovies: LinearLayout
    private lateinit var navShows: LinearLayout
    private lateinit var navHomeText: TextView
    private lateinit var navDiscoverText: TextView
    private lateinit var navMoviesText: TextView
    private lateinit var navShowsText: TextView
    private lateinit var navHomeIcon: TextView
    private lateinit var navDiscoverIcon: TextView
    private lateinit var navMoviesIcon: TextView
    private lateinit var navShowsIcon: TextView

    // ── Top Bar ───────────────────────────────────────────────────────────────
    private lateinit var searchInput: EditText
    private lateinit var searchBtn: ImageButton

    // ── Home screen ───────────────────────────────────────────────────────────
    private lateinit var continueWatchingRow: LinearLayout
    private lateinit var continueWatchingList: RecyclerView
    private lateinit var continueWatchingEmpty: TextView
    private lateinit var homePopularMoviesList: RecyclerView
    private lateinit var homeTrendingTvList: RecyclerView
    private lateinit var homeNetflixList: RecyclerView
    private lateinit var homePrimeList: RecyclerView

    // ── Movies section ────────────────────────────────────────────────────────
    private lateinit var moviesRecycler: RecyclerView
    private lateinit var moviesLoading: ProgressBar
    private lateinit var moviesSortSpinner: Spinner
    private lateinit var moviesGenreSpinner: Spinner
    private lateinit var moviesYearSpinner: Spinner
    private lateinit var moviesLangSpinner: Spinner
    private lateinit var moviesPrevBtn: Button
    private lateinit var moviesNextBtn: Button
    private lateinit var moviesPageIndicator: TextView

    // ── Series section ────────────────────────────────────────────────────────
    private lateinit var seriesRecycler: RecyclerView
    private lateinit var seriesLoading: ProgressBar
    private lateinit var seriesSortSpinner: Spinner
    private lateinit var seriesGenreSpinner: Spinner
    private lateinit var seriesYearSpinner: Spinner
    private lateinit var seriesLangSpinner: Spinner
    private lateinit var seriesPrevBtn: Button
    private lateinit var seriesNextBtn: Button
    private lateinit var seriesPageIndicator: TextView

    // ── Discover section ──────────────────────────────────────────────────────
    private lateinit var discoverRecycler: RecyclerView
    private lateinit var discoverLoading: ProgressBar
    private lateinit var discoverCategorySpinner: Spinner
    private lateinit var discoverNetworkSpinner: Spinner
    private lateinit var discoverPrevBtn: Button
    private lateinit var discoverNextBtn: Button
    private lateinit var discoverPageIndicator: TextView

    // ── Search section ────────────────────────────────────────────────────────
    private lateinit var searchLoading: ProgressBar
    private lateinit var searchRecycler: RecyclerView
    private lateinit var paginationRow: LinearLayout
    private lateinit var prevPageBtn: Button
    private lateinit var nextPageBtn: Button
    private lateinit var pageIndicator: TextView

    // ── Detail section ────────────────────────────────────────────────────────
    private lateinit var detailLoading: ProgressBar
    private lateinit var detailContent: LinearLayout
    private lateinit var detailHero: FrameLayout
    private lateinit var detailBackdrop: ImageView
    private lateinit var detailPoster: ImageView
    private lateinit var detailTitle: TextView
    private lateinit var detailYear: TextView
    private lateinit var detailGenre: TextView
    private lateinit var detailRating: TextView
    private lateinit var detailRuntime: TextView
    private lateinit var detailBadgeType: TextView
    private lateinit var detailTagline: TextView
    private lateinit var detailOverview: TextView
    private lateinit var detailStatus: TextView
    private lateinit var detailLanguage: TextView
    private lateinit var playButton: Button
    private lateinit var detailCastList: RecyclerView
    private lateinit var detailCastSection: LinearLayout
    private lateinit var detailTrailerSection: LinearLayout
    private lateinit var detailTrailerWebView: WebView
    private lateinit var detailRecommendationsList: RecyclerView
    private lateinit var detailRecommendationsSection: LinearLayout
    private lateinit var detailMoreInfoSection: LinearLayout
    private lateinit var detailSeasons: TextView

    // ── Player ────────────────────────────────────────────────────────────────
    private lateinit var playerModal: LinearLayout
    private lateinit var videoContainer: FrameLayout
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var closePlayer: ImageButton
    private lateinit var playerWebView: WebView
    private lateinit var playerLoadingOverlay: FrameLayout
    private lateinit var playerTitle: TextView
    private lateinit var playerEpisodeInfo: TextView
    private lateinit var tvControls: LinearLayout
    private lateinit var movieControls: LinearLayout
    private lateinit var seasonSpinner: Spinner
    private lateinit var episodeSpinner: Spinner
    private lateinit var prevEpBtn: ImageButton
    private lateinit var nextEpBtn: ImageButton
    private lateinit var chromeClient: SmartChromeClient

    // ── State ─────────────────────────────────────────────────────────────────
    private val api = TmdbApi()
    private lateinit var storage: RecentStorage
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Adapters
    private lateinit var continueAdapter: MediaAdapter
    private lateinit var homePopularMoviesAdapter: DiscoverAdapter
    private lateinit var homeTrendingTvAdapter: DiscoverAdapter
    private lateinit var homeNetflixAdapter: DiscoverAdapter
    private lateinit var homePrimeAdapter: DiscoverAdapter
    private lateinit var moviesAdapter: DiscoverAdapter
    private lateinit var seriesAdapter: DiscoverAdapter
    private lateinit var discoverAdapter: DiscoverAdapter
    private lateinit var searchAdapter: MediaAdapter

    // Player/detail state
    private var seasonsMap = mutableMapOf<Int, TmdbSeason>()
    private var currentId = 0
    private var currentType = "tv"
    private var currentSeason = 1
    private var currentEpisode = 1
    private var currentPosterUrl: String? = null
    private var currentDetailType = "movie"

    // Search
    private var searchPage = 1
    private var totalSearchPages = 1
    private var lastQuery = ""

    // Genres
    private var movieGenres: List<TmdbGenre> = emptyList()
    private var tvGenres: List<TmdbGenre> = emptyList()

    // Movies filter state
    private var moviesSortBy = "popularity.desc"
    private var moviesGenreId = ""
    private var moviesYear = ""
    private var moviesLanguage = ""
    private var moviesPage = 1
    private var moviesTotalPages = 1

    // Series filter state
    private var seriesSortBy = "popularity.desc"
    private var seriesGenreId = ""
    private var seriesYear = ""
    private var seriesLanguage = ""
    private var seriesPage = 1
    private var seriesTotalPages = 1

    // Discover filter state
    private var discoverCategory = "popular"
    private var discoverNetwork = ""
    private var discoverPage = 1
    private var discoverTotalPages = 1

    // Current active section
    private var activeSection = "home"
    // From which section we opened detail
    private var detailOriginSection = "home"

    companion object {
        const val EMBED_TV = "https://vidsrcme.ru/embed/tv"
        const val EMBED_MOVIE = "https://vidsrcme.ru/embed/movie"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        storage = RecentStorage(this)
        bindViews()
        setupWebView()
        setupAdapters()
        setupListeners()
        loadGenres()
        showHome()
    }

    // ─── View Binding ─────────────────────────────────────────────────────────

    private fun bindViews() {
        // Navigation
        homeSection = findViewById(R.id.homeSection)
        moviesSection = findViewById(R.id.moviesSection)
        seriesSection = findViewById(R.id.seriesSection)
        discoverSection = findViewById(R.id.discoverSection)
        searchSection = findViewById(R.id.searchSection)
        detailSection = findViewById(R.id.detailSection)

        navHome = findViewById(R.id.navHome)
        navDiscover = findViewById(R.id.navDiscover)
        navMovies = findViewById(R.id.navMovies)
        navShows = findViewById(R.id.navShows)
        navHomeText = findViewById(R.id.navHomeText)
        navDiscoverText = findViewById(R.id.navDiscoverText)
        navMoviesText = findViewById(R.id.navMoviesText)
        navShowsText = findViewById(R.id.navShowsText)
        navHomeIcon = findViewById(R.id.navHomeIcon)
        navDiscoverIcon = findViewById(R.id.navDiscoverIcon)
        navMoviesIcon = findViewById(R.id.navMoviesIcon)
        navShowsIcon = findViewById(R.id.navShowsIcon)

        searchInput = findViewById(R.id.searchInput)
        searchBtn = findViewById(R.id.searchButton)

        // Home
        continueWatchingRow = findViewById(R.id.continueWatchingRow)
        continueWatchingList = findViewById(R.id.continueWatchingList)
        continueWatchingEmpty = findViewById(R.id.continueWatchingEmpty)
        homePopularMoviesList = findViewById(R.id.homePopularMoviesList)
        homeTrendingTvList = findViewById(R.id.homeTrendingTvList)
        homeNetflixList = findViewById(R.id.homeNetflixList)
        homePrimeList = findViewById(R.id.homePrimeList)

        // Movies section
        moviesRecycler = findViewById(R.id.moviesRecycler)
        moviesLoading = findViewById(R.id.moviesLoading)
        moviesSortSpinner = findViewById(R.id.moviesSortSpinner)
        moviesGenreSpinner = findViewById(R.id.moviesGenreSpinner)
        moviesYearSpinner = findViewById(R.id.moviesYearSpinner)
        moviesLangSpinner = findViewById(R.id.moviesLangSpinner)
        moviesPrevBtn = findViewById(R.id.moviesPrevBtn)
        moviesNextBtn = findViewById(R.id.moviesNextBtn)
        moviesPageIndicator = findViewById(R.id.moviesPageIndicator)

        // Series section
        seriesRecycler = findViewById(R.id.seriesRecycler)
        seriesLoading = findViewById(R.id.seriesLoading)
        seriesSortSpinner = findViewById(R.id.seriesSortSpinner)
        seriesGenreSpinner = findViewById(R.id.seriesGenreSpinner)
        seriesYearSpinner = findViewById(R.id.seriesYearSpinner)
        seriesLangSpinner = findViewById(R.id.seriesLangSpinner)
        seriesPrevBtn = findViewById(R.id.seriesPrevBtn)
        seriesNextBtn = findViewById(R.id.seriesNextBtn)
        seriesPageIndicator = findViewById(R.id.seriesPageIndicator)

        // Discover
        discoverRecycler = findViewById(R.id.discoverRecycler)
        discoverLoading = findViewById(R.id.discoverLoading)
        discoverCategorySpinner = findViewById(R.id.discoverCategorySpinner)
        discoverNetworkSpinner = findViewById(R.id.discoverNetworkSpinner)
        discoverPrevBtn = findViewById(R.id.discoverPrevBtn)
        discoverNextBtn = findViewById(R.id.discoverNextBtn)
        discoverPageIndicator = findViewById(R.id.discoverPageIndicator)

        // Search
        searchLoading = findViewById(R.id.searchLoading)
        searchRecycler = findViewById(R.id.searchRecycler)
        paginationRow = findViewById(R.id.paginationRow)
        prevPageBtn = findViewById(R.id.prevPageBtn)
        nextPageBtn = findViewById(R.id.nextPageBtn)
        pageIndicator = findViewById(R.id.pageIndicator)

        // Detail
        detailLoading = findViewById(R.id.detailLoading)
        detailContent = findViewById(R.id.detailContent)
        detailHero = findViewById(R.id.detailHero)
        detailBackdrop = findViewById(R.id.detailBackdrop)
        detailPoster = findViewById(R.id.detailPoster)
        detailTitle = findViewById(R.id.detailTitle)
        detailYear = findViewById(R.id.detailYear)
        detailGenre = findViewById(R.id.detailGenre)
        detailRating = findViewById(R.id.detailRating)
        detailRuntime = findViewById(R.id.detailRuntime)
        detailBadgeType = findViewById(R.id.detailBadgeType)
        detailTagline = findViewById(R.id.detailTagline)
        detailOverview = findViewById(R.id.detailOverview)
        detailStatus = findViewById(R.id.detailStatus)
        detailLanguage = findViewById(R.id.detailLanguage)
        playButton = findViewById(R.id.playButton)
        detailCastList = findViewById(R.id.detailCastList)
        detailCastSection = findViewById(R.id.detailCastSection)
        detailTrailerSection = findViewById(R.id.detailTrailerSection)
        detailTrailerWebView = findViewById(R.id.detailTrailerWebView)
        detailRecommendationsList = findViewById(R.id.detailRecommendationsList)
        detailRecommendationsSection = findViewById(R.id.detailRecommendationsSection)
        detailMoreInfoSection = findViewById(R.id.detailMoreInfoSection)
        detailSeasons = findViewById(R.id.detailSeasons)

        // Player
        playerModal = findViewById(R.id.playerModal)
        videoContainer = findViewById(R.id.videoContainer)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        closePlayer = findViewById(R.id.closePlayer)
        playerWebView = findViewById(R.id.playerWebView)
        playerLoadingOverlay = findViewById(R.id.playerLoadingOverlay)
        playerTitle = findViewById(R.id.playerTitle)
        playerEpisodeInfo = findViewById(R.id.playerEpisodeInfo)
        tvControls = findViewById(R.id.tvControls)
        movieControls = findViewById(R.id.movieControls)
        seasonSpinner = findViewById(R.id.seasonSpinner)
        episodeSpinner = findViewById(R.id.episodeSpinner)
        prevEpBtn = findViewById(R.id.prevEpisodeBtn)
        nextEpBtn = findViewById(R.id.nextEpisodeBtn)

        val screenWidth = resources.displayMetrics.widthPixels
        val videoHeight = screenWidth * 9 / 16
        videoContainer.layoutParams = videoContainer.layoutParams.apply { height = videoHeight }
    }

    // ─── WebView Setup ────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val decor = window.decorView as FrameLayout
        (fullscreenContainer.parent as? ViewGroup)?.removeView(fullscreenContainer)
        decor.addView(
            fullscreenContainer,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        fullscreenContainer.visibility = View.GONE
        fullscreenContainer.setBackgroundColor(Color.BLACK)

        playerWebView.webViewClient = SmartWebViewClient(
            onPageReady = { playerLoadingOverlay.visibility = View.GONE },
            onError = { message ->
                runOnUiThread {
                    playerLoadingOverlay.visibility = View.GONE
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Playback Error")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        )
        playerWebView.webChromeClient = SmartChromeClient(
            fullscreenContainer = fullscreenContainer,
            onFullscreenEnter = {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
                    ctrl.hide(WindowInsetsCompat.Type.systemBars())
                    ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                playerModal.visibility = View.INVISIBLE
            },
            onFullscreenExit = {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                playerModal.visibility = View.VISIBLE
            }
        ).also { chromeClient = it }

        val s = playerWebView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setSupportMultipleWindows(false) // 🚨 This physically prevents popunders from existing
        s.javaScriptCanOpenWindowsAutomatically = false // 🚨 Stops JS from hijacking the tab
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.textZoom = 100
        s.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(playerWebView, true) }
        playerWebView.setBackgroundColor(Color.BLACK)
        playerWebView.setDownloadListener { _, _, _, _, _ -> }

        // Trailer WebView setup
        detailTrailerWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
    }

    // ─── Adapters Setup ───────────────────────────────────────────────────────

    private fun setupAdapters() {
        // Continue watching
        continueAdapter = MediaAdapter(
            emptyList(), true,
            onClick = { loadDetail(it.id, it.type, it.season, it.episode) },
            onRemove = { storage.remove(it.id, it.type); renderContinueWatching() }
        )
        continueWatchingList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        continueWatchingList.adapter = continueAdapter

        // Home sections
        homePopularMoviesAdapter = DiscoverAdapter(emptyList(), "movie", onClick = { loadDetail(it.id, it.mediaType ?: "movie") }, useGridLayout = false)
        homePopularMoviesList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        homePopularMoviesList.adapter = homePopularMoviesAdapter

        homeTrendingTvAdapter = DiscoverAdapter(emptyList(), "tv", onClick = { loadDetail(it.id, "tv") }, useGridLayout = false)
        homeTrendingTvList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        homeTrendingTvList.adapter = homeTrendingTvAdapter

        homeNetflixAdapter = DiscoverAdapter(emptyList(), "movie", onClick = { loadDetail(it.id, it.mediaType ?: "movie") }, useGridLayout = false)
        homeNetflixList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        homeNetflixList.adapter = homeNetflixAdapter

        homePrimeAdapter = DiscoverAdapter(emptyList(), "movie", onClick = { loadDetail(it.id, it.mediaType ?: "movie") }, useGridLayout = false)
        homePrimeList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        homePrimeList.adapter = homePrimeAdapter

        // Movies section
        moviesAdapter = DiscoverAdapter(emptyList(), "movie", onClick = { loadDetail(it.id, "movie") })
        moviesRecycler.layoutManager = GridLayoutManager(this, 3)
        moviesRecycler.adapter = moviesAdapter

        // Series section
        seriesAdapter = DiscoverAdapter(emptyList(), "tv", onClick = { loadDetail(it.id, "tv") })
        seriesRecycler.layoutManager = GridLayoutManager(this, 3)
        seriesRecycler.adapter = seriesAdapter

        // Discover section
        discoverAdapter = DiscoverAdapter(emptyList(), "movie", onClick = { item ->
            loadDetail(item.id, item.mediaType ?: "movie")
        })
        discoverRecycler.layoutManager = GridLayoutManager(this, 3)
        discoverRecycler.adapter = discoverAdapter

        // Search
        searchAdapter = MediaAdapter(
            emptyList(), false,
            onClick = { loadDetail(it.id, it.type, 1, 1) }
        )
        searchRecycler.layoutManager = GridLayoutManager(this, 3)
        searchRecycler.adapter = searchAdapter

        // Detail cast & recommendations
        detailCastList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        detailRecommendationsList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    // ─── Listeners ────────────────────────────────────────────────────────────

    private fun setupListeners() {
        // Bottom nav
        navHome.setOnClickListener { navigateTo("home") }
        navDiscover.setOnClickListener { navigateTo("discover") }
        navMovies.setOnClickListener { navigateTo("movies") }
        navShows.setOnClickListener { navigateTo("series") }

        // Search
        searchBtn.setOnClickListener {
            val q = searchInput.text.toString().trim()
            if (q.isNotEmpty()) { hideKeyboard(); doSearch(q) }
        }
        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                val q = searchInput.text.toString().trim()
                if (q.isNotEmpty()) { hideKeyboard(); doSearch(q) }
                true
            } else false
        }

        // Search pagination
        prevPageBtn.setOnClickListener { if (searchPage > 1) doSearch(lastQuery, searchPage - 1) }
        nextPageBtn.setOnClickListener { if (searchPage < totalSearchPages) doSearch(lastQuery, searchPage + 1) }

        // Player
        closePlayer.setOnClickListener { closePlayer() }
        prevEpBtn.setOnClickListener {
            if (currentEpisode > 1) { currentEpisode-- }
            else {
                val keys = seasonsMap.keys.sorted()
                val idx = keys.indexOf(currentSeason)
                if (idx > 0) { currentSeason = keys[idx - 1]; currentEpisode = seasonsMap[currentSeason]?.episodeCount ?: 1 }
            }
            syncSpinnersToState(); updateTvFrame()
        }
        nextEpBtn.setOnClickListener {
            val epCount = seasonsMap[currentSeason]?.episodeCount ?: 1
            if (currentEpisode < epCount) { currentEpisode++ }
            else {
                val keys = seasonsMap.keys.sorted()
                val idx = keys.indexOf(currentSeason)
                if (idx < keys.size - 1) { currentSeason = keys[idx + 1]; currentEpisode = 1 }
            }
            syncSpinnersToState(); updateTvFrame()
        }

        // Movies filters
        setupFilterSpinners()

        // Movies pagination
        moviesPrevBtn.setOnClickListener { if (moviesPage > 1) { moviesPage--; loadMovies() } }
        moviesNextBtn.setOnClickListener { if (moviesPage < moviesTotalPages) { moviesPage++; loadMovies() } }

        // Series pagination
        seriesPrevBtn.setOnClickListener { if (seriesPage > 1) { seriesPage--; loadSeries() } }
        seriesNextBtn.setOnClickListener { if (seriesPage < seriesTotalPages) { seriesPage++; loadSeries() } }

        // Discover pagination
        discoverPrevBtn.setOnClickListener { if (discoverPage > 1) { discoverPage--; loadDiscover() } }
        discoverNextBtn.setOnClickListener { if (discoverPage < discoverTotalPages) { discoverPage++; loadDiscover() } }
    }

    private fun setupFilterSpinners() {
        val sortOptions = listOf(
            "Popularity ↓" to "popularity.desc",
            "Popularity ↑" to "popularity.asc",
            "Rating ↓" to "vote_average.desc",
            "Rating ↑" to "vote_average.asc",
            "Release ↓" to "release_date.desc",
            "Release ↑" to "release_date.asc",
            "Title A-Z" to "original_title.asc",
            "Title Z-A" to "original_title.desc"
        )
        val discoverCategories = listOf(
            "Popular" to "popular",
            "Top Rated" to "top_rated",
            "Now Playing" to "now_playing",
            "Upcoming" to "upcoming"
        )
        val networks = listOf(
            "All Networks" to "",
            "Netflix" to "8",
            "Amazon Prime" to "119",
            "Apple TV+" to "350",
            "Hotstar" to "122",
            "Jio Cinema" to "220",
            "Zee5" to "232",
            "Crunchyroll" to "283"
        )
        val langOptions = listOf(
            "All Languages" to "",
            "English" to "en",
            "Hindi" to "hi",
            "Tamil" to "ta",
            "Telugu" to "te",
            "Malayalam" to "ml",
            "Kannada" to "kn",
            "Japanese" to "ja",
            "Korean" to "ko",
            "Spanish" to "es",
            "French" to "fr"
        )

        // Movies sort
        val moviesSortLabels = sortOptions.map { it.first }
        moviesSortSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, moviesSortLabels)
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item) }
        moviesSortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val newSort = sortOptions[pos].second
                if (newSort != moviesSortBy) { moviesSortBy = newSort; moviesPage = 1; loadMovies() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Movies lang
        val langLabels = langOptions.map { it.first }
        moviesLangSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, langLabels)
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item) }
        moviesLangSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val newLang = langOptions[pos].second
                if (newLang != moviesLanguage) { moviesLanguage = newLang; moviesPage = 1; loadMovies() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Series sort
        seriesSortSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, moviesSortLabels)
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item) }
        seriesSortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val newSort = sortOptions[pos].second
                if (newSort != seriesSortBy) { seriesSortBy = newSort; seriesPage = 1; loadSeries() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Series lang
        seriesLangSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, langLabels)
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item) }
        seriesLangSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val newLang = langOptions[pos].second
                if (newLang != seriesLanguage) { seriesLanguage = newLang; seriesPage = 1; loadSeries() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Discover category
        val categoryLabels = discoverCategories.map { it.first }
        discoverCategorySpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, categoryLabels)
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item) }
        discoverCategorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val newCat = discoverCategories[pos].second
                if (newCat != discoverCategory) { discoverCategory = newCat; discoverNetwork = ""; discoverPage = 1; loadDiscover() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Discover network
        val networkLabels = networks.map { it.first }
        discoverNetworkSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, networkLabels)
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item) }
        discoverNetworkSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val newNet = networks[pos].second
                if (newNet != discoverNetwork) { discoverNetwork = newNet; discoverPage = 1; loadDiscover() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    // ─── Genre Loading ────────────────────────────────────────────────────────

    private fun loadGenres() {
        scope.launch {
            val mg = withContext(Dispatchers.IO) { api.movieGenres() }
            val tg = withContext(Dispatchers.IO) { api.tvGenres() }
            movieGenres = mg
            tvGenres = tg
            populateGenreSpinners()
        }
    }

    private fun populateGenreSpinners() {
        val allMovieGenres = listOf(TmdbGenre(-1, "All Genres")) + movieGenres
        val allTvGenres = listOf(TmdbGenre(-1, "All Genres")) + tvGenres
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val years = listOf("All Years") + (currentYear downTo (currentYear - 50)).map { it.toString() }

        // Movies genre spinner
        moviesGenreSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, allMovieGenres.map { it.name })
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item) }
        moviesGenreSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val g = if (pos == 0) "" else allMovieGenres[pos].id.toString()
                if (g != moviesGenreId) { moviesGenreId = g; moviesPage = 1; loadMovies() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Movies year
        moviesYearSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, years)
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item) }
        moviesYearSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val y = if (pos == 0) "" else years[pos]
                if (y != moviesYear) { moviesYear = y; moviesPage = 1; loadMovies() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Series genre
        seriesGenreSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, allTvGenres.map { it.name })
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item) }
        seriesGenreSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val g = if (pos == 0) "" else allTvGenres[pos].id.toString()
                if (g != seriesGenreId) { seriesGenreId = g; seriesPage = 1; loadSeries() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Series year
        seriesYearSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, years)
            .also { it.setDropDownViewResource(R.layout.spinner_dropdown_item) }
        seriesYearSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val y = if (pos == 0) "" else years[pos]
                if (y != seriesYear) { seriesYear = y; seriesPage = 1; loadSeries() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    // ─── Navigation ──────────────────────────────────────────────────────────

    private fun navigateTo(section: String) {
        if (searchInput.text.toString().isNotEmpty()) searchInput.setText("")
        activeSection = section
        homeSection.visibility = View.GONE
        moviesSection.visibility = View.GONE
        seriesSection.visibility = View.GONE
        discoverSection.visibility = View.GONE
        searchSection.visibility = View.GONE
        detailSection.visibility = View.GONE

        // Reset nav active states
        listOf(navHomeIcon, navDiscoverIcon, navMoviesIcon, navShowsIcon).forEach {
            it.setTextColor(Color.parseColor("#666680"))
        }
        listOf(navHomeText, navDiscoverText, navMoviesText, navShowsText).forEach {
            it.setTextColor(Color.parseColor("#666680"))
        }

        when (section) {
            "home" -> {
                homeSection.visibility = View.VISIBLE
                navHomeIcon.setTextColor(Color.parseColor("#E50914"))
                navHomeText.setTextColor(Color.parseColor("#E50914"))
                renderContinueWatching()
                loadHomeContent()
            }
            "discover" -> {
                discoverSection.visibility = View.VISIBLE
                navDiscoverIcon.setTextColor(Color.parseColor("#E50914"))
                navDiscoverText.setTextColor(Color.parseColor("#E50914"))
                if (discoverAdapter.itemCount == 0) loadDiscover()
            }
            "movies" -> {
                moviesSection.visibility = View.VISIBLE
                navMoviesIcon.setTextColor(Color.parseColor("#E50914"))
                navMoviesText.setTextColor(Color.parseColor("#E50914"))
                if (moviesAdapter.itemCount == 0) loadMovies()
            }
            "series" -> {
                seriesSection.visibility = View.VISIBLE
                navShowsIcon.setTextColor(Color.parseColor("#E50914"))
                navShowsText.setTextColor(Color.parseColor("#E50914"))
                if (seriesAdapter.itemCount == 0) loadSeries()
            }
        }
    }

    private fun showHome() { navigateTo("home") }

    private fun renderContinueWatching() {
        val items = storage.getAll()
        continueWatchingEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        continueWatchingList.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        continueAdapter.update(items)
    }

    // ─── Home Content ─────────────────────────────────────────────────────────

    private fun loadHomeContent() {
        scope.launch {
            val popular = withContext(Dispatchers.IO) { api.popularMovies() }
            val trending = withContext(Dispatchers.IO) { api.trendingTv() }
            popular?.results?.let { homePopularMoviesAdapter.update(it) }
            trending?.results?.let { homeTrendingTvAdapter.update(it) }

            // Load Netflix content
            val netflix = withContext(Dispatchers.IO) { api.discoverMovies(networkId = "8") }
            netflix?.results?.let { homeNetflixAdapter.update(it) }

            // Load Amazon Prime content
            val prime = withContext(Dispatchers.IO) { api.discoverMovies(networkId = "119") }
            prime?.results?.let { homePrimeAdapter.update(it) }
        }
    }

    // ─── Movies Content ───────────────────────────────────────────────────────

    private fun loadMovies() {
        moviesLoading.visibility = View.VISIBLE
        moviesRecycler.visibility = View.GONE
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                api.discoverMovies(moviesPage, moviesSortBy, moviesGenreId, moviesYear, moviesLanguage)
            }
            moviesLoading.visibility = View.GONE
            result?.let {
                moviesTotalPages = it.totalPages
                moviesAdapter.update(it.results ?: emptyList())
                moviesRecycler.visibility = View.VISIBLE
                moviesPrevBtn.isEnabled = moviesPage > 1
                moviesNextBtn.isEnabled = moviesPage < moviesTotalPages
                moviesPageIndicator.text = "Page $moviesPage / $moviesTotalPages"
            }
        }
    }

    // ─── Series Content ───────────────────────────────────────────────────────

    private fun loadSeries() {
        seriesLoading.visibility = View.VISIBLE
        seriesRecycler.visibility = View.GONE
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                api.discoverTv(seriesPage, seriesSortBy, seriesGenreId, seriesYear, seriesLanguage)
            }
            seriesLoading.visibility = View.GONE
            result?.let {
                seriesTotalPages = it.totalPages
                seriesAdapter.update(it.results ?: emptyList())
                seriesRecycler.visibility = View.VISIBLE
                seriesPrevBtn.isEnabled = seriesPage > 1
                seriesNextBtn.isEnabled = seriesPage < seriesTotalPages
                seriesPageIndicator.text = "Page $seriesPage / $seriesTotalPages"
            }
        }
    }

    // ─── Discover Content ─────────────────────────────────────────────────────

    private fun loadDiscover() {
        discoverLoading.visibility = View.VISIBLE
        discoverRecycler.visibility = View.GONE
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (discoverNetwork.isNotEmpty()) {
                    // Mixed movie+tv for specific network
                    val movies = api.discoverMovies(discoverPage, "popularity.desc", networkId = discoverNetwork)
                    val tvs = api.discoverTv(discoverPage, "popularity.desc", networkId = discoverNetwork)
                    val combined = ((movies?.results ?: emptyList()).map { it.copy(mediaType = "movie") } +
                            (tvs?.results ?: emptyList()).map { it.copy(mediaType = "tv") })
                        .sortedByDescending { it.popularity }
                    TmdbDiscoverResponse(combined, maxOf(movies?.totalPages ?: 1, tvs?.totalPages ?: 1))
                } else {
                    when (discoverCategory) {
                        "top_rated" -> api.topRatedMovies(discoverPage)?.let { r -> TmdbDiscoverResponse(r.results?.map { it.copy(mediaType = "movie") }, r.totalPages) }
                        "now_playing" -> api.nowPlayingMovies(discoverPage)?.let { r -> TmdbDiscoverResponse(r.results?.map { it.copy(mediaType = "movie") }, r.totalPages) }
                        "upcoming" -> api.upcomingMovies(discoverPage)?.let { r -> TmdbDiscoverResponse(r.results?.map { it.copy(mediaType = "movie") }, r.totalPages) }
                        else -> api.popularMovies(discoverPage)?.let { r -> TmdbDiscoverResponse(r.results?.map { it.copy(mediaType = "movie") }, r.totalPages) }
                    }
                }
            }
            discoverLoading.visibility = View.GONE
            result?.let {
                discoverTotalPages = it.totalPages
                discoverAdapter.update(it.results ?: emptyList())
                discoverRecycler.visibility = View.VISIBLE
                discoverPrevBtn.isEnabled = discoverPage > 1
                discoverNextBtn.isEnabled = discoverPage < discoverTotalPages
                discoverPageIndicator.text = "Page $discoverPage / $discoverTotalPages"
            }
        }
    }

    // ─── Search ──────────────────────────────────────────────────────────────

    private fun doSearch(query: String, page: Int = 1) {
        lastQuery = query; searchPage = page
        detailOriginSection = activeSection
        homeSection.visibility = View.GONE
        moviesSection.visibility = View.GONE
        seriesSection.visibility = View.GONE
        discoverSection.visibility = View.GONE
        detailSection.visibility = View.GONE
        searchSection.visibility = View.VISIBLE
        searchLoading.visibility = View.VISIBLE
        searchRecycler.visibility = View.GONE
        paginationRow.visibility = View.GONE

        scope.launch {
            val result = withContext(Dispatchers.IO) { api.search(query, page) }
            searchLoading.visibility = View.GONE
            if (result == null) {
                Toast.makeText(this@MainActivity, "Search failed. Check connection.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            totalSearchPages = result.totalPages
            val items = (result.results ?: emptyList())
                .filter { it.mediaType == "tv" || it.mediaType == "movie" }
                .map { MediaItem(it.id, it.mediaType, it.displayTitle, if (it.mediaType == "tv") "TV Series" else "Movie", it.posterUrl) }
            searchAdapter.update(items)
            searchRecycler.visibility = View.VISIBLE
            if (totalSearchPages > 1) {
                paginationRow.visibility = View.VISIBLE
                prevPageBtn.isEnabled = page > 1
                nextPageBtn.isEnabled = page < totalSearchPages
                pageIndicator.text = "Page $page of $totalSearchPages"
            }
        }
    }

    // ─── Detail ──────────────────────────────────────────────────────────────

    private fun loadDetail(id: Int, type: String, season: Int = 1, episode: Int = 1) {
        currentId = id; currentType = type
        currentSeason = season; currentEpisode = episode
        currentDetailType = type
        seasonsMap.clear()

        detailOriginSection = activeSection

        homeSection.visibility = View.GONE
        moviesSection.visibility = View.GONE
        seriesSection.visibility = View.GONE
        discoverSection.visibility = View.GONE
        searchSection.visibility = View.GONE
        detailSection.visibility = View.VISIBLE
        detailLoading.visibility = View.VISIBLE
        detailContent.visibility = View.GONE
        detailTrailerSection.visibility = View.GONE
        detailCastSection.visibility = View.GONE
        detailRecommendationsSection.visibility = View.GONE

        scope.launch {
            if (type == "movie") {
                val movie = withContext(Dispatchers.IO) { api.movie(id) }
                val credits = withContext(Dispatchers.IO) { api.movieCredits(id) }
                val videos = withContext(Dispatchers.IO) { api.movieVideos(id) }
                val recs = withContext(Dispatchers.IO) { api.movieRecommendations(id) }

                detailLoading.visibility = View.GONE
                if (movie == null) {
                    Toast.makeText(this@MainActivity, "Failed to load details.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                currentPosterUrl = movie.posterUrl
                detailContent.visibility = View.VISIBLE

                // Backdrop
                if (!movie.backdropUrl.isNullOrEmpty()) {
                    Glide.with(this@MainActivity).load(movie.backdropUrl)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .centerCrop().into(detailBackdrop)
                } else if (!movie.posterUrl.isNullOrEmpty()) {
                    Glide.with(this@MainActivity).load(movie.posterUrl)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .centerCrop().into(detailBackdrop)
                }

                // Poster
                movie.posterUrl?.let {
                    Glide.with(this@MainActivity).load(it)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .placeholder(R.drawable.poster_placeholder)
                        .error(R.drawable.poster_placeholder)
                        .into(detailPoster)
                }

                detailTitle.text = movie.displayTitle
                detailYear.text = movie.year
                detailGenre.text = movie.genres?.take(3)?.joinToString(" · ") { it.name } ?: ""
                detailRating.text = "★ ${"%.1f".format(movie.voteAverage ?: 0.0)} (${movie.voteCount ?: 0} votes)"
                detailRuntime.text = movie.runtimeFormatted
                detailBadgeType.text = "MOVIE"
                detailBadgeType.setBackgroundColor(Color.parseColor("#c0392b"))
                detailTagline.text = movie.tagline ?: ""
                detailTagline.visibility = if (movie.tagline.isNullOrEmpty()) View.GONE else View.VISIBLE
                detailOverview.text = movie.overview ?: "No overview available."
                detailSeasons.visibility = View.GONE
                detailStatus.text = "Status: ${movie.status ?: "—"}"
                detailLanguage.text = "Language: ${movie.spokenLanguages?.firstOrNull()?.englishName ?: "—"}"
                detailMoreInfoSection.visibility = View.VISIBLE

                playButton.setOnClickListener { openPlayer("movie") }

                // Cast
                val cast = credits?.cast?.take(10) ?: emptyList()
                if (cast.isNotEmpty()) {
                    detailCastSection.visibility = View.VISIBLE
                    val castAdapter = CastAdapter(cast) {}
                    detailCastList.adapter = castAdapter
                }

                // Trailer
                val trailer = videos?.results?.find { it.type == "Trailer" && it.site == "YouTube" }
                if (trailer?.key != null) {
                    detailTrailerSection.visibility = View.VISIBLE
                    detailTrailerWebView.loadUrl("https://www.youtube.com/embed/${trailer.key}?autoplay=0&controls=1")
                }

                // Recommendations
                val recItems = recs?.results?.take(10) ?: emptyList()
                if (recItems.isNotEmpty()) {
                    detailRecommendationsSection.visibility = View.VISIBLE
                    val recAdapter = DiscoverAdapter(recItems, "movie", onClick = { item ->
                        loadDetail(item.id, "movie")
                    }, useGridLayout = false)
                    detailRecommendationsList.adapter = recAdapter
                }

            } else {
                val show = withContext(Dispatchers.IO) { api.tv(id) }
                val credits = withContext(Dispatchers.IO) { api.tvCredits(id) }
                val videos = withContext(Dispatchers.IO) { api.tvVideos(id) }
                val recs = withContext(Dispatchers.IO) { api.tvRecommendations(id) }

                detailLoading.visibility = View.GONE
                if (show == null) {
                    Toast.makeText(this@MainActivity, "Failed to load details.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                show.seasons?.forEach { s ->
                    if (s.seasonNumber > 0 && s.episodeCount > 0) seasonsMap[s.seasonNumber] = s
                }
                currentPosterUrl = show.posterUrl
                detailContent.visibility = View.VISIBLE

                // Backdrop
                if (!show.backdropUrl.isNullOrEmpty()) {
                    Glide.with(this@MainActivity).load(show.backdropUrl)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .centerCrop().into(detailBackdrop)
                }

                // Poster
                show.posterUrl?.let {
                    Glide.with(this@MainActivity).load(it)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .placeholder(R.drawable.poster_placeholder)
                        .error(R.drawable.poster_placeholder)
                        .into(detailPoster)
                }

                detailTitle.text = show.displayTitle
                detailYear.text = show.year
                detailGenre.text = show.genres?.take(3)?.joinToString(" · ") { it.name } ?: ""
                detailRating.text = "★ ${"%.1f".format(show.voteAverage ?: 0.0)} (${show.voteCount ?: 0} votes)"
                detailRuntime.text = "${show.numberOfSeasons ?: 0} Season${if ((show.numberOfSeasons ?: 0) != 1) "s" else ""}"
                detailBadgeType.text = "TV SERIES"
                detailBadgeType.setBackgroundColor(Color.parseColor("#1a6fd4"))
                detailTagline.text = show.tagline ?: ""
                detailTagline.visibility = if (show.tagline.isNullOrEmpty()) View.GONE else View.VISIBLE
                detailOverview.text = show.overview ?: "No overview available."
                detailStatus.text = "Status: ${show.status ?: "—"}"
                detailLanguage.text = "Language: ${show.spokenLanguages?.firstOrNull()?.englishName ?: "—"}"
                detailMoreInfoSection.visibility = View.VISIBLE

                val seasonsText = seasonsMap.values.sortedBy { it.seasonNumber }
                    .joinToString("  ·  ") { "S${it.seasonNumber} (${it.episodeCount} eps)" }
                detailSeasons.text = "Seasons: $seasonsText"
                detailSeasons.visibility = View.VISIBLE

                playButton.setOnClickListener { openPlayer("tv") }

                // Cast
                val cast = credits?.cast?.take(10) ?: emptyList()
                if (cast.isNotEmpty()) {
                    detailCastSection.visibility = View.VISIBLE
                    val castAdapter = CastAdapter(cast) {}
                    detailCastList.adapter = castAdapter
                }

                // Trailer
                val trailer = videos?.results?.find { it.type == "Trailer" && it.site == "YouTube" }
                if (trailer?.key != null) {
                    detailTrailerSection.visibility = View.VISIBLE
                    detailTrailerWebView.loadUrl("https://www.youtube.com/embed/${trailer.key}?autoplay=0&controls=1")
                }

                // Recommendations
                val recItems = recs?.results?.take(10) ?: emptyList()
                if (recItems.isNotEmpty()) {
                    detailRecommendationsSection.visibility = View.VISIBLE
                    val recAdapter = DiscoverAdapter(recItems, "tv", onClick = { item ->
                        loadDetail(item.id, "tv")
                    }, useGridLayout = false)
                    detailRecommendationsList.adapter = recAdapter
                }
            }
        }
    }

    // ─── Player ──────────────────────────────────────────────────────────────

    private fun openPlayer(type: String) {
        playerModal.visibility = View.VISIBLE
        playerLoadingOverlay.visibility = View.VISIBLE
        playerTitle.text = detailTitle.text
        if (type == "movie") {
            tvControls.visibility = View.GONE
            movieControls.visibility = View.VISIBLE
            playerEpisodeInfo.text = "Movie"
            loadPlayerFrame("$EMBED_MOVIE/$currentId")
            storage.add(MediaItem(currentId, "movie", detailTitle.text.toString(), "Movie", currentPosterUrl))
        } else {
            tvControls.visibility = View.VISIBLE
            movieControls.visibility = View.GONE
            buildSpinners()
            updateTvFrame()
        }
    }

    private fun loadPlayerFrame(embedUrl: String) { playerWebView.loadUrl(embedUrl) }

    private fun closePlayer() {
        if (::chromeClient.isInitialized) {
            chromeClient.exitFullscreenIfNeeded()
        }
        playerModal.visibility = View.GONE
        playerLoadingOverlay.visibility = View.VISIBLE
        playerWebView.stopLoading()
        playerWebView.loadUrl("about:blank")
    }

    private fun buildSpinners() {
        val seasons = seasonsMap.values.sortedBy { it.seasonNumber }
        if (seasons.isEmpty()) return
        val seasonLabels = seasons.map {
            it.name?.takeIf { n -> n != "Season ${it.seasonNumber}" }
                ?.let { n -> "$n (${it.episodeCount} eps)" }
                ?: "Season ${it.seasonNumber}  ·  ${it.episodeCount} eps"
        }
        val sAdapter = ArrayAdapter(this, R.layout.spinner_item, seasonLabels)
        sAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        seasonSpinner.adapter = sAdapter
        val sIdx = seasons.indexOfFirst { it.seasonNumber == currentSeason }.coerceAtLeast(0)
        seasonSpinner.setSelection(sIdx, false)
        seasonSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val ns = seasons[pos].seasonNumber
                if (ns != currentSeason) { currentSeason = ns; currentEpisode = 1; buildEpisodeSpinner(); updateTvFrame() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        buildEpisodeSpinner()
    }

    private fun buildEpisodeSpinner() {
        val epCount = seasonsMap[currentSeason]?.episodeCount ?: return
        val labels = (1..epCount).map { "Episode $it" }
        val eAdapter = ArrayAdapter(this, R.layout.spinner_item, labels)
        eAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        episodeSpinner.adapter = eAdapter
        episodeSpinner.setSelection((currentEpisode - 1).coerceAtLeast(0), false)
        episodeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val ne = pos + 1
                if (ne != currentEpisode) { currentEpisode = ne; updateTvFrame() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun syncSpinnersToState() { buildSpinners() }

    private fun updateTvFrame() {
        playerLoadingOverlay.visibility = View.VISIBLE
        loadPlayerFrame("$EMBED_TV/$currentId/$currentSeason/$currentEpisode")
        val epInfo = "S${currentSeason} · E${currentEpisode}"
        playerEpisodeInfo.text = epInfo
        storage.add(MediaItem(currentId, "tv", detailTitle.text.toString(), epInfo, currentPosterUrl, currentSeason, currentEpisode))
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            playerModal.visibility == View.VISIBLE && ::chromeClient.isInitialized && chromeClient.exitFullscreenIfNeeded() -> {}
            playerModal.visibility == View.VISIBLE -> closePlayer()
            detailSection.visibility == View.VISIBLE -> {
                detailSection.visibility = View.GONE
                when (detailOriginSection) {
                    "movies" -> { moviesSection.visibility = View.VISIBLE; activeSection = "movies" }
                    "series" -> { seriesSection.visibility = View.VISIBLE; activeSection = "series" }
                    "discover" -> { discoverSection.visibility = View.VISIBLE; activeSection = "discover" }
                    else -> {
                        if (lastQuery.isNotEmpty()) { searchSection.visibility = View.VISIBLE }
                        else { homeSection.visibility = View.VISIBLE; activeSection = "home" }
                    }
                }
            }
            searchSection.visibility == View.VISIBLE -> { searchInput.setText(""); navigateTo(activeSection.ifEmpty { "home" }) }
            else -> super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::chromeClient.isInitialized && chromeClient.isFullscreen()) {
            if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
                ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        playerWebView.destroy()
    }
}

// ─── Cast Adapter ─────────────────────────────────────────────────────────────

class CastAdapter(
    private val cast: List<TmdbCastMember>,
    private val onClick: (TmdbCastMember) -> Unit
) : RecyclerView.Adapter<CastAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val image: ImageView = v.findViewById(R.id.castImage)
        val name: TextView = v.findViewById(R.id.castName)
        val character: TextView = v.findViewById(R.id.castCharacter)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cast_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val member = cast[pos]
        h.name.text = member.name ?: ""
        h.character.text = member.character ?: ""
        if (!member.profileUrl.isNullOrEmpty()) {
            Glide.with(h.image.context)
                .load(member.profileUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.poster_placeholder)
                .error(R.drawable.poster_placeholder)
                .circleCrop()
                .into(h.image)
        } else {
            h.image.setImageResource(R.drawable.poster_placeholder)
        }
        h.itemView.setOnClickListener { onClick(member) }
    }

    override fun getItemCount() = cast.size
}
