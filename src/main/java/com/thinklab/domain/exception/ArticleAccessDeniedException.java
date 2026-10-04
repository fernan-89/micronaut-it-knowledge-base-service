package com.thinklab.domain.exception;

/**
 * Domain Exception: a {@code REQUESTER} tried to do something only staff may do (ADR-032): write, submit, review, publish, retire or
 * version an article, or read its audit trail. A requester reads the published public articles and nothing else.
 *
 * <p>RFC 7807 mapping: HTTP 403 Forbidden.
 */
public class ArticleAccessDeniedException extends BusinessException {

    private static final String ERROR_CODE = "ERR-KNB-00403";

    public ArticleAccessDeniedException(String operation) {
        super(ERROR_CODE, "A requester cannot " + operation + ": that is a staff action.");
    }
}
