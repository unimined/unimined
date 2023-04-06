package xyz.wagyourtail.unimined.jarmodagent;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

public class ArgsReader extends StringReader {
    String nextArg = null;

    public ArgsReader(String args) {
        super(args);
    }

    public Arg readArg() throws IOException {
        String key = readNextArg();
        String value = readNextArg();
        if (value != null && value.startsWith("-")) {
            nextArg = value;
            value = null;
        }
        if (key == null)
            return null;
        if (key.contains("=")) {
            String[] split = key.split("=");
            key = split[0];
            if (value != null) {
                nextArg = value;
            }
            value = split[1];
        }
        while (key.startsWith("-")) {
            key = key.substring(1);
        }
        return new Arg(key, value);
    }

    public Map<String, String> readAll() {
        Map<String, String> args = new HashMap<>();
        try {
            Arg arg;
            while ((arg = readArg()) != null) {
                args.put(arg.key, arg.value);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return args;
    }

    private String readNextArg() throws IOException {
        if (nextArg != null) {
            String arg = nextArg;
            nextArg = null;
            return arg;
        }
        StringBuilder arg = new StringBuilder();
        int c;
        boolean inQuotes = false;
        boolean escaped = false;
        while ((c = read()) != -1) {
            if (c == '\\') {
                if (!escaped) {
                    escaped = true;
                    continue;
                }
            }
            if (c == ' ' && arg.length() > 0 && !(inQuotes || escaped)) {
                return arg.toString();
            }
            if (c == '"') {
                //TODO: fix bug where " can be in middle of arg
                inQuotes = !inQuotes;
                continue;
            }
            arg.append((char) c);
            escaped = false;
        }
        if (arg.length() > 0)
            return arg.toString();
        return null;
    }

    public static class Arg {
        public final String key;
        public final String value;

        public Arg(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
