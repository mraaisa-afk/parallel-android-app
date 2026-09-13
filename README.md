# Parallel

Parallel is a privacy-first Android local management hub. The phone exposes a browser console over the local network so a desktop on the same network can interact with the device without an account or cloud backend.

## Foundation in this commit

- Native Android application with Jetpack Compose / Material 3 UI.
- Local Ktor CIO server bound to the LAN interface.
- Browser dashboard served directly from the phone.
- Health API at `/api/v1/health`.
- WebSocket endpoint at `/ws` for future live device events.
- Explicit separation between the native app and browser hub.
- No account, analytics, cloud service, or remote backend.

## Planned capability layers

Files, photos, video, audio, contacts, SMS, call logs, notifications, installed apps, device information, file transfer, media streaming, screen mirroring, conditional remote control, notes, RSS, media playback, TV casting, peer-to-peer chat/file sharing, Pomodoro, and sound meter will be implemented behind Android permission and platform capability gates.

Sensitive Android capabilities such as SMS/call logs, notification access, microphone, and screen capture require explicit user consent and will never be silently enabled.

## Build

Open the repository in Android Studio using JDK 21 and sync the Gradle project. The Gradle wrapper will be added with the build automation layer in the next foundation step.
