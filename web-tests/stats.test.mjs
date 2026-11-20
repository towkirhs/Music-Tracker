// Run: node --test web-tests/*.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as S from '../app/src/main/assets/web/stats.js';

const now = new Date(2026, 10, 21, 15, 0).getTime(); // 21 Nov 2026, 3 pm local
const hour = 3_600_000;
const row = (trackId, startedAt, playedMs, extra = {}) => ({
  playId: Math.random(), trackId, startedAt, playedMs, sourceApp: 'Instagram', detector: 'label',
  title: 'Song ' + trackId, artist: trackId === 3 ? '' : 'Artist ' + trackId, language: 'Hindi',
  isFavourite: trackId === 1, firstSeen: now - 100 * 24 * hour, ...extra,
});
const rows = [
  row(1, now - 1 * hour, 120_000),
  row(1, now - 26 * hour, 60_000),
  row(2, now - 50 * hour, 30_000),
  row(3, now - 40 * 24 * hour, 600_000),
];

test('ranges start at local midnight', () => {
  assert.equal(S.since('today', now), new Date(2026, 10, 21).getTime());
  assert.equal(S.since('week', now), new Date(2026, 10, 15).getTime());
  assert.equal(S.since('all', now), 0);
  assert.equal(S.inRange(rows, 'month', now).length, 3);
});

test('songs add up plays, time and score', () => {
  const s = S.songs(rows, 'all', now).find((x) => x.trackId === 1);
  assert.equal(s.plays, 2);
  assert.equal(s.ms, 180_000);
  assert.equal(s.score, 40); // 2 recent plays × 10 + 20 for the heart
});

test('groups by artist, unknown artist named', () => {
  const g = S.groups(rows, S.artistOf);
  assert.deepEqual(g.map((x) => x.name), ['Unknown artist', 'Artist 1', 'Artist 2']);
  assert.equal(g[1].songs, 1);
});

test('streak counts days in a row', () => assert.equal(S.streak(rows, now), 3));

test('daily minutes, oldest first', () => {
  const d = S.dailyMinutes(rows, 7, now);
  assert.equal(d.length, 7);
  assert.equal(d[6].minutes, 2);
  assert.equal(d[5].minutes, 1);
});

test('durations read like the app', () => {
  assert.equal(S.duration(3_900_000), '1 h 5 m');
  assert.equal(S.duration(240_000), '4 m');
  assert.equal(S.duration(45_000), '45 s');
  assert.equal(S.clock(245_000), '4:05');
});

test('song key matches the app', () => {
  assert.equal(S.songKey('Kesariya (From "Brahmastra")', 'Arijit Singh'), 'kesariya|arijit singh');
  assert.equal(S.songKey('Tumi Robe Nirobe - Official Video', ''), 'tumi robe nirobe|');
});

test('backup file becomes play rows, newest first', () => {
  const r = S.rowsFromBackup({
    app: 'Music Tracker',
    tracks: [{ id: 7, title: 'Alo', artist: 'Tahsan', language: 'Bangla', firstSeen: 1, isFavourite: true, coverUrl: 'c' }],
    plays: [
      { id: 1, trackId: 7, startedAt: 10, playedMs: 5, sourceApp: 'YouTube', detector: 'label' },
      { id: 2, trackId: 7, startedAt: 20, playedMs: 5, sourceApp: 'TikTok', detector: 'label' },
      { id: 3, trackId: 99, startedAt: 30, playedMs: 5, sourceApp: 'x', detector: 'x' },
    ],
  });
  assert.deepEqual(r.map((x) => x.playId), [2, 1]);
  assert.equal(r[0].coverUrl, 'c');
  assert.throws(() => S.rowsFromBackup({ foo: 1 }));
});
