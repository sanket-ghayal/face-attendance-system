package com.attendance.ui;

import com.attendance.api.ApiClient;
import com.attendance.utils.CsvExporter;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.time.LocalDate;
import static com.attendance.ui.Theme.*;

public class RecordsPanel extends JPanel {

    // Daily table: Student ID, Name, Course, Status, Time, Action
    private final String[] DAILY_COLS = {"STUDENT ID","NAME","COURSE","STATUS","TIME","ACTION"};
    private final DefaultTableModel dailyModel = new DefaultTableModel(DAILY_COLS, 0) {
        public boolean isCellEditable(int r, int c) { return c == 5; }
    };
    private final JTable dailyTable = new JTable(dailyModel);

    private final String[] HIST_COLS = {"DATE","PRESENT","TOTAL","ABSENT","STUDENTS PRESENT"};
    private final DefaultTableModel histModel = new DefaultTableModel(HIST_COLS, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable histTable = new JTable(histModel);

    private final JTextField txtDate   = inputField();
    private final JButton    btnLoad   = button("Load",        BLUE);
    private final JButton    btnToday  = button("Today",       new Color(70,70,110));
    private final JButton    btnExport = button("\u2B07  Export CSV", GREEN);
    private final JLabel     lblSum    = new JLabel("  ");

    public RecordsPanel() {
        setBackground(BG_APP);
        setLayout(new BorderLayout(14, 14));
        add(sectionTitle("View & Export"), BorderLayout.NORTH);

        // Force tab colours via UIManager BEFORE creating the JTabbedPane.
        // Some look-and-feels (esp. GTK/Metal) ignore setBackground() on the
        // SELECTED tab and paint it light grey/white instead, which makes
        // light-coloured tab text invisible. Setting these UIManager keys
        // first is the most reliable cross-platform fix.
        UIManager.put("TabbedPane.selected",          BLUE.darker());
        UIManager.put("TabbedPane.background",        BG_PANEL);
        UIManager.put("TabbedPane.foreground",         Color.WHITE);
        UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
        UIManager.put("TabbedPane.contentAreaColor",   BG_PANEL);
        UIManager.put("TabbedPane.unselectedBackground", BG_PANEL);
        UIManager.put("TabbedPane.highlight",          BLUE.darker());
        UIManager.put("TabbedPane.borderHightlightColor", BORDER);
        UIManager.put("TabbedPane.darkShadow",         BORDER);
        UIManager.put("TabbedPane.light",              BORDER);
        UIManager.put("TabbedPane.focus",              BLUE.darker());

        JTabbedPane tabs = new JTabbedPane();
        tabs.setOpaque(true);
        tabs.setBackground(BG_PANEL);
        tabs.setForeground(Color.WHITE);
        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));

        tabs.addTab("  Present Today  ",        buildDailyTab());
        tabs.addTab("  Day-by-Day History  ",   buildHistoryTab());

        // Belt-and-suspenders: force explicit per-tab colours too, in case
        // the active look-and-feel still ignores some UIManager keys above.
        for (int i = 0; i < tabs.getTabCount(); i++) {
            tabs.setForegroundAt(i, Color.WHITE);
            tabs.setBackgroundAt(i, BG_PANEL);
        }

        add(tabs, BorderLayout.CENTER);

        btnLoad.addActionListener(e   -> loadDaily());
        btnToday.addActionListener(e  -> { txtDate.setText(LocalDate.now().toString()); loadDaily(); });
        btnExport.addActionListener(e -> export());

        SwingUtilities.invokeLater(() -> { loadDaily(); loadHistory(); });
    }

    // ───────────────────────── Daily tab ─────────────────────────
    private JPanel buildDailyTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_APP);
        panel.setBorder(new EmptyBorder(10, 0, 0, 0));

        // ── Filter card ────────────────────────────────────────────
        JPanel filterCard = card();
        filterCard.setLayout(new BorderLayout(8, 8));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        filterRow.setOpaque(false);

        txtDate.setText(LocalDate.now().toString());
        txtDate.setPreferredSize(new Dimension(130, 32));
        btnLoad.setPreferredSize(new Dimension(90, 32));
        btnToday.setPreferredSize(new Dimension(90, 32));
        btnExport.setPreferredSize(new Dimension(150, 32));

        JLabel dl = new JLabel("DATE");
        dl.setForeground(TEXT_DIM);
        dl.setFont(new Font("SansSerif", Font.BOLD, 11));

        filterRow.add(dl);
        filterRow.add(txtDate);
        filterRow.add(btnLoad);
        filterRow.add(btnToday);
        filterRow.add(Box.createHorizontalStrut(20));
        filterRow.add(btnExport);

        lblSum.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblSum.setForeground(GREEN);

        JLabel hint = new JLabel("Showing students marked PRESENT for the selected date. Use Delete to remove a record (admin).");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        hint.setForeground(TEXT_DIM);

        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.setOpaque(false);
        bottomRow.add(lblSum, BorderLayout.WEST);
        bottomRow.add(hint,   BorderLayout.EAST);

        filterCard.add(filterRow,  BorderLayout.NORTH);
        filterCard.add(bottomRow,  BorderLayout.SOUTH);

        // ── Table ────────────────────────────────────────────────
        styleTable(dailyTable);
        TableColumnModel cm = dailyTable.getColumnModel();
        int[] dw  = {110, 200, 150, 110, 100, 100};
        int[] align = {
            SwingConstants.CENTER,  // Student ID
            SwingConstants.LEFT,    // Name
            SwingConstants.LEFT,    // Course
            SwingConstants.CENTER,  // Status
            SwingConstants.CENTER,  // Time
            SwingConstants.CENTER   // Action
        };
        for (int i = 0; i < dw.length; i++) cm.getColumn(i).setPreferredWidth(dw[i]);

        DailyCellRenderer renderer = new DailyCellRenderer(align);
        for (int i = 0; i < 5; i++) cm.getColumn(i).setCellRenderer(renderer);

        TableColumn actionCol = cm.getColumn(5);
        actionCol.setCellRenderer(new DeleteButtonRenderer());
        actionCol.setCellEditor(new DeleteButtonEditor());

        JScrollPane scroll = scrollPane(dailyTable);
        scroll.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(0,0,0,0)));

        panel.add(filterCard, BorderLayout.NORTH);
        panel.add(scroll,     BorderLayout.CENTER);
        return panel;
    }

    // ───────────────────────── History tab ────────────────────────
    private JPanel buildHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_APP);
        panel.setBorder(new EmptyBorder(10, 0, 0, 0));

        JPanel topCard = card();
        topCard.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 4));
        JButton btnRefresh = button("\u21BB  Refresh (Last 30 Days)", BLUE);
        btnRefresh.setPreferredSize(new Dimension(230, 34));
        topCard.add(btnRefresh);
        btnRefresh.addActionListener(e -> loadHistory());

        styleTable(histTable);
        TableColumnModel cm = histTable.getColumnModel();
        int[] hw = {110, 90, 90, 90, 420};
        int[] align = {
            SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER,
            SwingConstants.CENTER, SwingConstants.LEFT
        };
        for (int i = 0; i < hw.length; i++) cm.getColumn(i).setPreferredWidth(hw[i]);
        HistoryCellRenderer renderer = new HistoryCellRenderer(align);
        for (int i = 0; i < hw.length; i++) cm.getColumn(i).setCellRenderer(renderer);

        panel.add(topCard, BorderLayout.NORTH);
        panel.add(scrollPane(histTable), BorderLayout.CENTER);
        return panel;
    }

    // ───────────────────────── Data loading ───────────────────────
    private void loadDaily() {
        String date = txtDate.getText().trim();
        if (date.isEmpty()) date = LocalDate.now().toString();
        final String d = date;
        dailyModel.setRowCount(0);
        lblSum.setText("Loading " + d + " ...");
        lblSum.setForeground(TEXT_DIM);

        new SwingWorker<JSONArray,Void>() {
            protected JSONArray doInBackground() throws Exception {
                return ApiClient.getPresentOnly(d);
            }
            protected void done() {
                try {
                    JSONArray arr = get();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject row = arr.getJSONObject(i);
                        dailyModel.addRow(new Object[]{
                            row.optString("student_id"),
                            row.optString("name"),
                            row.optString("course"),
                            row.optString("status","PRESENT"),
                            row.optString("marked_at","--"),
                            "Delete"
                        });
                    }
                    if (arr.length() == 0) {
                        lblSum.setText(d + "  —  No students marked present yet.");
                        lblSum.setForeground(TEXT_DIM);
                    } else {
                        lblSum.setText(d + "  —  " + arr.length() + " student(s) present");
                        lblSum.setForeground(GREEN);
                    }
                } catch (Exception ex) {
                    lblSum.setText("Error: " + ex.getMessage());
                    lblSum.setForeground(RED);
                }
            }
        }.execute();
    }

    private void loadHistory() {
        histModel.setRowCount(0);
        new SwingWorker<JSONArray,Void>() {
            protected JSONArray doInBackground() throws Exception {
                return ApiClient.getAttendanceHistory(30);
            }
            protected void done() {
                try {
                    JSONArray arr = get();
                    if (arr.length() == 0) {
                        histModel.addRow(new Object[]{"No records","","","","Take attendance first"});
                        return;
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject r = arr.getJSONObject(i);
                        int pr  = r.optInt("present_count", 0);
                        int tot = r.optInt("total_students", 0);
                        histModel.addRow(new Object[]{
                            r.optString("date"),
                            String.valueOf(pr),
                            String.valueOf(tot),
                            String.valueOf(tot - pr),
                            r.optString("present_names","")
                        });
                    }
                } catch (Exception ex) {
                    histModel.addRow(new Object[]{"Error","","","",ex.getMessage()});
                }
            }
        }.execute();
    }

    // ───────────────────────── Delete / Export ────────────────────
    private void deleteRecord(int modelRow) {
        String sid  = (String) dailyModel.getValueAt(modelRow, 0);
        String name = (String) dailyModel.getValueAt(modelRow, 1);
        String date = txtDate.getText().trim();
        if (date.isEmpty()) date = LocalDate.now().toString();
        final String d = date;

        int choice = JOptionPane.showConfirmDialog(this,
            "Delete attendance record for:\n\n" + name + " (" + sid + ")\nDate: " + d +
            "\n\nThis cannot be undone.",
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        new SwingWorker<JSONObject,Void>() {
            protected JSONObject doInBackground() throws Exception {
                return ApiClient.deleteAttendance(sid, d);
            }
            protected void done() {
                try {
                    JSONObject r = get();
                    if (r.optBoolean("success", false)) {
                        JOptionPane.showMessageDialog(RecordsPanel.this,
                            "Deleted attendance record for " + name + " on " + d,
                            "Deleted", JOptionPane.INFORMATION_MESSAGE);
                        loadDaily();
                        loadHistory();
                    } else {
                        JOptionPane.showMessageDialog(RecordsPanel.this,
                            r.optString("message", "Delete failed"),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(RecordsPanel.this,
                        "Delete failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void export() {
        if (dailyModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No data to export. Load attendance first.",
                "Empty", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("attendance_present_" + txtDate.getText().trim() + ".csv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                CsvExporter.export(dailyModel, fc.getSelectedFile());
                JOptionPane.showMessageDialog(this,
                    "Saved:\n" + fc.getSelectedFile().getAbsolutePath(),
                    "Exported", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ───────────────────────── Table styling ──────────────────────
    private void styleTable(JTable t) {
        t.setBackground(BG_PANEL);
        t.setForeground(TEXT);
        t.setFont(new Font("SansSerif", Font.PLAIN, 13));
        t.setRowHeight(34);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setGridColor(BORDER);
        t.setShowGrid(false);
        t.setShowHorizontalLines(true);
        t.setAutoCreateRowSorter(false);
        t.setFillsViewportHeight(true);

        JTableHeader header = t.getTableHeader();
        header.setBackground(TABLE_HEADER_BG);
        header.setForeground(TEXT_DIM);
        header.setFont(new Font("SansSerif", Font.BOLD, 11));
        header.setPreferredSize(new Dimension(0, 36));
        header.setReorderingAllowed(false);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, BORDER));

        t.setSelectionBackground(new Color(59,130,246,90));
        t.setSelectionForeground(Color.WHITE);
    }

    // ───────────────────────── Renderers ───────────────────────────

    /** Daily table renderer: zebra rows, right column alignment, solid
     *  status badges with proper contrast (no near-invisible alpha tints). */
    class DailyCellRenderer extends DefaultTableCellRenderer {
        private final int[] align;
        DailyCellRenderer(int[] align) { this.align = align; }

        public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int row, int col) {

            // Status column rendered as a coloured badge instead of plain text
            if (col == 3) {
                String status = v == null ? "" : v.toString();
                boolean present = "PRESENT".equalsIgnoreCase(status);
                JLabel badge = badge(status,
                    present ? new Color(20,90,55) : new Color(90,30,30),
                    present ? GREEN_ROW_TEXT      : RED_ROW_TEXT);
                badge.setHorizontalAlignment(SwingConstants.CENTER);
                JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                wrap.add(badge);
                wrap.setOpaque(true);
                wrap.setBackground(rowColor(row, sel));
                return wrap;
            }

            Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            setHorizontalAlignment(align[col]);
            c.setFont(col == 1
                ? new Font("SansSerif", Font.BOLD, 13)
                : new Font("SansSerif", Font.PLAIN, 13));

            if (!sel) {
                c.setBackground(rowColor(row, false));
                c.setForeground(col == 0 ? TEXT_DIM : TEXT);
            } else {
                c.setBackground(new Color(59,130,246,90));
                c.setForeground(Color.WHITE);
            }
            setBorder(new EmptyBorder(0, 12, 0, 12));
            return c;
        }
    }

    /** History table renderer: zebra rows, centred numeric columns. */
    class HistoryCellRenderer extends DefaultTableCellRenderer {
        private final int[] align;
        HistoryCellRenderer(int[] align) { this.align = align; }

        public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            setHorizontalAlignment(align[col]);
            c.setFont(col == 0
                ? new Font("SansSerif", Font.BOLD, 13)
                : new Font("SansSerif", Font.PLAIN, 13));

            if (!sel) {
                c.setBackground(rowColor(row, false));
                c.setForeground(col == 1 ? GREEN_ROW_TEXT
                              : col == 3 ? RED_ROW_TEXT
                              : TEXT);
            } else {
                c.setBackground(new Color(59,130,246,90));
                c.setForeground(Color.WHITE);
            }
            setBorder(new EmptyBorder(0, 12, 0, 12));
            return c;
        }
    }

    /** Shared zebra-striping helper used by both renderers. */
    private static Color rowColor(int row, boolean selected) {
        if (selected) return new Color(59,130,246,90);
        return (row % 2 == 0) ? BG_PANEL : TABLE_ROW_ALT;
    }

    class DeleteButtonRenderer extends JPanel implements TableCellRenderer {
        private final JButton btn = new JButton("Delete");
        DeleteButtonRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
            btn.setFont(new Font("SansSerif", Font.BOLD, 11));
            btn.setBackground(new Color(90,30,30));
            btn.setForeground(RED_ROW_TEXT);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setOpaque(true);
            btn.setPreferredSize(new Dimension(72, 24));
            add(btn);
        }
        public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            setBackground(rowColor(row, sel));
            return this;
        }
    }

    class DeleteButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel  wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        private final JButton btn  = new JButton("Delete");
        private int editingRow;

        DeleteButtonEditor() {
            btn.setFont(new Font("SansSerif", Font.BOLD, 11));
            btn.setBackground(new Color(90,30,30));
            btn.setForeground(RED_ROW_TEXT);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setOpaque(true);
            btn.setPreferredSize(new Dimension(72, 24));
            btn.addActionListener(e -> {
                fireEditingStopped();
                deleteRecord(editingRow);
            });
            wrap.add(btn);
        }
        public Component getTableCellEditorComponent(
                JTable t, Object v, boolean sel, int row, int col) {
            editingRow = row;
            wrap.setBackground(rowColor(row, sel));
            return wrap;
        }
        public Object getCellEditorValue() { return "Delete"; }
    }
}
