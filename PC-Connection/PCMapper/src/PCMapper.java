import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Stack;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javafx.util.Pair;
import lejos.robotics.geometry.Line;
import lejos.robotics.geometry.Rectangle;
import lejos.robotics.mapping.LineMap;
import lejos.robotics.navigation.Pose;
import lejos.robotics.navigation.Waypoint;
import lejos.robotics.pathfinding.Path;

public class PCMapper extends Application {

  public enum COMMANDS {
    POSE('P'), DESTINATION('D'), START('B'), STOP('E'), EXIT('X'), MAP('M');
    private final char keyCode;
    private COMMANDS(char k) { keyCode = k;}
    public char getCode() { 
      return keyCode;
    }
  }

  private static final double X_ROBOT_OFFSET = 20.5;
  private static final double Y_ROBOT_OFFSET = 26;

  private DataOutputStream out = null;
  private DataInputStream in = null;
  private double xScale;
  private double yScale;
  private Map localMap = null;

  enum DrawType { DRIVE, MAP, BOUNDARY }
  DrawType drawType = DrawType.MAP; // Change the effect of clicking on the canvas.

  //////////////////////////////////////////////
  // The user interface components controls
  @FXML
  private Label connectedLabel;
  
  @FXML
  private Button connectButton;
  
  @FXML
  private Button sendButton;
  
  @FXML
  private HBox drawButtons;
  
  @FXML
  private FlowPane connectControls;
  
  @FXML
  private Spinner<Integer> spinner; // Get the IP address last digit.

  @FXML
  private Button startStopButton;

  ///////////////////////////////////////////////////////////
  // The many layers in the Stack of panes for drawing
  @FXML
  private Pane robotPane; // Just for the robot and its planned destination

  @FXML
  private ImageView robot; // The robot picture on the robot pane.
  private Rotate robotRotation; // The current facing direction of the robot.
  
  @FXML
  private javafx.scene.shape.Line journeyArrow; // The journey we are planning.

  @FXML
  private Canvas gridCanvas; // Fixed coordinates - using centimetres. 
  
  @FXML
  private Canvas mapCanvas; // Show the lines and the boundary

  @FXML
  private Canvas pathCanvas; // Show the planned path sent to us from the EV3 

  @FXML
  private Canvas mapLineCanvas; // Only used as a temporary drawing area - and to collect mouse and keyboard events

  //////////////////////////////////////////////////////////////////////
  // Set appropriate controls active at different times
  public void hasConnected(boolean connected) {
    connectControls.setDisable(connected);
    sendButton.setDisable(!connected);
    hasSentRoute(false); // Whenever the connection status changes, we have not yet sent the map.
    mapAlterable(true); // ... and the map is drawable.
    connectedLabel.setVisible(connected);
  }
  
//  public void disableSend(boolean cannotSend) {
//    sendButton.setDisable(cannotSend);
//  }
  
  public void hasSentRoute(boolean sent) {
    startStopButton.setDisable(!sent);
  }

  private void mapAlterable(boolean alterable) {
    startStopButton.setText(alterable ? "Start" : "Stop");
    drawButtons.setDisable(!alterable);
    // change mouse and keyboard sensitivity for the canvas.
    mapLineCanvas.setDisable(!alterable);
  }
  
  /////////////////////////////////////////////////////////////////////
  // All of this is mechanisms for collecting user input
  private Pair<Double, Double> initialTouch;

  @FXML
  void canvasDragged(MouseEvent event) { // This is a listener for mapLineCanvas.
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
  void canvasPressed(MouseEvent event) { // This is a listener for mapLineCanvas.
    initialTouch = new Pair<>(event.getX(), event.getY());
  }
  
  @FXML
  void canvasReleased(MouseEvent event) { // This is a listener for mapLineCanvas.
    double x1 = initialTouch.getKey();
    double x2 = event.getX();
    double y1 = initialTouch.getValue();
    double y2 = event.getY();
    switch (drawType) {
      case BOUNDARY:
        if (localMap.setBoundary(Math.max(x1, x2), Math.max(y1,  y2), Math.abs(x1 - x2), Math.abs(y1 - y2))) {
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
        // If initial press close to release then it is an endpoint, otherwise it is the pose
        double angle = -1 * Math.atan2(x2 - x1, y2 - y1) * 180 / Math.PI;
        journeyArrow.setStartX(robot.getLayoutX() + X_ROBOT_OFFSET);
        journeyArrow.setStartY(robot.getLayoutY() + Y_ROBOT_OFFSET);
       
        if (Math.abs(x1 - x2) + Math.abs(y1 - y2) < Y_ROBOT_OFFSET && localMap.setDestination(x1, y1)) {
          journeyArrow.setEndX(x1);
          journeyArrow.setEndY(y1);
        } else if (localMap.setPose(x1,y1, angle)) {
          robotRotation.setAngle(angle);
          robot.relocate(x1 - X_ROBOT_OFFSET, y1 - Y_ROBOT_OFFSET);
        }
    }    
    GraphicsContext context = mapLineCanvas.getGraphicsContext2D();
    context.clearRect(0, 0, mapLineCanvas.getWidth(), mapLineCanvas.getHeight());
  }

  @FXML
  void DriveSelect(ActionEvent event) { // Draw the robot pose (click and drag) or the destination (click and release)
    drawType = DrawType.DRIVE;
  }

  @FXML
  void MapSelect(ActionEvent event) { // Draw lines on the map when selected - Delete KEY removes lines 
    drawType = DrawType.MAP;
  }

  @FXML
  void BoundarySelect(ActionEvent event) { // Draw the boundary
    drawType = DrawType.BOUNDARY;
  }

  @FXML
  void procUndo(KeyEvent event) {
    if (localMap == null) return;
    if (event.getCharacter().charAt(0) == 8 || event.getCharacter().charAt(0) == 127) {
      localMap.removeLine();
    }
  }

  ///////////////////////////////////////////////////////////////////////////////
  // The connect callback - this should really be a JavaFX Task
  @FXML
  void connect(ActionEvent event) {
    
    // The server needs to be listening on the correct IP address/Port combo
    int val = (Integer) spinner.getValue();
    ServerSocket serverSocket= null;
    Socket clientSocket;
    try {
      InetAddress addr = InetAddress.getByName("10.0.1." + val);
      serverSocket = new ServerSocket(2468, 1, addr);
      clientSocket = serverSocket.accept();
      serverSocket.close();

      out = new DataOutputStream(clientSocket.getOutputStream());
      in = new DataInputStream(clientSocket.getInputStream());
    } catch (IOException e) {
      return;
    } finally {
      if (serverSocket != null) {
        try {
          serverSocket.close();
        } catch (Exception e) {}
      }
    }
    Thread rdr = new ReaderThread(in);
    rdr.setDaemon(true);
    rdr.start();
    hasConnected(true);
  }
  
  ////////////////////////////////////////////////////////////////////////////////////
  // Send all (updated) data to the EV3
  @FXML
  void sendUpdate(ActionEvent event) {
    // First send the map
    LineMap mapToSend = localMap.getLineMap();
    if (mapToSend != null) {
      try {
        out.writeChar(COMMANDS.MAP.getCode());
        mapToSend.dumpObject(out);
      } catch (IOException e) {
        hasConnected(false);  // Connection broken.  Change all active components.
      }
    }
    
    // Next send the Pose
    Pose currentPose = localMap.getPose();
    if (currentPose != null) {
      // System.out.println("Sending (PC) Pose: " + currentPose.getX() + ", " + currentPose.getY() + ", " + currentPose.getHeading());
      try {
        out.writeChar(COMMANDS.POSE.getCode());
        currentPose.dumpObject(out);
      } catch (IOException e) {
        hasConnected(false);  // Connection broken.  Change all active components.
      }
      hasSentRoute(true);
    }
    
    // Finally send the new destination
    Waypoint goTo = localMap.getDestination();
    if (goTo != null) {
      // System.out.println("Sending (PC) Destination: " + goTo.getX() + ", " + goTo.getY());
      try {
        out.writeChar(COMMANDS.DESTINATION.getCode());
        goTo.dumpObject(out);
      } catch (IOException e) {
        hasConnected(false);  // Connection broken.  Change all active components.
      }
      hasSentRoute(true);
    }
  }
  
  ///////////////////////////////////////////////////////////
  // Start and stop the robot moving

  private boolean isStopped = true;
  @FXML
  void sendStartStop(ActionEvent event) {
    try {
      out.writeChar(isStopped ? COMMANDS.START.getCode() : COMMANDS.STOP.getCode());
      isStopped = !isStopped;
      mapAlterable(isStopped); // If we are stopped then we can alter the map.
      hasSentRoute(!isStopped); // We can only start a route once.
      // It is now not necessary to disable sending the same route twice as the EV3 deals with this.
//      disableSend(true);  // Disable sending of route button whenever we press start/stop 
    } catch (IOException e) {
      hasConnected(false);  // Connection broken.  Change all active components.
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////
  // Exit this program - the EV3 notices this! - The stop method is part of the JavaFX framework
  @FXML
  void sendExit(ActionEvent event) {
    Platform.exit();
  }
  
  @Override
  public void stop() {
      if (out != null) {
        try {
          out.writeChar('X');
          out.close();          
        } catch (IOException e) {
          // DO not let anything stop us exiting!
        }
      }
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

  @FXML
  public void initialize() {
      initSpinner();
      initDraw(mapCanvas.getGraphicsContext2D());
      initDraw(mapLineCanvas.getGraphicsContext2D());
      robotRotation = new Rotate();
      robotRotation.setPivotX(X_ROBOT_OFFSET);
      robotRotation.setPivotY(Y_ROBOT_OFFSET);
      robot.getTransforms().add(robotRotation);
      drawGridLinesAndSetBoundary(mapCanvas);
      mapLineCanvas.addEventFilter(MouseEvent.ANY, (e) -> mapLineCanvas.requestFocus());
  }
  
  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Initialisation mechanisms
  private void drawGridLinesAndSetBoundary(Canvas mapArea) {
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
      gContext.fillText(String.valueOf(coord), xPos, gridCanvas.getHeight() - 20);
      coord += 10;
    }
    coord = 10;
    for (double yPos = yStep; yPos < gridCanvas.getHeight() ; yPos += yStep) {
      double yAdjusted = gridCanvas.getHeight() - yPos;
      gContext.strokeLine(0, yAdjusted, gridCanvas.getWidth(), yAdjusted);      
      gContext.fillText(String.valueOf(coord), 20, yAdjusted);
      coord += 10;
    }
    localMap = new Map(
        gridCanvas.getWidth(), 
        gridCanvas.getHeight(), 
        journeyArrow.getStartX(), 
        journeyArrow.getStartY(), 
        robotRotation.getAngle(), 
        journeyArrow.getEndX(), 
        journeyArrow.getEndY());
  }
  
  private void initSpinner() {
    SpinnerValueFactory<Integer> valueFactory = //
        new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 254, 8);
    spinner.setValueFactory(valueFactory);
    spinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);    
  }

  private void initDraw(GraphicsContext gc){
    gc.setFill(Color.RED);
    gc.setStroke(Color.BLUE);
    gc.setLineWidth(1);
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Read the data that the EV3 sends in a Thread and update the JavaFX components in the JavaFX Thread
  private class ReaderThread extends Thread {
    private DataInputStream in;
    Path path = new Path();
    Pose pose = new Pose();
    public ReaderThread(DataInputStream in) {
      this.in = in;
    }

    public void run() {
      try {
        // We can read in car Pose or Planned Path - nothing else.
        while (true) {
          if (in.readChar() == COMMANDS.POSE.getCode())  { // Pose
            pose.loadObject(in);
            // System.out.println("Got (robot) Pose: " + pose.getX() + ", " + pose.getY() + ", " + pose.getHeading());
            Platform.runLater(new Runnable() {
              public void run() {
                double xPos = pose.getX() / xScale - X_ROBOT_OFFSET;
                double yPos = (mapCanvas.getHeight() - pose.getY() / yScale) - Y_ROBOT_OFFSET; 
                robot.relocate(xPos,  yPos);
                robotRotation.setAngle(-90 - pose.getHeading());
                localMap.setPose(pose); // set pose in localMap from JavaFX thread to avoid the need for synchronisation.
              }
            });
          } else {
            path.loadObject(in);
            // Convert path to two array for using multiline draw
            int count = path.size();
            double[] xList = new double[count];
            double[] yList = new double[count];
            for (int index = 0 ; index < count; index++) {
            //  System.out.println("Got(robot) Path (point): " + path.get(index).getX() + ", " + path.get(index).getY());

              xList[index] = path.get(index).getX() / xScale;
              yList[index] = mapCanvas.getHeight() - path.get(index).getY() / yScale;
            }
            Platform.runLater(new Runnable() {
              public void run() {
                GraphicsContext context = pathCanvas.getGraphicsContext2D();
                context.clearRect(0, 0, pathCanvas.getWidth(), pathCanvas.getHeight());
                context.save();
                context.setStroke(Color.LIGHTGREEN);
                context.strokePolyline(xList, yList, count);
                context.restore();
              }
            });
            
          }
        }
      } catch (IOException e) {
        return;
      }
    }
  }
  
  //////////////////////////////////////////////////////////////////////////////////
  // The current state of the program - including the Map and the robot and its journey.
  // All data stored in robot coordinates.
  private class Map {
    private boolean mapChanged = true;
    private boolean destinationChanged = true;
    private boolean poseChanged = true;
    private Stack<Line> lines = new Stack<>();
    private Rectangle boundary = null;
    private Waypoint destination = null;
    private Pose pose = null;

    private float horizToEV3(double x) {
      return (float) (x * xScale);
    }
    private float vertToEV3(double y) {
      return (float) ((mapCanvas.getHeight() - y) * yScale);
    }
    private float angleToEV3(double angle) {
      angle -= 90;
      while (angle > 180) {
        angle -= 360;
      }
      while (angle < -180) {
        angle += 360;
      }
      return (float) angle;
    }
    
    public Map(double width, double height, double x, double y, double angle, double rDX, double rDy) {
      boundary = new Rectangle(0f, 0f, (float) (width * xScale), (float) (height * yScale));
      pose = new Pose(horizToEV3(x), vertToEV3(y), angleToEV3(angle));
      destination = new Waypoint(horizToEV3(rDX), vertToEV3(rDy));
    }
    
    public Boolean addLine(double x1,double y1, double x2, double y2) {
      if (boundary.contains(horizToEV3(x1), vertToEV3(y1)) && boundary.contains(horizToEV3(x2), vertToEV3(y2))) {
        Line nLine = new Line(horizToEV3(x1), vertToEV3(y1), horizToEV3(x2), vertToEV3(y2));
        lines.push(nLine);
        mapChanged = true;
        return true;
      }
      return false;
    }
    public boolean setDestination(double x1, double y1) {
      if (!boundary.contains(horizToEV3(x1), vertToEV3(y1))) {
        return false;
      }
      destination = new Waypoint(horizToEV3(x1), vertToEV3(y1));
      destinationChanged = true;
      return true;
    }

    public Boolean setBoundary(double x1, double y1, double w, double h) {
      Rectangle nBoundary = new Rectangle(horizToEV3(x1), vertToEV3(y1), (float) (w * xScale), (float) (h * yScale));
      if (!nBoundary.contains(pose.getLocation()) || !nBoundary.contains(destination)) {
        return false;
      }
      for (Line l: lines) {
        if (!nBoundary.contains(l.getP1()) || !nBoundary.contains(l.getP2())) {
          return false;
        }
      }
      boundary = nBoundary;
      mapChanged = true;
      return true;
    }
    
    public void setPose(Pose poseFromEV3) {
      pose = poseFromEV3;
      poseChanged = false;
    }
    
    public boolean setPose(double x1, double y1, double angle) {
      if (!boundary.contains(horizToEV3(x1), vertToEV3(y1))) {
        return false;
      }
      pose = new Pose(horizToEV3(x1), vertToEV3(y1), angleToEV3(angle));
      poseChanged = true;
      return true;
    }

    public Pose getPose() { // Does not need checking for boundary - got from EV3!
      if (!poseChanged) {
        return null;
      }
      poseChanged = false;
      return pose;
    }
    
    public LineMap getLineMap() {
      if (!mapChanged) {
        return null;
      }
      mapChanged = false;
      return new LineMap(lines.toArray(new Line[lines.size()]), boundary);
    }
    
    public Waypoint getDestination() {
      if (!destinationChanged) {
        return null;
      }
      destinationChanged = false;
      return destination;
    }
    
    public void drawMap() {
      GraphicsContext baseContext = mapCanvas.getGraphicsContext2D();      
      baseContext.clearRect(0, 0, mapCanvas.getWidth(), mapCanvas.getHeight());
      if (boundary != null) {
        baseContext.strokeRect(
          boundary.getX() / xScale, mapCanvas.getHeight() - boundary.getY() / yScale, 
          boundary.getWidth() / xScale, boundary.getHeight() / yScale);
      }
      for (Line l: lines) {
        // draw is screen coordinates
        baseContext.strokeLine(l.getX1() / xScale, mapCanvas.getHeight() - l.getY1() / yScale, l.getX2() / xScale, mapCanvas.getHeight() - l.getY2() / yScale);
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