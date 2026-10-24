package de.objektbaum;

/**
 * Wird bei jedem Klick auf ein Objekt aufgerufen (Navigation, Baumtabelle, Pfad, Unterobjekte) – im Hintergrund,
 * bevor das Objekt angezeigt wird. Hier kann der Baum Schritt für Schritt aufgebaut werden.
 *
 * <pre>
 * .onNodeClick(knoten -> {
 *     if (knoten.type() == Typ.VORHABEN) {
 *         knoten.removeChildren();                       // bei jedem Klick frisch
 *         for (Variante v : dyns.varianten(knoten.fields().get("ObjektId"))) {
 *             knoten.child(v.name(), Typ.VARIANTE).field("ObjektId", v.id());
 *         }
 *     }
 *     return null;                                       // keine Meldung
 * })
 * </pre>
 *
 * Danach werden Navigation und Baumtabelle aktualisiert, das Objekt wird aufgeklappt und angezeigt.
 * Auf- und Zuklappen über +/− oder die Dreiecke löst den Listener nicht aus.
 */
@FunctionalInterface
public interface NodeClickListener {

    /**
     * @param node das angeklickte Objekt; Felder und Kinder dürfen geändert werden
     * @return Meldung für den Benutzer (grün) oder null
     * @throws Exception wird als rote Meldung angezeigt; das Objekt wird trotzdem angezeigt
     */
    String clicked(ObjectNode node) throws Exception;
}
