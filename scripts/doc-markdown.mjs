import fs from 'node:fs';
import path from 'node:path';

// Reads the documentation pages the way the checks need them: headings, fenced blocks with the
// snippet marker above them, images, links, tables and inline code.

export const config = JSON.parse(fs.readFileSync(new URL('./docs-config.json', import.meta.url), 'utf8'));
export const projectRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');

const MARKER = /^<!-- snippet: (\S+?)(?:#([\w-]+))? -->$/;

/** @returns the length and language of the fence a line opens, or null */
export function opens(line) {
  const found = /^(`{3,})(\S*)\s*$/.exec(line);
  return found ? { ticks: found[1].length, language: found[2] } : null;
}

/** Whether a line closes a fence opened with this many backticks. */
export function closes(line, ticks) {
  const found = /^(`{3,})\s*$/.exec(line);
  return Boolean(found) && found[1].length >= ticks;
}

/** The page files of a locale, without the sidebar. */
export function pageNames(locale) {
  return fs.readdirSync(path.join(projectRoot, config.docs, locale))
    .filter(name => name.endsWith('.md') && !name.startsWith('_'))
    .sort();
}

export function readPage(locale, name) {
  return fs.readFileSync(path.join(projectRoot, config.docs, locale, name), 'utf8');
}

/** @returns the structure of a page; text inside fenced blocks is never read as markup */
export function parse(text) {
  const lines = text.split('\n');
  const page = { headings: [], fences: [], images: [], links: [], tables: [], inlineCode: [], prose: [] };
  let fence = null;
  let marker = null;
  let table = null;
  lines.forEach((line, index) => {
    if (fence) {
      if (closes(line, fence.ticks)) {
        page.fences.push(fence);
        fence = null;
      } else {
        fence.lines.push(line);
      }
      return;
    }
    const opening = opens(line);
    if (opening) {
      fence = { language: opening.language, ticks: opening.ticks, lines: [], line: index + 1, marker };
      marker = null;
      return;
    }
    const found = MARKER.exec(line.trim());
    if (found) {
      marker = { file: found[1], region: found[2] || null, line: index + 1 };
      return;
    }
    if (line.trim() !== '' && marker) {
      page.strayMarker = marker;
      marker = null;
    }
    const heading = /^(#{1,6}) (.+)$/.exec(line);
    if (heading) {
      page.headings.push({ level: heading[1].length, text: heading[2].trim(), line: index + 1 });
    }
    if (/^\|.*\|\s*$/.test(line)) {
      const cells = line.trim().slice(1, -1).split('|').length;
      if (!table) {
        table = { rows: 0, columns: cells, line: index + 1 };
        page.tables.push(table);
      }
      table.rows += 1;
    } else {
      table = null;
    }
    for (const image of line.matchAll(/!\[[^\]]*\]\(([^)\s]+)\)/g)) page.images.push(image[1]);
    for (const link of line.matchAll(/(?<!!)\[[^\]]*\]\(([^)\s]+)\)/g)) page.links.push(link[1]);
    for (const code of line.matchAll(/`([^`]+)`/g)) page.inlineCode.push(code[1]);
    page.prose.push(line);
  });
  if (fence) throw new Error('Unclosed fenced block starting at line ' + fence.line);
  return page;
}

/** Everything a page shows as code: inline code and the bodies of fenced blocks. */
export function codeText(page) {
  return page.inlineCode.join('\n') + '\n' + page.fences.map(fence => fence.lines.join('\n')).join('\n');
}

/** The id Docsify gives a heading, so that a link to a section can be checked. */
export function headingId(text) {
  return text.trim().toLowerCase()
    .replace(/<[^>]+>/g, '')
    .replace(/[\u2000-\u206F\u2E00-\u2E7F\\'!"#$%&()*+,./:;<=>?@[\]^`{|}~]/g, '')
    .replace(/\s/g, '-')
    .replace(/-+/g, '-')
    .replace(/^(\d)/, '_$1');
}
