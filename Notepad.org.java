import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class Notepad extends JFrame {
    private final JTextArea textArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("Ready");
    private final UndoManager undoManager = new UndoManager();
    private File currentFile = null;
    private boolean modified = false;

    public Notepad() {
        super("Simple Notepad");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        // Text area setup
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        JScrollPane scroll = new JScrollPane(textArea);
        add(scroll, BorderLayout.CENTER);

        // Status bar
        JPanel status = new JPanel(new BorderLayout());
        status.add(statusLabel, BorderLayout.WEST);
        add(status, BorderLayout.SOUTH);

        // Document listener for modified flag and caret position
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { setModified(true); updateStatus(); }
            public void removeUpdate(DocumentEvent e) { setModified(true); updateStatus(); }
            public void changedUpdate(DocumentEvent e) { setModified(true); updateStatus(); }
        });
        textArea.addCaretListener(e -> updateStatus());

        // Undo manager
        textArea.getDocument().addUndoableEditListener(e -> undoManager.addEdit(e.getEdit()));

        // Menu
        setJMenuBar(createMenuBar());

        // Key bindings for undo/redo (Ctrl+Z / Ctrl+Y)
        InputMap im = textArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = textArea.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "Undo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "Redo");
        am.put("Undo", new AbstractAction() { public void actionPerformed(ActionEvent e) { doUndo(); }});
        am.put("Redo", new AbstractAction() { public void actionPerformed(ActionEvent e) { doRedo(); }});

        setVisible(true);
    }

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem miNew = new JMenuItem("New");
        miNew.addActionListener(e -> newFile());
        JMenuItem miOpen = new JMenuItem("Open...");
        miOpen.addActionListener(e -> openFile());
        JMenuItem miSave = new JMenuItem("Save");
        miSave.addActionListener(e -> saveFile());
        JMenuItem miSaveAs = new JMenuItem("Save As...");
        miSaveAs.addActionListener(e -> saveFileAs());
        JMenuItem miExit = new JMenuItem("Exit");
        miExit.addActionListener(e -> exitApp());
        file.add(miNew); file.add(miOpen); file.addSeparator(); file.add(miSave); file.add(miSaveAs); file.addSeparator(); file.add(miExit);

        JMenu edit = new JMenu("Edit");
        JMenuItem miUndo = new JMenuItem("Undo");
        miUndo.addActionListener(e -> doUndo());
        JMenuItem miRedo = new JMenuItem("Redo");
        miRedo.addActionListener(e -> doRedo());
        JMenuItem miCut = new JMenuItem("Cut");
        miCut.addActionListener(e -> textArea.cut());
        JMenuItem miCopy = new JMenuItem("Copy");
        miCopy.addActionListener(e -> textArea.copy());
        JMenuItem miPaste = new JMenuItem("Paste");
        miPaste.addActionListener(e -> textArea.paste());
        JMenuItem miFind = new JMenuItem("Find / Replace...");
        miFind.addActionListener(e -> showFindReplaceDialog());
        JMenuItem miSelectAll = new JMenuItem("Select All");
        miSelectAll.addActionListener(e -> textArea.selectAll());
        edit.add(miUndo); edit.add(miRedo); edit.addSeparator();
        edit.add(miCut); edit.add(miCopy); edit.add(miPaste); edit.addSeparator();
        edit.add(miFind); edit.add(miSelectAll);

        JMenu view = new JMenu("View");
        JMenuItem miFont = new JMenuItem("Font...");
        miFont.addActionListener(e -> chooseFont());
        view.add(miFont);

        JMenu help = new JMenu("Help");
        JMenuItem miAbout = new JMenuItem("About");
        miAbout.addActionListener(e -> JOptionPane.showMessageDialog(this, "Notepad Clone - Java Swing\nBy Agung Sudrajat S\nFeatures: Open/Save/Find/Undo/Redo", "About", JOptionPane.INFORMATION_MESSAGE));
        help.add(miAbout);

        mb.add(file);
        mb.add(edit);
        mb.add(view);
        mb.add(help);

        return mb;
    }

    private void newFile() {
        if (!confirmSave()) return;
        textArea.setText("");
        currentFile = null;
        setModified(false);
        setTitle("Simple Notepad");
    }

    private void openFile() {
        if (!confirmSave()) return;
        JFileChooser fc = new JFileChooser();
        int rv = fc.showOpenDialog(this);
        if (rv == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                String content = readFile(f);
                textArea.setText(content);
                currentFile = f;
                setModified(false);
                setTitle(f.getName() + " - Simple Notepad");
                undoManager.discardAllEdits();
            } catch (IOException ex) {
                showError("Error opening file: " + ex.getMessage());
            }
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
            statusLabel.setText("Saved: " + currentFile.getName());
        } catch (IOException ex) {
            showError("Error saving file: " + ex.getMessage());
        }
    }

    private void saveFileAs() {
        JFileChooser fc = new JFileChooser();
        int rv = fc.showSaveDialog(this);
        if (rv == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                writeFile(f, textArea.getText());
                currentFile = f;
                setModified(false);
                setTitle(f.getName() + " - Simple Notepad");
                statusLabel.setText("Saved: " + f.getName());
            } catch (IOException ex) {
                showError("Error saving file: " + ex.getMessage());
            }
        }
    }

    private void exitApp() {
        if (!confirmSave()) return;
        System.exit(0);
    }

    private boolean confirmSave() {
        if (!modified) return true;
        int ans = JOptionPane.showConfirmDialog(this, "Save changes?", "Confirm", JOptionPane.YES_NO_CANCEL_OPTION);
        if (ans == JOptionPane.CANCEL_OPTION || ans == JOptionPane.CLOSED_OPTION) return false;
        if (ans == JOptionPane.YES_OPTION) {
            saveFile();
            return !modified; // if still modified then user cancelled or error
        }
        return true; // NO: continue without saving
    }

    private void setModified(boolean m) {
        modified = m;
        String t = (currentFile == null ? "Untitled" : currentFile.getName()) + (modified ? "*" : "") + " - Simple Notepad";
        setTitle(t);
    }

    private void updateStatus() {
        int pos = textArea.getCaretPosition();
        try {
            int line = textArea.getLineOfOffset(pos) + 1;
            int col = pos - textArea.getLineStartOffset(line - 1) + 1;
            statusLabel.setText("Ln " + line + ", Col " + col + (modified ? "  (modified)" : ""));
        } catch (BadLocationException ex) {
            statusLabel.setText("Ready");
        }
    }

    private void doUndo() {
        if (undoManager.canUndo()) undoManager.undo();
    }

    private void doRedo() {
        if (undoManager.canRedo()) undoManager.redo();
    }

    private void showFindReplaceDialog() {
        JDialog dlg = new JDialog(this, "Find & Replace", false);
        dlg.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST;
        dlg.add(new JLabel("Find:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        JTextField tfFind = new JTextField(30);
        dlg.add(tfFind, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        dlg.add(new JLabel("Replace:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        JTextField tfReplace = new JTextField(30);
        dlg.add(tfReplace, c);

        c.gridx = 0; c.gridy = 2; c.gridwidth = 2;
        JPanel pButtons = new JPanel();
        JButton bFindNext = new JButton("Find Next");
        JButton bReplace = new JButton("Replace");
        JButton bReplaceAll = new JButton("Replace All");
        pButtons.add(bFindNext); pButtons.add(bReplace); pButtons.add(bReplaceAll);
        dlg.add(pButtons, c);

        // Find logic
        final int[] lastIndex = {0};
        bFindNext.addActionListener(e -> {
            String target = tfFind.getText();
            if (target.isEmpty()) return;
            String content = textArea.getText();
            int start = Math.max(textArea.getSelectionEnd(), lastIndex[0]);
            int idx = content.indexOf(target, start);
            if (idx == -1) {
                // wrap
                idx = content.indexOf(target, 0);
            }
            if (idx != -1) {
                textArea.requestFocus();
                textArea.select(idx, idx + target.length());
                lastIndex[0] = idx + target.length();
            } else {
                JOptionPane.showMessageDialog(dlg, "Not found");
            }
        });

        bReplace.addActionListener(e -> {
            String target = tfFind.getText();
            String rep = tfReplace.getText();
            if (!textArea.getSelectedText().isEmpty() && textArea.getSelectedText().equals(target)) {
                textArea.replaceSelection(rep);
            } else {
                bFindNext.doClick();
            }
        });

        bReplaceAll.addActionListener(e -> {
            String target = tfFind.getText();
            String rep = tfReplace.getText();
            if (target.isEmpty()) return;
            textArea.setText(textArea.getText().replace(target, rep));
        });

        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void chooseFont() {
        Font current = textArea.getFont();
        String family = (String) JOptionPane.showInputDialog(this, "Font family:", "Font", JOptionPane.PLAIN_MESSAGE, null, GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(), current.getFamily());
        if (family != null) {
            String sizeStr = JOptionPane.showInputDialog(this, "Font size:", current.getSize());
            try {
                int size = Integer.parseInt(sizeStr);
                textArea.setFont(new Font(family, Font.PLAIN, size));
            } catch (Exception ex) { /* ignore */ }
        }
    }

    private static String readFile(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private static void writeFile(File f, String content) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            bw.write(content);
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        // set native look & feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(Notepad::new);
    }
}

