# Kịch bản demo 3 điểm bắt buộc (hackathon)

Ba tình huống dưới đây KHÔNG phải bug — app đã có đủ cơ chế. Chúng chỉ chưa từng
lọt vào video vì chưa ai diễn đúng. Mỗi cảnh ghi rõ: làm gì, mong thấy gì.

## 1. Bấm nhầm được phục hồi (wrong-tap recovery)

1. Bắt đầu bất kỳ hướng dẫn nhiều bước nào (ví dụ: mở Zalo).
2. Khi vòng khoanh hiện ở một nút, cố tình bấm một nút KHÁC gần đó.
3. Mong thấy: app nói “Mình sẽ hướng dẫn tiếp từ màn hình này.” rồi lập kế hoạch
   lại từ màn hình hiện tại — không kẹt, không mắng, không cần bấm lại từ đầu.

Bằng chứng cần quay: câu nói phục hồi + vòng khoanh mới xuất hiện.

## 2. Dừng khẩn cấp (hard stop)

1. Bắt đầu một hướng dẫn bất kỳ, chờ tới lúc đang chờ bấm (vòng khoanh hiện).
2. Bấm nút Dừng trên thẻ hướng dẫn.
3. Mong thấy: thẻ hướng dẫn biến mất ngay, vòng khoanh tắt, máy im lặng.
   Thử nói “Giúp bà…” sau đó để xác nhận app vẫn sẵn sàng nhận lệnh mới.

Bằng chứng cần quay: một tap Dừng → toàn bộ overlay tắt hẳn.

## 3. Kiểm tra trước khi gửi (pre-send check)

1. Nói “Nhắn tin cho <người thân> là <nội dung>”.
2. Khi app đọc lại nội dung và hỏi xác nhận, KHÔNG bấm Dùng ngay: bấm Sửa câu này,
   sửa một chữ, rồi mới bấm Dùng.
3. Trước khi gửi thật, app hỏi lại người nhận/nội dung (CONFIRMING_COPY).
   Chỉ bấm Dùng khi mọi chữ đã đúng.

Bằng chứng cần quay: thẻ xác nhận sao chép + câu TTS đọc lại đúng nội dung đã sửa.

## Lưu ý khi quay

- Giữ mỗi cảnh dưới 60 giây, quay dọc màn hình điện thoại.
- Nếu app báo “Chưa chụp được màn hình”, giữ nguyên màn hình 2 giây rồi bấm
  Kiểm tra lại (bản mới đã tự thử lại một lần trước khi báo).
- Nếu “Đang kiểm tra bước vừa làm…” quá 15 giây, app sẽ tự hỏi lại thay vì im lặng.
