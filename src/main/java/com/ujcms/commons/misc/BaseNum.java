package com.ujcms.commons.misc;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author PONY
 */
public class BaseNum {
    private static final int STANDARD_BASE = 256;

    private final int targetBase;

    private final byte[] alphabet;

    private byte[] lookup;

    private BaseNum(final byte[] alphabet) {
        this.alphabet = alphabet;
        this.targetBase = this.alphabet.length;
        createLookupTable();
    }

    /**
     * 创建Base62一个实例
     */
    public static BaseNum base62() {
        return new BaseNum(BASE_62);
    }

    /**
     * 创建Base62一个实例
     */
    public static BaseNum base36() {
        return new BaseNum(BASE_36);
    }

    /**
     * 编码
     *
     * @param src 待编码的字节数组
     * @return 编码后的字节数组
     */
    public byte[] encode(final byte[] src) {
        final byte[] indices = convert(src, STANDARD_BASE, targetBase);
        return translate(indices, alphabet);
    }

    public String encodeToString(final byte[] src) {
        return new String(encode(src), UTF_8);
    }

    /**
     * 解码
     *
     * @param src 待解码的字节数组
     * @return 原始字节数组
     */
    public byte[] decode(final byte[] src) {
        if (!isBase62Encoding(src)) {
            throw new IllegalArgumentException("Input is not encoded correctly");
        }
        final byte[] prepared = translate(src, lookup);
        return convert(prepared, targetBase, STANDARD_BASE);
    }

    public byte[] decode(final String src) {
        return decode(src.getBytes(UTF_8));
    }

    /**
     * 是否为base62编码的字节数组
     *
     * @param bytes 待判断的字节数组
     */
    public static boolean isBase62Encoding(final byte[] bytes) {
        if (bytes == null) {
            return false;
        }
        for (final byte e : bytes) {
            if ('0' > e || '9' < e) {
                if ('a' > e || 'z' < e) {
                    if ('A' > e || 'Z' < e) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * 是否为base36编码的字节数组
     *
     * @param bytes 待判断的字节数组
     */
    public static boolean isBase36Encoding(final byte[] bytes) {
        if (bytes == null) {
            return false;
        }
        for (final byte e : bytes) {
            if ('0' > e || '9' < e) {
                if ('a' > e || 'z' < e) {
                    return false;
                }
            }
        }
        return true;
    }

    private byte[] translate(final byte[] indices, final byte[] dictionary) {
        final byte[] translation = new byte[indices.length];
        for (int i = 0; i < indices.length; i++) {
            translation[i] = dictionary[indices[i]];
        }
        return translation;
    }

    private byte[] convert(final byte[] message, final int sourceBase, final int targetBase) {
        // 算法：http://codegolf.stackexchange.com/a/21672
        final int estimatedLength = estimateOutputLength(message.length, sourceBase, targetBase);
        final ByteArrayOutputStream out = new ByteArrayOutputStream(estimatedLength);
        byte[] source = message;
        while (source.length > 0) {
            final ByteArrayOutputStream quotient = new ByteArrayOutputStream(source.length);
            int remainder = 0;
            for (byte b : source) {
                final int accumulator = (b & 0xFF) + remainder * sourceBase;
                final int digit = (accumulator - (accumulator % targetBase)) / targetBase;
                remainder = accumulator % targetBase;
                if (quotient.size() > 0 || digit > 0) {
                    quotient.write(digit);
                }
            }
            out.write(remainder);
            source = quotient.toByteArray();
        }
        // pad output with zeroes corresponding to the number of leading zeroes in the message
        for (int i = 0; i < message.length - 1 && message[i] == 0; i++) {
            out.write(0);
        }
        return reverse(out.toByteArray());
    }

    private int estimateOutputLength(int inputLength, int sourceBase, int targetBase) {
        return (int) Math.ceil((Math.log(sourceBase) / Math.log(targetBase)) * inputLength);
    }

    private byte[] reverse(final byte[] arr) {
        final int length = arr.length;
        final byte[] reversed = new byte[length];
        for (int i = 0; i < length; i++) {
            reversed[length - i - 1] = arr[i];
        }
        return reversed;
    }

    private void createLookupTable() {
        lookup = new byte[256];
        for (int i = 0; i < alphabet.length; i++) {
            lookup[alphabet[i]] = (byte) (i & 0xFF);
        }
    }

    private static final byte[] BASE_62 = {
            (byte) '0', (byte) '1', (byte) '2', (byte) '3', (byte) '4', (byte) '5', (byte) '6', (byte) '7',
            (byte) '8', (byte) '9', (byte) 'A', (byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F',
            (byte) 'G', (byte) 'H', (byte) 'I', (byte) 'J', (byte) 'K', (byte) 'L', (byte) 'M', (byte) 'N',
            (byte) 'O', (byte) 'P', (byte) 'Q', (byte) 'R', (byte) 'S', (byte) 'T', (byte) 'U', (byte) 'V',
            (byte) 'W', (byte) 'X', (byte) 'Y', (byte) 'Z', (byte) 'a', (byte) 'b', (byte) 'c', (byte) 'd',
            (byte) 'e', (byte) 'f', (byte) 'g', (byte) 'h', (byte) 'i', (byte) 'j', (byte) 'k', (byte) 'l',
            (byte) 'm', (byte) 'n', (byte) 'o', (byte) 'p', (byte) 'q', (byte) 'r', (byte) 's', (byte) 't',
            (byte) 'u', (byte) 'v', (byte) 'w', (byte) 'x', (byte) 'y', (byte) 'z'
    };
    private static final byte[] BASE_36 = {
            (byte) '0', (byte) '1', (byte) '2', (byte) '3', (byte) '4', (byte) '5', (byte) '6', (byte) '7',
            (byte) '8', (byte) '9', (byte) 'a', (byte) 'b', (byte) 'c', (byte) 'd', (byte) 'e', (byte) 'f',
            (byte) 'g', (byte) 'h', (byte) 'i', (byte) 'j', (byte) 'k', (byte) 'l', (byte) 'm', (byte) 'n',
            (byte) 'o', (byte) 'p', (byte) 'q', (byte) 'r', (byte) 's', (byte) 't', (byte) 'u', (byte) 'v',
            (byte) 'w', (byte) 'x', (byte) 'y', (byte) 'z'
    };

    private static byte[] intToBytes(int num) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byteBuffer.putInt(num);
        return byteBuffer.array();
    }

    private static int bytesToInt(byte[] bytes) {
        ByteBuffer wrapped = ByteBuffer.wrap(bytes);
        return wrapped.getInt();
    }

    private static byte[] longToBytes(long num) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(8);
        byteBuffer.putLong(num);
        return byteBuffer.array();
    }

    private static long bytesToLong(byte[] bytes) {
        ByteBuffer wrapped = ByteBuffer.wrap(bytes);
        return wrapped.getLong();
    }

    public String fromInt(int num) {
        byte[] bytes = intToBytes(num);
        return encodeToString(bytes);
    }

    public int toInt(String s) {
        byte[] bytes = decode(s);
        return bytesToInt(bytes);
    }

    public String fromReverseInt(int num) {
        return fromInt(Integer.reverseBytes(num));
    }

    public int toReverseInt(String s) {
        return Integer.reverseBytes(toInt(s));
    }

    public String fromLong(long num) {
        byte[] bytes = longToBytes(num);
        return encodeToString(bytes);
    }

    public long toLong(String s) {
        byte[] bytes = decode(s);
        return bytesToLong(bytes);
    }

    public String fromReverseLong(long num) {
        return fromLong(Long.reverseBytes(num));
    }

    public long toReverseLong(String s) {
        return Long.reverseBytes(toLong(s));
    }
}
