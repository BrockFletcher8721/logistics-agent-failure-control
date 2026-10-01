package dev.infrai.logistics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {}

    public static Object parse(String source) {
        Parser parser = new Parser(source);
        Object value = parser.value();
        parser.space();
        if (!parser.end()) throw new IllegalArgumentException("Unexpected JSON suffix");
        return value;
    }

    public static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder output = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) output.append(',');
                first = false;
                output.append(quote(String.valueOf(entry.getKey()))).append(':')
                        .append(stringify(entry.getValue()));
            }
            return output.append('}').toString();
        }
        if (value instanceof Iterable<?> items) {
            StringBuilder output = new StringBuilder("[");
            boolean first = true;
            for (Object item : items) {
                if (!first) output.append(',');
                first = false;
                output.append(stringify(item));
            }
            return output.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
    }

    private static String quote(String text) {
        StringBuilder output = new StringBuilder("\"");
        for (char character : text.toCharArray()) {
            switch (character) {
                case '\"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) output.append(String.format("\\u%04x", (int) character));
                    else output.append(character);
                }
            }
        }
        return output.append('\"').toString();
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) { this.source = source; }
        private boolean end() { return index == source.length(); }
        private void space() { while (!end() && Character.isWhitespace(source.charAt(index))) index++; }

        private Object value() {
            space();
            if (end()) throw new IllegalArgumentException("Expected JSON value");
            return switch (source.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '\"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            index++;
            Map<String, Object> map = new LinkedHashMap<>();
            space();
            if (take('}')) return map;
            do {
                space();
                String key = string();
                space();
                expect(':');
                map.put(key, value());
                space();
            } while (take(','));
            expect('}');
            return map;
        }

        private List<Object> array() {
            index++;
            List<Object> list = new ArrayList<>();
            space();
            if (take(']')) return list;
            do {
                list.add(value());
                space();
            } while (take(','));
            expect(']');
            return list;
        }

        private String string() {
            expect('\"');
            StringBuilder output = new StringBuilder();
            while (!end()) {
                char character = source.charAt(index++);
                if (character == '\"') return output.toString();
                if (character != '\\') {
                    output.append(character);
                    continue;
                }
                if (end()) throw new IllegalArgumentException("Incomplete JSON escape");
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '\"', '\\', '/' -> output.append(escaped);
                    case 'b' -> output.append('\b');
                    case 'f' -> output.append('\f');
                    case 'n' -> output.append('\n');
                    case 'r' -> output.append('\r');
                    case 't' -> output.append('\t');
                    case 'u' -> {
                        if (index + 4 > source.length()) throw new IllegalArgumentException("Incomplete unicode escape");
                        output.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Object number() {
            int start = index;
            while (!end() && "-+0123456789.eE".indexOf(source.charAt(index)) >= 0) index++;
            String token = source.substring(start, index);
            try {
                return token.contains(".") || token.contains("e") || token.contains("E")
                        ? Double.parseDouble(token) : Long.parseLong(token);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid JSON number", exception);
            }
        }

        private Object literal(String token, Object value) {
            if (!source.startsWith(token, index)) throw new IllegalArgumentException("Invalid JSON literal");
            index += token.length();
            return value;
        }

        private boolean take(char expected) {
            if (!end() && source.charAt(index) == expected) { index++; return true; }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) throw new IllegalArgumentException("Expected '" + expected + "'");
        }
    }
}
