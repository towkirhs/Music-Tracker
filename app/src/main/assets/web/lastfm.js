// Last.fm in the browser: similar songs and artists, charts by tag, your top songs. Same rules as the app.
import { songKey, songs as songStats, groups, inRange, artistOf } from './stats.js';

const API = 'https://ws.audioscrobbler.com/2.0/';

async function call(key, method, params) {
  const url = new URL(API);
  Object.entries({ method, api_key: key, format: 'json', autocorrect: '1', ...params }).forEach(([k, v]) => url.searchParams.set(k, v));
  const r = await fetch(url);
  const json = await r.json();
  if (json.error === 10 || json.error === 26) throw new Error("Last.fm didn't accept the API key");
  return json.error ? null : json;
}

// Last.fm gives one item as an object instead of a list.
const list = (x) => (Array.isArray(x) ? x : x ? [x] : []);
const tracks = (json, outer) => list(json?.[outer]?.track)
  .map((t) => ({ title: (t.name || '').trim(), artist: (t.artist?.name || t.artist?.['#text'] || '').trim() }))
  .filter((t) => t.title);
const names = (json, outer, inner) => list(json?.[outer]?.[inner]).map((a) => (a.name || '').trim()).filter(Boolean);

export const similarTracks = async (key, t, limit = 20) =>
  tracks(await call(key, 'track.getsimilar', { track: t.title, artist: t.artist, limit }), 'similartracks');
export const similarArtists = async (key, artist, limit = 10) =>
  names(await call(key, 'artist.getsimilar', { artist, limit }), 'similarartists', 'artist');
export const artistTopTracks = async (key, artist, limit = 10) =>
  tracks(await call(key, 'artist.gettoptracks', { artist, limit }), 'toptracks');
export const tagTopTracks = async (key, tag, limit = 30) =>
  tracks(await call(key, 'tag.gettoptracks', { tag, limit }), 'tracks');
export const userTopTracks = async (key, user, limit = 20) =>
  tracks(await call(key, 'user.gettoptracks', { user, period: '1month', limit }), 'toptracks');
export const userTopArtists = async (key, user, limit = 10) =>
  names(await call(key, 'user.gettopartists', { user, period: '3month', limit }), 'topartists', 'artist');

const shuffle = (a) => {
  const b = [...a];
  for (let i = b.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [b[i], b[j]] = [b[j], b[i]];
  }
  return b;
};

/** Songs like [seeds], one from each in turn; falls back to similar artists' hits, then the artist's own. */
export async function similarTo(key, seeds) {
  const lists = await Promise.all(seeds.map(async (seed) => {
    let found = await similarTracks(key, seed, 20).catch(() => []);
    if (!found.length && seed.artist) {
      const artists = await similarArtists(key, seed.artist, 4).catch(() => []);
      found = (await Promise.all(artists.map((a) => artistTopTracks(key, a, 4).catch(() => [])))).flat();
      if (!found.length) found = await artistTopTracks(key, seed.artist, 10).catch(() => []);
    }
    return shuffle(found);
  }));
  const mixed = [];
  const longest = Math.max(0, ...lists.map((l) => l.length));
  for (let i = 0; i < longest; i++) for (const l of lists) if (l[i]) mixed.push(l[i]);
  const seen = new Set();
  return mixed.filter((t) => !seen.has(songKey(t.title, t.artist)) && seen.add(songKey(t.title, t.artist)));
}

/** Discover sections: songs like your top song, artists for you, Bangla and Hindi charts. */
export async function discover(key, user, rows) {
  const month = songStats(rows, 'month').sort((a, b) => b.plays - a.plays);
  const myArtists = groups(inRange(rows, 'month'), artistOf).sort((a, b) => b.plays - a.plays)
    .map((g) => g.name).filter((n) => n !== 'Unknown artist');
  const lastFmTop = user ? await userTopTracks(key, user, 5).catch(() => []) : [];
  const seeds = [...month.slice(0, 3).map((s) => ({ title: s.title, artist: s.artist })), ...lastFmTop];
  let basedOn = null;
  let similar = [];
  for (const seed of seeds) {
    similar = await similarTracks(key, seed, 15);
    if (similar.length) { basedOn = seed; break; }
  }
  const lastFmArtists = user ? await userTopArtists(key, user, 5).catch(() => []) : [];
  const known = new Set([...myArtists, ...lastFmArtists].map((a) => a.toLowerCase()));
  const sources = [...new Set([...myArtists.slice(0, 3), ...lastFmArtists.slice(0, 2)])];
  const artists = [...new Map((await Promise.all(sources.map((a) => similarArtists(key, a, 8).catch(() => []))))
    .flat().map((a) => [a.toLowerCase(), a])).values()].filter((a) => !known.has(a.toLowerCase())).slice(0, 12);
  const [bangla, hindi] = await Promise.all([tagTopTracks(key, 'bangla', 10), tagTopTracks(key, 'hindi', 10)]);
  return { basedOn, similar, artists, bangla, hindi };
}

export { shuffle };
