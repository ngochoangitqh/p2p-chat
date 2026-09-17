package client;

import java.io.*;
import java.net.*;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * Xử lý kết nối P2P trực tiếp giữa hai peer.
 * Nếu kết nối trực tiếp thất bại (do tường lửa, NAT, cùng máy hoặc khác mạng),
 * tự động chuyển tiếp (Relay) qua Server trung gian như cơ chế của Skype.
 */
public class P2PConnection {
    private final String peerUsername;
    private final String peerIp;
    private final int peerPort;
    private final Client client;

    public P2PConnection(String peerUsername, String peerIp, int peerPort, Client client) {
        this.peerUsername = peerUsername;
        this.peerIp = peerIp;
        this.peerPort = peerPort;
        this.client = client;
    }

    public P2PConnection(String peerUsername, String peerIp, int peerPort) {
        this(peerUsername, peerIp, peerPort, null);
    }

    public String getPeerUsername() { return peerUsername; }
    public String getPeerIp() { return peerIp; }
    public int getPeerPort() { return peerPort; }

    private Socket createDirectSocket() throws IOException {
        Socket s = new Socket();
        // Giới hạn timeout 1.5 giây để nếu bị firewall chặn thì lập tức fallback sang Server
        s.connect(new InetSocketAddress(peerIp, peerPort), 1500);
        return s;
    }

    /**
     * Gửi tin nhắn text tới peer (ưu tiên P2P trực tiếp, fallback qua Server Relay).
     */
    public void sendMessage(String sender, String content) {
        new Thread(() -> {
            String escaped = content.replace("|", "\\|");
            // Thử P2P trực tiếp
            try (Socket s = createDirectSocket();
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true)) {
                pw.println("MSG|" + sender + "|" + escaped);
                System.out.println("[P2P Trực tiếp] Đã gửi tin nhắn tới " + peerUsername);
                return;
            } catch (Exception e) {
                System.out.println("[P2P] Không thể kết nối trực tiếp tới " + peerUsername + " (" + peerIp + ":" + peerPort + "). Tự động chuyển tiếp qua Server Relay...");
            }

            // Fallback qua Server Relay
            if (client != null && client.isConnected()) {
                client.sendRelay("RELAY_MSG|" + peerUsername + "|" + escaped);
                System.out.println("[Server Relay] Đã gửi tin nhắn qua Server tới " + peerUsername);
            } else {
                System.err.println("[Lỗi] Không thể gửi tin tới " + peerUsername + ": mất kết nối.");
            }
        }).start();
    }

    /**
     * Gửi thông báo có file để peer quyết định tải hay không.
     */
    public void sendFileOffer(String sender, String fileId, String filename, long fileSize) {
        new Thread(() -> {
            try (Socket s = createDirectSocket();
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true)) {
                pw.println("FILE_OFFER|" + sender + "|" + fileId + "|" + filename + "|" + fileSize);
                System.out.println("[P2P Trực tiếp] Đã gửi thông báo file tới " + peerUsername);
                return;
            } catch (Exception e) {
                System.out.println("[P2P] Gửi thông báo file trực tiếp thất bại, chuyển tiếp qua Server Relay...");
            }

            if (client != null && client.isConnected()) {
                client.sendRelay("RELAY_FILE_OFFER|" + peerUsername + "|" + fileId + "|" + filename + "|" + fileSize);
                System.out.println("[Server Relay] Đã gửi thông báo file qua Server tới " + peerUsername);
            }
        }).start();
    }

    /**
     * Gửi yêu cầu bắt đầu tải file về.
     */
    public void requestFileDownload(String sender, String fileId) {
        new Thread(() -> {
            try (Socket s = createDirectSocket();
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true)) {
                pw.println("FILE_REQUEST|" + sender + "|" + fileId);
                System.out.println("[P2P Trực tiếp] Đã gửi yêu cầu tải file tới " + peerUsername);
                return;
            } catch (Exception e) {
                System.out.println("[P2P] Yêu cầu tải file trực tiếp thất bại, chuyển tiếp qua Server Relay...");
            }

            if (client != null && client.isConnected()) {
                client.sendRelay("RELAY_FILE_REQ|" + peerUsername + "|" + fileId);
                System.out.println("[Server Relay] Đã gửi yêu cầu tải file qua Server tới " + peerUsername);
            }
        }).start();
    }

    /**
     * Gửi từ chối tải file.
     */
    public void rejectFileOffer(String sender, String fileId) {
        new Thread(() -> {
            try (Socket s = createDirectSocket();
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true)) {
                pw.println("FILE_REJECT|" + sender + "|" + fileId);
                return;
            } catch (Exception e) {
                System.out.println("[P2P] Từ chối file trực tiếp thất bại, chuyển tiếp qua Server Relay...");
            }

            if (client != null && client.isConnected()) {
                client.sendRelay("RELAY_FILE_REJECT|" + peerUsername + "|" + fileId);
            }
        }).start();
    }

    /**
     * Gửi nội dung file tới peer theo yêu cầu.
     */
    public void sendFileChunks(String sender, String fileId, File file,
                               Consumer<Integer> progressCallback,
                               Consumer<Boolean> doneCallback) {
        new Thread(() -> {
            boolean directOk = false;
            long fileSize = file.length();

            // Thử gửi trực tiếp qua P2P socket
            try (Socket s = createDirectSocket();
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true);
                 FileInputStream fis = new FileInputStream(file)) {

                pw.println("FILE_DATA_START|" + sender + "|" + fileId + "|" + file.getName() + "|" + fileSize);

                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalRead = 0;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] chunk = (bytesRead == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);
                    String encoded = Base64.getEncoder().encodeToString(chunk);
                    pw.println("FILE_DATA|" + fileId + "|" + encoded);
                    totalRead += bytesRead;
                    if (progressCallback != null && fileSize > 0) {
                        int pct = (int) (totalRead * 100 / fileSize);
                        progressCallback.accept(pct);
                    }
                }
                pw.println("FILE_DATA_END|" + fileId);
                if (doneCallback != null) doneCallback.accept(true);
                directOk = true;
                System.out.println("[P2P Trực tiếp] Đã truyền xong file tới " + peerUsername);

            } catch (Exception e) {
                System.out.println("[P2P] Truyền file trực tiếp thất bại (" + e.getMessage() + "), chuyển tiếp qua Server Relay...");
            }

            // Fallback gửi qua Server Relay
            if (!directOk && client != null && client.isConnected()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    client.sendRelay("RELAY_FILE_START|" + peerUsername + "|" + fileId + "|" + file.getName() + "|" + fileSize);

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalRead = 0;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        byte[] chunk = (bytesRead == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);
                        String encoded = Base64.getEncoder().encodeToString(chunk);
                        client.sendRelay("RELAY_FILE_CHUNK|" + peerUsername + "|" + fileId + "|" + encoded);
                        totalRead += bytesRead;
                        if (progressCallback != null && fileSize > 0) {
                            int pct = (int) (totalRead * 100 / fileSize);
                            progressCallback.accept(pct);
                        }
                    }
                    client.sendRelay("RELAY_FILE_DONE|" + peerUsername + "|" + fileId + "|" + file.getName());
                    if (doneCallback != null) doneCallback.accept(true);
                    System.out.println("[Server Relay] Đã truyền xong file qua Server tới " + peerUsername);
                } catch (Exception ex) {
                    System.err.println("[Server Relay] Lỗi gửi file qua server: " + ex.getMessage());
                    if (doneCallback != null) doneCallback.accept(false);
                }
            }
        }).start();
    }
}
