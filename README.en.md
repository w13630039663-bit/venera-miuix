<div align="center">

<img src="screenshots/readme-banner.svg" width="100%" alt="Venera: Native Manga and Gallery Reader">

[![Version](https://img.shields.io/badge/version-2.0-orange)](https://github.com/w13630039663-bit/venera-miuix/releases)
[![Branch](https://img.shields.io/badge/branch-compose--migration-blue)](https://github.com/w13630039663-bit/venera-miuix/tree/compose-migration)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack_Compose-1.7.8-2A2A2A)
![Miuix](https://img.shields.io/badge/Miuix_KMP-0.9.4--rc01-0E7AFF)
![Android](https://img.shields.io/badge/Android-API%2033%2B-3DDC84?logo=android&logoColor=white)
[![License GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-FF7A00)](LICENSE)

[简体中文](README.md) | **English**

</div>

---

## Screenshots

<p align="center">
  <img src="screenshots/home.jpg" height="320" alt="Home">
  <img src="screenshots/search.jpg" height="320" alt="Search">
  <img src="screenshots/comic-detail.jpg" height="320" alt="Comic Detail">
  <img src="screenshots/reading-stats.jpg" height="320" alt="Reading Stats">
  <img src="screenshots/search-sources.jpg" height="320" alt="Multi-Source Search">
</p>
<p align="center"><sub>Comic Home · Search & Discover · Comic Detail · Reading Stats · Multi-Source Search</sub></p>

<p align="center">
  <img src="screenshots/gallery.jpg" height="320" alt="Gallery Wall">
  <img src="screenshots/gallery-pixiv.jpg" height="320" alt="Pixiv Home">
  <img src="screenshots/pixiv-artist.jpg" height="320" alt="Pixiv Artist Profile">
  <img src="screenshots/pixiv-work-detail.jpg" height="320" alt="Pixiv Artwork Detail">
  <img src="screenshots/gallery-artist.jpg" height="320" alt="Artist via Cross-Site Jump">
  <img src="screenshots/gallery-pixiv-jump.jpg" height="320" alt="Direct Jump to Pixiv">
</p>
<p align="center"><sub>Gallery Wall · Pixiv Home · Pixiv Artist Profile · Native Artwork Detail · Cross-Site Artist Landing · Direct Jump to Pixiv</sub></p>

---

## Table of contents

- [What is this](#what-is-this)
- [Screenshots](#screenshots)
- [Core Features](#core-features)
  - [Gallery & Pixiv Native Ecosystem](#1-gallery--pixiv-native-ecosystem)
  - [Comic Reader](#2-comic-reader)
  - [Comic Source & Network Engine](#3-comic-source--network-engine)
  - [Favorites & History](#4-favorites--history)
  - [Downloads & Local Bookshelf](#5-downloads--local-bookshelf)
  - [Rating, Filtering & Privacy](#6-rating-filtering--privacy)
  - [Appearance & Personalization](#7-appearance--personalization)
- [Known Gaps & Deliberate Non-Goals](#known-gaps--deliberate-non-goals)
- [Download & Installation](#download--installation)
- [Building from Source](#building-from-source)
- [Architecture & Tech Stack](#architecture--tech-stack)
- [License & Acknowledgements](#license--acknowledgements)

---

## What is this

**Venera** is an excellent open-source comic reader: comic sources are written as JavaScript plugins to aggregate local and online comics.

This branch rewrites upstream Venera from Flutter into pure native Kotlin + Jetpack Compose, revamps all screens with Xiaomi HyperOS's Miuix design language, and introduces a powerful native Gallery and Pixiv multi-source aggregation module.

This branch (`compose-migration`) focuses on four core innovations:

1. **Pure Native Rewrite**: Full migration to Kotlin + Jetpack Compose, removing Flutter/Dart/Rust runtimes for lower memory usage and millisecond cold starts.
2. **Miuix Design System**: Complete UI rebuild around Xiaomi HyperOS's Miuix design language and liquid glass material, supporting seamless switching between Miuix and Material 3 dynamic color palettes.
3. **Plugin Protocol Compatibility**: Full byte-for-byte compatibility with the official ComicSource JavaScript plugin protocol, bundled with 34 popular source scripts and supporting one-tap import and bulk updates.
4. **Gallery & Pixiv Multi-Source Aggregation**: A native "Gallery" module on the bottom bar, offering full Booru image-board integration and Pixiv dual-domain aggregation with native artwork detail, artist profiles, account sync, and smart cross-platform deep-linking.

---

## Core Features

### 1. Gallery & Pixiv Native Ecosystem

- **Dual-Domain Multi-Source Aggregation**: Top segmented pill switch between "Booru" (yande.re + Gelbooru + Safebooru) and "Pixiv", each maintaining independent state machines and feeds.
- **Pixiv Native Ecosystem Integration**:
  - **Live Rankings & Recommendations**: Daily, weekly, monthly, rookie, and original ranking leaderboards; logged-in accounts access official discovery feeds with shuffle support; offline mode offers tag-seed-based smart recommendations.
  - **Native Artwork Details (`PixivWorkActivity`)**: Multi-page continuous vertical reader, complete metadata panel (translated tags, artwork metrics), official one-tap bookmarking & artist following, and direct original image saving.
  - **Native Artist Profile (`PixivArtistActivity`)**: Artist banner, avatar, illustrations/manga/public bookmarks tabs, and one-tap follow.
  - **My Pixiv Account (`PixivAccountActivity`)**: Activities, cloud bookmarks (public & private), and followed artists list with automatic scroll-to-top on entry.
- **Cross-Source Smart Jump & Direct ID Navigation**:
  - **Source Link Sniffing**: Automatically detects Pixiv source URLs in Booru artwork details and provides an prominent pill button to open native Pixiv artwork details directly.
  - **Search Bar ID / URL Direct Jump**: Input pure artwork IDs, `#ID`, `id:ID`, or Pixiv web URLs into the search bar to jump instantly to native artwork details.
- **Expandable Followed Artists Grid**: The followed artists row on the homepage smoothly expands (`animateContentSize`) into a 5-column grid showing up to 10 artists on-screen, with vertical scrolling for larger collections.
- **Minimalist Image-Only Favorites & Decoupled Architecture**:
  - Top single-line "Pixiv Bookmarks" card directly accesses cloud bookmarks;
  - Local favorites display a clean 3-column adaptive waterfall without border clutter or heart overlays;
  - Horizontal Miuix glass pill filter and sorter (filter by source site and safety rating, sort by latest/oldest);
  - **Dual-Track Decoupling**: Pixiv bookmarks communicate directly with official APIs without polluting the local favorites database.
- **Pixiv-Specific Customization**:
  - Independent quality controls for card preview, artwork page, and fullscreen viewer ("Medium / Large / Original");
  - Dedicated download path and filename templates (`{illust_id}`, `{part}`, `{title}`, `{user_id}`, `{artist_name}`);
  - Network modes (ECH / Compatible / Standard) and image host switching (`i.pximg.net` / `i.pixiv.re` / Custom Host).
- **Full Gallery Miuix Dynamic Frosted Blur (Blur Backdrop)**: Live blur sampling applied across Gallery home, daily rankings, favorites, history, Pixiv account, and artwork details for fluid top-bar frosted-glass backdrops while scrolling.
- **Gallery Discovery Tools**: Daily trending, recommendations, artist alias normalization, SauceNAO reverse image search, AI inpainting, and "Paint as Watercolor" feature.

### 2. Comic Reader

- **All Reading Modes Supported**: Continuous vertical webtoon, Japanese manga (right-to-left), Western comic (left-to-right), dual-page spread, and continuous vertical scroll.
- **Touch & Reading Controls**: Smooth two-finger zoom, auto-scroll cruising (1–10s adjustable), volume key navigation, immersive fullscreen, and keep-screen-on.
- **Display Adjustments & Colors**: Night inverted mode, green-tint eye protection, contrast enhancement, sharpening filters, and tonal adjustments.
- **Seamless Reading Experience**: Auto-advance to next chapter at the end of a volume, with exact chapter and page-level progress resumption.

### 3. Comic Source & Network Engine

- **JavaScript Plugin Ecosystem**: Fully compatible with upstream plugin protocol, supporting dynamic rule compilation, category trees, and search.
- **Automated Cloudflare Bypass**: Built-in automated challenge solver with dynamic Cookie/UA persistence.
- **High-Performance Networking**: HTTP/SOCKS5 proxy, per-host circuit breaking, DNS optimized IP routing, anti-ban rate limiting, and multi-connection image distribution.
- **Deep Linking**: Direct intent resolution for 29+ comic sites from external browsers into the app.

### 4. Favorites & History

- **Dual Local & Remote Favorites**: Multiple custom folders, cover-to-detail shared element transitions, and one-tap remote source sync.
- **Background Update Checker**: Set any favorite folder as auto-tracking; background worker polls for new chapters daily and pushes system notifications.
- **Reading History & Statistics**: Reading time tracking, page counts, 14-day reading curve, Top 10 most-read titles, and genre distribution charts.

### 5. Downloads & Local Bookshelf

- **Multi-Thread High-Speed Downloads**: 1–16 configurable concurrency, chapter multi-selection, resume broken downloads, retry on failure, and progress notifications.
- **Integrity Verification**: Automatic detection of fake/placeholder images and corrupt bytes; incomplete chapters are explicitly marked with an "Incomplete" badge.
- **External Format Support**: Local comic directory scanner, CBZ archive import, and packaging/export.

### 6. Rating, Filtering & Privacy

- **Multi-Tier Content Masking**: Unfiltered / Blur cover / Completely hidden modes powered by rules and built-in source presets.
- **Precise Blocking Engine**: Full-word or regex matching across keywords, tags, artists, and comic IDs, including one-tap explicit AI content filtering.
- **Privacy Shield**: System-level `FLAG_SECURE` prevents screenshots, screen recording, and task switcher previews across all Activities.

### 7. Appearance & Personalization

- **Dual UI Styles**: Native Material 3 (with Android 12+ wallpaper dynamic color) and HyperOS Miuix style.
- **Materials & Motion**: Liquid floating glass bottom bar, dynamic frosted blur (Blur Backdrop), and responsive fluid touch animations.
- **Custom Header & Mascots**: Custom header images for home and gallery, plus optional top-bar mascot sticker.

---

## Known Gaps & Deliberate Non-Goals

- **Chapter Comments & Log Viewer**: Basic definitions exist in code; UI entry points are pending integration.
- **Desktop (Windows / Linux / macOS)**: Focus remains on Android native evolution; the desktop branch (`:desktop`) is an independent experimental module.
- **Biometric App Lock**: Hardware biometric authentication is not currently built-in; system-level app locks are recommended.
- **Non-Official Disclaimer**: Users must comply with third-party service terms; this repository is for educational and personal reading purposes only.

---

## Download & Installation

Pre-built APKs are available on the **[Releases](https://github.com/w13630039663-bit/venera-miuix/releases)** page.

| Architecture | Target Devices |
| :--- | :--- |
| `venera-miuix-universal.apk` | Universal package (all architectures) |
| `venera-miuix-arm64-v8a.apk` | Modern Android phones and tablets (Recommended) |
| `venera-miuix-armeabi-v7a.apk` | Legacy 32-bit devices |
| `venera-miuix-x86_64.apk` | PC Android emulators / Chromebooks |

> **System Requirements**: Android 13 (API 33) and above.

---

## Building from Source

### Prerequisites
- JDK 17 or JDK 21
- Android SDK Platform 35 / Build-Tools 35.0.0

### Common Build Commands
```bash
# Build debug APK
./gradlew :app:assembleDebug

# Build release APKs for all ABIs
./gradlew :app:assembleRelease

# Run unit tests
./gradlew :app:testDebugUnitTest
```

---

## Architecture & Tech Stack

- **Language**: Kotlin 2.1+ / Kotlin Coroutines & Flow
- **UI Framework**: Jetpack Compose 1.7+ / Miuix KMP / Material 3
- **Image Loading & Network**: Coil 3 (Kotlin Multiplatform) / OkHttp 4
- **Persistence & Serialization**: SQLite (Room / SQLDelight) / kotlinx.serialization

---

## License & Acknowledgements

- **License**: Released under the **GNU General Public License v3.0 (GPL-3.0)**.
- **Upstream Acknowledgements**: Thanks to original Venera creator [@wgh136](https://github.com/wgh136) and its innovative plugin architecture.
- **Component Acknowledgements**: Thanks to [Miuix-KMP](https://github.com/MiuiX-KMP/Miuix-KMP) for bringing the HyperOS visual experience to Compose.
