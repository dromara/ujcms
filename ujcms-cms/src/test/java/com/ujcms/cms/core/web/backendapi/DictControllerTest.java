package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.cms.core.service.DictService;
import com.ujcms.cms.core.service.DictTypeService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http403Exception;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 字典条目后台接口契约（dictionary plan P1）：{@code sys} 恒由骨架写入；系统条目 value/enabled
 * 锁定；typeId 不可改；同类型内 value 唯一；全局类型的条目需全局数据权限。
 * 站点/用户上下文经 {@link Contexts} ThreadLocal 注入（standalone MockMvc 同线程执行）。
 */
class DictControllerTest {
    private static final Long SITE = 1L;
    private static final String URL = "/api/backend/core/dict";

    private DictService service;
    private DictTypeService typeService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DictService.class);
        typeService = mock(DictTypeService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DictController(service, typeService)).build();
        Site site = new Site();
        site.setId(SITE);
        Contexts.setCurrentSite(site);
        globalUser(true);
        DictType globalType = new DictType();
        globalType.setId(3L);
        globalType.setScope(EntityConstants.SCOPE_GLOBAL);
        when(typeService.select(3L)).thenReturn(globalType);
        DictType siteType = new DictType();
        siteType.setId(4L);
        siteType.setSiteId(SITE);
        when(typeService.select(4L)).thenReturn(siteType);
    }

    @AfterEach
    void tearDown() {
        Contexts.clearCurrentSite();
        Contexts.clearCurrentUser();
    }

    private static void globalUser(boolean global) {
        User user = new User();
        user.setId(2L);
        Role role = new Role();
        role.setGlobalPermission(global);
        user.setRoleList(List.of(role));
        Contexts.setCurrentUser(user);
    }

    /** standalone MockMvc 无项目异常解析器：控制器异常以 ServletException 包裹抛出。 */
    private static void assertCause(Class<?> type, Executable executable) {
        Throwable thrown = assertThrows(Throwable.class, executable);
        Throwable cause = thrown;
        while (cause.getCause() != null && !type.isInstance(cause)) {
            cause = cause.getCause();
        }
        assertInstanceOf(type, cause);
    }

    @Test
    void createForcesSysFalseAndChecksValueUniqueness() throws Exception {
        when(service.existsByValue(3L, "pt-BR")).thenReturn(false);
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeId\":3,\"name\":\"葡萄牙语（巴西）\",\"value\":\"pt-BR\",\"sys\":true}"))
                .andExpect(status().isOk());
        ArgumentCaptor<Dict> captor = ArgumentCaptor.forClass(Dict.class);
        verify(service).insert(captor.capture());
        assertFalse(captor.getValue().getSys());
        assertEquals(3L, captor.getValue().getTypeId());
        assertEquals("pt-BR", captor.getValue().getValue());
    }

    @Test
    void createRejectsDuplicateValueWithinType() {
        when(service.existsByValue(3L, "en")).thenReturn(true);
        assertCause(Http400Exception.class, () -> mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeId\":3,\"name\":\"English\",\"value\":\"en\"}")));
        verify(service, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateKeepsValueEnabledTypeAndSysOnSysEntry() throws Exception {
        Dict stored = new Dict();
        stored.setId(9L);
        stored.setTypeId(3L);
        stored.setName("文章审核");
        stored.setValue("sys_article");
        stored.setEnabled(true);
        stored.setSys(true);
        when(service.select(9L)).thenReturn(stored);
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":9,\"typeId\":4,\"name\":\"改名\",\"value\":\"hacked\",\"enabled\":false,\"sys\":false}"))
                .andExpect(status().isOk());
        ArgumentCaptor<Dict> captor = ArgumentCaptor.forClass(Dict.class);
        verify(service).update(captor.capture());
        Dict saved = captor.getValue();
        assertEquals("改名", saved.getName());
        assertEquals("sys_article", saved.getValue());
        assertTrue(saved.getEnabled());
        assertTrue(saved.getSys());
        assertEquals(3L, saved.getTypeId());
        verify(service, never()).existsByValue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateAllowsValueChangeOnPlainEntryWhenUnique() throws Exception {
        Dict stored = new Dict();
        stored.setId(10L);
        stored.setTypeId(4L);
        stored.setValue("1");
        stored.setSys(false);
        when(service.select(10L)).thenReturn(stored);
        when(service.existsByValue(4L, "2")).thenReturn(false);
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":10,\"typeId\":3,\"name\":\"读者投稿\",\"value\":\"2\",\"sys\":true}"))
                .andExpect(status().isOk());
        ArgumentCaptor<Dict> captor = ArgumentCaptor.forClass(Dict.class);
        verify(service).update(captor.capture());
        assertEquals("2", captor.getValue().getValue());
        assertEquals(4L, captor.getValue().getTypeId(), "typeId is immutable");
        assertFalse(captor.getValue().getSys(), "sys is immutable");
    }

    /** 列表行内的启用开关回传整行：其 order 可能早于刚完成的拖拽排序，不得写回 */
    @Test
    void updateNeverCopiesOrder() throws Exception {
        Dict stored = new Dict();
        stored.setId(10L);
        stored.setTypeId(4L);
        stored.setValue("1");
        stored.setOrder(2);
        stored.setEnabled(true);
        stored.setSys(false);
        when(service.select(10L)).thenReturn(stored);
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":10,\"typeId\":4,\"name\":\"本站原创\",\"value\":\"1\",\"order\":7,\"enabled\":false}"))
                .andExpect(status().isOk());
        ArgumentCaptor<Dict> captor = ArgumentCaptor.forClass(Dict.class);
        verify(service).update(captor.capture());
        assertEquals(2, captor.getValue().getOrder(), "order changes only through the order endpoint");
        assertFalse(captor.getValue().getEnabled());
    }

    @Test
    void entriesOfGlobalTypeRequireGlobalPermission() {
        globalUser(false);
        assertCause(Http403Exception.class, () -> mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeId\":3,\"name\":\"x\",\"value\":\"x\"}")));
        verify(service, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void entriesOfSiteTypeNeedNoGlobalPermission() throws Exception {
        globalUser(false);
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeId\":4,\"name\":\"x\",\"value\":\"x\"}"))
                .andExpect(status().isOk());
        verify(service).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void valueExistEndpoint() throws Exception {
        when(service.existsByValue(3L, "en")).thenReturn(true);
        mvc.perform(get(URL + "/value-exist").param("typeId", "3").param("value", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }
}
