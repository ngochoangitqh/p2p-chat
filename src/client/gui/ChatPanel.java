package client.gui;

import client.P2PConnection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

/**
 * Panel chat với một peer cụ thể.
 * Chứa: vùng hiển thị tin nhắn, ô nhập, nút gửi, nút gửi file, progress bar.
 */
public class ChatPanel extends JPanel {

    private final String myUsername;
    private final P2PConnection conn;
    private final MainFrame mainFrame;
    private final JPanel messagesContainer = new JPanel();
    private final JPanel messagesWrapper = new JPanel(new BorderLayout());
    private final JScrollPane scrollPane;
    private final JTextField inputField = new JTextField();
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel progressLabel = new JLabel();
    private final JPanel progressPanel = new JPanel();

    private final java.util.Map<String, File> sharedFiles = new java.util.concurrent.ConcurrentHashMap<>();

    public ChatPanel(String myUsername, P2PConnection conn, MainFrame mainFrame) {
        this.myUsername = myUsername;
        this.conn       = conn;
        this.mainFrame  = mainFrame;

        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBackground(new Color(30, 34, 45));
        messagesContainer.setBorder(new EmptyBorder(10, 12, 10, 12));

        messagesWrapper.setBackground(new Color(30, 34, 45));
        messagesWrapper.add(messagesContainer, BorderLayout.NORTH);

        scrollPane = new JScrollPane(messagesWrapper);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBackground(new Color(30, 34, 45));
        scrollPane.getViewport().setBackground(new Color(30, 34, 45));

        buildUI();
        loadHistory();
    }

    private void buildUI() {
        setLayout(new BorderLayout(0, 0));
        setBackground(new Color(30, 34, 45));

        add(scrollPane, BorderLayout.CENTER);

        // ── Bottom panel: input + buttons ─────────────────
        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.setBackground(new Color(24, 27, 38));
        bottom.setBorder(new EmptyBorder(8, 10, 8, 10));

        // Input field
        inputField.setBackground(new Color(45, 50, 65));
        inputField.setForeground(Color.WHITE);
        inputField.setCaretColor(Color.WHITE);
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 80, 110)),
                new EmptyBorder(4, 8, 4, 8)));
        inputField.addActionListener(e -> doSendMessage());
        bottom.add(inputField, BorderLayout.CENTER);

        // Nút Send + File
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.setBackground(new Color(24, 27, 38));

        JButton sendBtn = createBtn("Gửi", new Color(0, 122, 204));
        JButton fileBtn = createBtn("Chọn File", new Color(80, 80, 150));

        sendBtn.addActionListener(e -> doSendMessage());
        fileBtn.addActionListener(e -> doSendFile());

        btnPanel.add(fileBtn);
        btnPanel.add(sendBtn);
        bottom.add(btnPanel, BorderLayout.EAST);

        // ── Progress bar (ẩn hoàn toàn mặc định) ─────────────────────
        progressPanel.setLayout(new BorderLayout(6, 0));
        progressPanel.setBackground(new Color(24, 27, 38));
        progressPanel.setBorder(new EmptyBorder(4, 10, 4, 10));
        progressPanel.setVisible(false);

        progressBar.setStringPainted(true);
        progressBar.setBackground(new Color(40, 44, 58));
        progressBar.setForeground(new Color(0, 160, 230));
        progressBar.setPreferredSize(new Dimension(0, 14));

        progressLabel.setForeground(new Color(150, 155, 175));
        progressLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        progressPanel.add(progressLabel, BorderLayout.WEST);
        progressPanel.add(progressBar,   BorderLayout.CENTER);

        JPanel southWrap = new JPanel(new BorderLayout());
        southWrap.setBackground(new Color(24, 27, 38));
        southWrap.add(progressPanel, BorderLayout.NORTH);
        southWrap.add(bottom,        BorderLayout.CENTER);
        add(southWrap, BorderLayout.SOUTH);
    }

    // =========================================================
    //  Actions
    // =========================================================

    // =========================================================
    //  Lịch sử tin nhắn từ HashMap
    // =========================================================

    /** Nạp lại toàn bộ tin nhắn từ HashMap khi mở tab */
    private void loadHistory() {
        if (mainFrame == null || mainFrame.getClient() == null) return;
        java.util.List<client.ChatMessage> list = mainFrame.getClient().getMessageHistory(conn.getPeerUsername());
        for (client.ChatMessage msg : list) {
            switch (msg.getType()) {
                case TEXT -> appendMessageUI(msg.getSender(), msg.getContent(), msg.getTime());
                case FILE -> {
                    if (msg.isSentByMe()) {
                        appendFileBubbleUI(true, msg.getSender(), msg.getFileId(), msg.getFileName(), msg.getFileSize(), true, null);
                    } else {
                        appendFileBubbleUI(false, msg.getSender(), msg.getFileId(), msg.getFileName(), msg.getFileSize(), false,
                                createDownloadAction(msg.getFileId(), msg.getFileName()));
                    }
                }
                case SYSTEM -> appendSystemUI(msg.getContent());
            }
        }
    }

    private Runnable createDownloadAction(String fileId, String filename) {
        return () -> {
            JFileChooser saveChooser = new JFileChooser();
            saveChooser.setDialogTitle("Lưu file về máy: " + filename);
            saveChooser.setSelectedFile(new File(filename));
            int res = saveChooser.showSaveDialog(this);
            if (res == JFileChooser.APPROVE_OPTION) {
                File target = saveChooser.getSelectedFile();
                client.P2PServer.registerPendingDownload(fileId, target);
                appendSystem("[..] Đang bắt đầu tải: " + filename + "...");
                conn.requestFileDownload(myUsername, fileId);
            }
        };
    }

    // =========================================================
    //  Actions
    // =========================================================

    private void doSendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.setText("");
        appendMessage(myUsername, text);
        conn.sendMessage(myUsername, text);
    }

    private void doSendFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn file để gửi");
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.exists()) return;

        String fileId = java.util.UUID.randomUUID().toString().substring(0, 8);
        sharedFiles.put(fileId, file);

        String time = currentTime();
        // Lưu tin nhắn gửi file vào HashMap
        if (mainFrame != null && mainFrame.getClient() != null) {
            mainFrame.getClient().addMessage(conn.getPeerUsername(),
                    client.ChatMessage.file(myUsername, fileId, file.getName(), file.length(), time, true, true));
        }

        // Hiển thị thẻ file bên phía người gửi (như Messenger)
        appendFileBubbleUI(true, myUsername, fileId, file.getName(), file.length(), true, null);

        conn.sendFileOffer(myUsername, fileId, file.getName(), file.length());
    }

    // =========================================================
    //  Callbacks từ MainFrame (đã invokeLater)
    // =========================================================

    /** Khi bên kia gửi file -> hiển thị thẻ file Messenger trong dòng chat, click để tải */
    public void onFileOfferReceived(String fileId, String filename, long size) {
        String time = currentTime();
        // Lưu vào HashMap
        if (mainFrame != null && mainFrame.getClient() != null) {
            mainFrame.getClient().addMessage(conn.getPeerUsername(),
                    client.ChatMessage.file(conn.getPeerUsername(), fileId, filename, size, time, false, false));
        }

        appendFileBubbleUI(false, conn.getPeerUsername(), fileId, filename, size, false, createDownloadAction(fileId, filename));
    }

    /** Khi bên kia bấm tải xuống -> bắt đầu truyền các chunk dữ liệu */
    public void onFileRequestReceived(String fileId) {
        File file = sharedFiles.get(fileId);
        if (file == null || !file.exists()) {
            appendSystem("[!] Lỗi: Không tìm thấy file nguồn để gửi!");
            return;
        }

        appendSystem("[Gui] Đối phương đang tải: " + file.getName());
        showProgress("Gửi: " + file.getName(), true);

        conn.sendFileChunks(myUsername, fileId, file,
            pct -> SwingUtilities.invokeLater(() -> progressBar.setValue(pct)),
            ok  -> SwingUtilities.invokeLater(() -> {
                hideProgress();
                if (ok) appendSystem("[OK] Đã gửi xong file: " + file.getName());
                else    appendSystem("[X] Gửi file thất bại: " + file.getName());
            })
        );
    }

    /** Khi bên kia từ chối nhận file */
    public void onFileRejectReceived(String fileId) {
        File file = sharedFiles.remove(fileId);
        String fname = (file != null) ? file.getName() : fileId;
        appendSystem("[i] Đối phương đã bỏ qua file: " + fname);
    }

    public void appendMessage(String sender, String content) {
        String time = currentTime();
        boolean isMe = sender.equals(myUsername);

        // Lưu vào HashMap trong Client
        if (mainFrame != null && mainFrame.getClient() != null) {
            mainFrame.getClient().addMessage(conn.getPeerUsername(), client.ChatMessage.text(sender, content, time, isMe));
        }

        appendMessageUI(sender, content, time);
    }

    private void appendMessageUI(String sender, String content, String time) {
        boolean isMe = sender.equals(myUsername);

        JPanel row = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);

        JPanel bubble = new JPanel(new BorderLayout(0, 4));
        bubble.setBackground(isMe ? new Color(0, 132, 255) : new Color(55, 62, 78));
        bubble.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isMe ? new Color(0, 120, 240) : new Color(70, 78, 96), 1, true),
                new EmptyBorder(7, 12, 7, 12)));

        JLabel nameAndMsg = new JLabel("<html><b style='color:" + (isMe ? "#E0F0FF" : "#90CAF9") + "'>"
                + (isMe ? "Bạn" : sender) + "</b><br><span style='color:white; font-size:13px;'>"
                + escapeHtml(content) + "</span></html>");
        nameAndMsg.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JLabel timeLbl = new JLabel(time, SwingConstants.RIGHT);
        timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLbl.setForeground(isMe ? new Color(200, 225, 255) : new Color(160, 168, 185));

        bubble.add(nameAndMsg, BorderLayout.CENTER);
        bubble.add(timeLbl, BorderLayout.SOUTH);

        row.add(bubble);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        messagesContainer.add(row);
        messagesContainer.add(Box.createVerticalStrut(6));
        refreshMessages();
    }

    /** Hiển thị thẻ file như Messenger (click để tải) */
    private void appendFileBubbleUI(boolean isMe, String sender, String fileId, String filename, long size, boolean sentByMe, Runnable onDownload) {
        JPanel row = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);

        JPanel fileCard = new JPanel(new BorderLayout(10, 0));
        fileCard.setBackground(isMe ? new Color(30, 90, 160) : new Color(48, 56, 74));
        fileCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isMe ? new Color(50, 120, 210) : new Color(80, 95, 125), 1, true),
                new EmptyBorder(8, 12, 8, 12)));
        fileCard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel iconLabel = new JLabel("[FILE]");
        iconLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        iconLabel.setForeground(new Color(130, 190, 255));

        JPanel infoBox = new JPanel(new GridLayout(2, 1, 0, 2));
        infoBox.setOpaque(false);

        JLabel nameLbl = new JLabel(filename);
        nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        nameLbl.setForeground(Color.WHITE);

        JLabel subLbl = new JLabel(formatSize(size) + (sentByMe ? " - Đã gửi" : " - Nhấp để tải về"));
        subLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subLbl.setForeground(sentByMe ? new Color(190, 220, 255) : new Color(120, 215, 140));

        infoBox.add(nameLbl);
        infoBox.add(subLbl);

        fileCard.add(iconLabel, BorderLayout.WEST);
        fileCard.add(infoBox, BorderLayout.CENTER);

        if (!sentByMe && onDownload != null) {
            JButton dlBtn = new JButton("Tải về");
            dlBtn.setBackground(new Color(46, 139, 87));
            dlBtn.setForeground(Color.WHITE);
            dlBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            dlBtn.setFocusPainted(false);
            dlBtn.setBorderPainted(false);
            dlBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            dlBtn.addActionListener(e -> onDownload.run());
            fileCard.add(dlBtn, BorderLayout.EAST);

            // Click vào bất kỳ chỗ nào trên card cũng tải
            fileCard.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    onDownload.run();
                }
            });
        }

        row.add(fileCard);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        messagesContainer.add(row);
        messagesContainer.add(Box.createVerticalStrut(6));
        refreshMessages();
    }

    public void appendSystem(String msg) {
        String time = currentTime();
        // Lưu thông báo hệ thống vào HashMap
        if (mainFrame != null && mainFrame.getClient() != null) {
            mainFrame.getClient().addMessage(conn.getPeerUsername(), client.ChatMessage.system(msg, time));
        }
        appendSystemUI(msg);
    }

    private void appendSystemUI(String msg) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);

        JLabel lbl = new JLabel("-- " + msg + " --");
        lbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lbl.setForeground(new Color(140, 148, 170));

        row.add(lbl);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        messagesContainer.add(row);
        messagesContainer.add(Box.createVerticalStrut(6));
        refreshMessages();
    }

    /** Gọi khi bắt đầu nhận file. */
    public void onFileReceiving(String filename, long size) {
        appendSystem("[Nhan] Đang kéo file về: " + filename + " (" + formatSize(size) + ")");
        showProgress("Tải: " + filename, true);
    }

    /** Cập nhật progress khi đang nhận. */
    public void onFileProgress(String filename, int pct) {
        progressBar.setValue(pct);
    }

    /** Gọi khi nhận file xong hoặc người dùng huỷ. */
    public void onFileDone(String filename, String savedPath) {
        hideProgress();
        if (savedPath != null) {
            appendSystem("[OK] Đã lưu xong file: " + filename);
            appendSystem("[Luu] Vị trí: " + savedPath);
        } else {
            appendSystem("[X] Đã huỷ tải file: " + filename);
        }
    }

    // =========================================================
    //  Helpers
    // =========================================================

    private void showProgress(String label, boolean show) {
        progressLabel.setText("  " + label + "  ");
        progressBar.setValue(0);
        progressPanel.setVisible(show);
        revalidate();
        repaint();
    }

    private void hideProgress() {
        progressPanel.setVisible(false);
        revalidate();
        repaint();
    }

    private void refreshMessages() {
        messagesContainer.revalidate();
        messagesWrapper.revalidate();
        messagesContainer.repaint();
        messagesWrapper.repaint();
        scrollToBottom();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\n", "<br>");
    }

    private String currentTime() {
        java.time.LocalTime t = java.time.LocalTime.now();
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private JButton createBtn(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setPreferredSize(new Dimension(90, 32));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
