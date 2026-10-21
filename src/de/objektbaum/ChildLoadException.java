package de.objektbaum;

/** Wird geworfen, wenn ein {@link ChildLoader} beim Laden scheitert. */
public class ChildLoadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ObjectNode node;

    public ChildLoadException(ObjectNode node, Throwable cause) {
        super("Unterobjekte von '" + node.name() + "' konnten nicht geladen werden: " + cause.getMessage(), cause);
        this.node = node;
    }

    public ObjectNode node() {
        return node;
    }
}
