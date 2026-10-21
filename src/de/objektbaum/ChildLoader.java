package de.objektbaum;

import java.util.List;

/**
 * Lädt die Unterobjekte eines Knotens erst dann, wenn sie zum ersten Mal gebraucht werden
 * (z. B. aus einer Datenbank, einer Datei oder einem Webservice).
 */
@FunctionalInterface
public interface ChildLoader {

    /**
     * @param parent der Knoten, dessen Kinder geladen werden sollen
     * @return die Kinder; leere Liste, wenn es keine gibt (nie null)
     */
    List<ObjectNode> load(ObjectNode parent) throws Exception;
}
