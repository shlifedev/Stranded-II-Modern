/* Exercise the browser bridge against Emscripten's real virtual filesystem.
 * DOM and IndexedDB transactions are test doubles; this is NOT a browser test. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

module.exports = async function testStorage(FS, shellPath, savedLanguage = '') {
  assert.equal(typeof FS.filesystems.IDBFS?.mount, 'function',
    'the linked runtime must include IDBFS before storage transactions are simulated');
  function target(properties = {}) {
    const handlers = new Map();
    return Object.assign({
      addEventListener(type, callback, capture = false) {
        if (!handlers.has(type)) handlers.set(type, []);
        handlers.get(type).push({callback, capture});
      },
      async emit(type, properties = {}) {
        const event = Object.assign({type, target: this, button: 0, defaultPrevented: false, stopped: false,
          preventDefault() { this.defaultPrevented = true; },
          stopPropagation() {}, stopImmediatePropagation() { this.stopped = true; }
        }, properties);
        for (const {callback} of [...(handlers.get(type) || [])].sort((a, b) => Number(b.capture) - Number(a.capture))) {
          await callback(event);
          if (event.stopped) break;
        }
        return event;
      }
    }, properties);
  }
  const elements = new Map();
  const element = id => {
    if (!elements.has(id)) elements.set(id, target({
      textContent: '', value: '', style: {}, parentElement: {style: {}}, hidden: false, width: 1280, height: 720,
      setAttribute(name, value) { this[name] = value; },
      focus() { context.document.activeElement = this; },
      replaceChildren(...options) { this.options = options; this.value = options[0]?.value || ''; },
      add(option) { this.options.push(option); },
      getBoundingClientRect: () => ({width: 1280, height: 720})
    }));
    return elements.get(id);
  };
  const messages = [];
  let downloaded;
  const link = {click() { downloaded = {name: this.download, url: this.href}; }};
  // Load the real shared overlay bridge before the shell.

  const context = vm.createContext({
    document: target({body:{append(){}},getElementById: element, createElement: tag => tag === 'a' ? link : element("textarea"), hasFocus: () => true,
      exitPointerLock() { this.pointerLockElement = null; },
      baseURI: 'https://unit-test/Stranded-II-Modern/',
      currentScript: {src: 'https://unit-test/Stranded-II-Modern/shell.js?v=fixture'}}),
    window: target({innerWidth: 1920, innerHeight: 1080}), location: {hostname: 'unit-test'},
    navigator: {language: 'ko-KR', gpu: {requestAdapter: () => new Promise(() => {})}},
    URL, Blob, TextDecoder, Option: class { constructor(text, value) { Object.assign(this, {text, value}); } },
    WebAssembly, performance, setTimeout, clearTimeout,
    console: {log: message => messages.push(message)},
    fetch: async () => ({ok: true, json: async () => ({files: [
      '/game/core/modern.ini', '/game/mods/Stranded II/sys/settings.cfg'
    ], languages: [{code: 'en', name: 'English'}, {code: 'ko', name: '한국어'}, {code: 'pt-BR', name: 'Português'}]})})
  });
  vm.runInContext(fs.readFileSync(require('node:path').join(require('node:path').dirname(shellPath),'debugui.js'),'utf8'),context);
  FS.mkdirTree('/game/core'); FS.mkdirTree('/game/mods/Stranded II/sys');
  FS.writeFile('/game/core/modern.ini', 'language=en\nfps=144\n');
  let failNext = false, inFlight = 0, maximum = 0;
  const durable = new Map();
  function snapshot(path, result = new Map()) {
    for (const name of FS.readdir(path)) {
      if (name === '.' || name === '..') continue;
      const file = path + '/' + name, stat = FS.stat(file);
      if (FS.isDir(stat.mode)) snapshot(file, result);
      else result.set(file, Buffer.from(FS.readFile(file)));
    }
    return result;
  }
  FS.syncfs = (populate, callback) => {
    ++inFlight; maximum = Math.max(maximum, inFlight);
    setTimeout(() => {
      --inFlight;
      if (failNext) { failNext = false; callback(new Error('simulated quota/transaction failure')); return; }
      if (populate && savedLanguage) {
        FS.mkdirTree('/persist/classic/config/core');
        FS.writeFile('/persist/classic/config/core/modern.ini', 'language=' + savedLanguage + '\nfps=165\n');
      } else if (!populate) {
        durable.clear();
        for (const pair of snapshot('/persist')) durable.set(...pair);
      }
      callback(null);
    }, 5);
  };
  vm.runInContext(fs.readFileSync(shellPath, 'utf8'), context, {filename: shellPath});
  assert.equal(elements.has('language'), false, 'language selection belongs to the original game');
  assert.equal(elements.has('start'), false, 'no separate start gate');
  assert.equal(context.Module.locateFile('game.wasm'), 'https://unit-test/Stranded-II-Modern/game.wasm?v=fixture');
  assert.equal(context.Module.locateFile('assets.data'), 'https://unit-test/Stranded-II-Modern/assets.data?v=fixture');
  assert.equal(element('canvas').style.width, '1920px');
  assert.equal(element('canvas').style.height, '1080px');
  context.window.innerWidth = 800; context.window.innerHeight = 800;
  vm.runInContext('fitCanvas()', context);
  assert.equal(element('canvas').style.width, '800px');
  assert.equal(element('canvas').style.height, '450px');
  const canvas = element('canvas'), warning = element('warning');
  warning.hidden = true;
  let captures = 0;
  canvas.requestPointerLock = async () => {
    ++captures;
    throw Object.assign(new Error('Escape cooldown'), {name: 'SecurityError'});
  };
  assert.equal(await vm.runInContext('lockPointer()', context), false);
  assert.equal(warning.hidden, true, 'capture rejection is not a storage/game error');
  assert.equal(element('cover').hidden, false);
  assert.equal(vm.runInContext('started', context), false);
  let finishCapture;
  canvas.requestPointerLock = () => {
    ++captures;
    return new Promise(resolve => { finishCapture = resolve; });
  };
  const capture = vm.runInContext('lockPointer()', context);
  const repeatedCapture = vm.runInContext('lockPointer()', context);
  assert.equal(captures, 2, 'only one browser request may be in flight');
  context.document.pointerLockElement = canvas;
  finishCapture();
  assert.equal(await capture, true);
  assert.equal(await repeatedCapture, true);
  assert.equal(await vm.runInContext('lockPointer()', context), true);
  assert.equal(captures, 2, 'an acquired lock is not requested again');
  vm.runInContext('started = true; refreshInput()', context);
  assert.equal(element('cover').hidden, true);
  assert.equal(await context.Module.s2Bridge('focus'), 1);
  context.document.pointerLockElement = null;
  assert.equal(await context.Module.s2Bridge('focus\x1fmenu'), 1, 'the original menu renders before capture');
  assert.equal(await context.Module.s2Bridge('focus\x1fplay'), 0, 'entering gameplay without capture pauses');
  await context.document.emit('pointerlockchange');
  assert.equal(element('cover').hidden, true, 'Escape must never bring back the start/resume cover');
  assert.equal(element('input-hint').hidden, false);
  assert.equal(await context.Module.s2Bridge('focus'), 0, 'unlocked mouse must not drive the camera');
  context.document.hidden = true;
  await context.document.emit('visibilitychange');
  assert.equal(element('cover').hidden, true);
  context.document.hidden = false;
  context.document.hasFocus = () => false;
  await context.window.emit('blur');
  assert.equal(await context.Module.s2Bridge('focus'), 0);
  assert.equal(element('cover').hidden, true);
  context.document.hasFocus = () => true;
  await context.window.emit('focus');
  assert.equal(captures, 2, 'focus return must not request a lock without a click');
  context.document.activeElement = element('search');
  assert.equal(await context.Module.s2Bridge('focus'), 1, 'recipe search can run without pointer lock');
  canvas.focus();
  canvas.requestPointerLock = async () => { ++captures; throw new Error('Escape cooldown'); };
  await vm.runInContext('lockPointer()', context);
  assert.equal(warning.hidden, true);
  assert.equal(element('cover').hidden, true, 'a rejected return click cannot cover the game');
  let gameMouseEvents = 0;
  for (const type of ['mousedown', 'click']) canvas.addEventListener(type, () => ++gameMouseEvents);
  context.window.addEventListener('mouseup', () => ++gameMouseEvents);
  canvas.requestPointerLock = async () => { ++captures; context.document.pointerLockElement = canvas; };
  let audioResumes = 0;
  context.Module.SDL2 = {audioContext: {state: 'suspended', async resume() { ++audioResumes; this.state = 'running'; }}};
  await canvas.emit('mousedown');
  await context.window.emit('mouseup', {target: canvas});
  await canvas.emit('click');
  await vm.runInContext('pointerRequest', context);
  assert.equal(context.document.pointerLockElement, canvas);
  assert.equal(gameMouseEvents, 0, 'the return click must not also attack or activate a menu');
  assert.equal(element('input-hint').hidden, true);
  assert.equal(audioResumes, 1, 'consuming the activation click must still enable SDL audio');
  await canvas.emit('mousedown');
  await context.window.emit('mouseup', {target: canvas});
  await canvas.emit('click');
  assert.equal(gameMouseEvents, 3, 'normal captured clicks must reach the game');

  const fullscreen = element('fullscreen');
  assert.equal(fullscreen.hidden, true, 'unsupported full screen must not show a dead button');
  context.document.fullscreenEnabled = true;
  let fullscreenRequests = 0, keyboardUnlocks = 0;
  const keyboardLocks = [];
  context.navigator.keyboard = {
    lock: async keys => { keyboardLocks.push(Array.from(keys)); },
    unlock: () => { ++keyboardUnlocks; }
  };
  context.document.documentElement = {
    async requestFullscreen() {
      ++fullscreenRequests;
      context.document.fullscreenElement = this;
      await context.document.emit('fullscreenchange');
    }
  };
  context.document.exitFullscreen = async () => {
    context.document.fullscreenElement = null;
    await context.document.emit('fullscreenchange');
  };
  context.document.pointerLockElement = null;
  await context.document.emit('pointerlockchange');
  assert.equal(fullscreen.hidden, false);
  await fullscreen.emit('click');
  assert.deepEqual(keyboardLocks, [['Escape']], 'only Escape is captured, not system shortcuts');
  assert.equal(fullscreen['aria-pressed'], 'true');
  context.document.pointerLockElement = canvas;
  await context.document.emit('pointerlockchange');
  let gameEscapeEvents = 0;
  context.window.addEventListener('keydown', event => { if (event.code === 'Escape') ++gameEscapeEvents; });
  const escape = await context.window.emit('keydown', {code: 'Escape'});
  assert.equal(escape.defaultPrevented, true);
  assert.equal(gameEscapeEvents, 1, 'short Escape must still reach the original game');
  assert.equal(await context.Module.s2Bridge('focus'), 1);
  await context.window.emit('keydown', {code: 'Enter', altKey: true, repeat: true});
  assert.equal(fullscreen['aria-pressed'], 'true', 'holding Alt+Enter cannot toggle repeatedly');
  const shortcut = await context.window.emit('keydown', {code: 'Enter', altKey: true});
  assert.equal(shortcut.stopped, true, 'the fullscreen shortcut must not activate game input');
  assert.equal(context.document.fullscreenElement, null);
  assert.equal(keyboardUnlocks, 1);
  assert.equal(vm.runInContext('escapeLocked', context), false);
  context.navigator.keyboard.lock = async () => { throw new Error('permission denied'); };
  await fullscreen.emit('click');
  assert.equal(Boolean(context.document.fullscreenElement), true, 'keyboard rejection must not prevent fullscreen');
  assert.equal(vm.runInContext('escapeLocked', context), false);
  assert.equal(warning.hidden, true);
  assert.equal((await context.window.emit('keydown', {code: 'Escape'})).defaultPrevented, false);
  await context.document.exitFullscreen();
  let finishKeyboardLock;
  context.navigator.keyboard.lock = () => new Promise(resolve => { finishKeyboardLock = resolve; });
  const entering = fullscreen.emit('click');
  await context.document.exitFullscreen();
  finishKeyboardLock(); await entering;
  assert.equal(vm.runInContext('escapeLocked', context), false, 'a late keyboard grant cannot restore an exited session');
  context.document.documentElement.requestFullscreen = async () => { throw new Error('permission denied'); };
  await fullscreen.emit('click');
  assert.equal(fullscreen.disabled, false);
  assert.equal(warning.hidden, true, 'fullscreen rejection is not a game/storage error');
  assert.equal(element('cover').hidden, true);
  assert.equal(fullscreenRequests, 3);
  context.document.pointerLockElement = null;
  vm.runInContext('started = false; menuActive = true', context);
  assert.equal(vm.runInContext('words(4294967295)', context), '-1' + String.fromCharCode(31) + '0');
  assert.equal(vm.runInContext('words(4294967296)', context), '0' + String.fromCharCode(31) + '1');
  let grantStorage;
  if (!savedLanguage) {
    context.navigator.locks = {request(name, hold) {
      assert.equal(name, 'stranded-ii-classic-storage');
      grantStorage = hold;
      return new Promise(() => {});
    }};
  }
  context.Module.FS = FS;
  let mainCalls = 0, exitGame, mainReady;
  const mainStarted = new Promise(resolve => { mainReady = resolve; });
  context.Module.callMain = () => {
    ++mainCalls;
    assert.equal(FS.cwd(), '/game', 'storage is prepared before engine startup');
    mainReady();
    return new Promise(resolve => { exitGame = resolve; });
  };
  const beforeBootCaptures = captures;
  const boot = context.Module.onRuntimeInitialized();
  assert.equal(context.Module.onRuntimeInitialized(), boot, 'runtime initialization must start only once');
  if (!savedLanguage) {
    assert.equal(mainCalls, 0);
    assert.throws(() => FS.stat('/persist'), 'storage must not be mounted while another tab owns it');
    context.Module.setStatus('');
    assert.match(element('status').textContent, /another tab/);
    assert.equal(warning.hidden, true);
    grantStorage();
  }
  await Promise.race([mainStarted, boot.then(() => {
    throw new Error('Engine did not start: ' + warning.textContent);
  })]);
  assert.equal(mainCalls, 1);
  assert.equal(captures, beforeBootCaptures, 'autostart does not request pointer lock');
  assert.equal(element('cover').hidden, true, 'the original loading/menu replaces the HTML loader');
  assert.equal(await context.Module.s2Bridge('focus\x1fmenu'), 1);
  assert.equal(FS.readFile('/game/core/modern.ini', {encoding: 'utf8'}), savedLanguage
    ? 'language=' + savedLanguage + '\nfps=165\n' : 'language=en\nfps=144\n');
  assert.equal(element('fullscreen').textContent, 'Full screen', 'HTML stays English in a Korean browser');
  assert.throws(() => FS.stat('/game/mods/Stranded II/sys/settings.cfg'));
  FS.writeFile('/game/mods/Stranded II/sys/settings.cfg', 'volume=0.5');
  FS.chdir('/game/mods/Stranded II');
  const bridge = context.Module.s2Bridge, sep = String.fromCharCode(31);
  const request = (name, ...args) => bridge([name, ...args].join(sep));
  FS.writeFile('saves/test.sav', 'previous save');
  await request('sync');
  assert.equal(durable.get('/persist/classic/config/mods/Stranded II/sys/settings.cfg').toString(), 'volume=0.5');
  FS.writeFile('saves/test.sav.tmp', 'new save');
  assert.equal(await request('copy', 'saves\\test.sav', 'saves\\test.sav.bak.tmp', '0'), 1);
  assert.equal(await request('move', 'saves\\test.sav.bak.tmp', 'saves\\test.sav.bak'), 1);
  assert.equal(await request('move', 'saves\\test.sav.tmp', 'saves\\test.sav'), 1);
  assert.equal(durable.get('/persist/classic/saves/test.sav').toString(), 'new save');
  assert.equal(FS.readFile('SAVES/TEST.SAV', {encoding: 'utf8'}), 'new save');
  assert.equal(durable.get('/persist/classic/saves/test.sav.bak').toString(), 'previous save');
  FS.writeFile('saves/test.sav.tmp', 'pending replacement');
  const realReadFile = FS.readFile;
  const beforeMove = FS.stat('saves/test.sav.tmp').mtime.getTime();
  FS.readFile = (path, options) => {
    if (path.endsWith('/saves/test.sav')) throw new Error('simulated backup read failure');
    return realReadFile(path, options);
  };
  try {
    assert.equal(await request('move', 'saves/test.sav.tmp', 'saves/test.sav'), 0);
  } finally {
    FS.readFile = realReadFile;
  }
  assert.equal(FS.readFile('saves/test.sav', {encoding: 'utf8'}), 'new save');
  assert.equal(FS.readFile('saves/test.sav.tmp', {encoding: 'utf8'}), 'pending replacement');
  assert.equal(FS.stat('saves/test.sav.tmp').mtime.getTime(), beforeMove);
  await request('sync');
  assert.equal(durable.get('/persist/classic/saves/test.sav').toString(), 'new save');
  FS.writeFile('saves/test.sav.tmp', 'failed replacement');
  failNext = true;
  assert.equal(await request('move', 'saves/test.sav.tmp', 'saves/test.sav'), 0);
  assert.equal(FS.readFile('saves/test.sav', {encoding: 'utf8'}), 'new save');
  assert.equal(durable.get('/persist/classic/saves/test.sav').toString(), 'new save');
  assert.equal(FS.readFile('saves/test.sav.tmp', {encoding: 'utf8'}), 'failed replacement');
  FS.writeFile('saves/first.sav.tmp', 'first save attempt');
  failNext = true;
  assert.equal(await request('move', 'saves/first.sav.tmp', 'saves/first.sav'), 0);
  assert.throws(() => FS.stat('saves/first.sav'));
  assert.equal(FS.readFile('saves/first.sav.tmp', {encoding: 'utf8'}), 'first save attempt');
  FS.writeFile('/game/core/modern.ini.tmp', 'language=de\nfps=120\n');
  assert.equal(await request('move', '/game/core/modern.ini.tmp', '/game/core/modern.ini'), 1);
  assert.equal(FS.readFile('/game/core/modern.ini', {encoding: 'utf8'}), 'language=de\nfps=120\n');
  assert.equal(FS.isLink(FS.lstat('/game/core/modern.ini').mode), true);
  assert.equal(FS.isLink(FS.lstat('/game/core/modern.ini.tmp').mode), true);
  await Promise.all([request('sync'), request('sync'), request('sync')]);
  assert.equal(maximum, 1, 'persistent transactions must be serialized');
  assert(messages.some(message => String(message).includes('simulated quota')));
  assert.deepEqual((await request('stamp', 'saves/test.sav')).split(sep).map(Number).map(Number.isInteger), [true, true]);
  FS.unlink('saves/test.sav'); await request('sync');
  assert.equal(durable.has('/persist/classic/saves/test.sav'), false);
  const panel = element('save-files'), picker = element('save-import'), select = element('save-slot');
  assert.equal(panel.hidden, false);
  panel.open = true;
  await panel.emit('toggle');
  assert.equal(await request('focus', 'menu'), 0, 'pause while the player manages saves');
  assert.equal(element('save-download').disabled, true, 'backup and staging files stay out of the list');
  const save = Buffer.alloc(22000);
  save.write('### Stranded II Mapfile\r\nversion\r\ndate\r\ntime\r\ndefault\r\nsav\r\n');
  save.write('### EOF Map File\r\nwww.unrealsoftware.de', save.length - 50);
  const importFile = async (name, bytes, size = bytes.length) => {
    picker.files = [{name, size, arrayBuffer: async () => Uint8Array.from(bytes).buffer}];
    await picker.emit('change');
    assert.equal(picker.disabled, false);
    return element('save-transfer-status').textContent;
  };
  const name = '섬.sav';
  assert.match(await importFile(name, save), /Imported/);
  assert.deepEqual(durable.get('/persist/classic/saves/' + name), save);
  assert.equal(select.value, name);
  await element('save-download').emit('click');
  assert.equal(downloaded.name, name);
  assert.deepEqual(Buffer.from(await (await fetch(downloaded.url)).arrayBuffer()), save);

  const second = Buffer.from(save);
  second[1000] = 123;
  assert.match(await importFile('섬.SAV', second), /Imported/);
  assert.equal(select.value, '섬 (2).sav');
  assert.deepEqual(durable.get('/persist/classic/saves/' + name), save);
  assert.deepEqual(durable.get('/persist/classic/saves/섬 (2).sav'), second);
  for (const [invalid, bytes, length] of [
    ['partial.sav', save.subarray(0, 100)],
    ['wrong.sav', Buffer.alloc(save.length)],
    ['too-large.sav', save, 128 * 1024 * 1024 + 1],
    ['sub/folder.sav', save],
    ['a.sav.tmp', save]
  ]) assert.match(await importFile(invalid, bytes, length), /Import failed/);
  const missingTrailer = Buffer.from(save);
  missingTrailer.fill(0, missingTrailer.length - 80);
  assert.match(await importFile('truncated.sav', missingTrailer), /Import failed/);
  failNext = true;
  assert.match(await importFile('retry.sav', save), /Import failed/);
  assert.throws(() => FS.stat('saves/retry.sav'));
  assert.equal(durable.has('/persist/classic/saves/retry.sav'), false);
  assert.match(await importFile('retry.sav', save), /Imported/);
  panel.open = false;
  await panel.emit('toggle');
  assert.equal(await request('focus', 'menu'), 1);
  exitGame(); await boot;
  assert.equal(element('cover').hidden, false);
  assert.equal(element('status').textContent, 'Game closed. Refresh this page to start again.');
  await context.Module.onRuntimeInitialized();
  assert.equal(mainCalls, 1, 'exiting does not restart the engine in the same runtime');
  console.log('Bridge startup/input/fullscreen/storage checks passed (' + (savedLanguage || 'new English settings') + '; real VFS, simulated DOM/persistence; no browser).');
};
