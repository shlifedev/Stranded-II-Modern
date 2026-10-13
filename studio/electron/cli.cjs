'use strict';
const {spawn} = require('node:child_process');
const path = require('node:path');
const {root, python} = require('./python.cjs');
const child = spawn(python(), ['-X', 'utf8', path.join(root, 'scripts/studio.py'), ...process.argv.slice(2)], {stdio:'inherit', cwd:process.cwd()});
child.on('error', error => { console.error(error.message); process.exitCode = 1; });
child.on('exit', code => { process.exitCode = code ?? 1; });
