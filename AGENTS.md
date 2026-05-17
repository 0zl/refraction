# Refraction — Agent Build Guide

## Project Identity

**Name**: Refraction  
**Type**: Native Android Application  
**Language**: Kotlin (100% Kotlin, no Java source files)  
**Package**: `shiro.refraction`  
**Minimum SDK**: 26 (Android 8.0)  
**Target SDK**: 35 (Android 15)  
**Compile SDK**: 35  

**Purpose**: A lightweight multi-account WebView manager that isolates web sessions per profile via encrypted cookie jar swapping. Designed primarily for Facebook, but architected to support any single-domain web app.

**Size Constraint**: APK must stay under 5MB with R8/ProGuard enabled. No React Native, Flutter, or cross-platform frameworks.

---

## Core Architecture

### Model: Profile-Based Session Isolation

The app runs **one WebView** and swaps cookie jars underneath it. This is the only viable approach for 10+ accounts without destroying device memory.

Each user account is called a **Profile**. Each Profile owns:
- Display name (user-editable)
- Color identifier (Material You palette)
- Encrypted cookie bundle
- Creation and last-used timestamps
- Profile icon initials (auto-generated from name, first 2 characters)

### Data Flow

```
User opens app
    -> Load last active Profile (or default)
    -> Decrypt cookie bundle
    -> Inject into CookieManager
    -> Load WebView with target URL

User switches Profile
    -> Capture current cookies from CookieManager (main thread)
    -> Encrypt and save to current Profile (IO thread)
    -> Clear WebView state (cookies, storage, cache)
    -> Decrypt target Profile cookies (IO thread)
    -> Inject into CookieManager (main thread)
    -> Emit switchEvent -> WebView reloads (after cookies are restored)
```

### Threading Rules

**CookieManager must be called on the main thread.** This is not optional — calling `getCookie`, `setCookie`, or `removeAllCookies` from a background thread causes a crash.

| Operation | Thread |
|-----------|--------|
| `CookieManager.getCookie()` | Main |
| `CookieManager.setCookie()` | Main |
| `CookieManager.removeAllCookies()` | Main |
| `CookieManager.flush()` | Main |
| `CookieStorage.save/load/delete` | IO |
| `Room DAO operations` | IO (handled by Room) |

`CookieRepository` handles this internally — CookieManager calls are wrapped in `withContext(Dispatchers.Main)`, storage calls in `withContext(Dispatchers.IO)`.

### Storage Layer

| Component | Library | Purpose |
|-----------|---------|---------|
| Profile metadata | Room | Names, colors, timestamps, IDs |
| Cookie bundles | EncryptedSharedPreferences (AndroidX Security) | AES-256 encrypted cookie strings |
| Encryption key | Android Keystore | Hardware-backed or TEE-backed key |

**Cookie format per Profile**: `Map<String, String>` where key is domain and value is the full `CookieManager.getCookie(domain)` string. Serialized to JSON before encryption.

**Room schema**:
```kotlin
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,           // UUID
    val name: String,                     // User-defined name
    val colorHex: String,                 // Material color hex
    val isDefault: Boolean,               // Load on startup
    val createdAt: Long,
    val lastUsedAt: Long
)
```

---

## Technology Stack

### Required Dependencies

```kotlin
// AndroidX Core
implementation("androidx.core:core-ktx:1.15.0")
implementation("androidx.appcompat:appcompat:1.7.0")
implementation("androidx.activity:activity-ktx:1.9.3")

// Material Design 3
implementation("com.google.android.material:material:1.12.0")

// Room (metadata only) — uses KSP, not kapt
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// Encrypted SharedPreferences
implementation("androidx.security:security-crypto:1.1.0-alpha06")

// Coroutines (async cookie ops)
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

// Lifecycle (ViewModel, etc.)
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

// Fragment (activityViewModels delegate)
implementation("androidx.fragment:fragment-ktx:1.8.5")

// SwipeRefreshLayout (for WebView pull-to-refresh)
implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

// RecyclerView
implementation("androidx.recyclerview:recyclerview:1.3.2")

// CoordinatorLayout
implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")

// WebKit (algorithmic darkening)
implementation("androidx.webkit:webkit:1.12.1")
```

### Build Configuration

- `minifyEnabled true` (R8) in release
- `shrinkResources true` in release
- Kotlin DSL for build scripts (`build.gradle.kts`)
- KSP (not kapt) for Room annotation processing
- No data binding, no Compose (keep APK small)
- View system with XML layouts
- `namespace = "shiro.refraction"` in build.gradle.kts (not in AndroidManifest)

---

## Directory Structure

```
refraction/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/shiro/refraction/
│       │   ├── RefractionApp.kt                    // Application class
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── RefractionDatabase.kt       // Room database singleton
│       │   │   │   ├── ProfileDao.kt               // Room DAO
│       │   │   │   ├── ProfileEntity.kt            // Room entity
│       │   │   │   └── CookieStorage.kt            // Encrypted cookie I/O
│       │   │   └── model/
│       │   │       └── Profile.kt                  // Domain model (data class)
│       │   ├── domain/
│       │   │   ├── ProfileManager.kt               // Profile CRUD + switching
│       │   │   └── CookieRepository.kt             // Cookie capture/restore/clear
│       │   ├── ui/
│       │   │   ├── main/
│       │   │   │   ├── MainActivity.kt             // Single activity, WebView host
│       │   │   │   ├── MainViewModel.kt            // State + switchEvent flow
│       │   │   │   └── WebViewClient.kt            // RefractionWebViewClient + RefractionWebChromeClient
│       │   │   ├── dashboard/
│       │   │   │   ├── DashboardBottomSheet.kt     // Profile switcher bottom sheet
│       │   │   │   └── ProfileAdapter.kt           // RecyclerView adapter + ViewHolder (inner class)
│       │   │   └── dialog/
│       │   │       ├── AddProfileDialog.kt         // Combined add/edit dialog
│       │   │       └── CookieBottomSheet.kt        // Cookie extraction view + copy
│       │   └── util/
│       │       ├── Constants.kt                    // Target URL, Material colors
│       │       └── Extensions.kt                   // dpToPx, toRelativeTime
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml               // CoordinatorLayout + Toolbar + WebView + loading overlay
│           │   ├── bottom_sheet_dashboard.xml      // Profile list bottom sheet
│           │   ├── bottom_sheet_cookie.xml         // Cookie extraction bottom sheet
│           │   ├── item_profile.xml                // Single profile row
│           │   └── dialog_profile_form.xml         // Add/edit profile form
│           ├── menu/
│           │   ├── main_menu.xml                   // WebView toolbar overflow: Add Profile, Refresh, Extract/Clear Cookies
│           │   └── profile_context_menu.xml        // Long-press: Edit, Delete
│           ├── values/
│           │   ├── colors.xml
│           │   ├── strings.xml
│           │   ├── themes.xml
│           │   └── dimens.xml
│           ├── values-v31/
│           │   └── themes.xml                      // Splash screen config for API 31+
│           ├── drawable/
│           │   ├── circle_color.xml                // Circle shape for avatar
│           │   ├── circle_selected.xml             // Selected ring overlay
│           │   ├── ic_check.xml                    // Checkmark vector
│           │   ├── ic_arrow_drop_down.xml          // Dropdown arrow for profile switcher
│           │   ├── ic_empty.xml                    // Transparent 1px for splash icon
│           │   └── splash_background.xml           // Solid color splash background
│           └── mipmap-*dpi/                        // Launcher icons (PNG, all densities)
├── .github/workflows/
│   ├── build-debug.yml                            // GitHub Actions: debug build on push/PR
│   └── build-release.yml                          // GitHub Actions: release build on tag
├── .semaphore/
│   ├── semaphore.yml                              // Semaphore CI: debug build
│   └── release.yml                                // Semaphore CI: release promotion
├── build-release.sh                               // Docker release build wrapper
├── build-debug.sh                                 // Docker debug build wrapper
├── docker-compose.yml                             // Docker build environment
├── .dockerignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── AGENTS.md (this file)
```

---

## Key Implementation Rules

### 1. CookieManager Handling

`CookieManager.getInstance()` is a **global singleton per process**. All WebViews share it. This is why swapping is necessary.

**Critical**: All CookieManager methods must be called on the **main thread**. The `CookieRepository` class enforces this by wrapping CookieManager calls in `withContext(Dispatchers.Main)`.

**Capture cookies** (save current session):
```kotlin
withContext(Dispatchers.Main) {
    CookieManager.getInstance().getCookie(url)
}
```

**Restore cookies** (load session):
```kotlin
withContext(Dispatchers.Main) {
    cookieManager.setCookie(url, "name=value")
    cookieManager.flush()
}
```

**Clear cookies** (before switching):
```kotlin
withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { continuation ->
        CookieManager.getInstance().removeAllCookies {
            continuation.resume(Unit)
        }
    }
}
```

**Important**: `removeAllCookies`, `setCookie`, and `getCookie` are all asynchronous or have specific threading requirements. Never call them from a background thread.

### 2. Profile Switch Sequence

Profile switching must be sequential and atomic. The flow in `ProfileManager.switchToProfile()`:

1. Capture current profile cookies (main thread read, IO thread encrypt)
2. Clear all cookies via `removeAllCookies` (main thread, suspend until callback)
3. Restore target profile cookies (IO thread decrypt, main thread inject)
4. Update lastUsedAt timestamp in Room

After this completes, `MainViewModel` emits `switchEvent`, which `MainActivity` observes to call `clearWebViewState()` and `webView.loadUrl()`. This ensures the WebView only reloads after cookies are fully restored — no race condition.

### 3. WebView State Clearing

Before reloading WebView after a switch, clear all state to prevent cross-contamination:

```kotlin
webView.clearCache(true)
WebStorage.getInstance().deleteAllData()
webView.clearHistory()
webView.clearFormData()
```

This runs on the main thread in `MainActivity`, triggered by `switchEvent`.

### 4. Encryption

All cookie bundles must be encrypted with `EncryptedSharedPreferences` using `MasterKey` from Android Keystore:

```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val sharedPreferences = EncryptedSharedPreferences.create(
    context,
    "cookies_$profileId",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

### 5. Cookie Capture Triggers

Save cookies at these moments:
- `onPause()` of MainActivity
- After `onPageFinished()` if URL is the target domain
- Immediately before switching profiles (inside `ProfileManager.switchToProfile`)
- User taps "Clear Cookies" (deletes the stored bundle)

### 6. Target Domain

The app is built for single-domain use. The default target is Facebook, but make the domain configurable via `Constants.kt`:

```kotlin
object Constants {
    const val TARGET_DOMAIN = "https://www.facebook.com"
    const val TARGET_URL = "https://www.facebook.com"

    val MATERIAL_COLORS = listOf(
        "#2196F3", "#F44336", "#4CAF50", "#9C27B0",
        "#FF9800", "#009688", "#E91E63", "#3F51B5",
        "#00BCD4", "#FFC107", "#CDDC39", "#FF5722"
    )
}
```

All cookie capture/restore operations should use this domain.

### 7. Profile Limits

No hardcoded limit, but UI should handle 20+ profiles gracefully (scrollable RecyclerView).

---

## UI/UX Specifications

### Toolbar Layout

The toolbar has two distinct interaction zones:

**Left side** — Profile switcher (clickable):
- Colored indicator dot (10dp circle, tinted with profile color)
- Profile name text
- Dropdown arrow icon
- Tapping this entire area opens the Dashboard BottomSheet
- NOT a navigation icon — it's a custom `LinearLayout` inside the toolbar

**Right side** — Overflow menu (triple dot):
- Add Profile
- Refresh
- Extract Cookies
- Clear Cookies

The toolbar title display is disabled (`setDisplayShowTitleEnabled(false)`) because the custom profile switcher replaces it.

### Main Screen

- **Fullscreen WebView** with edge-to-edge display via `enableEdgeToEdge()`
- **Transparent status bar and navigation bar** — no colored backgrounds
- **AppBarLayout** with `fitsSystemWindows="true"` to handle status bar inset
- **Pull-to-refresh**: SwipeRefreshLayout around WebView
- **Progress bar**: LinearProgressIndicator below toolbar (2dp, transparent track)

### Dashboard BottomSheet

- **BottomSheetDialog** (not persistent)
- **Header**: "Switch Account" + "Add New" button
- **RecyclerView** of profiles:
  - Circular avatar with profile color + initials (first 2 chars, uppercase)
  - Profile name
  - "Last used: 2 hours ago" (relative time)
  - Checkmark for active profile
  - Tap to switch and dismiss
  - Long-press to show context menu: Edit, Delete
- **Delete confirmation**: Dialog with "This will remove the saved session."

### Cookie BottomSheet

- Shows current cookie string for the active profile
- Selectable monospace text in a scrollable container
- Copy button (copies to clipboard)
- Close button

### Profile Form Dialog

- **Title**: "Add Profile" or "Edit Profile"
- **Fields**:
  - Name (TextInputLayout with outlined box)
  - Color picker (row of circular color chips, Material palette)
- **Buttons**: Cancel, Save
- Combined dialog — uses `ARG_PROFILE_ID` argument to distinguish add vs edit mode

### Empty State

When no profiles exist:
- Auto-shows AddProfileDialog
- WebView does not load until at least one profile exists

### Loading State

During account switch:
- Fullscreen loading overlay with CircularProgressIndicator
- Text: "Switching profile..."
- Prevents user interaction (`clickable="true"`, `focusable="true"`)

### Edge-to-Edge

- `enableEdgeToEdge()` called before `super.onCreate()`
- Status bar and navigation bar are transparent
- AppBarLayout uses `fitsSystemWindows="true"` to draw behind status bar
- WebView content scrolls below the toolbar via `appbar_scrolling_view_behavior`

### Colors

Use **Material 3 dynamic colors** (system default). The app theme inherits from `Theme.Material3.DayNight.NoActionBar` with transparent system bars and light status bar following the theme.

Each profile gets a static Material color from `Constants.MATERIAL_COLORS`.

### Splash Screen

- Pre-API 31: `Theme.Refraction.Starting` with `splash_background.xml` (solid color)
- API 31+: `windowSplashScreenBackground` + transparent `ic_empty` icon, 0ms animation duration
- Theme switches to `Theme.Refraction` after `super.onCreate()` via `setTheme()`

---

## Security Requirements

1. **Encrypt all cookies at rest** — no plaintext session tokens on disk
2. **Use Android Keystore** for the encryption master key
3. **Do not log cookies** to Logcat, crash reports, or analytics
4. **ProGuard rules**: Keep Room entities, keep EncryptedSharedPreferences classes
5. **No network permissions beyond INTERNET** (no analytics, no telemetry)
6. **android:allowBackup="false"** — prevent cloud backup of encrypted cookies
7. **android:usesCleartextTraffic="false"** — enforce HTTPS

---

## Lifecycle Rules

- **Application class** (`RefractionApp.kt`): Initialize Room, set default night mode (`MODE_NIGHT_FOLLOW_SYSTEM`).
- **MainActivity**: Single activity architecture. No fragments for main flow (BottomSheets are dialogs, not fragment transactions).
- **ViewModel**: `MainViewModel` (AndroidViewModel) survives config changes. Holds active profile state, profiles list, loading state, and `switchEvent` SharedFlow.
- **WebView**: Destroy in `onDestroy()`. Pause in `onPause()`, resume in `onResume()`.

---

## CI/CD

### GitHub Actions

Two workflow files in `.github/workflows/`:

- `build-debug.yml` — Runs on push to main and on pull requests. Builds debug APK using `ubuntu-latest` runner with `--max-workers=2`.
- `build-release.yml` — Runs on tag push (`v*`). Builds release APK with R8, creates GitHub Release, uploads APK as asset.

Both use `actions/setup-java@v4` with Temurin JDK 17 and Gradle cache.

**Note**: The GitHub token used for pushing must have `workflow` scope to push `.github/workflows/` files.

### Semaphore CI

Alternative CI in `.semaphore/`:

- `semaphore.yml` — Debug build on every push using `mingc/android-build-box` Docker image
- `release.yml` — Release build promotion pipeline

### Docker Build

Local Docker build setup for CI servers:

| Resource | Limit | Reservation |
|----------|-------|-------------|
| CPU | 2 cores | 1 core |
| Memory | 8 GB | 4 GB |
| Gradle heap | 6 GB | — |

Scripts: `build-release.sh`, `build-debug.sh` (use `sudo` for Docker).

---

## What NOT To Do

1. **Do not use multiple WebViews** — memory death with 10+ profiles
2. **Do not use multiple processes** — unnecessary complexity and memory bloat
3. **Do not use WebView.setDataDirectorySuffix()** — can only be called once per process, incompatible with runtime switching
4. **Do not store cookies in Room** — Room is for metadata only. Use EncryptedSharedPreferences for cookie bundles
5. **Do not add a browser address bar** — this is not a general browser. Lock to target domain or allow limited navigation
6. **Do not use Jetpack Compose** — APK size constraint
7. **Do not add analytics, crash reporting, or ads libraries** — bloat and privacy risk
8. **Do not use Java** — 100% Kotlin
9. **Do not request unnecessary permissions** — only `INTERNET` and `ACCESS_NETWORK_STATE`
10. **Do not call CookieManager from a background thread** — always use `Dispatchers.Main`
11. **Do not use kapt** — project uses KSP for Room
12. **Do not put `package=` in AndroidManifest** — namespace is set in build.gradle.kts

---

## Build Checklist for Agents

Before declaring the project complete, verify:

- [x] `minifyEnabled true` and `shrinkResources true` in release build type
- [x] R8/ProGuard rules include Room and Security Crypto keep rules
- [x] Application class initializes Room on startup
- [x] Cookie capture happens on app pause and before profile switch
- [x] Cookie restoration waits for `removeAllCookies` callback (via `suspendCancellableCoroutine`)
- [x] WebView state is fully cleared before injecting new cookies
- [x] Dashboard BottomSheet handles empty state, normal state, and overflow (10+ items)
- [x] Delete profile requires confirmation dialog
- [x] Active profile indicator is visible in toolbar and dashboard
- [x] No hardcoded strings (all in `strings.xml`)
- [x] Dark theme works correctly with WebView (algorithmic darkening enabled)
- [x] Edge-to-edge display implemented with `enableEdgeToEdge()`
- [ ] APK size checked: must be under 5MB
- [ ] Test profile switching between 3+ profiles without crash or cookie leak

---

## Notes for Isla

This project replaces dual space for the user. The user has already experienced cookie leaking between cloned app instances. Refraction must guarantee that:

- Profile A's cookies never leak to Profile B
- Switching profiles is deterministic and reliable
- The app remains lightweight and fast
- The user trusts their sessions are safe (encryption)

Keep code minimal. Good code explains itself. No comment bloat. No unnecessary abstractions. Build exactly what is specified — no more, no less.
