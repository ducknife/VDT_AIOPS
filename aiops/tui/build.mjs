// Build TUI: doc .env -> "nuong" gia tri vao bundle luc build (esbuild define).
// Sua .env xong chay `npm run build` la gia tri moi duoc bake vao dist/index.js.
import { build } from 'esbuild';
import { readFileSync, existsSync, copyFileSync, mkdirSync } from 'node:fs';

// parser .env toi gian (KEY=VALUE, bo qua comment/dong trong) — khong can them dependency.
function loadEnv(path) {
  const env = {};
  if (!existsSync(path)) return env;
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const s = line.trim();
    if (!s || s.startsWith('#')) continue;
    const i = s.indexOf('=');
    if (i === -1) continue;
    env[s.slice(0, i).trim()] = s.slice(i + 1).trim();
  }
  return env;
}

const env = loadEnv('.env');
const WS = env.DUCKOMPOSE_WS || 'ws://localhost:8088/ws/incidents';

await build({
  entryPoints: ['src/index.tsx'],
  bundle: true,
  platform: 'node',
  format: 'esm',
  packages: 'external', // ink/react/ws load tu node_modules -> tranh loi wasm khi bundle
  jsx: 'automatic',
  outfile: 'dist/index.js',
  define: { 'process.env.DUCKOMPOSE_WS': JSON.stringify(WS) }, // bake gia tri tu .env
});

// Copy asset doc luc chay (readFileSync canh import.meta.url = dist/) vao dist/.
mkdirSync('dist', { recursive: true });
for (const src of ['src/assets/logo.ansi', 'src/utils/duckompose.txt']) {
  copyFileSync(src, `dist/${src.split('/').pop()}`);
}

console.log(`build ok -> dist/index.js   (DUCKOMPOSE_WS = ${WS})`);
