package client;

import java.io.*;
import java.net.*;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * Xử lý một kết nối P2P với peer khác.
 * Dùng cho cả việc GỬI chat message và GỬI file.
 */
public class P2PConnection {
    private final String peerUsername;
    private final String peerIp;
    private final int peerPort;

    public P2PConnection(String peerUsername, String peerIp, int peerPort) {
        this.peerUsername = peerUsername;
        this.peerIp = peerIp;
        this.peerPort = peerPort;
    }

    public String getPeerUsername() { return peerUsername; }
    public String getPeerIp() { return peerIp; }
    public int getPeerPort() { return peerPort; }

    /**
     * Gửi tin nhắn text tới peer.
     * Giao thức: MSG|sender|content
     */
    public void sendMessage(String sender, String content) {
        new Thread(() -> {
            try (Socket s = new Socket(peerIp, peerPort);
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true)) {
                // Escape ký tự | trong nội dung
                String escaped = content.replace("|", "\\|");
                pw.println("MSG|" + sender + "|" + escaped);
            } catch (IOException e) {
                System.err.println("[P2P] Không thể gửi tin tới " + peerUsername + ": " + e.getMessage());
            }
        }).start();
    }

    /**
     * Gửi thông báo có file để peer quyết định tải hay không.
     * Giao thức: FILE_OFFER|sender|fileId|filename|filesize
     */
    public void sendFileOffer(String sender, String fileId, String filename, long fileSize) {
        new Thread(() -> {
            try (Socket s = new Socket(peerIp, peerPort);
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true)) {
                pw.println("FILE_OFFER|" + sender + "|" + fileId + "|" + filename + "|" + fileSize);
            } catch (IOException e) {
                System.err.println("[P2P] Không thể gửi thông báo file tới " + peerUsername + ": " + e.getMessage());
            }
        }).start();
    }

    /**
     * Gửi yêu cầu bắt đầu tải file về.
     * Giao thức: FILE_REQUEST|sender|fileId
     */
    public void requestFileDownload(String sender, String fileId) {
        new Thread(() -> {
            try (Socket s = new Socket(peerIp, peerPort);
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true)) {
                pw.println("FILE_REQUEST|" + sender + "|" + fileId);
            } catch (IOException e) {
                System.err.println("[P2P] Không thể gửi yêu cầu tải file: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Gửi từ chối tải file.
     * Giao thức: FILE_REJECT|sender|fileId
     */
    public void rejectFileOffer(String sender, String fileId) {
        new Thread(() -> {
            try (Socket s = new Socket(peerIp, peerPort);
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true)) {
                pw.println("FILE_REJECT|" + sender + "|" + fileId);
            } catch (IOException e) {
                System.err.println("[P2P] Không thể gửi từ chối file: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Gửi nội dung file tới peer theo yêu cầu.
     * Giao thức:
     *   FILE_DATA_START|sender|fileId|filename|filesize
     *   FILE_DATA|fileId|base64_chunk
     *   FILE_DATA_END|fileId
     */
    public void sendFileChunks(String sender, String fileId, File file,
                               Consumer<Integer> progressCallback,
                               Consumer<Boolean> doneCallback) {
        new Thread(() -> {
            try (Socket s = new Socket(peerIp, peerPort);
                 PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true);
                 FileInputStream fis = new FileInputStream(file)) {

                long fileSize = file.length();
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

            } catch (IOException e) {
                System.err.println("[P2P] Lỗi gửi nội dung file: " + e.getMessage());
                if (doneCallback != null) doneCallback.accept(false);
            }
        }).start();
    }
}
