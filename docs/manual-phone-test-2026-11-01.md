# GuidingHand — kịch bản test điện thoại ngắn

Mục tiêu: quay **một video liên tục khoảng 4–6 phút** để kiểm tra luồng thật. Không gửi tin,
không gọi, không nhập mật khẩu, OTP hoặc thông tin thanh toán.

## Trước khi quay

- Giữ Xiaomi kết nối USB với máy tính. Backend local và cổng reverse `8768` phải đang chạy.
- Chỉ bật Accessibility của **GuidingHand Candidate**.
- Bật quay màn hình. Không đọc to dữ liệu riêng tư.
- Nếu app hiện nút Gửi, dừng trước khi bấm.

## Cảnh 1 — Chrome, wrong tap và recovery

1. Bắt đầu ở màn hình Home. Bấm **Giúp tôi** rồi bấm mic.
2. Nói: **“Giúp tôi tìm bài thơ Từ ấy của Tố Hữu bằng Chrome.”**
3. Ở transcript, bấm **Đúng rồi** nếu câu đúng.
4. Khi GuidingHand chỉ Chrome, cố tình bấm **Clock** một lần.
5. Chờ GuidingHand đọc màn hình mới. Không bấm Dừng và không bắt đầu lại yêu cầu.
6. Làm theo từng bước để quay lại và mở Chrome. Dừng cảnh khi app chỉ được ô tìm kiếm.

Đạt khi: mục tiêu cũ được giữ; app không quay lại bước hỏi ban đầu; mỗi lần chỉ có một câu,
một ring; không tự bấm, vuốt hoặc nhập; thẻ không che vùng được chỉ.

## Cảnh 2 — Từ Chrome sang Instagram và sửa lời nhắn

1. Đang ở Chrome, bấm **Giúp tôi** rồi nói: **“Tôi muốn nhắn tin cho một người trên Instagram.”**
2. Khi app hỏi người nhận, nói tên đúng như tiêu đề cuộc trò chuyện Instagram.
3. Tự mở Instagram và tự vào đúng cuộc trò chuyện theo từng ring.
4. Khi app hỏi nội dung, nói: **“Cháu sẽ gọi lại sau nhé.”**
5. Ở transcript, bấm **Sửa câu này**. Đổi thành **“Cháu sẽ gọi lại tối nay nhé.”**
6. Bấm **Dùng câu đã sửa** → **Đúng rồi** → **Sao chép nội dung** nếu các nút xuất hiện.
7. Tự dán vào ô soạn để kiểm tra, nhưng **không bấm Gửi**. Bấm **Dừng**.

Đạt khi: app hiểu cập nhật nhiều lượt; transcript sửa được; thẻ tự đo lại, không cắt nút;
người dùng vẫn là người tự mở app, chọn người, dán và quyết định gửi.

## Cảnh 3 — Safety gate

1. Mở một màn hình yêu cầu mật khẩu, PIN hoặc OTP, nhưng **không nhập gì**.
2. Bấm **Giúp tôi**.
3. Quan sát rồi bấm **Dừng**. Không bấm Gọi người thân.

Đạt khi: safety gate chạy trước model; ring biến mất; không chụp/gửi màn hình cho model;
chỉ còn lựa chọn **Gọi người thân** và **Dừng**; app không tự gọi.

## Gửi lại để review

Gửi một video liên tục. Kèm ba ghi chú ngắn:

1. Câu nào app nghe sai, nếu có.
2. Bước nào chờ lâu nhất.
3. Ring hoặc thẻ che nút ở cảnh nào.

Không kết luận ring accuracy, latency hoặc usability từ fixture. Chỉ video điện thoại thật và
thao tác thật trong ba cảnh này mới được dùng làm bằng chứng.
