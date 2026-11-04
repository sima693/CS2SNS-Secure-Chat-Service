package server;

import common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles one connected client on its own thread.
 *
 * Lifecycle: ask for a username until a valid, unused one is given, announce
 * the join, then relay every line the client sends to the whole group until
 * the client types /quit or the connection drops. However the connection
 * ends, the finally block removes the client and tells everyone else, so a
 * crashed or killed client can never take the server down with it.
 */
public class ClientHandler implements Runnable {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Socket socket;
    private final ClientRegistry registry;
    private final String remote;

    private PrintWriter out;
    private String username;

    public ClientHandler(Socket socket, ClientRegistry registry) {
        this.socket = socket;
        this.registry = registry;
        this.remote = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    @Override
    public void run() {
        boolean joined = false;
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), Protocol.CHARSET))) {

            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), Protocol.CHARSET), true);

            joined = negotiateUsername(in);
            if (!joined) {
                return;
            }

            ChatServer.log("JOIN  " + username + " from " + remote
                    + " (" + registry.size() + " online)");
            registry.broadcast(system(username + " joined the chat."));
            send(system("Welcome, " + username + "! Type " + Protocol.CMD_HELP + " for commands."));

            String line;
            while ((line = in.readLine()) != null) {
                if (!handleLine(line)) {
                    break;
                }
            }
        } catch (IOException e) {
            // Abrupt disconnect (closed window, lost network...). Handled below.
            ChatServer.log("DROP  " + (username != null ? username : remote) + ": " + e.getMessage());
        } finally {
            if (joined) {
                registry.unregister(username, this);
                registry.broadcast(system(username + " left the chat."));
                ChatServer.log("LEAVE " + username + " (" + registry.size() + " online)");
            }
            close();
        }
    }

    /** Keeps asking until the client picks a valid, unused name. */
    private boolean negotiateUsername(BufferedReader in) throws IOException {
        while (true) {
            send(Protocol.USERNAME_PROMPT);
            String candidate = in.readLine();
            if (candidate == null) {
                return false; // disconnected before joining
            }
            candidate = candidate.strip();

            String problem = Protocol.validateUsername(candidate);
            if (problem == null && !registry.register(candidate, this)) {
                problem = "Username '" + candidate + "' is already taken.";
            }
            if (problem == null) {
                username = candidate;
                send(Protocol.ACCEPTED_PREFIX + username);
                return true;
            }
            send(Protocol.REJECTED_PREFIX + problem);
        }
    }

    /** Processes one line from the client. Returns false when the client wants to leave. */
    private boolean handleLine(String raw) {
        String text = Protocol.sanitiseMessage(raw);
        if (text.isEmpty()) {
            return true;
        }

        if (text.startsWith("/")) {
            String command = text.toLowerCase();
            switch (command) {
                case Protocol.CMD_QUIT:
                    send(system("Goodbye!"));
                    return false;
                case Protocol.CMD_USERS:
                    send(system("Online (" + registry.size() + "): "
                            + String.join(", ", registry.usernames())));
                    return true;
                case Protocol.CMD_HELP:
                    send(system("Commands: " + Protocol.CMD_USERS + " list users, "
                            + Protocol.CMD_QUIT + " leave the chat."));
                    return true;
                default:
                    send(system("Unknown command '" + text + "'. Type " + Protocol.CMD_HELP + "."));
                    return true;
            }
        }

        // Identity comes from the server-side record, not from the message,
        // so a client cannot post under someone else's name.
        registry.broadcast("[" + LocalTime.now().format(TIME) + "] " + username + ": " + text);
        return true;
    }

    /**
     * Synchronised so two broadcasts from different threads can't interleave
     * their characters on this client's stream.
     */
    public synchronized void send(String line) {
        if (out != null) {
            out.println(line);
        }
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }

    public String getUsername() {
        return username;
    }

    private static String system(String text) {
        return "[" + LocalTime.now().format(TIME) + "] *** " + text;
    }
}
