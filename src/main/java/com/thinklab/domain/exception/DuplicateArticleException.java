package com.thinklab.domain.exception;

/**
 * Domain Exception: Thrown when an Article is initiated with a serial number that already exists
 * within the same Organisation scope.
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict.
 */
public class DuplicateArticleException extends BusinessException {

    private static final String ERROR_CODE = "ERR-KNB-00409";

    public DuplicateArticleException(String message) {
        super(ERROR_CODE, message);
    }
}
