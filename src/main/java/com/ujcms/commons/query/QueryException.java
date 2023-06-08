package com.ujcms.commons.query;

/**
 * 查询异常
 *
 * @author PONY
 */
public class QueryException extends RuntimeException {
    private static final long serialVersionUID = -4695441995551168903L;

    public QueryException() {
    }

    public QueryException(String message) {
        super(message);
    }

    public QueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
