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
 * Handles one connected client on its own thread over the TLS-secured socket.
 *
 * Lifecycle (Level 3):
 *   1. Authenticate or register the user (LOGIN or REGISTER + username + password)
 *      over the TLS stream using PBKDF2-hashed credentials in {@link AuthManager}.
 *   2. Enforce failed-attempt limits and account lockouts before granting access;
 *      unauthenticated clients are never added to {@link ClientRegistry}.
 *   3. Relay validated chat messages and commands (/users, /help, /quit) until
 *      the client leaves or disconnects, cleaning up in the finally block.
 */
public class ClientHandler implements Runnable {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_FAILED_ATTEMPTS = 3;

    private final Socket socket;
    private final ClientRegistry registry;
    private final AuthManager authManager;
    private final String remote;

    private PrintWriter out;
    private String username;

    public ClientHandler(Socket socket, ClientRegistry registry, AuthManager authManager) {
        this.socket = socket;
        this.registry = registry;
        this.authManager = authManager;
        this.remote = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    @Override
    public void run() {
        boolean joined = false;
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), Protocol.CHARSET))) {

            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), Protocol.CHARSET), true);

            joined = authenticateClient(in);
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

    /**
     * Authenticates or registers a user before allowing access to the chat room.
     * Enforces a per-session attempt cap and delegates password verification/lockout
     * rules to {@link AuthManager}.
     */
    private boolean authenticateClient(BufferedReader in) throws IOException {
        int failedAttempts = 0;

        while (failedAttempts < MAX_FAILED_ATTEMPTS) {
            send(Protocol.AUTH_MODE_PROMPT);
            String rawMode = in.readLine();
            if (rawMode == null) {
                return false;
            }
            String mode = Protocol.sanitiseLine(rawMode).toUpperCase();
            if (!Protocol.MODE_LOGIN.equals(mode) && !Protocol.MODE_REGISTER.equals(mode)) {
                failedAttempts++;
                send(Protocol.REJECTED_PREFIX + "Invalid mode. Use "
                        + Protocol.MODE_LOGIN + " or " + Protocol.MODE_REGISTER + ".");
                continue;
            }

            send(Protocol.USERNAME_PROMPT);
            String rawCandidate = in.readLine();
            if (rawCandidate == null) {
                return false;
            }
            String candidate = Protocol.sanitiseLine(rawCandidate);

            String userProblem = Protocol.validateUsername(candidate);
            if (userProblem != null) {
                failedAttempts++;
                send(Protocol.REJECTED_PREFIX + userProblem);
                continue;
            }

            send(Protocol.PASSWORD_PROMPT);
            String password = in.readLine();
            if (password == null) {
                return false;
            }

            // Strip line-ending control characters without altering intentional spaces/symbols
            String cleanPassword = password.replaceAll("[\\r\\n]", "");

            if (Protocol.MODE_REGISTER.equals(mode)) {
                String passProblem = Protocol.validatePassword(cleanPassword);
                if (passProblem != null) {
                    failedAttempts++;
                    send(Protocol.REJECTED_PREFIX + passProblem);
                    continue;
                }

                try {
                    if (!authManager.registerUser(candidate, cleanPassword)) {
                        failedAttempts++;
                        ChatServer.log("AUTH_FAIL REGISTER " + candidate + " from " + remote);
                        send(Protocol.REJECTED_PREFIX + "Registration failed for '" + candidate + "'.");
                        continue;
                    }
                } catch (IOException e) {
                    ChatServer.log("AUTH_ERR storage failure for " + candidate + ": " + e.getMessage());
                    send(Protocol.REJECTED_PREFIX + "Internal error saving credentials.");
                    return false;
                }

                if (!registry.register(candidate, this)) {
                    send(Protocol.REJECTED_PREFIX + "Account created, but user is already active.");
                    return false;
                }

                this.username = candidate;
                ChatServer.log("AUTH_OK REGISTER " + username + " from " + remote);
                send(Protocol.ACCEPTED_PREFIX + username);
                return true;
            } else {
                AuthManager.AuthResult result = authManager.authenticate(candidate, cleanPassword);
                switch (result) {
                    case SUCCESS -> {
                        if (!registry.register(candidate, this)) {
                            failedAttempts++;
                            send(Protocol.REJECTED_PREFIX + "User '" + candidate + "' is already logged in.");
                            continue;
                        }
                        this.username = candidate;
                        ChatServer.log("AUTH_OK LOGIN " + username + " from " + remote);
                        send(Protocol.ACCEPTED_PREFIX + username);
                        return true;
                    }
                    case ACCOUNT_LOCKED -> {
                        ChatServer.log("AUTH_LOCKED " + candidate + " from " + remote);
                        send(Protocol.REJECTED_PREFIX + "Account temporarily locked. Try again later.");
                        return false;
                    }
                    case INVALID_CREDENTIALS -> {
                        failedAttempts++;
                        ChatServer.log("AUTH_FAIL LOGIN " + candidate + " from " + remote
                                + " (attempt " + failedAttempts + "/" + MAX_FAILED_ATTEMPTS + ")");
                        send(Protocol.REJECTED_PREFIX + "Invalid username or password.");
                    }
                }
            }
        }

        ChatServer.log("AUTH_DENY max attempts reached from " + remote);
        send(Protocol.REJECTED_PREFIX + "Too many failed authentication attempts. Disconnecting.");
        return false;
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
