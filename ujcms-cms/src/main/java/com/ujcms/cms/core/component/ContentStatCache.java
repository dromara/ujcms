package com.ujcms.cms.core.component;

import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.UserService;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.function.ToIntFunction;

import static java.util.Collections.emptyList;

/**
 * 首页「概览」的内容统计：文章 / 栏目 / 用户 / 附件的总数、最近 30 天新增及按日序列（console-home D10）。
 * <p>
 * 按日序列不写分日 SQL（各库的日期截断函数不同），而是复用现有的「自某时刻起计数」查询：
 * 自今天 0 点、昨天 0 点 …… 29 天前 0 点各数一次（{@value #DAYS} 次累计），相邻两次相减即为每日新增。
 * 缓存 {@value #EXPIRES_MINUTE} 分钟，每桶 {@value #DAYS} + 1 次索引范围 count，代价可接受（与流量卡同为 30 天窗口）。
 *
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
    /**
     * 按日序列的天数（含今天）
     */
    public static final int DAYS = 30;

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
    public StatBucket articleStat(Long siteId) {
        return bucket(articleService.countByPublishDate(siteId, null, emptyList()),
                since -> articleService.countByPublishDate(siteId, since, emptyList()));
    }

    @Cacheable(key = "'channel'+#siteId")
    public StatBucket channelStat(Long siteId) {
        return bucket(channelService.countByCreated(siteId, null),
                since -> channelService.countByCreated(siteId, since));
    }

    @Cacheable(key = "'user'")
    public StatBucket userStat() {
        return bucket(userService.countByCreated(null), userService::countByCreated);
    }

    @Cacheable(key = "'attachment'+#siteId")
    public StatBucket attachmentStat(Long siteId) {
        return bucket(attachmentService.countByCreated(siteId, null),
                since -> attachmentService.countByCreated(siteId, since));
    }

    /**
     * 由「自某时刻起的累计数」推出按日序列：最后一项是今天，第一项是 {@value #DAYS} - 1 天前；last30day 为序列之和
     * （即自 29 天前 0 点起的累计，含今天），与图上的折线一致。
     */
    static StatBucket bucket(int total, ToIntFunction<OffsetDateTime> countSince) {
        return bucket(total, LocalDate.now(), ZoneId.systemDefault(), countSince);
    }

    static StatBucket bucket(int total, LocalDate today, ZoneId zone, ToIntFunction<OffsetDateTime> countSince) {
        int[] daily = new int[DAYS];
        int newer = 0;
        for (int back = 0; back < DAYS; back++) {
            OffsetDateTime dayStart = today.minusDays(back).atStartOfDay(zone).toOffsetDateTime();
            int cumulative = countSince.applyAsInt(dayStart);
            daily[DAYS - 1 - back] = Math.max(cumulative - newer, 0);
            newer = cumulative;
        }
        return new StatBucket(total, newer, daily);
    }

    /**
     * @param total    总数
     * @param last30day 最近 30 天（含今天的 30 个自然日）新增
     * @param daily    按日新增，长度 {@value #DAYS}，最后一项是今天
     */
    public record StatBucket(int total, int last30day, int[] daily) implements Serializable {
        @Serial
        private static final long serialVersionUID = 2L;
    }
}
