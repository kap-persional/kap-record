# CLAUDE.md — KapRecord

Ứng dụng Flutter Android-only ghi màn hình + âm thanh nội bộ. Tài liệu kỹ thuật đầy đủ ở `WIKI.md` (đọc trước khi sửa code — mô tả kiến trúc, state machine, các lỗi đã sửa, vấn đề còn tồn tại).

---

## 🌿 Quy tắc branch (BẮT BUỘC tuân theo)

- **`dev`** là nhánh phát triển chính. Push code, commit fix/tính năng lên `dev` **tự do, không cần hỏi xin phép trước**.
- **`main`** chỉ được đụng tới (merge vào, hoặc push trực tiếp) khi người dùng **yêu cầu rõ ràng** ("merge", "build", "lên main", v.v.). Không tự ý merge `dev` → `main`.
- **CI/CD** (`.github/workflows/build.yml`) chỉ tự động chạy build khi có push vào `main` — push vào `dev` sẽ **không** tự trigger build APK. Nếu cần build thử trên `dev`, dùng `workflow_dispatch` (chạy tay qua tab Actions) chứ không đổi trigger `push` trở lại `dev`.
- Khi người dùng bảo "merge/build": tạo/merge vào `main` từ trạng thái mới nhất của `dev`, rồi push — lúc đó CI mới tự chạy.

---

## 🧠 Tài liệu kỹ thuật

Đọc `WIKI.md` ở đầu mỗi phiên làm việc trên project này. File này ghi:
- Kiến trúc tổng thể (Flutter ↔ Native qua MethodChannel/EventChannel)
- State machine ghi hình chi tiết
- Lịch sử lỗi đã sửa (đánh số, có nguyên nhân + cách sửa)
- Vấn đề còn tồn tại / chưa xác nhận — đặc biệt các phần cần test tay trên thiết bị thật vì CI/emulator không xác nhận được (âm thanh nội bộ, xoay màn hình, ULTRA quality, hành vi khi OEM kill service)

Sau khi sửa lỗi hoặc thêm tính năng và người dùng xác nhận ổn, cập nhật `WIKI.md` tương ứng (thêm mục lỗi đã sửa, cập nhật phần "còn tồn tại" nếu liên quan).
