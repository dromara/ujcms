package com.ujcms.common.captcha;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试用的验证码缓存：没有 Spring 缓存代理时 {@link CaptchaCache} 的注解不生效（永远返回 0），
 * 这里用 Map 实现一次性消费与尝试计数。
 */
class MapCaptchaCache extends CaptchaCache {
    private final Map<String, Integer> map = new HashMap<>();

    @Override
    public int getAttempts(String sign) {
        return map.getOrDefault(sign, 0);
    }

    @Override
    public int updateAttempts(String sign, int count) {
        map.put(sign, count);
        return count;
    }
}
