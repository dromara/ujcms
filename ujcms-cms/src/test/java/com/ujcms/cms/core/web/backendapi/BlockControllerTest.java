package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.args.BlockArgs;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 区块后台接口契约：别名唯一性以“别名 × 共享范围”为命名空间；排序按共享范围分组提交——只含本站区块的
 * 一组无需全局数据权限；列表统计本站的区块项数；全局区块的区块项分属各站，其它站点仍有区块项时不可删除
 * （删除会级联区块项）。站点/用户上下文经 {@link Contexts} ThreadLocal 注入（standalone MockMvc 同线程执行）。
 */
class BlockControllerTest {
    private static final Long SITE = 1L;
    private static final String URL = "/api/backend/core/block";

    private BlockItemService itemService;
    private BlockService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        itemService = mock(BlockItemService.class);
        service = mock(BlockService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BlockController(itemService, service)).build();
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

    private Block block(long id, String alias, boolean global) {
        Block bean = new Block();
        bean.setId(id);
        bean.setName("区块");
        bean.setAlias(alias);
        if (global) {
            bean.setScope(EntityConstants.SCOPE_GLOBAL);
        } else {
            bean.setSiteId(SITE);
        }
        when(service.select(id)).thenReturn(bean);
        return bean;
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
        block(4L, "focus", false);
        when(service.existsByAlias("focus", null)).thenReturn(true);
        assertCause(Http400Exception.class, () -> mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":4,\"name\":\"焦点图\",\"alias\":\"focus\",\"scope\":2}")));
        verify(service, never()).update(any(), any());
    }

    @Test
    void updateSkipsAliasCheckWhenAliasAndScopeUnchanged() throws Exception {
        block(4L, "focus", false);
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":4,\"name\":\"焦点图\",\"alias\":\"focus\",\"scope\":0}"))
                .andExpect(status().isOk());
        verify(service, never()).existsByAlias(any(), any());
        verify(service).update(any(), any());
    }

    @Test
    void orderOfSiteBlocksNeedsNoGlobalPermission() throws Exception {
        globalUser(false);
        block(4L, "focus", false);
        block(5L, "notice", false);
        mvc.perform(put(URL + "/order").contentType(MediaType.APPLICATION_JSON).content("[5,4]"))
                .andExpect(status().isOk());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Block>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).updateOrder(captor.capture());
        assertEquals(List.of(5L, 4L), captor.getValue().stream().map(Block::getId).toList());
    }

    @Test
    void orderContainingGlobalBlockRequiresGlobalPermission() {
        globalUser(false);
        block(3L, "shared", true);
        block(4L, "focus", false);
        assertCause(Http403Exception.class, () -> mvc.perform(put(URL + "/order")
                .contentType(MediaType.APPLICATION_JSON).content("[3,4]")));
        verify(service, never()).updateOrder(any());
    }
    @Test
    void listCountsItemsOfTheCurrentSite() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isOk());
        ArgumentCaptor<BlockArgs> args = ArgumentCaptor.forClass(BlockArgs.class);
        verify(service).selectList(args.capture());
        assertEquals(SITE, args.getValue().getItemCountSiteId());
    }

    @Test
    void deleteOfGlobalBlockRefusedWhileOtherSitesHoldItems() {
        block(3L, "shared", true);
        when(itemService.existsByBlockId(3L, SITE)).thenReturn(true);
        assertCause(LogicException.class, () -> mvc.perform(delete(URL)
                .contentType(MediaType.APPLICATION_JSON).content("[3]")));
        verify(service, never()).delete(anyList());
    }

    @Test
    void deleteOfGlobalBlockAllowedWhenOnlyThisSiteHoldsItems() throws Exception {
        block(3L, "shared", true);
        mvc.perform(delete(URL).contentType(MediaType.APPLICATION_JSON).content("[3]")).andExpect(status().isOk());
        verify(service).delete(List.of(3L));
    }

    @Test
    void deleteOfSiteBlockNeverAsksAboutOtherSites() throws Exception {
        block(4L, "focus", false);
        mvc.perform(delete(URL).contentType(MediaType.APPLICATION_JSON).content("[4]")).andExpect(status().isOk());
        verify(itemService, never()).existsByBlockId(any(), any());
        verify(service).delete(List.of(4L));
    }

    // ---- 区块项模型的绑定（docs/spec/model-management/plan.md M11）：新增、改换模型或改变共享范围时校验，其余不动

    @Test
    void createValidatesTheItemModelForTheSite() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"焦点图\",\"alias\":\"focus\",\"scope\":0,\"blockItemModelId\":9}"))
                .andExpect(status().isOk());
        verify(service).validateModel(SITE, 9L);
        verify(service).insert(any(), any());
    }

    @Test
    void updateValidatesTheItemModelOnlyWhenTheBindingChanges() throws Exception {
        Block bean = block(4L, "focus", false);
        bean.setBlockItemModelId(9L);
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":4,\"name\":\"焦点图\",\"alias\":\"focus\",\"scope\":0,\"blockItemModelId\":9}"))
                .andExpect(status().isOk());
        verify(service, never()).validateModel(any(), any());
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":4,\"name\":\"焦点图\",\"alias\":\"focus\",\"scope\":0,\"blockItemModelId\":10}"))
                .andExpect(status().isOk());
        verify(service).validateModel(SITE, 10L);
    }

    @Test
    void aBlockTurnedGlobalRevalidatesItsModelAsGlobal() throws Exception {
        Block bean = block(4L, "focus", false);
        bean.setBlockItemModelId(9L);
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":4,\"name\":\"焦点图\",\"alias\":\"focus\",\"scope\":2,\"blockItemModelId\":9}"))
                .andExpect(status().isOk());
        verify(service).validateModel(null, 9L);
    }

    @Test
    void aRefusedModelBindingWritesNothing() throws Exception {
        block(4L, "focus", false);
        doThrow(new Http400Exception("error.block.modelInvalid", "9")).when(service).validateModel(SITE, 9L);
        assertCause(Http400Exception.class, () -> mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":4,\"name\":\"焦点图\",\"alias\":\"focus\",\"scope\":0,\"blockItemModelId\":9}")));
        verify(service, never()).update(any(), any());
    }
}
