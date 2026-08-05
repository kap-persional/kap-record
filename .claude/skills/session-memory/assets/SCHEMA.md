# SCHEMA — Quy ước wiki của dự án

File này định nghĩa cách wiki này được tổ chức. LLM đọc file này **trước khi**
thao tác với wiki. Khi quy ước thay đổi, sửa file này — nó là nguồn sự thật,
được ưu tiên hơn nội dung skill `session-memory`.

> Đây là template khởi tạo. Hãy điều chỉnh phần "Phân loại trang" và "Tiêu chí"
> cho đúng lĩnh vực của dự án — mặc định bên dưới cố tình viết chung chung.

## Ba lớp

| Thư mục | Nội dung | Ai sửa |
|---|---|---|
| `raw/` | Bản ghi gốc từng phiên, bất biến | Chỉ thêm file mới |
| `pages/` | Trang tri thức liên kết chéo | LLM biên soạn & bảo trì |
| (gốc) | `SCHEMA.md`, `index.md`, `log.md`, `README.md` | LLM cập nhật, user tinh chỉnh SCHEMA |

## Phân loại trang trong `pages/`

Mỗi trang thuộc đúng một loại, khai trong frontmatter `type`:

- `module` — một phần cụ thể của codebase (một service, một nhóm file, một API
  endpoint). Ví dụ: `booking-api.md`, `auth-flow.md`.
- `concept` — một khái niệm hoặc quy tắc xuyên suốt nhiều chỗ. Ví dụ:
  `idempotency.md`, `fare-rules.md`.
- `decision` — một quyết định thiết kế đã chốt, kèm lý do và đánh đổi. Ghi cả
  phương án đã loại và tại sao — phần này thường có giá trị hơn phương án được
  chọn, vì nó chặn việc bàn lại vòng tròn sau vài tháng.
- `troubleshooting` — một lỗi đã gặp và cách khắc phục **đã kiểm chứng**.

## Frontmatter chuẩn

```yaml
---
type: module          # module | concept | decision | troubleshooting
tags: [booking, api]
created: 2026-01-15
updated: 2026-01-15
sources:              # các file trong raw/ đã sinh ra trang này
  - raw/2026-01-15-ten-phien.md
---
```

`updated` sửa mỗi lần nội dung trang thay đổi. `sources` là danh sách tích lũy —
thêm vào, không thay thế, để truy được một khẳng định về phiên nào sinh ra nó.

## Liên kết chéo

Dùng `[[tên-file-không-đuôi]]`, ví dụ `[[booking-api]]`. Cú pháp này hoạt động
với Obsidian và vẫn đọc được ở dạng text thuần.

Mỗi trang nên có mục `## Liên quan` ở cuối liệt kê các trang kết nối. Trang
không có liên kết nào trỏ tới là **orphan** — trên thực tế nó vô hình, vì
không ai đi tới được. Lượt lint sẽ bắt các trang này.

## Quy ước ngôn ngữ — song ngữ

- Giữ **tiếng Anh**: tên file, tên hàm/biến/class, thuật ngữ kỹ thuật
  (`retry policy`, `race condition`, `idempotency key`), tiêu đề khái niệm.
- Viết **tiếng Việt**: phần diễn giải, lý do, đánh đổi, hướng dẫn.
- Tên file trang dùng tiếng Anh kebab-case: `booking-api.md`, không phải
  `api-dat-cho.md` — để liên kết ổn định và `grep` được.

## Format `log.md`

Mỗi entry một dòng, bắt đầu bằng `## [` để `grep`/`tail` được:

```
  ## [YYYY-MM-DD HH:MM] <loại> | <mô tả> — <trạng thái>
```

- Loại: `phase` | `ingest` | `query` | `lint` | `init`
- Trạng thái (chỉ cho `phase`): `DONE` | `IN PROGRESS` | `BLOCKED: <lý do>`
- Giờ lấy từ `scripts/log_entry.py` hoặc `date '+%Y-%m-%d %H:%M'`, **không tự
  đoán** — một mốc thời gian sai làm hỏng đúng cái công dụng khôi phục của log.
- Entry chưa `DONE` phải kèm dòng `- next: <bước tiếp theo>` ngay bên dưới.
- Entry mới luôn **thêm xuống cuối file**, để `grep ... | tail` ra đúng những
  việc gần nhất. Xếp ngược lại là hỏng đúng thao tác dùng nhiều nhất.

**Append-only nghĩa là không sửa entry cũ.** Khi quay lại làm nốt một việc đang
`IN PROGRESS`, viết một entry mới ở cuối — đừng sửa entry cũ thành `DONE`. Sửa
đè xoá mất bằng chứng rằng phiên đó từng bị đứt, và xoá luôn dòng `- next:` cho
biết lúc ấy định làm gì; log sẽ đọc như thể mọi việc đều trôi chảy. Nếu một entry
cũ ghi sai, cũng đính chính bằng entry mới có dạng
`## [<giờ mới>] phase | Đính chính entry [<giờ cũ>]: <nội dung đúng> — DONE`.

## Tiêu chí: cái gì đáng lên wiki

**Đáng ghi**: quyết định thiết kế và lý do; hành vi phi trực giác của hệ thống;
lỗi đã tốn thời gian debug và cách khắc phục; ràng buộc nghiệp vụ; lý do một
cách làm hiển nhiên lại không dùng được.

**Không đáng ghi**: lỗi cú pháp sửa ngay; thông tin đọc code là biết; tiến độ
thuần túy (thuộc về `log.md`); thử nghiệm chưa được xác nhận hoạt động.

Khi phân vân, hỏi: *sáu tháng nữa có ai mất một giờ để tìm lại điều này không?*
Nếu có thì viết trang.
