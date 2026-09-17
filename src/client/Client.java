package client;

import client.gui.MainFrame;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý kết nối tới Server trung tâm và duy trì bảng peer online.
 */
public class Client {
    private static final String DEFAULT_HOST = "localhost";
    private static final int SERVER_PORT = 5000;

    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    private String username;
    private int p2pPort;
    private MainFrame mainFrame;

    // Bảng peer online: username -> [ip, port]
    private final Map<String, String[]> peerMap = new ConcurrentHashMap<>();

    // Lưu trữ lịch sử tin nhắn bằng HashMap: peerUsername -> danh sách tin nhắn List<ChatMessage>
    private final HashMap<String, List<ChatMessage>> messageHistory = new HashMap<>();

    // Listener thread
    private Thread listenerThread;
    private volatile boolean connected = false;

    public Client() {}

    // -------------------------------------------------------
    //  Kết nối tới server
    // -------------------------------------------------------
    public boolean connect(String host, int serverPort) {
        try {
            socket = new Socket(host, serverPort);
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            connected = true;
            return true;
        } catch (IOException e) {
            System.err.println("[Client] Không thể kết nối server: " + e.getMessage());
            return false;
        }
    }

    public boolean connect(String host) {
        return connect(host, SERVER_PORT);
    }

    // -------------------------------------------------------
    //  Gửi lệnh tới server (đồng bộ, chờ 1 dòng phản hồi)
    // -------------------------------------------------------
    private String sendCmd(String cmd) {
        if (!connected) return "ERROR|Chưa kết nối server";
        writer.println(cmd);
        try {
            String resp = reader.readLine();
            return resp != null ? resp : "ERROR|Mất kết nối";
        } catch (IOException e) {
            return "ERROR|" + e.getMessage();
        }
    }

    /**
     * Đăng ký tài khoản mới.
     * Trả về null nếu thành công, chuỗi lỗi nếu thất bại.
     */
    public String register(String user, String password) {
        String resp = sendCmd("REGISTER|" + user + "|" + password);
        if (resp.startsWith("OK")) return null;
        return resp.contains("|") ? resp.split("\\|", 2)[1] : resp;
    }

    /**
     * Đăng nhập. Sau đó bắt đầu listener thread nhận event từ server.
     * Trả về null nếu thành công, chuỗi lỗi nếu thất bại.
     */
    public String login(String user, String password, int p2pPort, MainFrame frame) {
        this.p2pPort   = p2pPort;
        this.mainFrame = frame;

        String resp = sendCmd("LOGIN|" + user + "|" + password + "|" + p2pPort);
        if (!resp.startsWith("OK")) {
            return resp.contains("|") ? resp.split("\\|", 2)[1] : resp;
        }
        this.username = user;

        // Khôi phục HashMap lịch sử tin nhắn từ file lưu trữ của user
        loadHistoryFromFile();

        // Đọc dòng USERS ngay tiếp theo (server gửi sau OK)
        try {
            String usersLine = reader.readLine();
            if (usersLine != null) parseUsers(usersLine);
        } catch (IOException e) {
            System.err.println("[Client] Lỗi đọc danh sách user: " + e.getMessage());
        }

        // Bắt đầu lắng nghe event bất đồng bộ từ server
        startListener();
        return null;
    }

    /** Đăng xuất và đóng kết nối. */
    public void logout() {
        saveHistoryToFile();
        connected = false;
        writer.println("LOGOUT");
        try { socket.close(); } catch (IOException ignored) {}
        if (listenerThread != null) listenerThread.interrupt();
    }

    // -------------------------------------------------------
    //  Listener thread: nhận USER_JOINED / USER_LEFT từ server
    // -------------------------------------------------------
    private void startListener() {
        listenerThread = new Thread(() -> {
            try {
                String line;
                while (connected && (line = reader.readLine()) != null) {
                    handleServerEvent(line);
                }
            } catch (IOException e) {
                if (connected) {
                    System.err.println("[Client] Mất kết nối server: " + e.getMessage());
                    if (mainFrame != null) mainFrame.onServerDisconnected();
                }
            }
        }, "ServerListener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void handleServerEvent(String line) {
        if (line.startsWith("USER_JOINED|")) {
            // USER_JOINED|username:ip:port
            String info = line.substring("USER_JOINED|".length());
            String[] parts = info.split(":");
            if (parts.length >= 3) {
                String user = parts[0];
                String ip   = parts[1];
                String port = parts[2];
                peerMap.put(user, new String[]{ip, port});
                if (mainFrame != null) mainFrame.onUserJoined(user, ip, Integer.parseInt(port));
            }
        } else if (line.startsWith("USER_LEFT|")) {
            String user = line.substring("USER_LEFT|".length()).trim();
            peerMap.remove(user);
            if (mainFrame != null) mainFrame.onUserLeft(user);
        }
    }

    private void parseUsers(String usersLine) {
        // USERS|user1:ip:port,user2:ip:port,...
        String payload = usersLine.startsWith("USERS|") ? usersLine.substring(6) : usersLine;
        if (payload.isBlank()) return;
        for (String entry : payload.split(",")) {
            String[] parts = entry.split(":");
            if (parts.length >= 3) {
                peerMap.put(parts[0], new String[]{parts[1], parts[2]});
            }
        }
    }

    // -------------------------------------------------------
    //  Gửi chat / file tới peer
    // -------------------------------------------------------
    public P2PConnection getPeerConnection(String peerUsername) {
        String[] info = peerMap.get(peerUsername);
        if (info == null) return null;
        return new P2PConnection(peerUsername, info[0], Integer.parseInt(info[1]));
    }

    /** Danh sách peer hiện tại (không gồm bản thân). */
    public Map<String, String[]> getPeerMap() {
        return Collections.unmodifiableMap(peerMap);
    }

    // -------------------------------------------------------
    //  Quản lý lưu trữ tin nhắn bằng HashMap
    // -------------------------------------------------------

    /** Lưu tin nhắn mới vào HashMap theo peer và tự động ghi ra file */
    public synchronized void addMessage(String peerUsername, ChatMessage message) {
        messageHistory.computeIfAbsent(peerUsername, k -> new ArrayList<>()).add(message);
        System.out.println("[Client] Đã lưu tin nhắn vào HashMap cho peer [" + peerUsername + "]: " + message.getContent());
        saveHistoryToFile();
    }

    /** Lấy danh sách tin nhắn từ HashMap theo peer */
    public synchronized List<ChatMessage> getMessageHistory(String peerUsername) {
        List<ChatMessage> list = messageHistory.get(peerUsername);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    /** Truy xuất trực tiếp HashMap lưu tin nhắn */
    public synchronized HashMap<String, List<ChatMessage>> getMessageHistoryMap() {
        return messageHistory;
    }

    /** Lưu HashMap lịch sử tin nhắn ra file riêng của tài khoản */
    public synchronized void saveHistoryToFile() {
        if (username == null || username.isBlank()) return;
        File file = new File("history_" + username + ".dat");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(messageHistory);
            oos.flush();
        } catch (Exception e) {
            System.err.println("[Client] Lỗi lưu lịch sử tin nhắn vào file: " + e.getMessage());
        }
    }

    /** Nạp lại HashMap lịch sử tin nhắn từ file khi đăng nhập lại */
    @SuppressWarnings("unchecked")
    public synchronized void loadHistoryFromFile() {
        if (username == null || username.isBlank()) return;
        File file = new File("history_" + username + ".dat");
        if (!file.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = ois.readObject();
            if (obj instanceof HashMap<?, ?> map) {
                messageHistory.clear();
                messageHistory.putAll((HashMap<String, List<ChatMessage>>) map);
                System.out.println("[Client] Đã nạp thành công lịch sử tin nhắn từ " + file.getName()
                        + " (gồm " + messageHistory.size() + " người dùng)");
            }
        } catch (Exception e) {
            System.err.println("[Client] Lỗi đọc lịch sử tin nhắn từ file: " + e.getMessage());
        }
    }

    public String getUsername() { return username; }
    public boolean isConnected() { return connected; }
}
