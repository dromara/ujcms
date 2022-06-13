package com.ujcms.util.security;

import org.springframework.lang.Nullable;

/**
 * 现代加密算法<code>PBKDF2WithHmacSHA512</code>
 *
 * @author PONY
 */
public class Pbkdf2WithHmacSha512Digest extends Pbkdf2Digest {
    public static final String ALGORITHM = "PBKDF2WithHmacSHA512";

    public Pbkdf2WithHmacSha512Digest() {
        this(null);
    }

    public Pbkdf2WithHmacSha512Digest(@Nullable String pepper) {
        super(ALGORITHM, pepper);
    }
}
