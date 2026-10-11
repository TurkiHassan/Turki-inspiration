# ZTV

A native Android TV IPTV player written in Java, built for the remote control (D-pad) first and
also usable on phones and tablets.

ZTV is a **player only**. It ships with no channels, playlists or servers: each user signs in
with the Xtream Codes server address, username and password from their own provider.

## Features

- Xtream Codes API (`player_api.php`) sign-in with a user-entered server address
- Live TV, movies and series with categories, search and "recently added"
- Dashboard, favorites, watch history and resume ("continue watching")
- Short EPG (now / next) on live channels
- Playback through AndroidX Media3 (ExoPlayer) with auto-reconnect and tuned live/VOD buffering
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
