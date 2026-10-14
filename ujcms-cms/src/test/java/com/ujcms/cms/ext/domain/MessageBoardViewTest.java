package com.ujcms.cms.ext.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ujcms.common.web.Views;

/**
 * 验证留言联系方式字段的视图门控：
 * 公开列表/详情（{@code @JsonView(Views.List)}）不返回 联系人/电话/邮箱/地址/IP；
 * 后台（无视图，等价 Whole）仍可见。镜像生产的 {@code DEFAULT_VIEW_INCLUSION: true}。
 */
class MessageBoardViewTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .findAndAddModules()
            .configure(MapperFeature.DEFAULT_VIEW_INCLUSION, true).build();

    private MessageBoard sample() {
        MessageBoard mb = new MessageBoard();
        mb.setTitle("公开的留言标题");
        mb.setContact("张三");
        mb.setPhone("13800138000");
        mb.setEmail("zhangsan@example.com");
        mb.setAddress("北京市朝阳区某街道");
        mb.setIp("203.0.113.7");
        return mb;
    }

    @Test
    void publicViewHidesContactPii() throws Exception {
        String json = mapper.writerWithView(Views.List.class).writeValueAsString(sample());

        assertTrue(json.contains("公开的留言标题"), "公开内容（标题）应保留");
        assertFalse(json.contains("13800138000"), "电话不应泄露给匿名访客");
        assertFalse(json.contains("zhangsan@example.com"), "邮箱不应泄露");
        assertFalse(json.contains("张三"), "联系人不应泄露");
        assertFalse(json.contains("朝阳区"), "地址不应泄露");
        assertFalse(json.contains("203.0.113.7"), "IP 不应泄露");
        for (String key : new String[]{"\"contact\"", "\"phone\"", "\"email\"", "\"address\"", "\"ip\""}) {
            assertFalse(json.contains(key), "公开视图不应包含字段 " + key);
        }
    }

    @Test
    void adminViewKeepsContactPii() throws Exception {
        // 后台接口无 @JsonView，等价默认（Whole）序列化，管理员仍可见联系方式
        String json = mapper.writeValueAsString(sample());

        assertTrue(json.contains("13800138000"), "后台应能看到电话");
        assertTrue(json.contains("zhangsan@example.com"), "后台应能看到邮箱");
        assertTrue(json.contains("张三"), "后台应能看到联系人");
        assertTrue(json.contains("203.0.113.7"), "后台应能看到 IP");
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
