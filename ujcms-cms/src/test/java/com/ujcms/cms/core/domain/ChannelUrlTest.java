package com.ujcms.cms.core.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChannelUrlTest {

    private Site site;

    @BeforeEach
    void setUp() {
        site = new Site();
        site.setProtocol("https");
        site.setDomain("example.com");
        // html disabled by default → uses getDynamicUrl
    }

    private Channel createChannel(String alias) {
        Channel channel = new Channel();
        channel.setSite(site);
        channel.setAlias(alias);
        // type defaults to TYPE_NORMAL (1), no link
        return channel;
    }

    @Test
    void getDynamicUrl_singleDomain_noContextPath_noDoubleSlash() {
        Channel channel = createChannel("news");
        String url = channel.getDynamicUrl();
        assertFalse(url.startsWith("//"), "URL must not start with //: " + url);
        assertEquals("/channel/news", url);
    }

    @Test
    void getDynamicUrl_withContextPath_includesContextPath() {
        site.getConfig().setContextPath("/ctx");
        Channel channel = createChannel("news");
        assertEquals("/ctx/channel/news", channel.getDynamicUrl());
    }

    @Test
    void getDynamicUrl_page2_appendsPageNumber() {
        Channel channel = createChannel("news");
        assertEquals("/channel/news/2", channel.getDynamicUrl(2));
    }

    @Test
    void getDynamicUrl_withContextPath_page2() {
        site.getConfig().setContextPath("/ctx");
        Channel channel = createChannel("news");
        assertEquals("/ctx/channel/news/2", channel.getDynamicUrl(2));
    }

    @Test
    void getDynamicUrl_customChannelUrl() {
        site.getConfig().setChannelUrl("/c");
        Channel channel = createChannel("news");
        assertEquals("/c/news", channel.getDynamicUrl());
    }

    @Test
    void getDynamicUrl_multiDomain_includesProtocolAndDomain() {
        site.getConfig().setMultiDomain(true);
        Channel channel = createChannel("news");
        assertEquals("https://example.com/channel/news", channel.getDynamicUrl());
    }
}
