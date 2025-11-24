package com.ujcms.commons.sms;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.xml.bind.DatatypeConverter;

/**
 * 短信工具类
 *
 * @author PONY
 */
public class SmsUtils {    
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    public static byte[] hmac256(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, mac.getAlgorithm());
            mac.init(secretKeySpec);
            return mac.doFinal(message.getBytes(UTF8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Invalid key for HMAC", e);
        }
    }

    public static String sha256Hex(String str) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = messageDigest.digest(str.getBytes(UTF8));
            return DatatypeConverter.printHexBinary(bytes).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
    
    private SmsUtils() {
        throw new IllegalStateException("Utility class");
    }

}
