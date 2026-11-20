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

## Listen: the built-in player

The **Listen** tab plays music right in the app, with no account and no ads:

- **Search** any song and play it, or add it to the queue.
- **Play your library:** Most played, Shuffle all, Favourites, Recently heard. Every song page has **Play here** and **Play next**, and the Library tab has **Play** and **Shuffle** for whatever list you're looking at.
- **Full player:** seek bar, shuffle, repeat (all / one), **synced lyrics** that follow the song (LRCLIB), **Up next** queue, **speed and pitch**, and a **sleep timer**.
- Keeps playing in the background, with notification, lock-screen and headphone controls.
- Played songs are cached (up to 512 MB), so replays start at once and use no data.
- Songs you play here count in your library and stats (Settings → Player and Recognise can turn this off).

Songs stream from YouTube Music through [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).

## Recognise (like Shazam)

Tap **Recognise** on Home or in Listen. The app listens for up to 12 seconds and names the song: through the **microphone** (music around you) or straight from **this phone's audio** (Reels, videos) when audio is allowed on Home. You get the cover, album and links to YouTube, Spotify and Shazam, a **Play here** button, and a history of everything you've recognised. Recognised songs are added to your library.

The same fingerprinting now also names **"Original audio" reels and covers automatically**: when a reel has no song name, the app first asks Shazam (fast, works without words, no speech model needed) and only then transcribes the lyrics with Whisper. Only a short sound fingerprint is sent, never the audio.

## Updates

Settings → **Updates** → **Check for updates**. If a newer build is on this repo's Releases, tap **Update now**: the app downloads the APK and Android asks you to confirm the install. The first time, Android asks you to allow Music Tracker to install apps. Your library is kept. The app also checks once a day when it opens and shows an **Update available** card on Home (you can turn this off).

## If songs are missed

Each app shows the song label differently and changes it with updates. Turn on **Settings → Label lab**, scroll some Reels for a few minutes, then **Save CSV** and share it: the reading rules in `LabelParser.kt` can then be fixed for your phone.

## YouTube links

Tap a song, then "Open on YouTube", to search for it on YouTube (opens the YouTube app, or the browser if it isn't installed). The links are plain free YouTube searches, with no API key or account, and they are also included in the songs CSV and the HTML report.

## Exports

Settings → Export and backup: song list and full history as CSV (opens in Excel), an HTML report (print to PDF from Chrome), and a backup file you can restore later. Everything stays on the phone.

## Build

Built by GitHub Actions (`.github/workflows/build-apk.yml`): Kotlin, Jetpack Compose, Room, Media3. No Android Studio needed. Every branch is built to check it compiles; only `main` publishes a release.

## Credits and licence

Features in the player and recognition are modelled on [ArchiveTune](https://github.com/rukamori/ArchiveTune). The Shazam signature generator (`recognize/ShazamSignatureGenerator.kt`) and the recognition request are taken from ArchiveTune's `shazamkit` module, © Rukamori, under the GPL-3.0; NewPipe Extractor is also GPL-3.0. Because of this, Music Tracker as a whole is distributed under the GPL-3.0.
