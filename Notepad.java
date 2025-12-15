import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.prefs.Preferences;

public class Notepad extends JFrame {

    private final JTextArea textArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("Ready");
    private final UndoManager undoManager = new UndoManager();

    private File currentFile = null;
    private boolean modified = false;

    /* === Preferences === */
    private final Preferences prefs =
            Preferences.userNodeForPackage(Notepad.class);
    private static final String LAST_DIR_KEY = "lastDir";

    public Notepad() {
        super("Agung Notepad");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        add(new JScrollPane(textArea), BorderLayout.CENTER);

        JPanel status = new JPanel(new BorderLayout());
        status.add(statusLabel, BorderLayout.WEST);
        add(status, BorderLayout.SOUTH);

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { setModified(true); updateStatus(); }
            public void removeUpdate(DocumentEvent e) { setModified(true); updateStatus(); }
            public void changedUpdate(DocumentEvent e) { setModified(true); updateStatus(); }
        });
        textArea.addCaretListener(e -> updateStatus());

        textArea.getDocument().addUndoableEditListener(
                e -> undoManager.addEdit(e.getEdit()));

        setJMenuBar(createMenuBar());
        setupKeyBindings();
        setVisible(true);
    }

    /* ================= MENU ================= */

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        JMenu file = new JMenu("File");
        addItem(file, "New", null, e -> newFile());
        addItem(file, "Open...", KeyStroke.getKeyStroke(KeyEvent.VK_O, mask), e -> openFileDialog());
        addItem(file, "Save", KeyStroke.getKeyStroke(KeyEvent.VK_S, mask), e -> saveFile());
        addItem(file, "Save As...", null, e -> saveFileAs());
        file.addSeparator();
        addItem(file, "Exit", null, e -> exitApp());

        JMenu edit = new JMenu("Edit");
        addItem(edit, "Undo", null, e -> doUndo());
        addItem(edit, "Redo", null, e -> doRedo());
        edit.addSeparator();
        addItem(edit, "Cut", KeyStroke.getKeyStroke(KeyEvent.VK_X, mask), e -> textArea.cut());
        addItem(edit, "Copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, mask), e -> textArea.copy());
        addItem(edit, "Paste", KeyStroke.getKeyStroke(KeyEvent.VK_V, mask), e -> textArea.paste());
        edit.addSeparator();
        addItem(edit, "Find / Replace...",
                KeyStroke.getKeyStroke(KeyEvent.VK_F, mask),
                e -> showFindReplaceDialog());
        addItem(edit, "Select All", null, e -> textArea.selectAll());

        JMenu view = new JMenu("View");
        addItem(view, "Font...", null, e -> chooseFont());

        JMenu help = new JMenu("Help");
        addItem(help, "About", null, e ->
                JOptionPane.showMessageDialog(
                        this,
                        "Agung Notepad\nJava Swing\n\nFeatures:\n- Find Case Sensitive / Insensitive\n- Shortcuts\n- Remember Last Folder",
                        "About",
                        JOptionPane.INFORMATION_MESSAGE
                )
        );

        mb.add(file);
        mb.add(edit);
        mb.add(view);
        mb.add(help);
        return mb;
    }

    private void addItem(JMenu m, String text, KeyStroke ks, ActionListener al) {
        JMenuItem mi = new JMenuItem(text);
        if (ks != null) mi.setAccelerator(ks);
        mi.addActionListener(al);
        m.add(mi);
    }

    private void setupKeyBindings() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        InputMap im = textArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = textArea.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask), "Undo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, mask), "Redo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, mask), "Find");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, mask), "Open");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, mask), "Save");
        
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, mask), "Copy");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, mask), "Cut");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, mask), "Paste");
        
        am.put("Undo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { doUndo(); }
        });
        am.put("Redo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { doRedo(); }
        });
        am.put("Find", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { showFindReplaceDialog(); }
        });
        am.put("Open", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { openFileDialog(); }
        });
        am.put("Save", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { saveFile(); }
        });
        
        
        am.put("Copy", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { textArea.cut(); }
        });
        am.put("Cut", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { textArea.copy(); }
        });
        am.put("Paste", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { showFindReplaceDialog(); }
        });
        
    }

    /* ================= FILE ================= */

    private void newFile() {
        if (!confirmSave()) return;
        textArea.setText("");
        currentFile = null;
        undoManager.discardAllEdits();
        setModified(false);
    }

    private void openFileDialog() {
        if (!confirmSave()) return;

        JFileChooser fc = new JFileChooser();
        File lastDir = getLastDirectory();
        if (lastDir != null) fc.setCurrentDirectory(lastDir);

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            saveLastDirectory(f);
            openFile(f);
        }
    }

    private void openFile(File f) {
        try {
            textArea.setText(readFile(f));
            currentFile = f;
            undoManager.discardAllEdits();
            setModified(false);
            statusLabel.setText("Opened: " + f.getAbsolutePath());
        } catch (IOException ex) {
            showError(ex.getMessage());
        }
    }

    private void saveFile() {
        if (currentFile == null) {
            saveFileAs();
            return;
        }
        try {
            writeFile(currentFile, textArea.getText());
            setModified(false);
            saveLastDirectory(currentFile);
            statusLabel.setText("Saved: " + currentFile.getName());
        } catch (IOException ex) {
            showError(ex.getMessage());
        }
    }

    private void saveFileAs() {
        JFileChooser fc = new JFileChooser();
        File lastDir = getLastDirectory();
        if (lastDir != null) fc.setCurrentDirectory(lastDir);

        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = fc.getSelectedFile();
            saveLastDirectory(currentFile);
            saveFile();
        }
    }

    private void exitApp() {
        if (!confirmSave()) return;
        System.exit(0);
    }

    private boolean confirmSave() {
        if (!modified) return true;
        int r = JOptionPane.showConfirmDialog(
                this, "Save changes?", "Confirm",
                JOptionPane.YES_NO_CANCEL_OPTION
        );
        if (r == JOptionPane.CANCEL_OPTION) return false;
        if (r == JOptionPane.YES_OPTION) saveFile();
        return !modified;
    }

    /* ================= EDIT ================= */

    private void doUndo() { if (undoManager.canUndo()) undoManager.undo(); }
    private void doRedo() { if (undoManager.canRedo()) undoManager.redo(); }

    private void setModified(boolean m) {
        modified = m;
        String name = (currentFile == null ? "Untitled" : currentFile.getName());
        setTitle(name + (modified ? "*" : "") + " - Agung Notepad");
    }

    private void updateStatus() {
        try {
            int pos = textArea.getCaretPosition();
            int line = textArea.getLineOfOffset(pos) + 1;
            int col = pos - textArea.getLineStartOffset(line - 1) + 1;
            statusLabel.setText("Ln " + line + ", Col " + col);
        } catch (Exception e) {
            statusLabel.setText("Ready");
        }
    }

    /* ================= FIND ================= */

    private void showFindReplaceDialog() {
        JDialog d = new JDialog(this, "Find & Replace", false);

        JTextField findField = new JTextField(20);
        JTextField replaceField = new JTextField(20);
        JCheckBox caseSensitive = new JCheckBox("Case Sensitive");

        JButton findBtn = new JButton("Find Next");
        JButton replaceBtn = new JButton("Replace All");

        d.setLayout(new GridLayout(4, 2, 4, 4));
        d.add(new JLabel("Find")); d.add(findField);
        d.add(new JLabel("Replace")); d.add(replaceField);
        d.add(caseSensitive); d.add(new JLabel());
        d.add(findBtn); d.add(replaceBtn);

        findBtn.addActionListener(e -> {
            String key = findField.getText();
            if (key.isEmpty()) return;

            String text = textArea.getText();
            int start = textArea.getCaretPosition();
            int idx = caseSensitive.isSelected()
                    ? text.indexOf(key, start)
                    : text.toLowerCase().indexOf(key.toLowerCase(), start);

            if (idx >= 0) {
                textArea.select(idx, idx + key.length());
            } else {
                JOptionPane.showMessageDialog(d, "Text not found");
            }
        });

        replaceBtn.addActionListener(e -> {
            String find = findField.getText();
            String repl = replaceField.getText();
            if (find.isEmpty()) return;

            String text = textArea.getText();

            if (caseSensitive.isSelected()) {
                textArea.setText(text.replace(find, repl));
            } else {
                StringBuilder sb = new StringBuilder();
                String lowerText = text.toLowerCase();
                String lowerFind = find.toLowerCase();
                int i = 0;
                while (true) {
                    int idx = lowerText.indexOf(lowerFind, i);
                    if (idx < 0) {
                        sb.append(text.substring(i));
                        break;
                    }
                    sb.append(text, i, idx).append(repl);
                    i = idx + find.length();
                }
                textArea.setText(sb.toString());
            }
        });

        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    /* ================= FONT ================= */

    private void chooseFont() {
        Font f = textArea.getFont();
        String size = JOptionPane.showInputDialog(this, "Font size:", f.getSize());
        try {
            textArea.setFont(f.deriveFont(Float.parseFloat(size)));
        } catch (Exception ignored) {}
    }

    /* ================= PREFS ================= */

    private File getLastDirectory() {
        String p = prefs.get(LAST_DIR_KEY, null);
        if (p != null) {
            File d = new File(p);
            if (d.exists() && d.isDirectory()) return d;
        }
        return null;
    }

    private void saveLastDirectory(File f) {
        if (f == null) return;
        File d = f.isDirectory() ? f : f.getParentFile();
        if (d != null) prefs.put(LAST_DIR_KEY, d.getAbsolutePath());
    }

    /* ================= IO ================= */

    private static String readFile(File f) throws IOException {
        return new String(
                java.nio.file.Files.readAllBytes(f.toPath()),
                StandardCharsets.UTF_8
        );
    }

    private static void writeFile(File f, String s) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            bw.write(s);
        }
    }

    private void showError(String m) {
        JOptionPane.showMessageDialog(this, m, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /* ================= MAIN ================= */

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            Notepad app = new Notepad();
            if (args.length > 0) {
                File f = new File(args[0]);
                if (f.exists()) app.openFile(f);
            }
        });
    }
}
