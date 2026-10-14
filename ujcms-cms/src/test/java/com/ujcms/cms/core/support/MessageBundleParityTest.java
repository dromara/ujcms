package com.ujcms.cms.core.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后端消息包一致性（docs/spec/console-i18n/plan.md）：{@code messages.properties}（中文基础包）与
 * {@code messages_en.properties} 键集必须完全一致，英文包不得残留中文。控制台切换为英文时按
 * {@code Accept-Language} 取英文包，缺键会静默回落到中文——本测试让缺键在构建期暴露。
 */
class MessageBundleParityTest {

    private static final Pattern HAN = Pattern.compile("\\p{IsHan}");

    private static Properties load(String name) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessageBundleParityTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(in, name + " not found on the classpath");
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    void englishBundleMirrorsBaseBundle() throws IOException {
        Properties base = load("messages.properties");
        Properties en = load("messages_en.properties");
        assertEquals(new TreeSet<>(base.stringPropertyNames()), new TreeSet<>(en.stringPropertyNames()),
                "messages_en.properties must define exactly the keys of messages.properties");

        List<String> chinese = new ArrayList<>();
        List<String> empty = new ArrayList<>();
        for (String key : en.stringPropertyNames()) {
            String value = en.getProperty(key);
            if (value.isBlank()) {
                empty.add(key);
            } else if (HAN.matcher(value).find()) {
                chinese.add(key);
            }
        }
        assertTrue(chinese.isEmpty(), "messages_en.properties still contains Chinese: " + chinese);
        assertTrue(empty.isEmpty(), "messages_en.properties has empty values: " + empty);
    }
}
