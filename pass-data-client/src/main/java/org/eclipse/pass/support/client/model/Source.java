package org.eclipse.pass.support.client.model;

/**
 * Source of the Submission, from a PASS user or imported from another source
 */
public enum Source {

    /**
     * PASS source
     */
    PASS("pass"),

    /**
     * Other source
     */
    OTHER("other");

    private String value;

    private Source(String value) {
        this.value = value;
    }

    /**
     * Parse performer role
     *
     * @param s status string
     * @return parsed source
     */
    public static Source of(String s) {
        for (Source o: Source.values()) {
            if (o.value.equals(s)) {
                return o;
            }
        }

        throw new IllegalArgumentException("Invalid performer role: " + s);
    }

    /**
     * @return public value
     */
    public String getValue() {
        return value;
    }
}