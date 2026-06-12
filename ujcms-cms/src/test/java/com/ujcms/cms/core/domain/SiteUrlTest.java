package com.ujcms.cms.core.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SiteUrlTest {

    private Site createSite() {
        Site site = new Site();
        site.setProtocol("https");
        site.setDomain("example.com");
        return site;
    }

    // region getDynamicUrl

    @Test
    void getDynamicUrl_singleDomain_noContextPath_noSubDir_returnsSlash() {
        Site site = createSite();
        assertEquals("/", site.getDynamicUrl());
    }

    @Test
    void getDynamicUrl_singleDomain_withContextPath_returnsContextPath() {
        Site site = createSite();
        site.getConfig().setContextPath("/ctx");
        assertEquals("/ctx", site.getDynamicUrl());
    }

    @Test
    void getDynamicUrl_singleDomain_withContextPathAndSubDir() {
        Site site = createSite();
        site.getConfig().setContextPath("/ctx");
        site.setSubDir("sub");
        assertEquals("/ctx/sub", site.getDynamicUrl());
    }

    @Test
    void getDynamicUrl_multiDomain_includesProtocolAndDomain() {
        Site site = createSite();
        site.getConfig().setMultiDomain(true);
        assertEquals("https://example.com", site.getDynamicUrl());
    }

    @Test
    void getDynamicUrl_multiDomain_withContextPath() {
        Site site = createSite();
        site.getConfig().setMultiDomain(true);
        site.getConfig().setContextPath("/ctx");
        assertEquals("https://example.com/ctx", site.getDynamicUrl());
    }

    @Test
    void getDynamicUrl_neverReturnsEmptyString() {
        Site site = createSite();
        assertFalse(site.getDynamicUrl().isEmpty());
    }

    // endregion

    // region getDynamicFullUrl

    @Test
    void getDynamicFullUrl_alwaysContainsProtocolAndDomain() {
        Site site = createSite();
        String url = site.getDynamicFullUrl();
        assertEquals("https://example.com", url);
    }

    @Test
    void getDynamicFullUrl_withContextPath() {
        Site site = createSite();
        site.getConfig().setContextPath("/ctx");
        assertEquals("https://example.com/ctx", site.getDynamicFullUrl());
    }

    // endregion

    // region getUrl / getStaticUrl

    @Test
    void getUrl_htmlDisabled_singleDomain_returnsSlash() {
        Site site = createSite();
        // Html disabled by default
        assertEquals("/", site.getUrl());
    }

    @Test
    void getUrl_htmlEnabled_blankStorageUrl_returnsSlash() {
        Site site = createSite();
        Site.Html html = new Site.Html();
        html.setEnabled(true);
        site.setHtml(html);
        // HtmlStorage url defaults to "" → getStaticUrl falls through to "/"
        assertEquals("/", site.getStaticUrl());
    }

    @Test
    void getNormalStaticUrl_blankStorageUrl_singleDomain_returnsSlash() {
        Site site = createSite();
        assertEquals("/", site.getNormalStaticUrl());
    }

    @Test
    void getNormalStaticUrl_withContextPath() {
        Site site = createSite();
        site.getConfig().setContextPath("/ctx");
        assertEquals("/ctx", site.getNormalStaticUrl());
    }

    @Test
    void getNormalStaticUrl_withStoragePath_noProtocol_singleDomain() {
        Site site = createSite();
        site.getConfig().setHtmlStorage(new Config.Storage(0, "", "/static"));
        assertEquals("/static", site.getNormalStaticUrl());
    }

    @Test
    void getNormalStaticUrl_withStoragePath_hasHttpProtocol_returnsStorageUrl() {
        Site site = createSite();
        site.getConfig().setHtmlStorage(new Config.Storage(0, "", "https://cdn.example.com/static"));
        assertEquals("https://cdn.example.com/static", site.getNormalStaticUrl());
    }

    // endregion
}
