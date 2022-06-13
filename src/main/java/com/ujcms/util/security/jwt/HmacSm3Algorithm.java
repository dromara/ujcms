package com.ujcms.util.security.jwt;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.SignatureGenerationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ujcms.util.security.Secures;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author PONY
 */
public class HmacSm3Algorithm extends Algorithm {
    public HmacSm3Algorithm(String secret) {
        super("SM3", ALGORITHMS);
        if (secret == null) {
            throw new IllegalArgumentException("The Secret cannot be null");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void verify(DecodedJWT jwt) throws SignatureVerificationException {
        byte[] signatureBytes = Base64.decodeBase64(jwt.getSignature());
        try {
            byte[] headerBytes = jwt.getHeader().getBytes(StandardCharsets.UTF_8);
            byte[] payloadBytes = jwt.getPayload().getBytes(StandardCharsets.UTF_8);
            boolean valid = MessageDigest.isEqual(sign(headerBytes, payloadBytes), signatureBytes);
            if (!valid) {
                throw new SignatureVerificationException(this);
            }
        } catch (IllegalStateException e) {
            throw new SignatureVerificationException(this, e);
        }

    }

    @Override
    public byte[] sign(byte[] headerBytes, byte[] payloadBytes) throws SignatureGenerationException {
        try {
            final Mac mac = getHmacSm3(secretBytes);
            mac.update(headerBytes);
            mac.update(JWT_PART_SEPARATOR);
            return mac.doFinal(payloadBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SignatureGenerationException(this, e);
        }
    }

    @Override
    @Deprecated
    public byte[] sign(byte[] contentBytes) throws SignatureGenerationException {
        try {
            return getHmacSm3(secretBytes).doFinal(contentBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SignatureGenerationException(this, e);
        }
    }

    public static Mac getHmacSm3(byte[] secretBytes) throws NoSuchAlgorithmException, InvalidKeyException {
        final Mac mac = Mac.getInstance(ALGORITHMS, Secures.PROVIDER);
        mac.init(new SecretKeySpec(secretBytes, ALGORITHMS));
        return mac;
    }

    public static final String ALGORITHMS = "HmacSM3";
    private static final byte JWT_PART_SEPARATOR = (byte) '.';
    private final byte[] secretBytes;
}
