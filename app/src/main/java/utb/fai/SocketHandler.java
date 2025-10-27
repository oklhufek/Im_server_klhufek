package utb.fai;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.HashSet;

public class SocketHandler {
	Socket mySocket;
	String clientID;
	String userName = null;
	HashSet<String> userRooms = new HashSet<>();
	
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
				
				String request = "";
				activeHandlers.add(SocketHandler.this);
				
				BufferedReader reader = new BufferedReader(
					new InputStreamReader(mySocket.getInputStream(), "UTF-8"));
				
				boolean firstMessage = true;
				
				while ((request = reader.readLine()) != null) {
					request = request.trim();
					
					if (request.isEmpty()) {
						continue;
					}
					
					System.out.println("Received from " + clientID + ": " + request);
					
					if (firstMessage && userName == null) {
						firstMessage = false;
						if (!request.startsWith("#")) {
							activeHandlers.setUserName(SocketHandler.this, request);
							continue;
						}
					}
					
					if (request.startsWith("#")) {
						processCommand(request);
					} else {
						activeHandlers.sendMessageToAll(SocketHandler.this, request);
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
		
		private void processCommand(String command) {
			String[] parts = command.split("\\s+", 3);
			String cmd = parts[0].toLowerCase();
			
			switch (cmd) {
				case "#setmyname":
					if (parts.length < 2) {
						messages.offer("Usage: #setMyName <n>");
					} else {
						activeHandlers.setUserName(SocketHandler.this, parts[1]);
					}
					break;
					
				case "#sendprivate":
					if (parts.length < 3) {
						messages.offer("Usage: #sendPrivate <n> <message>");
					} else {
						String targetName = parts[1];
						String privateMessage = parts[2];
						activeHandlers.sendPrivateMessage(SocketHandler.this, targetName, privateMessage);
					}
					break;
					
				case "#join":
					if (parts.length < 2) {
						messages.offer("Usage: #join <room>");
					} else {
						activeHandlers.joinRoom(SocketHandler.this, parts[1]);
					}
					break;
					
				case "#leave":
					if (parts.length < 2) {
						messages.offer("Usage: #leave <room>");
					} else {
						activeHandlers.leaveRoom(SocketHandler.this, parts[1]);
					}
					break;
					
				case "#groups":
					activeHandlers.listUserRooms(SocketHandler.this);
					break;
					
				default:
					messages.offer("Unknown command: " + cmd);
					break;
			}
		}
	}
}