## Bộ nhớ dự án — bắt buộc

Dự án này duy trì bộ nhớ dạng wiki trong `wiki/`, theo mô hình LLM Wiki. Quy ước
đầy đủ ở [wiki/SCHEMA.md](wiki/SCHEMA.md); quy trình chi tiết ở skill
`session-memory`.

Ba quy tắc cứng:

1. **Đầu mỗi phiên, đọc `wiki/log.md` trước khi làm gì khác.** File này cho biết
   phiên trước dừng ở đâu và bước tiếp theo là gì. Đọc nhanh:
   `grep "^## \[" wiki/log.md | tail -10`. Nếu entry cuối là `IN PROGRESS` hoặc
   `BLOCKED`, tiếp tục từ dòng `- next:` của nó thay vì làm lại từ đầu.

2. **Sau mỗi phase công việc, tự append checkpoint vào `wiki/log.md`.** Không
   cần user nhắc, không hỏi xin phép. Lấy giờ thật bằng
   `date '+%Y-%m-%d %H:%M'`. Việc chưa xong ghi `IN PROGRESS` hoặc
   `BLOCKED: <lý do>` kèm dòng `- next:` — không bao giờ ghi `DONE` khống, vì
   phiên sau sẽ tin vào đó và bỏ qua phần còn dở.

3. **Chỉ ghi vào `wiki/raw/` và `wiki/pages/` sau khi user xác nhận** code chạy
   đúng ("chạy ok", "test pass", "confirm", ...). Wiki chứa tri thức đã kiểm
   chứng, không chứa thử nghiệm dang dở.

Ngôn ngữ wiki: song ngữ — thuật ngữ kỹ thuật và tên file giữ tiếng Anh, phần
diễn giải viết tiếng Việt.
