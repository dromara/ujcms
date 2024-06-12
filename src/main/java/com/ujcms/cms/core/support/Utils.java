package com.ujcms.cms.core.support;

import com.ujcms.commons.security.Secures;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * 工具类
 *
 * @author PONY
 */
public class Utils {
    /**
     * 获取下载KEY，用于校验下载连接是否在有效期内，防止盗链
     *
     * @param id     文章ID
     * @param time   时间
     * @param secret 密钥
     * @return 使用 SM3 对 secret.id.time 杂凑
     */
    public static String getDownloadKey(Long id, long time, String secret) {
        return Secures.sm3Hex(secret + "." + id + "." + time);
    }

    /**
     * 校验下载KEY是否合法
     *
     * @param key    下载KEY
     * @param id     文章ID
     * @param time   时间
     * @param secret 密钥
     * @return 是否合法
     */
    public static boolean validateDownloadKey(String key, Long id, long time, @Nullable String secret) {
        if (StringUtils.isBlank(secret)) {
            return false;
        }
        return StringUtils.equals(key, getDownloadKey(id, time, secret));
    }

    public static void boot() {
        String epBoot = System.getProperty("ujcms.boot");
        if (!Boolean.FALSE.toString().equals(epBoot)) {
            try {
                Class.forName("com.ujcms.cms.commercial.r1.a.Aa").getMethod("a").invoke(null);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private Utils() {
    }
}
