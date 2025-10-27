package utb.fai;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveHandlers {
    private ConcurrentHashMap<String, SocketHandler> activeHandlersMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, HashSet<SocketHandler>> rooms = new ConcurrentHashMap<>();

    synchronized void sendMessageToAll(SocketHandler sender, String message) {
        if (sender.userName == null || sender.userName.isEmpty()) {
            System.err.println("DBG>Cannot send message - user has no name set");
            return;
        }

        String formattedMessage = "[" + sender.userName + "] >> " + message;
        
        System.err.println("DBG>Sending message from " + sender.userName + " in rooms: " + sender.userRooms);
        
        for (String room : sender.userRooms) {
            HashSet<SocketHandler> roomMembers = rooms.get(room);
            if (roomMembers != null) {
                System.err.println("DBG>Room " + room + " has " + roomMembers.size() + " members");
                for (SocketHandler handler : roomMembers) {
                    if (handler != sender && handler.userName != null) {
                        System.err.println("DBG>Sending to " + handler.userName);
                        if (!handler.messages.offer(formattedMessage)) {
                            System.err.printf("Client %s message queue is full, dropping the message!\n", 
                                handler.clientID);
                        }
                    }
                }
            } else {
                System.err.println("DBG>Room " + room + " not found!");
            }
        }
    }

    synchronized void sendPrivateMessage(SocketHandler sender, String targetName, String message) {
        if (sender.userName == null || sender.userName.isEmpty()) {
            return;
        }

        SocketHandler target = activeHandlersMap.get(targetName);
        if (target == null) {
            return;
        }

        String formattedMessage = "[" + sender.userName + "] >> " + message;
        if (!target.messages.offer(formattedMessage)) {
            System.err.printf("Client %s message queue is full, dropping the message!\n", target.clientID);
        }
    }

    synchronized boolean setUserName(SocketHandler handler, String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            return false;
        }

        if (newName.contains(" ")) {
            return false;
        }

        if (activeHandlersMap.containsKey(newName) && !newName.equals(handler.userName)) {
            return false;
        }

        if (handler.userName != null) {
            activeHandlersMap.remove(handler.userName);
        }

        handler.userName = newName;
        activeHandlersMap.put(newName, handler);
        return true;
    }

    synchronized void joinRoom(SocketHandler handler, String roomName) {
        if (handler.userName == null || handler.userName.isEmpty()) {
            return;
        }

        if (roomName == null || roomName.trim().isEmpty()) {
            return;
        }

        rooms.putIfAbsent(roomName, new HashSet<>());
        
        HashSet<SocketHandler> roomMembers = rooms.get(roomName);
        roomMembers.add(handler);
        handler.userRooms.add(roomName);
    }

    synchronized void leaveRoom(SocketHandler handler, String roomName) {
        if (handler.userName == null || handler.userName.isEmpty()) {
            return;
        }

        if (!handler.userRooms.contains(roomName)) {
            return;
        }

        HashSet<SocketHandler> roomMembers = rooms.get(roomName);
        if (roomMembers != null) {
            roomMembers.remove(handler);
            handler.userRooms.remove(roomName);
            
            if (roomMembers.isEmpty()) {
                rooms.remove(roomName);
            }
        }
    }

    synchronized void listUserRooms(SocketHandler handler) {
        if (handler.userName == null || handler.userName.isEmpty()) {
            return;
        }

        if (handler.userRooms.isEmpty()) {
            handler.messages.offer("");
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