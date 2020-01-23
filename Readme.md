# Lejos EV3 Example Programs:

* EV3 requires the code to be compiled at Java version 7 or below (1.7)
* You can download a program to the EV3 from the command line. 
 1. Compile the code. 
 2. Add it a runnable jar file with a manifest.
 3. scp it over to the EV3 at root@10.0.1.1:/home/lejos/programs.
 
See: https://sourceforge.net/p/lejos/wiki/Developing%20with%20leJOS%20-%20alpha%20version/?version=4

## ClapFilter.java  

The clap filter as shown in Worksheet Three. 

## TunePlayer.java

This class creates a Thread to play a tune and counts up on the screen the number of times its been played.

You must download the Trumpet.wav file to your brick to make the music player work.

It is not a standalone program, but can be run inside any other program, for example MainClass.java
## PathFinding.java

This program is a working Navigation example.

## MainClass.java

This program waits for a BlueTooth or USB connection.  It then prints whatever arrives over this connection and also echoes it back to the sender.

It works well with the EV3Sensors Android program, but any simple network connection program (like telnet) will work from a PC as well.

It relies on the TUnePlayer Thread since it (annoyingly) also plays music while it is chatting over the network.

