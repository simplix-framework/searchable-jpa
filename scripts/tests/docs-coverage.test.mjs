import { test } from 'node:test';
import assert from 'node:assert/strict';
import { config, codeText, parse, readPage } from '../doc-markdown.mjs';
import { ledger, namesOf, shows } from '../doc-ledger.mjs';

// A feature is covered when a guide page shows it as code and a reference page lists it, in every
// language. The names come from the sources, so a name added there fails here until a page has it.

const code = (locale, page) => codeText(parse(readPage(locale, page)));

function missing(names, page) {
  const gaps = [];
  for (const locale of config.locales) {
    const shown = code(locale, page);
    for (const name of names) if (!shows(shown, name)) gaps.push(locale + '/' + page + ': ' + name);
  }
  return gaps;
}

for (const group of ledger.groups) {
  test('every name of "' + group.id + '" has a guide and a reference entry', () => {
    const names = namesOf(group);
    const gaps = missing(names, group.reference);
    const guides = group.guides || {};
    const samples = group.guideSamples;
    const byGuide = new Map();
    for (const name of samples || names) {
      const page = guides[name] || group.guide;
      byGuide.set(page, [...(byGuide.get(page) || []), name]);
    }
    for (const [page, shown] of byGuide) gaps.push(...missing(shown, page));
    assert.deepEqual(gaps, []);
  });
}

for (const feature of ledger.features) {
  test('the guide shows "' + feature.id + '"', () => {
    assert.deepEqual(missing(feature.tokens, feature.guide), []);
  });
}

test('every internal name of the ledger states why it has no page', () => {
  for (const group of ledger.groups) {
    for (const [name, reason] of Object.entries(group.internal || {})) {
      assert.ok(reason && reason.length > 10, group.id + '.' + name);
    }
  }
});
