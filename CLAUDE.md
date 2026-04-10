# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A local network photo-sharing app. The server is a FastAPI Python backend; the client is an Android app written in Kotlin with Jetpack Compose. Devices identify themselves via `X-Device-Id` and `X-Device-Name` headers on every request — there is no auth system.

## Server (FastAPI + SQLite)

**Run locally:**
```bash
cd server
pip install -r requirements.txt
uvicorn main:app --reload
```

**Run via Docker:**
```bash
cd server
docker compose up --build
```

Server runs on port 8000. SQLite DB is `server/photo_share.db`. Uploaded files go to `server/uploads/`.

**Key behaviors:**
- Devices are auto-registered on every request that includes both headers (middleware in `main.py`).
- Upload deduplication: if `file_hash` (SHA-256) already exists, the existing photo is returned (200, not 201).
- Ephemeral photos: once a device has viewed a photo (`PhotoView` row exists), the `/photos` listing filters it out for that device. Viewing happens via `GET /photos/{id}/file` (implicit) or `POST /photos/{id}/view` (explicit).
- Only the uploader can delete a photo.

## Android (Kotlin + Jetpack Compose)

**Build:**
```bash
cd android
./gradlew assembleDebug
```

**Install on connected device:**
```bash
cd android
./gradlew installDebug
```

**Run tests:**
```bash
cd android
./gradlew test                    # unit tests
./gradlew connectedAndroidTest    # instrumented tests
```

**Architecture:**
- `ApiClient` is a singleton that must be initialized with server URL + device credentials before use. Initialization happens in `MainActivity` after preferences load.
- Navigation is handled entirely in `MainActivity` via Compose Navigation with three routes: `setup`, `gallery`, `upload`.
- `PreferencesManager` wraps DataStore — emits `AppPrefs?` (non-null only when all three prefs are set). First launch shows `SetupScreen`; configured devices go straight to `GalleryScreen`.
- ViewModels (`GalleryViewModel`, `UploadViewModel`) use `StateFlow` and `viewModelScope`.
- Coil is used for image loading and shares the same `OkHttpClient` as Retrofit so device headers are injected automatically into image requests.
- The app registers as a share target (`ACTION_SEND`/`ACTION_SEND_MULTIPLE`) and passes the image URI to `UploadScreen`.

**Key data flow for upload:**
1. `PhotoRepository.uploadPhoto()` reads bytes from the URI, computes SHA-256, constructs multipart form data, and posts to `/photos`.
2. The server verifies the hash, deduplicates, saves the file, and returns the photo record.
