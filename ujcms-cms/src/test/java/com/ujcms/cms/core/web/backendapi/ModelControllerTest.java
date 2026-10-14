package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.ModelUsage;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.cms.core.service.DictTypeService;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.LogicException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模型接口契约（docs/spec/model-management/plan.md）：写接口各管一段（M2）、校验只在值变化时进行（M3）、
 * 使用统计按可见集投影补零（M4）、读取不需全局数据权限（M5）、单例类型与系统行（M6）、同名校验（M7）、
 * 全局模型只绑全局字典（M8）；排序按共享范围分组提交。
 * 站点/用户上下文经 {@link Contexts} ThreadLocal 注入（standalone MockMvc 同线程执行）。
 */
class ModelControllerTest {
    private static final Long SITE = 1L;
    private static final String URL = "/api/backend/core/model";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private ModelService service;
    private DictTypeService dictTypeService;
    private MockMvc mvc;
    private User user;

    @BeforeEach
    void setUp() {
        service = mock(ModelService.class);
        dictTypeService = mock(DictTypeService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ModelController(service, dictTypeService)).build();
        Site site = new Site();
        site.setId(SITE);
        Contexts.setCurrentSite(site);
        user = new User();
        user.setId(2L);
        globalPermission(false);
        Contexts.setCurrentUser(user);
        when(service.selectUsage(anyLong(), anyLong())).thenAnswer(inv -> ModelUsage.none(inv.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        Contexts.clearCurrentSite();
        Contexts.clearCurrentUser();
    }

    private void globalPermission(boolean granted) {
        Role role = new Role();
        role.setGlobalPermission(granted);
        user.setRoleList(List.of(role));
    }

    private Model model(long id, boolean global) {
        Model bean = new Model();
        bean.setId(id);
        bean.setName("模型" + id);
        bean.setType("article");
        bean.setOrder(5);
        bean.setMains("[{\"code\":\"title\"}]");
        bean.setCustoms("[{\"code\":\"brand\",\"name\":\"品牌\",\"type\":\"select\",\"dictTypeId\":9}]");
        if (global) {
            bean.setScope(EntityConstants.SCOPE_GLOBAL);
        } else {
            bean.setSiteId(SITE);
        }
        when(service.select(id)).thenReturn(bean);
        return bean;
    }

    /** standalone MockMvc 无项目异常解析器：控制器异常以 ServletException 包裹抛出。 */
    private static Throwable assertCause(Class<?> type, Executable executable) {
        Throwable thrown = assertThrows(Throwable.class, executable);
        Throwable cause = thrown;
        while (cause.getCause() != null && !type.isInstance(cause)) {
            cause = cause.getCause();
        }
        assertInstanceOf(type, cause);
        return cause;
    }

    // ----- 排序 -----

    @Test
    void orderOfSiteModelsNeedsNoGlobalPermission() throws Exception {
        model(30L, false);
        model(31L, false);
        mvc.perform(put(URL + "/order").contentType(JSON).content("[31,30]")).andExpect(status().isOk());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Model>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).updateOrder(captor.capture());
        assertEquals(List.of(31L, 30L), captor.getValue().stream().map(Model::getId).toList());
    }

    @Test
    void orderContainingGlobalModelRequiresGlobalPermission() {
        model(1L, true);
        model(30L, false);
        assertCause(Http403Exception.class, () -> mvc.perform(put(URL + "/order").contentType(JSON).content("[1,30]")));
        verify(service, never()).updateOrder(any());
    }

    // ----- 读取 -----

    @Test
    void showGlobalModelNeedsNoGlobalPermission() throws Exception {
        model(1L, true);
        mvc.perform(get(URL + "/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void usagesProjectOntoVisibleModelsAndFillZeros() throws Exception {
        Model a = model(30L, false);
        Model b = model(31L, false);
        when(service.selectList(any())).thenReturn(List.of(a, b));
        when(service.listUsages(SITE)).thenReturn(Map.of(30L, new ModelUsage(30L, 3, 1), 99L, new ModelUsage(99L, 7, 7)));
        mvc.perform(get(URL + "/usages").param("type", "article"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"modelId\":30,\"count\":3,\"external\":1},{\"modelId\":31,\"count\":0,\"external\":0}]"));
    }

    // ----- 新增 -----

    @Test
    void createCopiesOnlyOperatorColumnsAndValidatesFields() throws Exception {
        mvc.perform(post(URL).contentType(JSON).content("{\"name\":\"新闻\",\"type\":\"article\",\"scope\":0,\"order\":1,"
                        + "\"origin\":\"theme:x\",\"originId\":\"m1\",\"customs\":\"[{\\\"code\\\":\\\"a\\\",\\\"name\\\":\\\"A\\\",\\\"type\\\":\\\"text\\\"}]\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<Model> captor = ArgumentCaptor.forClass(Model.class);
        verify(service).insert(captor.capture(), eq(SITE));
        Model inserted = captor.getValue();
        assertEquals("新闻", inserted.getName());
        assertNull(inserted.getOrigin());
        assertNull(inserted.getOriginId());
        assertEquals(999999, inserted.getOrder());
    }

    @Test
    void createRefusesSingletonTypesAndUnknownTypes() {
        assertCause(LogicException.class, () -> mvc.perform(post(URL).contentType(JSON)
                .content("{\"name\":\"x\",\"type\":\"user\",\"scope\":0}")));
        assertCause(Http400Exception.class, () -> mvc.perform(post(URL).contentType(JSON)
                .content("{\"name\":\"x\",\"type\":\"widget\",\"scope\":0}")));
        verify(service, never()).insert(any(), any());
    }

    @Test
    void createRefusesDuplicateNameWithinVisibleSet() {
        when(service.existsByName("新闻", "article", false, SITE, null)).thenReturn(true);
        Throwable cause = assertCause(LogicException.class, () -> mvc.perform(post(URL).contentType(JSON)
                .content("{\"name\":\"新闻\",\"type\":\"article\",\"scope\":0}")));
        assertEquals("error.model.nameExist", cause.getMessage());
        verify(service, never()).insert(any(), any());
    }

    @Test
    void createRefusesDuplicateFieldCodes() {
        Throwable cause = assertCause(LogicException.class, () -> mvc.perform(post(URL).contentType(JSON)
                .content("{\"name\":\"新闻\",\"type\":\"article\",\"scope\":0,"
                        + "\"customs\":\"[{\\\"code\\\":\\\"a\\\",\\\"name\\\":\\\"A\\\",\\\"type\\\":\\\"text\\\"},{\\\"code\\\":\\\"a\\\",\\\"name\\\":\\\"B\\\",\\\"type\\\":\\\"text\\\"}]\"}")));
        assertEquals("error.model.fieldCodeDuplicated", cause.getMessage());
        verify(service, never()).insert(any(), any());
    }

    // ----- 修改 -----

    @Test
    void updateWritesOnlyNameAndScope() throws Exception {
        Model stored = model(30L, false);
        mvc.perform(put(URL).contentType(JSON).content("{\"id\":30,\"name\":\"改名\",\"type\":\"channel\",\"scope\":0,"
                        + "\"order\":1,\"mains\":\"[]\",\"customs\":\"broken\"}"))
                .andExpect(status().isOk());
        verify(service).update(stored, SITE);
        assertEquals("改名", stored.getName());
        assertEquals("article", stored.getType());
        assertEquals(5, stored.getOrder());
        assertEquals("[{\"code\":\"title\"}]", stored.getMains());
        // 同名校验只在名称或范围变化时进行：这里名称变了，须查；范围未变
        verify(service).existsByName("改名", "article", false, SITE, 30L);
    }

    @Test
    void updateToleratesUnchangedNameEvenIfDuplicated() throws Exception {
        Model stored = model(30L, false);
        when(service.existsByName(anyString(), anyString(), anyBoolean(), anyLong(), any())).thenReturn(true);
        mvc.perform(put(URL).contentType(JSON).content("{\"id\":30,\"name\":\"模型30\",\"scope\":0}"))
                .andExpect(status().isOk());
        verify(service).update(stored, SITE);
    }

    @Test
    void updateRefusesGlobalToPrivateWhileOtherSitesUseIt() {
        globalPermission(true);
        model(40L, true);
        when(service.selectUsage(40L, SITE)).thenReturn(new ModelUsage(40L, 2, 1));
        Throwable cause = assertCause(LogicException.class, () -> mvc.perform(put(URL).contentType(JSON)
                .content("{\"id\":40,\"name\":\"模型40\",\"scope\":0}")));
        assertEquals("error.model.scopeInUse", cause.getMessage());
        verify(service, never()).update(any(), any());
    }

    @Test
    void updateKeepsSystemModelsGlobal() {
        globalPermission(true);
        model(1L, true);
        assertCause(Http400Exception.class, () -> mvc.perform(put(URL).contentType(JSON)
                .content("{\"id\":1,\"name\":\"全局设置模型\",\"scope\":0}")));
        verify(service, never()).update(any(), any());
    }

    @Test
    void promotingToGlobalRequiresGlobalDictBindings() {
        globalPermission(true);
        model(30L, false);
        DictType siteType = new DictType();
        siteType.setId(9L);
        siteType.setScope(EntityConstants.SCOPE_PRIVATE);
        when(dictTypeService.select(9L)).thenReturn(siteType);
        Throwable cause = assertCause(LogicException.class, () -> mvc.perform(put(URL).contentType(JSON)
                .content("{\"id\":30,\"name\":\"模型30\",\"scope\":2}")));
        assertEquals("error.model.fieldDictScope", cause.getMessage());
        verify(service, never()).update(any(), any());
    }

    // ----- 字段集 -----

    @Test
    void customFieldsEndpointValidatesAndWritesOnlyCustoms() throws Exception {
        Model stored = model(30L, false);
        String customs = "[{\"code\":\"a\",\"name\":\"A\",\"type\":\"text\"}]";
        mvc.perform(put(URL + "/30/custom-fields").contentType(JSON)
                        .content("{\"customs\":" + quote(customs) + "}"))
                .andExpect(status().isOk());
        verify(service).updateCustomFields(stored, customs);

        assertCause(LogicException.class, () -> mvc.perform(put(URL + "/30/custom-fields").contentType(JSON)
                .content("{\"customs\":\"not json\"}")));
        verify(service, never()).updateCustomFields(stored, "not json");
    }

    @Test
    void customFieldsOfGlobalModelToleratesUnchangedBindingButChecksNewOnes() throws Exception {
        globalPermission(true);
        Model stored = model(40L, true);
        DictType siteType = new DictType();
        siteType.setId(8L);
        siteType.setScope(EntityConstants.SCOPE_PRIVATE);
        when(dictTypeService.select(8L)).thenReturn(siteType);
        // brand 仍绑 9（库中现值，不查）；新增字段绑本站字典 8 → 拒绝
        String customs = "[{\"code\":\"brand\",\"name\":\"品牌\",\"type\":\"select\",\"dictTypeId\":9},"
                + "{\"code\":\"color\",\"name\":\"颜色\",\"type\":\"radio\",\"dictTypeId\":8}]";
        Throwable cause = assertCause(LogicException.class, () -> mvc.perform(put(URL + "/40/custom-fields")
                .contentType(JSON).content("{\"customs\":" + quote(customs) + "}")));
        assertEquals("error.model.fieldDictScope", cause.getMessage());
        verify(dictTypeService, never()).select(9L);
        verify(service, never()).updateCustomFields(any(), any());

        String unchanged = "[{\"code\":\"brand\",\"name\":\"品牌\",\"type\":\"select\",\"dictTypeId\":9}]";
        mvc.perform(put(URL + "/40/custom-fields").contentType(JSON).content("{\"customs\":" + quote(unchanged) + "}"))
                .andExpect(status().isOk());
        verify(service).updateCustomFields(stored, unchanged);
    }

    @Test
    void systemFieldsEndpointRequiresGlobalPermissionForGlobalModel() throws Exception {
        model(40L, true);
        assertCause(Http403Exception.class, () -> mvc.perform(put(URL + "/40/system-fields").contentType(JSON)
                .content("{\"mains\":\"[{\\\"code\\\":\\\"title\\\"}]\",\"asides\":null}")));
        verify(service, never()).updateSystemFields(any(), any(), any());

        Model stored = model(30L, false);
        mvc.perform(put(URL + "/30/system-fields").contentType(JSON)
                        .content("{\"mains\":\"[{\\\"code\\\":\\\"title\\\"}]\",\"asides\":\"[]\"}"))
                .andExpect(status().isOk());
        verify(service).updateSystemFields(stored, "[{\"code\":\"title\"}]", "[]");
    }

    // ----- 删除 -----

    @Test
    void deleteValidatesEveryIdBeforeDeletingAnything() {
        model(30L, false);
        model(40L, true);
        assertCause(Http403Exception.class, () -> mvc.perform(delete(URL).contentType(JSON).content("[30,40]")));
        verify(service, never()).delete(anyList());
        verify(service, never()).delete(anyLong());
    }

    @Test
    void deleteHandsTheWholeBatchToTheService() throws Exception {
        model(30L, false);
        model(31L, false);
        mvc.perform(delete(URL).contentType(JSON).content("[30,31]")).andExpect(status().isOk());
        verify(service).delete(List.of(30L, 31L));
    }

    private static String quote(String json) {
        return "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
