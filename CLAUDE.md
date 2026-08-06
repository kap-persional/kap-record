# CLAUDE.md — KapRecord

Ứng dụng Flutter Android-only ghi màn hình + âm thanh nội bộ. Tài liệu kỹ thuật đầy đủ ở `WIKI.md` (đọc trước khi sửa code — mô tả kiến trúc, state machine, các lỗi đã sửa, vấn đề còn tồn tại).

---

## 🌿 Quy trình branch (BẮT BUỘC tuân theo — 3 bước)

### 1. Code chỉ trên `dev`
Push code, commit fix/tính năng lên `dev` **tự do, không cần hỏi xin phép trước**. KHÔNG bao giờ push/commit trực tiếp lên `main`.

### 2. Khi người dùng bảo "build"
- KHÔNG merge vào `main`. Chỉ **mở Pull Request `dev` → `main`** (nếu đã có PR đang mở từ `dev` vào `main` thì dùng lại PR đó, không tạo PR trùng).
- CI (`.github/workflows/build.yml`, trigger `pull_request: branches: [main]`) sẽ tự chạy trên PR này — build APK debug + emulator smoke-test.
- Báo cho người dùng biết PR đã mở/đã có sẵn (kèm link), để họ tự vào tab **Actions** của PR đó theo dõi và tải APK debug (artifact `kap-record-debug-apk`) về test.
- Nếu người dùng báo còn lỗi sau khi test: sửa tiếp trên `dev` rồi push như bình thường. KHÔNG cần làm gì thêm với PR — GitHub tự bắn sự kiện `pull_request.synchronize` mỗi khi nhánh nguồn (`dev`) của một PR đang mở nhận thêm commit, nên CI tự build lại trên PR đó mà không cần tạo PR mới hay thao tác gì thêm.

### 3. Khi người dùng bảo "merge main" / "merge vào main"
- **Trước tiên cập nhật `WIKI.md`** (mục lỗi đã sửa, trạng thái build đã xác nhận, vấn đề còn tồn tại nếu có thay đổi) — cập nhật trên `dev` trước khi merge.
- Sau đó **merge PR đang mở vào `main`** (không tạo PR mới nếu đã có sẵn từ bước 2).
- Việc cập nhật `WIKI.md` không tự trigger build lại (đã có `paths-ignore: ['**.md']`) nên không ảnh hưởng tới kết quả build đã test ở bước 2.

**Không tự ý merge vào `main` khi chưa được yêu cầu rõ ràng ở bước 3**, kể cả khi PR ở bước 2 đã build xanh.

---

## 🧠 Tài liệu kỹ thuật

Đọc `WIKI.md` ở đầu mỗi phiên làm việc trên project này. File này ghi:
- Kiến trúc tổng thể (Flutter ↔ Native qua MethodChannel/EventChannel)
- State machine ghi hình chi tiết
- Lịch sử lỗi đã sửa (đánh số, có nguyên nhân + cách sửa)
- Vấn đề còn tồn tại / chưa xác nhận — đặc biệt các phần cần test tay trên thiết bị thật vì CI/emulator không xác nhận được (âm thanh nội bộ, xoay màn hình, ULTRA quality, hành vi khi OEM kill service)

Sau khi sửa lỗi hoặc thêm tính năng và người dùng xác nhận ổn, cập nhật `WIKI.md` tương ứng (thêm mục lỗi đã sửa, cập nhật phần "còn tồn tại" nếu liên quan) — theo đúng bước 3 ở trên, làm trước khi merge vào `main`.
