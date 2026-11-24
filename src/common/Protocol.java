package common;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Shared constants and validation rules for the chat protocol.
 *
 * The protocol is line-based text over TLS (UTF-8, one message per line):
 *
 *   1. Client connects over TLS and negotiates login or registration:
 *        - Server may send AUTH_MODE_PROMPT ("SELECT_MODE") or USERNAME_PROMPT
 *          ("SUBMIT_USERNAME") followed by PASSWORD_PROMPT ("SUBMIT_PASSWORD").
 *        - For registration, client sends mode ("REGISTER" / "LOGIN" or equivalent
 *          handled by the server/client flow), username, and password over the
 *          encrypted TLS stream.
 *   2. Server replies "OK <name>" if authenticated, or "ERR <reason>" and allows
 *      another attempt up to the server's attempt/lockout limit.
 *   3. After joining, each line the client sends is either a chat message
 *      (broadcast to everyone as "[time] name: text") or a command such as
 *      /users, /help, or /quit.
 *
 * Keeping the protocol in one class means server and client can never
 * disagree on the rules, and later levels extend it in one place.
 */
public final class Protocol {

    public static final int DEFAULT_PORT = 8888;
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    public static final int MAX_USERNAME_LENGTH = 20;
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;
    public static final int MAX_MESSAGE_LENGTH = 500;

    // Handshake & authentication prompts (server -> client)
    public static final String AUTH_MODE_PROMPT = "SELECT_MODE";
    public static final String USERNAME_PROMPT = "SUBMIT_USERNAME";
    public static final String PASSWORD_PROMPT = "SUBMIT_PASSWORD";
    public static final String ACCEPTED_PREFIX = "OK ";
    public static final String REJECTED_PREFIX = "ERR ";

    // Authentication modes (client -> server)
    public static final String MODE_LOGIN = "LOGIN";
    public static final String MODE_REGISTER = "REGISTER";

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
     * Returns null if the password meets basic strength and length requirements,
     * otherwise a short reason that is sent back to the client.
     */
    public static String validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return "Password cannot be empty.";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return "Password must be at most " + MAX_PASSWORD_LENGTH + " characters.";
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigitOrSymbol = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        if (!hasLetter || !hasDigitOrSymbol) {
            return "Password must include at least one letter and one number or symbol.";
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

    /**
     * Sanitises single-line protocol tokens such as mode selection so control
     * characters cannot inject extra protocol lines.
     */
    public static String sanitiseLine(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\p{Cntrl}", "").strip();
    }
}
