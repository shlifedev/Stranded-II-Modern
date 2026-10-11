/* Browser services for the shared Blitz3D game. No gameplay is implemented here. */
'use strict';
const canvas = document.getElementById('canvas');
const cover = document.getElementById('cover');
const statusText = document.getElementById('status');
const search = document.getElementById('search');
const fullscreen = document.getElementById('fullscreen');
const inputHint = document.getElementById('input-hint');
const shellURL = new URL(document.currentScript.src, document.baseURI);
const deployment = shellURL.searchParams.get('v');
let started = false, menuActive = true, syncing = false, dirty = false, syncTimer, bootPromise;
let sequence = Promise.resolve(), lastWrite = Date.now(), revision = 0;
let searchBounds = [0, 0, 0, 0];
let pointerRequest;
let fullscreenPending = false, escapeLocked = false, keyboardSession = 0, returningToGame = false;
const redirects = new Map();
const separator = String.fromCharCode(31);
const persistent = '/persist/classic';

function assetURL(path) {
  const url = new URL(path, shellURL);
  if (deployment) url.searchParams.set('v', deployment);
  return url.href;
}

function log(message) {
  const line = String(message);
  const output = document.getElementById('log');
  output.textContent = (output.textContent + line + '\n').slice(-16000);
  console.log(line);
  if (location.hostname === '127.0.0.1' || location.hostname === 'localhost') {
    fetch('/__diagnostics', {method: 'POST', body: line.slice(0, 16000)}).catch(() => {});
  }
}

function fail(error) {
  log(error?.stack || error);
  const warning = document.getElementById('warning');
  warning.hidden = false;
  warning.textContent = 'Game or storage error: ' + (error?.message || error);
}

function sync(populate = false) {
  clearTimeout(syncTimer);
  // Never overlap IDBFS transactions. Save success is returned only after this resolves.
  const operation = sequence.catch(() => {}).then(() => new Promise((resolve, reject) => {
    const version = revision;
    syncing = true;
    Module.FS.syncfs(populate, error => {
      syncing = false;
      if (error) reject(error); else { dirty = revision !== version; resolve(); }
    });
  }));
  sequence = operation;
  return operation;
}

function changed() {
  ++revision;
  dirty = true;
  clearTimeout(syncTimer);
  syncTimer = setTimeout(() => sync().catch(fail), 300);
}

function absolute(path) {
  const parts = (path.startsWith('/') ? path : Module.FS.cwd() + '/' + path).replaceAll('\\', '/').split('/');
  const clean = [];
  for (const part of parts) {
    if (part === '..') clean.pop(); else if (part && part !== '.') clean.push(part);
  }
  return '/' + clean.join('/');
}

function filePath(path) {
  const full = absolute(path);
  return redirects.get(full.toLowerCase()) || full;
}

function exists(path) {
  try { Module.FS.stat(path); return true; } catch { return false; }
}
function mkdir(path) { Module.FS.mkdirTree(path); }
function timestamp(path) {
  lastWrite = Math.max(Date.now(), lastWrite + 1);
  Module.FS.utime(path, lastWrite, lastWrite);
}
function words(value) {
  const n = BigInt(Math.floor(value));
  return String(Number(BigInt.asIntN(32, n))) + separator + String(Number(BigInt.asIntN(32, n >> 32n)));
}

async function prepareStorage() {
  const FS = Module.FS;
  // A second tab must not restore an old snapshot and overwrite the first tab.
  if (navigator.locks) {
    await new Promise((resolve, reject) => {
      navigator.locks.request('stranded-ii-classic-storage', {ifAvailable: true}, lock => {
        if (!lock) { reject(new Error('Close the other Stranded II tab first.')); return; }
        resolve();
        return new Promise(() => {}); // The browser releases this lock when the page closes.
      }).catch(reject);
    });
  }
  mkdir('/persist');
  FS.mount(FS.filesystems.IDBFS, {}, '/persist');
  await sync(true);
  const defaults = await fetch(assetURL('defaults.json')).then(response => {
    if (!response.ok) throw new Error('Cannot load defaults.json');
    return response.json();
  });
  mkdir(persistent + '/saves');
  const saves = '/game/mods/Stranded II/saves';
  if (exists(saves)) FS.rmdir(saves);
  FS.symlink(persistent + '/saves', saves);
  for (const path of defaults.files) {
    const target = persistent + '/config/' + path.slice('/game/'.length);
    mkdir(target.slice(0, target.lastIndexOf('/')));
    if (!exists(target) && exists(path)) FS.writeFile(target, FS.readFile(path));
    for (const suffix of ['', '.tmp', '.bak', '.bak.tmp']) {
      const link = path + suffix, destination = target + suffix;
      if (exists(link)) FS.unlink(link);
      FS.symlink(destination, link);
      redirects.set(link.toLowerCase(), destination);
    }
  }
  // Direct legacy settings writes also become durable after closing the file.
  const close = FS.close.bind(FS);
  FS.close = stream => {
    const path = stream.path;
    const writable = (stream.flags & 3) !== 0 && path.startsWith('/persist/');
    close(stream);
    if (writable) { timestamp(path); changed(); }
  };
  const unlink = FS.unlink.bind(FS);
  FS.unlink = path => {
    const real = FS.lookupPath(path, {follow: true}).path;
    unlink(path);
    if (real.startsWith('/persist/')) changed();
  };
  FS.chdir('/game');
  await sync();
  log('Persistent storage ready (classic; independent of Windows saves).');
}

function focused() {
  return started && !document.hidden && document.hasFocus() &&
    (menuActive || document.pointerLockElement === canvas || document.activeElement === search);
}

function refreshInput() {
  const locked = document.pointerLockElement === canvas;
  // Focus loss pauses the shared engine, but never covers the running game.
  if (started) cover.hidden = true;
  fullscreen.hidden = !document.fullscreenEnabled || (started && locked);
  fullscreen.textContent = document.fullscreenElement ? 'Exit full screen' : 'Full screen';
  fullscreen.setAttribute('aria-pressed', String(Boolean(document.fullscreenElement)));
  fullscreen.title = 'Toggle full screen (Alt+Enter)';
  inputHint.hidden = !started || locked || document.activeElement === search || document.hidden || !document.hasFocus();
  inputHint.textContent = escapeLocked
    ? 'Click to enable controls · Esc: game menu · Hold Esc: release · Alt+Enter: full screen'
    : 'Click to enable controls · Alt+Enter: full screen';
}

function inputNotice(message) {
  if (started) { inputHint.textContent = message; inputHint.hidden = false; }
  else statusText.textContent = message;
}

async function toggleFullscreen() {
  if (!document.fullscreenEnabled || fullscreenPending) return;
  fullscreenPending = true;
  fullscreen.disabled = true;
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await document.documentElement.requestFullscreen();
  } catch (error) {
    inputNotice('Full screen was declined. You can keep playing in this window.');
    log('Full screen request declined: ' + (error?.message || error));
  } finally {
    fullscreenPending = false;
    fullscreen.disabled = false;
  }
}

async function fullscreenChanged() {
  const session = ++keyboardSession;
  escapeLocked = false;
  refreshInput();
  fitCanvas();
  if (!document.fullscreenElement) {
    navigator.keyboard?.unlock?.();
    return;
  }
  // Only capture Escape. Short presses reach the original game; holding it
  // still invokes the browser's exit gesture. Unsupported/denied locks are OK.
  if (navigator.keyboard?.lock) {
    try {
      await navigator.keyboard.lock(['Escape']);
      if (session === keyboardSession && document.fullscreenElement) escapeLocked = true;
    } catch (error) {
      log('Escape key capture unavailable: ' + (error?.message || error));
    }
    if (session === keyboardSession) refreshInput();
  }
}

async function lockPointer() {
  activateAudio();
  if (document.pointerLockElement === canvas) return true;
  if (pointerRequest) return pointerRequest;
  canvas.focus();
  pointerRequest = (async () => {
    try {
      await canvas.requestPointerLock();
      refreshInput();
      return document.pointerLockElement === canvas;
    } catch (error) {
      if (document.pointerLockElement === canvas) return true;
      // Escape/cooldown or a declined capture request is a recoverable input
      // state. Keep the game paused without reporting a game/storage failure.
      refreshInput();
      inputNotice('Click the game again to capture the mouse.');
      log('Pointer lock request declined: ' + (error?.message || error));
      return false;
    }
  })();
  try { return await pointerRequest; } finally { pointerRequest = undefined; }
}

function activateAudio() {
  // The pinned SDL2 backend keeps its Web Audio context on Module. Our return
  // click is consumed before SDL's auto-resume listener, so resume explicitly.
  const context = Module.SDL2?.audioContext;
  if (context?.state === 'suspended') {
    context.resume().catch(error => log('Audio activation declined: ' + (error?.message || error)));
  }
}

function searchPosition(values) {
  const [x, y, width, height] = values.map(Number);
  searchBounds = [x, y, width, height];
  const rect = canvas.getBoundingClientRect();
  const sx = rect.width / canvas.width, sy = rect.height / canvas.height;
  Object.assign(search.style, {left: x * sx + 'px', top: y * sy + 'px', width: width * sx + 'px', height: height * sy + 'px'});
}

function fitCanvas() {
  const scale = Math.min(window.innerWidth / canvas.width, window.innerHeight / canvas.height);
  canvas.style.width = canvas.width * scale + 'px';
  canvas.style.height = canvas.height * scale + 'px';
  canvas.parentElement.style.width = canvas.style.width;
  searchPosition(searchBounds);
}

// SDL can change the drawing-buffer resolution when applying game settings.
// CSS scales the whole image uniformly; never change the buffer on a resize.
if (typeof MutationObserver !== 'undefined') {
  new MutationObserver(fitCanvas).observe(canvas, {attributes: true, attributeFilter: ['width', 'height']});
}
window.addEventListener('resize', fitCanvas);
fitCanvas();

async function bridge(request) {
  const [op, ...args] = request.split(separator);
  const FS = Module.FS;
  switch (op) {
    case 'counter': return words(performance.now() * 1000);
    case 'walltime': return words(performance.now() * 10000);
    case 'focus': {
      if (args[0] === 'menu' || args[0] === 'play') {
        const next = args[0] === 'menu';
        if (menuActive !== next) { menuActive = next; refreshInput(); }
      }
      return focused() ? 1 : 0;
    }
    case 'width': return Math.min(1920, Math.max(800, window.innerWidth));
    case 'height': return Math.min(1080, Math.max(600, window.innerHeight));
    case 'canvas-width': return canvas.width;
    case 'canvas-height': return canvas.height;
    case 'sync':
      try { await sync(); return 1; } catch (error) { fail(error); return 0; }
    case 'copy':
      try {
        const destination = filePath(args[1]);
        if (Number(args[2]) && exists(destination)) return 0;
        FS.writeFile(destination, FS.readFile(filePath(args[0])));
        timestamp(destination);
        return 1;
      } catch (error) { fail(error); return 0; }
    case 'move': {
      const source = filePath(args[0]), destination = filePath(args[1]);
      let previous, staged;
      try {
        staged = FS.readFile(source);
        if (exists(destination)) previous = FS.readFile(destination);
        // Both resolved paths belong to the same IDBFS mount, including config staging links.
        FS.rename(source, destination);
        timestamp(destination);
        dirty = true;
        await sync();
        return 1;
      } catch (error) {
        // A failed transaction must not leave the active in-memory save replaced.
        if (staged) {
          try {
            FS.writeFile(source, staged);
            if (previous) FS.writeFile(destination, previous);
            else if (exists(destination)) FS.unlink(destination);
          } catch (restoreError) { fail(restoreError); }
        }
        clearTimeout(syncTimer);
        fail(error);
        return 0;
      }
    }
    case 'stamp':
      try { return words(FS.stat(filePath(args[0])).mtime.getTime() * 10000); } catch { return ''; }
    case 'search-open': search.hidden = false; search.value = args[0]; return 1;
    case 'search-close': search.hidden = true; canvas.focus(); refreshInput(); return 1;
    case 'search-position': searchPosition(args); return 1;
    case 'search-text': return search.value;
    case 'search-set': search.value = args[0]; return 1;
    case 'search-focus':
      if (Number(args[0]) === 2) { search.focus(); document.exitPointerLock(); }
      else { canvas.focus(); refreshInput(); }
      return 1;
    case 'control-focus': return document.activeElement === search ? 2 : 1;
    default: throw new Error('Unknown browser service: ' + op);
  }
}

var Module = {
  canvas, noInitialRun: true, s2Bridge: bridge, locateFile: assetURL,
  s2SearchClick: (x, y) => {
    const [left, top, width, height] = searchBounds;
    if (!search.hidden && x >= left && x < left + width && y >= top && y < top + height) {
      search.focus(); document.exitPointerLock();
    }
  },
  print: log, printErr: log, onAbort: fail,
  setStatus: text => { if (!started) statusText.textContent = text; },
  onRuntimeInitialized: bootGame
};

function bootGame() {
  if (!bootPromise) bootPromise = (async () => {
    await prepareStorage();
    started = true;
    refreshInput();
    log('Starting shared Stranded II source.');
    await Module.callMain([]);
    await sync();
    started = false;
    document.exitPointerLock();
    cover.hidden = false;
    statusText.textContent = 'Game closed. Refresh this page to start again.';
    refreshInput();
  })().catch(fail);
  return bootPromise;
}

for (const event of ['pointerlockchange', 'visibilitychange']) document.addEventListener(event, refreshInput);
for (const event of ['blur', 'focus']) window.addEventListener(event, refreshInput);
document.addEventListener('fullscreenchange', fullscreenChanged);
fullscreen.addEventListener('click', toggleFullscreen);
for (const type of ['keydown', 'keyup']) {
  window.addEventListener(type, event => {
    if (!event.isComposing && event.altKey && event.code === 'Enter') {
      event.preventDefault(); event.stopImmediatePropagation();
      if (type === 'keydown' && !event.repeat) return toggleFullscreen();
    } else if (escapeLocked && event.code === 'Escape' && document.pointerLockElement === canvas) {
      event.preventDefault(); // Let SDL receive Escape for menus and skipping the intro.
    }
  }, true);
}
// Consume the entire return click before SDL sees it: resuming must not also
// attack, select a menu item, or leave a held mouse button in the game.
canvas.addEventListener('mousedown', event => {
  if (started) activateAudio();
  if (started && document.pointerLockElement !== canvas) {
    returningToGame = true;
    event.preventDefault(); event.stopImmediatePropagation();
  }
}, true);
window.addEventListener('mouseup', event => {
  if (returningToGame) {
    event.preventDefault(); event.stopImmediatePropagation();
    if (event.target !== canvas) returningToGame = false;
  }
}, true);
canvas.addEventListener('click', event => {
  if (started && (returningToGame || document.pointerLockElement !== canvas)) {
    returningToGame = false;
    event.preventDefault(); event.stopImmediatePropagation();
    if (event.button === 0) return lockPointer();
  }
}, true);
refreshInput();
window.addEventListener('beforeunload', event => {
  if (dirty || syncing) { event.preventDefault(); event.returnValue = ''; }
});
document.addEventListener('visibilitychange', () => { if (document.hidden && dirty) sync().catch(fail); });
canvas.addEventListener('contextmenu', event => event.preventDefault());
search.addEventListener('keydown', event => {
  event.stopPropagation();
  if (!event.isComposing && ['Enter', 'Escape', 'Tab'].includes(event.key)) {
    event.preventDefault(); lockPointer().catch(fail);
  }
});
for (const event of ['keyup', 'keypress']) search.addEventListener(event, e => e.stopPropagation());
window.addEventListener('error', event => fail(event.error || event.message));
window.addEventListener('unhandledrejection', event => fail(event.reason));

async function load() {
  if (!navigator.gpu || typeof WebAssembly.Suspending !== 'function') {
    throw new Error('This preview requires desktop Chrome or Edge with WebGPU and JSPI support.');
  }
  const adapter = await navigator.gpu.requestAdapter();
  if (!adapter) throw new Error('No WebGPU adapter is available.');
  for (const source of ['assets.js', 'game.js']) {
    await new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = assetURL(source); script.onload = resolve; script.onerror = () => reject(new Error('Cannot load ' + source));
      document.body.append(script);
    });
  }
}
load().catch(fail);
