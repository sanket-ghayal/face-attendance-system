package com.attendance.ui;

import com.attendance.api.ApiClient;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import static com.attendance.ui.Theme.*;

public class AttendancePanel extends JPanel {

    private final JButton      btnTrain = button("⚙  Train Model",       BLUE);
    private final JButton      btnStart = button("▶  Start Recognition",  GREEN);
    private final JButton      btnStop  = button("■  Stop Session",        new Color(220,60,60));
    private final JTextArea    log      = logArea();
    private final JLabel       lblCount = new JLabel("0");
    private final JLabel       lblInfo  = new JLabel("Ready");
    private final CameraBox    camBox   = new CameraBox();

    private volatile boolean streaming = false;
    private Thread mjpegThread;

    public AttendancePanel() {
        setBackground(BG_APP);
        setLayout(new BorderLayout(14, 14));

        // ── NORTH: title + buttons ────────────────────────────────
        JPanel north = new JPanel(new BorderLayout(8,8));
        north.setBackground(BG_APP);
        north.add(sectionTitle("Attendance Recognition"), BorderLayout.NORTH);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        btnRow.setBackground(BG_APP);
        btnTrain.setPreferredSize(new Dimension(148, 38));
        btnStart.setPreferredSize(new Dimension(168, 38));
        btnStop.setPreferredSize(new Dimension(140, 38));
        btnStop.setEnabled(false);
        btnRow.add(btnTrain); btnRow.add(btnStart); btnRow.add(btnStop);

        // Present counter
        JPanel counter = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        counter.setBackground(BG_APP);
        JLabel pl = new JLabel("Present:");
        pl.setForeground(TEXT_DIM); pl.setFont(new Font("SansSerif",Font.PLAIN,13));
        lblCount.setForeground(GREEN); lblCount.setFont(new Font("SansSerif",Font.BOLD,24));
        counter.add(pl); counter.add(lblCount);
        btnRow.add(Box.createHorizontalStrut(16)); btnRow.add(counter);
        north.add(btnRow, BorderLayout.CENTER);

        lblInfo.setFont(new Font("SansSerif",Font.PLAIN,11));
        lblInfo.setForeground(GREEN);
        lblInfo.setBorder(new EmptyBorder(0,4,4,0));
        north.add(lblInfo, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        // ── CENTER: camera box (left) + log (right) ───────────────
        JPanel center = new JPanel(new GridLayout(1,2,12,0));
        center.setBackground(BG_APP);

        // Camera side
        JPanel camSide = new JPanel(new BorderLayout(0,6));
        camSide.setBackground(BG_APP);
        JLabel camTitle = new JLabel("  Live Camera Feed");
        camTitle.setFont(new Font("SansSerif",Font.BOLD,13));
        camTitle.setForeground(TEXT_DIM);
        camTitle.setBorder(BorderFactory.createMatteBorder(0,3,0,0,GREEN));
        camSide.add(camTitle, BorderLayout.NORTH);
        camSide.add(camBox,   BorderLayout.CENTER);

        // Log side
        JPanel logSide = new JPanel(new BorderLayout(0,6));
        logSide.setBackground(BG_APP);
        JLabel logTitle = new JLabel("  System Log");
        logTitle.setFont(new Font("SansSerif",Font.BOLD,13));
        logTitle.setForeground(TEXT_DIM);
        logTitle.setBorder(BorderFactory.createMatteBorder(0,3,0,0,BLUE));
        log.setText("System ready.\n\n"
            + "How to use:\n"
            + "1. Register Student tab → enter details → capture face\n"
            + "2. Click Train Model (after all students registered)\n"
            + "3. Click Start Recognition\n"
            + "   → Camera opens HERE in this box\n"
            + "   → UNKNOWN = red box (not registered)\n"
            + "   → MATCH found = orange box → BLINK to confirm\n"
            + "   → After blink = green box → PRESENT marked\n"
            + "4. Click Stop Session when done\n");
        JScrollPane logScroll = scrollPane(log);
        logSide.add(logTitle,  BorderLayout.NORTH);
        logSide.add(logScroll, BorderLayout.CENTER);

        center.add(camSide);
        center.add(logSide);
        add(center, BorderLayout.CENTER);

        btnTrain.addActionListener(e -> doTrain());
        btnStart.addActionListener(e -> doStart());
        btnStop.addActionListener(e  -> doStop());
    }

    // ── Train Model ───────────────────────────────────────────────
    private void doTrain() {
        setButtons(false, false, false);
        lblInfo.setText("Training model..."); lblInfo.setForeground(ORANGE);
        log("[" + ts() + "] Training LBPH model...");

        new SwingWorker<JSONObject,Void>() {
            protected JSONObject doInBackground() throws Exception {
                return ApiClient.trainModel();
            }
            protected void done() {
                setButtons(true, true, false);
                try {
                    JSONObject r = get();
                    boolean ok  = r.optBoolean("success",false);
                    String  msg = r.optString("message","");
                    if (ok) {
                        lblInfo.setText("Model ready — click Start Recognition");
                        lblInfo.setForeground(GREEN);
                        log("[" + ts() + "] ✔ " + msg);
                        JOptionPane.showMessageDialog(AttendancePanel.this,
                            "✅ " + msg + "\n\nNow click Start Recognition.",
                            "Training Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        lblInfo.setText("Training failed"); lblInfo.setForeground(RED);
                        log("[" + ts() + "] ✘ " + msg);
                        JOptionPane.showMessageDialog(AttendancePanel.this,
                            msg, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    log("[" + ts() + "] Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ── Start Recognition ─────────────────────────────────────────
    private void doStart() {
        streaming = true;
        lblCount.setText("0");
        setButtons(false, false, true);
        lblInfo.setText("Starting camera..."); lblInfo.setForeground(BLUE);
        log("[" + ts() + "] Starting recognition stream...");
        camBox.showMessage("Starting camera...", "Please wait");

        new SwingWorker<JSONObject,Void>() {
            protected JSONObject doInBackground() throws Exception {
                return ApiClient.startStream();
            }
            protected void done() {
                try {
                    JSONObject r = get();
                    if (r.optBoolean("success",false)) {
                        log("[" + ts() + "] Camera stream started.");
                        log("[" + ts() + "] ● Red    = UNKNOWN face (not registered)");
                        log("[" + ts() + "] ● Orange = Match found → BLINK to confirm!");
                        log("[" + ts() + "] ● Green  = Blink OK → PRESENT marked");
                        log("[" + ts() + "] Click Stop Session when done.");
                        lblInfo.setText("Running — blink to confirm attendance");
                        lblInfo.setForeground(GREEN);
                        startMjpegReader();   // show stream in camera box
                        startStatusPoller();  // update present counter
                    } else {
                        streaming = false;
                        setButtons(true, true, false);
                        log("[" + ts() + "] Failed: " + r.optString("message"));
                    }
                } catch (Exception ex) {
                    streaming = false;
                    setButtons(true, true, false);
                    log("[" + ts() + "] Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ── Stop Session ──────────────────────────────────────────────
    private void doStop() {
        streaming = false;
        setButtons(false, false, false);
        lblInfo.setText("Stopping..."); lblInfo.setForeground(ORANGE);
        log("[" + ts() + "] Stopping session...");

        new SwingWorker<JSONObject,Void>() {
            protected JSONObject doInBackground() throws Exception {
                return ApiClient.stopStream();
            }
            protected void done() {
                setButtons(true, true, false);
                camBox.showMessage("Session ended", "View Records tab for attendance");
                try {
                    JSONObject r   = get();
                    JSONArray  ids = r.optJSONArray("recognized_ids");
                    int present    = ids != null ? ids.length() : 0;
                    int spoof      = r.optInt("spoof_count", 0);
                    int unknown    = r.optInt("unknown_count", 0);

                    JSONObject att = r.optJSONObject("attendance");
                    int fresh=0, already=0;
                    if (att != null) {
                        JSONArray m = att.optJSONArray("marked");
                        JSONArray a = att.optJSONArray("already");
                        fresh   = m!=null ? m.length() : 0;
                        already = a!=null ? a.length() : 0;
                    }

                    lblCount.setText(String.valueOf(present));
                    lblInfo.setText("Session ended — " + present + " present");
                    lblInfo.setForeground(present > 0 ? GREEN : TEXT_DIM);

                    log("[" + ts() + "] ─────────────────────────────");
                    log("[" + ts() + "] Session complete!");
                    log("[" + ts() + "] PRESENT (blink verified): " + present);
                    if (fresh  > 0) log("[" + ts() + "] Newly marked: " + ids);
                    if (already > 0) log("[" + ts() + "] Already marked: " + already);
                    if (spoof  > 0) log("[" + ts() + "] ⚠ Spoof blocked: " + spoof);
                    if (unknown > 0) log("[" + ts() + "] Unknown faces: " + unknown);
                    log("[" + ts() + "] ─────────────────────────────");

                    // Result popup
                    StringBuilder msg = new StringBuilder("Attendance Session Complete!\n\n");
                    msg.append("✅  Students marked PRESENT: ").append(present).append("\n");
                    if (already > 0) msg.append("ℹ   Already marked earlier: ").append(already).append("\n");
                    msg.append("❓  Unknown faces detected:  ").append(unknown).append("\n");
                    if (spoof > 0) msg.append("\n⚠️  SPOOF attempts blocked: ").append(spoof)
                        .append("\n    (No blink detected — photo/screen rejected)");
                    JOptionPane.showMessageDialog(AttendancePanel.this,
                        msg.toString(), "Session Complete",
                        spoof > 0 ? JOptionPane.WARNING_MESSAGE
                                  : JOptionPane.INFORMATION_MESSAGE);

                } catch (Exception ex) {
                    log("[" + ts() + "] Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ── MJPEG reader: pull frames from Python and show in camBox ──
    private void startMjpegReader() {
        mjpegThread = new Thread(() -> {
            try {
                URL url = new URL(ApiClient.STREAM_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                conn.connect();

                InputStream    is  = conn.getInputStream();
                DataInputStream dis = new DataInputStream(is);
                byte[] buf = new byte[8192];
                ByteArrayOutputStream frameBuf = new ByteArrayOutputStream();
                boolean inJpeg = false;

                while (streaming) {
                    int n = is.read(buf);
                    if (n < 0) break;

                    for (int i = 0; i < n; i++) {
                        byte b = buf[i];
                        frameBuf.write(b & 0xFF);
                        byte[] d   = frameBuf.toByteArray();
                        int    len = d.length;

                        // Detect JPEG start FF D8
                        if (!inJpeg && len >= 2
                                && (d[len-2]&0xFF)==0xFF
                                && (d[len-1]&0xFF)==0xD8) {
                            frameBuf.reset();
                            frameBuf.write(0xFF);
                            frameBuf.write(0xD8);
                            inJpeg = true;
                        }
                        // Detect JPEG end FF D9
                        else if (inJpeg && len >= 2
                                && (d[len-2]&0xFF)==0xFF
                                && (d[len-1]&0xFF)==0xD9) {
                            final byte[] jpg = frameBuf.toByteArray();
                            frameBuf.reset();
                            inJpeg = false;
                            try {
                                BufferedImage img = ImageIO.read(
                                    new ByteArrayInputStream(jpg));
                                if (img != null) {
                                    SwingUtilities.invokeLater(
                                        () -> camBox.setImage(img));
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
                is.close(); conn.disconnect();
            } catch (Exception e) {
                if (streaming) log("[" + ts() + "] Stream error: " + e.getMessage());
            }
        }, "mjpeg-reader");
        mjpegThread.setDaemon(true);
        mjpegThread.start();
    }

    // ── Status poller: update present count every 2s ──────────────
    private void startStatusPoller() {
        new Thread(() -> {
            while (streaming) {
                try {
                    Thread.sleep(2000);
                    JSONObject s = ApiClient.getStreamStatus();
                    int cnt = s.optInt("present_count", 0);
                    SwingUtilities.invokeLater(() -> {
                        lblCount.setText(String.valueOf(cnt));
                        lblInfo.setText("Running — " + cnt + " present  |  blink to confirm");
                    });
                } catch (Exception ignored) {}
            }
        }, "status-poller").start();
    }

    private void setButtons(boolean train, boolean start, boolean stop) {
        btnTrain.setEnabled(train);
        btnStart.setEnabled(start);
        btnStop.setEnabled(stop);
    }
    private void log(String m) {
        log.append(m+"\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    // ── Camera feed panel ─────────────────────────────────────────
    static class CameraBox extends JPanel {
        private BufferedImage img     = null;
        private String        line1   = "Camera feed appears here";
        private String        line2   = "";

        CameraBox() {
            setBackground(new Color(10,10,18));
            setBorder(BorderFactory.createLineBorder(BORDER));
            setPreferredSize(new Dimension(460, 360));
        }

        void setImage(BufferedImage i) {
            img = i; line1 = null; repaint();
        }

        void showMessage(String l1, String l2) {
            img = null; line1 = l1; line2 = l2; repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (img != null) {
                int pw = getWidth(), ph = getHeight();
                double sc = Math.min((double)pw/img.getWidth(),
                                     (double)ph/img.getHeight());
                int iw = (int)(img.getWidth()*sc);
                int ih = (int)(img.getHeight()*sc);
                g2.drawImage(img, (pw-iw)/2, (ph-ih)/2, iw, ih, null);
            } else {
                g2.setColor(new Color(80,80,120));
                g2.setFont(new Font("SansSerif",Font.PLAIN,14));
                FontMetrics fm = g2.getFontMetrics();
                if (line1 != null) {
                    g2.drawString(line1,
                        (getWidth()-fm.stringWidth(line1))/2,
                        getHeight()/2 - 10);
                }
                if (line2 != null && !line2.isEmpty()) {
                    g2.setColor(new Color(60,60,100));
                    g2.setFont(new Font("SansSerif",Font.PLAIN,12));
                    fm = g2.getFontMetrics();
                    g2.drawString(line2,
                        (getWidth()-fm.stringWidth(line2))/2,
                        getHeight()/2 + 18);
                }
            }
        }
    }
}
