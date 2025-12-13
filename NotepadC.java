import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class NotepadC extends JFrame {
    private final JTextPane textPane = new JTextPane();
    private final JLabel statusLabel = new JLabel("Ready");
    private final UndoManager undoManager = new UndoManager();
    private File currentFile = null;
    private boolean modified = false;

    // Syntax highlighting
    private final StyleContext styleContext = new StyleContext();
    private final StyledDocument doc = new DefaultStyledDocument(styleContext);
    private final Style normal = styleContext.addStyle("normal", null);
    private final Style keyword = styleContext.addStyle("keyword", null);
    private final Style comment = styleContext.addStyle("comment", null);
    private final Style stringStyle = styleContext.addStyle("string", null);
    private final String[] cKeywords = {
            "int","float","double","char","void","if","else","for","while","do",
            "return","switch","case","break","continue","struct","typedef","const","sizeof"
    };

    public NotepadC() {
        super("NotepadC");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        // Text pane setup
        textPane.setDocument(doc);
        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        JScrollPane scroll = new JScrollPane(textPane);
        add(scroll, BorderLayout.CENTER);

        // Status bar
        JPanel status = new JPanel(new BorderLayout());
        status.add(statusLabel, BorderLayout.WEST);
        add(status, BorderLayout.SOUTH);

        // Styles
        StyleConstants.setForeground(normal, Color.BLACK);
        StyleConstants.setForeground(keyword, Color.BLUE);
        StyleConstants.setForeground(comment, Color.GRAY);
        StyleConstants.setForeground(stringStyle, new Color(206,123,0));

        // Document listener
        doc.addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { highlightSyntax(); setModified(true); updateStatus(); }
            public void removeUpdate(DocumentEvent e) { highlightSyntax(); setModified(true); updateStatus(); }
            public void changedUpdate(DocumentEvent e) { highlightSyntax(); setModified(true); updateStatus(); }
        });

        // Undo manager
        doc.addUndoableEditListener(e -> undoManager.addEdit(e.getEdit()));

        // Menu
        setJMenuBar(createMenuBar());

        // Key bindings undo/redo
        InputMap im = textPane.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = textPane.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "Undo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "Redo");
        am.put("Undo", new AbstractAction() { public void actionPerformed(ActionEvent e) { doUndo(); }});
        am.put("Redo", new AbstractAction() { public void actionPerformed(ActionEvent e) { doRedo(); }});

        setVisible(true);
    }

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem miNew = new JMenuItem("New"); miNew.addActionListener(e -> newFile());
        JMenuItem miOpen = new JMenuItem("Open..."); miOpen.addActionListener(e -> openFile());
        JMenuItem miSave = new JMenuItem("Save"); miSave.addActionListener(e -> saveFile());
        JMenuItem miSaveAs = new JMenuItem("Save As..."); miSaveAs.addActionListener(e -> saveFileAs());
        JMenuItem miExit = new JMenuItem("Exit"); miExit.addActionListener(e -> exitApp());
        file.add(miNew); file.add(miOpen); file.addSeparator(); file.add(miSave); file.add(miSaveAs); file.addSeparator(); file.add(miExit);

        JMenu edit = new JMenu("Edit");
        JMenuItem miUndo = new JMenuItem("Undo"); miUndo.addActionListener(e -> doUndo());
        JMenuItem miRedo = new JMenuItem("Redo"); miRedo.addActionListener(e -> doRedo());
        JMenuItem miCut = new JMenuItem("Cut"); miCut.addActionListener(e -> textPane.cut());
        JMenuItem miCopy = new JMenuItem("Copy"); miCopy.addActionListener(e -> textPane.copy());
        JMenuItem miPaste = new JMenuItem("Paste"); miPaste.addActionListener(e -> textPane.paste());
        JMenuItem miFind = new JMenuItem("Find / Replace..."); miFind.addActionListener(e -> showFindReplaceDialog());
        JMenuItem miSelectAll = new JMenuItem("Select All"); miSelectAll.addActionListener(e -> textPane.selectAll());
        edit.add(miUndo); edit.add(miRedo); edit.addSeparator();
        edit.add(miCut); edit.add(miCopy); edit.add(miPaste); edit.addSeparator();
        edit.add(miFind); edit.add(miSelectAll);

        JMenu view = new JMenu("View");
        JMenuItem miFont = new JMenuItem("Font..."); miFont.addActionListener(e -> chooseFont());
        view.add(miFont);

        JMenu help = new JMenu("Help");
        JMenuItem miAbout = new JMenuItem("About");
        miAbout.addActionListener(e -> JOptionPane.showMessageDialog(this, "NotepadC - Java Swing\nBy ChatGPT\nFeatures: Open/Save/Undo/Redo/Find/Replace/Syntax Highlight C", "About", JOptionPane.INFORMATION_MESSAGE));
        help.add(miAbout);

        mb.add(file); mb.add(edit); mb.add(view); mb.add(help);
        return mb;
    }

    private void newFile() { if (!confirmSave()) return; textPane.setText(""); currentFile=null; setModified(false); statusLabel.setText("Ready"); }
    private void openFile() { if (!confirmSave()) return; JFileChooser fc = new JFileChooser(); int rv=fc.showOpenDialog(this); if(rv==JFileChooser.APPROVE_OPTION){File f=fc.getSelectedFile(); try{String content=readFile(f); textPane.setText(content); currentFile=f; setModified(false); undoManager.discardAllEdits(); statusLabel.setText("Opened: "+f.getName());}catch(IOException ex){showError("Error opening file: "+ex.getMessage());}}}
    private void saveFile() { if(currentFile==null){saveFileAs(); return;} try{writeFile(currentFile,textPane.getText()); setModified(false); statusLabel.setText("Saved: "+currentFile.getName());}catch(IOException ex){showError("Error saving file: "+ex.getMessage());}}
    private void saveFileAs() { JFileChooser fc=new JFileChooser(); int rv=fc.showSaveDialog(this); if(rv==JFileChooser.APPROVE_OPTION){File f=fc.getSelectedFile(); try{writeFile(f,textPane.getText()); currentFile=f; setModified(false); statusLabel.setText("Saved: "+f.getName());}catch(IOException ex){showError("Error saving file: "+ex.getMessage());}}}
    private void exitApp() { if(!confirmSave()) return; System.exit(0);}
    private boolean confirmSave() { if(!modified) return true; int ans=JOptionPane.showConfirmDialog(this,"Save changes?","Confirm",JOptionPane.YES_NO_CANCEL_OPTION); if(ans==JOptionPane.CANCEL_OPTION || ans==JOptionPane.CLOSED_OPTION) return false; if(ans==JOptionPane.YES_OPTION){saveFile(); return !modified;} return true;}
    private void setModified(boolean m){modified=m; updateStatus();}
    
    // --------- Update status bar (line & column) ----------
    private void updateStatus() {
        int pos = textPane.getCaretPosition();
        String text = textPane.getText();
        int line = 1;
        int col = 1;

        for (int i = 0; i < pos; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }

        statusLabel.setText("Ln " + line + ", Col " + col + (modified ? "  (modified)" : ""));
    }

    private void doUndo(){if(undoManager.canUndo()) undoManager.undo();}
    private void doRedo(){if(undoManager.canRedo()) undoManager.redo();}

    // ---------------- Syntax highlighting ----------------
    private void highlightSyntax(){
        SwingUtilities.invokeLater(()->{
            try{
                String text=doc.getText(0,doc.getLength());
                doc.setCharacterAttributes(0,text.length(),normal,true);

                // keyword
                for(String kw:cKeywords){
                    int idx=0;
                    while((idx=text.indexOf(kw,idx))>=0){
                        if((idx==0 || !Character.isLetterOrDigit(text.charAt(idx-1))) &&
                           (idx+kw.length()==text.length() || !Character.isLetterOrDigit(text.charAt(idx+kw.length())))){
                            doc.setCharacterAttributes(idx,kw.length(),keyword,true);
                        }
                        idx+=kw.length();
                    }
                }

                // comments //
                int idx=0;
                while((idx=text.indexOf("//",idx))>=0){
                    int end=text.indexOf("\n",idx); if(end==-1) end=text.length();
                    doc.setCharacterAttributes(idx,end-idx,comment,true);
                    idx=end;
                }

                // strings ""
                idx=0;
                while((idx=text.indexOf("\"",idx))>=0){
                    int end=text.indexOf("\"",idx+1); if(end==-1) break;
                    doc.setCharacterAttributes(idx,end-idx+1,stringStyle,true);
                    idx=end+1;
                }

            }catch(BadLocationException e){ e.printStackTrace();}
        });
    }

    private void showFindReplaceDialog(){
        JDialog dlg=new JDialog(this,"Find & Replace",false);
        dlg.setLayout(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();
        c.insets=new Insets(4,4,4,4);
        c.gridx=0;c.gridy=0;c.anchor=GridBagConstraints.WEST;
        dlg.add(new JLabel("Find:"),c);
        c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;
        JTextField tfFind=new JTextField(30); dlg.add(tfFind,c);

        c.gridx=0;c.gridy=1;c.weightx=0;c.fill=GridBagConstraints.NONE;
        dlg.add(new JLabel("Replace:"),c);
        c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;
        JTextField tfReplace=new JTextField(30); dlg.add(tfReplace,c);

        c.gridx=0;c.gridy=2;c.gridwidth=2;
        JPanel pButtons=new JPanel();
        JButton bFindNext=new JButton("Find Next");
        JButton bReplace=new JButton("Replace");
        JButton bReplaceAll=new JButton("Replace All");
        pButtons.add(bFindNext); pButtons.add(bReplace); pButtons.add(bReplaceAll);
        dlg.add(pButtons,c);

        final int[] lastIndex={0};
        bFindNext.addActionListener(e->{
            String target=tfFind.getText(); if(target.isEmpty()) return;
            String content=textPane.getText();
            int start=Math.max(textPane.getSelectionEnd(),lastIndex[0]);
            int idx=content.indexOf(target,start);
            if(idx==-1) idx=content.indexOf(target,0);
            if(idx!=-1){textPane.requestFocus(); textPane.select(idx,idx+target.length()); lastIndex[0]=idx+target.length();}
            else JOptionPane.showMessageDialog(dlg,"Not found");
        });
        bReplace.addActionListener(e->{
            String target=tfFind.getText(); String rep=tfReplace.getText();
            if(!textPane.getSelectedText().isEmpty() && textPane.getSelectedText().equals(target)) textPane.replaceSelection(rep);
            else bFindNext.doClick();
        });
        bReplaceAll.addActionListener(e->{
            String target=tfFind.getText(); String rep=tfReplace.getText();
            if(target.isEmpty()) return;
            textPane.setText(textPane.getText().replace(target,rep));
        });

        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void chooseFont(){
        Font current=textPane.getFont();
        String family=(String)JOptionPane.showInputDialog(this,"Font family:","Font",JOptionPane.PLAIN_MESSAGE,null,GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(),current.getFamily());
        if(family!=null){
            String sizeStr=JOptionPane.showInputDialog(this,"Font size:",current.getSize());
            try{int size=Integer.parseInt(sizeStr); textPane.setFont(new Font(family,Font.PLAIN,size));}catch(Exception ex){}
        }
    }

    private static String readFile(File f) throws IOException{StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){String line; while((line=br.readLine())!=null) sb.append(line).append("\n");} return sb.toString();}
    private static void writeFile(File f,String content) throws IOException{try(BufferedWriter bw=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),StandardCharsets.UTF_8))){bw.write(content);}}
    private void showError(String msg){JOptionPane.showMessageDialog(this,msg,"Error",JOptionPane.ERROR_MESSAGE);}

    public static void main(String[] args){
        try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(Exception ignored){}
        SwingUtilities.invokeLater(NotepadC::new);
    }
}

