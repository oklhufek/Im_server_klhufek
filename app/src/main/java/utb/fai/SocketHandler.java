package utb.fai;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.HashSet;
import java.util.Set;

public class SocketHandler {
	Socket mySocket;
	String clientID;
	volatile String userName = null;
	Set<String> userRooms = new HashSet<>();
	
	ActiveHandlers activeHandlers;
	ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<String>(20);
	CountDownLatch startSignal = new CountDownLatch(2);
	
	OutputHandler outputHandler = new OutputHandler();
	InputHandler inputHandler = new InputHandler();
	volatile boolean inputFinished = false;

	public SocketHandler(Socket mySocket, ActiveHandlers activeHandlers) {
		this.mySocket = mySocket;
		clientID = mySocket.getInetAddress().toString() + ":" + mySocket.getPort();
		this.activeHandlers = activeHandlers;
	}

	class OutputHandler implements Runnable {
		public void run() {
			OutputStreamWriter writer;
			try {
				System.err.println("DBG>Output handler starting for " + clientID);
				startSignal.countDown();
				startSignal.await();
				System.err.println("DBG>Output handler running for " + clientID);
				
				writer = new OutputStreamWriter(mySocket.getOutputStream(), "UTF-8");
				
				while (!inputFinished) {
					String m = messages.take();
					writer.write(m + "\r\n");
					writer.flush();
					System.err.println("DBG>Message sent to " + clientID + ":" + m + "\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.err.println("DBG>Output handler for " + clientID + " has finished.");
		}
	}

	class InputHandler implements Runnable {
		public void run() {
			try {
				System.err.println("DBG>Input handler starting for " + clientID);
				startSignal.countDown();
				startSignal.await();
				System.err.println("DBG>Input handler running for " + clientID);
				
				activeHandlers.add(SocketHandler.this);
				
				BufferedReader reader = new BufferedReader(
					new InputStreamReader(mySocket.getInputStream(), "UTF-8"));
				
				String line;
				boolean nameSet = false;
				
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty()) continue;
					
					System.out.println("Received from " + clientID + ": " + line);
					
					if (!nameSet) {
						String candidate = null;
						if (line.startsWith("#setMyName")) {
							String[] parts = line.split("\\s+", 2);
							if (parts.length == 2) {
								candidate = parts[1].trim();
							}
						} else {
							candidate = line;
						}
						
						if (candidate != null && activeHandlers.setName(SocketHandler.this, candidate)) {
							nameSet = true;
						}
						continue;
					}
					
					if (line.startsWith("#")) {
						processCommand(line);
					} else {
						String formatted = "[" + userName + "] >> " + line;
						activeHandlers.broadcastToGroups(SocketHandler.this, formatted);
					}
				}
				
				inputFinished = true;
				messages.offer("OutputHandler, wakeup and die!");
				
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				synchronized (activeHandlers) {
					activeHandlers.remove(SocketHandler.this);
				}
			}
			System.err.println("DBG>Input handler for " + clientID + " has finished.");
		}
		
		private void processCommand(String line) {
			if (line.startsWith("#setMyName")) {
				String[] parts = line.split("\\s+", 2);
				if (parts.length >= 2) {
					activeHandlers.setName(SocketHandler.this, parts[1].trim());
				}
			} else if (line.startsWith("#sendPrivate")) {
				String[] parts = line.split("\\s+", 3);
				if (parts.length >= 3) {
					String targetName = parts[1].trim();
					String msg = parts[2];
					String formatted = "[" + userName + "] >> " + msg;
					activeHandlers.sendPrivate(targetName, formatted, SocketHandler.this);
				}
			} else if (line.startsWith("#join")) {
				String[] parts = line.split("\\s+", 2);
				if (parts.length >= 2) {
					String room = parts[1].trim();
					activeHandlers.joinGroup(room, SocketHandler.this);
				}
			} else if (line.startsWith("#leave")) {
				String[] parts = line.split("\\s+", 2);
				if (parts.length >= 2) {
					String room = parts[1].trim();
					activeHandlers.leaveGroup(room, SocketHandler.this);
				}
			} else if (line.equals("#groups")) {
				Set<String> groups = activeHandlers.groupsOf(SocketHandler.this);
				if (groups.isEmpty()) {
					messages.offer("");
				} else {
					messages.offer(String.join(",", groups));
				}
			}
		}
	}
}