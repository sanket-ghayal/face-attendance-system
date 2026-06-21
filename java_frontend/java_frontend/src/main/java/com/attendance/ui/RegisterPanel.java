package com.attendance.ui;

import com.attendance.api.ApiClient;
import org.json.JSONObject;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import static com.attendance.ui.Theme.*;

public class RegisterPanel extends JPanel {

    private final JTextField txtId     = inputField();
    private final JTextField txtName   = inputField();
    private final JTextField txtCourse = inputField();
    private final JButton    btnReg    = button("Register & Capture Face", GREEN);
    private final JTextArea  log       = logArea();
    private final JProgressBar bar     = new JProgressBar();
    private final JLabel     lblStatus = new JLabel("Ready");

    public RegisterPanel() {
        setBackground(BG_APP);
        setLayout(new BorderLayout(14, 14));

        // Title
        add(sectionTitle("Register Student"), BorderLayout.NORTH);

        // Form card
        JPanel form = darkPanel();
        form.setLayout(new GridBagLayout());
        form.setBorder(new CompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(22, 26, 22, 26)));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(8, 0, 8, 14);

        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets  = new Insets(8, 0, 8, 0);

        addRow(form, lc, fc, 0, "Student ID  *", txtId);
        addRow(form, lc, fc, 1, "Full Name   *", txtName);
        addRow(form, lc, fc, 2, "Course       ", txtCourse);

        GridBagConstraints bc = new GridBagConstraints();
        bc.gridy=3; bc.gridwidth=2;
        bc.insets=new Insets(18,0,0,0);
        bc.anchor=GridBagConstraints.CENTER;
        btnReg.setPreferredSize(new Dimension(270, 44));
        form.add(btnReg, bc);

        GridBagConstraints sc = new GridBagConstraints();
        sc.gridy=4; sc.gridwidth=2;
        sc.insets=new Insets(10,0,0,0);
        sc.anchor=GridBagConstraints.CENTER;
        lblStatus.setFont(new Font("SansSerif",Font.PLAIN,12));
        lblStatus.setForeground(TEXT_DIM);
        form.add(lblStatus, sc);

        GridBagConstraints pc = new GridBagConstraints();
        pc.gridy=5; pc.gridwidth=2;
        pc.fill=GridBagConstraints.HORIZONTAL;
        pc.insets=new Insets(8,0,0,0);
        bar.setBackground(BG_APP);
        bar.setForeground(BLUE);
        bar.setStringPainted(true); bar.setString("");
        bar.setBorderPainted(false);
        form.add(bar, pc);

        // Log
        log.setText("System ready.\nFill in the form and click the button.\n");
        JScrollPane scroll = scrollPane(log);
        scroll.setPreferredSize(new Dimension(0, 160));
        JLabel logLbl = new JLabel("  Log");
        logLbl.setFont(new Font("SansSerif",Font.BOLD,11));
        logLbl.setForeground(TEXT_DIM);
        logLbl.setBorder(new EmptyBorder(0,0,4,0));

        JPanel bottom = new JPanel(new BorderLayout(4,4));
        bottom.setBackground(BG_APP);
        bottom.add(logLbl, BorderLayout.NORTH);
        bottom.add(scroll, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(12,12));
        center.setBackground(BG_APP);
        center.add(form,   BorderLayout.NORTH);
        center.add(bottom, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        btnReg.addActionListener(e -> doRegister());
    }

    private void doRegister() {
        String sid    = txtId.getText().trim();
        String name   = txtName.getText().trim();
        String course = txtCourse.getText().trim();
        if (sid.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Student ID and Name are required!", "Validation",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        btnReg.setEnabled(false);
        bar.setIndeterminate(true); bar.setString("Saving...");
        lblStatus.setText("Registering..."); lblStatus.setForeground(BLUE);
        log("[" + ts() + "] Registering: " + name + " (" + sid + ")");

        new SwingWorker<String,Void>() {
            protected String doInBackground() throws Exception {
                JSONObject r = ApiClient.registerStudent(sid, name, course);
                if (!r.optBoolean("success",false))
                    return r.optBoolean("duplicate",false)
                        ? "DUP:" + r.optString("message")
                        : "ERR:" + r.optString("message");
                SwingUtilities.invokeLater(() -> {
                    bar.setString("Camera opening...");
                    lblStatus.setText("Camera opening — look at camera and follow guide");
                    log("[" + ts() + "] Saved. Camera opening...");
                });
                JSONObject c = ApiClient.captureStudent(sid);
                return c.optBoolean("success",false)
                    ? "OK:" + c.optString("message")
                    : "ERR:" + c.optString("message");
            }
            protected void done() {
                bar.setIndeterminate(false); btnReg.setEnabled(true);
                try {
                    String res = get();
                    if (res.startsWith("DUP:")) {
                        bar.setString(""); lblStatus.setForeground(ORANGE);
                        lblStatus.setText("Duplicate ID");
                        log("[" + ts() + "] ⚠ " + res.substring(4));
                        JOptionPane.showMessageDialog(RegisterPanel.this,
                            "⚠ " + res.substring(4), "Duplicate ID",
                            JOptionPane.WARNING_MESSAGE);
                    } else if (res.startsWith("OK:")) {
                        bar.setString("Done!"); lblStatus.setForeground(GREEN);
                        lblStatus.setText("Registration complete!");
                        log("[" + ts() + "] ✔ " + res.substring(3));
                        log("[" + ts() + "] → Go to Take Attendance → Train Model");
                        JOptionPane.showMessageDialog(RegisterPanel.this,
                            "✅  " + res.substring(3) +
                            "\n\nNow: Take Attendance → Train Model → Start Recognition",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                        txtId.setText(""); txtName.setText(""); txtCourse.setText("");
                    } else {
                        bar.setString("Error"); lblStatus.setForeground(RED);
                        log("[" + ts() + "] ✘ " + res.substring(4));
                        JOptionPane.showMessageDialog(RegisterPanel.this,
                            res.substring(4), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    bar.setString("Error"); lblStatus.setForeground(RED);
                    log("[" + ts() + "] ✘ " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void addRow(JPanel p, GridBagConstraints lc, GridBagConstraints fc,
                        int row, String lbl, JTextField f) {
        lc.gridy=row; fc.gridy=row;
        JLabel l = new JLabel(lbl);
        l.setFont(new Font("SansSerif",Font.BOLD,13));
        l.setForeground(TEXT_DIM);
        l.setPreferredSize(new Dimension(130,28));
        p.add(l,lc); p.add(f,fc);
    }

    private void log(String m) {
        log.append(m+"\n");
        log.setCaretPosition(log.getDocument().getLength());
    }
}
