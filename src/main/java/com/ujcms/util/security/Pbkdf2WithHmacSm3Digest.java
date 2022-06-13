package com.ujcms.util.security;

import org.springframework.lang.Nullable;

/**
 * 现代加密算法 国密<code>PBKDF2WithHmacSM3</code>
 *
 * @author PONY
 */
public class Pbkdf2WithHmacSm3Digest extends Pbkdf2Digest {
    public static final String ALGORITHM = "PBKDF2WithHmacSM3";

    public Pbkdf2WithHmacSm3Digest() {
        this(null);
    }

    public Pbkdf2WithHmacSm3Digest(@Nullable String pepper) {
        super(ALGORITHM, Secures.PROVIDER, pepper);
    }
}
