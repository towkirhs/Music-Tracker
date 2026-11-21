// Plays songs through YouTube's embedded player: queue, shuffle, repeat, endless radio, synced lyrics.
import { songKey } from './stats.js';
import { similarTo, shuffle as shuffled } from './lastfm.js';

let ytReady;
function loadYouTube() {
  ytReady ??= new Promise((resolve) => {
    window.onYouTubeIframeAPIReady = () => resolve();
    const s = document.createElement('script');
    s.src = 'https://www.youtube.com/iframe_api';
    document.head.append(s);
  });
  return ytReady;
}

export class Player {
  queue = [];
  index = -1;
  playing = false;
  loading = false;
  shuffle = false;
  repeat = 'off'; // off, all, one
  volume = Number(localStorage.getItem('mt-volume') || 80);
  radio = null; // { name, key, firstKey }
  error = '';

  constructor(source, onChange, toast) {
    this.source = source;
    this.onChange = onChange;
    this.toast = toast;
    this.videoCache = new Map();
    // How long the current song has really played, sent to the phone so it joins your library.
    this.listen = null; // { song, startedAt, playedMs, playId, lastTick, sentMs }
    setInterval(() => this.countListening(), 1000);
  }

  /** Adds up playing time each second and tells the phone every 5 seconds (from 10 seconds on). */
  countListening() {
    if (!this.source.recordPlay) return; // only when the phone serves this page
    const song = this.current;
    if (!song) return;
    const now = Date.now();
    if (!this.listen || this.listen.song !== song) {
      this.listen = { song, startedAt: now, playedMs: 0, playId: 0, lastTick: now, sentMs: 0 };
    }
    const l = this.listen;
    if (this.playing) l.playedMs += Math.min(now - l.lastTick, 2000);
    l.lastTick = now;
    if (l.playedMs < 10_000 || l.playedMs - l.sentMs < 5_000 || l.sending) return;
    l.sending = true;
    l.sentMs = l.playedMs;
    this.source.recordPlay({ title: song.title, artist: song.artist || '', startedAt: l.startedAt, playedMs: l.playedMs, playId: l.playId })
      .then((id) => { if (id) l.playId = id; })
      .catch(() => {})
      .finally(() => { l.sending = false; });
  }

  get current() { return this.queue[this.index] || null; }
  position() { return this.yt?.getCurrentTime ? this.yt.getCurrentTime() * 1000 : 0; }
  duration() {
    const d = this.yt?.getDuration ? this.yt.getDuration() * 1000 : 0;
    return d || this.current?.durationMs || 0;
  }

  changed() { this.onChange(); }

  async ensureYt() {
    if (this.yt) return;
    await loadYouTube();
    await new Promise((resolve) => {
      this.yt = new YT.Player('yt', {
        width: 200, height: 200,
        playerVars: { autoplay: 1, controls: 0, playsinline: 1 },
        events: {
          onReady: () => { this.yt.setVolume(this.volume); resolve(); },
          onStateChange: (e) => this.onState(e.data),
          onError: () => this.onYtError(),
        },
      });
    });
  }

  onState(s) {
    if (s === YT.PlayerState.PLAYING) { this.playing = true; this.loading = false; }
    if (s === YT.PlayerState.PAUSED) this.playing = false;
    if (s === YT.PlayerState.BUFFERING) this.loading = true;
    if (s === YT.PlayerState.ENDED) {
      this.playing = false;
      if (this.repeat === 'one') {
        this.yt.seekTo(0);
        this.yt.playVideo();
      } else {
        this.next(true);
      }
    }
    this.changed();
  }

  onYtError() {
    // Some videos can't be played on other websites; skip them.
    this.toast(`Skipped "${this.current?.title}": YouTube doesn't allow it here`);
    this.next(true);
  }

  /** Plays [songs] from [start]. A list started by hand ends any radio. */
  async playList(songs, start = 0, { shuffle = false, fromRadio = false } = {}) {
    if (!songs.length) return;
    if (!fromRadio) this.radio = null;
    this.queue = shuffle ? shuffled(songs) : [...songs];
    this.index = shuffle ? 0 : Math.min(start, songs.length - 1);
    this.shuffle = shuffle;
    await this.load();
  }

  async load() {
    const song = this.current;
    if (!song) return;
    this.loading = true;
    this.error = '';
    this.changed();
    try {
      await this.ensureYt();
      if (!song.videoId) {
        const k = songKey(song.title, song.artist);
        song.videoId = this.videoCache.get(k) || (await this.source.videoFor(song.title, song.artist));
        if (!song.videoId) throw new Error(`Couldn't find "${song.title}" on YouTube`);
        this.videoCache.set(k, song.videoId);
      }
      if (this.current !== song) return; // skipped while looking it up
      this.yt.loadVideoById(song.videoId);
    } catch (e) {
      this.loading = false;
      this.error = e.message;
      this.changed();
      return;
    }
    this.fillRadio();
  }

  toggle() {
    if (!this.yt || !this.current) return;
    this.playing ? this.yt.pauseVideo() : this.yt.playVideo();
  }

  next(auto = false) {
    if (this.index < this.queue.length - 1) this.index++;
    else if (this.repeat === 'all' || !auto) this.index = 0;
    else { this.changed(); return; }
    this.load();
  }

  previous() {
    if (this.position() > 3000) return this.seek(0);
    this.index = Math.max(0, this.index - 1);
    this.load();
  }

  seek(ms) { this.yt?.seekTo(ms / 1000, true); }
  skipTo(i) { this.index = i; this.load(); }

  removeAt(i) {
    if (i === this.index) return;
    this.queue.splice(i, 1);
    if (i < this.index) this.index--;
    this.changed();
  }

  playNext(song) {
    if (!this.current) return this.playList([song]);
    this.queue.splice(this.index + 1, 0, song);
    this.changed();
  }

  add(song) {
    if (!this.current) return this.playList([song]);
    this.queue.push(song);
    this.changed();
  }

  toggleShuffle() {
    this.shuffle = !this.shuffle;
    if (this.shuffle) this.queue = [...this.queue.slice(0, this.index + 1), ...shuffled(this.queue.slice(this.index + 1))];
    this.changed();
  }

  cycleRepeat() {
    this.repeat = { off: 'all', all: 'one', one: 'off' }[this.repeat];
    this.changed();
  }

  setVolume(v) {
    this.volume = v;
    localStorage.setItem('mt-volume', v);
    this.yt?.setVolume(v);
  }

  /** Endless radio from Last.fm's similar songs; tops the queue up when 3 songs are left. */
  async startRadio(name, key, seeds, { playSeedsFirst = false } = {}) {
    if (!key) return this.toast('Add a Last.fm API key in Settings to use radio');
    if (!seeds.length) return this.toast('Nothing to start the radio from yet');
    this.toast(`Starting ${name}…`);
    try {
      const similar = await similarTo(key, seeds.slice(0, 5));
      const first = (playSeedsFirst ? [...shuffled(seeds), ...similar] : similar).slice(0, 30);
      if (!first.length) return this.toast('Last.fm has no similar songs for this one yet');
      this.queued = new Set(first.map((t) => songKey(t.title, t.artist)));
      await this.playList(first.map((t) => ({ title: t.title, artist: t.artist })), 0, { fromRadio: true });
      this.radio = { name, key };
      this.changed();
    } catch (e) {
      this.toast(e.message || 'Radio needs the internet');
    }
  }

  async fillRadio() {
    const radio = this.radio;
    if (!radio || this.filling || this.index < this.queue.length - 3) return;
    this.filling = true;
    try {
      const recent = this.queue.slice(-3).map((s) => ({ title: s.title, artist: s.artist }));
      const more = (await similarTo(radio.key, recent).catch(() => []))
        .filter((t) => !this.queued.has(songKey(t.title, t.artist))).slice(0, 15);
      if (this.radio !== radio) return;
      more.forEach((t) => { this.queued.add(songKey(t.title, t.artist)); this.queue.push({ title: t.title, artist: t.artist }); });
      this.changed();
    } finally {
      this.filling = false;
    }
  }
}

/** Timed lyrics from LRCLIB (free, no key): [{timeMs, text}], with synced=false for plain lyrics. */
export async function findLyrics(title, artist) {
  const url = `https://lrclib.net/api/search?track_name=${encodeURIComponent(title)}&artist_name=${encodeURIComponent(artist)}`;
  let hits = await (await fetch(url)).json().catch(() => []);
  if (!hits.length) hits = await (await fetch(`https://lrclib.net/api/search?q=${encodeURIComponent(title + ' ' + artist)}`)).json().catch(() => []);
  const synced = hits.find((h) => h.syncedLyrics);
  if (synced) return { synced: true, lines: parseLrc(synced.syncedLyrics) };
  const plain = hits.find((h) => h.plainLyrics);
  return { synced: false, lines: plain ? plain.plainLyrics.split('\n').map((text) => ({ timeMs: -1, text })) : [] };
}

export function parseLrc(text) {
  const lines = [];
  for (const raw of text.split('\n')) {
    const m = raw.match(/^\[(\d+):(\d+(?:\.\d+)?)\](.*)$/);
    if (m) lines.push({ timeMs: Math.round((Number(m[1]) * 60 + Number(m[2])) * 1000), text: m[3].trim() });
  }
  return lines;
}
