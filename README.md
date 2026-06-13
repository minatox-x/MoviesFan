<div align="center">

```
███████╗███╗   ███╗ █████╗ ██████╗ ████████╗███████╗██████╗ ███████╗
██╔════╝████╗ ████║██╔══██╗██╔══██╗╚══██╔══╝██╔════╝██╔══██╗╚════██║
███████╗██╔████╔██║███████║██████╔╝   ██║   █████╗  ██████╔╝    ██╔╝
╚════██║██║╚██╔╝██║██╔══██║██╔══██╗   ██║   ██╔══╝  ██╔══██╗   ██╔╝ 
███████║██║ ╚═╝ ██║██║  ██║██║  ██║   ██║   ███████╗██║  ██║   ██║  
╚══════╝╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝   ╚══════╝╚═╝  ╚═╝   ╚═╝  
```

**Stream smarter. Watch anything.**

[![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/TMDB-API-01B4E4?style=flat-square)](https://themoviedb.org)
[![License](https://img.shields.io/badge/License-MIT-F5C518?style=flat-square)](LICENSE)

</div>

---

## What is Smarter?

Smarter is a native Android streaming companion that lets you search any movie or TV show, browse rich metadata, and watch directly inside the app — no browser, no redirects, no nonsense. It wraps a smart WebView player that blocks ads at the navigation layer while letting the actual video through cleanly.

---

## Features

| | |
|---|---|
| 🔍 **Universal Search** | Search across movies and TV shows simultaneously via TMDB multi-search |
| 🎬 **Rich Detail Pages** | Poster, overview, genres, rating, runtime, and release year at a glance |
| 📺 **Season & Episode Picker** | Full season/episode navigation with spinners and prev/next buttons |
| ▶️ **Embedded Player** | Direct WebView playback — no external browser, no app switching |
| 🛡️ **Smart Ad Blocking** | Navigation-layer blocking: intent/market/app-store schemes and known ad hosts are silently dropped |
| 🕶️ **Desktop UA Spoofing** | Injects a full Chrome 124 Windows UA to bypass mobile redirect detection |
| ⛶ **True Fullscreen** | Hardware-accelerated fullscreen with landscape lock and system UI hide |
| 🕐 **Recently Watched** | Persistent watch history with season/episode progress, grid layout, swipe-to-remove |
| 📄 **Pagination** | Search results paginate with page indicator and prev/next controls |

---

## Architecture

```
com.smarterz.app
├── MainActivity.kt          ← single-activity architecture, all UI logic
│
├── SmartWebViewClient       ← URL navigation guard
│   ├── shouldOverrideUrlLoading()   blocks ad schemes + non-player hosts
│   ├── shouldInterceptRequest()     blocks non-http resource schemes
│   ├── onPageFinished()             hides loading overlay (main frame only)
│   └── SPOOF_JS                     navigator.webdriver / platform / UA spoof
│
├── SmartChromeClient        ← fullscreen + popup routing
│   ├── onShowCustomView()           enters fullscreen (landscape + hide system UI)
│   ├── onHideCustomView()           exits fullscreen
│   └── onCreateWindow()             routes sub-player popups back into WebView
│
├── TmdbApi                  ← TMDB REST client (OkHttp + Gson)
│   ├── search(query, page)
│   ├── movie(id)
│   └── tv(id)
│
├── MediaAdapter             ← RecyclerView adapter (grid, poster + badge)
└── RecentStorage            ← SharedPreferences-backed watch history (Gson)
```

---

## How the Player Works

The player is intentionally minimal:

```kotlin
private fun loadPlayerFrame(embedUrl: String) {
    playerWebView.loadUrl(embedUrl)
}
```

That's it. No wrapper HTML, no fake base URLs, no iframe injection. The embed URL loads directly. Ad blocking happens entirely inside `SmartWebViewClient.shouldOverrideUrlLoading`:

```
Request comes in
    │
    ├─ Blocked scheme? (intent://, market://, tel://)  →  DROP
    ├─ Known ad host? (doubleclick, play.google.com)   →  DROP
    ├─ Known player host?                              →  ALLOW ✓
    ├─ Originated from a player host? (Referer check)  →  ALLOW ✓
    └─ Everything else                                 →  DROP
```

Player redirect chains (vidsrc → cloudnestra → rabbitstream → CDN) all flow through because each hop is either a known player domain or carries a player referer.

---

## Tech Stack

| Library | Purpose |
|---|---|
| [OkHttp](https://square.github.io/okhttp/) | HTTP client for TMDB API calls |
| [Gson](https://github.com/google/gson) | JSON parsing for API responses |
| [Glide](https://bumptech.github.io/glide/) | Poster image loading with crossfade |
| [AndroidX RecyclerView](https://developer.android.com/jetpack/androidx/releases/recyclerview) | Grid layout for search results and history |
| [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) | Non-blocking API calls on IO dispatcher |
| [WebView](https://developer.android.com/reference/android/webkit/WebView) | Embedded video player |
| [TMDB API](https://developers.themoviedb.org/3) | Movie/TV metadata and search |

---

## Setup

**1. Clone**
```bash
git clone https://github.com/yourname/smarter.git
cd smarter
```

**2. Open in Android Studio**

File → Open → select the project root. Let Gradle sync.

**3. Build & Run**

Connect a device or start an emulator, then:
```bash
./gradlew installDebug
```

> Requires Android 5.0 (API 21) or higher.

---

## Project Structure

```
app/
├── src/main/
│   ├── java/com/smarterz/app/
│   │   └── MainActivity.kt
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml
│   │   │   ├── item_media_card.xml
│   │   │   ├── spinner_item.xml
│   │   │   └── spinner_dropdown_item.xml
│   │   └── drawable/
│   │       └── poster_placeholder.xml
│   └── AndroidManifest.xml
└── build.gradle
```

---

## Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

That's the only permission required.

---

## Known Limitations

- Stream availability depends on third-party embed providers — if a source is down, try again later
- Some embed chains require JavaScript-heavy redirect pages; initial load can take 3–5 seconds on slow connections
- Subtitles depend on the embed provider and are not always available

---

## Contributing

Pull requests welcome. For major changes, open an issue first to discuss what you'd like to change.

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m 'add: my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

---

## Disclaimer

This app does not host or distribute any media. It embeds publicly accessible third-party player URLs. Use responsibly and in accordance with the laws of your jurisdiction.

---

<div align="center">

Built with ♥ using Kotlin and too much caffeine.

</div>
