// Where the library comes from: the phone (live, on home Wi-Fi) or a backup file kept in this browser.
import { rowsFromBackup } from './stats.js';

export class NeedCode extends Error {}

/** The phone when it is serving this page; otherwise this browser's copy of a backup file. */
export async function connect() {
  try {
    const r = await fetch('/api/ping', { cache: 'no-store' });
    if (r.ok && (await r.json()).app === 'Music Tracker') return new PhoneSource();
  } catch { /* not served by the phone */ }
  return new FileSource();
}

// Free YouTube search for the website away from home (no key). Public servers, so several are tried.
const PIPED = ['https://pipedapi.kavin.rocks', 'https://pipedapi.adminforge.de', 'https://api.piped.private.coffee'];

async function pipedSearch(q) {
  for (const base of PIPED) {
    try {
      const r = await fetch(`${base}/search?q=${encodeURIComponent(q)}&filter=music_songs`, { signal: AbortSignal.timeout(8000) });
      if (!r.ok) continue;
      const items = ((await r.json()).items || []).filter((i) => i.url && i.url.includes('v='));
      if (!items.length) continue;
      return items.map((i) => ({
        videoId: new URL(i.url, 'https://youtube.com').searchParams.get('v'),
        title: i.title, artist: (i.uploaderName || '').replace(/ - Topic$/, ''),
        thumbnail: i.thumbnail || '', durationMs: (i.duration || 0) * 1000,
      }));
    } catch { /* try the next server */ }
  }
  throw new Error('Search is unavailable right now. Try again later, or use the website from your phone at home.');
}

class PhoneSource {
  mode = 'phone';
  canEdit = true;

  get code() { return localStorage.getItem('mt-code') || ''; }
  setCode(code) { localStorage.setItem('mt-code', code.trim()); }
  forgetCode() { localStorage.removeItem('mt-code'); }

  async call(path, body) {
    const r = await fetch('/api' + path, {
      method: body ? 'POST' : 'GET',
      headers: { 'X-Code': this.code, 'Content-Type': 'application/json' },
      body: body ? JSON.stringify(body) : undefined,
      cache: 'no-store',
    });
    if (r.status === 401) throw new NeedCode('Enter the code shown in the app');
    const json = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(json.error || `The phone answered ${r.status}`);
    return json;
  }

  library() { return this.call('/library'); }
  async version() { return (await this.call('/version')).version; }
  /** Adds (or, with playId, updates) a play of a song heard on this website. Returns its playId, 0 if not counted. */
  async recordPlay(play) { return (await this.call('/play/record', play)).playId || 0; }
  editTrack(id, title, artist, language) { return this.call('/track/edit', { id, title, artist, language }); }
  favourite(id, favourite) { return this.call('/track/favourite', { id, favourite }); }
  deleteTrack(id) { return this.call('/track/delete', { id }); }
  deletePlay(playId, trackId) { return this.call('/play/delete', { playId, trackId }); }
  async search(q) { return (await this.call('/search?q=' + encodeURIComponent(q))).songs; }
  async videoFor(title, artist) {
    return (await this.call('/video?q=' + encodeURIComponent([title, artist].filter(Boolean).join(' ')))).videoId || null;
  }
  async ytPlaylists() { return (await this.call('/ytm/playlists')).playlists; }
  async ytPlaylist(id) { return (await this.call('/ytm/playlist?id=' + encodeURIComponent(id))).songs; }
}

// A tiny IndexedDB store: backups can be bigger than localStorage allows.
function idb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open('music-tracker', 1);
    req.onupgradeneeded = () => req.result.createObjectStore('files');
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}
async function idbGet(key) {
  const db = await idb();
  return new Promise((resolve, reject) => {
    const req = db.transaction('files').objectStore('files').get(key);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}
async function idbPut(key, value) {
  const db = await idb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('files', 'readwrite');
    tx.objectStore('files').put(value, key);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

class FileSource {
  mode = 'file';
  canEdit = false;

  async library() {
    const saved = await idbGet('backup');
    return {
      plays: saved ? rowsFromBackup(JSON.parse(saved.text)) : [],
      backupName: saved?.name || '',
      backupDate: saved?.at || 0,
      lastFmKey: localStorage.getItem('mt-lastfm-key') || '',
      lastFmUser: localStorage.getItem('mt-lastfm-user') || '',
      ytSignedIn: false,
    };
  }

  /** Checks and keeps a backup file made by the app. */
  async openBackup(file) {
    const text = await file.text();
    rowsFromBackup(JSON.parse(text)); // throws if it isn't one
    await idbPut('backup', { text, name: file.name, at: Date.now() });
  }

  saveLastFm(key, user) {
    localStorage.setItem('mt-lastfm-key', key.trim());
    localStorage.setItem('mt-lastfm-user', user.trim());
  }

  search(q) { return pipedSearch(q); }
  async videoFor(title, artist) {
    return (await pipedSearch([title, artist].filter(Boolean).join(' ')))[0]?.videoId || null;
  }
}
