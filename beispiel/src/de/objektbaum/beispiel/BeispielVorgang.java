package de.objektbaum.beispiel;

import de.objektbaum.NodeStyle;
import de.objektbaum.ObjectNode;
import de.objektbaum.ObjectViewer;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Beispiel für den Ablauf wie im Fachsystem (Swing):
 * <ol>
 *   <li>Anmeldemaske – hier: Benutzer {@code demo}, Passwort {@code demo}</li>
 *   <li>„Vorgang suchen“ mit VorgangsId und/oder VorgangsNummer – hier: Nummer {@code 100000001}</li>
 *   <li>„Vorgang lesen“ zeigt nur den Vorgang (Wurzel)</li>
 *   <li>Jeder Klick auf ein Objekt ruft den Listener auf, der die Kinder holt – der Baum baut sich Klick für Klick auf</li>
 * </ol>
 * Als „Server“ dienen die erfundenen Daten aus {@link BeispielApp}. In der echten Anwendung stehen an den markierten
 * Stellen die Aufrufe der DynS-Webservices.
 *
 * <pre>
 * ./run-vorgang.sh              Konfiguration aus beispiel/config
 * </pre>
 */
public final class BeispielVorgang {

    /** Angezeigtes Objekt → Objekt beim "Server" (nur für dieses Beispiel). */
    private static final Map<ObjectNode, ObjectNode> SERVER = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final ObjectNode DATEN = BeispielApp.beispielDaten();

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Kein Bildschirm verfügbar: Die Swing-Version braucht eine grafische Oberfläche.");
            System.exit(1);
        }
        Path config = Path.of(args.length > 0 ? args[0] : "beispiel/config");
        System.out.println("Anmeldung: Benutzer demo, Passwort demo – dann VorgangsNummer 100000001 oder VorgangsId "
                + DATEN.fields().get("ObjektId") + " eingeben.");
        viewer(config).openSwing();
    }

    static ObjectViewer viewer(Path configDir) {
        return BeispielApp.builder(configDir)
                .title("Vorgang Viewer")
                .root(null)

                // 1. Anmeldung – Prüfung macht die Anwendung (hier: demo/demo)
                .login((benutzer, passwort) -> {
                    Thread.sleep(300);                                   // z. B. LDAP oder Webservice
                    if (!benutzer.equals("demo") || !new String(passwort).equals("demo")) {
                        throw new Exception("Benutzer oder Passwort falsch.");
                    }
                })

                // 2. Vorgang suchen + Vorgang lesen
                .searchFields("VorgangsId", "VorgangsNummer")
                .search(kriterien -> {
                    String id = kriterien.get("VorgangsId");
                    String nr = kriterien.get("VorgangsNummer");
                    Thread.sleep(300);                                   // hier: DynS-Aufruf „Vorgang lesen“
                    boolean passt = (id == null || id.equals(DATEN.fields().get("ObjektId")))
                            && (nr == null || DATEN.name().contains("(" + nr + ")"));
                    return passt ? kopie(DATEN) : null;                  // nur der Vorgang, ohne Kinder
                })

                // 3. Jeder Klick: Kinder holen und anhängen – der Baum baut sich auf
                .onNodeClick(knoten -> {
                    ObjectNode quelle = SERVER.get(knoten);
                    if (quelle == null) return null;
                    Thread.sleep(150);                                   // hier: DynS-Aufruf für dieses Objekt
                    knoten.removeChildren();                             // bei jedem Klick frisch
                    for (ObjectNode kind : quelle.children()) {
                        knoten.add(kopie(kind));
                    }
                    return null;                                         // keine Meldung
                })

                // 4. Aussehen einzelner Objekte nach einer Regel
                .nodeStyle(n -> {
                    Object status = n.fields().get("Status");
                    if ("Abgelehnt".equals(status)) {
                        return NodeStyle.of().color("#9ca3af").italic().tooltip("Variante abgelehnt");
                    }
                    if ("Hauptangebot".equals(n.fields().get("Beziehungsart"))) {
                        return NodeStyle.of().bold().icon("★").tooltip("Hauptangebot");
                    }
                    return null;
                })

                // Buttons unter der Baumtabelle – wirken auf das ausgewählte Objekt
                .treeButton("Vorgang builden", (knoten, werte) -> "Vorgang wird gebaut (Beispiel).")
                .treeButton("Angebot löschen", (knoten, werte) -> {
                    if (!knoten.type().id().equals("ANGEBOT") && !knoten.type().id().equals("NEUES_ANGEBOT")) {
                        throw new Exception("Bitte zuerst ein Angebot auswählen.");
                    }
                    knoten.parent().removeChild(knoten);
                    return "„" + knoten.name() + "“ gelöscht.";
                })
                .build();
    }

    /** Flache Kopie ohne Kinder (so wie ein Webservice nur dieses eine Objekt liefert). */
    private static ObjectNode kopie(ObjectNode quelle) {
        ObjectNode n = new ObjectNode(quelle.name(), quelle.type());
        quelle.fields().forEach(n::field);
        if ("Fzve".equals(quelle.name())) {
            n.style(NodeStyle.of().color("#b91c1c").background("#fee2e2").tooltip("Eigenes Aussehen per knoten.style(...)"));
        }
        SERVER.put(n, quelle);
        return n;
    }

    private BeispielVorgang() {
    }
}
