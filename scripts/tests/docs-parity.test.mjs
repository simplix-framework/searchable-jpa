import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { config, headingId, pageNames, parse, projectRoot, readPage } from '../doc-markdown.mjs';

// The pages of every locale are one document in several languages: the same pages, the same
// headings, listings, figures and tables, in the same order.

const [first, ...others] = config.locales;
const localeDir = locale => path.join(projectRoot, config.docs, locale);

test('every locale has the same pages', () => {
  for (const locale of others) assert.deepEqual(pageNames(locale), pageNames(first));
});

test('every page has the same structure in every locale', () => {
  const differences = [];
  for (const name of pageNames(first)) {
    const base = parse(readPage(first, name));
    for (const locale of others) {
      const page = parse(readPage(locale, name));
      const where = locale + '/' + name;
      const levels = each => each.headings.map(heading => heading.level).join('');
      if (levels(page) !== levels(base)) differences.push(where + ': heading levels');
      if (page.fences.length !== base.fences.length) {
        differences.push(where + ': ' + page.fences.length + ' listings, ' + base.fences.length + ' in ' + first);
      } else {
        page.fences.forEach((fence, index) => {
          const other = base.fences[index];
          if (fence.language !== other.language || fence.lines.join('\n') !== other.lines.join('\n')) {
            differences.push(where + ':' + fence.line + ': listing differs from ' + first);
          }
        });
      }
      if (page.images.join() !== base.images.join()) differences.push(where + ': figures');
      const shape = each => each.tables.map(table => table.rows + 'x' + table.columns).join();
      if (shape(page) !== shape(base)) differences.push(where + ': tables ' + shape(page) + ' against ' + shape(base));
    }
  }
  assert.deepEqual(differences, []);
});

test('every link and figure of a page resolves inside its locale', () => {
  const broken = [];
  for (const locale of config.locales) {
    const names = pageNames(locale);
    const ids = new Map(names.map(name => [name, parse(readPage(locale, name)).headings.map(h => headingId(h.text))]));
    for (const name of names) {
      const page = parse(readPage(locale, name));
      for (const image of page.images) {
        if (/^https?:/.test(image)) continue;
        if (!fs.existsSync(path.join(localeDir(locale), image))) broken.push(locale + '/' + name + ': ' + image);
      }
      for (const link of page.links) {
        if (!link.startsWith('./')) continue;
        const [target, anchor] = link.slice(2).split('#');
        if (!names.includes(target)) broken.push(locale + '/' + name + ': ' + link);
        else if (anchor && !ids.get(target).includes(decodeURIComponent(anchor))) {
          broken.push(locale + '/' + name + ': ' + link);
        }
      }
    }
  }
  assert.deepEqual(broken, []);
});

test('the sidebar of every locale lists every page once, in the same order', () => {
  const order = locale => [...fs.readFileSync(path.join(localeDir(locale), '_sidebar.md'), 'utf8')
    .matchAll(/\]\(\/[a-z]+\/([\w-]+\.md)\)/g)].map(match => match[1]);
  for (const locale of config.locales) {
    assert.deepEqual([...order(locale)].sort(), pageNames(locale), locale + ' sidebar');
  }
  for (const locale of others) assert.deepEqual(order(locale), order(first));
});
