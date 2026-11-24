import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { config, pageNames, parse, projectRoot, readPage } from '../doc-markdown.mjs';
import { listingPages, sync } from '../doc-snippets.mjs';

// Java, HTTP and JSON listings are regions of files the build compiles or the test suite sends.

test('every marked listing equals its source', () => {
  const stale = listingPages().filter(file => {
    const text = fs.readFileSync(file, 'utf8');
    return sync(text) !== text;
  });
  assert.deepEqual(stale.map(file => path.relative(projectRoot, file)), [],
    'run node scripts/doc-snippets.mjs');
});

test('every Java, HTTP and JSON listing names the file it comes from', () => {
  const unsourced = [];
  for (const locale of config.locales) {
    for (const name of pageNames(locale)) {
      if (config.historicalPages.includes(name)) continue;
      const page = parse(readPage(locale, name));
      assert.equal(page.strayMarker, undefined, locale + '/' + name + ' has a marker without a listing');
      for (const fence of page.fences) {
        if (config.sourcedLanguages.includes(fence.language) && !fence.marker) {
          unsourced.push(locale + '/' + name + ':' + fence.line);
        }
      }
    }
  }
  assert.deepEqual(unsourced, []);
});
