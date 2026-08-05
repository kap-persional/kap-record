# wiki/ — Bộ nhớ dự án

Thư mục này là bộ nhớ dài hạn của dự án. **LLM viết, bạn đọc.** Bạn không cần
tự cập nhật nó — nhiệm vụ của bạn là cung cấp nguồn, đặt câu hỏi, và xác nhận
kết quả; phần biên soạn, liên kết chéo, và bảo trì là việc của LLM.

Dựa trên mô hình **LLM Wiki** của Andrej Karpathy:
https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f

Ý tưởng cốt lõi: thay vì RAG — truy xuất và tổng hợp lại từ đầu ở mỗi câu hỏi —
tri thức được **biên soạn một lần rồi giữ cho luôn cập nhật**. Wiki là artifact
tích luỹ: liên kết chéo đã sẵn ở đó, mâu thuẫn đã được đánh dấu, phần tổng hợp
đã phản ánh mọi thứ đã nạp vào.

## Đọc gì trước

| File | Dùng khi |
|---|---|
| [log.md](log.md) | **Mở đầu tiên khi quay lại sau khi phiên bị đứt** — biết đã làm tới đâu |
| [index.md](index.md) | Muốn tra cứu: danh mục mọi trang tri thức |
| [SCHEMA.md](SCHEMA.md) | Muốn hiểu hoặc đổi quy ước tổ chức wiki |

## Hai chế độ ghi

- **`log.md` ghi tự động** sau mỗi phase công việc, không cần bạn nhắc. Đây là
  checkpoint tiến độ, có cả ngày và giờ.
- **`raw/` và `pages/` chỉ ghi khi bạn confirm** code đã chạy đúng. Đây là tri
  thức đã kiểm chứng — wiki không chứa thử nghiệm dang dở.

## Thư mục

- `raw/` — bản ghi gốc từng phiên, bất biến, chỉ thêm mới.
- `pages/` — các trang tri thức liên kết chéo. Mở bằng Obsidian để xem graph
  view và đi theo liên kết `[[...]]`.

Wiki là một phần của git repo, nên bạn có sẵn lịch sử phiên bản và diff cho mọi
thay đổi tri thức.
