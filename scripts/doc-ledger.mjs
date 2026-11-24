import fs from 'node:fs';
import path from 'node:path';
import { projectRoot } from './doc-markdown.mjs';

// Reads from the sources what the documentation has to cover. The ledger (docs-ledger.json) says
// which page explains each group; a name the sources gain appears here on its own, so a feature
// that is added without a page fails the documentation check instead of going unnoticed.

export const ledger = JSON.parse(fs.readFileSync(new URL('./docs-ledger.json', import.meta.url), 'utf8'));

const read = file => fs.readFileSync(path.join(projectRoot, file), 'utf8');
const unique = values => [...new Set(values)];
const all = (text, pattern) => [...text.matchAll(pattern)].map(match => match[1]);

function kebab(name) {
  return name.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();
}

/** The keys under a prefix that a @ConfigurationProperties class and its nested classes declare. */
function propertyKeys(file, prefix) {
  const classes = new Map();
  let current = null;
  for (const line of read(file).split('\n')) {
    const declared = /^\s*public (?:static )?class (\w+)/.exec(line);
    if (declared) {
      current = [];
      classes.set(declared[1], current);
      continue;
    }
    const field = /^\s+private (?!static)([\w<>]+) (\w+)(?: =.*)?;/.exec(line);
    if (field && current) current.push({ type: field[1], name: field[2] });
  }
  const root = [...classes.keys()][0];
  const keys = [];
  const expand = (owner, base) => {
    for (const { type, name } of classes.get(owner)) {
      if (classes.has(type)) expand(type, base + '.' + kebab(name));
      else keys.push(base + '.' + kebab(name));
    }
  };
  expand(root, prefix);
  return keys;
}

const extractors = {
  enumConstants: source => all(read(source.file), /^\s{4}([A-Z][A-Z0-9_]*)(?=[(,;\s]|$)/gm),
  enumWireNames: source => all(read(source.file), /\b[A-Z][A-Z0-9_]*\("([A-Za-z]+)"/g),
  annotationAttributes: source => all(read(source.file), /^\s{4}[\w.<>\[\]]+ (\w+)\(\) default/gm),
  defaultMethods: source => all(read(source.file), /^\s{4}default (?:<[^>]+> )?[\w<>?,. \[\]]+ (\w+)\(/gm),
  interfaceMethods: source => all(read(source.file), /^\s{4}(?!default|return|public|private|static)[\w<>?,. \[\]]+ (\w+)\(/gm),
  publicMethods: source => all(read(source.file), /^\s{4}public (?:static )?(?:final )?(?:<[^>]+> )?[\w<>?,. \[\]$]+ (\w+)\(/gm),
  stringConstants: source => all(read(source.file), /public static final String \w+ = "([^"]+)"/g),
  propertyKeys: source => propertyKeys(source.file, source.prefix),
  bundleKeys: source => all(read(source.file), /^([a-z][\w.]*)=/gm),
  typeNames: source => fs.readdirSync(path.join(projectRoot, source.directory))
    .filter(name => name.endsWith('.java'))
    .filter(name => /^public (?:final |abstract )?(?:class|interface|enum|record|@interface) /m
      .test(read(path.join(source.directory, name))))
    .map(name => name.slice(0, -5)),
};

/** @returns the names a group of the ledger takes from its source, without the ones it marks internal */
export function namesOf(group) {
  const extract = extractors[group.source.extract];
  if (!extract) throw new Error('Unknown extraction ' + group.source.extract + ' in group ' + group.id);
  const names = unique(extract(group.source));
  if (!names.length) throw new Error('Group ' + group.id + ' reads no name from its source');
  const internal = Object.keys(group.internal || {});
  const unknown = internal.filter(name => !names.includes(name));
  if (unknown.length) throw new Error('Group ' + group.id + ' marks names its source does not have: ' + unknown.join(', '));
  return names.filter(name => !internal.includes(name));
}

/** Whether a page shows a name as code: as a whole word for an identifier, as written for a dotted key. */
export function shows(code, name) {
  if (/^[A-Za-z_]\w*$/.test(name)) return new RegExp('(?<![A-Za-z0-9_])' + name + '(?![A-Za-z0-9_])').test(code);
  return code.includes(name);
}
