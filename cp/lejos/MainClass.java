package cp.lejos;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import lejos.nxt.Button;
import lejos.nxt.LCD;
import lejos.nxt.comm.BTConnection;
import lejos.nxt.comm.Bluetooth;
import lejos.nxt.comm.NXTConnection;

public class MainClass {
	public static BTConnection connection = null;
	public static DataInputStream dis;
	public static DataOutputStream dos;
	private static int MAX_READ = 30;

	public static void main(String[] args) {
		byte[] buffer = new byte[MAX_READ];
		(new TunePlayer()).start();
		
		LCD.drawString("Waiting  ", 0, 0);
		MainClass.connection = Bluetooth.waitForConnection(0,  NXTConnection.RAW);
		LCD.drawString("Connected", 0, 0);
		
		while (!Button.ESCAPE.isDown()) {
			if (connection != null) {
				if (connection.available() > 0) {
					LCD.drawString("Chars read: ", 0, 2);
					LCD.drawInt(connection.available(), 12, 2);
					int read = connection.read(buffer, MAX_READ);
					LCD.drawChar('[', 3, 3);
					for (int index= 0 ; index < read ; index++) {						
						LCD.drawChar((char)buffer[index], index + 4, 3);
					}
					LCD.drawChar(']', read + 4, 3);
					connection.write("Reply:".getBytes(), 6);
					connection.write(buffer, read);
				}
			}
		}
	}
}
