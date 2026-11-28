var Module = {
  noInitialRun: true,
  onRuntimeInitialized() {
    const assert = require('node:assert/strict');
    const fs = Module.FS;
    const path = code => '/game/core/locales/' + code + '/ui.ttf';
    const text = code => fs.readFile(path(code), {encoding: 'utf8'});
    function writeByte(code, offset, value) {
      const stream = fs.open(path(code), 'r+');
      fs.write(stream, Uint8Array.of(value), 0, 1, offset);
      fs.close(stream);
    }
    assert.deepEqual(['de', 'en', 'ja', 'ko'].map(text), ['ABC', 'ABC', 'XYZ', 'DEF']);
    writeByte('en', 0, 90);
    assert.equal(text('de'), 'ABC');
    writeByte('de', 1, 90);
    assert.equal(text('en'), 'ZBC');
    console.log('Independent font files verified');
  }
};
