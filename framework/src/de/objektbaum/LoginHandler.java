package de.objektbaum;

/**
 * Prüft eine Anmeldung. Wird von der Anmeldemaske aufgerufen – im Hintergrund, die Oberfläche bleibt bedienbar.
 *
 * <pre>
 * .login((benutzer, passwort) -> {
 *     if (!meinVerzeichnis.pruefe(benutzer, passwort)) {
 *         throw new Exception("Benutzer oder Passwort falsch");
 *     }
 * })
 * </pre>
 *
 * Normales Ende = angemeldet. Eine Exception = nicht angemeldet; ihre Meldung erscheint in der Maske.
 */
@FunctionalInterface
public interface LoginHandler {

    /**
     * @param user     eingegebener Benutzername (ohne Leerzeichen am Rand)
     * @param password eingegebenes Passwort; wird nach dem Aufruf mit Nullen überschrieben
     * @throws Exception wenn die Anmeldung abgelehnt wird (Meldung für den Benutzer)
     */
    void login(String user, char[] password) throws Exception;
}
