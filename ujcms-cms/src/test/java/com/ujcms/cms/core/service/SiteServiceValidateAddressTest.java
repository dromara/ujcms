package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.mapper.OrgMapper;
import com.ujcms.cms.core.mapper.SiteMapper;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.support.SubDirRouteWords;
import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SiteService#validateAddress(Site, boolean)} 与
 * {@link SiteService#validateLangGroupBeforeDelete(Site)} 单元测试
 * （addressing-core.md §3：作用域规则、空白子目录规范化、语言组完整性）。
 */
class SiteServiceValidateAddressTest {
    private SiteMapper mapper;
    private SiteService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SiteMapper.class);
        service = new SiteService(mock(HtmlSanitizeService.class), mock(AttachmentService.class),
                mock(ModelService.class), mapper, mock(OrgMapper.class));
    }

    private Site site(long id, String domain, String subDir) {
        Site bean = new Site();
        bean.setId(id);
        bean.setDomain(domain);
        bean.setSubDir(subDir);
        return bean;
    }

    // region 空白子目录规范化

    @Test
    void blankSubDirNormalizedToNull() {
        Site bean = site(1L, "www.example.com", " ");
        service.validateAddress(bean, false);
        assertNull(bean.getSubDir());
        // 规范化为 null 后不应再触发子目录唯一性查询
        verify(mapper, never()).countBySubDir(anyString(), any());
    }

    @Test
    void emptySubDirNormalizedToNull() {
        Site bean = site(1L, "www.example.com", "");
        service.validateAddress(bean, false);
        assertNull(bean.getSubDir());
    }

    // endregion

    // region 路由保留字

    @SuppressWarnings("unchecked")
    private void routeWords(String... words) {
        ObjectProvider<SubDirRouteWords> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(() -> Set.of(words));
        service.setRouteWordsProvider(provider);
    }

    @Test
    void subDirEqualToRouteWordRejected() {
        routeWords("search", "news");
        Http400Exception ex = assertThrows(Http400Exception.class,
                () -> service.validateAddress(site(1L, "www.example.com", "search"), false));
        assertEquals("error.siteSubDirReserved", ex.getCode());
        // 自定义栏目 URL 前缀同样是路由首段；忽略大小写（与 SUB_DIR_PATTERN 一致）
        assertThrows(Http400Exception.class,
                () -> service.validateAddress(site(1L, "www.example.com", "News"), true));
    }

    @Test
    void subDirNotRouteWordAccepted() {
        routeWords("search");
        assertDoesNotThrow(() -> service.validateAddress(site(1L, "www.example.com", "en"), false));
        assertDoesNotThrow(() -> service.validateAddress(site(1L, "www.example.com", null), false));
    }

    // endregion

    // region 唯一性作用域：单域名（multiDomain=false）

    @Test
    void singleDomainDuplicateSubDirRejected() {
        when(mapper.countBySubDir("en", 2L)).thenReturn(1);
        Site bean = site(2L, "b.example.com", "en");
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.validateAddress(bean, false));
        assertEquals("error.siteSubDirExist", e.getCode());
    }

    @Test
    void singleDomainUniqueSubDirPasses() {
        when(mapper.countBySubDir("en", 2L)).thenReturn(0);
        assertDoesNotThrow(() -> service.validateAddress(site(2L, "b.example.com", "en"), false));
        verify(mapper).countBySubDir("en", 2L);
    }

    @Test
    void singleDomainNeverQueriesComposite() {
        service.validateAddress(site(2L, "b.example.com", "en"), false);
        verify(mapper, never()).countByDomainAndSubDir(anyString(), any(), any());
    }

    // endregion

    // region 唯一性作用域：多域名（multiDomain=true）

    @Test
    void multiDomainDuplicateCompositeRejected() {
        when(mapper.countByDomainAndSubDir("a.example.com", "en", 2L)).thenReturn(1);
        Site bean = site(2L, "a.example.com", "en");
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.validateAddress(bean, true));
        assertEquals("error.siteSubDirExist", e.getCode());
    }

    /**
     * D1 场景：a.example.com/en 与 b.example.com/en 共存（组合寻址放行）
     */
    @Test
    void multiDomainSameSubDirOnDifferentDomainPasses() {
        when(mapper.countByDomainAndSubDir("b.example.com", "en", 2L)).thenReturn(0);
        assertDoesNotThrow(() -> service.validateAddress(site(2L, "b.example.com", "en"), true));
        verify(mapper, never()).countBySubDir(anyString(), any());
    }

    @Test
    void multiDomainDuplicateDomainOnlyRejected() {
        when(mapper.countByDomainAndSubDir("www.example.com", null, 2L)).thenReturn(1);
        Site bean = site(2L, "www.example.com", null);
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.validateAddress(bean, true));
        assertEquals("error.siteDomainExist", e.getCode());
    }

    // endregion

    // region 语言组完整性

    private Site master(long id) {
        Site bean = site(id, "www.example.com", null);
        bean.setLang("zh-CN");
        return bean;
    }

    @Test
    void langParentSelfReferenceRejected() {
        Site bean = site(2L, "www.example.com", "en");
        bean.setLangParentId(2L);
        assertThrows(Http400Exception.class, () -> service.validateAddress(bean, false));
    }

    @Test
    void langParentNotFoundRejected() {
        when(mapper.selectRefer(1L)).thenReturn(null);
        Site bean = site(2L, "www.example.com", "en");
        bean.setLangParentId(1L);
        assertThrows(Http400Exception.class, () -> service.validateAddress(bean, false));
    }

    /**
     * 不允许链：langParentId 指向的站点必须是组主站点
     */
    @Test
    void langParentChainRejected() {
        Site middle = master(3L);
        middle.setLangParentId(1L);
        when(mapper.selectRefer(3L)).thenReturn(middle);
        Site bean = site(2L, "www.example.com", "en");
        bean.setLangParentId(3L);
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.validateAddress(bean, false));
        assertTrue(String.valueOf(e.getCode()).contains("lang master"));
    }

    @Test
    void duplicateLangInGroupRejected() {
        when(mapper.selectRefer(1L)).thenReturn(master(1L));
        when(mapper.countByLangGroupAndLang(1L, "en", 2L)).thenReturn(1);
        Site bean = site(2L, "www.example.com", "en");
        bean.setLang("en");
        bean.setLangParentId(1L);
        assertThrows(Http400Exception.class, () -> service.validateAddress(bean, false));
    }

    @Test
    void validVariantPasses() {
        when(mapper.selectRefer(1L)).thenReturn(master(1L));
        when(mapper.countByLangGroupAndLang(1L, "en", 2L)).thenReturn(0);
        Site bean = site(2L, "www.example.com", "en");
        bean.setLang("en");
        bean.setLangParentId(1L);
        assertDoesNotThrow(() -> service.validateAddress(bean, false));
    }

    @Test
    void masterWithoutGroupSkipsGroupQueries() {
        service.validateAddress(master(1L), false);
        verify(mapper, never()).selectRefer(anyLong());
        verify(mapper, never()).countByLangGroupAndLang(anyLong(), anyString(), any());
    }

    // endregion

    // region 删除主站点前校验

    @Test
    void deleteMasterWithVariantsRejected() {
        when(mapper.countByLangParentId(1L)).thenReturn(2);
        Site bean = master(1L);
        assertThrows(Http400Exception.class, () -> service.validateLangGroupBeforeDelete(bean));
    }

    @Test
    void deleteSiteWithoutVariantsPasses() {
        when(mapper.countByLangParentId(1L)).thenReturn(0);
        assertDoesNotThrow(() -> service.validateLangGroupBeforeDelete(master(1L)));
    }

    // endregion
}
