package org.jahia.community.translation.deepl.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MultiGlossaryManagementException extends Exception {

    private static final Logger logger = LoggerFactory.getLogger(MultiGlossaryManagementException.class);

    private final List<Throwable> wrappedErrors;

    public MultiGlossaryManagementException(Throwable wrappedError) {
        wrappedErrors = new ArrayList<>();
        addError(wrappedError);
    }

    public void addError(Throwable wrappedError) {
        wrappedErrors.add(wrappedError);
    }

    public List<Throwable> getWrappedErrors() {
        return Collections.unmodifiableList(wrappedErrors);
    }
}
