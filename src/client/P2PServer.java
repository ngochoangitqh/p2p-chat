package client;

import client.gui.MainFrame;

import java.io.*;
import java.net.*;
import java.util.Base64;

/**
 * Server P2P phía client: lắng nghe kết nối từ peer khác.
 * Xử lý cả chat message và nhận file.
 */
public class P2PServer implements Runnable {
    private final int port;
    private final MainFrame mainFrame;
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    private static final String DOWNLOAD_DIR = "downloads";
    private static final java.util.Map<String, File> pendingDownloads = new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerPendingDownload(String fileId, File targetFile) {
        pendingDownloads.put(fileId, targetFile);
    }

    public static File getPendingDownload(String fileId) {
        return pendingDownloads.get(fileId);
    }

    public P2PServer(int port, MainFrame mainFrame) {
        this.port = port;
        this.mainFrame = mainFrame;
        new File(DOWNLOAD_DIR).mkdirs();
    }

    public int getPort() { return port; }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            System.out.println("[P2PServer] Lắng nghe P2P trên port " + port);

            while (running) {
                try {
                    Socket conn = serverSocket.accept();
                    // Mỗi kết nối xử lý trong thread riêng
                    new Thread(() -> handleIncoming(conn)).start();
                } catch (IOException e) {
                    if (running) System.err.println("[P2PServer] Lỗi chấp nhận: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[P2PServer] Không thể mở port " + port + ": " + e.getMessage());
        }
    }

    private void handleIncoming(Socket conn) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {

            String firstLine = br.readLine();
            if (firstLine == null) return;

            if (firstLine.startsWith("MSG|")) {
                // Tin nhắn chat: MSG|sender|content
                String[] parts = firstLine.split("\\|", 3);
                if (parts.length >= 3) {
                    String sender  = parts[1];
                    String content = parts[2].replace("\\|", "|");
                    mainFrame.onMessageReceived(sender, content);
                }

            } else if (firstLine.startsWith("FILE_OFFER|")) {
                // FILE_OFFER|sender|fileId|filename|filesize
                String[] parts = firstLine.split("\\|", 5);
                if (parts.length >= 5) {
                    String sender   = parts[1];
                    String fileId   = parts[2];
                    String filename = parts[3];
                    long fileSize   = Long.parseLong(parts[4]);
                    mainFrame.onFileOfferReceived(sender, fileId, filename, fileSize);
                }

            } else if (firstLine.startsWith("FILE_REQUEST|")) {
                // FILE_REQUEST|sender|fileId
                String[] parts = firstLine.split("\\|", 3);
                if (parts.length >= 3) {
                    String requester = parts[1];
                    String fileId    = parts[2];
                    mainFrame.onFileRequestReceived(requester, fileId);
                }

            } else if (firstLine.startsWith("FILE_REJECT|")) {
                // FILE_REJECT|sender|fileId
                String[] parts = firstLine.split("\\|", 3);
                if (parts.length >= 3) {
                    String sender = parts[1];
                    String fileId = parts[2];
                    mainFrame.onFileRejectReceived(sender, fileId);
                }

            } else if (firstLine.startsWith("FILE_DATA_START|")) {
                // FILE_DATA_START|sender|fileId|filename|filesize
                String[] parts = firstLine.split("\\|", 5);
                if (parts.length < 5) return;
                String sender   = parts[1];
                String fileId   = parts[2];
                String filename = parts[3];
                long fileSize;
                try { fileSize = Long.parseLong(parts[4]); }
                catch (NumberFormatException e) { return; }

                File targetFile = pendingDownloads.remove(fileId);
                if (targetFile == null) {
                    targetFile = new File(DOWNLOAD_DIR, filename);
                }

                mainFrame.onFileTransferStarted(sender, filename, fileSize);

                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    long received = 0;
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("FILE_DATA_END|")) break;
                        if (line.startsWith("FILE_DATA|")) {
                            // FILE_DATA|fileId|base64
                            String[] dataParts = line.split("\\|", 3);
                            if (dataParts.length >= 3) {
                                byte[] data = Base64.getDecoder().decode(dataParts[2]);
                                fos.write(data);
                                received += data.length;
                                if (fileSize > 0) {
                                    int pct = (int) (received * 100 / fileSize);
                                    mainFrame.onFileTransferProgress(sender, filename, pct);
                                }
                            }
                        }
                    }
                }
                mainFrame.onFileTransferDone(sender, filename, targetFile.getAbsolutePath());
            }

        } catch (IOException e) {
            System.err.println("[P2PServer] Lỗi xử lý kết nối đến: " + e.getMessage());
        }
    }
}
