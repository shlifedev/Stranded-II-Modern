'use strict';
const path = require('node:path');
const fs = require('node:fs');
const root = path.resolve(__dirname, '../..');
function python() {
  if (process.env.S2_STUDIO_PYTHON) return process.env.S2_STUDIO_PYTHON;
  const local = path.join(root, '.venv', process.platform === 'win32' ? 'Scripts/python.exe' : 'bin/python');
  return fs.existsSync(local) ? local : process.platform === 'win32' ? 'python' : 'python3';
}
module.exports = {root, python};
