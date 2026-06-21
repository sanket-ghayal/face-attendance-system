package com.attendance.ui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

public class Theme {
    public static final Color BG_APP      = new Color(14, 14, 22);
    public static final Color BG_PANEL    = new Color(24, 24, 38);
    public static final Color BG_SIDEBAR  = new Color(18, 18, 30);
    public static final Color BG_INPUT    = new Color(30, 30, 50);
    public static final Color BG_LOG      = new Color(10, 10, 18);

    public static final Color BLUE        = new Color(59, 130, 246);
    public static final Color GREEN       = new Color(34, 197, 94);
    public static final Color RED         = new Color(239, 68,  68);
    public static final Color ORANGE      = new Color(251, 146, 60);

    // Solid (non-transparent) row tints for table use — readable on a
    // dark theme, unlike low-alpha tints which wash out to near-invisible.
    public static final Color GREEN_ROW_BG   = new Color(22, 58, 38);
    public static final Color GREEN_ROW_TEXT = new Color(110, 231, 160);
    public static final Color RED_ROW_BG     = new Color(58, 24, 24);
    public static final Color RED_ROW_TEXT   = new Color(252, 130, 130);

    public static final Color TABLE_ROW_ALT  = new Color(20, 20, 32);
    public static final Color TABLE_HEADER_BG = new Color(36, 36, 58);

    public static final Color TEXT        = new Color(220, 220, 255);
    public static final Color TEXT_DIM    = new Color(110, 110, 155);
    public static final Color BORDER      = new Color(45,  45,  70);

    public static JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 18));
        l.setForeground(TEXT);
        l.setBorder(new CompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, BLUE),
            new EmptyBorder(2, 12, 6, 0)
        ));
        return l;
    }

    public static JTextField inputField() {
        JTextField f = new JTextField();
        f.setBackground(BG_INPUT);
        f.setForeground(TEXT);
        f.setCaretColor(Color.WHITE);
        f.setFont(new Font("SansSerif", Font.PLAIN, 13));
        f.setBorder(new CompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(7, 10, 7, 10)
        ));
        return f;
    }

    public static JButton button(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static JTextArea logArea() {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setFont(new Font("Monospaced", Font.PLAIN, 12));
        a.setBackground(BG_LOG);
        a.setForeground(GREEN);
        a.setMargin(new Insets(8, 10, 8, 10));
        return a;
    }

    public static JScrollPane scrollPane(JComponent c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createLineBorder(BORDER));
        s.getViewport().setBackground(BG_LOG);
        return s;
    }

    public static JPanel darkPanel() {
        JPanel p = new JPanel();
        p.setBackground(BG_PANEL);
        p.setBorder(BorderFactory.createLineBorder(BORDER));
        return p;
    }

    /** A rounded "card" container with padding — used to group related controls. */
    public static JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(BG_PANEL);
        p.setBorder(new CompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            new EmptyBorder(14, 16, 14, 16)
        ));
        return p;
    }

    /** A small rounded status badge (e.g. "PRESENT" / "ABSENT") with solid colour. */
    public static JLabel badge(String text, Color bg, Color fg) {
        JLabel l = new JLabel(text, SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setOpaque(false);
        l.setBackground(bg);
        l.setForeground(fg);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setBorder(new EmptyBorder(3, 10, 3, 10));
        return l;
    }

    public static String ts() {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
    }
}
