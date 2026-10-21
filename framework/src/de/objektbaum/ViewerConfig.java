package de.objektbaum;

import java.util.List;
import java.util.Map;

/**
 * Inhalt einer viewer.xml: Titel, Objekttypen, Spalten der Baumtabelle und Ansichten.
 *
 * @param title       Anwendungstitel oder null (dann gilt der Titel aus dem Code)
 * @param types       Typdefinitionen: Kennung, Anzeigetext, Icon
 * @param treeColumns Spalten der Baumtabelle oder null (dann gelten die aus dem Code)
 * @param views       Ansichten je Objekttyp
 */
public record ViewerConfig(String title, List<TypeDef> types, List<TreeColumn> treeColumns,
                           Map<ObjectType, NodeView> views) {

    public static final ViewerConfig EMPTY = new ViewerConfig(null, List.of(), null, Map.of());

    /** Ein Objekttyp aus der Konfiguration. */
    public record TypeDef(String id, String label, String icon) {
    }

    public ViewerConfig {
        types = types == null ? List.of() : List.copyOf(types);
        treeColumns = treeColumns == null ? null : List.copyOf(treeColumns);
        views = views == null ? Map.of() : Map.copyOf(views);
    }
}
