package com.ujcms.commons.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.impl.CriticalHeaderParamsDeferral;
import com.nimbusds.jose.crypto.impl.HMAC;
import com.nimbusds.jose.crypto.utils.ConstantTimeUtils;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.util.StandardCharset;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.Set;

import static com.ujcms.commons.security.jwt.JwtUtils.HMAC_SM3;

/**
 * @author PONY
 */
public class HmacSm3JwsVerifier implements JWSVerifier {
    public HmacSm3JwsVerifier(final byte[] secret) throws KeyLengthException {
        int minLength = 256 / 8;
        if (secret.length < minLength) {
            throw new KeyLengthException("The secret length must be at least 256 bits");
        }
        this.secret = secret;
    }

    public HmacSm3JwsVerifier(final String secretString) throws KeyLengthException {
        this(secretString.getBytes(StandardCharset.UTF_8));
    }

    @Override
    public boolean verify(JWSHeader header, byte[] signingInput, Base64URL signature) throws JOSEException {
        if (!critPolicy.headerPasses(header)) {
            return false;
        }
        byte[] expectedHmac = HMAC.compute(HMAC_SM3.getName(), getSecret(), signingInput, getJCAContext().getProvider());
        return ConstantTimeUtils.areEqual(expectedHmac, signature.decode());
    }

    @Override
    public Set<JWSAlgorithm> supportedJWSAlgorithms() {
        return Collections.singleton(HMAC_SM3);
    }

    @Override
    public JCAContext getJCAContext() {
        return new JCAContext(JwtUtils.PROVIDER, new SecureRandom());
    }

    /**
     * The critical header policy.
     */
    private final CriticalHeaderParamsDeferral critPolicy = new CriticalHeaderParamsDeferral();

    /**
     * The secret.
     */
    private final byte[] secret;

    public byte[] getSecret() {
        return secret;
    }

    public String getSecretString() {
        return new String(secret, StandardCharset.UTF_8);
    }
}
