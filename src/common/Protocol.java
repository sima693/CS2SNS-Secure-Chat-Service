package common;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Shared constants and validation rules for the chat protocol.
 *
 * The protocol is line-based text over TCP (UTF-8, one message per line):
 *
 *   1. Client connects and the server sends a USERNAME_PROMPT line.
 *   2. Client sends a candidate username.
 *   3. Server replies "OK <name>" if accepted, or "ERR <reason>" and waits for
 *      another attempt.
 *   4. After joining, each line the client sends is either a chat message
 *      (broadcast to everyone as "[time] name: text") or a command such as
 *      /users or /quit.
 *
 * Keeping the protocol in one class means server and client can never
 * disagree on the rules, and later levels (TLS, the Level 3 control) can
 * extend it in one place.
 */
public final class Protocol {

    public static final int DEFAULT_PORT = 8888;
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    public static final int MAX_USERNAME_LENGTH = 20;
    public static final int MAX_MESSAGE_LENGTH = 500;

    // Handshake lines (server -> client)
    public static final String USERNAME_PROMPT = "SUBMIT_USERNAME";
    public static final String ACCEPTED_PREFIX = "OK ";
    public static final String REJECTED_PREFIX = "ERR ";

    // Commands (client -> server)
    public static final String CMD_QUIT = "/quit";
    public static final String CMD_USERS = "/users";
    public static final String CMD_HELP = "/help";

    private Protocol() {
    }

    /**
     * Returns null if the username is acceptable, otherwise a short reason
     * that is sent back to the client.
     */
    public static String validateUsername(String name) {
        if (name == null || name.isBlank()) {
            return "Username cannot be empty.";
        }
        if (name.length() > MAX_USERNAME_LENGTH) {
            return "Username must be at most " + MAX_USERNAME_LENGTH + " characters.";
        }
        if (!name.matches("[A-Za-z0-9_-]+")) {
            return "Username may only contain letters, digits, '_' and '-'.";
        }
        if (name.equalsIgnoreCase("server")) {
            return "That username is reserved.";
        }
        return null;
    }

    /**
     * Removes control characters (which could fake new lines or mess with
     * other users' terminals) and trims the message to the maximum length.
     */
    public static String sanitiseMessage(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replaceAll("\\p{Cntrl}", "").strip();
        if (cleaned.length() > MAX_MESSAGE_LENGTH) {
            cleaned = cleaned.substring(0, MAX_MESSAGE_LENGTH);
        }
        return cleaned;
    }
}
