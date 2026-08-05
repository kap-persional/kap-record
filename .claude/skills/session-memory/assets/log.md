# Log — Nhật ký & checkpoint khôi phục

Append-only. Entry mới **thêm xuống cuối file**.

Mục đích kép: (1) timeline cho thấy wiki tiến hoá thế nào, (2) **checkpoint** —
nếu phiên làm việc bị đứt (sập nguồn, mất mạng, hết context), đọc file này là
biết đã làm tới đâu và đi tiếp từ đâu.

Format mỗi entry:

```
  ## [YYYY-MM-DD HH:MM] <phase|ingest|query|lint|init> | <mô tả> — <trạng thái>
```

(dòng mẫu trên thụt lề một khoảng trắng để không lọt vào kết quả `grep`.)

Entry chưa xong kèm dòng `- next:` mô tả bước kế tiếp. Xem [SCHEMA.md](SCHEMA.md).

Đọc nhanh 10 entry gần nhất:

```bash
grep "^## \[" wiki/log.md | tail -10
```

---
