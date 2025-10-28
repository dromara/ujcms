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

    public static byte[] hmac256(byte[] key, String message) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, mac.getAlgorithm());
        mac.init(secretKeySpec);
        return mac.doFinal(message.getBytes(UTF8));
    }

    public static String sha256Hex(String str) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = messageDigest.digest(str.getBytes(UTF8));
        return DatatypeConverter.printHexBinary(bytes).toLowerCase();
    }
    private SmsUtils() {
        throw new IllegalStateException("Utility class");
    }

}
