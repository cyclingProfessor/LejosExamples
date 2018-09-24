package cp.lejos;
import lejos.hardware.Button;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.port.MotorPort;
import lejos.robotics.RegulatedMotor;
import lejos.robotics.chassis.Chassis;
import lejos.robotics.chassis.Wheel;
import lejos.robotics.chassis.WheeledChassis;
import lejos.robotics.geometry.Line;
import lejos.robotics.geometry.Rectangle;
import lejos.robotics.localization.OdometryPoseProvider;
import lejos.robotics.localization.PoseProvider;
import lejos.robotics.mapping.LineMap;
import lejos.robotics.navigation.MovePilot;
import lejos.robotics.navigation.Navigator;
import lejos.robotics.navigation.Pose;
import lejos.robotics.navigation.Waypoint;
import lejos.robotics.pathfinding.Path;
import lejos.robotics.pathfinding.PathFinder;
import lejos.robotics.pathfinding.ShortestPathFinder;

/**
 * Modified from the sample code at Lejos.org written by BB
 *
 */
public class PathFinding {

	public static void main(String[] args) throws Exception {
		RegulatedMotor left = new EV3LargeRegulatedMotor(MotorPort.A);
		RegulatedMotor right = new EV3LargeRegulatedMotor(MotorPort.B);
		Wheel wheelLeft = WheeledChassis.modelWheel(left, 60).offset(-29);
		Wheel wheelRight = WheeledChassis.modelWheel(right, 60).offset(29);
		Chassis chassis = new WheeledChassis(new Wheel[]{wheelRight, wheelLeft}, WheeledChassis.TYPE_DIFFERENTIAL); 
		
		MovePilot robot = new MovePilot(chassis);
		PoseProvider posep = new OdometryPoseProvider(robot);
		
		// Create a rudimentary map:
		Line [] lines = new Line[4];
		lines [0] = new Line(-20f, 20f, 100f, 20f);
		lines [1] = new Line(-20f, 40f, 20f, 40f);
		lines [2] = new Line(-20f, 60f, 20f, 60f);
		lines [3] = new Line(-20f, 80f, 20f, 80f);
		Rectangle bounds = new Rectangle(-50, -50, 250, 250);
		LineMap myMap = new LineMap(lines, bounds);
		
		PathFinder pf = new ShortestPathFinder(myMap);
		
		Navigator nav = new Navigator(robot, posep) ;
		System.out.println("Planning path...");
		Path route = pf.findRoute(new Pose(0,0,0), new Waypoint(0, 100));
		System.out.println("Planned path...");
		System.out.println(route.toString());
		Button.ENTER.waitForPressAndRelease();
		nav.followPath(route);
		nav.waitForStop();
	}		
}
