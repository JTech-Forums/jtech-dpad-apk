package com.android.cts.jtech;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A download's file name from its Content-Disposition header. Android's own URLUtil.guessFileName
 * can't read a header that also carries the RFC 6266 form - the forum sends
 * {@code attachment; filename="a.png"; filename*=UTF-8''a.png} - on older Android, so it fell back
 * to the URL's last segment (an upload's hash) and named the file "<hash>.bin".
 */
final class FileNames {

    private FileNames() {}

    // filename*=charset'language'percent-encoded
    private static final Pattern EXTENDED = Pattern.compile(
            "(?:^|;)\\s*filename\\*\\s*=\\s*([^';\\s]*)'[^']*'([^;\\s]+)",
            Pattern.CASE_INSENSITIVE);
    // filename="quoted \" string" or filename=token
    private static final Pattern PLAIN = Pattern.compile(
            "(?:^|;)\\s*filename\\s*=\\s*(?:\"((?:\\\\.|[^\"\\\\])*)\"|([^;]*))",
            Pattern.CASE_INSENSITIVE);

    /** The file name the header gives, or null when it gives none (or only an unusable one). */
    static String fromDisposition(String disposition) {
        if (disposition == null) return null;
        Matcher m = EXTENDED.matcher(disposition);
        if (m.find()) {
            String name = clean(decode(m.group(2), m.group(1)));
            if (name != null) return name;
        }
        m = PLAIN.matcher(disposition);
        if (m.find()) {
            String name = m.group(1) != null
                    ? m.group(1).replaceAll("\\\\(.)", "$1")
                    : m.group(2);
            return clean(name);
        }
        return null;
    }

    /** Whether a file name has an extension ("a.png" yes; "a", ".profile", "a." no). */
    static boolean hasExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1;
    }

    private static String decode(String value, String charset) {
        String cs = charset == null || charset.isEmpty() ? "UTF-8" : charset;
        try {
            // URLDecoder turns '+' into a space; in this form a '+' is a literal '+'.
            return URLDecoder.decode(value.replace("+", "%2B"), cs);
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return null;
        }
    }

    // Only the last path segment, never a path, and never a hidden or empty name.
    private static String clean(String name) {
        if (name == null) return null;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1).replaceAll("[\\x00-\\x1f\"*:<>?|]", "_").trim();
        while (name.startsWith(".")) name = name.substring(1);
        return name.isEmpty() ? null : name;
    }

    /** The bare, lower-case MIME type ("Image/PNG; x=y" -> "image/png"), or null. */
    static String mimeKey(String mimetype) {
        if (mimetype == null) return null;
        int semi = mimetype.indexOf(';');
        String type = (semi >= 0 ? mimetype.substring(0, semi) : mimetype).trim();
        return type.isEmpty() ? null : type.toLowerCase(Locale.ROOT);
    }
}
