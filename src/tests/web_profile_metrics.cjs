'use strict';
const assert = require('node:assert/strict');
const {profileStats, profileSuite, profileSummary} = require('./web_profile.js');

const approx = (actual, expected) => assert.ok(Math.abs(actual - expected) < 1e-9, `${actual} != ${expected}`);
const stats = profileStats([100, 5, 20, NaN, 10]);
assert.equal(stats.count, 4);
approx(stats.mean, 33.75); approx(stats.p50, 15); approx(stats.p95, 88); approx(stats.p99, 97.6);
assert.equal(profileStats([]).mean, null);

const suite = profileSuite('island');
assert.equal(suite.length, 12);
for (let repeat = 1; repeat <= 3; repeat++) {
  const trials = suite.filter(t => t.repeat === repeat);
  assert.equal(trials.length, 4);
  assert.deepEqual(trials.map(t => `${t.water}/${t.fps}`).sort(), ['0/144', '0/60', '3/144', '6/144']);
}
assert.deepEqual(suite.filter((_, i) => i % 4 === 0).map(t => t.water), [6, 3, 0]);
const samples = [
  {ms: 10, interval: null, phases: {water: 4, reflection: 3, wait: 1}, counters: {temporary_textures: 258}},
  {ms: 10, interval: 10, phases: {water: 4, reflection: 3, wait: 1}, counters: {temporary_textures: 258}},
  {ms: 40, interval: 40, phases: {water: 7, reflection: 6, wait: 0}, counters: {temporary_textures: 258}},
];
const result = profileSummary(samples, [], 1000 / 60);
assert.equal(result.valid, true);
assert.equal(result.submission_interval_ms.count, 2); // No warmup/first-frame interval.
approx(result.fps, 40);
assert.equal(result.intervals_over_33ms, 1);
assert.equal(result.intervals_over_two_refreshes, 1);
approx(result.cpu_phases_ms.water.mean, 5);
approx(result.cpu_phases_ms.reflection.mean, 4); // Nested, never added to water.
assert.equal(result.gpu_api_calls_per_frame.temporary_textures.mean, 258);
assert.equal(profileSummary(samples, ['background tab'], 16.67).valid, false);
assert.equal(profileSummary([], [], 16.67).valid, false);
console.log('WebGPU profiler statistics, trial order and invalid-run checks passed. No GPU test performed.');
