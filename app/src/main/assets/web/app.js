// The Music Tracker website: Home, Listen, Library, Stats, History and Settings, plus the player.
import * as S from './stats.js';
import { connect, NeedCode } from './source.js';
import { Player, findLyrics } from './player.js';
import * as LastFm from './lastfm.js';

const $ = (sel) => document.querySelector(sel);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const icon = (name, cls = '') => `<svg class="icon ${cls}"><use href="#i-${name}"/></svg>`;

// Same tile colours as the app (ArtPalette), picked the same way (Java's String.hashCode).
const PALETTE = [['#FF8216', '#E5375B'], ['#7B5CFF', '#2EC4F1'], ['#16C79A', '#2E7DF1'], ['#F1C40F', '#FF6B35'], ['#E5375B', '#7B2CBF'], ['#2EC4F1', '#16C79A']];
function javaHash(s) {
  let h = 0;
  for (const ch of s) {
    const code = ch.codePointAt(0);
    // Java hashes UTF-16 units; characters outside the basic plane are two of them.
    if (code > 0xffff) {
      h = (Math.imul(31, h) + (0xd800 + ((code - 0x10000) >> 10))) | 0;
      h = (Math.imul(31, h) + (0xdc00 + ((code - 0x10000) & 0x3ff))) | 0;
    } else h = (Math.imul(31, h) + code) | 0;
  }
  return h;
}
const paletteFor = (seed) => PALETTE[((javaHash(seed) % PALETTE.length) + PALETTE.length) % PALETTE.length];

function art(url, size, seed, cls = '') {
  const [a, b] = paletteFor(seed || '');
  return `<div class="art ${cls}" style="width:${size}px;height:${size}px;background:linear-gradient(135deg,${a},${b})">
    ${icon('note', size > 100 ? 'lg' : '')}${url ? `<img src="${esc(url)}" alt="" loading="lazy" onerror="this.remove()">` : ''}</div>`;
}
function avatar(name, size) {
  const [a, b] = paletteFor(name);
  return `<div class="avatar" style="width:${size}px;height:${size}px;background:linear-gradient(135deg,${a},${b});font-size:${size / 2.6}px">${esc((name.trim()[0] || '?').toUpperCase())}</div>`;
}

const LANGUAGES = ['Bangla', 'Hindi', 'English/other'];
const NAV = [
  ['home', 'Home', 'home'], ['listen', 'Listen', 'listen'], ['library', 'Library', 'library'],
  ['stats', 'Stats', 'stats'], ['history', 'History', 'history'], ['settings', 'Settings', 'settings'],
];

const state = {
  source: null,
  lib: { plays: [] },
  loaded: false,
  needCode: false,
  loadError: '',
  view: 'home',
  search: { query: '', loading: false, results: [], error: '' },
  discover: null, // { loading, error, basedOn, similar, artists, bangla, hindi }
  yt: null, // { loading, error, playlists }
  library: { tab: 'songs', query: '', range: 'all', favourites: false, sort: 'plays', filter: null },
  statsRange: 'week',
  historyShown: 300,
  full: { open: false, pane: 'queue' },
  lyrics: null, // { key, synced, lines }
};
let player;

// ---------- Start ----------

async function start() {
  state.source = await connect();
  player = new Player(state.source, renderPlayer, toast);
  renderNav();
  window.addEventListener('hashchange', route);
  $('#search-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const q = $('#search').value.trim();
    if (!q) return;
    location.hash = '#/listen';
    runSearch(q);
  });
  window.addEventListener('focus', () => state.source.mode === 'phone' && reload());
  setInterval(() => state.source.mode === 'phone' && !document.hidden && reload(), 120_000);
  setInterval(tick, 500);
  document.addEventListener('keydown', (e) => {
    if (e.target.matches('input, textarea, select')) return;
    if (e.code === 'Space' && player.current) { e.preventDefault(); player.toggle(); }
    if (e.key === 'Escape') { closeDialog(); closeFull(); }
  });
  await reload();
  route();
}

async function reload() {
  try {
    state.lib = await state.source.library();
    state.loaded = true;
    state.needCode = false;
    state.loadError = '';
  } catch (e) {
    if (e instanceof NeedCode) state.needCode = true;
    else state.loadError = e.message;
  }
  renderMode();
  render();
}

function route() {
  const v = (location.hash.match(/^#\/(\w+)/) || [])[1];
  state.view = NAV.some((n) => n[0] === v) ? v : 'home';
  renderNav();
  render();
  $('#main').scrollTop = 0;
}

function toast(text) {
  const t = $('#toast');
  t.textContent = text;
  t.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { t.hidden = true; }, 3500);
}

// ---------- Frame ----------

function renderNav() {
  $('#nav').innerHTML = `
    <div class="logo"><div class="logo-mark">${icon('note')}</div>Music Tracker</div>
    ${NAV.map(([id, label, ic]) => `<a class="nav-item ${state.view === id ? 'active' : ''}" href="#/${id}">${icon(ic)}<span>${label}</span></a>`).join('')}
    <div class="nav-spacer"></div>
    <div class="nav-note">${state.source.mode === 'phone' ? 'Live from your phone' : 'From a backup file'}</div>`;
}

function renderMode() {
  const lib = state.lib;
  $('#mode').innerHTML = state.source.mode === 'phone'
    ? `<span class="dot"></span>Live from your phone`
    : lib.backupName ? `Backup from ${esc(S.date(lib.backupDate))}` : `<a href="#/settings">Open a backup file</a>`;
}

function render() {
  const view = $('#view');
  if (state.needCode) {
    view.innerHTML = codeScreen();
    return wire(view);
  }
  if (!state.loaded) {
    view.innerHTML = state.loadError ? `<div class="notice error">${esc(state.loadError)}</div>` : '<div class="loader"></div>';
    return;
  }
  view.innerHTML = { home, listen, library, stats, history, settings }[state.view]();
  wire(view);
}

function codeScreen() {
  return `<div class="center-screen"><div class="card">
    <div class="logo" style="justify-content:center"><div class="logo-mark">${icon('note')}</div>Music Tracker</div>
    <p class="muted">Enter the code shown in the app under Settings → Website on your computer.</p>
    <form data-form="code"><input class="field code-input" name="code" inputmode="numeric" maxlength="6" autofocus>
    <button class="btn" style="width:100%;margin-top:16px">Open my library</button></form></div></div>`;
}

// ---------- Small pieces ----------

const sectionTitle = (title, action = '', act = '') =>
  `<div class="section-title"><h2>${esc(title)}</h2>${action ? `<button class="link" data-act="${act}">${esc(action)}</button>` : ''}</div>`;

// Click handlers for the HTML being built; wire() binds them to that HTML and starts a fresh list.
let pending = [];
const on = (fn) => pending.push(fn) - 1;

function songRow({ title, subtitle, artUrl, rank, onOpen, onPlay, extra = '' }) {
  return `<div class="row ${rank ? '' : 'plain'}" data-h="${on(onOpen)}">
    ${rank ? `<span class="rank">${rank}</span>` : ''}
    ${art(artUrl, 56, title)}
    <div class="ellipsis"><div class="t ellipsis">${esc(title)}</div><div class="s ellipsis">${esc(subtitle)}</div></div>
    <div class="actions">${extra}${onPlay ? `<button class="play-circle" data-h="${on(onPlay)}" title="Play">${icon('play')}</button>` : ''}</div>
  </div>`;
}

const toSong = (s) => ({ title: s.title, artist: s.artist, thumbnail: s.coverUrl || s.thumbnail || '', videoId: s.videoId || null, durationMs: s.durationMs || 0 });

function libSongRows(list, { rank = false, detail } = {}) {
  const songs = list.map(toSong);
  return list.map((s, i) => songRow({
    title: (s.isFavourite ? '♥ ' : '') + s.title,
    subtitle: `${s.artist || 'Unknown artist'}  |  ${detail ? detail(s) : `${s.plays} plays · ${S.duration(s.ms)}`}`,
    artUrl: s.coverUrl, rank: rank ? i + 1 : 0,
    onOpen: () => openSong(s.trackId),
    onPlay: () => player.playList(songs, i),
  })).join('');
}

function suggestionRows(tracks) {
  const songs = tracks.map((t) => ({ title: t.title, artist: t.artist }));
  return songs.map((s, i) => songRow({
    title: s.title, subtitle: s.artist || 'Unknown artist',
    onOpen: () => player.playList(songs, i),
    extra: `<button class="icon-btn" title="Add to queue" data-h="${on(() => { player.add(s); toast('Added to queue'); })}">${icon('add-queue')}</button>`,
  })).join('');
}

function wire(root) {
  const fns = pending;
  pending = [];
  root.querySelectorAll('[data-h]').forEach((el) => el.addEventListener('click', (e) => {
    e.stopPropagation();
    fns[Number(el.dataset.h)]?.();
  }));
  root.querySelectorAll('[data-act]').forEach((el) => el.addEventListener('click', (e) => {
    e.stopPropagation();
    actions[el.dataset.act]?.(el.dataset);
  }));
  root.querySelectorAll('form[data-form]').forEach((f) => f.addEventListener('submit', (e) => {
    e.preventDefault();
    forms[f.dataset.form]?.(new FormData(f), f);
  }));
  root.querySelectorAll('[data-input]').forEach((el) => el.addEventListener('input', () => inputs[el.dataset.input]?.(el)));
  root.querySelectorAll('[data-change]').forEach((el) => el.addEventListener('change', () => inputs[el.dataset.change]?.(el)));
}

function rerender() {
  render();
}

// ---------- Home ----------

function home() {
  const rows = state.lib.plays;
  const today = S.inRange(rows, 'today');
  const top = S.songs(rows, 'week').sort((a, b) => b.plays - a.plays)[0];
  const recent = [];
  const seen = new Set();
  for (const r of rows) if (!seen.has(r.trackId) && seen.add(r.trackId) && recent.push(r) >= 14) break;
  const artists = S.groups(S.inRange(rows, 'week'), S.artistOf).sort((a, b) => b.plays - a.plays).slice(0, 12);
  if (!rows.length) return `<h1>Home</h1>${emptyLibrary()}`;
  return `<h1>Home</h1>
    <div class="tiles">
      <div class="tile"><b>${new Set(today.map((r) => r.trackId)).size}</b><span>songs today</span></div>
      <div class="tile"><b>${S.duration(today.reduce((s, r) => s + r.playedMs, 0))}</b><span>listened today</span></div>
      <div class="tile"><b>${S.streak(rows)}</b><span>day streak</span></div>
      <div class="tile"><b>${new Set(rows.map((r) => r.trackId)).size}</b><span>songs in your library</span></div>
    </div>
    ${top ? `<div class="highlight" data-h="${on(() => openSong(top.trackId))}">
      ${art(top.coverUrl, 88, top.title)}
      <div class="ellipsis"><div class="kicker">Most listened this week</div><div class="big ellipsis">${esc(top.title)}</div>
      <div>${esc(top.artist || 'Unknown artist')} · ${top.plays} plays</div></div></div>` : ''}
    ${sectionTitle('Recently heard', 'See all', 'go-history')}
    <div class="shelf">${recent.map((r) => `<button class="shelf-item" data-h="${on(() => openSong(r.trackId))}">
      ${art(r.coverUrl, 160, r.title, 'big')}<div class="t ellipsis">${esc(r.title)}</div><div class="s ellipsis">${esc(r.artist || 'Unknown artist')}</div></button>`).join('')}</div>
    ${artists.length ? `${sectionTitle('Top artists this week', 'See all', 'go-stats')}
    <div class="shelf artists">${artists.map((a) => `<button class="shelf-item" data-h="${on(() => openArtist(a.name))}">
      ${avatar(a.name, 112)}<div class="t ellipsis">${esc(a.name)}</div><div class="s">${a.plays} plays</div></button>`).join('')}</div>` : ''}
    ${sectionTitle('Latest plays')}
    <div class="rows">${rows.slice(0, 15).map((r) => songRow({
      title: r.title, subtitle: `${r.artist || 'Unknown artist'}  |  ${r.sourceApp}  |  ${S.time(r.startedAt)}`,
      artUrl: r.coverUrl, onOpen: () => openSong(r.trackId),
      onPlay: () => player.playList([toSong(r)]),
    })).join('')}</div>`;
}

function emptyLibrary() {
  return state.source.mode === 'phone'
    ? `<div class="notice">Nothing yet. Songs the app hears show up here.</div>`
    : `<div class="notice">To see your stats here, export a backup in the app (Settings → Export and backup → Backup file) and open it in <a href="#/settings">Settings</a>. At home, switch on "Website on your computer" in the app for a live, editable library.</div>
       <p class="muted">You can still search and play any song, and use radio once a Last.fm key is added.</p>`;
}

function openArtist(name) {
  Object.assign(state.library, { tab: 'songs', filter: { group: 'artists', value: name }, query: '' });
  location.hash = '#/library';
}

// ---------- Listen ----------

async function runSearch(q) {
  state.search = { query: q, loading: true, results: [], error: '' };
  if (state.view === 'listen') rerender();
  try {
    state.search.results = await state.source.search(q);
  } catch (e) {
    state.search.error = e.message;
  }
  state.search.loading = false;
  if (state.view === 'listen') rerender();
}

function lastFmKey() { return state.lib.lastFmKey || ''; }

function loadDiscover(force = false) {
  const key = lastFmKey();
  if (!key || (!force && state.discover)) return;
  state.discover = { loading: true };
  LastFm.discover(key, state.lib.lastFmUser, state.lib.plays)
    .then((d) => { state.discover = d; })
    .catch((e) => { state.discover = { error: e.message || "Couldn't load suggestions" }; })
    .finally(() => state.view === 'listen' && rerender());
}

function loadYt(force = false) {
  if (!state.lib.ytSignedIn || (!force && state.yt)) return;
  state.yt = { loading: true, playlists: [] };
  state.source.ytPlaylists()
    .then((p) => { state.yt = { playlists: p }; })
    .catch((e) => { state.yt = { error: e.message, playlists: [] }; })
    .finally(() => state.view === 'listen' && rerender());
}

function listen() {
  loadDiscover();
  loadYt();
  const s = state.search;
  const songsAll = S.songs(state.lib.plays, 'all');
  let out = `<h1>Listen</h1>`;
  if (s.query) {
    out += `<div class="section-title"><h2>Results for “${esc(s.query)}”</h2><button class="link" data-act="clear-search">Clear</button></div>`;
    if (s.loading) out += '<div class="loader"></div>';
    if (s.error) out += `<div class="notice error">${esc(s.error)}</div>`;
    if (!s.loading && !s.error && !s.results.length) out += '<p class="muted">No songs found.</p>';
    out += `<div class="rows">${s.results.map((song, i) => songRow({
      title: song.title,
      subtitle: [song.artist, song.durationMs ? S.clock(song.durationMs) : ''].filter(Boolean).join('  |  '),
      artUrl: song.thumbnail, onOpen: () => player.playList(s.results.map(toSong), i),
      extra: `<button class="icon-btn" title="Add to queue" data-h="${on(() => { player.add(toSong(song)); toast('Added to queue'); })}">${icon('add-queue')}</button>`,
    })).join('')}</div>`;
    return out;
  }
  if (songsAll.length) {
    const most = [...songsAll].sort((a, b) => b.plays - a.plays);
    const favs = songsAll.filter((x) => x.isFavourite);
    out += `<div class="btn-row">
      <button class="btn" data-h="${on(() => player.playList(songsAll.map(toSong), 0, { shuffle: true }))}">${icon('shuffle', 'sm')}Shuffle my library</button>
      <button class="btn tonal" data-h="${on(() => player.playList(most.map(toSong)))}">${icon('play', 'sm')}Most played</button>
      <button class="btn tonal" ${favs.length ? '' : 'disabled'} data-h="${on(() => player.playList(favs.map(toSong)))}">♥ Favourites</button></div>`;
  }
  if (state.lib.ytSignedIn) {
    const yt = state.yt || { playlists: [] };
    out += sectionTitle('Your YouTube Music') + `<div class="shelf">
      <button class="shelf-item" data-h="${on(() => openPlaylist('LIKED', 'Liked songs'))}">${art('', 160, 'Liked songs', 'big')}<div class="t">Liked songs</div><div class="s">Your likes</div></button>
      ${yt.playlists.map((p) => `<button class="shelf-item" data-h="${on(() => openPlaylist(p.browseId, p.title, p.thumbnail))}">
        ${art(p.thumbnail, 160, p.title, 'big')}<div class="t ellipsis">${esc(p.title)}</div><div class="s ellipsis">${esc(p.subtitle)}</div></button>`).join('')}</div>
      ${yt.error ? `<div class="notice error">${esc(yt.error)}</div>` : ''}`;
  }
  out += sectionTitle('Radio');
  if (!lastFmKey()) {
    return out + `<div class="card"><b>Discover with Last.fm</b><p class="muted">Radio and suggestions based on what you listen to.
      ${state.source.mode === 'phone' ? 'Add your free Last.fm API key in the app (Settings → Last.fm).' : 'Add your free Last.fm API key in Settings.'}</p>
      ${state.source.mode === 'file' ? '<a class="btn" href="#/settings">Open Settings</a>' : ''}</div>`;
  }
  out += `<div class="tiles">
    <button class="radio-card main" data-act="radio-mine">${icon('radio')}<b>Your radio</b><span>From your top songs</span></button>
    <button class="radio-card" data-act="radio-tag" data-label="Bangla" data-tag="bangla">${icon('radio')}<b>Bangla radio</b><span>Top Bangla songs</span></button>
    <button class="radio-card" data-act="radio-tag" data-label="Hindi" data-tag="hindi">${icon('radio')}<b>Hindi radio</b><span>Top Hindi songs</span></button></div>`;
  const d = state.discover;
  if (!d || d.loading) return out + '<div class="loader"></div>';
  if (d.error) return out + `<div class="notice error">${esc(d.error)} <button class="link" data-act="discover-again">Try again</button></div>`;
  if (d.basedOn) {
    out += `<div class="section-title"><h2>Because you listened to ${esc(d.basedOn.title)}</h2><button class="link" data-h="${on(() => songRadio(d.basedOn))}">Radio</button></div>
      <div class="rows">${suggestionRows(d.similar.slice(0, 8))}</div>`;
  }
  if (d.artists.length) {
    out += sectionTitle('Artists for you') + `<div class="shelf artists">${d.artists.map((a) => `<button class="shelf-item" data-h="${on(() => artistRadio(a))}">
      ${avatar(a, 112)}<div class="t ellipsis">${esc(a)}</div><div class="s">Radio</div></button>`).join('')}</div>`;
  }
  if (d.bangla.length) out += `<div class="section-title"><h2>Top Bangla</h2><button class="link" data-act="radio-tag" data-label="Bangla" data-tag="bangla">Radio</button></div><div class="rows">${suggestionRows(d.bangla.slice(0, 6))}</div>`;
  if (d.hindi.length) out += `<div class="section-title"><h2>Top Hindi</h2><button class="link" data-act="radio-tag" data-label="Hindi" data-tag="hindi">Radio</button></div><div class="rows">${suggestionRows(d.hindi.slice(0, 6))}</div>`;
  return out;
}

const songRadio = (t) => player.startRadio(`${t.title} radio`, lastFmKey(), [{ title: t.title, artist: t.artist }]);
async function artistRadio(name) {
  const key = lastFmKey();
  const top = key ? await LastFm.artistTopTracks(key, name, 5).catch(() => []) : [];
  player.startRadio(`${name} radio`, key, top, { playSeedsFirst: true });
}

async function openPlaylist(id, title, thumbnail = '') {
  dialog(`<div class="head">${art(thumbnail, 72, title)}<div><h3>${esc(title)}</h3><div class="muted">Loading…</div></div></div><div class="loader"></div>`);
  try {
    const songs = (await state.source.ytPlaylist(id)).map(toSong);
      dialog(`<div class="head">${art(thumbnail, 72, title)}<div class="ellipsis"><h3 class="ellipsis">${esc(title)}</h3><div class="muted">${songs.length} songs</div></div></div>
      <div class="btn-row"><button class="btn" data-h="${on(() => { player.playList(songs, 0, { shuffle: true }); closeDialog(); })}">${icon('shuffle', 'sm')}Shuffle</button>
      <button class="btn tonal" data-h="${on(() => { player.playList(songs); closeDialog(); })}">${icon('play', 'sm')}Play</button></div>
      <div class="rows">${songs.map((s, i) => songRow({
        title: s.title, subtitle: s.artist, artUrl: s.thumbnail,
        onOpen: () => { player.playList(songs, i); closeDialog(); },
      })).join('')}</div>`);
  } catch (e) {
    dialog(`<h3>${esc(title)}</h3><div class="notice error">${esc(e.message)}</div>`);
  }
}

// ---------- Library ----------

const GROUPS = {
  songs: ['Songs', () => ''],
  artists: ['Artists', S.artistOf],
  apps: ['Apps', (r) => r.sourceApp],
  languages: ['Languages', (r) => r.language],
  genres: ['Genres', (r) => r.genre || 'Not found yet'],
};
const SORTS = { plays: 'Most played', time: 'Most time', recent: 'Recently heard', new: 'Newest finds', score: 'Favourite score', az: 'A to Z' };

function library() {
  const L = state.library;
  const rows = L.filter ? state.lib.plays.filter((r) => GROUPS[L.filter.group][1](r) === L.filter.value) : state.lib.plays;
  const showSongs = L.tab === 'songs' || L.filter;
  const q = L.query.trim().toLowerCase();
  let out = `<h1>Library</h1>
    <div class="tabs">${Object.entries(GROUPS).map(([id, [label]]) => `<button class="tab ${L.tab === id && !L.filter ? 'on' : ''}" data-act="lib-tab" data-tab="${id}">${label}</button>`).join('')}</div>
    <input class="field" data-input="lib-query" placeholder="Search songs or artists" value="${esc(L.query)}" style="max-width:520px;margin-top:12px">
    <div class="chips">${S.RANGES.map((r) => `<button class="chip ${L.range === r.id ? 'on' : ''}" data-act="lib-range" data-range="${r.id}">${r.label}</button>`).join('')}
      <button class="chip ${L.favourites ? 'on' : ''}" data-act="lib-favs">♥ Favourites</button></div>`;
  if (L.filter) {
    out += `<div class="chips"><button class="chip on" data-act="lib-unfilter">${esc(GROUPS[L.filter.group][0].slice(0, -1))}: ${esc(L.filter.value)} ✕</button></div>`;
  }
  if (showSongs) {
    let list = S.songs(rows, L.range)
      .filter((s) => !L.favourites || s.isFavourite)
      .filter((s) => !q || s.title.toLowerCase().includes(q) || s.artist.toLowerCase().includes(q));
    const by = {
      plays: (a, b) => b.plays - a.plays, time: (a, b) => b.ms - a.ms, recent: (a, b) => b.lastPlayed - a.lastPlayed,
      new: (a, b) => b.firstSeen - a.firstSeen, score: (a, b) => b.score - a.score, az: (a, b) => a.title.localeCompare(b.title),
    };
    list = list.sort(by[L.sort]);
    const detail = {
      recent: (s) => `${S.date(s.lastPlayed)} · ${s.plays} plays`, new: (s) => `found ${S.date(s.firstSeen)} · ${s.plays} plays`,
      score: (s) => `score ${s.score} · ${s.plays} plays`,
    }[L.sort];
    out += `<div class="toolbar"><span class="count">${list.length} songs</span>${sortSelect()}</div>`;
    if (list.length) {
      const songs = list.map(toSong);
      out += `<div class="btn-row"><button class="btn" data-h="${on(() => player.playList(songs, 0, { shuffle: true }))}">${icon('shuffle', 'sm')}Shuffle</button>
        <button class="btn tonal" data-h="${on(() => player.playList(songs))}">${icon('play', 'sm')}Play</button></div>
        <div class="rows">${libSongRows(list.slice(0, 400), { detail })}</div>
        ${list.length > 400 ? '<p class="muted">Showing the first 400. Search to find the rest.</p>' : ''}`;
    } else out += '<p class="muted">No songs here yet.</p>';
  } else {
    const [label, keyOf] = GROUPS[L.tab];
    let list = S.groups(S.inRange(rows, L.range), keyOf).filter((g) => !q || g.name.toLowerCase().includes(q));
    list = list.sort(L.sort === 'az' ? (a, b) => a.name.localeCompare(b.name) : (L.sort === 'plays' || L.sort === 'score') ? (a, b) => b.plays - a.plays : (a, b) => b.ms - a.ms);
    out += `<div class="toolbar"><span class="count">${list.length} ${label.toLowerCase()}</span>${sortSelect()}</div>
      <div class="rows">${list.map((g) => `<div class="row plain" data-h="${on(() => { L.filter = { group: L.tab, value: g.name }; rerender(); })}">
        ${avatar(g.name, 56)}<div class="ellipsis"><div class="t ellipsis">${esc(g.name)}</div>
        <div class="s">${g.songs} songs  |  ${g.plays} plays  |  ${S.duration(g.ms)}</div></div><span class="muted">›</span></div>`).join('')}</div>`;
  }
  return out;
}

const sortSelect = () => `<select data-change="lib-sort">${Object.entries(SORTS).map(([id, label]) => `<option value="${id}" ${state.library.sort === id ? 'selected' : ''}>${label}</option>`).join('')}</select>`;

// ---------- Stats ----------

function bars(values, labels) {
  const max = Math.max(1, ...values);
  return `<div class="bars">${values.map((v, i) => `<div class="bar" title="${Math.round(v)} min"><i style="height:${(v / max) * 100}%"></i><span>${esc(labels[i])}</span></div>`).join('')}</div>`;
}
function shares(list, total, detail) {
  return list.map((g) => `<div class="share"><div class="top"><b class="ellipsis">${esc(g.name)}</b><span>${detail(g)}</span></div>
    <div class="track"><i style="width:${total ? (g.ms / total) * 100 : 0}%"></i></div></div>`).join('');
}

function stats() {
  const range = state.statsRange;
  const rows = S.inRange(state.lib.plays, range);
  const songs = S.songs(state.lib.plays, range);
  const total = rows.reduce((s, r) => s + r.playedMs, 0);
  const artists = S.groups(rows, S.artistOf);
  const newFinds = songs.filter((s) => s.firstSeen >= S.since(range)).length;
  const days = range === 'today' || range === 'week' ? 7 : 30;
  const daily = S.dailyMinutes(state.lib.plays, days);
  const hourly = S.hourly(rows);
  return `<h1>Stats</h1>
    <div class="chips">${S.RANGES.map((r) => `<button class="chip ${range === r.id ? 'on' : ''}" data-act="stats-range" data-range="${r.id}">${r.label}</button>`).join('')}</div>
    <div class="tiles">
      <div class="tile"><b>${S.duration(total)}</b><span>listened</span></div>
      <div class="tile"><b>${rows.length}</b><span>plays</span></div>
      <div class="tile"><b>${songs.length}</b><span>songs</span></div>
      <div class="tile"><b>${newFinds}</b><span>new finds</span></div>
      <div class="tile"><b>${artists.length}</b><span>artists</span></div></div>
    <div class="grid2">
      <div>${sectionTitle('Minutes per day')}<div class="card">${bars(daily.map((d) => d.minutes), daily.map((d) => d.label))}</div></div>
      <div>${sectionTitle('Listening clock')}<div class="card">${bars(hourly, hourly.map((_, h) => (h % 4 === 0 ? String(h) : '')))}</div></div>
    </div>
    <div class="grid2">
      <div>${sectionTitle('Top songs')}<div class="rows">${songs.length ? libSongRows(songs.sort((a, b) => b.plays - a.plays).slice(0, 10), { rank: true }) : '<p class="muted">No plays in this period.</p>'}</div></div>
      <div>
        ${sectionTitle('Top artists')}${shares(artists.slice(0, 6), total, (g) => `${g.plays} plays · ${S.duration(g.ms)}`)}
        ${sectionTitle('Languages')}${shares(S.groups(rows, (r) => r.language), total, (g) => `${g.songs} songs · ${S.duration(g.ms)}`)}
        ${sectionTitle('Apps')}${shares(S.groups(rows, (r) => r.sourceApp), total, (g) => `${g.plays} plays · ${S.duration(g.ms)}`)}
      </div>
    </div>`;
}

// ---------- History ----------

function history() {
  const rows = state.lib.plays.slice(0, state.historyShown);
  if (!rows.length) return '<h1>History</h1><p class="muted">No plays yet.</p>';
  const days = new Map();
  for (const r of rows) {
    const k = S.dayKey(r.startedAt);
    if (!days.has(k)) days.set(k, []);
    days.get(k).push(r);
  }
  let out = '<h1>History</h1>';
  for (const [day, list] of days) {
    out += `<div class="section-title"><h2>${esc(S.dayHeader(day))}</h2><span class="orange" style="font-weight:600">${list.length} plays · ${S.duration(list.reduce((s, r) => s + r.playedMs, 0))}</span></div>
      <div class="rows">${list.map((r) => songRow({
        title: r.title, subtitle: [r.artist || 'Unknown artist', r.sourceApp, S.time(r.startedAt), S.duration(r.playedMs)].join('  |  '),
        artUrl: r.coverUrl, onOpen: () => openSong(r.trackId),
        extra: state.source.canEdit ? `<button class="icon-btn" title="Delete this play" data-h="${on(() => deletePlay(r))}">${icon('delete')}</button>` : '',
      })).join('')}</div>`;
  }
  if (state.lib.plays.length > state.historyShown) out += '<div class="btn-row"><button class="btn tonal" data-act="history-more">Show more</button></div>';
  return out;
}

async function deletePlay(r) {
  if (!confirm(`Delete this play of ${r.title} at ${S.time(r.startedAt)}?`)) return;
  await mutate(() => state.source.deletePlay(r.playId, r.trackId), 'Play deleted');
}

// ---------- Settings ----------

function settings() {
  const phone = state.source.mode === 'phone';
  return `<h1>Settings</h1><div class="grid2"><div>
    <div class="card"><h2 style="margin-top:0">Your library</h2>
    ${phone
    ? `<p><span class="dot" style="display:inline-block"></span> Live from your phone over home Wi-Fi. Edits here change the app too.</p>
       <button class="btn outline" data-act="forget-code">Forget the code on this computer</button>`
    : `<p class="muted">This website keeps a copy of your library from a backup file, only in this browser.
       In the app: Settings → Export and backup → Backup file, then send the file to this computer and open it here.</p>
       ${state.lib.backupName ? `<p>Now showing <b>${esc(state.lib.backupName)}</b>, opened ${esc(S.date(state.lib.backupDate))}.</p>` : ''}
       <label class="btn" style="cursor:pointer">Open backup file<input type="file" accept=".json,application/json" data-change="open-backup" hidden></label>
       <p class="muted" style="margin-top:16px">At home, switch on "Website on your computer" in the app and open the address it shows for a live, editable library.</p>`}
    </div></div><div>
    <div class="card"><h2 style="margin-top:0">Last.fm</h2>
    ${phone
    ? `<p class="muted">Radio and Discover use the Last.fm key saved in the app${state.lib.lastFmKey ? '' : ' (none yet: add it in the app under Settings → Last.fm)'}.</p>`
    : `<p class="muted">Free. Create an API account at <a href="https://www.last.fm/api/account/create" target="_blank" rel="noopener">last.fm/api/account/create</a> and paste the API key.</p>
       <form data-form="lastfm"><label class="f">Last.fm API key</label><input class="field" name="key" value="${esc(state.lib.lastFmKey)}">
       <label class="f">Last.fm username (optional)</label><input class="field" name="user" value="${esc(state.lib.lastFmUser)}">
       <button class="btn" style="margin-top:16px">Save</button></form>`}
    </div>
    <div class="card" style="margin-top:16px"><h2 style="margin-top:0">About</h2>
      <p class="muted">Music plays through YouTube's player. Lyrics come from LRCLIB, suggestions from Last.fm.
      Space bar plays and pauses.</p></div>
  </div></div>`;
}

// ---------- Song menu ----------

function openSong(trackId) {
  const rows = state.lib.plays.filter((r) => r.trackId === trackId);
  if (!rows.length) return;
  const s = S.songs(rows, 'all')[0];
  const apps = S.groups(rows, (r) => r.sourceApp).map((g) => `${g.name} (${g.plays})`).join(', ');
  const edit = state.source.canEdit;
  const online = s.onlineTitle && S.songKey(s.onlineTitle, s.onlineArtist) !== S.songKey(s.title, s.artist);
  dialog(`<div class="head">${art(s.coverUrl, 72, s.title)}
      <div class="ellipsis" style="flex:1"><h3 class="ellipsis">${esc(s.title)}</h3><div class="muted">${esc(s.artist || 'Unknown artist')}  |  ${s.plays} plays · ${S.duration(s.ms)}</div></div>
      ${edit ? `<button class="icon-btn on" title="${s.isFavourite ? 'Unheart' : 'Heart'}" data-h="${on(() => mutate(() => state.source.favourite(trackId, !s.isFavourite)).then(() => openSong(trackId)))}">${icon(s.isFavourite ? 'library' : 'heart-o')}</button>` : ''}</div>
    <div class="orange" style="font-weight:600">${esc([s.genre, s.language].filter(Boolean).join('  ·  '))}</div>
    <p class="muted" style="font-size:13px">First heard ${S.date(s.firstSeen)} · last ${S.date(s.lastPlayed)}<br>Heard on: ${esc(apps)}</p>
    ${online && edit ? `<div class="notice" style="display:flex;align-items:center;gap:12px"><div style="flex:1"><div style="font-size:12px">Found online as</div>${esc(s.onlineTitle)} · ${esc(s.onlineArtist)}</div>
      <button class="link" data-h="${on(() => saveTrack(trackId, s.onlineTitle, s.onlineArtist, s.language))}">Use this name</button></div>` : ''}
    <button class="sheet-action" data-h="${on(() => { player.playList([toSong(s)]); closeDialog(); })}">${icon('play')}Play</button>
    <button class="sheet-action" data-h="${on(() => { player.playNext(toSong(s)); toast('Plays next'); })}">${icon('queue')}Play next</button>
    <button class="sheet-action" data-h="${on(() => { songRadio(s); closeDialog(); })}">${icon('radio')}Start radio</button>
    <a class="sheet-action" style="color:inherit;text-decoration:none" target="_blank" rel="noopener" href="https://www.youtube.com/results?search_query=${encodeURIComponent([s.title, s.artist].filter(Boolean).join(' '))}">${icon('listen')}Open on YouTube</a>
    ${edit ? `<form data-form="edit" data-id="${trackId}" style="margin-top:8px">
      <label class="f">Title</label><input class="field" name="title" value="${esc(s.title)}" required>
      <label class="f">Artist</label><input class="field" name="artist" value="${esc(s.artist)}">
      <div class="chips">${LANGUAGES.map((l) => `<label class="chip ${l === s.language ? 'on' : ''}" style="display:inline-flex;align-items:center;cursor:pointer">
        <input type="radio" name="language" value="${l}" ${l === s.language ? 'checked' : ''} hidden data-change="lang-chip">${l}</label>`).join('')}</div>
      <p class="muted" style="font-size:13px">Renaming to a name you already have merges the two songs.</p>
      <div class="btn-row"><button class="btn">Save</button></div></form>
      <button class="sheet-action danger" data-h="${on(() => deleteTrack(trackId, s))}">${icon('delete')}Delete song and all its plays</button>`
    : '<p class="muted" style="font-size:13px">To edit songs, open this website from your phone at home (Settings → Website on your computer in the app).</p>'}`);
}

async function saveTrack(id, title, artist, language) {
  await mutate(() => state.source.editTrack(id, title, artist, language), 'Saved');
  closeDialog();
}

async function deleteTrack(id, s) {
  if (!confirm(`Delete ${s.title} and its ${s.plays} plays?`)) return;
  await mutate(() => state.source.deleteTrack(id), 'Song deleted');
  closeDialog();
}

/** A change on the phone, then a fresh copy of the library. */
async function mutate(fn, done) {
  try {
    await fn();
    if (done) toast(done);
  } catch (e) {
    toast(e.message);
  }
  await reload();
}

function dialog(html) {
  const d = $('#dialog');
  d.innerHTML = `<div class="dialog">${html}</div>`;
  d.hidden = false;
  d.onclick = (e) => { if (e.target === d) closeDialog(); };
  wire(d);
}
function closeDialog() { $('#dialog').hidden = true; }

// ---------- Clicks, forms and inputs ----------

const actions = {
  'go-history': () => { location.hash = '#/history'; },
  'go-stats': () => { location.hash = '#/stats'; },
  'clear-search': () => { state.search = { query: '', results: [] }; $('#search').value = ''; rerender(); },
  'radio-mine': () => {
    const mine = S.songs(state.lib.plays, 'month').sort((a, b) => b.plays - a.plays).slice(0, 5);
    const user = state.lib.lastFmUser;
    const key = lastFmKey();
    (user && key ? LastFm.userTopTracks(key, user, 5).catch(() => []) : Promise.resolve([])).then((lf) =>
      player.startRadio('Your radio', key, LastFm.shuffle([...mine.map((s) => ({ title: s.title, artist: s.artist })), ...lf])));
  },
  'radio-tag': async ({ label, tag }) => {
    const key = lastFmKey();
    const top = key ? await LastFm.tagTopTracks(key, tag, 15).catch(() => []) : [];
    player.startRadio(`${label} radio`, key, top, { playSeedsFirst: true });
  },
  'discover-again': () => { state.discover = null; loadDiscover(true); rerender(); },
  'lib-tab': ({ tab }) => { Object.assign(state.library, { tab, filter: null }); rerender(); },
  'lib-range': ({ range }) => { state.library.range = range; rerender(); },
  'lib-favs': () => { state.library.favourites = !state.library.favourites; rerender(); },
  'lib-unfilter': () => { state.library.filter = null; rerender(); },
  'stats-range': ({ range }) => { state.statsRange = range; rerender(); },
  'history-more': () => { state.historyShown += 300; rerender(); },
  'forget-code': () => { state.source.forgetCode(); state.needCode = true; rerender(); },
};

const forms = {
  code: async (data) => {
    state.source.setCode(String(data.get('code')));
    await reload();
    if (state.needCode) toast('That code is not right. Check the app.');
  },
  lastfm: (data) => {
    state.source.saveLastFm(String(data.get('key')), String(data.get('user')));
    state.discover = null;
    toast('Saved');
    reload();
  },
  edit: (data, form) => saveTrack(Number(form.dataset.id), String(data.get('title')).trim(), String(data.get('artist')).trim(), String(data.get('language'))),
};

let queryTimer;
const inputs = {
  'lib-query': (el) => {
    state.library.query = el.value;
    clearTimeout(queryTimer);
    queryTimer = setTimeout(() => {
      rerender();
      const box = document.querySelector('[data-input="lib-query"]');
      box.focus();
      box.setSelectionRange(box.value.length, box.value.length);
    }, 250);
  },
  'lib-sort': (el) => { state.library.sort = el.value; rerender(); },
  'lang-chip': (el) => {
    el.closest('.chips').querySelectorAll('.chip').forEach((c) => c.classList.toggle('on', c.contains(el)));
  },
  'open-backup': async (el) => {
    const file = el.files[0];
    if (!file) return;
    try {
      await state.source.openBackup(file);
      toast('Backup opened');
      state.discover = null;
      await reload();
    } catch (e) {
      toast(e.message.includes('backup') ? e.message : "That file isn't a Music Tracker backup");
    }
  },
};

// ---------- Player bar and full player ----------

function renderPlayer() {
  const bar = $('#playerbar');
  const song = player.current;
  $('#app').classList.toggle('with-player', !!song);
  bar.hidden = !song;
  if (!song) return;
  const repeatIcon = player.repeat === 'one' ? 'repeat-one' : 'repeat';
  bar.innerHTML = `
    <div class="pb-song" data-act="open-full">${art(song.thumbnail, 56, song.title)}
      <div class="ellipsis"><div class="t ellipsis">${esc(song.title)}</div><div class="s ellipsis">${esc(song.artist || 'Unknown artist')}</div>
      ${player.radio ? `<div class="radio-tag">${icon('radio', 'sm')} ${esc(player.radio.name)}</div>` : ''}${player.error ? `<div class="radio-tag" style="color:var(--error)">${esc(player.error)}</div>` : ''}</div></div>
    <div class="pb-center"><div class="pb-controls">
      <button class="icon-btn ${player.shuffle ? 'on' : ''}" title="Shuffle" data-act="shuffle">${icon('shuffle')}</button>
      <button class="icon-btn" title="Previous" data-act="prev">${icon('prev')}</button>
      <button class="pb-play" title="Play or pause" data-act="toggle">${player.loading ? '<div class="loader" style="width:20px;height:20px;margin:0"></div>' : icon(player.playing ? 'pause' : 'play')}</button>
      <button class="icon-btn" title="Next" data-act="next">${icon('next')}</button>
      <button class="icon-btn ${player.repeat !== 'off' ? 'on' : ''}" title="Repeat" data-act="repeat">${icon(repeatIcon)}</button></div>
      <div class="pb-progress"><span id="pb-pos">0:00</span><input id="pb-seek" type="range" min="0" max="1000" value="0"><span id="pb-dur">0:00</span></div></div>
    <div class="pb-right">
      <button class="icon-btn" title="Lyrics" data-act="open-lyrics">${icon('lyrics')}</button>
      <button class="icon-btn" title="Next songs" data-act="open-full">${icon('queue')}</button>
      ${icon('volume', 'sm muted')}<input id="pb-vol" type="range" min="0" max="100" value="${player.volume}">
      <button class="icon-btn" title="Full player" data-act="open-full">${icon('expand')}</button></div>`;
  wirePlayer(bar);
  bar.querySelector('#pb-seek').addEventListener('input', (e) => { seeking = Number(e.target.value) / 1000; });
  bar.querySelector('#pb-seek').addEventListener('change', (e) => { player.seek((Number(e.target.value) / 1000) * player.duration()); seeking = null; });
  bar.querySelector('#pb-vol').addEventListener('input', (e) => player.setVolume(Number(e.target.value)));
  if (state.full.open) renderFull();
  tick();
}

let seeking = null;
const playerActions = {
  toggle: () => player.toggle(), next: () => player.next(), prev: () => player.previous(),
  shuffle: () => player.toggleShuffle(), repeat: () => player.cycleRepeat(),
  'open-full': () => openFull('queue'), 'open-lyrics': () => openFull('lyrics'),
  'close-full': () => closeFull(), 'pane-queue': () => { state.full.pane = 'queue'; renderFull(); },
  'pane-lyrics': () => { state.full.pane = 'lyrics'; renderFull(); },
  'start-radio': () => { const s = player.current; if (s) songRadio(s); },
};
function wirePlayer(root) {
  root.querySelectorAll('[data-act]').forEach((el) => el.addEventListener('click', (e) => {
    e.stopPropagation();
    playerActions[el.dataset.act]?.();
  }));
}

function openFull(pane) {
  state.full = { open: true, pane };
  $('#fullplayer').hidden = false;
  renderFull();
}
function closeFull() {
  state.full.open = false;
  $('#fullplayer').hidden = true;
}

function renderFull() {
  const song = player.current;
  const el = $('#fullplayer');
  if (!song) return closeFull();
  loadLyrics(song);
  el.innerHTML = `
    <button class="icon-btn fp-close" title="Close" data-act="close-full">${icon('collapse')}</button>
    <div class="fp-left">
      <div class="fp-title"><div class="ellipsis" style="flex:1"><div class="t ellipsis">${esc(song.title)}</div><div class="s ellipsis">${esc(song.artist || 'Unknown artist')}</div></div>
        <button class="icon-btn" title="Start radio from this song" data-act="start-radio">${icon('radio')}</button></div>
      <div class="disc" id="disc" title="Click the ring to jump">
        <svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="50" fill="var(--panel-high)"/>
          <circle cx="50" cy="50" r="45" fill="none" stroke="var(--line)" stroke-width="1.2"/>
          <circle id="ring" cx="50" cy="50" r="45" fill="none" stroke="var(--orange)" stroke-width="1.6" stroke-linecap="round"
            transform="rotate(90 50 50)" stroke-dasharray="0 1000"/></svg>
        ${art(song.thumbnail, 300, song.title, 'round')}
        <span class="time" id="fp-time">0:00</span></div>
      <div class="fp-controls">
        <button class="icon-btn ${player.shuffle ? 'on' : ''}" title="Shuffle" data-act="shuffle">${icon('shuffle')}</button>
        <button class="icon-btn" title="Previous" data-act="prev">${icon('prev', 'lg')}</button>
        <button class="fp-play" title="Play or pause" data-act="toggle">${player.loading ? '<div class="loader" style="margin:0"></div>' : icon(player.playing ? 'pause' : 'play')}</button>
        <button class="icon-btn" title="Next" data-act="next">${icon('next', 'lg')}</button>
        <button class="icon-btn ${player.repeat !== 'off' ? 'on' : ''}" title="Repeat" data-act="repeat">${icon(player.repeat === 'one' ? 'repeat-one' : 'repeat')}</button></div>
    </div>
    <div class="fp-right">
      <div class="fp-tabs"><button class="${state.full.pane === 'queue' ? 'on' : ''}" data-act="pane-queue">Next Songs</button>
        <button class="${state.full.pane === 'lyrics' ? 'on' : ''}" data-act="pane-lyrics">Lyrics</button></div>
      <div class="fp-panel" id="fp-panel">${state.full.pane === 'queue' ? queuePane() : lyricsPane()}</div>
    </div>`;
  wirePlayer(el);
  el.querySelectorAll('[data-skip]').forEach((r) => r.addEventListener('click', () => player.skipTo(Number(r.dataset.skip))));
  el.querySelectorAll('[data-remove]').forEach((b) => b.addEventListener('click', (e) => { e.stopPropagation(); player.removeAt(Number(b.dataset.remove)); }));
  el.querySelectorAll('[data-seek]').forEach((l) => l.addEventListener('click', () => player.seek(Number(l.dataset.seek))));
  el.querySelector('#disc').addEventListener('click', (e) => {
    const r = e.currentTarget.getBoundingClientRect();
    const deg = (Math.atan2(e.clientY - (r.top + r.height / 2), e.clientX - (r.left + r.width / 2)) * 180) / Math.PI;
    player.seek((((deg - 90 + 360) % 360) / 360) * player.duration());
  });
  tick();
}

function queuePane() {
  const next = player.queue.map((s, i) => [s, i]).slice(player.index + 1);
  if (!next.length) return '<p class="muted">Nothing up next. Start a radio to keep the music going.</p>';
  return next.map(([s, i]) => `<div class="row plain" data-skip="${i}">
    ${art(s.thumbnail, 52, s.title)}<div class="ellipsis"><div class="t ellipsis">${esc(s.title)}</div><div class="s ellipsis">${esc(s.artist || 'Unknown artist')}</div></div>
    <div class="actions">${s.durationMs ? `<span class="muted" style="font-size:13px">${S.clock(s.durationMs)}</span>` : ''}
      <button class="icon-btn" title="Remove" data-remove="${i}">${icon('close', 'sm')}</button></div></div>`).join('');
}

function lyricsPane() {
  const l = state.lyrics;
  if (!l || l.loading) return '<div class="loader"></div>';
  if (!l.lines.length) return '<p class="muted">No lyrics found for this song.</p>';
  return l.lines.map((line, i) => `<p class="lyric" id="lyric-${i}" ${line.timeMs >= 0 ? `data-seek="${line.timeMs}"` : ''}>${esc(line.text || '♪')}</p>`).join('')
    + (l.synced ? '' : '<p class="muted" style="font-size:13px">Lyrics from LRCLIB (not timed)</p>');
}

function loadLyrics(song) {
  const key = S.songKey(song.title, song.artist);
  if (state.lyrics?.key === key) return;
  state.lyrics = { key, loading: true, lines: [] };
  findLyrics(song.title, song.artist)
    .then((l) => { if (state.lyrics.key === key) state.lyrics = { key, ...l }; })
    .catch(() => { if (state.lyrics.key === key) state.lyrics = { key, synced: false, lines: [] }; })
    .finally(() => { if (state.full.open && state.full.pane === 'lyrics') renderFull(); });
}

// Twice a second: progress, the ring, and the current lyric line.
let lastLyric = -1;
function tick() {
  if (!player?.current) return;
  const pos = player.position();
  const dur = player.duration();
  const frac = seeking ?? (dur ? Math.min(1, pos / dur) : 0);
  const posEl = $('#pb-pos');
  if (posEl) {
    posEl.textContent = S.clock(frac * dur);
    $('#pb-dur').textContent = S.clock(dur);
    if (seeking === null) $('#pb-seek').value = Math.round(frac * 1000);
  }
  if (!state.full.open) return;
  const ring = $('#ring');
  if (ring) ring.setAttribute('stroke-dasharray', `${(frac * 2 * Math.PI * 45).toFixed(2)} 1000`);
  const t = $('#fp-time');
  if (t) t.textContent = `${S.clock(frac * dur)} / ${S.clock(dur)}`;
  const l = state.lyrics;
  if (state.full.pane === 'lyrics' && l?.synced) {
    let current = -1;
    l.lines.forEach((line, i) => { if (line.timeMs <= pos + 300) current = i; });
    document.querySelectorAll('.lyric').forEach((p, i) => {
      p.classList.toggle('sung', i <= current);
      p.classList.toggle('now', i === current);
    });
    if (current !== lastLyric && current >= 0) {
      lastLyric = current;
      document.getElementById(`lyric-${current}`)?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    }
  }
}

start();
