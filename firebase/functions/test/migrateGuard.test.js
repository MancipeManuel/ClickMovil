const test = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const path = require('node:path');

test('la migración se niega a correr fuera del emulador', () => {
  const env = { ...process.env };
  delete env.FIRESTORE_EMULATOR_HOST;
  const result = spawnSync(process.execPath, [path.join(__dirname, '..', 'migrateSellerIds.js')], {
    env,
    encoding: 'utf8',
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /emulador/);
});
