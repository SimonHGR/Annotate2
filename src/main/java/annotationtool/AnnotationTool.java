package annotationtool;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class AnnotationTool extends JFrame {

  private record ShapeDef(Stroke stroke, Paint paint, Shape shape, Image img) {
  }

  private final Image backingMain;
  private final Image backingScratch;
  private final Color clearPaint = POINTER_MODE ?
      new Color(0, 0, 0, 255) :
      new Color(0, 0, 0, 0);

  private Paint paint;
  private Stroke stroke;

  private final Stroke blockOutStroke;
  private final Path2D.Float blockOutShape;

  private final Deque<ShapeDef> undoStack = new ArrayDeque<>();
  private final Deque<ShapeDef> redoStack = new ArrayDeque<>();

  private int saveImageIndex = 0;

  public AnnotationTool(int x, int y, int w, int h, String iconFile, int iconX, int iconY) {

    super("Drawing Frame");
    setUndecorated(true);
//    setOpacity(0.5F);
//    setOpacity(0); // makes entire window and contents invisible?

    Toolkit toolkit = Toolkit.getDefaultToolkit();

    try {
      InputStream imageStream = this.getClass().getResourceAsStream(iconFile);
      System.out.println("Stream is " + imageStream);
      Image image = ImageIO.read(imageStream);
      Cursor pencilCursor = toolkit.createCustomCursor(image, new Point(iconX, iconY), iconFile);
      setCursor(pencilCursor);
    } catch (IOException ioe) {
      ioe.printStackTrace(System.err);
    }

    setBounds(x - 5, y - 5, w + 10, h + 10);

    blockOutStroke = new BasicStroke(h, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL);
    blockOutShape = new Path2D.Float();
    blockOutShape.moveTo(0, h / 2);
    blockOutShape.lineTo(w, h / 2);

    // make the window transparent
    setBackground(clearPaint);  // TODO allow this to be black for pointer mode

    setPreferredSize(new Dimension(w + 10, h + 10));
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    enableEvents(AWTEvent.KEY_EVENT_MASK
        + AWTEvent.MOUSE_EVENT_MASK
        + AWTEvent.MOUSE_MOTION_EVENT_MASK);
    setVisible(true);

    backingMain = createImage(w, h);
    backingScratch = createImage(w, h);

    // create a drawing panel border, if desired!
//    Path2D.Float borderShape = new Path2D.Float();
//    borderShape.moveTo(0, 0);
//    borderShape.lineTo(w + 10, 0);
//    borderShape.lineTo(w + 10, h + 10);
//    borderShape.lineTo(0, h + 10);
//    borderShape.closePath();
//    border = new ShapeDef(
//        new BasicStroke(10, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER),
//        new Color(255, 128, 0, 255),
//        borderShape, null);

    addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        System.out.println("key: extended code is " + (int)e.getExtendedKeyCode());
        if (e.getKeyChar() == 26) { // Control-Z for undo
          undo();
        } else if (e.getKeyChar() == 25) { // Control-Y for redo
          redo();
        } else if (e.getKeyChar() == 'C' || e.getKeyChar() == 'c') { // Toggle controller box
          controllerBox.setVisible(!controllerBox.isVisible());
        }
      }
    });
  }

  public void setPaint(Paint paint) {
    this.paint = paint;
  }

  public void setStroke(Stroke stroke) {
    this.stroke = stroke;
  }

  public void doClear(Paint paint) {
    ShapeDef blockOutShapeDef = new ShapeDef(blockOutStroke, paint, blockOutShape, null);
    commitShape(blockOutShapeDef);
    repaint();
  }

  public void doClear() {
    doClear(clearPaint);
  }

  public void clearHistory() {
    doClear();
    undoStack.clear();
    redoStack.clear();
  }

  final ClipboardOwner clipboardOwner = (clipboard, contents) -> {
  };

  // expect to overwrite the null during startup
  private static Path baseDir = null;

  static {
    String imageDir = System.getProperty("annotate.imagedir");
    if (imageDir != null) baseDir = Paths.get(imageDir);
  }

  public void doLoad() {
    JFileChooser chooser = new JFileChooser();
    FileNameExtensionFilter filter = new FileNameExtensionFilter(
        "Image files", "bmp", "jpg", "jpeg", "png", "gif", "tif", "tiff");
    chooser.setFileFilter(filter);
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      File file = chooser.getSelectedFile();
      try {
        BufferedImage loadedImage = ImageIO.read(file);
        Image newImage = loadedImage;
        int loadedWidth = loadedImage.getWidth();
        int loadedHeight = loadedImage.getHeight();
        int thisWidth = this.getWidth();
        int thisHeight = this.getHeight();
        if (loadedWidth > thisWidth
            || loadedHeight > thisHeight) { // needs shrinking
          // keep aspect ratio
          double widthRatio = (double) thisWidth / loadedWidth;
          double heightRatio = (double) thisHeight / loadedHeight;
          double requiredRatio = Math.min(widthRatio, heightRatio);
          int targetWidth = (int) (loadedWidth * requiredRatio);
          int targetHeight = (int) (loadedHeight * requiredRatio);
          newImage = loadedImage.getScaledInstance(
              targetWidth, targetHeight, Image.SCALE_SMOOTH);
        }
        clearHistory();
        // add loadedImage as a "shape" to be drawn in the stack.
        commitShape(new ShapeDef(null, null, null, newImage));
        repaint();
      } catch (IOException e) {
        System.err.println("Failed to load image");
        e.printStackTrace(System.err);
      }
    }
  }

  public void doSave() {
    // find filename for use
    Path outPath;
    String fname;
    do {
      fname = String.format("image-%06d.png", saveImageIndex++);
      outPath = baseDir.resolve(fname);
    } while (Files.exists(outPath));

    String imageTag = "<img src='" + fname + "'>";
    Clipboard clip = this.getToolkit().getSystemClipboard();
    clip.setContents(new StringSelection(imageTag), clipboardOwner);
    System.out.println(imageTag);

    // no image found yet...
    BufferedImage outImg = null;

    // get bounding rectangle for image
    Rectangle bounds = this.getBounds();
    try {
      Robot robot = new Robot();
      outImg = robot.createScreenCapture(bounds);
    } catch (AWTException e) {
      System.err.println("Failed to create Robot for screen capture");
    }

    // fallback capture (does not get the background under
    // transparent pixels of drawing area)
    if (outImg == null && backingMain instanceof BufferedImage) {
      outImg = (BufferedImage) backingMain;
    }
//      else if (backingMain instanceof ToolkitImage) {
//        System.err.println("Using toolkit image...");
//        outImg = ((ToolkitImage) backingMain).getBufferedImage();
//      } 
    if (outImg == null) {
      System.err.println("Failed to find workable image capture method");
      return; // give up
    }

    try {
      ImageIO.write(outImg, "png", outPath.toFile());
    } catch (IOException ex) {
      System.err.println("Save failed: " + ex.getMessage());
    }
  }

  @Override
  public void paint(Graphics graphics) {
    // Blank out the scratch image
    Graphics2D gScratch = (Graphics2D) backingScratch.getGraphics();
    gScratch.setComposite(AlphaComposite.Src);
    gScratch.setBackground(clearPaint);
    gScratch.clearRect(0, 0, this.getBounds().width, this.getBounds().height);
    gScratch.drawImage(backingMain, 0, 0, null);

    gScratch.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);
    // if there is a "shape in progress" draw it on the scratch image
    if (p2d != null) {
      gScratch.setPaint(paint);
      gScratch.setStroke(stroke);
      gScratch.draw(p2d);
    }

    Graphics2D g = (Graphics2D) graphics;
    g.setComposite(AlphaComposite.Src);
    AffineTransform trans = g.getTransform();
    g.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);
//    g.translate(5, 5);
    g.drawImage(backingScratch, 0, 0, null);
    g.setTransform(trans);
//    g.setPaint(border.paint);
//    g.setStroke(border.stroke);
//    g.draw(border.shape);
  }

  private Path2D.Float p2d; // shape in progress...

  public void undo() {
    if (!undoStack.isEmpty()) {
      ShapeDef sd = undoStack.pop();
      redoStack.push(sd);
      paintFromUndoStack();
    }
  }

  public void redo() {
    if (!redoStack.isEmpty()) {
      ShapeDef sd = redoStack.pop();
      undoStack.push(sd);
      paintFromUndoStack();
    }
  }

  private void paintFromUndoStack() {
    Graphics2D g = (Graphics2D) backingMain.getGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);

    g.setBackground(clearPaint);
    g.setComposite(AlphaComposite.Src);
    g.clearRect(0, 0, this.getBounds().width, this.getBounds().height);
    Iterator<ShapeDef> sdi = undoStack.descendingIterator();
    while (sdi.hasNext()) {
      ShapeDef s = sdi.next();
      if (s.stroke != null) {
        g.setPaint(s.paint);
        g.setStroke(s.stroke);
        g.draw(s.shape);
      } else {
        assert s.img != null;
        g.drawImage(s.img, 0, 0, null);
      }
    }
    repaint();
  }

  private void commitShape(ShapeDef s) {
    undoStack.push(s);
    Graphics2D g = (Graphics2D) backingMain.getGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);

    if (s.shape != null) {
      g.setComposite(AlphaComposite.Src);
      g.setPaint(s.paint);
      g.setStroke(s.stroke);
      g.draw(s.shape);
      p2d = null;
    } else {
      assert s.img != null;
      g.drawImage(s.img, 0, 0, null);
    }
  }

  @Override
  protected void processEvent(AWTEvent evt) {
    super.processEvent(evt);
    if (evt instanceof MouseEvent me) {
      if (me.getID() == MouseEvent.MOUSE_PRESSED) {
        p2d = new Path2D.Float();
        p2d.moveTo(me.getX(), me.getY());
      } else if (p2d != null && me.getID() == MouseEvent.MOUSE_DRAGGED) {
        p2d.lineTo(me.getX(), me.getY());
      } else if (p2d != null && me.getID() == MouseEvent.MOUSE_RELEASED) {
        ShapeDef sd = new ShapeDef(stroke, paint, p2d, null);
        commitShape(sd);
      }
      repaint();
    }
  }

  public static boolean POINTER_MODE = false;
  public static ControllerBox controllerBox;

  public static void main(final String[] args) {
    System.err.println("Annotation tool by simon@dancingcloudservices.com");
    System.err.println("Icons by www.iconfinder.com");
    int x1 = 0, y1 = 0; // default top-left
    Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
    int w1 = size.width, h1 = size.height; // default width/height
    String iconFile1 = "CrossHairs16.png"; // default image icon
    int iconX1 = 7, iconY1 = 7; // hotspot for CrossHairs16 image

    if (args.length == 1 && "pointer".equals(args[0])) {
      POINTER_MODE = true;
      iconFile1 = "pointing_hand-64.png";
      iconX1 = 27;
      iconY1 = 1;
    } else if (args.length == 2 || args.length == 4 || args.length == 7) {
      w1 = Integer.parseInt(args[0]);
      h1 = Integer.parseInt(args[1]);
      if (args.length > 2) {
        x1 = Integer.parseInt(args[2]);
        y1 = Integer.parseInt(args[3]);
      }
      if (args.length > 4) {
        iconFile1 = args[4];
        iconX1 = Integer.parseInt(args[5]);
        iconY1 = Integer.parseInt(args[6]);
      }
      System.out.println("AnnotationTool " + w1 + " by " + h1 + " offset: " + x1 + "," + y1);
    } else if (args.length != 0) {
      System.err.println("Usage: java annotationtool.AnnotationTool "
          + "[<width> <height> [ <x> <y>]] [<iconfile>, <hotspotX>, <hotspotY>]"
          + "\nUsing defaults " + w1 + "x" + h1 + " @" + x1 + "," + y1 +
          ", cursor " + iconFile1 + ", hotspot " + iconX1 + "," + iconY1);
    }
    final int x = x1, y = y1, w = w1, h = h1;
    final String iconFile = iconFile1;
    final int iconX = iconX1, iconY = iconY1;
    // Create the GUI on the event-dispatching thread
    SwingUtilities.invokeLater(() -> {
          GraphicsEnvironment ge = GraphicsEnvironment
              .getLocalGraphicsEnvironment();

          // check if the OS supports translucency
          if (!ge.getDefaultScreenDevice().isWindowTranslucencySupported(
              GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
            System.err.println("Transparent window not supported");
          }
          System.err.println("Per-pixel transluscent OK...");

          if (baseDir == null && !POINTER_MODE) {
            // launch a file selector box to determine base save directory.
            JFileChooser jfc = new JFileChooser();
            jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            while (jfc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
              ;
            baseDir = jfc.getSelectedFile().toPath();
          }
          System.out.println("Selected base directory for images is: " + baseDir);

          controllerBox = new ControllerBox(
              new AnnotationTool(x, y, w, h, iconFile, iconX, iconY)
          );
          controllerBox.setBounds(x + w + 10, y, 0, 0);
          controllerBox.pack();
          controllerBox.setVisible(!POINTER_MODE);
        }
    );
  }
}
