import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Timer;
import java.util.TimerTask;
import lejos.hardware.Button;
import lejos.hardware.lcd.LCD;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.port.MotorPort;
import lejos.robotics.RegulatedMotor;
import lejos.robotics.chassis.Chassis;
import lejos.robotics.chassis.Wheel;
import lejos.robotics.chassis.WheeledChassis;
import lejos.robotics.geometry.Line;
import lejos.robotics.geometry.Rectangle;
import lejos.robotics.mapping.LineMap;
import lejos.robotics.navigation.DestinationUnreachableException;
import lejos.robotics.navigation.MovePilot;
import lejos.robotics.navigation.Navigator;
import lejos.robotics.navigation.Pose;
import lejos.robotics.navigation.Waypoint;
import lejos.robotics.pathfinding.Path;
import lejos.robotics.pathfinding.PathFinder;
import lejos.robotics.pathfinding.ShortestPathFinder;

public class EV3Mapper {
  private final static String BASE_IP = "10.0.1."; // Check this from the PC application.
  private final static int PORT = 2468; // Yu can choose any port, but it must be the same on the
  // server
  private static DataInputStream in = null;
  private static PrintWriter out = null;


  public static void main(String[] args) {
    LCD.drawString("Use up/down", 0, 0);
    LCD.drawString("to set IP", 2, 1);
    LCD.drawString("ENTER to finish", 2, 2);

    int ip_addr = 2;
    LCD.drawString("IP " + BASE_IP + ip_addr + "   ", 0, 4);
    int id = Button.waitForAnyPress();
    while (id != Button.ID_ENTER) {
      switch (id) {
        case Button.ID_UP:
          ip_addr++;
          break;
        case Button.ID_DOWN:
          ip_addr--;
          break;
      }
      ip_addr = Math.min(254, ip_addr);
      ip_addr = Math.max(1, ip_addr);
      LCD.drawString("IP " + BASE_IP + ip_addr + "   ", 0, 4);
      id = Button.waitForAnyPress();
    }

    LCD.clear();
    LCD.drawString("Server::" + BASE_IP + ip_addr + "   ", 0, 0);
    LCD.drawString("Connecting ...", 0, 1);
    SocketAddress sa = new InetSocketAddress(BASE_IP + ip_addr, PORT);
    Socket connection = null;
    try {
      connection = new Socket();
      DataInputStream dis;
      DataOutputStream dos;

      connection.connect(sa, 1500); // Timeout possible
      in = new DataInputStream(connection.getInputStream());
      out = new PrintWriter(connection.getOutputStream());
      LCD.drawString("Connected to Server", 0, 1);

      startPoseSenderThread(1000, out);
    } catch (Exception ex) {
      // Could be Timeout or just a normal IO exception
      LCD.drawString(ex.getMessage(), 0, 6);
      connection = null;
    }

    LineMap map = null;
    Navigator navigator = getNavigator();
    PathFinder pf = null;
    final int MAP = 'M';
    final int EXIT = 'X';
    final int GOTO = 'G';
    final int STOP = 'S';
    final int CLOSED = -1;

    while (connection != null) {
      try {
        int command = in.read();
        LCD.clear(3);
        switch (command) {
          default:
            System.err.println("Got an unexpected character: " + command);
            break;
          case CLOSED:
            LCD.drawString("Remote close", 0, 3);
            connection = null;
          case MAP:
            LCD.drawString("(M)AP", 0, 3);
            map = readMap(in);
            if (map != null) {
              pf = new ShortestPathFinder(map);
            }
            break;
          case EXIT:
            LCD.drawString("E(X)IT", 0, 3);
            connection = null;
            break;
          case GOTO:
            LCD.drawString("(G)OTO", 0, 3);
            if (pf != null) {
              navigator.stop();
              try {
                Path route = pf.findRoute(new Pose(0, 0, 0), new Waypoint(0, 100));
                navigator.followPath(route);
              } catch (DestinationUnreachableException e) {
                LCD.drawString("POSE UNREACHABLE", 0, 3);
              }
            }
            break;
          case STOP:
            navigator.stop();
            LCD.drawString("(S)TOP", 0, 3);
            break;
        }
      } catch (IOException e) {
        // Just end the program if we get a broken file read
        connection = null;
      }
    }
    LCD.clear();
    LCD.drawString("Exiting - press ENTER", 0, 5);
    Button.ENTER.waitForPressAndRelease();
  }

  private static LineMap readMap(DataInputStream in) {
    try {
      int width = in.readShort();
      int height = in.readShort();
      Rectangle bounds = new Rectangle(0, 0, width, height);
      int lineCount = in.readShort();
      Line[] lines = new Line[lineCount];
      for (int index = 0 ; index < lineCount ; index++) {
        int start_x = in.readShort();
        int start_y = in.readShort();
        int end_x = in.readShort();
        int end_y = in.readShort();
        lines[index] = new Line(start_x, start_y, end_x, end_y);
      }
      return new LineMap(lines, bounds);
    } catch (IOException e) {
      return null;
    }
  }

  private static Navigator getNavigator() {
    RegulatedMotor left = new EV3LargeRegulatedMotor(MotorPort.A);
    RegulatedMotor right = new EV3LargeRegulatedMotor(MotorPort.B);
    Wheel wheelLeft = WheeledChassis.modelWheel(left, 60).offset(-29);
    Wheel wheelRight = WheeledChassis.modelWheel(right, 60).offset(29);
    Chassis chassis =
        new WheeledChassis(new Wheel[] {wheelRight, wheelLeft}, WheeledChassis.TYPE_DIFFERENTIAL);
    MovePilot robot = new MovePilot(chassis);
    return new Navigator(robot); // Use default OdometryPoseProvider
  }

  // Send the Pose message to the server every "period" milliseconds
  private static void startPoseSenderThread(int period, PrintWriter server) {
    Timer sender = new Timer(true); // make sure to always set Timer to use Daemon thread.
    sender.schedule(new SenderTask(server), 0, period);
  }

  private static class SenderTask extends TimerTask {
    private PrintWriter server;

    public SenderTask(PrintWriter server) {
      this.server = server;
    }

    public void run() {
      server.println("hello");
      server.flush();
    }
  }
}
