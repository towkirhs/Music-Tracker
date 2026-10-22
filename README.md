# Music Tracker

A personal Android app that logs the songs you hear in Instagram Reels, YouTube Shorts, TikTok and music apps, and turns them into a library and listening stats. Free, offline, no account, no paid APIs.

## Get the app

Every push to `main` builds a new APK. Download the latest one from **Releases** on this repo page and install it on your phone. New versions install over the old one and keep your library.

## First-time setup (Nothing Phone (2a))

1. Install the APK and open **Music Tracker → Settings**.
2. **Allow restricted settings first:** tap *Open App info*, then the ⋮ menu (top right) → *Allow restricted settings*. Android requires this for apps installed from a file.
3. Turn on **Song reader** (Accessibility → Music Tracker song reader).
4. Turn on **Music app reader** (Notification access → Music Tracker now playing).
5. Turn on **No battery limits**, and in App info → Battery choose **Unrestricted**.
6. Optional: add the **Music Tracker** tile to Quick Settings.
7. Go to **Home** and switch it to **Listening**. It keeps running until you switch it off.

## How it works

- **Song reader:** reads the song label that Reels, Shorts and TikTok show on screen ("♪ Artist · Song"). Works in Bangla, Hindi and English because it uses the app's own label.
- **Music app reader:** reads now-playing info from Spotify, YouTube Music and other players. Podcast and audiobook apps, and anything longer than 15 minutes, are ignored.
- **Play rule:** a song counts after 10 seconds. Coming back to the same song within a minute continues the same play.
- "Original audio" clips have no song name and are not logged yet; the on-phone fingerprint index for those is the next phase.

## If songs are missed

Each app shows the song label differently and changes it with updates. Turn on **Settings → Label lab**, scroll some Reels for a few minutes, then **Save CSV** and share it: the reading rules in `LabelParser.kt` can then be fixed for your phone.

## Exports

Settings → Export and backup: song list and full history as CSV (opens in Excel), an HTML report (print to PDF from Chrome), and a backup file you can restore later. Everything stays on the phone.

## Build

Built by GitHub Actions (`.github/workflows/build-apk.yml`): Kotlin, Jetpack Compose, Room. No Android Studio needed.
