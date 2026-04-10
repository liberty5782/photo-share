# PhotoShare

A local network photo-sharing app. Run the server on any machine on your network, install the Android app on your devices, and share photos without any cloud accounts or external services.

## Features

- Share photos from any Android app via the system share sheet
- **Ephemeral photos** — mark a photo as ephemeral and it disappears from each viewer's feed after they open it (Snapchat-style)
- Automatic upload deduplication via SHA-256 hashing
- No accounts or authentication — devices identify by a self-assigned name and UUID

## Server

Requires Python 3.12+ or Docker.

**With Docker (recommended):**
```bash
cd server
docker compose up --build
```

**Without Docker:**
```bash
cd server
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

The server listens on port 8000. The SQLite database (`photo_share.db`) and uploaded files (`uploads/`) are created in the `server/` directory. When using Docker, both are mounted as volumes so data persists across container restarts.

## Android App

**Requirements:** Android Studio with AGP 8.5 / Kotlin 2.0 toolchain, or `./gradlew` with a JDK.

```bash
cd android
./gradlew assembleDebug       # build APK
./gradlew installDebug        # build and install on connected device
```

On first launch the app shows a setup screen — enter a display name for the device and the server URL (e.g. `http://192.168.1.100:8000`). The app hits `/health` to verify the server is reachable before saving.

## API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Health check |
| `GET` | `/photos` | List photos (ephemeral ones already viewed by the caller are excluded) |
| `POST` | `/photos` | Upload a photo (multipart: `file`, `file_hash`, `caption?`, `is_ephemeral`) |
| `GET` | `/photos/{id}/file` | Download photo file; marks it viewed |
| `POST` | `/photos/{id}/view` | Mark a photo viewed without downloading |
| `DELETE` | `/photos/{id}` | Delete a photo (uploader only) |
| `GET` | `/devices` | List registered devices |

All requests must include `X-Device-Id` and `X-Device-Name` headers. Devices are automatically registered on first request.
