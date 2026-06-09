package com.example.quickbridge.service;

import java.security.SecureRandom;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

/**
 * Generates short, human-friendly session codes.
 *
 * Codes are 5 characters drawn from an unambiguous alphabet — ambiguous glyphs
 * (0/O, 1/I/L) are intentionally excluded so a code is easy to read aloud and
 * type across devices.
 */
@Component
public class CodeGenerator {

    /**
     * Allowed alphabet for session codes. Ambiguous glyphs 0, O, 1 and I are
     * excluded so codes read cleanly across devices.
     */
    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public static final int CODE_LENGTH = 5;

    private final SecureRandom random = new SecureRandom();

    /** Generates a single random code. May collide with an existing one. */
    public String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Generates a code that is unique according to {@code isTaken}, retrying on
     * collision. Uniqueness against the live session map is the caller's
     * concern; the caller should perform the final insert atomically.
     */
    public String generateUnique(Predicate<String> isTaken) {
        String code;
        do {
            code = generate();
        } while (isTaken.test(code));
        return code;
    }

    /** Validates that a code is exactly 5 chars from the allowed alphabet. */
    public static boolean isValid(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
