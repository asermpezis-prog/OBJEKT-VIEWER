package de.objektbaum;

import java.util.EnumMap;
import java.util.Map;

/**
 * Legt fest, welche {@link NodeView} für welchen {@link ObjectType} gilt.
 * Reihenfolge beim Anzeigen eines Knotens:
 * <ol>
 *   <li>eigene Ansicht des Knotens ({@link ObjectNode#view(NodeView)}),</li>
 *   <li>Ansicht seines Typs aus dieser Registry,</li>
 *   <li>keine: Standarddarstellung (alle Felder + Unterobjekte).</li>
 * </ol>
 */
public final class ViewRegistry {

    private final Map<ObjectType, NodeView> byType = new EnumMap<>(ObjectType.class);

    public ViewRegistry register(ObjectType type, NodeView view) {
        byType.put(type, view);
        return this;
    }

    /** Die Ansicht für diesen Knoten oder null für die Standarddarstellung. */
    public NodeView resolve(ObjectNode node) {
        NodeView own = node.view();
        return own != null ? own : byType.get(node.type());
    }
}
