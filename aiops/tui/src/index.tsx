#!/usr/bin/env node
import { render } from 'ink';
import App from './App';

// exitOnCtrlC: false -> Ctrl+C (và Ctrl+Shift+C khi không có vùng chọn) KHÔNG tự thoát app
// -> copy/paste an toàn. Thoát app bằng Ctrl+Q (xử lý trong App).
render(<App />, { exitOnCtrlC: false });
