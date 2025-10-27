package utb.fai;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveHandlers {
    private ConcurrentHashMap<String, SocketHandler> activeHandlersMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, HashSet<SocketHandler>> rooms = new ConcurrentHashMap<>();

    synchronized void broadcastToGroups(SocketHandler sender, String message) {
        if (sender.userName == null || sender.userName.isEmpty()) {
            return;
        }

        HashSet<SocketHandler> recipients = new HashSet<>();
        for (String room : sender.userRooms) {
            HashSet<SocketHandler> roomMembers = rooms.get(room);
            if (roomMembers != null) {
                recipients.addAll(roomMembers);
            }
        }
        
        recipients.remove(sender);
        
        for (SocketHandler handler : recipients) {
            if (handler.userName != null) {
                if (!handler.messages.offer(message)) {
                    System.err.printf("Client %s message queue is full, dropping the message!\n", 
                        handler.clientID);
                }
            }
        }
    }

    synchronized boolean sendPrivate(String targetName, String message, SocketHandler sender) {
        if (sender.userName == null || sender.userName.isEmpty()) {
            return false;
        }

        SocketHandler target = activeHandlersMap.get(targetName);
        if (target == null) {
            return false;
        }

        if (!target.messages.offer(message)) {
            System.err.printf("Client %s message queue is full, dropping the message!\n", target.clientID);
            return false;
        }
        return true;
    }

    synchronized boolean setName(SocketHandler handler, String newName) {
        if (newName == null || newName.trim().isEmpty() || newName.contains(" ")) {
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

    synchronized void joinGroup(String roomName, SocketHandler handler) {
        if (roomName == null || roomName.trim().isEmpty()) {
            return;
        }

        rooms.putIfAbsent(roomName, new HashSet<>());
        HashSet<SocketHandler> roomMembers = rooms.get(roomName);
        roomMembers.add(handler);
        handler.userRooms.add(roomName);
    }

    synchronized void leaveGroup(String roomName, SocketHandler handler) {
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

    synchronized Set<String> groupsOf(SocketHandler handler) {
        return new LinkedHashSet<>(handler.userRooms);
    }

    synchronized boolean add(SocketHandler handler) {
        joinGroup("public", handler);
        return true;
    }

    synchronized boolean remove(SocketHandler handler) {
        for (String room : new HashSet<>(handler.userRooms)) {
            leaveGroup(room, handler);
        }
        
        if (handler.userName != null) {
            activeHandlersMap.remove(handler.userName);
        }
        
        return true;
    }
}