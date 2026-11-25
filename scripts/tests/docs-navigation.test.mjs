import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import { prepareDocs } from '../prepare-docs.mjs';
const require = createRequire(import.meta.url);
const navigation = require('../../docs/assets/version-selector.js');

test('version context supports project sites, root sites and explicit index pages', () => {
  for (const url of ['https://example.test/searchable-jpa/1.1.1/', 'https://example.test/searchable-jpa/1.1.1/index.html']) {
    const result = navigation.context(url);
    assert.equal(result.current, '1.1.1');
    assert.equal(result.base.href, 'https://example.test/searchable-jpa/');
  }
  assert.equal(navigation.context('https://example.test/snapshot/').current, 'snapshot');
  assert.equal(navigation.context('http://localhost:3000/'), null);
  assert.equal(navigation.context('https://example.test/searchable-jpa/'), null);
});

test('manifest validation excludes traversal, prereleases and duplicates and sorts numerically', () => {
  assert.deepEqual(navigation.versions({ releases: ['1.9.0', '1.10.0', '../evil', '1.10.0', '2.0.0-RC1', 42], snapshot: 'snapshot' }),
    ['snapshot', '1.10.0', '1.9.0']);
  assert.deepEqual(navigation.versions({ releases: [], snapshot: '//evil.test' }), []);
  assert.throws(() => navigation.versions({ releases: '1.0.0' }));
});

test('navigation preserves language, document and heading within the deployment prefix', () => {
  const base = new URL('https://example.test/searchable-jpa/');
  assert.equal(navigation.target(base, '1.10.0', '#/ko/basic-usage.md?id=search').href,
    'https://example.test/searchable-jpa/1.10.0/#/ko/basic-usage.md?id=search');
  assert.equal(navigation.route('#/ko/').file, 'ko/README.md');
  assert.equal(navigation.route('#/ko/../../secret').file, 'en/README.md');
  assert.throws(() => navigation.target(base, '../evil', '#/ko/'));
});

test('a deployment keeps the newest release of each line and gives every version the current shell', t => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'searchable-docs-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const put = (base, file, text) => {
    fs.mkdirSync(path.dirname(path.join(base, file)), { recursive: true });
    fs.writeFileSync(path.join(base, file), text);
  };
  const previous = path.join(root, 'previous'), built = path.join(root, 'built'), output = path.join(root, 'output');
  const html = '<html><head><title>Project Documentation</title></head><body>a shell of its own time</body></html>';
  for (const directory of [path.join(previous, 'snapshot'), path.join(previous, '1.9.0'), path.join(previous, '1.10.0'), path.join(previous, '2.0.0'), built]) {
    put(directory, 'index.html', html);
  }
  put(previous, '1.10.0/obsolete.md', 'old');
  put(previous, '2.0.0/ko/README.md', '# Home');
  put(built, 'new.md', 'new');
  assert.deepEqual(prepareDocs({ previous, built, output, version: '1.10.0' }), { releases: ['2.0.0', '1.10.0'], snapshot: 'snapshot' });
  assert.ok(fs.existsSync(path.join(output, 'snapshot', 'index.html')));
  assert.ok(!fs.existsSync(path.join(output, '1.9.0')), 'an older release of a line leaves the site');
  assert.ok(!fs.existsSync(path.join(output, '1.10.0', 'obsolete.md')));
  assert.ok(fs.existsSync(path.join(output, '1.10.0', 'new.md')));
  const shell = fs.readFileSync(new URL('../../docs/index.html', import.meta.url), 'utf8');
  for (const version of ['snapshot', '1.10.0', '2.0.0']) {
    assert.equal(fs.readFileSync(path.join(output, version, 'index.html'), 'utf8'), shell);
    assert.ok(fs.existsSync(path.join(output, version, 'assets', 'site', 'site.js')));
  }
  assert.match(fs.readFileSync(path.join(output, '2.0.0', 'assets', 'site-config.js'), 'utf8'), /SIMPLIX_FOLDER = \{"languages":\["ko"\]\}/);
  const redirect = fs.readFileSync(path.join(output, 'index.html'), 'utf8');
  assert.match(redirect, /location\.hash/);
  assert.match(redirect, /\.\/2\.0\.0\//);
  assert.match(redirect, /<title>Project Documentation<\/title>/);
  assert.throws(() => prepareDocs({ previous, built, output, version: '../outside' }));
});
