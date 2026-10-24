package de.objektbaum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ein Objekt mit Typ, beliebig vielen Feldern (Key/Value) und beliebig vielen Unterobjekten.
 *
 * <p>Unterobjekte können sofort angehängt werden ({@link #add}, {@link #child}) oder per
 * {@link #lazy(ChildLoader)} erst beim ersten Zugriff auf {@link #children()} geladen werden.
 */
public final class ObjectNode {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger();

    private final int id;
    private final String name;
    private final ObjectType type;
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final List<ObjectNode> children = new ArrayList<>();
    private volatile ObjectNode parent;

    private volatile NodeView view;
    private volatile NodeStyle style;
    private ChildLoader loader;
    private volatile boolean loaded = true;

    public ObjectNode(String name, ObjectType type) {
        this.id = ID_COUNTER.incrementAndGet();
        this.name = name;
        this.type = type == null ? ObjectType.UNBEKANNT : type;
    }

    // ------------------------------------------------------------------ Felder

    /** Setzt ein Feld. Gibt this zurück, damit Aufrufe verkettet werden können. */
    public synchronized ObjectNode field(String key, Object value) {
        fields.put(key, value);
        return this;
    }

    public synchronized Map<String, Object> fields() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    // ----------------------------------------------------------------- Ansicht

    /**
     * Eigene Ansicht nur für diesen Knoten. Überschreibt die Ansicht seines Typs
     * aus der {@link ViewRegistry}. null = Ansicht des Typs verwenden.
     */
    public ObjectNode view(NodeView view) {
        this.view = view;
        return this;
    }

    /** Die eigene Ansicht dieses Knotens oder null. */
    public NodeView view() {
        return view;
    }

    /** Eigenes Aussehen dieses Objekts (Farbe, fett, Icon …); null = Standard bzw. Regel aus dem Builder. */
    public ObjectNode style(NodeStyle style) {
        this.style = style;
        return this;
    }

    /** Das eigene Aussehen oder null. */
    public NodeStyle style() {
        return style;
    }

    // ------------------------------------------------------------ Unterobjekte

    /** Hängt ein Unterobjekt an und gibt this zurück. */
    public ObjectNode add(ObjectNode child) {
        ObjectNode oldParent = child.parent;
        if (oldParent != null && oldParent != this) {
            oldParent.removeChild(child);
        }
        synchronized (this) {
            if (!children.contains(child)) {
                child.parent = this;
                children.add(child);
            }
        }
        return this;
    }

    /** Erzeugt ein neues Unterobjekt, hängt es an und gibt das Kind zurück. */
    public ObjectNode child(String name, ObjectType type) {
        ObjectNode child = new ObjectNode(name, type);
        add(child);
        return child;
    }

    public synchronized boolean removeChild(ObjectNode child) {
        if (children.remove(child)) {
            child.parent = null;
            return true;
        }
        return false;
    }

    /** Entfernt alle Unterobjekte (z. B. um sie in einem {@link NodeClickListener} neu aufzubauen). */
    public synchronized void removeChildren() {
        for (ObjectNode c : children) {
            c.parent = null;
        }
        children.clear();
    }

    /**
     * Die Unterobjekte. Ist der Knoten lazy und noch nicht geladen, wird jetzt geladen.
     *
     * @throws ChildLoadException wenn der Loader scheitert (der Knoten bleibt dann ungeladen)
     */
    public List<ObjectNode> children() {
        ensureLoaded();
        return loadedChildren();
    }

    /** Die bereits vorhandenen Unterobjekte, ohne einen Ladevorgang auszulösen. */
    public synchronized List<ObjectNode> loadedChildren() {
        return List.copyOf(children);
    }

    // ------------------------------------------------------------ Lazy Loading

    /**
     * Macht diesen Knoten lazy: die Kinder werden erst beim ersten Zugriff über den Loader geholt.
     * Bereits per {@link #add} angehängte Kinder bleiben erhalten.
     */
    public synchronized ObjectNode lazy(ChildLoader loader) {
        this.loader = loader;
        this.loaded = loader == null;
        return this;
    }

    /** Lädt die Kinder, falls noch nicht geschehen. Mehrere Threads laden nie doppelt. */
    public void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (this) {
            if (loaded) {
                return;
            }
            List<ObjectNode> result;
            try {
                result = loader.load(this);
            } catch (Exception e) {
                throw new ChildLoadException(this, e);
            }
            if (result != null) {
                for (ObjectNode c : result) {
                    add(c);
                }
            }
            loaded = true;
        }
    }

    /**
     * Verwirft die geladenen Kinder, damit sie beim nächsten Zugriff neu geladen werden.
     * Wirkt nur bei lazy Knoten.
     */
    public synchronized void reload() {
        if (loader == null) {
            return;
        }
        for (ObjectNode c : children) {
            c.parent = null;
        }
        children.clear();
        loaded = false;
    }

    /** true, wenn dieser Knoten einen Loader hat. */
    public synchronized boolean isLazy() {
        return loader != null;
    }

    /** true, wenn die Kinder vorhanden sind (nicht lazy, oder bereits geladen). */
    public boolean isLoaded() {
        return loaded;
    }

    /** true, wenn es Kinder gibt oder geben könnte (noch ungeladen). Löst kein Laden aus. */
    public synchronized boolean mayHaveChildren() {
        return !loaded || !children.isEmpty();
    }

    // ---------------------------------------------------------------- Abfragen

    /**
     * Sucht ein Objekt mit dieser id im bereits geladenen Teil des Baums.
     * Löst kein Laden aus.
     */
    public Optional<ObjectNode> find(int searchId) {
        if (id == searchId) {
            return Optional.of(this);
        }
        for (ObjectNode c : loadedChildren()) {
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

    public int id() { return id; }
    public String name() { return name; }
    public ObjectType type() { return type; }
    public ObjectNode parent() { return parent; }

    @Override
    public String toString() {
        return name + " [" + type + "] " + fields();
    }
}
