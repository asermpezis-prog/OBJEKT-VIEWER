package de.objektbaum;

import java.util.Map;

/**
 * Liest einen Vorgang (oder ein anderes Wurzelobjekt) anhand der Suchmaske, z. B. VorgangsId und/oder VorgangsNummer.
 * Wird beim Klick auf „Vorgang lesen“ im Hintergrund aufgerufen. Das Ergebnis wird zur Wurzel des Baums.
 *
 * <pre>
 * .search(kriterien -> {
 *     String id = kriterien.get("VorgangsId");
 *     String nr = kriterien.get("VorgangsNummer");
 *     return dynsService.leseVorgang(id, nr);   // ObjectNode mit Typ VORGANG
 * })
 * </pre>
 */
@FunctionalInterface
public interface SearchHandler {

    /**
     * @param criteria Eingaben der Suchmaske: Feldname → Wert (ohne Leerzeichen am Rand; leere Felder fehlen).
     *                 Mindestens ein Feld ist gefüllt.
     * @return die Wurzel des Baums; null = nichts gefunden (Meldung „Nichts gefunden“)
     * @throws Exception Fehlermeldung für den Benutzer
     */
    ObjectNode search(Map<String, String> criteria) throws Exception;
}
