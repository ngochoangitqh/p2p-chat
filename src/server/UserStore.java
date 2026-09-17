package server;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Lưu trữ và xác thực thông tin user (file-based).
 */
public class UserStore {
    private static final String DATA_FILE = "users.dat";
    private final Map<String, String> users = new HashMap<>(); // username -> password

    public UserStore() {
        load();
    }

    /** Đăng ký user mới. Trả về false nếu username đã tồn tại. */
    public synchronized boolean register(String username, String password) {
        if (users.containsKey(username)) return false;
        users.put(username, password);
        save();
        return true;
    }

    /** Xác thực đăng nhập. */
    public synchronized boolean authenticate(String username, String password) {
        return password.equals(users.get(username));
    }

    private void load() {
        File f = new File(DATA_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) users.put(parts[0], parts[1]);
            }
        } catch (IOException e) {
            System.err.println("[UserStore] Lỗi đọc file: " + e.getMessage());
        }
    }

    private void save() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(DATA_FILE))) {
            for (Map.Entry<String, String> e : users.entrySet()) {
                pw.println(e.getKey() + ":" + e.getValue());
            }
        } catch (IOException e) {
            System.err.println("[UserStore] Lỗi ghi file: " + e.getMessage());
        }
    }
}
