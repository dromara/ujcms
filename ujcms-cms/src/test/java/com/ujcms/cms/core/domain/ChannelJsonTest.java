package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 栏目请求体的反序列化（docs/spec/channel-management/plan.md）。控制台把栏目详情原样回传：其中栏目模型、文章模型带着
 * 只读的字段定义列表 {@code fieldList}。模型定义了自定义字段时该列表非空，曾使整个请求以 JSON 解析错误（400）告终——
 * 用了带自定义字段的模型的栏目因此无法保存。
 */
class ChannelJsonTest {
    /**
     * 与 Spring Boot 默认一致：忽略未知属性
     */
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void channelPostedBackWithModelFieldListsIsReadable() throws Exception {
        String json = """
                {"id":"5","name":"体育","slug":"sport",
                 "channelModel":{"id":"1","name":"Section",
                   "fieldList":[{"code":"subtitle","name":"栏目副标题","type":"text"}]},
                 "articleModel":{"id":"2","name":"Story",
                   "fieldList":[{"code":"price","name":"价格","type":"number"}]},
                 "customs":{"subtitle":"赛事"}}
                """;

        Channel channel = mapper.readValue(json, Channel.class);

        assertEquals("体育", channel.getName());
        assertEquals("sport", channel.getSlug());
        assertEquals("赛事", channel.getCustoms().get("subtitle"));
    }

    @Test
    void modelStillExposesFieldListInResponses() throws Exception {
        Model model = new Model();
        model.setCustoms("[{\"code\":\"subtitle\",\"name\":\"栏目副标题\",\"type\":\"text\"}]");

        String json = mapper.writeValueAsString(model);

        assertTrue(json.contains("\"fieldList\":[{\"code\":\"subtitle\""), json);
        assertEquals(1, model.getFieldList().size());
    }
}
