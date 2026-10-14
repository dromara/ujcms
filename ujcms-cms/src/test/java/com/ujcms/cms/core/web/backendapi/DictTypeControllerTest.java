package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.cms.core.service.DictTypeService;
import com.ujcms.cms.core.service.DictTypeService.Binding;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 字典类型后台接口契约：别名唯一性以“别名 × 共享范围”为命名空间；{@code order} 只经排序接口改写；
 * 排序按共享范围分组提交——只含本站类型的一组无需全局数据权限；系统类型的 alias/scope 锁定；
 * 全局类型被站外模型绑定时不可改为本站私有。
 * 站点/用户上下文经 {@link Contexts} ThreadLocal 注入（standalone MockMvc 同线程执行）。
 */
class DictTypeControllerTest {
    private static final Long SITE = 1L;
    private static final String URL = "/api/backend/core/dict-type";

    private DictTypeService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DictTypeService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DictTypeController(service)).build();
        Site site = new Site();
        site.setId(SITE);
        Contexts.setCurrentSite(site);
        globalUser(true);
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

    private DictType globalType(long id, String alias) {
        DictType bean = new DictType();
        bean.setId(id);
        bean.setName("全局类型");
        bean.setAlias(alias);
        bean.setScope(EntityConstants.SCOPE_GLOBAL);
        when(service.select(id)).thenReturn(bean);
        return bean;
    }

    private DictType siteType(long id, String alias) {
        DictType bean = new DictType();
        bean.setId(id);
        bean.setSiteId(SITE);
        bean.setName("本站类型");
        bean.setAlias(alias);
        when(service.select(id)).thenReturn(bean);
        return bean;
    }

    private static Binding binding(@SuppressWarnings("SameParameterValue") String modelName, Long siteId, boolean global) {
        Model model = new Model();
        model.setId(7L);
        model.setName(modelName);
        model.setSiteId(siteId);
        model.setScope(global ? EntityConstants.SCOPE_GLOBAL : EntityConstants.SCOPE_PRIVATE);
        Model.Field field = new Model.Field();
        field.setCode("brand");
        field.setName("品牌");
        return new Binding(model, field);
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
    void updateRechecksAliasWhenOnlyScopeChanges() {
        siteType(4L, "color");
        when(service.existsByAlias("color", null)).thenReturn(true);
        assertCause(Http400Exception.class, () -> mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":4,\"name\":\"颜色\",\"alias\":\"color\",\"scope\":2}")));
        verify(service, never()).update(any(), any());
    }

    @Test
    void updateSkipsAliasCheckWhenAliasAndScopeUnchanged() throws Exception {
        siteType(4L, "color");
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":4,\"name\":\"颜色\",\"alias\":\"color\",\"scope\":0}"))
                .andExpect(status().isOk());
        verify(service, never()).existsByAlias(any(), any());
        verify(service).update(any(), any());
    }

    @Test
    void updateNeverCopiesOrder() throws Exception {
        siteType(4L, "color").setOrder(3);
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":4,\"name\":\"颜色\",\"alias\":\"color\",\"scope\":0,\"order\":99}"))
                .andExpect(status().isOk());
        ArgumentCaptor<DictType> captor = ArgumentCaptor.forClass(DictType.class);
        verify(service).update(captor.capture(), any());
        assertEquals(3, captor.getValue().getOrder(), "order changes only through the order endpoint");
    }

    @Test
    void sysTypeKeepsAliasAndScope() throws Exception {
        globalType(3L, "sys_site_lang").setSys(true);
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":3,\"name\":\"语言\",\"alias\":\"hacked\",\"scope\":0,\"sys\":false}"))
                .andExpect(status().isOk());
        ArgumentCaptor<DictType> captor = ArgumentCaptor.forClass(DictType.class);
        verify(service).update(captor.capture(), any());
        DictType saved = captor.getValue();
        assertEquals("语言", saved.getName());
        assertEquals("sys_site_lang", saved.getAlias());
        assertEquals(EntityConstants.SCOPE_GLOBAL, saved.getScope());
        assertEquals(Boolean.TRUE, saved.getSys());
    }

    @Test
    void orderOfSiteTypesNeedsNoGlobalPermission() throws Exception {
        globalUser(false);
        siteType(4L, "color");
        siteType(5L, "size");
        mvc.perform(put(URL + "/order").contentType(MediaType.APPLICATION_JSON).content("[5,4]"))
                .andExpect(status().isOk());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DictType>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).updateOrder(captor.capture());
        assertEquals(List.of(5L, 4L), captor.getValue().stream().map(DictType::getId).toList());
    }

    @Test
    void orderContainingGlobalTypeRequiresGlobalPermission() {
        globalUser(false);
        globalType(3L, "sys_site_lang");
        siteType(4L, "color");
        assertCause(Http403Exception.class, () -> mvc.perform(put(URL + "/order")
                .contentType(MediaType.APPLICATION_JSON).content("[3,4]")));
        verify(service, never()).updateOrder(any());
    }

    @Test
    void globalToPrivateRefusedWhileBoundOutsideTheSite() {
        globalType(3L, "brand");
        when(service.listBindings(3L)).thenReturn(List.of(binding("产品", 2L, false)));
        assertCause(Http400Exception.class, () -> mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":3,\"name\":\"品牌\",\"alias\":\"brand\",\"scope\":0}")));
        verify(service, never()).update(any(), any());
    }

    @Test
    void globalToPrivateAllowedWhenOnlyThisSiteBinds() throws Exception {
        globalType(3L, "brand");
        when(service.listBindings(3L)).thenReturn(List.of(binding("产品", SITE, false)));
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":3,\"name\":\"品牌\",\"alias\":\"brand\",\"scope\":0}"))
                .andExpect(status().isOk());
        verify(service).update(any(), any());
    }

    @Test
    void usagesFlagBindingsOutsideTheSite() throws Exception {
        globalType(3L, "brand");
        when(service.listBindings(3L)).thenReturn(List.of(
                binding("产品", SITE, false), binding("商品", 2L, false), binding("全局产品", null, true)));
        mvc.perform(get(URL + "/3/usages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].modelName").value("产品"))
                .andExpect(jsonPath("$[0].fieldName").value("品牌"))
                .andExpect(jsonPath("$[0].external").value(false))
                .andExpect(jsonPath("$[1].external").value(true))
                .andExpect(jsonPath("$[2].external").value(true));
    }
}
