package org.jahia.community.translation.deepl.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlossaryManagementException extends Exception {

    private static final Logger logger = LoggerFactory.getLogger(GlossaryManagementException.class);

    public GlossaryManagementException() {
        super();
    }

    public GlossaryManagementException(String message) {
        super(message);
    }

    public GlossaryManagementException(String message, Throwable cause) {
        super(message, cause);
    }

    public GlossaryManagementException(Throwable cause) {
        super(cause);
    }

    protected GlossaryManagementException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
