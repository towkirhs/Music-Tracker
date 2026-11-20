// Listening stats, the same rules as the phone app (ui/Stats.kt). Pure functions, no page access.

export const RANGES = [
  { id: 'today', label: 'Today', back: 0 },
  { id: 'week', label: '7 days', back: 6 },
  { id: 'month', label: '30 days', back: 29 },
  { id: 'year', label: 'Year', back: 364 },
  { id: 'all', label: 'All time', back: null },
];

const DAY = 86_400_000;

/** Local midnight of the day [back] days ago. */
export function startOfDay(now, back = 0) {
  const d = new Date(now);
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - back);
  return d.getTime();
}

export function since(rangeId, now = Date.now()) {
  const r = RANGES.find((x) => x.id === rangeId);
  return r.back === null ? 0 : Math.min(startOfDay(now, r.back), now);
}

export const inRange = (rows, rangeId, now = Date.now()) => {
  const from = since(rangeId, now);
  return rows.filter((r) => r.startedAt >= from);
};

export const artistOf = (row) => row.artist || 'Unknown artist';

/** One entry per song: plays, time, last and first heard, favourite score. */
export function songs(all, rangeId, now = Date.now()) {
  const ninety = now - 90 * DAY;
  const recent = new Map();
  for (const r of all) if (r.startedAt >= ninety) recent.set(r.trackId, (recent.get(r.trackId) || 0) + 1);
  const byTrack = new Map();
  for (const r of inRange(all, rangeId, now)) {
    if (!byTrack.has(r.trackId)) byTrack.set(r.trackId, []);
    byTrack.get(r.trackId).push(r);
  }
  return [...byTrack.entries()].map(([trackId, plays]) => {
    const first = plays[0];
    const apps = new Map();
    for (const p of plays) apps.set(p.sourceApp, (apps.get(p.sourceApp) || 0) + 1);
    return {
      trackId,
      title: first.title,
      artist: first.artist,
      language: first.language,
      genre: first.genre || '',
      coverUrl: first.coverUrl || '',
      onlineTitle: first.onlineTitle || '',
      onlineArtist: first.onlineArtist || '',
      isFavourite: first.isFavourite,
      plays: plays.length,
      ms: plays.reduce((s, p) => s + p.playedMs, 0),
      lastPlayed: Math.max(...plays.map((p) => p.startedAt)),
      firstSeen: first.firstSeen,
      topSource: [...apps.entries()].sort((a, b) => b[1] - a[1])[0][0],
      score: Math.min(100, (recent.get(trackId) || 0) * 10 + (first.isFavourite ? 20 : 0)),
    };
  });
}

/** Totals for each artist / app / language / genre, most listened first. */
export function groups(rows, keyOf) {
  const m = new Map();
  for (const r of rows) {
    const k = keyOf(r);
    if (!m.has(k)) m.set(k, { name: k, plays: 0, ms: 0, tracks: new Set() });
    const g = m.get(k);
    g.plays++;
    g.ms += r.playedMs;
    g.tracks.add(r.trackId);
  }
  return [...m.values()]
    .map((g) => ({ name: g.name, plays: g.plays, ms: g.ms, songs: g.tracks.size }))
    .sort((a, b) => b.ms - a.ms);
}

export const dayKey = (millis) => startOfDay(millis);

/** Minutes per day for the last [days] days, oldest first, with short labels. */
export function dailyMinutes(rows, days, now = Date.now()) {
  const byDay = new Map();
  for (const r of rows) {
    const k = dayKey(r.startedAt);
    byDay.set(k, (byDay.get(k) || 0) + r.playedMs);
  }
  const out = [];
  for (let back = days - 1; back >= 0; back--) {
    const k = startOfDay(now, back);
    const d = new Date(k);
    const label = days <= 7 ? d.toLocaleDateString(undefined, { weekday: 'short' }) : String(d.getDate());
    out.push({ label, minutes: (byDay.get(k) || 0) / 60_000 });
  }
  return out;
}

/** Minutes listened in each hour of the day (0–23). */
export function hourly(rows) {
  const out = new Array(24).fill(0);
  for (const r of rows) out[new Date(r.startedAt).getHours()] += r.playedMs / 60_000;
  return out;
}

/** Days in a row, ending today (or yesterday), with at least one play. */
export function streak(rows, now = Date.now()) {
  const days = new Set(rows.map((r) => dayKey(r.startedAt)));
  let back = days.has(startOfDay(now)) ? 0 : 1;
  let count = 0;
  while (days.has(startOfDay(now, back))) {
    count++;
    back++;
  }
  return count;
}

export function duration(ms) {
  const totalMin = Math.floor(ms / 60_000);
  const h = Math.floor(totalMin / 60);
  const m = totalMin % 60;
  if (h > 0) return `${h} h ${m} m`;
  if (totalMin > 0) return `${m} m`;
  return `${Math.floor(ms / 1000)} s`;
}

export const clock = (ms) => {
  const s = Math.max(0, Math.floor(ms / 1000));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
};

export const time = (millis) => new Date(millis).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
export const date = (millis) => new Date(millis).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

export function dayHeader(dayStart, now = Date.now()) {
  if (dayStart === startOfDay(now)) return 'Today';
  if (dayStart === startOfDay(now, 1)) return 'Yesterday';
  return new Date(dayStart).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'short', year: 'numeric' });
}

/** Same as the app's SongText.key, so "Kesariya (Official Video)" and "kesariya" are one song. */
export function songKey(title, artist) {
  const clean = (s) => {
    const noBrackets = s.replace(/[(\[{].*?[)\]}]/g, ' ');
    const base = noBrackets.trim() ? noBrackets : s;
    return base
      .toLowerCase()
      .replace(/\b(official|music|lyric|lyrics|video|audio|full|song|hd|4k|remastered|remaster)\b/g, ' ')
      .replace(/[^\p{L}\p{M}\p{N}]+/gu, ' ')
      .trim();
  };
  return clean(title) + '|' + clean(artist);
}

/** Plays joined with their songs, from a backup file made by the app (Settings → Backup file). */
export function rowsFromBackup(backup) {
  if (!backup || backup.app !== 'Music Tracker' || !Array.isArray(backup.tracks) || !Array.isArray(backup.plays)) {
    throw new Error("That file isn't a Music Tracker backup");
  }
  const tracks = new Map(backup.tracks.map((t) => [t.id, t]));
  return backup.plays
    .filter((p) => tracks.has(p.trackId))
    .map((p) => {
      const t = tracks.get(p.trackId);
      return {
        playId: p.id, trackId: p.trackId, startedAt: p.startedAt, playedMs: p.playedMs,
        sourceApp: p.sourceApp, detector: p.detector,
        title: t.title, artist: t.artist, language: t.language, isFavourite: !!t.isFavourite,
        firstSeen: t.firstSeen, coverUrl: t.coverUrl || '', genre: t.genre || '',
        onlineTitle: '', onlineArtist: '',
      };
    })
    .sort((a, b) => b.startedAt - a.startedAt);
}
