package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Server trung tâm: quản lý đăng ký, đăng nhập, danh sách user online.
 * Mỗi client kết nối được xử lý trong một thread riêng.
 *
 * Giao thức (text, mỗi dòng một message):
 *   Client → Server:
 *     REGISTER|username|password
 *     LOGIN|username|password|p2p_port
 *     GET_USERS
 *     LOGOUT
 *
 *   Server → Client:
 *     OK|message
 *     ERROR|message
 *     USERS|user1:ip:port,user2:ip:port,...
 *     USER_JOINED|username:ip:port
 *     USER_LEFT|username
 */
public class Server {
    public static final int PORT = 5000;

    // username -> ClientHandler (chỉ user đang online)
    private static final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();
    private static final UserStore userStore = new UserStore();

    public static void main(String[] args) throws IOException {
        System.out.println("=== P2P Chat Server ===");
        System.out.println("Đang lắng nghe trên port " + PORT + "...");

        ServerSocket serverSocket = new ServerSocket(PORT);
        // Cho phép tái sử dụng port ngay sau khi server tắt
        serverSocket.setReuseAddress(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { serverSocket.close(); } catch (IOException ignored) {}
            System.out.println("Server đã dừng.");
        }));

        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                new Thread(new ClientHandler(socket)).start();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("[Server] Lỗi chấp nhận kết nối: " + e.getMessage());
                }
            }
        }
    }

    // =========================================================
    //  Static helpers để ClientHandler gọi
    // =========================================================

    static boolean register(String username, String password) {
        return userStore.register(username, password);
    }

    static boolean authenticate(String username, String password) {
        return userStore.authenticate(username, password);
    }

    /** Thêm user vào danh sách online và broadcast cho tất cả. */
    static void addOnlineUser(String username, ClientHandler handler) {
        onlineUsers.put(username, handler);
        String info = username + ":" + handler.getClientIp() + ":" + handler.getP2pPort();
        broadcastExcept(username, "USER_JOINED|" + info);
    }

    /** Xóa user khỏi danh sách online và broadcast. */
    static void removeOnlineUser(String username) {
        onlineUsers.remove(username);
        broadcastExcept(username, "USER_LEFT|" + username);
    }

    /** Trả về chuỗi danh sách user online: "user1:ip:port,user2:ip:port,..." */
    static String getOnlineUsersString(String excludeUsername) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ClientHandler> e : onlineUsers.entrySet()) {
            if (e.getKey().equals(excludeUsername)) continue;
            if (sb.length() > 0) sb.append(",");
            sb.append(e.getKey())
              .append(":").append(e.getValue().getClientIp())
              .append(":").append(e.getValue().getP2pPort());
        }
        return sb.toString();
    }

    /** Gửi message tới tất cả user online trừ excludeUsername. */
    private static void broadcastExcept(String excludeUsername, String message) {
        for (Map.Entry<String, ClientHandler> e : onlineUsers.entrySet()) {
            if (!e.getKey().equals(excludeUsername)) {
                e.getValue().send(message);
            }
        }
    }

    // =========================================================
    //  Inner class: xử lý mỗi client trong thread riêng
    // =========================================================

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private String username = null;
        private int p2pPort = 0;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        String getClientIp() {
            return socket.getInetAddress().getHostAddress();
        }

        int getP2pPort() {
            return p2pPort;
        }

        void send(String message) {
            if (writer != null) writer.println(message);
        }

        @Override
        public void run() {
            String clientAddr = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
            System.out.println("[+] Kết nối mới từ " + clientAddr);

            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true);

                String line;
                while ((line = reader.readLine()) != null) {
                    handleMessage(line.trim());
                }
            } catch (IOException e) {
                // Client ngắt kết nối
            } finally {
                cleanup();
                System.out.println("[-] Ngắt kết nối: " + clientAddr);
            }
        }

        private void handleMessage(String msg) {
            if (msg.isEmpty()) return;
            String[] parts = msg.split("\\|", -1);
            String cmd = parts[0];

            switch (cmd) {
                case "REGISTER":
                    if (parts.length < 3) { send("ERROR|Thiếu tham số"); return; }
                    if (register(parts[1], parts[2])) {
                        send("OK|Đăng ký thành công");
                        System.out.println("[Register] " + parts[1]);
                    } else {
                        send("ERROR|Username đã tồn tại");
                    }
                    break;

                case "LOGIN":
                    if (parts.length < 4) { send("ERROR|Thiếu tham số"); return; }
                    String uname = parts[1];
                    String pwd   = parts[2];
                    try { p2pPort = Integer.parseInt(parts[3]); }
                    catch (NumberFormatException e) { send("ERROR|P2P port không hợp lệ"); return; }

                    if (onlineUsers.containsKey(uname)) {
                        send("ERROR|User đang online ở nơi khác");
                        return;
                    }
                    if (authenticate(uname, pwd)) {
                        this.username = uname;
                        // Gửi danh sách user online hiện tại trước khi add mình vào
                        String currentUsers = getOnlineUsersString(uname);
                        send("OK|" + uname);
                        send("USERS|" + currentUsers);
                        addOnlineUser(uname, this);
                        System.out.println("[Login] " + uname + " (P2P port: " + p2pPort + ")");
                    } else {
                        send("ERROR|Sai username hoặc mật khẩu");
                    }
                    break;

                case "GET_USERS":
                    if (username == null) { send("ERROR|Chưa đăng nhập"); return; }
                    send("USERS|" + getOnlineUsersString(username));
                    break;

                case "LOGOUT":
                    cleanup();
                    send("OK|Đã đăng xuất");
                    break;

                default:
                    send("ERROR|Lệnh không hợp lệ: " + cmd);
            }
        }

        private void cleanup() {
            if (username != null) {
                removeOnlineUser(username);
                System.out.println("[Logout] " + username);
                username = null;
            }
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
