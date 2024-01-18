package org.eclipse.pass.support.grant.data;

/**
 * General checked exception to be used while processing grant data loading.
 */
public class GrantDataException extends Exception {

    /**
     * Class constructor.
     * @param message error message
     */
    public GrantDataException(String message) {
        super(message);
    }
}
