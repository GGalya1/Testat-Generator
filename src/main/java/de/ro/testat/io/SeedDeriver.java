package de.ro.testat.io;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Seeds fuer die Aufgaben.
 *
 * Mit Schluessel: seed = HMAC-SHA256(schluessel, thema + "|" + matrikelnummer).
 * Gleicher Schluessel + gleiche Matrikelnummer + gleiches Thema = gleiche Aufgabe,
 * aber ohne Schluessel laesst sich die Aufgabe nicht vorhersagen.
 * Fuer eine Wiederholung (Nachtestat) einfach einen neuen Schluessel verwenden.
 *
 * Ohne Schluessel: zufaelliger Seed; reproduzierbar nur ueber die Zuordnungsdatei.
 */
public final class SeedDeriver {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SeedDeriver() {
    }

    public static long derive(String key, String testatId, String matrikel) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal((testatId + "|" + matrikel).getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(h, 0, 8).getLong() & Long.MAX_VALUE;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 nicht verfuegbar", e);
        }
    }

    public static long random() {
        return RANDOM.nextLong() & Long.MAX_VALUE;
    }
}
