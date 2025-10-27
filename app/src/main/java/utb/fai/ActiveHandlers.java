package utb.fai;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveHandlers {
    private ConcurrentHashMap<String, SocketHandler> activeHandlersMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, HashSet<SocketHandler>> rooms = new ConcurrentHashMap<>();

    synchronized void sendMessageToAll(SocketHandler sender, String message) {
        if (sender.userName == null || sender.userName.isEmpty()) {
            sender.messages.offer("Please set your name first using: #setMyName <n>");
            return;
        }

        String formattedMessage = "[" + sender.userName + "] >> " + message;
        
        for (String room : sender.userRooms) {
            HashSet<SocketHandler> roomMembers = rooms.get(room);
            if (roomMembers != null) {
                for (SocketHandler handler : roomMembers) {
                    if (handler != sender && handler.userName != null) {
                        if (!handler.messages.offer(formattedMessage)) {
                            System.err.printf("Client %s message queue is full, dropping the message!\n", 
                                handler.clientID);
                        }
                    }
                }
            }
        }
    }

    synchronized void sendPrivateMessage(SocketHandler sender, String targetName, String message) {
        if (sender.userName == null || sender.userName.isEmpty()) {
            sender.messages.offer("Please set your name first using: #setMyName <n>");
            return;
        }

        SocketHandler target = activeHandlersMap.get(targetName);
        if (target == null) {
            sender.messages.offer("User '" + targetName + "' not found or offline.");
            return;
        }

        String formattedMessage = "[PRIVATE from " + sender.userName + "] >> " + message;
        if (!target.messages.offer(formattedMessage)) {
            System.err.printf("Client %s message queue is full, dropping the message!\n", target.clientID);
            sender.messages.offer("Failed to send private message to " + targetName);
        }
    }

    synchronized boolean setUserName(SocketHandler handler, String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            handler.messages.offer("Name cannot be empty!");
            return false;
        }

        if (newName.contains(" ")) {
            handler.messages.offer("Name cannot contain spaces!");
            return false;
        }

        if (activeHandlersMap.containsKey(newName) && !newName.equals(handler.userName)) {
            handler.messages.offer("Name '" + newName + "' is already taken!");
            return false;
        }

        if (handler.userName != null) {
            activeHandlersMap.remove(handler.userName);
        }

        handler.userName = newName;
        activeHandlersMap.put(newName, handler);
        handler.messages.offer("Your name has been set to: " + newName);
        return true;
    }

    synchronized void joinRoom(SocketHandler handler, String roomName) {
        if (handler.userName == null || handler.userName.isEmpty()) {
            handler.messages.offer("Please set your name first using: #setMyName <n>");
            return;
        }

        if (roomName == null || roomName.trim().isEmpty()) {
            handler.messages.offer("Room name cannot be empty!");
            return;
        }

        rooms.putIfAbsent(roomName, new HashSet<>());
        
        HashSet<SocketHandler> roomMembers = rooms.get(roomName);
        if (roomMembers.add(handler)) {
            handler.userRooms.add(roomName);
            handler.messages.offer("You joined room: " + roomName);
        } else {
            handler.messages.offer("You are already in room: " + roomName);
        }
    }

    synchronized void leaveRoom(SocketHandler handler, String roomName) {
        if (handler.userName == null || handler.userName.isEmpty()) {
            handler.messages.offer("Please set your name first using: #setMyName <n>");
            return;
        }

        if (!handler.userRooms.contains(roomName)) {
            handler.messages.offer("You are not in room: " + roomName);
            return;
        }

        HashSet<SocketHandler> roomMembers = rooms.get(roomName);
        if (roomMembers != null) {
            roomMembers.remove(handler);
            handler.userRooms.remove(roomName);
            
            if (roomMembers.isEmpty()) {
                rooms.remove(roomName);
            }
            
            handler.messages.offer("You left room: " + roomName);
        }
    }

    synchronized void listUserRooms(SocketHandler handler) {
        if (handler.userName == null || handler.userName.isEmpty()) {
            handler.messages.offer("Please set your name first using: #setMyName <n>");
            return;
        }

        if (handler.userRooms.isEmpty()) {
            handler.messages.offer("You are not in any rooms.");
        } else {
            String roomList = String.join(",", handler.userRooms);
            handler.messages.offer(roomList);
        }
    }

    synchronized boolean add(SocketHandler handler) {
        joinRoom(handler, "public");
        return true;
    }

    synchronized boolean remove(SocketHandler handler) {
        for (String room : new HashSet<>(handler.userRooms)) {
            leaveRoom(handler, room);
        }
        
        if (handler.userName != null) {
            activeHandlersMap.remove(handler.userName);
        }
        
        return true;
    }
}