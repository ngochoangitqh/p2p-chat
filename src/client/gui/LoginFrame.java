package client.gui;

import client.Client;
import client.P2PServer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.net.ServerSocket;

/**
 * Màn hình đăng nhập / đăng ký.
 */
public class LoginFrame extends JFrame {

    private final JTextField hostField    = new JTextField("localhost", 15);
    private final JTextField userField    = new JTextField(15);
    private final JPasswordField passField = new JPasswordField(15);
    private final JLabel statusLabel      = new JLabel(" ");

    public LoginFrame() {
        setTitle("P2P Chat – Đăng nhập");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        buildUI();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void buildUI() {
        // ── Panel chính
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(30, 34, 45));

        // ── Header banner
        JLabel banner = new JLabel("P2P CHAT", SwingConstants.CENTER);
        banner.setFont(new Font("Segoe UI", Font.BOLD, 26));
        banner.setForeground(new Color(100, 210, 255));
        banner.setBorder(new EmptyBorder(24, 0, 16, 0));
        root.add(banner, BorderLayout.NORTH);

        // ── Form panel
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(new Color(30, 34, 45));
        form.setBorder(new EmptyBorder(0, 40, 10, 40));

        form.add(label("Server IP"));
        styleField(hostField);
        form.add(hostField);
        form.add(Box.createVerticalStrut(10));

        form.add(label("Tên đăng nhập"));
        styleField(userField);
        form.add(userField);
        form.add(Box.createVerticalStrut(10));

        form.add(label("Mật khẩu"));
        styleField(passField);
        form.add(passField);
        form.add(Box.createVerticalStrut(20));

        // ── Nút Đăng nhập / Đăng ký
        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        btnPanel.setBackground(new Color(30, 34, 45));

        JButton loginBtn = createButton("Đăng nhập", new Color(0, 122, 204));
        JButton regBtn   = createButton("Đăng ký",   new Color(70, 160, 70));

        loginBtn.addActionListener(e -> doLogin());
        regBtn.addActionListener(e -> doRegister());

        // Nhấn Enter trong passField → đăng nhập
        passField.addActionListener(e -> doLogin());

        btnPanel.add(loginBtn);
        btnPanel.add(regBtn);
        form.add(btnPanel);
        form.add(Box.createVerticalStrut(12));

        // ── Status label
        statusLabel.setForeground(new Color(255, 100, 100));
        statusLabel.setAlignmentX(CENTER_ALIGNMENT);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        form.add(statusLabel);
        form.add(Box.createVerticalStrut(16));

        root.add(form, BorderLayout.CENTER);
        setContentPane(root);
    }

    // ── Helpers UI ──────────────────────────────────────────

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(180, 185, 200));
        l.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        l.setBorder(new EmptyBorder(0, 0, 3, 0));
        return l;
    }

    private void styleField(JTextField field) {
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setBackground(new Color(45, 50, 65));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 80, 110)),
                new EmptyBorder(4, 8, 4, 8)));
    }

    private JButton createButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, 36));
        // Hover effect
        btn.addMouseListener(new MouseAdapter() {
            final Color normal = bg;
            final Color hover  = bg.brighter();
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(hover); }
            @Override public void mouseExited (MouseEvent e) { btn.setBackground(normal); }
        });
        return btn;
    }

    // ── Logic ────────────────────────────────────────────────

    private void doLogin() {
        String host = hostField.getText().trim();
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {
            setStatus("Vui lòng nhập đầy đủ thông tin.", false);
            return;
        }

        setStatus("Đang kết nối...", true);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            Client client;
            int p2pPort;

            @Override
            protected String doInBackground() {
                // Tìm port trống cho P2P
                try {
                    try (ServerSocket ss = new ServerSocket(0)) {
                        p2pPort = ss.getLocalPort();
                    }
                } catch (Exception e) {
                    p2pPort = 6000 + (int)(Math.random() * 1000);
                }

                client = new Client();
                if (!client.connect(host)) return "Không thể kết nối tới server " + host;
                return client.register(user, pass); // null nếu thành công hoặc đã tồn tại
            }

            @Override
            protected void done() {
                try {
                    // Bỏ qua lỗi "đã tồn tại" khi login (có thể đăng ký trước rồi)
                    String regErr = get();

                    // Tạo MainFrame trước rồi truyền vào login
                    MainFrame mainFrame = new MainFrame(client, user);

                    String loginErr = client.login(user, pass, p2pPort, mainFrame);
                    if (loginErr != null) {
                        mainFrame.dispose();
                        setStatus("Lỗi: " + loginErr, false);
                        return;
                    }

                    // Khởi động P2P server
                    P2PServer p2pSrv = new P2PServer(p2pPort, mainFrame);
                    new Thread(p2pSrv, "P2PServer").start();
                    mainFrame.setP2pServer(p2pSrv);
                    mainFrame.refreshUserList();

                    dispose(); // đóng cửa sổ login
                    mainFrame.setVisible(true);

                } catch (Exception ex) {
                    setStatus("Lỗi: " + ex.getMessage(), false);
                }
            }
        };
        worker.execute();
    }

    private void doRegister() {
        String host = hostField.getText().trim();
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {
            setStatus("Vui lòng nhập đầy đủ thông tin.", false);
            return;
        }
        if (pass.length() < 4) {
            setStatus("Mật khẩu tối thiểu 4 ký tự.", false);
            return;
        }

        setStatus("Đang đăng ký...", true);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                Client tmp = new Client();
                if (!tmp.connect(host)) return "Không thể kết nối tới server " + host;
                String err = tmp.register(user, pass);
                try { tmp.logout(); } catch (Exception ignored) {}
                return err;
            }

            @Override
            protected void done() {
                try {
                    String err = get();
                    if (err == null) {
                        setStatus("Đăng ký thành công! Hãy đăng nhập.", true);
                    } else {
                        setStatus("Lỗi: " + err, false);
                    }
                } catch (Exception ex) {
                    setStatus("Lỗi: " + ex.getMessage(), false);
                }
            }
        };
        worker.execute();
    }

    private void setStatus(String msg, boolean ok) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(msg);
            statusLabel.setForeground(ok ? new Color(100, 220, 100) : new Color(255, 100, 100));
        });
    }
}
