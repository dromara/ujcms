/*
 * nimbus-jose-jwt
 *
 * Copyright 2012-2016, Connect2id Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
/*
 * Modified for UJCMS by 南昌蓝智科技有限公司 (https://www.ujcms.com). Derived from Nimbus JOSE + JWT:
 * com.nimbusds.jose.crypto.MACVerifier and com.nimbusds.jose.crypto.impl.MACProvider
 * (Copyright 2012-2023, Connect2id Ltd and contributors).
 * First modified before 2026-04-19, the start of this repository's history; later changes are in the VCS log.
 */

package com.ujcms.common.security.jwt;

import static com.ujcms.common.security.jwt.JwtUtils.HMAC_SM3;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.Set;

import javax.crypto.spec.SecretKeySpec;

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

/**
 * 基于 Nimbus JOSE + JWT 的 MACVerifier 修改。
 *
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
        String algorithm = HMAC_SM3.getName();
        SecretKeySpec secretKeySpec = new SecretKeySpec(getSecret(), algorithm);
        byte[] expectedHmac = HMAC.compute(algorithm, secretKeySpec, signingInput, getJCAContext().getProvider());
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
