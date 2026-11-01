# JooCam: Milestone 1 (live view with video and audio)

Status: source written, NOT yet compiled or tested. The first cloud build may need small fixes.

## What Milestone 1 contains
- Live RTSP playback (LibVLC): H.264 video, G.711 A-law audio
- Settings: IP, RTSP port, ONVIF port, username, password, main/sub URI, auth method
- Password stored with EncryptedSharedPreferences (Android Keystore); passed to VLC as options, never put in URLs or logs
- ONVIF GetProfiles + GetStreamUri to fetch the substream (WS-Security digest, clock-offset corrected)
- Mute, snapshot (saved to Pictures/JooCam), Main/Sub selection, full screen + landscape
- Network status label, automatic reconnect with backoff, 15 s connect watchdog
- Playback stops when the app goes to the background and resumes on return

## Build in the cloud (no PC)
Both CI files build with plain `gradle assembleDebug` (no wrapper needed).
- Codemagic: `codemagic.yaml` (connect the repo, start the "JooCam debug APK" workflow, download the APK artifact)
- GitHub Actions: `.github/workflows/build.yml` (artifact "joocam-debug-apk")

## Install
Download the APK on the phone, open it, allow "Install unknown apps" for your browser/files app, install.
It is debug-signed, which is fine for personal use.

## Test checklist (Milestone 1)
1. Phone on the same Wi-Fi as the camera (192.168.0.x).
2. Settings: enter the password, Save.
3. Live tab, tap play: video appears within ~5 s. Confirm you hear audio.
4. Mute/unmute works.
5. Snapshot: toast appears, image in Pictures/JooCam.
6. Settings, "Fetch streams via ONVIF": log lists profile_1 (640x360) and the sub URI. Live tab, switch Main to Sub, video plays.
7. Full screen: landscape, bars hidden; Back exits.
8. Turn camera Wi-Fi off/on or toggle phone Wi-Fi: status goes to Reconnecting, then Live again.
9. Wrong password: should end in repeated Reconnecting, never a crash.

## Known limitations
- Untested on real hardware; snapshot depends on LibVLC rendering to a TextureView.
- Cleartext HTTP is allowed app-wide (the camera speaks plain HTTP/RTSP on the LAN).
- Main/sub streams only work on the camera's LAN, not over mobile data.
- No PTZ, AI, recording or Drive yet (Milestones 2 to 5).
