import { readFileSync, readdirSync, writeFileSync, existsSync } from 'node:fs';
import { extname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
function normalize(directory) {
  if (!existsSync(directory)) return;
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) { normalize(path); continue; }
    const extension = extname(entry.name);
    if (!['.js', '.css', '.html', '.svg'].includes(extension)) continue;
    const source = readFileSync(path, 'utf8');
    const text = source.replace(/[^\x00-\x7f]/gu, character => {
      const code = character.codePointAt(0).toString(16);
      if (extension === '.js') return `\\u{${code}}`;
      if (extension === '.css') return `\\${code} `;
      return `&#x${code};`;
    });
    if (source !== text) writeFileSync(path, text, 'ascii');
  }
}
normalize(join(root, 'frontend/dist'));
normalize(join(root, 'backend/src/main/resources/static'));
process.stdout.write('Built web assets use ASCII encoding.\n');
