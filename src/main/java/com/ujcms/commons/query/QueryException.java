package com.ujcms.commons.query;

import java.io.Serial;

/**
 * 查询异常
 *
 * @author PONY
 */
public class QueryException extends RuntimeException {
    @Serial
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
