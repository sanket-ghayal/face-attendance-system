package com.attendance.ui;

import com.attendance.api.ApiClient;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import static com.attendance.ui.Theme.*;

public class MainFrame extends JFrame {

    private final CardLayout   cards      = new CardLayout();
    private final JPanel       cardPanel  = new JPanel(cards);
    private final JLabel       statusDot  = new JLabel("●");
    private final JLabel       statusTxt  = new JLabel("Connecting...");

    public MainFrame() {
        super("Face Recognition Attendance System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 680);
        setMinimumSize(new Dimension(900, 580));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_APP);
        setLayout(new BorderLayout());

        add(buildTopBar(),  BorderLayout.NORTH);
        add(buildSidebar(), BorderLayout.WEST);

        cardPanel.setBackground(BG_APP);
        cardPanel.add(new RegisterPanel(), "register");
        cardPanel.add(new AttendancePanel(), "attend");
        cardPanel.add(new RecordsPanel(), "records");
        cardPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(cardPanel, BorderLayout.CENTER);

        checkBackend();
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(12, 12, 20));
        bar.setPreferredSize(new Dimension(0, 46));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));

        JLabel title = new JLabel("  🎓  Face Recognition Attendance System");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(TEXT);
        bar.add(title, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        right.setOpaque(false);
        statusDot.setFont(new Font("SansSerif", Font.BOLD, 14));
        statusDot.setForeground(Color.GRAY);
        statusTxt.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusTxt.setForeground(TEXT_DIM);
        right.add(statusDot); right.add(statusTxt);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildSidebar() {
        JPanel sb = new JPanel();
        sb.setBackground(BG_SIDEBAR);
        sb.setPreferredSize(new Dimension(185, 0));
        sb.setLayout(new BoxLayout(sb, BoxLayout.Y_AXIS));
        sb.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));
        sb.add(Box.createVerticalStrut(22));

        String[][] nav = {
            {"👤  Register Student", "register"},
            {"📷  Take Attendance",  "attend"},
            {"📊  View & Export",    "records"},
        };

        ButtonGroup grp = new ButtonGroup();
        boolean first = true;
        for (String[] item : nav) {
            JToggleButton btn = navBtn(item[0], item[1]);
            grp.add(btn);
            sb.add(btn);
            sb.add(Box.createVerticalStrut(2));
            if (first) { btn.setSelected(true); first = false; }
        }
        sb.add(Box.createVerticalGlue());
        JLabel eng = new JLabel("  OpenCV LBPH");
        eng.setFont(new Font("SansSerif", Font.PLAIN, 10));
        eng.setForeground(TEXT_DIM);
        sb.add(eng);
        sb.add(Box.createVerticalStrut(10));
        return sb;
    }

    private JToggleButton navBtn(String label, String card) {
        JToggleButton b = new JToggleButton(label) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                if (isSelected()) {
                    g2.setColor(new Color(59, 130, 246, 35));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),6,6);
                    g2.setColor(BLUE);
                    g2.fillRect(0, 8, 3, getHeight()-16);
                }
                super.paintComponent(g);
                g2.dispose();
            }
        };
        b.setFont(new Font("SansSerif", Font.PLAIN, 13));
        b.setForeground(TEXT_DIM);
        b.setBackground(new Color(0,0,0,0));
        b.setOpaque(false); b.setContentAreaFilled(false);
        b.setBorderPainted(false); b.setFocusPainted(false);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setMaximumSize(new Dimension(185, 42));
        b.setPreferredSize(new Dimension(185, 42));
        b.setBorder(new EmptyBorder(0, 16, 0, 0));
        b.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                b.setForeground(TEXT);
                cards.show(cardPanel, card);
            } else {
                b.setForeground(TEXT_DIM);
            }
        });
        return b;
    }

    private void checkBackend() {
        new SwingWorker<Boolean,Void>() {
            protected Boolean doInBackground() { return ApiClient.isBackendRunning(); }
            protected void done() {
                try {
                    boolean ok = get();
                    statusDot.setForeground(ok ? GREEN : RED);
                    statusTxt.setText(ok ? "Backend connected" : "Backend offline");
                    statusTxt.setForeground(ok ? GREEN : RED);
                    if (!ok) JOptionPane.showMessageDialog(MainFrame.this,
                        "Backend not running!\n\nOpen terminal:\n  bash start_backend.sh",
                        "Backend Offline", JOptionPane.WARNING_MESSAGE);
                } catch (Exception ignored) {}
            }
        }.execute();
    }
}
