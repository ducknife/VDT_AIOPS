import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { Box, Text } from 'ink';

// logo.ansi = ảnh PNG đã convert sang ANSI (nửa-khối ▀) bằng scripts/img2ansi.mjs
// Đổi kích thước: chạy lại `node scripts/img2ansi.mjs <COLS>` rồi reload.
const dir = dirname(fileURLToPath(import.meta.url));
const ansi = readFileSync(join(dir, 'logo.ansi'), 'utf8').replace(/\n+$/, '');

export function Logo() {
  return (
    <Box flexDirection="column">
      <Text>{ansi}</Text>
    </Box>
  );
}
