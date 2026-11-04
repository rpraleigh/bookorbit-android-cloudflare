# BookOrbit — Android (native)

Native Kotlin / Jetpack Compose client for a self-hosted
[BookOrbit](https://github.com/bookorbit/bookorbit) server. This is a **standalone Gradle project** —
open this repository's root directly in Android Studio.

> **This is a fork.** "BookOrbit PRM-CF" is [deranjer/bookorbit-android](https://github.com/deranjer/bookorbit-android)
> plus optional **Cloudflare Access service-token** support, so the app can reach a BookOrbit server that is
> published behind Cloudflare Access. It installs next to the original app (application id
> `com.bookorbit.cloudflare`) and keeps the upstream AGPL-3.0 license (see [LICENSE](LICENSE)). Documentation
> below the Cloudflare sections is upstream's and may still refer to the original app and package names.

> User-facing docs: **https://deranjer.github.io/bookorbit-android/** (source in [`website/`](website/)).
> Privacy policy: **https://deranjer.github.io/bookorbit-android/privacy.html**.
> Planned work: see [`ROADMAP.md`](ROADMAP.md).
> Also available via [F-Droid](https://deranjer.github.io/fdroid/repo) (self-hosted repo).

> **Status:** this is the BookOrbit mobile client. It supports auth + OIDC, library browsing, search,
> smart scopes, collections, authors/series, book detail, the foliate.js ebook reader, the Media3
> audiobook player (with sleep timer and Chromecast), and downloads/offline. See
> [Architecture & conventions](#architecture--conventions) below, the
> [Cross-repo couplings](#cross-repo-couplings) section, and [`ROADMAP.md`](ROADMAP.md) for what's next.

## Cloudflare Access support

### What it adds

- Optional **Client ID** and **Client Secret** fields on the server-setup screen. Leave them empty if your
  server is not behind Cloudflare Access; the app then behaves like upstream.
- The app sends `CF-Access-Client-Id` and `CF-Access-Client-Secret` on every request to your server: API calls,
  token refresh, cover images, audiobook streaming and Chromecast.
- The secret is stored in the app's encrypted storage. Nothing secret is built into the APK.

### Set up Cloudflare (once)

1. In Cloudflare Zero Trust, create a **service token** and keep its Client ID and Client Secret.
2. In the Access application that protects your BookOrbit hostname, add a policy whose action is
   **Service Auth** and that includes the token. A normal Allow policy does not accept service tokens.

### Set up the app

1. Enter your server address, starting with `https://`.
2. Enter the Client ID and Client Secret, then tap **Connect**.
3. Sign in with your BookOrbit username and password.

### Behavior worth knowing

- **Headers go to your server only.** They are attached only to requests for the configured host, only over
  HTTPS, and the host is checked again on every redirect, so the secret is never sent to another host or in
  clear text.
- **`http://` addresses are upgraded when the server redirects.** If you enter `http://host` and the server
  answers from `https://host` (same host and path), the app saves the HTTPS address. Without this, the redirect
  turns the sign-in POST into a GET and sign-in fails. A plain-HTTP server that does not redirect (for example on
  a LAN) works as before.
- **Clear errors.** The app tells you when Cloudflare Access blocked the request (no token) or rejected it
  (bad or expired token), and only reports "Invalid username or password" for an HTTP 401 from the server.
- **Native reader only.** The server-hosted web reader runs in a WebView that cannot send these headers, so the
  app always uses its native reader when a service token is configured.
- **No SSO sign-in behind Access.** Username and password sign-in works. The OIDC browser sign-in cannot carry
  the service token, so it is not supported when Access covers the whole hostname.

### Build this fork

The project has two flavors (`full` and `fdroid`), so use flavor-qualified task names. On Windows use
`gradlew.bat` instead of `./gradlew`.

```bash
./gradlew :app:testFullDebugUnitTest :app:assembleFullDebug
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

For a signed release APK, create a signing key with `keytool`, copy `app/keystore.properties.example` to
`app/keystore.properties` (it is git-ignored) and fill in your values. Use an **absolute path** for
`storeFile`. Then:

```bash
./gradlew :app:assembleFullRelease
```

The signed file is `app/build/outputs/apk/full/release/app-full-release.apk`. Keep the key file and its
password backed up: updates must be signed with the same key.

## Prerequisites

| Tool               | Version                                | Notes                                                   |
| ------------------ | -------------------------------------- | ------------------------------------------------------- |
| **Android Studio** | Ladybug (2024.2) or newer              | Easiest path; bundles a compatible JDK + SDK manager.   |
| **JDK**            | 17+ (21 recommended)                   | Android Studio's bundled JBR works. CI uses Temurin 21. |
| **Android SDK**    | Platform **36**, Build-Tools 35+       | Installed via Android Studio's SDK Manager.             |
| **A device**       | Emulator (API 26+) or a physical phone | Min SDK is **26**; compile/target SDK is **36**.        |

Gradle itself does **not** need to be installed — the committed wrapper (`./gradlew`) downloads the
right version (8.11.1) on first run.

## First-time setup

1. **Install the SDK.** In Android Studio: _Settings → Languages & Frameworks → Android SDK_ → install
   **Android API 36** (SDK Platform) and a recent **Build-Tools**. Accept the SDK licenses.
2. **`local.properties`.** This machine-specific file points Gradle at your SDK and must **not** be
   committed (it's git-ignored). Android Studio creates it automatically on first open. To create it
   by hand, put one line in `android/local.properties` (use forward slashes, even on Windows, to
   avoid Java-properties escaping issues):

   ```properties
   sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
   ```

   On macOS/Linux it's typically `~/Library/Android/sdk` / `~/Android/Sdk`.

## Run it in Android Studio (recommended)

1. **File → Open** and select this repository's **root** folder.
2. Wait for the initial **Gradle sync** to finish (first time pulls dependencies).
3. Pick a device in the toolbar's device dropdown (or create one in **Device Manager**).
4. Click **Run ▶**. Studio builds, installs, and launches the app.

The debug variant installs as application id **`com.bookorbit.debug`** (the `.debug` suffix lets debug
and release builds coexist on one device).

## Run it from the command line

From the repository root:

```bash
./gradlew :app:installDebug      # build + install on the connected device/emulator
./gradlew :app:assembleDebug     # just build the APK -> app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest # JVM unit tests
./gradlew :app:lintDebug         # Android lint
```

Launch after installing:

```bash
adb shell monkey -p com.bookorbit.debug -c android.intent.category.LAUNCHER 1
```

(`adb` lives in `<sdk>/platform-tools/`.) To start an emulator headlessly:
`"$ANDROID_HOME/emulator/emulator" -avd <avd-name>`.

## Connecting to a BookOrbit server

On first launch the app asks for a server URL. To point at a **local dev server**
(`pnpm run db:up && pnpm dev` from the repo root, listening on `:3000`):

- **Emulator:** the host machine is reachable at `10.0.2.2`, so enter `http://10.0.2.2:3000`.
- **Physical device:** use your machine's LAN IP, e.g. `http://192.168.1.50:3000`.

Cleartext HTTP is permitted (self-hosted servers are often plain HTTP on a LAN); prefer HTTPS when
exposed. For **OIDC** login, the server must whitelist the mobile redirect URI
`bookorbit://oauth2-callback` (`OIDC_MOBILE_REDIRECT_URIS`, the default).

## Project layout

```
app/src/main/
    assets/reader/            # foliate.js + bridge.js + index.html (foliate copied verbatim from the web client's build)
    java/com/bookorbit/
      app/                    # Application (Hilt), MainActivity, nav graph (BookOrbitApp / AuthenticatedApp)
      core/
        auth/                 # SessionManager, SecureStorage (EncryptedSharedPreferences)
        network/              # Retrofit ApiService, interceptors, token refresh, ImageUrls
        model/                # hand-written @Serializable DTOs (mirror the server's API types)
        db/                   # Room: reader/audio progress + downloads
        di/                   # Hilt modules (network, database, player)
        paging/               # Paging 3 sources
      feature/
        auth/ library/ search/ scopes/ collections/ authors/ series/
        dashboard/ bookdetail/ main/
        reader/               # foliate WebView host + bridge + CFI progress
        player/               # Media3 service, MediaController manager, player UI
        downloads/            # WorkManager downloads + offline catalog
      ui/                     # shared Compose components + theme
```

## Architecture & conventions

- **Kotlin + Jetpack Compose** (Material 3), **MVVM** with `ViewModel` + `StateFlow`, **Hilt** DI,
  **Coroutines/Flow**. Networking is Retrofit/OkHttp + kotlinx.serialization; images via Coil;
  audiobooks via Media3/ExoPlayer; persistence via Room + DataStore; downloads via WorkManager.
- **No emoji as icons** — use `androidx.compose.material.icons` (Material Symbols), matching the repo
  convention.
- **API models are hand-written** Kotlin `@Serializable` classes (there is no OpenAPI codegen), so
  server DTO changes must be mirrored here by hand. See [Cross-repo couplings](#cross-repo-couplings).
- **Reader theming is a three-way sync.** The foliate assets (`assets/reader/`) are a verbatim copy of
  the web client's build; when the web foliate or its `bridge.js` `THEMES`/`generateCSS` changes,
  re-copy into `assets/reader/` and keep `feature/reader/ReaderThemes.kt` aligned. See
  [Cross-repo couplings](#cross-repo-couplings) for the full rule.

## Testing

```bash
./gradlew :app:testDebugUnitTest    # unit tests (query builder, playback math, serialization)
```

CI (`.github/workflows/android-build.yml`) runs lint + unit tests + `assembleDebug` on every PR and
on pushes to `main`.

## Cross-repo couplings

This app is developed independently of the [BookOrbit server](https://github.com/bookorbit/bookorbit),
but two things must be kept in sync **by hand** when the server/web client changes — there is no build
dependency or codegen that enforces them:

1. **API DTOs.** The models in `app/src/main/java/com/bookorbit/core/model/` are hand-written
   `@Serializable` mirrors of the server's `/api/v1` response types. When a server endpoint's shape
   changes, update the matching Kotlin model here. `BaseUrlInterceptor` rewrites every request onto the
   configured server + `/api/v1`.
2. **Foliate reader assets (three-way sync).** `app/src/main/assets/reader/` is a verbatim copy of the
   web client's foliate.js build (`client/public/assets/foliate/` in the server repo) plus `bridge.js`,
   `index.html`. When the web client updates foliate or the `bridge.js` `THEMES`/`generateCSS` block,
   re-copy those files here and keep the settings-UI swatches in
   `app/src/main/java/com/bookorbit/feature/reader/ReaderThemes.kt` aligned with the same theme
   definitions. This preserves byte-identical EPUB CFI sync with the web client and Kobo.
