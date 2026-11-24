package de.ro.testat.probelauf;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Fuehrt C-Laeufe in einem wiederverwendeten Hintergrund-Thread mit grossem Stack aus, damit auch
 * tiefe Rekursion (bis 2000 Aufrufe) geht. Frueher wurde fuer jeden Lauf ein neuer Thread gestartet;
 * der Generator ruft den Interpreter aber pro Aufgabe 10-20-mal auf.
 *
 * Nur fuer die JVM (Kommandozeile). Im Browser wird diese Klasse nie geladen, dort laeuft der
 * Interpreter direkt (siehe {@link CInterpreter#setAusfuehrung}).
 */
public final class GrosserStack implements CInterpreter.Ausfuehrung {

    private static final ExecutorService THREAD = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(null, r, "c-probelauf", 512L << 20);
        t.setDaemon(true);
        return t;
    });

    private GrosserStack() {
    }

    /** Schaltet den C-Interpreter auf den Thread mit grossem Stack um. */
    public static void aktivieren() {
        CInterpreter.setAusfuehrung(new GrosserStack());
    }

    @Override
    public CInterpreter.Ergebnis ausfuehren(Supplier<CInterpreter.Ergebnis> lauf) {
        try {
            return THREAD.submit(lauf::get).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("unterbrochen", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Interner Fehler im C-Interpreter: " + e.getCause(), e.getCause());
        }
    }
}
