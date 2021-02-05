import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Stack;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Pair;
import lejos.robotics.geometry.Line;
import lejos.robotics.geometry.Rectangle;
import lejos.robotics.mapping.LineMap;
import lejos.robotics.navigation.Pose;
import lejos.robotics.navigation.Waypoint;
import lejos.robotics.pathfinding.Path;

public class PCMapper extends Application {

  private DataOutputStream out = null;
  private DataInputStream in = null;
  private double xScale;
  private double yScale;
  private final Map localMap = new Map();
  
  @FXML
  private Label connectedLabel;
  
  @FXML
  private Button connectButton;
  
  
  // Pane for adding nodes - like an image of the robot and Line's
  @FXML
  private Pane robotPane;
  
  ////////////////////////////////////
  // Fixed coordinates - using centimetres.
  // Just need a Lines vs Bounding Box choice.
  @FXML
  private Canvas gridCanvas;
  
  ////////////////////////////////////////////////////////////////////////////////
  // Two canvases used to allow drawing of lines on the map. 
  @FXML
  private Canvas mapCanvas;

  @FXML
  private Canvas pathCanvas;

  @FXML
  private Canvas mapLineCanvas;
  private Pair<Double, Double> initialTouch;

  @FXML
  void canvasDragged(MouseEvent event) {
    double x1 = initialTouch.getKey();
    double x2 = event.getX();
    double y1 = initialTouch.getValue();
    double y2 = event.getY();
    GraphicsContext context = mapLineCanvas.getGraphicsContext2D();
    context.clearRect(0, 0, mapLineCanvas.getWidth(), mapLineCanvas.getHeight());
    if (drawType == DrawType.BOUNDARY) {
      context.strokeRect(Math.min(x1, x2), Math.min(y1,  y2), Math.abs(x1 - x2), Math.abs(y1 - y2));
    } else {
      context.strokeLine(x1, y1, x2, y2);      
    }
  }

  @FXML
  void canvasPressed(MouseEvent event) {
    initialTouch = new Pair<>(event.getX(), event.getY());
  }
  
  @FXML
  private ImageView robot;

  @FXML
  private ImageView greyRobot;

  // Must remember to use a map changed etc,. boolean
  @FXML
  void canvasReleased(MouseEvent event) {
    GraphicsContext baseContext = mapCanvas.getGraphicsContext2D();
    double x1 = initialTouch.getKey();
    double x2 = event.getX();
    double y1 = initialTouch.getValue();
    double y2 = event.getY();
    switch (drawType) {
      case BOUNDARY:
        if (localMap.setBoundary(Math.min(x1, x2), Math.min(y1,  y2), Math.abs(x1 - x2), Math.abs(y1 - y2))) {
          localMap.drawMap();
        }
        break;
      case MAP:
        if (localMap.addLine(x1, y1, x2, y2)) {
          localMap.drawMap();
        }
        break;
      case DRIVE:
        // Move Car and set endpoint.
        if (localMap.setJourney(x1, y1, x2, y2)) {
          greyRobot.relocate(x1 - 20, y1 - 10);
          javafx.scene.shape.Line directPath = new javafx.scene.shape.Line(x1,y1,x2,y2);
          directPath.setStroke(Color.RED);
          double angle = 180 - Math.atan2(x2 - x1, y2 - y1) * 180 / Math.PI;
          greyRobot.setRotate(angle);
          List<Node> allNodesInPane = robotPane.getChildren();
          if (allNodesInPane.size() > 1) {
            allNodesInPane.set(0, directPath);
        } else {
            allNodesInPane.add(directPath);
          }
        }
    }    
    GraphicsContext context = mapLineCanvas.getGraphicsContext2D();
    context.clearRect(0, 0, mapLineCanvas.getWidth(), mapLineCanvas.getHeight());
  }

  @FXML
  private Spinner<Integer> spinner;

  @FXML
  void connect(ActionEvent event) {
    connectButton.setDisable(true);
    spinner.setDisable(true);
    
    // The server needs to be listening on the correct IP address/Port combo
    int val = (Integer) spinner.getValue();
    ServerSocket serverSocket= null;
    Socket clientSocket;
    try {
      InetAddress addr = InetAddress.getByName("10.0.1." + val);
      serverSocket = new ServerSocket(2468, 1, addr);
      serverSocket.setSoTimeout(100);
      clientSocket = serverSocket.accept();
      serverSocket.close();

      out = new DataOutputStream(clientSocket.getOutputStream());
      in = new DataInputStream(clientSocket.getInputStream());
    } catch (IOException e) {
      connectButton.setDisable(false);
      spinner.setDisable(false);
      return;
    } finally {
      if (serverSocket != null) {
        try {
          serverSocket.close();
        } catch (Exception e) {}
      }
    }
    connectedLabel.setVisible(true);
    Thread rdr = new ReaderThread(in);
    rdr.setDaemon(true);
    rdr.start();
  }
  
  enum DrawType { DRIVE, MAP, BOUNDARY }
  
  DrawType drawType = DrawType.MAP;
  
  // Draw lines when the map is selected, draw the boundary when it is selected, otherwise move car
  @FXML
  void DriveSelect(ActionEvent event) {
    drawType = DrawType.DRIVE;
  }

  @FXML
  void MapSelect(ActionEvent event) {
    drawType = DrawType.MAP;
  }

  @FXML
  void BoundarySelect(ActionEvent event) {
    drawType = DrawType.BOUNDARY;
  }

  @FXML
  void sendMap(ActionEvent event) {
  }
  @FXML
  void sendStop(ActionEvent event) {
  }
  @FXML
  void sendStart(ActionEvent event) {
  }
  @FXML
  void sendPlan(ActionEvent event) {
  }
  @FXML
  void sendExit(ActionEvent event) {
  }


  @FXML
  void procUndo(KeyEvent event) {
    if (localMap == null) return;
    if (event.getCharacter().charAt(0) == 8 || event.getCharacter().charAt(0) == 127) {
      localMap.removeLine();
    }
  }
  
  @Override
  public void stop() throws IOException{
      if (out != null) out.close();
  }
  
  @Override
  public void start(Stage primaryStage) throws IOException {

    final VBox page = (VBox) FXMLLoader.load(PCMapper.class.getClassLoader().getResource("View.fxml"));
    Scene scene = new Scene(page);
    primaryStage.setScene(scene);
    primaryStage.setTitle("Map Viewer");
    
    primaryStage.show();
    primaryStage.setMaxWidth(1300);
    primaryStage.setMaxHeight(800);
    
  }
  
  private void drawGridLines(Canvas mapArea) {
    GraphicsContext gContext = gridCanvas.getGraphicsContext2D();
    gContext.setFill(Color.BLACK);
    gContext.setStroke(Color.LIGHTGRAY);
    gContext.setLineWidth(1);
    double xStep = gridCanvas.getWidth() / 20 ;
    double yStep = gridCanvas.getHeight() / 15 ;
    xScale = (10 / xStep);
    yScale = (10 / yStep);
    
    int coord = 10;
    for (double xPos = xStep; xPos < gridCanvas.getWidth() ; xPos += xStep) {
      gContext.strokeLine(xPos, 0, xPos, gridCanvas.getHeight());     
      gContext.fillText(String.valueOf(coord), xPos, 20);
      coord += 10;
    }
    coord = 10;
    for (double yPos = yStep; yPos < gridCanvas.getHeight() ; yPos += yStep) {
      gContext.strokeLine(0, yPos, gridCanvas.getWidth(), yPos);      
      gContext.fillText(String.valueOf(coord), 20, yPos);
      coord += 10;
    }
  }

  @FXML
  public void initialize() {
      initSpinner();
      initDraw(mapCanvas.getGraphicsContext2D());
      initDraw(mapLineCanvas.getGraphicsContext2D());
      drawGridLines(mapCanvas);
      ColorAdjust colorAdjust = new ColorAdjust();
      colorAdjust.setBrightness(0.8);
      greyRobot.setEffect(colorAdjust);
      robot.setVisible(false);
  }
  
  private void initSpinner() {
    SpinnerValueFactory<Integer> valueFactory = //
        new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 254, 8);
    spinner.setValueFactory(valueFactory);
    spinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);    
  }

  private void initDraw(GraphicsContext gc){
    double canvasWidth = gc.getCanvas().getWidth();
    double canvasHeight = gc.getCanvas().getHeight();

    gc.setFill(Color.LIGHTGRAY);
    gc.setStroke(Color.BLACK);
    gc.setLineWidth(5);

    gc.fill();
    gc.strokeRect(
            0,              //x of the upper left corner
            0,              //y of the upper left corner
            canvasWidth,    //width of the rectangle
            canvasHeight);  //height of the rectangle

    gc.setFill(Color.RED);
    gc.setStroke(Color.BLUE);
    gc.setLineWidth(1);

}

  private class ReaderThread extends Thread {
    private DataInputStream in;
    Path path = new Path();
    Pose pose = new Pose();
    GraphicsContext context = pathCanvas.getGraphicsContext2D();
    public ReaderThread(DataInputStream in) {
      this.in = in;
    }

    public void run() {
      try {
        // We can read in car Pose or Planned Path - nothing else.
        while (true) {
          if (in.readChar() == 'P')  { // Pose
            pose.loadObject(in);
            Platform.runLater(new Runnable() {
              public void run() {
                robot.relocate(pose.getX(),  pose.getY());
                robot.setRotate(pose.getHeading());
              }
            });
          } else {
            path.loadObject(in);
            // Convert path to two array for using multiline draw
            int count = path.size();
            double[] xList = new double[count];
            double[] yList = new double[count];
            for (int index = 0 ; index < count; index++) {
              xList[index] = path.get(index).getX();
              yList[index] = path.get(index).getY();
            }
            Platform.runLater(new Runnable() {
              public void run() {
                context.save();
                context.setStroke(Color.LIGHTGREEN);
                context.strokePolyline(xList, yList, count);
                context.restore();
              }
            });
            
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
        return;
      }
    }
  }
  
  // We need our own class because the LineMap class does not allow deletes.
  private class Map {
    private boolean mapChanged = true;
    private boolean journeyChanged = true;
    private Stack<Line> lines = new Stack<>();
    private Rectangle boundary = null;
    private Pair<Double, Double> journeyStart = null;
    private Pair<Double, Double> journeyEnd = null;

    public Boolean addLine(double x1,double y1, double x2, double y2) {
      if (boundary == null || (boundary.contains(x1 * xScale, y1 * yScale) && boundary.contains(x2 * xScale, y2 * yScale))) {
        lines.push(new Line((float)(x1 * xScale), (float)(y1 * yScale), (float)(x2 * xScale), (float)(y2 * yScale)));
        mapChanged = true;
        return true;
      }
      return false;
    }
    public Boolean setBoundary(double x1, double y1, double w, double h) {
      Rectangle nBoundary = new Rectangle((float)(x1 * xScale), (float)(y1 * yScale), (float)(w * xScale), (float)(h * yScale));
      for (Line l: lines) {
        if (!nBoundary.contains(l.getP1()) || !nBoundary.contains(l.getP2())) {
          return false;
        }
      }
      boundary = nBoundary;
      mapChanged = true;
      return true;
    }
    public LineMap getLineMap() {
      if (!mapChanged || boundary == null || lines.size() == 0) {
        return null;
      }
      return new LineMap((Line[]) lines.toArray(), boundary);
    }
    public boolean setJourney(double x1, double y1, double x2, double y2) {
      if (boundary == null || !boundary.contains(x1 * xScale, y1 * yScale) || ! boundary.contains(x2 * xScale, y2 * yScale)) {
        return false;
      }
      journeyStart = new Pair<>(x1,y1);
      journeyEnd = new Pair<>(x2,y2);
      journeyChanged = true;
      return true;
    }
    public Waypoint getJourneyStart() {
      if (boundary == null || !journeyChanged || journeyStart == null) {
        return null;
      }
      return new Waypoint(journeyStart.getKey(), journeyStart.getValue());
    }
    public Waypoint getJourneyEnd() {
      if (boundary == null || !journeyChanged || journeyEnd == null) {
        return null;
      }
      return new Waypoint(journeyEnd.getKey(), journeyEnd.getValue());
    }
    public void drawMap() {
      GraphicsContext baseContext = mapCanvas.getGraphicsContext2D();      
      baseContext.clearRect(0, 0, mapCanvas.getWidth(), mapCanvas.getHeight());
      if (boundary != null) {
        baseContext.strokeRect(
          boundary.getX() / xScale, boundary.getY() / yScale, 
          boundary.getWidth() / xScale, boundary.getHeight() / yScale);
      }
      for (Line l: lines) {
        baseContext.strokeLine(l.getX1() / xScale, l.getY1() / yScale, l.getX2() / xScale, l.getY2() / yScale);
      }
    }
    public void removeLine() {
      if (lines.empty()) {
        return;
      }
      lines.pop();
      drawMap();
    }
  }
}