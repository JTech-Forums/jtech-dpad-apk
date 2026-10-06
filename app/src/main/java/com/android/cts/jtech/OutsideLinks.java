package com.android.cts.jtech;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Locale;

/**
 * The kinds of clicked link that may leave the app for another app, and the text copied for each
 * when the phone has no app for it. Plain Java, so it can be tested without a phone.
 */
final class OutsideLinks {

    private OutsideLinks() {}

    static final String WEB = "web";
    static final String EMAIL = "email";
    static final String PHONE = "phone";
    static final String SMS = "sms";

    /** WEB, EMAIL, PHONE or SMS for a link that may leave the app; null for anything else. */
    static String kind(String url) {
        if (url == null) return null;
        int colon = url.indexOf(':');
        if (colon <= 0) return null;
        String scheme = url.substring(0, colon).toLowerCase(Locale.ROOT);
        switch (scheme) {
            case "http":
            case "https":
                return WEB;
            case "mailto":
                return EMAIL;
            case "tel":
                return PHONE;
            case "sms":
            case "smsto":
                return SMS;
            default:
                return null;
        }
    }

    /**
     * What is copied when no app can take the link: a web link whole; for the others just the
     * address or number, without the scheme or anything after "?" (subject, body ...). Falls back
     * to the whole link when there is no address to take.
     */
    static String copyText(String url) {
        String kind = kind(url);
        if (kind == null || WEB.equals(kind)) return url;
        String rest = url.substring(url.indexOf(':') + 1);
        if (rest.startsWith("//")) rest = rest.substring(2);
        int q = rest.indexOf('?');
        if (q >= 0) rest = rest.substring(0, q);
        int hash = rest.indexOf('#');
        if (hash >= 0) rest = rest.substring(0, hash);
        try {
            // In a URL a '+' is a literal '+' (a phone number's), not a space.
            rest = URLDecoder.decode(rest.replace("+", "%2B"), "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            // Leave it as written.
        }
        rest = rest.trim();
        return rest.isEmpty() ? url : rest;
    }

    /** The message shown after copying. */
    static String copiedMessage(String kind) {
        if (EMAIL.equals(kind)) return "Email address copied";
        if (PHONE.equals(kind) || SMS.equals(kind)) return "Phone number copied";
        return "Link copied";
    }
}
