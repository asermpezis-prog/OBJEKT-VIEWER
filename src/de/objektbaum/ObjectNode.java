package de.objektbaum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ein Objekt mit beliebig vielen Feldern (Key/Value) und beliebig vielen Unterobjekten.
 */
public final class ObjectNode {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger();

    private final int id;
    private final String name;
    private final ObjectType type;
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final List<ObjectNode> children = new ArrayList<>();
    private ObjectNode parent;

    public ObjectNode(String name, ObjectType type) {
        this.id = ID_COUNTER.incrementAndGet();
        this.name = name;
        this.type = type == null ? ObjectType.SONSTIGES : type;
    }

    /** Setzt ein Feld. Gibt this zurück, damit Aufrufe verkettet werden können. */
    public ObjectNode field(String key, Object value) {
        fields.put(key, value);
        return this;
    }

    /** Hängt ein Unterobjekt an und gibt this zurück. */
    public ObjectNode add(ObjectNode child) {
        if (child.parent != null) {
            child.parent.children.remove(child);
        }
        child.parent = this;
        children.add(child);
        return this;
    }

    /** Erzeugt ein neues Unterobjekt, hängt es an und gibt das Kind zurück. */
    public ObjectNode child(String name, ObjectType type) {
        ObjectNode child = new ObjectNode(name, type);
        add(child);
        return child;
    }

    public boolean removeChild(ObjectNode child) {
        if (children.remove(child)) {
            child.parent = null;
            return true;
        }
        return false;
    }

    /** Sucht ein Objekt mit dieser id in diesem Teilbaum. */
    public Optional<ObjectNode> find(int searchId) {
        if (id == searchId) {
            return Optional.of(this);
        }
        for (ObjectNode c : children) {
            Optional<ObjectNode> found = c.find(searchId);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /** Pfad von der Wurzel bis zu diesem Objekt. */
    public List<ObjectNode> path() {
        List<ObjectNode> path = new ArrayList<>();
        for (ObjectNode n = this; n != null; n = n.parent) {
            path.add(0, n);
        }
        return path;
    }

    public boolean isAncestorOf(ObjectNode other) {
        for (ObjectNode n = other; n != null; n = n.parent) {
            if (n == this) {
                return true;
            }
        }
        return false;
    }

    public int countDescendants() {
        int count = children.size();
        for (ObjectNode c : children) {
            count += c.countDescendants();
        }
        return count;
    }

    public int id() { return id; }
    public String name() { return name; }
    public ObjectType type() { return type; }
    public ObjectNode parent() { return parent; }
    public Map<String, Object> fields() { return Collections.unmodifiableMap(fields); }
    public List<ObjectNode> children() { return Collections.unmodifiableList(children); }

    @Override
    public String toString() {
        return name + " [" + type + "] " + fields;
    }
}
