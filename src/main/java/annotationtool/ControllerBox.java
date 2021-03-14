package annotationtool;

import util.GridBagConstraintBuilder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

class ControllerBox extends JFrame {

  private AnnotationTool annotationTool;
  private static final int SWATCH_SIZE = 24;

  private static class SwatchIcon implements Icon {

    private Paint paint;

    public SwatchIcon(Paint p) {
      this.paint = p;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Dimension size = c.getSize();
      Graphics2D g2d = (Graphics2D) g;
      g2d.setPaint(paint);
      g2d.fillRect(x, y, size.width, size.height);
      if (((AbstractButton) c).isSelected()) {
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(8, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g2d.drawRect(x, y, size.width, size.height);
      }
    }

    @Override
    public int getIconWidth() {
      return SWATCH_SIZE;
    }

    @Override
    public int getIconHeight() {
      return SWATCH_SIZE;
    }
  }

  private static final Color[] penColors = {
      new Color(255, 0, 0, 255),
      new Color(255, 128, 0, 255),
      new Color(255, 255, 0, 255),
      new Color(0, 255, 0, 255),
      new Color(0, 0, 255, 255),
      new Color(255, 0, 255, 255),
      new Color(0, 0, 0, 255),
      new Color(255, 255, 255, 255),};

  private static final Color[] highlighterColors = {
      new Color(255, 0, 0, 128),
      new Color(255, 128, 0, 128),
      new Color(255, 255, 0, 128),
      new Color(0, 255, 0, 128),
      new Color(0, 0, 255, 128),
      new Color(0, 0, 0, 0)
  };
  private static final Color DEFAULT_COLOR = highlighterColors[2]; // Yellow highlighter

  private static class NamedStroke {
    public String name;
    public BasicStroke stroke;

    public NamedStroke(String name, BasicStroke stroke) {
      this.name = name;
      this.stroke = stroke;
    }
  }

  private static final NamedStroke[] strokes = {
      new NamedStroke("Thin", new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)),
      new NamedStroke("Medium", new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)),
      new NamedStroke("Thick", new BasicStroke(30, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)),
      new NamedStroke("Huge", new BasicStroke(70, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)),
  };
  private static final NamedStroke DEFAULT_STROKE = strokes[2];

  private static class PaintPalletteActionListener implements ActionListener {

    private AnnotationTool annotationTool;
    private Color paint;

    public PaintPalletteActionListener(AnnotationTool at, Color ppi) {
      annotationTool = at;
      paint = ppi;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      annotationTool.setPaint(paint);
    }
  }

  public ControllerBox(AnnotationTool at) {
    super("Tools");

    setLayout(new FlowLayout());
    JPanel leftPanel = new JPanel();
    add(leftPanel);
    JPanel rightPanel = new JPanel();
    add(rightPanel);

    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    annotationTool = at;
    leftPanel.setLayout(new GridBagLayout());
    rightPanel.setLayout(new GridBagLayout());

    GridBagConstraintBuilder leftGbcb = new GridBagConstraintBuilder(6);
    GridBagConstraintBuilder rightGbcb = new GridBagConstraintBuilder(6);
    this.setAlwaysOnTop(true);

    ButtonGroup toolGroup = new ButtonGroup();

    leftPanel.add(new JLabel("Pens"), leftGbcb.fullWidth().build());
    leftGbcb.nextY().singleWidth();
    JRadioButton defaultColorButton = null;
    for (Color ppi : penColors) {
      boolean defaultSelection = DEFAULT_COLOR == ppi;
      JRadioButton jrb = new JRadioButton(null, new SwatchIcon(ppi), defaultSelection);
      if (defaultSelection) defaultColorButton = jrb;
      jrb.addActionListener(new PaintPalletteActionListener(at, ppi));
      leftPanel.add(jrb, leftGbcb.build());
      leftGbcb.nextX();
      toolGroup.add(jrb);
    }
    leftPanel.add(new JLabel("Highlighters"), leftGbcb.fullWidth().nextX().build());
    leftGbcb.nextY().singleWidth();

    for (Color ppi : highlighterColors) {
      boolean defaultSelection = DEFAULT_COLOR == ppi;
      JRadioButton jrb = new JRadioButton(null, new SwatchIcon(ppi), defaultSelection);
      if (defaultSelection) defaultColorButton = jrb;
      jrb.addActionListener(new PaintPalletteActionListener(at, ppi));
      leftPanel.add(jrb, leftGbcb.build());
      leftGbcb.nextX();
      toolGroup.add(jrb);
    }
    // select the default color
    defaultColorButton.doClick();

    leftPanel.add(new JLabel("Pen Sizes"), leftGbcb.fullWidth().nextY().build());
    ButtonGroup thicknessGroup = new ButtonGroup();
    for (NamedStroke ns : strokes) {
      JRadioButton jrb = new JRadioButton(ns.name);
      jrb.addActionListener(e -> annotationTool.setStroke(ns.stroke));
      leftPanel.add(jrb, leftGbcb.nextY().build());
      leftGbcb.nextY();
      thicknessGroup.add(jrb);
      if (ns == DEFAULT_STROKE) jrb.doClick();
    }

    JButton eraseButton = new JButton("Erase Transparent");
    eraseButton.addActionListener(e -> annotationTool.doClear());
    rightPanel.add(eraseButton, rightGbcb.build());
    rightGbcb.nextY();

    JButton eraseWhiteButton = new JButton("Erase White");
    eraseWhiteButton.addActionListener(e -> annotationTool.doClear(new Color(255, 255, 255, 255)));
    rightPanel.add(eraseWhiteButton, rightGbcb.build());
    rightGbcb.nextY();

    JButton undoButton = new JButton("Undo");
    undoButton.addActionListener(e -> annotationTool.undo());
    rightPanel.add(undoButton, rightGbcb.build());
    rightGbcb.nextY();

    JButton redoButton = new JButton("Redo");
    redoButton.addActionListener(e -> annotationTool.redo());
    rightPanel.add(redoButton, rightGbcb.build());
    rightGbcb.nextY();

    JButton killHistoryButton = new JButton("Clear History");
    killHistoryButton.addActionListener(e -> annotationTool.clearHistory());
    rightPanel.add(killHistoryButton, rightGbcb.build());
    rightGbcb.nextY();

    rightPanel.add(new JLabel("----------"), rightGbcb.build());
    rightGbcb.nextY();

    JButton bringToTop = new JButton("Bring to top");
    bringToTop.addActionListener(e -> {
        annotationTool.toFront();
        annotationTool.setAlwaysOnTop(true);
    });
    rightPanel.add(bringToTop, rightGbcb.build());
    rightGbcb.nextY();

    JButton sendBack = new JButton("Send to back");
    sendBack.addActionListener(e -> {
      annotationTool.setAlwaysOnTop(false);
      annotationTool.toBack();
    });
    rightPanel.add(sendBack, rightGbcb.build());
    rightGbcb.nextY();

    JButton save = new JButton("Save image");
    save.addActionListener(e -> annotationTool.doSave());
    rightPanel.add(save, rightGbcb.build());
    rightGbcb.nextY();

    JButton quit = new JButton("Exit");
    quit.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (JOptionPane.showConfirmDialog(
            ControllerBox.this, "Confirm quit?", "Confirm quit",
            JOptionPane.YES_NO_OPTION)
            == JOptionPane.YES_OPTION) {
          System.exit(0);
        }
      }
    });
    rightPanel.add(quit, rightGbcb.build());
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        if (JOptionPane.showConfirmDialog(
            ControllerBox.this, "Confirm quit?", "Confirm quit",
            JOptionPane.YES_NO_OPTION)
            == JOptionPane.YES_OPTION) {
          System.exit(0);
        }
      }
    });
    rightGbcb.nextY();
  }
}
