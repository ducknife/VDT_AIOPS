# Duckompose TUI

Giao diện dòng lệnh (terminal UI) cho engine AIOps **Duckompose**. Hiển thị điều tra sự cố
theo thời gian thực và cho phép hỏi–đáp (chat) với Agent. Xây bằng React + [Ink](https://github.com/vadimdemedes/ink).

> TUI chỉ là **client**. Engine (Docker Compose) phải chạy trước — TUI kết nối vào qua WebSocket.

## Yêu cầu
- **Node.js** 18+ (và npm)
- **Engine đang chạy** (mặc định `ws://localhost:8088/ws/incidents`)

## Cài đặt (1 lần) → có lệnh `duckompose`
```bash
cd aiops/tui
npm install
npm run build
npm link          # hoặc: npm install -g .
```
Sau đó gõ **`duckompose`** ở bất kỳ đâu để mở TUI.

- `npm link` = symlink → sửa cấu hình + `npm run build` là áp dụng ngay (không cần link lại). Hợp khi đang phát triển.
- `npm install -g .` = bản copy → sửa cấu hình thì phải cài lại (`npm install -g .`).

Gỡ: `npm unlink -g duckompose-tui` (hoặc `npm uninstall -g duckompose-tui`).

> **Sau khi cập nhật code** (`git pull`, đổi asset/logo…): chạy lại **`npm run build`** để lệnh `duckompose` nhận bản mới — vì `duckompose` chạy bản đã build trong `dist/`, không phải source. (`npm link` không cần làm lại.)

## Cấu hình (`.env`)
Copy `.env.example` thành `.env` rồi sửa, sau đó **build lại** để nướng giá trị vào bản chạy:
```bash
cp .env.example .env      # Windows: copy .env.example .env
# sửa .env ...
npm run build
```

| Biến | Ý nghĩa | Mặc định |
|------|---------|----------|
| `DUCKOMPOSE_WS` | Địa chỉ WebSocket của engine | `ws://localhost:8088/ws/incidents` |

Giá trị `DUCKOMPOSE_WS` được **nướng vào bản build** (esbuild `define`) → với lệnh `duckompose` phải sửa `.env` rồi `npm run build` lại mới đổi được. Chỉ ở chế độ dev (`npm start`) mới ghi đè được lúc chạy:
```bash
DUCKOMPOSE_WS=ws://192.168.1.10:8088/ws/incidents npm start
```

## Chế độ phát triển (không cần build)
```bash
npm start          # chạy trực tiếp bằng tsx
npm run typecheck  # kiểm tra kiểu (tsc --noEmit)
```

## Phím tắt trong TUI

TUI có 2 chế độ: **CHAT** (gõ câu hỏi) và **SELECT** (chọn incident bằng phím). Bấm `Tab` để chuyển qua lại.

### Toàn cục (luôn hoạt động)
| Phím | Hành động |
|------|-----------|
| `Tab` | Chuyển đổi CHAT ↔ SELECT |
| `PgUp` / `PgDn` | Cuộn nhanh (6 dòng) |
| `Ctrl+Q` | Thoát |

### Chế độ CHAT
| Phím | Hành động |
|------|-----------|
| `↑` / `↓` | Cuộn viewport (2 dòng) |
| `Enter` | Gửi câu hỏi |

### Chế độ SELECT
| Phím | Hành động |
|------|-----------|
| `↑` / `↓` | Chọn incident trước / sau |
| `a` | Acknowledge incident đang chọn |
| `r` | Resolve incident đang chọn |
| `e` | Mở rộng / thu gọn chi tiết incident |
| `c` | Copy toàn bộ incident đang chọn vào clipboard |
| `1` / `2` / `3` | Feedback: correct / partial / wrong |
| `Esc` | Huỷ form feedback, hoặc quay về CHAT |

### Chuột
| Thao tác | Hành động |
|----------|-----------|
| Lăn chuột | Cuộn viewport |
| Click incident | Chọn + mở/thu chi tiết (chuyển sang SELECT) |
| Click nút `a` / `r` / `e` | Ack / Resolve / Mở rộng |
| Click hội thoại | Mở / thu gọn lịch sử hội thoại |
| Click tool/turn | Mở / thu gọn chi tiết tool calls |
| Click tiêu đề / đầu câu trả lời | Copy khối đó vào clipboard |
| Kéo thanh cuộn (cột cuối) | Cuộn nhanh tới vị trí |
| `Shift` + kéo | Bôi đen chọn text (do terminal xử lý) |

## Lệnh chat (slash commands)

Gõ trực tiếp vào ô chat:

| Lệnh | Hành động |
|-------|-----------|
| `/conversations` hoặc `/conversation-history` | Liệt kê lịch sử hội thoại đã lưu |
| `/ack <id>` | Acknowledge incident theo ID |
| `/resolve <id>` | Resolve incident theo ID |
| _(text bất kỳ)_ | Gửi câu hỏi cho Agent |

