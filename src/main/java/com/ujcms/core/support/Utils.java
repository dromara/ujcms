package com.ujcms.core.support;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * 工具类
 *
 * @author PONY
 */
public class Utils {
    public static String getDownloadKey(Integer id, long time, String secret) {
        return DigestUtils.sha256Hex(id + "," + time + "," + secret);
    }

    public static boolean validateDownloadKey(String key, Integer id, long time, @Nullable String secret) {
        if (StringUtils.isBlank(secret)) {
            return false;
        }
        return StringUtils.equals(key, DigestUtils.sha256Hex(id + "," + time + "," + secret));
    }

    public static void boot() {
        String epBoot = System.getProperty("ujcms.boot");
        if (!Boolean.FALSE.toString().equals(epBoot)) {
            try {
                Class.forName("com.ujcms.commercial.r1.a.Aa").getMethod("a").invoke(null);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private Utils() {
    }
}
