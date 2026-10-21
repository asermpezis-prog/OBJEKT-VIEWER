package de.objektbaum;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Legt fest, welche {@link NodeView} für welchen {@link ObjectType} gilt.
 * Reihenfolge beim Anzeigen eines Knotens:
 * <ol>
 *   <li>eigene Ansicht des Knotens ({@link ObjectNode#view(NodeView)}),</li>
 *   <li>Ansicht seines Typs aus der Konfiguration (viewer.xml, wird bei Änderungen neu gelesen),</li>
 *   <li>Ansicht seines Typs aus dem Code ({@link #register}),</li>
 *   <li>keine: Standarddarstellung (alle Felder + Unterobjekte).</li>
 * </ol>
 */
public final class ViewRegistry {

    private final Map<ObjectType, NodeView> byType = new ConcurrentHashMap<>();
    private volatile Supplier<Map<ObjectType, NodeView>> configured = Map::of;

    /** Ansicht im Code festlegen (wird von viewer.xml überschrieben, falls dort eine für den Typ steht). */
    public ViewRegistry register(ObjectType type, NodeView view) {
        byType.put(type, view);
        return this;
    }

    /** Quelle für die Ansichten aus der Konfiguration; wird bei jedem Anzeigen abgefragt. */
    public ViewRegistry configured(Supplier<Map<ObjectType, NodeView>> source) {
        this.configured = source == null ? Map::of : source;
        return this;
    }

    /** Die Ansicht für diesen Knoten oder null für die Standarddarstellung. */
    public NodeView resolve(ObjectNode node) {
        NodeView own = node.view();
        if (own != null) return own;
        NodeView fromConfig = configured.get().get(node.type());
        return fromConfig != null ? fromConfig : byType.get(node.type());
    }
}
