package com.ujcms.cms.core.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ujcms.cms.core.domain.Site;

/**
 * Unit tests for {@link Frontends#buildRoutes(Site, Site, String)} — the structured-globals
 * helper that exposes cryptic single-letter URL prefixes (dy/api/ctx/def) under self-documenting
 * names ({@code routes.root}, {@code routes.api}, {@code routes.context}, {@code routes.default}).
 */
class FrontendsTest {

    @Test
    void buildRoutesExposesAllFourPrefixesUnderReadableNames() {
        Site site = mock(Site.class);
        when(site.getDy()).thenReturn("/news");
        when(site.getApi()).thenReturn("/api");

        Site defaultSite = mock(Site.class);
        when(defaultSite.getDynamicUrl()).thenReturn("/");

        Map<String, String> routes = Frontends.buildRoutes(site, defaultSite, "");

        assertEquals("/news", routes.get("root"));
        assertEquals("/api", routes.get("api"));
        assertEquals("", routes.get("context"));
        assertEquals("", routes.get("default"));
    }

    @Test
    void buildRoutesStripsTrailingSlashFromDefaultSiteUrl() {
        Site site = mock(Site.class);
        when(site.getDy()).thenReturn("/main");
        when(site.getApi()).thenReturn("/api");
        Site defaultSite = mock(Site.class);
        when(defaultSite.getDynamicUrl()).thenReturn("/main/");

        Map<String, String> routes = Frontends.buildRoutes(site, defaultSite, "/myapp");

        assertEquals("/myapp", routes.get("context"));
        assertEquals("/main", routes.get("default"),
                "trailing slash on defaultSite.dynamicUrl must be stripped so {{ routes.default }}/login concatenates cleanly");
    }

    @Test
    void buildRoutesHandlesNullContextPath() {
        Site site = mock(Site.class);
        when(site.getDy()).thenReturn("");
        when(site.getApi()).thenReturn("/api");
        Site defaultSite = mock(Site.class);
        when(defaultSite.getDynamicUrl()).thenReturn("/");

        Map<String, String> routes = Frontends.buildRoutes(site, defaultSite, null);

        assertEquals("", routes.get("context"));
    }

    @Test
    void buildRoutesPreservesInsertionOrder() {
        Site site = mock(Site.class);
        when(site.getDy()).thenReturn("/x");
        when(site.getApi()).thenReturn("/api");
        Site defaultSite = mock(Site.class);
        when(defaultSite.getDynamicUrl()).thenReturn("/");

        Map<String, String> routes = Frontends.buildRoutes(site, defaultSite, "");

        // Stable ordering helps when serializing routes for debug output / dump-context tools.
        assertEquals(java.util.List.of("root", "api", "context", "default"),
                java.util.List.copyOf(routes.keySet()));
    }
}
