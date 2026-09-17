package client.gui;

import client.Client;
import client.P2PConnection;
import client.P2PServer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Map;

/**
 * Cửa sổ chính sau khi đăng nhập:
 *  - Trái: danh sách user online
 *  - Phải: tab chat với từng user
 */
public class MainFrame extends JFrame {

    private final Client client;
    private final String myUsername;
    private P2PServer p2pServer;

    // UI components
    private final DefaultListModel<String> userListModel = new DefaultListModel<>();
    private final JList<String> userList = new JList<>(userListModel);
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final JLabel statusBar = new JLabel("  [v] Online");

    // Giữ reference tới từng ChatPanel
    private final java.util.Map<String, ChatPanel> chatPanels = new java.util.LinkedHashMap<>();

    public MainFrame(Client client, String username) {
        this.client = client;
        this.myUsername = username;

        setTitle("P2P Chat  –  " + username);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(860, 580);
        setMinimumSize(new Dimension(700, 450));
        setLocationRelativeTo(null);

        buildUI();

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                doLogout();
            }
        });
    }

    public void setP2pServer(P2PServer srv) { this.p2pServer = srv; }
    public Client getClient() { return client; }

    // =========================================================
    //  Build UI
    // =========================================================
    private void buildUI() {
        getContentPane().setBackground(new Color(30, 34, 45));
        setLayout(new BorderLayout());

        // ── Left: user list ────────────────────────────────
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(200, 0));
        leftPanel.setBackground(new Color(24, 27, 38));

        JLabel onlineLabel = new JLabel("  Danh sách Online", SwingConstants.LEFT);
        onlineLabel.setForeground(new Color(140, 145, 165));
        onlineLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        onlineLabel.setBorder(new EmptyBorder(10, 8, 8, 0));
        onlineLabel.setBackground(new Color(24, 27, 38));
        onlineLabel.setOpaque(true);
        leftPanel.add(onlineLabel, BorderLayout.NORTH);

        userList.setBackground(new Color(24, 27, 38));
        userList.setForeground(new Color(210, 215, 230));
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userList.setSelectionBackground(new Color(50, 110, 180));
        userList.setSelectionForeground(Color.WHITE);
        userList.setFixedCellHeight(36);
        userList.setBorder(new EmptyBorder(0, 0, 0, 0));
        userList.setCellRenderer(new UserCellRenderer());

        // Double-click để mở chat
        userList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = userList.getSelectedValue();
                    if (selected != null) openChatWith(selected);
                }
            }
        });

        leftPanel.add(new JScrollPane(userList) {{
            setBorder(BorderFactory.createEmptyBorder());
            getViewport().setBackground(new Color(24, 27, 38));
        }}, BorderLayout.CENTER);

        // Nút logout nhỏ ở bottom
        JButton logoutBtn = new JButton("Đăng xuất");
        logoutBtn.setBackground(new Color(40, 44, 58));
        logoutBtn.setForeground(new Color(200, 80, 80));
        logoutBtn.setFocusPainted(false);
        logoutBtn.setBorderPainted(false);
        logoutBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        logoutBtn.addActionListener(e -> doLogout());
        leftPanel.add(logoutBtn, BorderLayout.SOUTH);

        add(leftPanel, BorderLayout.WEST);

        // ── Right: tabbed chat area ────────────────────────
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(new Color(30, 34, 45));

        tabbedPane.setBackground(new Color(30, 34, 45));
        tabbedPane.setForeground(new Color(200, 205, 220));
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tabbedPane.setFocusable(false);
        rightPanel.add(tabbedPane, BorderLayout.CENTER);

        // Status bar
        statusBar.setBackground(new Color(20, 23, 33));
        statusBar.setForeground(new Color(100, 200, 120));
        statusBar.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusBar.setOpaque(true);
        statusBar.setBorder(new EmptyBorder(3, 8, 3, 0));
        rightPanel.add(statusBar, BorderLayout.SOUTH);

        add(rightPanel, BorderLayout.CENTER);
    }

    // =========================================================
    //  Mở chat tab
    // =========================================================
    public void openChatWith(String peerUsername) {
        // Nếu đã có tab thì focus vào
        if (chatPanels.containsKey(peerUsername)) {
            int idx = tabbedPane.indexOfTab("  " + peerUsername + "  ");
            if (idx >= 0) tabbedPane.setSelectedIndex(idx);
            return;
        }

        P2PConnection conn = client.getPeerConnection(peerUsername);
        if (conn == null) {
            JOptionPane.showMessageDialog(this,
                "Không tìm thấy thông tin kết nối của " + peerUsername,
                "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ChatPanel panel = new ChatPanel(myUsername, conn, this);
        chatPanels.put(peerUsername, panel);

        // Tạo tab có nút đóng (X)
        int tabIdx = tabbedPane.getTabCount();
        tabbedPane.addTab("  " + peerUsername + "  ", panel);
        tabbedPane.setTabComponentAt(tabIdx, makeCloseTab(peerUsername, tabIdx));
        tabbedPane.setSelectedIndex(tabIdx);
    }

    /** Tạo tab header có nút X để đóng. */
    private JPanel makeCloseTab(String title, int tabIndex) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        JLabel lbl = new JLabel("  " + title + " ");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JButton close = new JButton("x");
        close.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        close.setPreferredSize(new Dimension(18, 18));
        close.setFocusPainted(false);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setForeground(new Color(160, 165, 180));
        close.addActionListener(e -> closeTab(title));
        p.add(lbl);
        p.add(close);
        return p;
    }

    private void closeTab(String peerUsername) {
        int idx = tabbedPane.indexOfTab("  " + peerUsername + "  ");
        // indexOfTab không tìm được khi dùng custom tab component → tìm bằng panel
        if (idx < 0) {
            ChatPanel cp = chatPanels.get(peerUsername);
            if (cp != null) {
                for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                    if (tabbedPane.getComponentAt(i) == cp) { idx = i; break; }
                }
            }
        }
        if (idx >= 0) tabbedPane.removeTabAt(idx);
        chatPanels.remove(peerUsername);
    }

    // =========================================================
    //  Refresh user list từ client.getPeerMap()
    // =========================================================
    public void refreshUserList() {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (String u : client.getPeerMap().keySet()) {
                userListModel.addElement(u);
            }
        });
    }

    // =========================================================
    //  Callbacks từ client / P2PServer  (gọi từ thread khác)
    // =========================================================

    public void onUserJoined(String username, String ip, int port) {
        SwingUtilities.invokeLater(() -> {
            if (!userListModel.contains(username)) {
                userListModel.addElement(username);
            }
            setStatus("[Online] " + username);
        });
    }

    public void onUserLeft(String username) {
        SwingUtilities.invokeLater(() -> {
            userListModel.removeElement(username);
            setStatus("[Offline] " + username);
        });
    }

    /** Gọi khi nhận được tin nhắn P2P. */
    public void onMessageReceived(String sender, String content) {
        SwingUtilities.invokeLater(() -> {
            // Nếu chưa có tab thì mở tab
            if (!chatPanels.containsKey(sender)) {
                openChatWith(sender);
            }
            ChatPanel cp = chatPanels.get(sender);
            if (cp != null) cp.appendMessage(sender, content);
        });
    }

    /** Gọi khi nhận lời mời gửi file từ peer. */
    public void onFileOfferReceived(String sender, String fileId, String filename, long size) {
        SwingUtilities.invokeLater(() -> {
            if (!chatPanels.containsKey(sender)) openChatWith(sender);
            ChatPanel cp = chatPanels.get(sender);
            if (cp != null) cp.onFileOfferReceived(fileId, filename, size);
        });
    }

    /** Gọi khi peer yêu cầu bắt đầu tải file mà mình đã chia sẻ. */
    public void onFileRequestReceived(String requester, String fileId) {
        SwingUtilities.invokeLater(() -> {
            ChatPanel cp = chatPanels.get(requester);
            if (cp != null) cp.onFileRequestReceived(fileId);
        });
    }

    /** Gọi khi peer từ chối nhận file. */
    public void onFileRejectReceived(String sender, String fileId) {
        SwingUtilities.invokeLater(() -> {
            ChatPanel cp = chatPanels.get(sender);
            if (cp != null) cp.onFileRejectReceived(fileId);
        });
    }

    /** Gọi khi bắt đầu nhận file. */
    public void onFileTransferStarted(String sender, String filename, long size) {
        SwingUtilities.invokeLater(() -> {
            if (!chatPanels.containsKey(sender)) openChatWith(sender);
            ChatPanel cp = chatPanels.get(sender);
            if (cp != null) cp.onFileReceiving(filename, size);
        });
    }

    /** Gọi khi nhận file đang chạy (0–100%). */
    public void onFileTransferProgress(String sender, String filename, int pct) {
        SwingUtilities.invokeLater(() -> {
            ChatPanel cp = chatPanels.get(sender);
            if (cp != null) cp.onFileProgress(filename, pct);
        });
    }

    /** Gọi khi nhận file xong. */
    public void onFileTransferDone(String sender, String filename, String savedPath) {
        SwingUtilities.invokeLater(() -> {
            ChatPanel cp = chatPanels.get(sender);
            if (cp != null) cp.onFileDone(filename, savedPath);
        });
    }

    public void onServerDisconnected() {
        SwingUtilities.invokeLater(() -> {
            setStatus("[!] Mất kết nối server!");
            statusBar.setForeground(new Color(255, 120, 80));
            JOptionPane.showMessageDialog(this,
                "Đã mất kết nối tới server.", "Mất kết nối", JOptionPane.WARNING_MESSAGE);
        });
    }

    // =========================================================
    //  Logout
    // =========================================================
    private void doLogout() {
        int ans = JOptionPane.showConfirmDialog(this,
            "Bạn có muốn đăng xuất không?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (ans != JOptionPane.YES_OPTION) return;
        if (p2pServer != null) p2pServer.stop();
        client.logout();
        dispose();
        new LoginFrame();
    }

    private void setStatus(String msg) {
        statusBar.setText("  " + msg);
    }

    // =========================================================
    //  Custom cell renderer cho user list
    // =========================================================
    private static class UserCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(
                    list, "  [o] " + value, index, isSelected, cellHasFocus);
            lbl.setBackground(isSelected ? new Color(50, 110, 180) : new Color(24, 27, 38));
            lbl.setForeground(new Color(210, 215, 230));
            lbl.setBorder(new EmptyBorder(4, 8, 4, 8));
            return lbl;
        }
    }
}
