# P2P Chat Application (Java Swing + Socket)

Ứng dụng nhắn tin và truyền tải tập tin theo mô hình lai **P2P / Client-Server** (lấy cảm hứng từ kiến trúc Skype / Napster), được viết hoàn toàn bằng **Java Core (JDK thuần)** và giao diện **Java Swing**.

## Tính năng nổi bật

- **Kiến trúc mạng lai (Hybrid P2P)**:
  - **Server trung tâm (Port 5000)**: Quản lý đăng ký, đăng nhập và theo dõi danh sách người dùng đang online.
  - **Kết nối P2P trực tiếp**: Các máy trạm (Peer) kết nối Socket trực tiếp với nhau để chat và truyền tải file mà không cần trung chuyển qua server.
- **Giao diện người dùng phong cách Messenger**:
  - Giao diện Dark Mode hiện đại, các bong bóng chat xếp sát nhau liên tục.
  - Không bị lỗi font, hiển thị tốt tiếng Việt UTF-8 trên Windows.
- **Gửi và tải file thủ công**:
  - Khi gửi file, giao diện hiển thị thẻ đính kèm `[FILE]`.
  - Phía người nhận có nút **"Tải về"** và có thể click trực tiếp vào thẻ để chọn thư mục lưu và đặt tên tuỳ ý.
  - Hỗ trợ thanh tiến trình theo dõi tiến độ tải file theo từng chunk dữ liệu.
- **Lưu trữ lịch sử tin nhắn bằng HashMap & Tự động khôi phục**:
  - Lưu trữ tin nhắn trong bộ nhớ bằng `HashMap<String, List<ChatMessage>>`.
  - Tự động đồng bộ ra file lưu trữ riêng của từng tài khoản.
  - Khi đăng xuất rồi đăng nhập lại hoặc đóng/mở tab, toàn bộ tin nhắn cũ và file chia sẻ vẫn được khôi phục nguyên vẹn.

## Cấu trúc thư mục

```
p2p-chat/
├── src/
│   ├── server/
│   │   ├── Server.java          # Server trung tâm
│   │   └── UserStore.java       # Quản lý tài khoản người dùng
│   └── client/
│       ├── Client.java          # Client kết nối server & quản lý HashMap lịch sử
│       ├── ChatMessage.java     # Model dữ liệu tin nhắn & file
│       ├── P2PConnection.java   # Socket gửi tin nhắn / file trực tiếp tới peer
│       ├── P2PServer.java       # Socket lắng nghe tin nhắn / file từ peer
│       └── gui/
│           ├── App.java         # Khởi chạy giao diện Client
│           ├── LoginFrame.java  # Đăng nhập / Đăng ký
│           ├── MainFrame.java   # Danh sách Online & các tab chat
│           └── ChatPanel.java   # Khung chat với từng người dùng
├── compile.bat                  # Script biên dịch mã nguồn
├── run_server.bat               # Script khởi động Server
└── run_client.bat               # Script khởi động Client
```

## Hướng dẫn cài đặt & Chạy

### Yêu cầu hệ thống
- Java Development Kit (JDK 17 trở lên).

### Các bước thực hiện

1. **Biên dịch mã nguồn**:
   ```cmd
   compile.bat
   ```

2. **Khởi động Server trung tâm**:
   ```cmd
   run_server.bat
   ```

3. **Khởi động Client (chạy 2 hoặc nhiều lần để mở nhiều client)**:
   ```cmd
   run_client.bat
   ```

4. **Đăng ký tài khoản, đăng nhập và trò chuyện**:
   - Double-click vào tài khoản trong danh sách Online để mở tab trò chuyện.
   - Nhập nội dung và nhấn **Gửi** hoặc phím `Enter`.
   - Bấm **Chọn File** để gửi tài liệu cho đối phương.
