# ASL Beats — `sigma67/ytmusicapi` Music Discovery & Authorized Playback Plan

Integrates a FastAPI discovery service powered by `sigma67/ytmusicapi` with the existing ASL Beats Android app for unauthenticated catalog browsing, artwork/lyrics display, and compliant playback routed solely through `AudioSourceResolver` (`LocalAudioProvider` + Creative Commons `LicensedAudioProvider`) on Media3 ExoPlayer.

### User Review & Critical Decisions

> [!IMPORTANT]
> All four architectural decisions below were confirmed during discovery and govern the design:
> - **FastAPI Backend Organization & URL**: A standalone Python FastAPI service (`sigma67/ytmusicapi`) will live in a dedicated backend module within the repository, paired with an in-app configurable backend URL in ASL Beats Settings (defaulting to local emulator/LAN access) and connection health status.
> - **Authorized Audio Source (`LicensedAudioProvider`)**: Track matching and authorized streaming will use the unauthenticated public **Audius** Creative Commons / artist-licensed catalog API alongside **LocalAudioProvider** (device library & local `SongEntity` / `MediaStore` matches).
> - **Strict Extraction Compliance**: YouTube audio extraction and unauthorized stream extractors will be disabled in the playback and download pipelines so YouTube tracks never stream or download unauthorized audiovisual copies.
> - **Unavailable Audio UX**: When neither `LocalAudioProvider` nor `LicensedAudioProvider` has an authorized match, ASL Beats displays an **"Audio source unavailable"** prompt with one-tap actions to open the user's **Local Music** picker or launch the **official YouTube Music** external link.

---

### 1. Overview & Core Concept

- **What It Does**: Separates *music discovery and metadata* (search, suggestions, song/album/artist/playlist/radio exploration, permitted cover art, and lyrics) from *authorized audio playback*. The FastAPI service wraps `sigma67/ytmusicapi` in unauthenticated mode to deliver normalized JSON catalog data, while ASL Beats resolves playable audio strictly through `AudioSourceResolver` using local device tracks or explicitly streamable Creative Commons recordings.
- **Target Audience / Persona**: ASL Beats listeners discovering new tracks, albums, artists, and playlists who want instant metadata, artwork, and lyrics inside ASL Beats with transparent, policy-compliant playback on Media3.
- **Key Value**: Reliable public catalog discovery without Google account cookies or OAuth, zero unauthorized YouTube stream extraction, preserved local library playback and MediaSession controls, and graceful external handoff when a track has no licensed stream.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Discovery Search & Browse**: Typing in Search triggers debounced autocomplete suggestions (`ytmusicapi`). Submitting runs paginated track search showing album artwork thumbnails, title, artist, duration, and smooth skeleton/dot loading states. Tapping an album, artist, or playlist loads rich detail screens with "Up Next / Radio" recommendations.
  2. **Track Selection & Source Resolution**: Selecting a discovery track immediately populates Now Playing metadata, loads its permitted thumbnail via URL, and fetches/renders plain or timestamped synced lyrics without storing unauthorized media copies. In parallel, `AudioSourceResolver` checks `LocalAudioProvider` first and then `LicensedAudioProvider` (Audius public stream catalog).
  3. **Authorized Playback vs. Unavailable Prompt**:
     - *Matched*: Media3 ExoPlayer plays the authorized local URI or licensed stream with full notification, lock-screen, Bluetooth/headset, seek, shuffle, repeat, and queue controls.
     - *Unmatched*: ASL Beats clearly surfaces **"Audio source unavailable"**, keeps metadata/lyrics inspectable, and offers quick actions to browse **Local Music** or **Open in YouTube Music**.
  4. **Backend Settings**: Users can inspect/edit the FastAPI backend base URL and verify connectivity (`Live` / `Unreachable`) inside Settings → Integrations.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Editorial, high-contrast dark studio aesthetic matching ASL Beats' existing dark canvas, deep card surfaces, and crisp accent highlights rather than flat default cards.
  - *Color Palette & Typography*: Uses `MaterialTheme.colorScheme` semantic tokens (`background`, `surface`, `primary`, `onSurfaceVariant`, `errorContainer`) paired with existing ASL Beats rounded display/body font pairings (`sp` scaling) and monospace metadata badges for track duration and source status (`Local`, `Licensed CC`, `External`).
  - *Component Styling & Layout*: Edge-to-edge layouts respecting `WindowInsets.safeDrawing` and `WindowInsets.navigationBars`, minimum `48.dp` touch targets, `AnimatedVisibility` / `spring()` cover art and state transitions, large Now Playing album art with subtle pulse/scale animation while playing, tactile playback controls, progress slider, lyrics toggle, queue sheet, shuffle, and repeat controls.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Normalized FastAPI Discovery Service over Embedded Scraping**
  - *Chosen Approach*: Isolate `sigma67/ytmusicapi` behind typed FastAPI endpoints (`/api/search`, `/api/suggestions`, `/api/song/{video_id}`, `/api/lyrics/{video_id}`, `/api/album/{browse_id}`, `/api/artist/{browse_id}`, `/api/playlist/{playlist_id}`, `/api/radio/{video_id}`) using public unauthenticated `YTMusic()`.
  - *Why*: Clean schema contracts, centralized timeout/rate-limit/error handling, easy independent updates when upstream YouTube Music shapes shift, and zero cookie/OAuth friction.
  - *Alternatives Considered*: Keeping direct Kotlin InnerTube scraping (tightly coupled, brittle to upstream changes) or requiring browser cookies (breaks zero-login discovery).

- **Decision 2 — Authorized-Only `AudioSourceResolver` Pipeline**
  - *Chosen Approach*: Introduce `AudioSourceProvider` implemented by `LocalAudioProvider` (device/Room local files) and `LicensedAudioProvider` (unauthenticated Audius Creative Commons / stream-permitted API matching normalized title + artist + duration tolerance), orchestrated by `AudioSourceResolver` inside Media3's data resolution path with YouTube extraction disabled.
  - *Why*: Guarantees `ytmusicapi` remains strictly metadata-only, protects local playback, and provides an honest "Audio source unavailable" fallback rather than unauthorized extraction.
  - *Alternatives Considered*: Retaining fallback YouTube stream extractors (explicitly prohibited by compliance requirements).

- **Decision 3 — Reuse ASL Beats Architecture & Preserve Existing Modules**
  - *Chosen Approach*: Integrate into existing `com.mochyy.mochymusic` Hilt DI, Ktor/OkHttp + `kotlinx.serialization`, Room DB, Compose Navigation, and Media3 `MusicService` (`MediaLibrarySession`, audio focus, Bluetooth/headset, notifications).
  - *Why*: Avoids redundant DI/player/database frameworks, protects local playlists and MediaSession behavior, and leaves web assets completely untouched.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

#### Architecture & Component Diagram

```text
┌───────────────────────────────────────────────────────────────────────────┐
│                         ASL Beats Android App (Kotlin)                    │
│                                                                           │
│  ┌──────────────────────┐    ┌──────────────────────┐    ┌──────────────┐ │
│  │ Search & Discovery   │    │ Now Playing & Lyrics │    │ Integrations │ │
│  │ - Suggestions / Page │    │ - Animated Art, Seek │    │ - Backend URL│ │
│  │ - Album/Artist/List  │    │ - Synced/Plain Lyric │    │ - Health Ping│ │
│  └──────────┬───────────┘    │ - Queue / Unavail UI │    └──────┬───────┘ │
│             │                └──────────┬───────────┘           │         │
│             ▼                           ▼                       ▼         │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │           ViewModels (Hilt) & Unified Catalog Repository             │ │
│  │  - Normalized metadata caching (title, artist, album, duration, art) │ │
│  │  - Lyrics fetching (only permitted plain/synced text, no AV caching) │ │
│  └──────────────────┬─────────────────────────────┬─────────────────────┘ │
│                     │                             │                       │
│                     ▼                             ▼                       │
│  ┌──────────────────────────────────┐  ┌────────────────────────────────┐ │
│  │ YtMusicBackendClient (Ktor/JSON) │  │ AudioSourceResolver            │ │
│  │  GET /api/search, /song, /lyrics │  │  1. LocalAudioProvider         │ │
│  │  GET /album, /artist, /playlist  │  │     (Device / Local Room URIs) │ │
│  │  GET /radio, /suggestions        │  │  2. LicensedAudioProvider      │ │
│  └──────────────────┬───────────────┘  │     (Audius Streamable Tracks) │ │
└─────────────────────┼──────────────────┴────────────────┬───────────────┘ │
                      │ HTTP / JSON                       │ Matched URI or  │
                      ▼                                   │ Unavailable     │
┌───────────────────────────────────────┐                 ▼                 │
│ Python FastAPI Backend (`ytmusicapi`) │  ┌──────────────────────────────┐ │
│  - Unauthenticated `YTMusic()`        │  │ Media3 ExoPlayer & Service   │ │
│  - Pydantic normalized models         │  │  - Audio-only playback       │ │
│  - Timeouts, HTTP 404/429/502 guards  │  │  - MediaSession / Lockscreen │ │
└───────────────────────────────────────┘  │  - Queue, Focus, BT Controls │ │
                                           └──────────────────────────────┘ │
```

#### Data Model & State
- **Backend Pydantic / Android `@Serializable` Models**: Normalized structures for `TrackSummary` (`videoId`, `title`, `artists`, `album`, `durationSeconds`, `thumbnailUrl`, `isExplicit`), `SongDetail`, `LyricsPayload` (`lines`, `hasTimestamps`, `source`), `AlbumDetail`, `ArtistDetail`, `PlaylistDetail` (`tracks`, `continuation`), and `RadioQueue` (`tracks`).
- **Authorized Source Models (`AudioSourceProvider`)**:
  - `AudioSourceResult.AuthorizedStream(uri, title, artist, sourceType, licenseLabel, mimeType, durationSeconds)`
  - `AudioSourceResult.Unavailable(reason, externalUrl, localSuggestions)`
  - `AudioSourceProvider` interface implemented independently by `LocalAudioProvider` (queries Room local songs / device content URIs using normalized fuzzy title + artist matching) and `LicensedAudioProvider` (searches Audius `/v1/tracks/search`, checks `is_streamable`, scores artist/title/duration similarity, returns `/v1/tracks/{id}/stream`).
- **Preference & UI States**: DataStore preference for custom FastAPI base URL; sealed UI states (`Loading`, `Loaded`, `Paginating`, `Error`, `SourceUnavailable`) driving Search, Lyrics, Now Playing, and Unavailable dialogs without exposing raw network exceptions or hanging spinners.

#### Interactive Component & State Mapping
- **Search & Suggestions**: Debounced input calls FastAPI `/api/search` and suggestion queries; pagination appends distinct tracks using continuation tokens; invalid/unreachable backend triggers a clean retry state with editable backend settings link.
- **Metadata, Artwork & Lyrics**: Selecting a track binds `TrackSummary` to Now Playing, renders remote artwork URL cleanly via Coil (graceful placeholder if missing), and loads `/api/lyrics/{video_id}`. Timestamped lines drive active lyric highlight synced to ExoPlayer playback position; plain lyrics render clean static text; missing lyrics show a friendly empty state. Downloads of YouTube audiovisual streams remain disabled.
- **Media3 Playback & Queue**: `MusicService` routes remote discovery tracks through `AudioSourceResolver` inside its resolving data source (short-circuiting non-local YouTube IDs whenever unauthorized rather than running extractors). Local tracks continue resolving `content://` / `file://` directly, preserving MediaSession, lock-screen & notification transport actions, audio focus, Bluetooth/headset media keys, queue reordering, shuffle, and repeat modes. When resolution returns `Unavailable`, UI shows **"Audio source unavailable"** with actions to open **Local Music** or **YouTube Music** externally.

#### Verification Plan
- **Python Backend (`pytest` + FastAPI `TestClient`)**: Verify `/api/search`, `/api/song/{video_id}`, `/api/lyrics/{video_id}` (both plain, synced, and missing), `/api/album/{browse_id}`, `/api/artist/{browse_id}`, `/api/playlist/{playlist_id}` pagination, `/api/radio/{video_id}`, private/missing content (404), rate-limit/timeout normalization (429/504), and upstream malformed payload resilience.
- **Android Unit & Robolectric Tests**: Test JSON deserialization, missing thumbnail/lyrics handling, network failure mapping, `LocalAudioProvider` & `LicensedAudioProvider` track matching accuracy, strict rejection of YouTube extraction in `AudioSourceResolver`, Media3 queue/playback/MediaSession state transitions, and Gradle APK compilation (`compile_applet` / `:app:testDebugUnitTest`).
