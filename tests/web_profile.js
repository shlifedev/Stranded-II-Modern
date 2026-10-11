/* Isolated profiler. No shell.js, IndexedDB mount, or production settings. */
'use strict';

function profileStats(values) {
  const sorted = values.filter(Number.isFinite).sort((a, b) => a - b);
  if (!sorted.length) return {count: 0, mean: null, p50: null, p95: null, p99: null, max: null};
  const quantile = p => {
    const at = (sorted.length - 1) * p, lo = Math.floor(at), hi = Math.ceil(at);
    return sorted[lo] + (sorted[hi] - sorted[lo]) * (at - lo);
  };
  return {count: sorted.length, mean: sorted.reduce((a, b) => a + b, 0) / sorted.length,
    p50: quantile(.5), p95: quantile(.95), p99: quantile(.99), max: sorted.at(-1)};
}

function profileSuite(scene, repeats = 3) {
  const conditions = [{water: 6, fps: 144}, {water: 3, fps: 144}, {water: 0, fps: 144}, {water: 0, fps: 60}];
  return Array.from({length: repeats}, (_, repeat) =>
    conditions.map((_, i) => ({scene, ...conditions[(i + repeat) % conditions.length], repeat: repeat + 1}))).flat();
}

function profileSummary(samples, invalid, refreshMs) {
  const phases = {}, counters = {};
  for (const frame of samples) {
    for (const [key, value] of Object.entries(frame.phases)) (phases[key] ??= []).push(value);
    for (const [key, value] of Object.entries(frame.counters)) (counters[key] ??= []).push(value);
  }
  const frameMs = profileStats(samples.map(f => f.ms));
  const intervals = samples.map(f => f.interval).filter(Number.isFinite);
  const intervalMs = profileStats(intervals);
  const reasons = [...new Set(invalid)];
  if (samples.length < 2) reasons.push('insufficient measured frames');
  return {valid: !reasons.length, invalid_reasons: reasons,
    frames: samples.length, frame_ms: frameMs, submission_interval_ms: intervalMs,
    fps: intervalMs.mean > 0 ? 1000 / intervalMs.mean : null,
    intervals_over_33ms: intervals.filter(n => n > 1000 / 30).length,
    intervals_over_two_refreshes: refreshMs > 0 ? intervals.filter(n => n > refreshMs * 2.2).length : null,
    cpu_phases_ms: Object.fromEntries(Object.entries(phases).map(([key, values]) => [key, profileStats(values)])),
    gpu_api_calls_per_frame: Object.fromEntries(Object.entries(counters).map(([key, values]) => [key, profileStats(values)])),
    submission_intervals_ms: intervals.map(n => +n.toFixed(3))};
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = {profileStats, profileSuite, profileSummary};
} else {
  profileBoot().catch(error => {
    document.getElementById('status').textContent = '측정 오류: ' + (error.stack || error);
  });
}

async function profileBoot() {
  const config = await fetch('profile-config.json').then(r => r.json());
  const query = new URLSearchParams(location.search);
  if (query.has('trial')) await profileRunner(config, query);
  else await profileController(config);
}

async function profileController(config) {
  const status = document.getElementById('status'), start = document.getElementById('start');
  const stop = document.getElementById('stop'), viewport = document.getElementById('viewport');
  const width = document.getElementById('width'), height = document.getElementById('height');
  width.value = Math.min(1920, Math.max(800, innerWidth));
  height.value = Math.min(1080, Math.max(600, innerHeight));
  let stopped = false, activeFrame, rejectActive;
  const save = async report => {
    const response = await fetch('/report', {method: 'POST', headers: {'Content-Type': 'application/json',
      'X-Profile-Token': config.report_token}, body: JSON.stringify(report)});
    if (!response.ok) throw new Error('측정 보고서를 저장하지 못했습니다: ' + response.status);
  };
  const show = trials => {
    const columns = ['반복', '물', '제한', 'FPS', 'p95 ms', 'p99 ms', '물 CPU ms', '임시 텍스처/프레임', '상태'];
    const table = document.createElement('table'), head = table.insertRow();
    for (const label of columns) { const th = document.createElement('th'); th.textContent = label; head.append(th); }
    for (const trial of trials) {
      const n = value => Number.isFinite(value) ? value.toFixed(2) : '—';
      const row = table.insertRow();
      for (const value of [trial.repeat, trial.water, trial.fps_limit, n(trial.fps),
        n(trial.submission_interval_ms.p95), n(trial.submission_interval_ms.p99),
        n(trial.cpu_phases_ms.water?.mean), n(trial.gpu_api_calls_per_frame.temporary_textures?.mean),
        trial.valid ? '유효' : trial.invalid_reasons.join(', ')]) row.insertCell().textContent = String(value);
    }
    document.getElementById('summary').replaceChildren(table);
  };
  stop.onclick = () => {
    stopped = true;
    activeFrame?.remove();
    rejectActive?.(new Error('사용자가 측정을 중단했습니다.'));
  };
  status.textContent = '물 품질 6·3·0 / 144 FPS, 물 품질 0 / 60 FPS를 비교합니다.';
  start.onclick = async () => {
    if (!navigator.gpu || typeof WebAssembly.Suspending !== 'function') {
      status.textContent = 'WebGPU와 JSPI를 지원하는 Chrome/Edge가 필요합니다.';
      return;
    }
    const w = Number(width.value), h = Number(height.value);
    if (!Number.isInteger(w) || !Number.isInteger(h) || w < 800 || w > 1920 || h < 600 || h > 1080) {
      status.textContent = '해상도 범위: 800–1920 × 600–1080'; return;
    }
    stopped = false; start.disabled = true; stop.disabled = false;
    const report = {status: 'running', started_utc: new Date().toISOString(), width: w, height: h,
      scene: document.getElementById('scene').value, trials: [],
      cpu_phase_note: 'reflection is a subset of water; phases must not be summed as independent totals',
      rendering_note: 'FPS is the interval between completed Flip calls, not scanout FPS; API counters add overhead'};
    try {
      const ticks = [];
      status.textContent = '화면 갱신 간격을 확인하고 있습니다…';
      for (let i = 0; i < 91; i++) ticks.push(await new Promise(requestAnimationFrame));
      if (stopped || document.hidden || !document.hasFocus()) throw new Error('측정 창을 앞에 두고 다시 시작하세요.');
      report.idle_raf_ms = profileStats(ticks.slice(1).map((n, i) => n - ticks[i]));
      await save(report);
      const suite = profileSuite(report.scene);
      for (let index = 0; index < suite.length; index++) {
        if (stopped) throw new Error('측정 중단');
        const trial = suite[index];
        status.textContent = `${index + 1}/${suite.length} · 물 ${trial.water}, ${trial.fps} FPS, 반복 ${trial.repeat} · 로딩/준비 5초 + 측정 30초`;
        const result = await new Promise((resolve, reject) => {
          const frame = document.createElement('iframe'); activeFrame = frame; rejectActive = reject;
          const params = new URLSearchParams({...trial, trial: index, width: w, height: h,
            refresh: report.idle_raf_ms.p50, warmup: 5000, duration: 30000});
          let timer;
          const receive = event => {
            if (event.origin !== location.origin || event.source !== frame.contentWindow) return;
            if (event.data?.type !== 's2-profile-result') return;
            cleanup();
            if (event.data.error) reject(new Error(event.data.error)); else resolve(event.data.result);
          };
          const cleanup = () => { clearTimeout(timer); removeEventListener('message', receive); frame.remove(); rejectActive = null; };
          rejectActive = error => { cleanup(); reject(error); };
          addEventListener('message', receive);
          timer = setTimeout(() => { cleanup(); reject(new Error('측정 시간 초과 (3분)')); }, 180000);
          frame.src = '?' + params; viewport.replaceChildren(frame);
        });
        report.trials.push(result); show(report.trials); await save(report);
        if (!result.valid) throw new Error('유효하지 않은 측정: ' + result.invalid_reasons.join(', ') + '. 창을 앞에 두고 다시 시작하세요.');
      }
      report.status = 'complete'; report.finished_utc = new Date().toISOString();
      await save(report);
      status.textContent = '측정 완료. 결과가 로컬 report.json에 저장되었습니다.';
    } catch (error) {
      report.status = 'failed'; report.error = String(error.message || error);
      status.textContent = report.error;
      try { await save(report); } catch (saveError) { status.textContent += ' / ' + saveError.message; }
    } finally {
      activeFrame?.remove(); activeFrame = null; start.disabled = false; stop.disabled = true;
    }
  };
}

async function profileRunner(config, query) {
  document.body.classList.add('runner');
  const number = (name, min, max) => {
    const value = Number(query.get(name));
    if (!Number.isFinite(value) || value < min || value > max) throw new Error('Invalid trial ' + name);
    return value;
  };
  const trial = {scene: query.get('scene'), water: number('water', 0, 6), fps_limit: number('fps', 60, 144),
    repeat: number('repeat', 1, 3), width: number('width', 800, 1920), height: number('height', 600, 1080)};
  const warmup = number('warmup', 0, 10000), duration = number('duration', 1000, 60000);
  const refreshMs = number('refresh', 1, 100);
  const canvas = document.getElementById('canvas'), status = document.getElementById('status');
  canvas.width = trial.width; canvas.height = trial.height;
  let started = 0, frame = null, previousEnd = null, ready = false, finished = false;
  const samples = [], invalid = [], rafIntervals = [], gpu = {}, errors = [], logs = [];
  let previousRaf = null, rafId, errorSent = false;
  const round = n => +n.toFixed(4);
  const invalidate = reason => { if (ready && !finished) { invalid.push(reason); finished = true; } };
  document.addEventListener('visibilitychange', () => { if (document.hidden) invalidate('background tab'); });
  window.addEventListener('blur', () => invalidate('window lost focus'));
  const counter = (name, amount = 1) => { if (frame) frame.counters[name] = (frame.counters[name] || 0) + amount; };
  const wrap = (prototype, name, observe) => {
    if (!prototype?.[name]) return;
    const original = prototype[name];
    prototype[name] = function (...args) {
      const result = original.apply(this, args); observe.call(this, args, result); return result;
    };
  };
  wrap(globalThis.GPUDevice?.prototype, 'createTexture', ([desc]) => {
    counter('textures');
    if (desc.label === 'bb.blit.temp') {
      counter('temporary_textures');
      const size = desc.size, w = size.width ?? size[0], h = size.height ?? size[1] ?? 1;
      counter('temporary_texture_bytes', w * h * 4);
    }
  });
  for (const [prototype, method, name] of [
    [globalThis.GPUDevice?.prototype, 'createBindGroup', 'bind_groups'],
    [globalThis.GPUDevice?.prototype, 'createRenderPipeline', 'pipelines'],
    [globalThis.GPUCommandEncoder?.prototype, 'beginRenderPass', 'render_passes'],
    [globalThis.GPUCommandEncoder?.prototype, 'copyTextureToTexture', 'texture_copies'],
    [globalThis.GPURenderPassEncoder?.prototype, 'draw', 'draws'],
    [globalThis.GPURenderPassEncoder?.prototype, 'drawIndexed', 'indexed_draws'],
    [globalThis.GPUQueue?.prototype, 'submit', 'submissions'],
  ]) wrap(prototype, method, () => counter(name));
  wrap(globalThis.GPU?.prototype, 'requestAdapter', (args, promise) => {
    promise.then(adapter => {
      if (adapter) gpu.adapter = Object.fromEntries(['vendor', 'architecture', 'device', 'description', 'isFallbackAdapter']
        .map(key => [key, adapter.info?.[key] ?? adapter[key] ?? null]));
    });
  });
  wrap(globalThis.GPUAdapter?.prototype, 'requestDevice', (args, promise) => {
    promise.then(device => {
      device.addEventListener('uncapturederror', event => { errors.push(event.error.message); invalidate('WebGPU error'); });
    });
  });
  const raf = time => {
    if (ready && time - started >= warmup && !finished) {
      if (previousRaf !== null) rafIntervals.push(time - previousRaf);
      previousRaf = time;
    }
    if (!finished) rafId = requestAnimationFrame(raf);
  };
  const mark = request => {
    const [op, name] = request.split(':');
    const now = performance.now();
    if (op === 'scene') return trial.scene === 'menu' ? 0 : 1;
    if (op === 'water') return trial.water;
    if (op === 'fps') return trial.fps_limit;
    if (op === 'ready') {
      canvas.focus(); started = now; ready = true;
      if (document.hidden || !document.hasFocus()) invalidate('trial started without focus');
      rafId = requestAnimationFrame(raf);
      status.textContent = '측정 중'; return 0;
    }
    if (op === 'finished') {
      if (ready && now - started >= warmup + duration) finished = true;
      return finished ? 1 : 0;
    }
    if (op === 'done') { finished = true; return 0; }
    if (op === 'frame') {
      frame = {begin: now, phases: {}, starts: {}, counters: {
        temporary_textures: 0, temporary_texture_bytes: 0, textures: 0, bind_groups: 0,
        pipelines: 0, render_passes: 0, texture_copies: 0, draws: 0, indexed_draws: 0, submissions: 0}};
    } else if (op === 'start' && frame) frame.starts[name] = now;
    else if (op === 'stop' && frame) {
      if (!(name in frame.starts)) throw new Error('Unmatched phase ' + name);
      frame.phases[name] = (frame.phases[name] || 0) + now - frame.starts[name];
      delete frame.starts[name];
      if (name === 'flip') frame.present = now;
    } else if (op === 'end' && frame) {
      if (Object.keys(frame.starts).length) throw new Error('Unclosed profiler phase');
      if (frame.begin - started >= warmup && !finished) {
        if (!Number.isFinite(frame.present)) throw new Error('Frame did not complete Flip');
        samples.push({ms: round(now - frame.begin), interval: previousEnd === null ? null : round(frame.present - previousEnd),
          phases: Object.fromEntries(Object.entries(frame.phases).map(([key, value]) => [key, round(value)])),
          counters: frame.counters});
        previousEnd = frame.present;
      }
      frame = null;
    }
    return 0;
  };
  const log = text => { logs.push(String(text)); if (logs.length > 40) logs.shift(); };
  const fail = error => {
    if (errorSent) return;
    errorSent = true; finished = true;
    parent.postMessage({type: 's2-profile-result', error: String(error?.stack || error)}, location.origin);
  };
  window.addEventListener('error', event => fail(event.error || event.message));
  window.addEventListener('unhandledrejection', event => fail(event.reason));
  const words = value => {
    const n = BigInt(Math.floor(value));
    return Number(BigInt.asIntN(32, n)) + String.fromCharCode(31) + Number(BigInt.asIntN(32, n >> 32n));
  };
  window.Module = {canvas, noInitialRun: true, print: log, printErr: log, onAbort: fail,
    s2Profile: mark,
    s2Bridge: async request => {
      const [op, ...args] = request.split(String.fromCharCode(31));
      if (op === 'counter') return words(performance.now() * 1000);
      if (op === 'walltime') return words(performance.now() * 10000);
      if (op === 'width' || op === 'canvas-width') return trial.width;
      if (op === 'height' || op === 'canvas-height') return trial.height;
      // Focus loss aborts the measured trial instead of waiting indefinitely.
      if (op === 'focus' || op === 'sync' || op === 'control-focus') return 1;
      if (op.startsWith('search-')) return '';
      // Script-triggered file operations stay in the disposable MEMFS.
      const path = value => value.replaceAll('\\', '/');
      if (op === 'copy' || op === 'move' || op === 'stamp') {
        try {
          const FS = Module.FS;
          if (op === 'stamp') return words((FS.stat(path(args[0])).mtime.getTime() + 11644473600000) * 10000);
          if (op === 'move') FS.rename(path(args[0]), path(args[1]));
          else {
            if (Number(args[2]) && FS.analyzePath(path(args[1])).exists) return 0;
            FS.writeFile(path(args[1]), FS.readFile(path(args[0])));
          }
          return 1;
        } catch { return op === 'stamp' ? '' : 0; }
      }
      throw new Error('Unexpected profiler service: ' + op);
    },
    onRuntimeInitialized: async () => {
      try {
        const FS = Module.FS;
        FS.chdir('/game');
        FS.mkdirTree('/game/mods/Stranded II/saves');
        FS.writeFile('/game/core/modern.ini', `fps=${trial.fps_limit}\ndisplay=windowed\nresolution=custom\nrender_width=${trial.width}\nrender_height=${trial.height}\nlanguage=en\ndiagnostics=0\n`);
        await Module.callMain([]);
        finished = true; cancelAnimationFrame(rafId);
        const result = {...trial, ...profileSummary(samples, invalid, refreshMs),
          raf_interval_ms: profileStats(rafIntervals), adapter: gpu.adapter || null,
          user_agent: navigator.userAgent, device_pixel_ratio: devicePixelRatio,
          warmup_ms: warmup, measurement_ms: duration, elapsed_ms: performance.now() - started,
          webgpu_errors: errors, logs};
        parent.postMessage({type: 's2-profile-result', result}, location.origin);
      } catch (error) { fail(error); }
    }};
  for (const name of ['assets.js', 'profile.js']) await new Promise((resolve, reject) => {
    const script = document.createElement('script'); script.src = name;
    script.onload = resolve; script.onerror = () => reject(new Error('Cannot load ' + name));
    document.body.append(script);
  });
}
