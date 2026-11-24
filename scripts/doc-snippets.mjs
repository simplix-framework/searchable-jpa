import fs from 'node:fs';
import path from 'node:path';
import { closes, config, opens, projectRoot, pageNames } from './doc-markdown.mjs';

// A listing in the documentation is a file, or a named region of a file, that the build compiles
// or the test suite sends. A page marks the listing with <!-- snippet: File.java#region --> above
// the fenced block, and this script writes the block from the file.
//
//   node scripts/doc-snippets.mjs          rewrite the blocks
//   node scripts/doc-snippets.mjs --check  fail when a block differs from its source

const START = /^\s*(?:\/\/|#) docs:start ([\w-]+)\s*$/;
const END = /^\s*(?:\/\/|#) docs:end ([\w-]+)\s*$/;

function sourceFile(name) {
  const found = config.snippetRoots
    .map(root => path.join(projectRoot, root, name))
    .filter(candidate => fs.existsSync(candidate));
  if (found.length !== 1) {
    throw new Error(`Snippet file ${name} is found ${found.length} times under the snippet roots`);
  }
  return found[0];
}

function trimBlankEnds(lines) {
  const kept = [...lines];
  while (kept.length && kept[0].trim() === '') kept.shift();
  while (kept.length && kept[kept.length - 1].trim() === '') kept.pop();
  return kept;
}

function dedent(lines) {
  const indents = lines.filter(line => line.trim() !== '').map(line => /^ */.exec(line)[0].length);
  const common = indents.length ? Math.min(...indents) : 0;
  return lines.map(line => line.slice(Math.min(common, line.length)));
}

/** @returns the lines of a file, or of the parts of it a region name marks, without the marker lines */
export function snippet(name, region) {
  const lines = fs.readFileSync(sourceFile(name), 'utf8').split('\n');
  if (!region) {
    const body = lines.filter(line => !START.test(line) && !END.test(line));
    const withoutPackage = name.endsWith('.java') ? body.filter(line => !/^package [\w.]+;$/.test(line)) : body;
    return trimBlankEnds(withoutPackage);
  }
  const collected = [];
  let inside = false;
  let seen = false;
  for (const line of lines) {
    const start = START.exec(line);
    const end = END.exec(line);
    if (start || end) {
      if (start && start[1] === region) { inside = true; seen = true; }
      if (end && end[1] === region) inside = false;
      continue;
    }
    if (inside) collected.push(line);
  }
  if (!seen) throw new Error(`Region ${region} is not marked in ${name}`);
  if (inside) throw new Error(`Region ${region} is not closed in ${name}`);
  return trimBlankEnds(dedent(collected));
}

/** @returns the page text with every marked block written from its source */
export function sync(text) {
  const lines = text.split('\n');
  const out = [];
  let fenced = 0;
  for (let index = 0; index < lines.length; index++) {
    const line = lines[index];
    out.push(line);
    if (fenced) {
      if (closes(line, fenced)) fenced = 0;
      continue;
    }
    const opened = opens(line);
    if (opened) {
      fenced = opened.ticks;
      continue;
    }
    const marker = /^<!-- snippet: (\S+?)(?:#([\w-]+))? -->$/.exec(line.trim());
    if (!marker) continue;
    let open = index + 1;
    while (open < lines.length && lines[open].trim() === '') open++;
    const listing = open < lines.length ? opens(lines[open]) : null;
    if (!listing) {
      throw new Error(`Snippet marker for ${marker[1]} at line ${index + 1} is not followed by a fenced block`);
    }
    let close = open + 1;
    while (close < lines.length && !closes(lines[close], listing.ticks)) close++;
    if (close >= lines.length) throw new Error(`Fenced block at line ${open + 1} is not closed`);
    out.push(...lines.slice(index + 1, open + 1), ...snippet(marker[1], marker[2]), lines[close]);
    index = close;
  }
  return out.join('\n');
}

/** Every page that carries listings: the pages of each locale and the root pages. */
export function listingPages() {
  const pages = config.rootPages.map(name => path.join(projectRoot, name));
  for (const locale of config.locales) {
    for (const name of pageNames(locale)) pages.push(path.join(projectRoot, config.docs, locale, name));
  }
  return pages;
}

if (process.argv[1] && path.resolve(process.argv[1]) === new URL(import.meta.url).pathname) {
  const check = process.argv.includes('--check');
  const changed = [];
  for (const file of listingPages()) {
    const before = fs.readFileSync(file, 'utf8');
    const after = sync(before);
    if (after !== before) {
      changed.push(path.relative(projectRoot, file));
      if (!check) fs.writeFileSync(file, after);
    }
  }
  if (changed.length) console.log((check ? 'Out of date: ' : 'Updated: ') + changed.join(', '));
  if (check && changed.length) process.exit(1);
}
