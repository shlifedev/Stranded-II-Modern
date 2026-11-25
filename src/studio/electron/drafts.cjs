'use strict';
const {createHash, randomUUID} = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

function openDraftStore(profile) {
  const directory = path.join(profile, 'authoring-drafts');
  const legacyFile = path.join(profile, 'authoring-drafts.json');
  const legacy = fs.existsSync(legacyFile) ? JSON.parse(fs.readFileSync(legacyFile, 'utf8')) : {};

  return function access(action, key, value) {
    if (typeof key !== 'string' || !key.startsWith('studio.')) throw new Error('Invalid storage key');
    const name = createHash('sha256').update(key).digest('hex');
    const file = path.join(directory, name + '.json');
    if (action === 'get') {
      try {
        return JSON.parse(fs.readFileSync(file, 'utf8'));
      } catch (error) {
        if (error.code !== 'ENOENT') throw error;
        return legacy[key] ?? null;
      }
    }
    if (action === 'remove') {
      if (!Object.hasOwn(legacy, key)) {
        fs.rmSync(file, {force:true});
        return;
      }
      // A tombstone prevents a discarded legacy draft from returning after restart.
      value = null;
    } else if (action !== 'set' || typeof value !== 'string') {
      throw new Error('Invalid storage action');
    }
    fs.mkdirSync(directory, {recursive:true});
    const temporary = file + '.' + randomUUID() + '.tmp';
    try {
      fs.writeFileSync(temporary, JSON.stringify(value), {flag:'wx'});
      fs.renameSync(temporary, file);
    } finally {
      fs.rmSync(temporary, {force:true});
    }
  };
}

module.exports = {openDraftStore};
