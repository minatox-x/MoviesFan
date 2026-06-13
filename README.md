<div align="center">

```
███╗   ███╗ ██████╗ ██╗   ██╗██╗███████╗███████╗███████╗ █████╗ ███╗   ██╗
████╗ ████║██╔═══██╗██║   ██║██║██╔════╝██╔════╝██╔════╝██╔══██╗████╗  ██║
██╔████╔██║██║   ██║██║   ██║██║█████╗  ███████╗█████╗  ███████║██╔██╗ ██║
██║╚██╔╝██║██║   ██║╚██╗ ██╔╝██║██╔══╝  ╚════██║██╔══╝  ██╔══██║██║╚██╗██║
██║ ╚═╝ ██║╚██████╔╝ ╚████╔╝ ██║███████╗███████║██║     ██║  ██║██║ ╚████║
╚═╝     ╚═╝ ╚═════╝   ╚═══╝  ╚═╝╚══════╝╚══════╝╚═╝     ╚═╝  ╚═╝╚═╝  ╚═══╝
```

**Your movies. Your shows. No nonsense.**

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![TMDB](https://img.shields.io/badge/TMDB-API-01B4E4?style=flat-square)](https://themoviedb.org)
[![SDK](https://img.shields.io/badge/compileSdk-34-FF6B35?style=flat-square)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-E50914?style=flat-square)](LICENSE)

</div>

---

## What is MoviesFan?

**MoviesFan** is a native Android app that lets you browse, discover, and watch movies and TV shows — all inside the app. No browser. No redirects. No ads breaking your fullscreen.

It pulls rich metadata from TMDB (posters, ratings, cast, trailers, recommendations) and plays everything through a hardened WebView with serious ad-blocking and fullscreen protection baked in.

---

## Screens

| Screen | What it does |
|---|---|
| 🏠 **Home** | Continue Watching row + trending movies & shows at a glance |
| 🎬 **Movies** | Browse popular, top-rated, upcoming & now-playing films |
| 📺 **Series** | Trending TV with full season & episode navigation |
| 🔭 **Discover** | Filter by genre, sort order, year, rating, and more |
| 🔍 **Search** | Multi-search across movies and TV simultaneously |
| 📋 **Detail** | Poster, backdrop, overview, genres, runtime, cast, trailers, recommendations |
| ▶️ **Player** | Full-screen embedded player — no app switching, no browser |

---

## Features

- **Continue Watching** — Persistent watch history saved to SharedPreferences, shown right on the Home screen with a swipe-to-remove grid layout
- **Season & Episode Picker** — Spinner-based navigation for every season and episode, with prev/next buttons and auto-sync to player state
- **Cast Row** — Horizontal scrolling cast cards with profile photos and character names pulled from TMDB Credits API
- **Recommendations** — Inline horizontal list of similar titles below every detail page
- **Trailer Support** — YouTube trailer integration via TMDB Videos API
- **Discover Filters** — Genre, sort order, year range, and minimum rating spinners for both movies and TV
- **Pagination** — Page indicator with prev/next controls on all browse and search screens
- **Smart Ad Blocking** — Navigation-layer blocking of intent/market/tel schemes and known ad hosts, without touching actual video content
- **Iron-Lock Fullscreen** — JS patches override `exitFullscreen`, `webkitExitFullscreen`, `requestFullscreen`, and `fullscreenchange` events so ads can't yank you out of fullscreen
- **Desktop UA Spoofing** — Chrome 124 Windows user-agent injected to bypass mobile redirect loops
- **Popup Routing** — Sub-player popup windows are caught and routed back into the WebView rather than opening external browsers

---

## Architecture

Single-activity app. Everything lives in `MainActivity.kt`.

```
com.smarterz.app
├── Data Models
│   ├── MediaItem                   watch history entry (id, type, title, season, episode)
│   ├── TmdbSearchResult            multi-search hit (movie or tv)
│   ├── TmdbMovieDetail             full movie metadata
│   ├── TmdbTvDetail                full TV metadata with seasons list
│   ├── TmdbCastMember              actor name, character, profile photo
│   ├── TmdbVideo                   trailer key + site
│   └── TmdbDiscoverItem            card for browse/discover grids
│
├── RecentStorage                   SharedPreferences-backed history (key: "recent_v5")
│   ├── getAll()                    returns List<MediaItem>
│   ├── add(item)                   upserts by id+type, caps at 20 entries
│   └── remove(id, type)            swipe-to-remove from home grid
│
├── TmdbApi                         OkHttp REST client
│   ├── search(query, page)
│   ├── movie(id) / tv(id)
│   ├── movieCredits / tvCredits
│   ├── movieVideos / tvVideos
│   ├── movieRecommendations / tvRecommendations
│   ├── popularMovies / trendingTv / topRatedMovies / upcomingMovies / nowPlayingMovies
│   ├── discoverMovies / discoverTv  (genre, sort, year, rating filters)
│   └── movieGenres / tvGenres
│
├── SmartWebViewClient              navigation guard + JS injection
│   ├── shouldOverrideUrlLoading()  blocks ad/app schemes, allows player domains
│   ├── shouldInterceptRequest()    blocks non-http resource schemes
│   ├── onPageFinished()            hides loading overlay on main frame
│   └── SPOOF_JS                    UA spoof + fullscreen iron-lock (6 sections)
│
├── SmartChromeClient               fullscreen + popup manager
│   ├── onShowCustomView()          enters fullscreen, locks to landscape, hides system UI
│   ├── onHideCustomView()          exits fullscreen, restores portrait
│   ├── onCreateWindow()            routes sub-player popups back into WebView
│   └── silences all JS dialogs     (alert, confirm, prompt, beforeunload)
│
├── MediaAdapter                    RecyclerView grid for search results & continue watching
└── GridDiscoverAdapter             RecyclerView grid for browse/discover screens
```

---

## How the Player Works

The embed URL loads directly — no wrapper HTML, no fake base URLs:

```kotlin
private fun loadPlayerFrame(embedUrl: String) {
    playerWebView.loadUrl(embedUrl)
}
```

Ad blocking happens inside `SmartWebViewClient`:

```
Incoming URL
    │
    ├── Blocked scheme? (intent://, market://, tel://, javascript://)  →  DROP
    ├── Blocked root site? (e.g. vidsrcme.ru root, not /embed)         →  DROP
    ├── Known ad host?    (doubleclick, play.google.com, etc.)          →  DROP
    ├── Known player host? (vidsrc.to, cloudnestra, filemoon, etc.)    →  ALLOW ✓
    ├── Referer from a player host?                                     →  ALLOW ✓
    └── Everything else                                                 →  DROP
```

**Allowed player domains:** `vidsrc.to`, `vidsrc.xyz`, `vidsrc.net`, `vidsrc.in`, `vidsrc.pm`, `vidsrc.rip`, `cloudnestra`, `vidplay`, `filemoon`, `megacloud`, `rabbitstream`, `youtube`

**Fullscreen JS iron-lock (6 sections injected on every page):**
1. Override `navigator.webdriver`, `platform`, and `userAgent` to spoof desktop Chrome
2. Block `window.open` popups from non-player domains
3. Intercept `<a target="_blank">` clicks to prevent fullscreen drops
4. Make `exitFullscreen` / `webkitExitFullscreen` / `mozCancelFullScreen` / `msExitFullscreen` silent no-ops — so ad SDKs can't pull you out of fullscreen
5. Spoof `document.fullscreenElement` to `null` so ads think they're not in fullscreen
6. Patch `requestFullscreen` to block ad iframes from hijacking it

---

## Tech Stack

| Library | Version | Purpose |
|---|---|---|
| [OkHttp](https://square.github.io/okhttp/) | 4.12.0 | HTTP client for TMDB API |
| [Gson](https://github.com/google/gson) | 2.10.1 | JSON deserialization |
| [Glide](https://bumptech.github.io/glide/) | 4.16.0 | Poster & backdrop image loading with crossfade |
| [RecyclerView](https://developer.android.com/jetpack/androidx/releases/recyclerview) | 1.3.2 | Grid and horizontal scroll lists |
| [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) | 1.7.3 | Non-blocking API calls on IO dispatcher |
| [Material Components](https://github.com/material-components/material-components-android) | 1.11.0 | UI theming |
| [SwipeRefreshLayout](https://developer.android.com/jetpack/androidx/releases/swiperefreshlayout) | 1.1.0 | Pull-to-refresh |
| [WebView](https://developer.android.com/reference/android/webkit/WebView) | system | Embedded video player |
| [TMDB API v3](https://developers.themoviedb.org/3) | — | All movie/TV metadata, search, cast, trailers |

---

## Setup

**1. Clone**
```bash
git clone https://github.com/yourname/MoviesFan.git
cd MoviesFan
```

**2. Open in Android Studio**

File → Open → select the project root. Let Gradle sync.

**3. Add your TMDB API key**

The key is baked into `TmdbApi.kt`. Replace the `KEY` constant with your own from [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api).

**4. Build & Run**

```bash
./gradlew installDebug
```

> Requires Android 7.0 (API 24) or higher. Target SDK 34.

---

## Project Structure

```
MoviesFan/
├── app/
│   ├── src/main/
│   │   ├── java/com/smarterz/app/
│   │   │   └── MainActivity.kt          ← entire app (2357 lines)
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml
│   │   │   │   ├── item_media_card.xml
│   │   │   │   ├── item_grid_card.xml
│   │   │   │   ├── item_cast_card.xml
│   │   │   │   ├── spinner_item.xml
│   │   │   │   └── spinner_dropdown_item.xml
│   │   │   ├── drawable/
│   │   │   │   ├── poster_placeholder.xml
│   │   │   │   ├── card_bg.xml
│   │   │   │   ├── backdrop_gradient.xml
│   │   │   │   └── search_bg.xml
│   │   │   ├── values/
│   │   │   │   └── themes.xml           ← dark theme, #E50914 red accent
│   │   │   └── xml/
│   │   │       └── network_security_config.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── .github/workflows/release.yml        ← CI: builds release APK on push to main
└── build.gradle
```

---

## CI / Release

GitHub Actions builds a signed release APK on every push to `main` or `master`.

The workflow (`release.yml`):
- Sets up JDK 17 (Temurin) + Gradle 8.4
- Caches Gradle packages
- Runs `gradle assembleRelease`
- Uploads `app-release.apk` as an artifact (retained 7 days)

Signing config is read from `local.properties` — keep that file out of version control.

---

## Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

That's it. No storage, no location, no camera.

---

## Known Limitations

- Stream availability depends on third-party embed providers — sources go down occasionally
- Initial player load can take 3–5 seconds on slow connections due to JS-heavy redirect chains
- Subtitles depend entirely on the embed provider and aren't always available
- Some player domains may rotate — update `ALLOWED_DOMAINS` in `SmartWebViewClient` if streams stop loading

---

## Contributing

PRs are welcome. For big changes open an issue first.

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit: `git commit -m 'add: my feature'`
4. Push: `git push origin feature/my-feature`
5. Open a Pull Request

---

## Disclaimer

MoviesFan does not host or distribute any media content. It embeds publicly accessible third-party player URLs. Use responsibly and in accordance with the laws of your jurisdiction.

---

<div align="center">

Built with ♥ in Kotlin — dark theme, red accent, zero ads.

</div>
