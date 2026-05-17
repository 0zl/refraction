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
- Optional: profile icon initials (auto-generated from name)

### Data Flow

```
User opens app
    -> Load last active Profile (or default)
    -> Decrypt cookie bundle
    -> Inject into CookieManager
    -> Load WebView with target URL

User switches Profile
    -> Capture current cookies from CookieManager
    -> Encrypt and save to current Profile
    -> Clear WebView state (cookies, storage, cache)
    -> Decrypt target Profile cookies
    -> Inject into CookieManager
    -> Reload WebView
```

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

// Material Design 3
implementation("com.google.android.material:material:1.12.0")

// Room (metadata only)
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// Encrypted SharedPreferences
implementation("androidx.security:security-crypto:1.1.0-alpha06")

// Coroutines (async cookie ops)
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

// Lifecycle (ViewModel, etc.)
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

// SwipeRefreshLayout (for WebView pull-to-refresh)
implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
```

### Build Configuration

- `minifyEnabled true` (R8)
- `shrinkResources true`
- Kotlin DSL for build scripts (`build.gradle.kts`)
- No data binding, no Compose (keep APK small)
- View system with XML layouts

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
│       │   │   │   ├── RefractionDatabase.kt       // Room database
│       │   │   │   ├── ProfileDao.kt               // Room DAO
│       │   │   │   ├── ProfileEntity.kt            // Room entity
│       │   │   │   └── CookieStorage.kt            // Encrypted cookie I/O
│       │   │   └── model/
│       │   │       └── Profile.kt                  // Domain model
│       │   ├── domain/
│       │   │   ├── ProfileManager.kt               // Core business logic
│       │   │   └── CookieRepository.kt             // Cookie CRUD + encryption
│       │   ├── ui/
│       │   │   ├── main/
│       │   │   │   ├── MainActivity.kt             // Host activity
│       │   │   │   ├── MainViewModel.kt            // State management
│       │   │   │   └── WebViewClient.kt            // Custom WebViewClient
│       │   │   ├── dashboard/
│       │   │   │   ├── DashboardBottomSheet.kt     // Profile switcher
│       │   │   │   ├── ProfileAdapter.kt           // RecyclerView adapter
│       │   │   │   └── ProfileViewHolder.kt
│       │   │   └── dialog/
│       │   │       ├── AddProfileDialog.kt
│       │   │       └── EditProfileDialog.kt
│       │   └── util/
│       │       ├── Constants.kt
│       │       └── Extensions.kt
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── bottom_sheet_dashboard.xml
│           │   ├── item_profile.xml
│           │   └── dialog_profile_form.xml
│           ├── menu/
│           │   └── main_menu.xml
│           ├── values/
│           │   ├── colors.xml
│           │   ├── strings.xml
│           │   ├── themes.xml
│           │   └── dimens.xml
│           └── drawable/
│               └── ...
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── AGENTS.md (this file)
```

---

## Key Implementation Rules

### 1. CookieManager Handling

`CookieManager.getInstance()` is a **global singleton per process**. All WebViews share it. This is why swapping is necessary.

**Capture cookies** (save current session):
```kotlin
val cookieManager = CookieManager.getInstance()
val cookies = cookieManager.getCookie(url) // Returns "name1=value1; name2=value2"
```

**Restore cookies** (load session):
```kotlin
cookieManager.setCookie(url, "name=value")
cookieManager.flush() // Force sync to disk
```

**Clear cookies** (before switching):
```kotlin
cookieManager.removeAllCookies { 
    // Callback: cookies cleared, safe to inject new ones
}
```

**Important**: `removeAllCookies` and `setCookie` are asynchronous. Use suspend functions or callbacks. Never assume synchronous completion.

### 2. WebView State Clearing

Before restoring a new profile, clear all WebView state to prevent cross-contamination:

```kotlin
webView.clearCache(true)
WebStorage.getInstance().deleteAllData()
cookieManager.removeAllCookies { 
    // Then inject new cookies
}
```

### 3. Encryption

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

### 4. Cookie Capture Triggers

Save cookies at these moments:
- `onPause()` of MainActivity
- After `onPageFinished()` if URL is the target domain
- Immediately before switching profiles
- User explicitly taps "Save Profile" (optional)

### 5. Target Domain

The app is built for single-domain use. The default target is Facebook, but make the domain configurable via `Constants.kt`:

```kotlin
object Constants {
    const val TARGET_DOMAIN = "https://www.facebook.com"
    const val TARGET_URL = "https://www.facebook.com"
}
```

All cookie capture/restore operations should use this domain.

### 6. Profile Limits

No hardcoded limit, but UI should handle 20+ profiles gracefully (scrollable RecyclerView).

---

## UI/UX Specifications

### Main Screen

- **Fullscreen WebView** with edge-to-edge display
- **Toolbar** at top with:
  - Profile name (center or start)
  - Colored indicator dot/avatar
  - Tap to open Dashboard BottomSheet
  - Overflow menu: Add Profile, Settings, About
- **Pull-to-refresh**: SwipeRefreshLayout around WebView
- **Progress bar**: LinearProgressIndicator below toolbar

### Dashboard BottomSheet

- **BottomSheetDialog** (not persistent)
- **Header**: "Switch Account" + "Add New" button
- **RecyclerView** of profiles:
  - Circular avatar with profile color + initials
  - Profile name
  - "Last used: 2 hours ago" (relative time)
  - Checkmark or highlight for active profile
  - Tap to switch and dismiss
  - Long-press to show context menu: Edit, Delete
- **Delete confirmation**: Dialog with "This will remove the saved session."

### Profile Form Dialog

- **Title**: "Add Profile" or "Edit Profile"
- **Fields**:
  - Name (EditText, required)
  - Color picker (row of circular color chips, Material palette)
- **Buttons**: Cancel, Save

### Empty State

When no profiles exist:
- Show centered illustration + "No profiles yet"
- "Add Profile" primary button
- Do not load WebView until at least one profile exists (or create a default)

### Loading State

During account switch:
- Show fullscreen or WebView-overlay loading indicator
- Text: "Switching profile..."
- Prevent user interaction

### Colors

Use **Material You / Material 3 dynamic colors** where available, fallback to a defined palette. Each profile gets a static Material color (e.g., Blue, Red, Green, Purple, Orange, Teal, Pink, Indigo, Cyan, Amber, Lime, Deep Orange).

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

- **Application class** (`RefractionApp.kt`): Initialize Room, set default night mode, set WebView data directory if needed.
- **MainActivity**: Single activity architecture. No fragments for main flow (BottomSheet is a dialog, not a fragment transaction).
- **ViewModel**: `MainViewModel` survives config changes. Holds active profile state.
- **WebView**: Destroy in `onDestroy()`. Pause in `onPause()`, resume in `onResume()`.

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
10. **Do not trust cookie operations to be synchronous** — always use callbacks or suspend functions

---

## Docker Build Environment

This project includes a Docker-based build setup for production servers or CI/CD pipelines. It uses the `mingc/android-build-box` image with strict resource limits.

### Resource Limits

| Resource | Limit | Reservation |
|----------|-------|-------------|
| CPU | 2 cores | 1 core |
| Memory | 8 GB | 4 GB |
| Gradle heap | 6 GB | — |

### Build Scripts

Two convenience scripts are provided in the project root:

- `build-release.sh` — Builds `app-release.apk`
- `build-debug.sh` — Builds `app-debug.apk`

Both scripts use `sudo` for Docker commands and enforce the resource limits above.

### Usage

```bash
cd /path/to/refraction

# Release build
sudo ./build-release.sh

# Debug build
sudo ./build-debug.sh

# Or use docker-compose directly
sudo docker-compose run --rm android-build ./gradlew assembleRelease --no-daemon
```

### Output

APKs are written to the host filesystem at:
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/apk/debug/app-debug.apk`

### Docker Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Container definition with resource limits |
| `build-release.sh` | Wrapper for release builds |
| `build-debug.sh` | Wrapper for debug builds |
| `.dockerignore` | Excludes git, IDE files, and keystores from build context |

### Notes

- Gradle daemon is disabled (`--no-daemon`) to prevent memory leaks in containerized builds.
- Gradle cache and Android SDK are persisted in named Docker volumes to speed up subsequent builds.
- Keystore files (`.jks`, `.keystore`) are explicitly excluded from the Docker build context for security.

---

## Build Checklist for Agents

Before declaring the project complete, verify:

- [ ] `minifyEnabled true` and `shrinkResources true` in release build type
- [ ] R8/ProGuard rules include Room and Security Crypto keep rules
- [ ] Application class initializes encryption before any cookie I/O
- [ ] Cookie capture happens on app pause and before profile switch
- [ ] Cookie restoration waits for `removeAllCookies` callback
- [ ] WebView state is fully cleared before injecting new cookies
- [ ] Dashboard BottomSheet handles empty state, normal state, and overflow (10+ items)
- [ ] Delete profile requires confirmation dialog
- [ ] Active profile indicator is visible in toolbar and dashboard
- [ ] APK size checked: must be under 5MB
- [ ] No hardcoded strings (all in `strings.xml`)
- [ ] Dark theme works correctly with WebView (algorithmic darkening enabled)
- [ ] Edge-to-edge display implemented properly

---

## Notes for Isla

This project replaces dual space for the user. The user has already experienced cookie leaking between cloned app instances. Refraction must guarantee that:

- Profile A's cookies never leak to Profile B
- Switching profiles is deterministic and reliable
- The app remains lightweight and fast
- The user trusts their sessions are safe (encryption)

Keep code minimal. Good code explains itself. No comment bloat. No unnecessary abstractions. Build exactly what is specified — no more, no less.
