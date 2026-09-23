package org.academy.api.common.ability.program;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Bounded, world-independent text operations shared by ability programs and extensions. */
public final class ProgramTextOperations {
    public static final int MAX_TEXT_LENGTH = 4096;
    public static final int MAX_DELIMITER_LENGTH = 64;
    private static final String NUMBER = "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?";
    private static final String AXIS = "(?:[~^](?:" + NUMBER + ")?|" + NUMBER + ")";
    private static final String SEPARATOR = "(?:\\h*[,，]\\h*|\\h+)";
    private static final String LABELLED = "[xX]\\h*[:=：]\\h*(" + AXIS + ")" + SEPARATOR
            + "[yY]\\h*[:=：]\\h*(" + AXIS + ")" + SEPARATOR
            + "[zZ]\\h*[:=：]\\h*(" + AXIS + ")";
    private static final Pattern TRIPLE = Pattern.compile("(" + AXIS + ")" + SEPARATOR
            + "(" + AXIS + ")" + SEPARATOR + "(" + AXIS + ")");
    private static final Pattern LABELLED_TRIPLE = Pattern.compile(LABELLED);
    // Consume an entire numeric run or bracketed expression before validating it. This avoids
    // silently turning four components or an invalid bracketed value into a different triplet.
    private static final Pattern CANDIDATE = Pattern.compile(
            "\\([^()\\r\\n]*\\)|\\[[^\\[\\]\\r\\n]*\\]|（[^（）\\r\\n]*）|"
                    + LABELLED + "|" + AXIS + "(?:" + SEPARATOR + AXIS + "){2,}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern LINES = Pattern.compile("\\R");
    private static final Pattern SENTENCES = Pattern.compile("[。！？!?；;]+|(?<!\\d)\\.|\\.(?!\\d)|\\R");

    private ProgramTextOperations() {
    }

    /** Parses the whole text or selects a zero-based, valid coordinate expression within it. */
    public static Optional<Coordinates> parseCoordinates(String text, boolean extract, int index) {
        if (text == null || text.length() > MAX_TEXT_LENGTH || index < 0) return Optional.empty();
        if (!extract) return index == 0 ? parseTriple(text.strip()) : Optional.empty();
        var matcher = CANDIDATE.matcher(text);
        var found = 0;
        while (matcher.find()) {
            if (!boundary(text, matcher.start() - 1) || !boundary(text, matcher.end())) continue;
            var parsed = parseTriple(matcher.group().strip());
            if (parsed.isPresent() && found++ == index) return parsed;
        }
        return Optional.empty();
    }

    private static boolean boundary(String text, int offset) {
        if (offset < 0 || offset >= text.length()) return true;
        var c = text.charAt(offset);
        // CJK prose may touch a coordinate; Latin identifiers and partial numbers may not.
        return !((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || Character.isDigit(c)
                || "_~^.+-=()[]（）".indexOf(c) >= 0);
    }

    private static Optional<Coordinates> parseTriple(String text) {
        if (text.isEmpty()) return Optional.empty();
        var opening = "([（".indexOf(text.charAt(0));
        if (opening >= 0) {
            if (text.charAt(text.length() - 1) != ")]）".charAt(opening)) return Optional.empty();
            text = text.substring(1, text.length() - 1).strip();
        }
        var matcher = TRIPLE.matcher(text);
        if (!matcher.matches()) {
            matcher = LABELLED_TRIPLE.matcher(text);
            if (!matcher.matches()) return Optional.empty();
        }
        try {
            var x = axis(matcher.group(1));
            var y = axis(matcher.group(2));
            var z = axis(matcher.group(3));
            var coordinates = new Coordinates(x, y, z);
            var localCount = (x.kind == AxisKind.LOCAL ? 1 : 0)
                    + (y.kind == AxisKind.LOCAL ? 1 : 0) + (z.kind == AxisKind.LOCAL ? 1 : 0);
            return localCount == 0 || localCount == 3 ? Optional.of(coordinates) : Optional.empty();
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private static Axis axis(String token) {
        var kind = switch (token.charAt(0)) {
            case '~' -> AxisKind.RELATIVE;
            case '^' -> AxisKind.LOCAL;
            default -> AxisKind.ABSOLUTE;
        };
        var number = kind == AxisKind.ABSOLUTE ? token : token.substring(1);
        return new Axis(kind, number.isEmpty() ? 0 : Double.parseDouble(number));
    }

    /** Splits on literal delimiters or bounded built-in rules; custom text is never a regex. */
    public static List<String> split(String text, SplitMode mode, String delimiter, boolean trim, boolean skipEmpty) {
        if (text == null || text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Text exceeds the split limit");
        }
        var pattern = switch (mode) {
            case WHITESPACE -> WHITESPACE;
            case LINE -> LINES;
            case SENTENCE -> SENTENCES;
            case DELIMITER -> {
                if (delimiter == null || delimiter.isEmpty() || delimiter.length() > MAX_DELIMITER_LENGTH) {
                    throw new IllegalArgumentException("A literal delimiter of 1 to 64 characters is required");
                }
                yield Pattern.compile(Pattern.quote(delimiter));
            }
        };
        var parts = new ArrayList<String>();
        for (var part : pattern.split(text, -1)) {
            if (trim) part = part.strip();
            if (!skipEmpty || !part.isEmpty()) parts.add(part);
        }
        return List.copyOf(parts);
    }

    public enum SplitMode { WHITESPACE, LINE, SENTENCE, DELIMITER }

    public enum AxisKind { ABSOLUTE, RELATIVE, LOCAL }

    public record Axis(AxisKind kind, double value) {
        public Axis {
            if (kind == null || !Double.isFinite(value)) throw new IllegalArgumentException("Invalid coordinate axis");
        }
    }

    /** Feet position and Minecraft yaw/pitch in degrees. Local axes are left, up, forward. */
    public record Reference(ProgramWorldPosition origin, double yaw, double pitch) {
        public Reference {
            if (origin == null || !Double.isFinite(yaw) || !Double.isFinite(pitch)) {
                throw new IllegalArgumentException("Invalid coordinate reference");
            }
        }
    }

    public record Coordinates(Axis x, Axis y, Axis z) {
        public boolean absolute() {
            return x.kind == AxisKind.ABSOLUTE && y.kind == AxisKind.ABSOLUTE && z.kind == AxisKind.ABSOLUTE;
        }

        /** Relative (~) axes require a position result. Local (^) vectors rotate without translation. */
        public Optional<ProgramVector> resolve(@Nullable Reference reference, boolean position) {
            try {
                if (absolute()) return Optional.of(new ProgramVector(x.value, y.value, z.value));
                if (reference == null) return Optional.empty();
                if (x.kind == AxisKind.LOCAL && y.kind == AxisKind.LOCAL && z.kind == AxisKind.LOCAL) {
                    var yaw = Math.toRadians(reference.yaw);
                    var pitch = Math.toRadians(reference.pitch);
                    var left = new ProgramVector(Math.cos(yaw), 0, Math.sin(yaw));
                    var forward = new ProgramVector(-Math.sin(yaw) * Math.cos(pitch),
                            -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
                    var up = forward.cross(left);
                    var result = left.scale(x.value).add(up.scale(y.value)).add(forward.scale(z.value));
                    return Optional.of(position ? result.add(ProgramVector.of(reference.origin)) : result);
                }
                if (!position || x.kind == AxisKind.LOCAL || y.kind == AxisKind.LOCAL || z.kind == AxisKind.LOCAL) {
                    return Optional.empty();
                }
                return Optional.of(new ProgramVector(
                        resolveAxis(x, reference.origin.x()), resolveAxis(y, reference.origin.y()),
                        resolveAxis(z, reference.origin.z())));
            } catch (IllegalArgumentException overflow) {
                return Optional.empty();
            }
        }

        private static double resolveAxis(Axis axis, double origin) {
            return axis.kind == AxisKind.RELATIVE ? origin + axis.value : axis.value;
        }
    }
}
