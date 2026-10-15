# ZTV

A native Android TV IPTV player written in Java, built for the remote control (D-pad) first and
also usable on phones and tablets.

ZTV is a **player only**. It ships with no channels, playlists or servers: each user signs in
with the Xtream Codes server address, username and password from their own provider.

## Features

- Xtream Codes API (`player_api.php`) sign-in with a user-entered server address
- Live TV, movies and series with categories, search and "recently added"
- Voice search from the header mic or the remote's search key: say a channel, movie or series
  in Arabic or English. On Android 13+ "Auto" records once and transcribes in both languages,
  then keeps the reading that matches the playlist best; other readings appear as "Also heard"
- Search that tolerates spelling: Arabic letter variants and diacritics, Arabic-Indic digits,
  words in any order, number words ("MBC one", "روتانا واحد"), and Arabic spellings of Latin
  channel names ("ام بي سي اكشن" finds "MBC-Action-HD", "الجزيرة" finds "Al Jazeera")
- Dashboard, favorites, watch history and resume ("continue watching")
- Kids mode: a parent PIN, an allow-list of categories plus single channels, movies and series
  (anything the provider adds later stays hidden), a separate kids profile for favorites and
  history, a daily watch-time limit and bedtime, and a lock screen only the PIN opens
- Full Arabic interface with right-to-left layout (Settings › General › App language)
- Screen edges setting that shrinks the interface for TVs that crop the picture (overscan)
- Short EPG (now / next) on live channels
- Playback through AndroidX Media3 (ExoPlayer) with auto-reconnect and tuned live/VOD buffering
- FFmpeg audio decoder fallback (AC-3, E-AC-3, DTS) for boxes without hardware support, plus automatic decoder fallback
- Optional frame rate matching (AFR): switches the TV refresh rate to the video, e.g. 50 Hz for sports
- Stay close to live: after a stall, live channels play slightly faster until they catch back up
- Optional tunneled playback for supported Android TV devices
- Player settings: live buffer size, audio decoder (Device / FFmpeg) and audio delay (±300 ms)
- Clear error messages when the provider refuses a stream (e.g. HTTP 503)
- Credentials stored with EncryptedSharedPreferences
- Memory + disk catalog cache and an on-disk poster cache for fast cold starts
- Workspace UI inspired by [IPTVnator](https://github.com/4gray/iptvnator), DM Sans typeface and
  Material Icons

## Requirements

- Android 6.0+ (minSdk 23, targetSdk 36), Android TV or touch devices
- JDK 21 to build

## Build

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew testDebugUnitTest assembleDebug
```

Build outputs are written to `~/Library/Caches/ZTV-build/` instead of `build/` (see the root
`build.gradle`), so the project can live in an iCloud-synced folder. Change `externalBuildRoot`
there if you prefer the default location.

### Release signing

`app/build.gradle` reads an optional `~/.config/ztv/signing.properties`:

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

If the file is missing, the release build is produced unsigned. Never commit keystores or
passwords.

## Project layout

```text
app/src/main/java/com/ztv/player/
  data/        Xtream API client, parsers, stores (session, favorites, history, progress, cache)
  workspace/   Launcher activity, pages, dialogs, design tokens
  ui/          Shared UI helpers
  PlayerActivity.java and playback policies (Media3)
```

## License

MIT, see [LICENSE](LICENSE). Third-party notices are in [NOTICE](NOTICE).
