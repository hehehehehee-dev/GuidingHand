# GuidingHand — hướng sản phẩm, 27/10/2026

Đây là hướng sản phẩm được triển khai trên nhánh `feat/elder-coach-core`. Nghiệm thu trên điện thoại vẫn chưa hoàn tất; source và kiểm tra giả lập không chứng minh tác vụ thật đã thành công. Không thay bản điện thoại trước khi qua cổng kiểm tra gửi ảnh.

## Mục tiêu

Giúp người lớn tuổi tự dùng điện thoại, có cảm giác gia đình đồng hành: nói điều cần làm, nhận một chỉ dẫn, tự bấm, được hướng dẫn tiếp khi đi nhầm. Gia đình thiết lập một lần; hằng ngày không cần mở GuidingHand hay gõ chữ. AI không giả làm người cháu.

## Hướng dự thi

Trang chính thức Meta Global AI Developer Hackathon công bố hạn **04/11/2026, 21:00 ET**, tương ứng America/Indianapolis ngày đó; lịch có thể đổi. Indie được xét về sáng tạo, tham vọng kỹ thuật và khả năng hoạt động. Đây là sự kiện phù hợp với thông tin hiện có, **không xác nhận người dùng đã đăng ký hoặc chọn track**. Không bảo đảm giải thưởng. [Nguồn sự kiện](https://cerebralvalley.ai/e/meta-ai-developer-hackathon)

Điểm khác biệt: hiểu mục tiêu, chỉ giao diện thật, phục hồi và giữ quyền điều khiển cho người dùng.

## Phạm vi

**Cập nhật theo yêu cầu ngày 28/10:** mở phạm vi hướng dẫn sang các app Android đã cài, đọc được và không nhạy cảm. Spark lập bước từ mục tiêu, hội thoại và màn hình thật, không giới hạn bằng danh sách tác vụ Zalo. Không tuyên bố mọi app/màn hình hoạt động khi chưa quan sát thực tế. Giữ kiểm chứng riêng cho gửi ảnh Zalo: tên người nhận ở tiêu đề, ảnh mới và bằng chứng gửi mới.

Spark hiểu yêu cầu và hỏi lại theo ngữ cảnh; không khớp vài câu ví dụ thành kịch bản. Yêu cầu mới đã cho phép hướng dẫn YouTube, đọc báo, Instagram và các app khác. Mọi thao tác vẫn do người dùng thực hiện. Không có API tự bấm/nhập/gửi/gọi.

### Điểm vào xuyên ứng dụng

Bắt đầu từ **bất kỳ màn hình đã mở khóa, đọc được và không nhạy cảm**, kể cả ứng dụng khác. Giữ mục tiêu và hội thoại qua chuyển ứng dụng hoặc mở nhầm. Danh sách tên/package app được đọc từ máy, vị trí biểu tượng lấy từ màn hình hiện tại. Kết quả tác vụ chung hỏi người dùng xác nhận; không giả làm bằng chứng máy xác minh đã gửi.

Khi bắt đầu và mỗi lần đổi cửa sổ: đang ở ứng dụng đích thì tiếp tục; đang ở nơi khác thì hướng dẫn người dùng về Home, tìm Search/danh sách ứng dụng/thư mục theo giao diện thực tế. Chỉ đúng biểu tượng, chờ người dùng bấm, xác minh package đích đã mở. Không mặc định vuốt lên trên mọi Android; không chắc thì hỏi.

Không tự Home, vuốt, bấm, nhập, gửi hay xác nhận. **Mở giúp tôi** chỉ xuất hiện ở bước launcher và chỉ mở ứng dụng sau khi người dùng bấm nút đó.

## Thiết kế

Nghiên cứu W3C nhấn mạnh khác biệt về thị lực, vận động, nghe và trí nhớ; không coi mọi người lớn tuổi giống nhau. Android khuyên vùng chạm tối thiểu 48×48dp, tương phản chữ nhỏ 4.5:1. Các kích thước lớn hơn dưới đây là **quyết định thiết kế cần thử với người dùng**. [W3C](https://www.w3.org/WAI/older-users/) · [Android](https://developer.android.com/guide/topics/ui/accessibility/apps)

| Token đề xuất | Giá trị |
|---|---|
| Nền thẻ | `#FFFCF5`, màu đặc |
| Chữ trên thẻ | `#18332A`; dùng cùng màu rõ cho chỉ dẫn, nhãn nút và nội dung |
| Vòng chỉ dẫn | Viền ngoài đen `#000000` và viền trong vàng sáng `#FFE600`, mỗi viền ít nhất 6dp; đệm ngoài mục tiêu ít nhất 12dp; không nhận chạm |
| Chữ chỉ dẫn / nhãn nút / nội dung | Ít nhất **24sp / 22sp / 20sp**, theo cỡ chữ hệ thống |
| Vùng chạm chính / phụ | Ít nhất **72dp / 48dp**; khoảng cách tối thiểu 8dp |
| Thẻ | Rộng 280–344dp khi đủ chỗ; đệm 12–16dp; bo 20dp |

Đã đo màu chữ `#18332A` trên nền thẻ đặc `#FFFCF5` bằng công cụ Node tính luminance sRGB/WCAG: **13.258279585:1** (làm tròn 13.26:1), vượt yêu cầu tối thiểu **7:1** cho chỉ dẫn. `GuideDesign.contrastRatio` và các kiểm tra thiết kế kiểm tra lại cùng cặp màu; không coi tỷ lệ màu là kết quả thử nghiệm người lớn tuổi.

Thẻ đặc, vùng ngoài trong suốt; không dùng chữ mờ. Một câu, một vùng bấm; không thu nhỏ nút. Nội dung nở theo chữ hệ thống đến 200%, không cắt câu. [Cỡ chữ Android](https://developer.android.com/about/versions/14/features#non-linear-font-scaling)

Thẻ gần đáy, tránh mục tiêu, bàn phím và thanh điều hướng; chỉ dịch khi cần. Kéo/thu gọn là tùy chọn. Vòng không nhận chạm, không che nhãn nút, không nhấp nháy liên tục; tôn trọng giảm chuyển động. Không dùng màu làm tín hiệu duy nhất: luôn có câu chỉ dẫn và nhãn nút. Không dùng đỏ để báo bấm nhầm, không phụ thuộc màu xanh dương hoặc phân biệt đỏ–xanh lá. [W3C](https://www.w3.org/WAI/WCAG22/Understanding/animation-from-interactions.html)

Thiết lập một lần: ngôn ngữ, cách gọi người dùng, cách trợ lý xưng hô, liên hệ gia đình. Xem trước lời chào; không mặc định đóng vai cháu. Tiếng Anh là mặc định, tiếng Việt là lựa chọn đầy đủ: toàn bộ thẻ, cài đặt, lỗi và giọng nói dùng ngôn ngữ đã chọn; giữ nguyên tên riêng.

## Năm trạng thái

Ví dụ dưới đây dùng lựa chọn Bác/Tôi; phải đổi theo tùy chọn và ngôn ngữ.

| Trạng thái | Nội dung và hành vi |
|---|---|
| Sẵn sàng | Bong bóng nhỏ: **Giúp**. Chạm để nói, không mở trang chính. |
| Nghe / làm rõ | “Tôi đang nghe. Bác cứ nói nhé.” Hiện Xong/Hủy. Khi thiếu chi tiết, hỏi một câu; tối đa vài lựa chọn liên quan. |
| Hướng dẫn | “Bác bấm biểu tượng Zalo trong khung vàng nhé.” Một vòng, Nghe lại/Dừng; chờ người dùng. |
| Phục hồi / chưa đọc được | “Mình đang ở Danh bạ. Bác bấm Tin nhắn để tiếp tục nhé.” Giữ mục tiêu. Nếu không đọc được: “Tôi chưa nhìn rõ màn hình này.” Hướng dẫn thử lại cụ thể, không gán nhãn nguy hiểm. |
| Dừng bảo vệ | Mật khẩu/thanh toán/OTP: xóa vòng, dừng gửi dữ liệu. “Màn hình này có thông tin riêng tư. Tôi đã tạm dừng.” Gọi người thân mở trình quay số; không tự gọi. |

Không ép thời gian, khen kiểu trẻ con hoặc báo thành công thiếu bằng chứng. Kết thúc: “Ảnh đã gửi cho Lan.” Thông báo accessibility tránh đọc lặp. [W3C](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html)

## Luồng kỹ thuật triển khai

1. Đọc cây accessibility trước; chọn đúng cửa sổ đích. Thu âm cục bộ chỉ sau thao tác của người dùng; đóng cửa sổ mic trước khi đọc lại và trước khi gọi Transcribe. Audio ở RAM; không dùng màn hình an toàn cũ để cho phép upload trên màn hình hiện không đọc được.
2. Cổng an toàn chạy trên máy trước mọi lời gọi model. Nhạy cảm thì dừng; không đủ thông tin kết luận thì hỏi, không đoán.
3. Khi lập bước hướng dẫn, tạo đúng một snapshot đã qua cổng: ảnh và cây giao diện cùng trạng thái. Gửi mục tiêu, ảnh và cây cho Spark qua backend trong một `/core/step`; không ép Spark chỉ nhìn cây.
4. Spark trả một bước và mục tiêu; SAM 3.1 dùng lại chính ảnh đó để trả vùng trong cùng request backend. SHA-256 của bytes ảnh ràng buộc cả hai model và được echo về client; không có lần chụp thứ hai giữa Spark/SAM. Không chụp/gửi liên tục từng khung hình. Model không thực thi hành động. [Spark và SAM](https://dev.meta.ai/docs/media-segmentation)
5. Xác minh màn hình vẫn phù hợp, vẽ vòng, nói bằng Android TTS offline tốc độ 0.85. Muse Voice Transcribe chỉ là đầu vào lời nói. [Meta speech-to-text](https://dev.meta.ai/docs/speech-to-text)
6. Người dùng tự bấm. Đọc trạng thái mới, xác minh chuyển bước. Màn hình đổi khi đang nghĩ: bỏ kết quả cũ, đọc lại, giữ tác vụ. Bấm nhầm: lập bước từ trạng thái mới.

Giữ kiểm chứng gửi ảnh chặt: đóng thư viện hoặc tin nhắn cũ dịch vị trí không phải bằng chứng gửi mới. MockModel chỉ là stand-in phát triển sau một interface; không là đường tắt runtime song song. Key chỉ nằm ở backend. USB chỉ là cầu nối phát triển; **chưa tuyên bố có backend HTTPS production đã triển khai**.

## Ngân sách và bằng chứng

Trần **150 USD toàn dự án**, gồm khoản đã tiêu; kiểm tra số dư tài khoản/ledger. Phân bổ dự kiến: 60 USD tích hợp Spark/SAM, 45 USD kiểm tra/phục hồi, 15 USD giọng nói, 30 USD dự phòng. Đây không phải chi phí đã phát sinh hoặc cam kết tiêu hết. Hosting nếu phát sinh cũng phải được hạch toán trong trần tổng; credit model không mặc nhiên trả được phí hosting. Giữ ledger, giới hạn, timeout; không retry vô hạn.

Thứ tự thực hiện: **mic → nhìn đúng màn hình/vùng → phục hồi/xác minh → thiết kế hoàn thiện**. Các adapter Meta thật được nối qua backend; MockModel chỉ là lựa chọn phát triển được đánh dấu, không mặc định production và không chạy song song. Toàn bộ luồng mới vẫn cần kiểm chứng trên điện thoại; không dùng kết quả của bản 1.3 cũ làm bằng chứng. Xem [quy trình và bảng bằng chứng gửi ảnh](send-photo-phone-test.md).

Nghiệm thu: bắt đầu từ **Facebook, trình duyệt và Home**, vẫn đến đúng Zalo và hoàn tất tác vụ; mở nhầm ứng dụng vẫn phục hồi, giữ mục tiêu/người nhận/lịch sử. Mic hoạt động; màn hình nhạy cảm không gọi model; dừng/nhắc lại đúng; chữ lớn không che mục tiêu. Thử với người lớn tuổi thật. Ghi thời gian từng chặng, kết quả/thất bại. Tỷ lệ thành công, độ trễ, video: **TBD**, không dựng số liệu.
