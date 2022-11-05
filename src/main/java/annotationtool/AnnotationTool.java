package annotationtool;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
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

  private class ShapeDef {

    Shape shape;
    Paint paint;
    Stroke stroke;
    Image img;

    ShapeDef(Stroke stroke, Paint paint, Shape shape, Image img) {
      this.stroke = stroke;
      this.paint = paint;
      this.shape = shape;
      this.img = img;
    }
  }

  private Image backingMain;
  private Image backingScratch;
  private static Color clearPaint = new Color(0, 0, 0, 0);

  private Paint paint;
  private Stroke stroke;

  private ShapeDef blockOutShapeDef;
//  private ShapeDef border;

  private Deque<ShapeDef> undoStack = new ArrayDeque<>();
  private Deque<ShapeDef> redoStack = new ArrayDeque<>();

  private Cursor defaultCursor;
  private Cursor pencilCursor;

  private int saveImageIndex = 0;

  public AnnotationTool(int x, int y, int w, int h) {

    super("Drawing Frame");
    setUndecorated(true);
//    setOpacity(0.5F);
//    setOpacity(0); // makes entire window and contents invisible?

    Toolkit toolkit = Toolkit.getDefaultToolkit();

    try {
//      InputStream imageStream = this.getClass().getResourceAsStream("pencil-32.png");
      InputStream imageStream = this.getClass().getResourceAsStream("CrossHairs16.png");
      System.out.println("Stream is " + imageStream);
      Image image = ImageIO.read(imageStream);
//      pencilCursor = toolkit.createCustomCursor(image, new Point(0, 26), "pencil");
      pencilCursor = toolkit.createCustomCursor(image, new Point(7, 7), "pencil");
      defaultCursor = getCursor();
      setCursor(pencilCursor);
    } catch (IOException ioe) {
      ioe.printStackTrace(System.err);
    }

    setBounds(x - 5, y - 5, w + 10, h + 10);

    Stroke blockOutStroke;
    Path2D.Float blockOutShape;
    blockOutStroke = new BasicStroke(h, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL);
    blockOutShape = new Path2D.Float();
    blockOutShape.moveTo(0, h / 2);
    blockOutShape.lineTo(w, h / 2);
    blockOutShapeDef = new ShapeDef(blockOutStroke, clearPaint, blockOutShape, null);

    // make the window transparent
    setBackground(clearPaint);

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
  }

  public void setPaint(Paint paint) {
    this.paint = paint;
  }

  public void setStroke(Stroke stroke) {
    this.stroke = stroke;
  }

  public void doClear(Paint paint) {
    blockOutShapeDef.paint = paint;
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

  final ClipboardOwner clipboardOwner = new ClipboardOwner() {
    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
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
        clearHistory();
        // add loadedImage as a "shape" to be drawn in the stack.
        commitShape(new ShapeDef(null, null, null, loadedImage));
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
      if(outImg == null && backingMain instanceof BufferedImage) {
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
    System.err.println("drawing backingMain");
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
    if (undoStack.size() > 0) {
      ShapeDef sd = undoStack.pop();
      redoStack.push(sd);
      paintFromUndoStack();
    }
  }

  public void redo() {
    if (redoStack.size() > 0) {
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
    if (evt instanceof MouseEvent) {
      MouseEvent me = (MouseEvent) evt;
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

  public static void main(final String[] args) {
    System.err.println("Annotation tool by simon@dancingcloudservices.com");
    System.err.println("Icons by www.iconfinder.com");
    int x1 = 50, y1 = 50, w1 = 1280, h1 = 720;
    if (args.length == 2 || args.length == 4) {
      w1 = Integer.parseInt(args[0]);
      h1 = Integer.parseInt(args[1]);
      if (args.length == 4) {
        x1 = Integer.parseInt(args[2]);
        y1 = Integer.parseInt(args[3]);
      }
      System.out.println("AnnotationTool " + w1 + " by " + h1 + " offset: " + x1 + "," + y1);
    } else if (args.length != 0) {
      System.err.println("Usage: java annotationtool.AnnotationTool "
          + "[<width> <height> [ <x> <y>]]"
          + "\nUsing defaults 1280 720 50 50");
    }
    final int x = x1, y = y1, w = w1, h = h1;
    // Create the GUI on the event-dispatching thread
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        GraphicsEnvironment ge = GraphicsEnvironment
            .getLocalGraphicsEnvironment();

        // check if the OS supports translucency
        if (!ge.getDefaultScreenDevice().isWindowTranslucencySupported(
            GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
          System.err.println("Transparent window not supported");
        }
        System.err.println("Per-pixel transluscent OK...");

        if (baseDir == null) {
          // launch a file selector box to determine base save directory.
          JFileChooser jfc = new JFileChooser();
          jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
          while (jfc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
            ;
          baseDir = jfc.getSelectedFile().toPath();
        }
        System.out.println("Selected base directory for images is: " + baseDir);

        ControllerBox controllerBox = new ControllerBox(
            new AnnotationTool(x, y, w, h)
        );
        controllerBox.setBounds(x + w + 10, y, 0, 0);
        controllerBox.pack();
        controllerBox.setVisible(true);
      }
    });
  }
}
