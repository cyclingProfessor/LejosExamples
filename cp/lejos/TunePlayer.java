package cp.lejos;

import java.io.File;

import lejos.nxt.LCD;
import lejos.nxt.Sound;

public class TunePlayer extends Thread {
	public void run() {
		int count = 1;
		while (true) {
			LCD.drawInt(count++, 0, 6);
			playTune(); 
		}
	}
	private void playTune() {
		int time = Sound.playSample(new File("Trumpet.wav"));
		try {
			Thread.sleep(time);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
