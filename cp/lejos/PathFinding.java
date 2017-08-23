package cp.lejos;
import lejos.geom.Line;
import lejos.geom.Rectangle;
import lejos.nxt.Button;
import lejos.nxt.Motor;
import lejos.robotics.localization.OdometryPoseProvider;
import lejos.robotics.localization.PoseProvider;
import lejos.robotics.mapping.LineMap;
import lejos.robotics.navigation.DifferentialPilot;
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
		DifferentialPilot robot = new DifferentialPilot(58,60,Motor.A,Motor.B,false);
		PoseProvider posep = new OdometryPoseProvider(robot);
		
		// Create a rudimentary map:
		Line [] lines = new Line[4];
		lines [0] = new Line(-20f, 20f, 100f, 20f);
		lines [1] = new Line(-20f, 40f, 20f, 40f);
		lines [2] = new Line(-20f, 60f, 20f, 60f);
		lines [3] = new Line(-20f, 80f, 20f, 80f);
		lejos.geom.Rectangle bounds = new Rectangle(-50, -50, 250, 250);
		LineMap myMap = new LineMap(lines, bounds);
		
		PathFinder pf = new ShortestPathFinder(myMap);
		
		Navigator nav = new Navigator(robot, posep) ;
		System.out.println("Planning path...");
		Path route = pf.findRoute(new Pose(0,0,0), new Waypoint(0, 100));
		System.out.println("Planned path...");
		System.out.println(route.toString());
		Button.ENTER.waitForPressAndRelease();
		nav.addWaypoint(0, 500);
		nav.followPath();
		nav.waitForStop();
	}		
}
