import { readFileSync, readdirSync } from 'node:fs';
import { extname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const excluded = new Set(['node_modules', '.local', 'target', 'dist', 'delivery', 'tmp', '.git', '.idea']);
const failures = [];
let checked = 0;

function hasCodeComment(text, extension) {
  if (extension === '.xml' || extension === '.html' || extension === '.svg') return text.includes('<!--');
  if (extension === '.ps1' || extension === '.properties') return /^\s*#/m.test(text);
  if (!['.java', '.js', '.jsx', '.mjs', '.css', '.sql'].includes(extension)) return false;
  let quote = '';
  for (let index = 0; index < text.length; index++) {
    const current = text[index];
    const next = text[index + 1];
    if (quote) {
      if (current === '\\') index++;
      else if (current === quote) quote = '';
      continue;
    }
    if (['"', "'", '`'].includes(current)) { quote = current; continue; }
    if (current === '/' && next === '*') return true;
    if (extension !== '.css' && extension !== '.sql' && current === '/' && next === '/') return true;
    if (extension === '.sql' && current === '-' && next === '-' && /\s/.test(text[index + 2] || '')) return true;
  }
  return false;
}

function walk(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (excluded.has(entry.name)) continue;
    const path = join(directory, entry.name);
    const name = relative(root, path);
    if (name === 'runtime' && entry.isDirectory()) continue;
    if (/[^\x00-\x7F]/.test(name)) failures.push(`Non-ASCII filename: ${name}`);
    if (entry.isDirectory()) {
      if (name.replaceAll('\\', '/') === 'backend/src/main/resources/static/assets') continue;
      walk(path);
    } else if (entry.isFile()) {
      const text = readFileSync(path, 'utf8');
      checked++;
      if (/[^\x00-\x7F]/.test(text)) failures.push(`Non-ASCII content: ${name}`);
      if (hasCodeComment(text, extname(path))) failures.push(`Code comment found: ${name}`);
    }
  }
}

walk(root);
const lock = JSON.parse(readFileSync(join(root, 'frontend/package-lock.json'), 'utf8'));
for (const dependency of Object.values(lock.packages)) {
  if (dependency.resolved && new URL(dependency.resolved).hostname !== 'registry.npmjs.org') failures.push(`Unofficial npm source: ${dependency.resolved}`);
}
const settings = readFileSync(join(root, 'backend/.mvn/settings.xml'), 'utf8');
for (const match of settings.matchAll(/<url>(.*?)<\/url>/g)) {
  if (match[1] !== 'https://repo.maven.apache.org/maven2') failures.push(`Unofficial Maven source: ${match[1]}`);
}
const npmConfiguration = readFileSync(join(root, 'frontend/.npmrc'), 'utf8');
for (const line of npmConfiguration.split(/\r?\n/)) {
  if (/registry\s*=/.test(line) && !line.endsWith('https://registry.npmjs.org/')) failures.push(`Unofficial npm registry: ${line}`);
}
if (failures.length) {
  failures.forEach(failure => process.stderr.write(`${failure}\n`));
  process.exitCode = 1;
} else {
  process.stdout.write(`Verified ${checked} source and document files: ASCII only, no code comments, official dependency sources.\n`);
}
