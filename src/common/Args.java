package common;

/** Tiny helper for "--name value" command-line options. */
public final class Args {

    private Args() {
    }

    public static String get(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    public static int getInt(String[] args, String name, int fallback) {
        String value = get(args, name, null);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > 65535) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a port number between 1 and 65535.");
        }
    }
}
