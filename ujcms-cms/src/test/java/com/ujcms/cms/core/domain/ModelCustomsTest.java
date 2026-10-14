package com.ujcms.cms.core.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自定义字段值的拆装（docs/spec/model-management/plan.md）：清空后的桶也要落 {@code null}，旧列值不得残留；
 * 音频上传字段参与附件引用统计。
 */
class ModelCustomsTest {
    private static Model model(String customs) {
        Model model = new Model();
        model.setCustoms(customs);
        return model;
    }

    @Test
    void disassembleWritesNullForEmptiedBuckets() {
        Model model = model("[{\"code\":\"note\",\"name\":\"备注\",\"type\":\"text\"},"
                + "{\"code\":\"body\",\"name\":\"正文\",\"type\":\"richEditor\",\"clob\":true}]");
        AtomicReference<String> mains = new AtomicReference<>("{\"note\":\"old\"}");
        AtomicReference<String> clobs = new AtomicReference<>("{\"body\":\"<p>old</p>\"}");

        Map<String, Object> filled = new HashMap<>();
        filled.put("note", "new");
        model.disassembleMap(filled, mains::set, clobs::set);
        assertEquals("{\"note\":\"new\"}", mains.get());
        // 大字段桶没有值：旧列值被清掉，而不是留在库里等下次读取时复活
        assertNull(clobs.get());

        model.disassembleMap(Map.of(), mains::set, clobs::set);
        assertNull(mains.get());
        assertNull(clobs.get());
    }

    @Test
    void attachmentUrlsIncludeAudioAndMarkdown() {
        Model model = model("[{\"code\":\"audio\",\"name\":\"音频\",\"type\":\"audioUpload\"},"
                + "{\"code\":\"video\",\"name\":\"视频\",\"type\":\"videoUpload\"},"
                + "{\"code\":\"md\",\"name\":\"说明\",\"type\":\"markdownEditor\"},"
                + "{\"code\":\"note\",\"name\":\"备注\",\"type\":\"text\"}]");
        Map<String, Object> values = new HashMap<>();
        values.put("audio", "/uploads/a.mp3");
        values.put("video", "/uploads/v.mp4");
        values.put("md", "<p><img src=\"/uploads/i.png\"></p>");
        values.put("note", "/uploads/not-a-file.txt");
        List<String> urls = model.getUrlsFromMap(values);
        assertTrue(urls.contains("/uploads/a.mp3"), urls.toString());
        assertTrue(urls.contains("/uploads/v.mp4"), urls.toString());
        assertTrue(urls.contains("/uploads/i.png"), urls.toString());
        assertEquals(3, urls.size(), urls.toString());
    }

    @Test
    void fieldTypeVocabularyCoversEveryTypeConstant() {
        assertTrue(Model.FIELD_TYPES.contains(Model.TYPE_AUDIO_UPLOAD));
        assertTrue(Model.FIELD_TYPES.contains(Model.TYPE_MARKDOWN_EDITOR));
        assertTrue(Model.MODEL_TYPES.containsAll(List.of("article", "channel", "topic", "blockItem", "form", "user",
                "site", "global")));
        assertEquals(8, Model.MODEL_TYPES.size());
    }
}
