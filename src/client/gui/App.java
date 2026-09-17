package client.gui;

import client.Client;

import java.io.*;
import java.net.*;

/**
 * Entry point cho ứng dụng client.
 * Chỉ cần hiện LoginFrame, mọi thứ còn lại tự xử lý.
 */
public class App {
    public static void main(String[] args) {
        // Dùng system look and feel nếu có
        try {
            javax.swing.UIManager.setLookAndFeel(
                javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        javax.swing.SwingUtilities.invokeLater(LoginFrame::new);
    }
}
