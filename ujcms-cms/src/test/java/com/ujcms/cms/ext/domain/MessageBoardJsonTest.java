package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ujcms.common.web.Views;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 留言隐私字段（IP、联系方式）只在后台接口输出
 *
 * @author PONY
 */
class MessageBoardJsonTest {
    private static final List<String> PRIVATE_FIELDS = List.of("ip", "contact", "phone", "email", "address");
    /**
     * 与 application.yaml 的 spring.jackson.mapper.DEFAULT_VIEW_INCLUSION: true 保持一致
     */
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(MapperFeature.DEFAULT_VIEW_INCLUSION).addModule(new JavaTimeModule()).build();

    private static MessageBoard createBean() {
        MessageBoard bean = new MessageBoard();
        bean.setTitle("title");
        bean.setIp("127.0.0.1");
        bean.setContact("contact");
        bean.setPhone("13800000000");
        bean.setEmail("test@example.com");
        bean.setAddress("address");
        return bean;
    }

    @Test
    void frontendViewsExcludePrivateFields() throws Exception {
        for (Class<?> view : List.of(Views.List.class, Views.Whole.class)) {
            JsonNode json = mapper.readTree(mapper.writerWithView(view).writeValueAsString(createBean()));
            assertEquals("title", json.get("title").asText());
            for (String field : PRIVATE_FIELDS) {
                assertFalse(json.has(field), view.getSimpleName() + " should not contain " + field);
            }
        }
    }

    @Test
    void backendIncludesPrivateFields() throws Exception {
        // 后台接口不使用 JsonView
        JsonNode json = mapper.readTree(mapper.writeValueAsString(createBean()));
        for (String field : PRIVATE_FIELDS) {
            assertTrue(json.has(field), "backend should contain " + field);
        }
    }

    @Test
    void privateFieldsCanBeSubmitted() throws Exception {
        // 前台提交留言时，联系方式仍可正常反序列化
        MessageBoard bean = mapper.readValue(
                "{\"title\":\"t\",\"phone\":\"13800000000\",\"email\":\"test@example.com\"}", MessageBoard.class);
        assertEquals("13800000000", bean.getPhone());
        assertEquals("test@example.com", bean.getEmail());
    }

    @Test
    void displayableRequiresAuditedAndOpen() {
        MessageBoard bean = new MessageBoard();
        assertFalse(bean.isDisplayable(), "default status is unreviewed");
        bean.setStatus(MessageBoard.STATUS_AUDITED);
        assertTrue(bean.isDisplayable());
        bean.setOpen(false);
        assertFalse(bean.isDisplayable());
        bean.setOpen(true);
        bean.setStatus(MessageBoard.STATUS_BLOCKED);
        assertFalse(bean.isDisplayable());
    }
}
