package com.tcts.exception;

/**
 * An exception thrown when email cannot be sent.
 */
public class EmailNotSentException extends Exception {
    public final String templateName;
    public final String toAddress;

    /**
     * Constructor.
     */
    public EmailNotSentException(String templateName, String toAddress, Throwable cause) {
        super("Failed to send email template '" + templateName + "' to address '" + toAddress + "'.", cause);
        this.templateName = templateName;
        this.toAddress = toAddress;
    }
}
