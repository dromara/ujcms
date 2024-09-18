package com.ujcms.cms.core.web.support;

import com.ujcms.commons.web.exception.Http503Exception;
import org.springframework.lang.Nullable;

/**
 * 站点禁用异常
 *
 * @author PONY
 */
public class SiteDisabledException extends Http503Exception {
    private static final long serialVersionUID = 1L;

    public SiteDisabledException() {
        super("Site disabled.");
    }

    public SiteDisabledException(String code, @Nullable String... args) {
        super(code, args);
    }
}
