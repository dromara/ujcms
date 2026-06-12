package com.ujcms.cms.core.component;

import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.UserService;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyList;

/**
 * @author PONY
 */
@Component
@CacheConfig(cacheNames = ContentStatCache.CACHE_NAME)
public class ContentStatCache {
    /**
     * 缓存名称
     */
    public static final String CACHE_NAME = "contentStat";
    /**
     * 缓存时间
     */
    public static final int EXPIRES_MINUTE = 10;
    /**
     * 缓存最大数量
     */
    public static final int MAXIMUM_SIZE = 10_000;

    private final ArticleService articleService;
    private final ChannelService channelService;
    private final UserService userService;
    private final AttachmentService attachmentService;

    public ContentStatCache(ArticleService articleService, ChannelService channelService,
                            UserService userService, AttachmentService attachmentService) {
        this.articleService = articleService;
        this.channelService = channelService;
        this.userService = userService;
        this.attachmentService = attachmentService;
    }

    @Cacheable(key = "'article'+#siteId")
    public Map<String, Object> articleStat(Long siteId) {
        int total = articleService.countByPublishDate(siteId, null, emptyList());
        int last7day = articleService.countByPublishDate(siteId, OffsetDateTime.now().minusDays(7), emptyList());
        return createResult(total, last7day);
    }

    @Cacheable(key = "'channel'+#siteId")
    public Map<String, Object> channelStat(Long siteId) {
        int total = channelService.countByCreated(siteId, null);
        int last7day = channelService.countByCreated(siteId, OffsetDateTime.now().minusDays(7));
        return createResult(total, last7day);
    }

    @Cacheable(key = "'user'")
    public Map<String, Object> userStat() {
        int total = userService.countByCreated(null);
        int last7day = userService.countByCreated(OffsetDateTime.now().minusDays(7));
        return createResult(total, last7day);
    }

    @Cacheable(key = "'attachment'+#siteId")
    public Map<String, Object> attachmentStat(Long siteId) {
        int total = attachmentService.countByCreated(siteId, null);
        int last7day = attachmentService.countByCreated(siteId, OffsetDateTime.now().minusDays(7));
        return createResult(total, last7day);
    }

    private Map<String, Object> createResult(int total, int last7day) {
        Map<String, Object> result = new HashMap<>(16);
        result.put("total", total);
        result.put("last7day", last7day);
        return result;
    }
}
