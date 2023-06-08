package com.ujcms.commons.security;

import com.ujcms.commons.web.Strings;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.util.BigIntegers;
import org.springframework.lang.Nullable;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.Serializable;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * 安全工具类
 *
 * @author PONY
 */
public class Secures {
    /**
     * 获取下一个salt字符串
     */
    public static String nextSalt() {
        byte[] bytes = new byte[NEXT_SALT_SIZE];
        SECURE_RANDOM.nextBytes(bytes);
        return Strings.encodeBase64(bytes);
    }

    /**
     * 所有字符随机数
     */
    public static String random(final int count) {
        return RandomStringUtils.random(count, 0, 0, false, false, null, SECURE_RANDOM);
    }

    /**
     * Ascii码随机数
     */
    public static String randomAscii(final int count) {
        return RandomStringUtils.random(count, 32, 127, false, false, null, SECURE_RANDOM);
    }

    /**
     * 字母随机数
     */
    public static String randomAlphabetic(final int count) {
        return RandomStringUtils.random(count, 0, 0, true, false, null, SECURE_RANDOM);
    }

    /**
     * 数字随机数
     */
    public static String randomNumeric(final int count) {
        return RandomStringUtils.random(count, 0, 0, false, true, null, SECURE_RANDOM);
    }

    /**
     * 大写字母、小写字母、数字随机数
     */
    public static String randomAlphanumeric(final int count) {
        return RandomStringUtils.random(count, 0, 0, true, true, null, SECURE_RANDOM);
    }

    /**
     * 小写字母、数字随机数
     */
    public static String randomLowerCaseAlphanumeric(final int count) {
        return RandomStringUtils.random(count, '0', 'Z' + 1, true, true, null, SECURE_RANDOM).toLowerCase();
    }

    /**
     * 随机整数
     */
    public static int nextInt() {
        return SECURE_RANDOM.nextInt();
    }

    /**
     * 随机整数
     *
     * @param bound 最大值（不包含）
     * @return 0-bound(不含）之间的随机整数
     */
    public static int nextInt(int bound) {
        return SECURE_RANDOM.nextInt(bound);
    }

    /**
     * 随机长整数
     */
    public static long nextLong() {
        return SECURE_RANDOM.nextLong();
    }

    /**
     * 使用国密SM3算法对字符串进行散列
     */
    public static String sm3Hex(String data) {
        return sm3Hex(Strings.toBytes(data));
    }

    /**
     * 使用国密SM3算法对字符串进行散列
     */
    public static String sm3Hex(byte[] data) {
        return Strings.encodeHex(sm3(data));
    }

    /**
     * 使用国密SM3算法对字符串进行散列
     */
    public static byte[] sm3(String data) {
        return sm3(Strings.toBytes(data));
    }

    /**
     * 使用国密SM3算法对字符串进行散列
     */
    public static byte[] sm3(byte[] data) {
        return sm3(data, null, 0);
    }

    /**
     * 使用国密SM3算法对字符串进行散列
     */
    public static byte[] sm3(byte[] data, @Nullable byte[] salt, int iterations) {
        MessageDigest digest = getSm3Digest();
        if (salt != null && salt.length > 0) {
            digest.update(salt);
        }
        byte[] result = digest.digest(data);
        for (int i = 1; i < iterations; i++) {
            digest.reset();
            result = digest.digest(result);
        }
        return result;
    }

    /**
     * 国密SM3散列加密。256 bit，32 byte，64 Hex字符。
     */
    public static MessageDigest getSm3Digest() {
        try {
            return MessageDigest.getInstance(SM3_ALGORITHM, PROVIDER);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * 国密 SM4 加密。使用 SM4/GCM/NoPadding 算法。
     *
     * @param input 待加密数据
     * @param key   密钥。长度为 16 字符
     * @return 加密后数据。使用Base64转码。前 16 bytes 为 IV 值
     */
    public static String sm4Encrypt(String input, String key) {
        byte[] result = sm4Encrypt(Strings.toBytes(input), Strings.toBytes(key));
        return Strings.encodeBase64(result);
    }

    /**
     * 国密 SM4 解密。使用 SM4/GCM/NoPadding 算法。
     *
     * @param input 待解密数据。前 16 bytes 必须是 IV 值
     * @param key   密钥。长度为 16 字符
     * @return 解密后数据
     */
    public static String sm4Decrypt(String input, String key) throws BadPaddingException, IllegalBlockSizeException {
        byte[] result = sm4Decrypt(Strings.decodeBase64(input), Strings.toBytes(key));
        return Strings.toString(result);
    }

    /**
     * 国密 SM4 加密。使用 SM4/GCM/NoPadding 算法。
     *
     * @param input    待加密数据
     * @param keyBytes 密钥。长度为 16 bytes
     * @return 加密后数据。前 16 bytes 为 IV 值
     */
    public static byte[] sm4Encrypt(byte[] input, byte[] keyBytes) {
        try {
            byte[] ivBytes = Strings.toBytes(randomAlphanumeric(SM4_LENGTH));
            byte[] resultBytes = sm4(input, keyBytes, ivBytes, CipherMode.ENCRYPT);
            return ArrayUtils.addAll(ivBytes, resultBytes);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 国密 SM4 解密。使用 SM4/GCM/NoPadding 算法。
     *
     * @param input    待解密数据。前 16 bytes 必须是 IV 值
     * @param keyBytes 密钥。长度为 16 bytes
     * @return 解密后数据
     */
    public static byte[] sm4Decrypt(byte[] input, byte[] keyBytes)
            throws BadPaddingException, IllegalBlockSizeException {
        byte[] ivBytes = ArrayUtils.subarray(input, 0, SM4_LENGTH);
        byte[] data = ArrayUtils.subarray(input, SM4_LENGTH, input.length);
        return sm4(data, keyBytes, ivBytes, CipherMode.DECRYPT);
    }

    /**
     * 国密 SM4。密钥、分组、IV 都为 128 bit（16 byte）
     *
     * @param data     待加密/解密数据
     * @param keyBytes 密钥。长度为 16 bytes
     * @param ivBytes  IV向量。长度为 16 bytes
     * @param mode     模式。加密或解密
     * @return 加密/解密后数据
     */
    public static byte[] sm4(byte[] data, byte[] keyBytes, byte[] ivBytes, CipherMode mode)
            throws BadPaddingException, IllegalBlockSizeException {
        try {
            SecretKeySpec secret = new SecretKeySpec(keyBytes, SM4_ALGORITHM);
            Cipher cipher = Cipher.getInstance(SM4_GCM_NO_PADDING, PROVIDER);
            cipher.init(mode.value, secret, new IvParameterSpec(ivBytes));
            return cipher.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException
                | NoSuchPaddingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Cipher模式
     */
    public enum CipherMode {
        /**
         * 加密模式
         */
        ENCRYPT(Cipher.ENCRYPT_MODE),
        /**
         * 解密模式
         */
        DECRYPT(Cipher.DECRYPT_MODE);
        /**
         * Cipher对应的值
         */
        private final int value;

        CipherMode(int value) {
            this.value = value;
        }
    }

    /**
     * 生成SM2密钥对。为兼容JS端，使用QD值作为密钥格式。HEX编码格式。
     */
    public static Pair generateSm2QdKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(EC_ALGORITHM, PROVIDER);
            ECGenParameterSpec sm2Spec = new ECGenParameterSpec(SM2_DEFAULT_CURVE);
            keyPairGenerator.initialize(SM2_KEY_SIZE);
            keyPairGenerator.initialize(sm2Spec);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            String publicKey = Strings.encodeHex(((BCECPublicKey) keyPair.getPublic()).getQ().getEncoded(false));
            String privateKey = Strings.encodeHex(((BCECPrivateKey) keyPair.getPrivate()).getD().toByteArray());
            return new Pair(publicKey, privateKey);
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sm2Encrypt(String data, String publicKeyQd) {
        byte[] result = sm2Encrypt(Strings.toBytes(data), getPublicKeyParamByQd(publicKeyQd));
        return Strings.encodeBase64(result);
    }

    public static String sm2Decrypt(String data, String privateKeyQd) {
        byte[] result = sm2Decrypt(Strings.decodeBase64(data), getPrivateKeyParamByQd(privateKeyQd));
        return Strings.toString(result);
    }

    public static byte[] sm2Encrypt(byte[] data, AsymmetricKeyParameter publicKeyParam) {
        try {
            SM2Engine sm2Engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            sm2Engine.init(true, new ParametersWithRandom(publicKeyParam));
            return sm2Engine.processBlock(data, 0, data.length);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] sm2Decrypt(byte[] data, AsymmetricKeyParameter privateKeyParam) {
        try {
            SM2Engine sm2Engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            sm2Engine.init(false, privateKeyParam);
            return sm2Engine.processBlock(data, 0, data.length);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sm2Sign(String msg, String privateKeyQd) {
        return sm2Sign(null, msg, privateKeyQd);
    }

    public static String sm2Sign(@Nullable String id, String msg, String privateKeyQd) {
        byte[] result = sm2Sign(StringUtils.getBytesUtf8(id), StringUtils.getBytesUtf8(msg),
                getPrivateKeyParamByQd(privateKeyQd));
        return Strings.encodeHex(result);
    }

    public static byte[] sm2Sign(byte[] msgBytes, AsymmetricKeyParameter privateKeyParam) {
        return sm2Sign(null, msgBytes, privateKeyParam);
    }

    public static byte[] sm2Sign(@Nullable byte[] idBytes, byte[] msgBytes, AsymmetricKeyParameter privateKeyParam) {
        try {
            SM2Signer signer = new SM2Signer(new SM3Digest());
            CipherParameters param = new ParametersWithRandom(privateKeyParam);
            if (idBytes != null) {
                param = new ParametersWithID(param, idBytes);
            }
            signer.init(true, param);
            signer.update(msgBytes, 0, msgBytes.length);
            return signer.generateSignature();
        } catch (CryptoException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean sm2Verify(String msg, String sign, String publicKeyQd) {
        return sm2Verify(null, msg, sign, publicKeyQd);
    }

    public static boolean sm2Verify(@Nullable String id, String msg, String sign, String publicKeyQd) {
        return sm2Verify(id != null ? Strings.toBytes(id) : null, Strings.toBytes(msg),
                Strings.decodeHex(sign), getPublicKeyParamByQd(publicKeyQd));
    }

    public static boolean sm2Verify(byte[] msgBytes, byte[] signBytes, AsymmetricKeyParameter publicKeyParam) {
        return sm2Verify(null, msgBytes, signBytes, publicKeyParam);
    }

    public static boolean sm2Verify(@Nullable byte[] idBytes, byte[] msgBytes, byte[] signBytes,
                                    AsymmetricKeyParameter publicKeyParam) {
        SM2Signer signer = new SM2Signer(new SM3Digest());
        signer.init(false, idBytes != null ? new ParametersWithID(publicKeyParam, idBytes) : publicKeyParam);
        signer.update(msgBytes, 0, msgBytes.length);
        return signer.verifySignature(signBytes);
    }

    public static AsymmetricKeyParameter getPublicKeyParamByQd(String publicKeyQd) {
        return getPublicKeyParamByQd(Strings.decodeHex(publicKeyQd));
    }

    public static AsymmetricKeyParameter getPublicKeyParamByQd(byte[] publicKeyBytes) {
        ECCurve curve = SM2_DOMAIN_PARAMS.getCurve();
        return new ECPublicKeyParameters(curve.decodePoint(publicKeyBytes), SM2_DOMAIN_PARAMS);
    }

    public static AsymmetricKeyParameter getPrivateKeyParamByQd(String privateKeyQd) {
        return getPrivateKeyParamByQd(Strings.decodeHex(privateKeyQd));
    }

    public static AsymmetricKeyParameter getPrivateKeyParamByQd(byte[] privateKeyBytes) {
        return new ECPrivateKeyParameters(BigIntegers.fromUnsignedByteArray(privateKeyBytes), SM2_DOMAIN_PARAMS);
    }

    public static AsymmetricKeyParameter getPublicKeyParam(String publicKeyStr) {
        return getPublicKeyParam(Strings.decodeHex(publicKeyStr));
    }

    public static AsymmetricKeyParameter getPublicKeyParam(byte[] publicKeyBytes) {
        try {
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(EC_ALGORITHM, PROVIDER);
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
            return ECUtil.generatePublicKeyParameter(publicKey);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    public static AsymmetricKeyParameter getPrivateKeyParam(String privateKeyStr) {
        return getPrivateKeyParam(Strings.decodeHex(privateKeyStr));
    }

    public static AsymmetricKeyParameter getPrivateKeyParam(byte[] privateKeyBytes) {
        try {
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(EC_ALGORITHM, PROVIDER);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
            return ECUtil.generatePrivateKeyParameter(privateKey);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ECDomainParameters getEcDomainParameters() {
        X9ECParameters x9EcParameters = GMNamedCurves.getByName(SM2_DEFAULT_CURVE);
        return new ECDomainParameters(x9EcParameters.getCurve(),
                x9EcParameters.getG(), x9EcParameters.getN(), x9EcParameters.getH());
    }

    private static final ECDomainParameters SM2_DOMAIN_PARAMS = getEcDomainParameters();

    /**
     * 16字节是salt最低的要求
     */
    private static final int NEXT_SALT_SIZE = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    public static final String SM2_ALGORITHM = "SM2";
    public static final String EC_ALGORITHM = "EC";
    public static final String SM3_ALGORITHM = "SM3";
    public static final String SM4_ALGORITHM = "SM4";
    public static final String SM4_GCM_NO_PADDING = "SM4/GCM/NoPadding";
    public static final int SM4_LENGTH = 16;
    public static final String SM2_DEFAULT_CURVE = "sm2p256v1";
    public static final int SM2_KEY_SIZE = 256;
    protected static final Provider PROVIDER = new BouncyCastleProvider();

    public static class Pair implements Serializable {
        private static final long serialVersionUID = 3021409215717977144L;

        public Pair(String publicKey, String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        @Override
        public String toString() {
            return "Pair{" +
                    "publicKey='" + publicKey + '\'' +
                    ", privateKey='" + privateKey + '\'' +
                    '}';
        }

        public String getPublicKey() {
            return publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
        }


        private final String publicKey;
        private final String privateKey;
    }

}
