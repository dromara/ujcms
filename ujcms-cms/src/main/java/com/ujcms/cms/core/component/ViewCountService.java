package com.ujcms.cms.core.component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.domain.global.GlobalViewCount;
import com.ujcms.cms.core.listener.ArticleViewListener;
import com.ujcms.cms.core.mapper.ArticleBufferMapper;
import com.ujcms.cms.core.mapper.ChannelBufferMapper;
import com.ujcms.cms.core.mapper.SiteBufferMapper;
import com.ujcms.cms.core.mapper.TopicMapper;
import com.ujcms.cms.core.service.*;
import com.ujcms.common.web.Dates;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.function.Predicate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;

/**
 * @author PONY
 */
@Component
public class ViewCountService {
    private final ArticleService articleService;
    private final ChannelService channelService;
    private final TopicService topicService;
    private final GlobalService globalService;
    private final ArticleBufferService articleBufferService;
    private final SiteBufferService siteBufferService;
    private final SqlSessionTemplate sqlSessionTemplate;

    public ViewCountService(ArticleService articleService, ChannelService channelService, TopicService topicService,
                            GlobalService globalService, ArticleBufferService articleBufferService,
                            SiteBufferService siteBufferService, SqlSessionTemplate sqlSessionTemplate) {
        this.articleService = articleService;
        this.channelService = channelService;
        this.topicService = topicService;
        this.globalService = globalService;
        this.articleBufferService = articleBufferService;
        this.siteBufferService = siteBufferService;
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    public long viewArticle(Long id, @Nullable Long userId) {
        return viewArticle(id, userId, article -> true);
    }

    /**
     * 记一次文章浏览。
     *
     * @param readable 访问者能否读到这篇文章。读不到的（未公开、没有浏览权限）不计数、不回调、不返回浏览次数：
     *                 否则拿任意ID调用，草稿、已删除、别的站点的文章都会记进调用者的浏览历史，标题随之可读
     * @return 文章的浏览次数。文章不存在或读不到时为 0
     */
    public long viewArticle(Long id, @Nullable Long userId, Predicate<Article> readable) {
        Article article = articleService.select(id);
        if (article == null || !readable.test(article)) {
            return 0;
        }
        incrementChannelViews(article.getChannel());
        // 增加文章浏览次数
        Optional.ofNullable(getCurrentArticleViewsCache().get(id, key -> new AtomicInteger()))
                .ifPresent(AtomicInteger::incrementAndGet);
        // 会员浏览回调（如浏览历史记录）。匿名浏览不回调
        if (userId != null) {
            viewListeners.forEach(it -> it.afterArticleView(article, userId));
        }
        return article.getViews();
    }

    public long viewChannel(Long id) {
        Channel channel = channelService.select(id);
        if (channel == null) {
            return 0;
        }
        incrementChannelViews(channel);
        // 增加栏目自身浏览次数
        getCurrentChannelSelfViewsMap().computeIfAbsent(id, key -> new AtomicInteger()).incrementAndGet();
        return channel.getSelfViews();
    }

    /**
     * 专题浏览计数。语义与 {@link #viewChannel} 对齐：{@code views_} 沿父链向上聚合
     * （"本分支内的总访问"），{@code self_views_} 仅记录直接访问该专题页面的次数。
     */
    public long viewTopic(Long id) {
        Topic topic = topicService.select(id);
        if (topic == null) {
            return 0;
        }
        incrementTopicViews(topic);
        // 增加专题自身浏览次数
        getCurrentTopicSelfViewsMap().computeIfAbsent(id, key -> new AtomicInteger()).incrementAndGet();
        return topic.getSelfViews();
    }

    private static void incrementTopicViews(Topic topic) {
        // 增加所属站点浏览次数
        getCurrentSiteViewsMap().computeIfAbsent(topic.getSiteId(), key -> new AtomicInteger()).incrementAndGet();
        // 增加专题及上级专题浏览次数
        for (Topic bean : topic.getPaths()) {
            getCurrentTopicViewsMap().computeIfAbsent(bean.getId(), key -> new AtomicInteger()).incrementAndGet();
        }
    }

    private static void incrementChannelViews(Channel channel) {
        // 增加所属站点浏览次数
        getCurrentSiteViewsMap().computeIfAbsent(channel.getSiteId(), key -> new AtomicInteger()).incrementAndGet();
        // 增加栏目及上级栏目浏览次数
        for (Channel bean : channel.getPaths()) {
            getCurrentChannelViewsMap().computeIfAbsent(bean.getId(), key -> new AtomicInteger()).incrementAndGet();
        }
    }

    @Nullable
    public SiteBuffer viewSite(Long id) {
        SiteBuffer siteBuffer = siteBufferService.select(id);
        if (siteBuffer == null) {
            return null;
        }
        getCurrentSiteViewsMap().computeIfAbsent(id, key -> new AtomicInteger()).incrementAndGet();
        getCurrentSiteSelfViewsMap().computeIfAbsent(id, key -> new AtomicInteger()).incrementAndGet();
        return siteBuffer;
    }

    public void flushSiteViews() {
        Map<Long, AtomicInteger> map = getPreviousSiteViewsMap();
        Map<Long, AtomicInteger> selfMap = getPreviousSiteSelfViewsMap();
        try (SqlSession session = sqlSessionTemplate.getSqlSessionFactory().openSession(ExecutorType.BATCH, false)) {
            SiteBufferMapper mapper = session.getMapper(SiteBufferMapper.class);
            batchOperator(session, map.keySet(), FOREACH_SIZE, mapper::updateBatch, key -> {
                SiteBuffer bean = new SiteBuffer();
                bean.setId(key);
                bean.setViews((long) map.get(key).get());
                bean.setSelfViews((long) selfMap.computeIfAbsent(key, it -> new AtomicInteger()).get());
                return bean;
            });
        } finally {
            map.clear();
            selfMap.clear();
        }
    }

    public void flushChannelViews() {
        Map<Long, AtomicInteger> map = getPreviousChannelViewsMap();
        Map<Long, AtomicInteger> selfMap = getPreviousChannelSelfViewsMap();
        try (SqlSession session = sqlSessionTemplate.getSqlSessionFactory().openSession(ExecutorType.BATCH, false)) {
            ChannelBufferMapper mapper = session.getMapper(ChannelBufferMapper.class);
            batchOperator(session, map.keySet(), FOREACH_SIZE, mapper::updateBatch, key -> {
                ChannelBuffer bean = new ChannelBuffer();
                bean.setId(key);
                bean.setViews((long) map.get(key).get());
                bean.setSelfViews((long) selfMap.computeIfAbsent(key, it -> new AtomicInteger()).get());
                return bean;
            });
        } finally {
            map.clear();
            selfMap.clear();
        }
    }

    /**
     * 写回专题浏览次数。专题无独立 Buffer 表，直接 UPDATE {@code ujcms_topic}
     * 的 {@code views_} / {@code self_views_}（量级远低于栏目，无需缓冲层）。
     */
    public void flushTopicViews() {
        Map<Long, AtomicInteger> map = getPreviousTopicViewsMap();
        Map<Long, AtomicInteger> selfMap = getPreviousTopicSelfViewsMap();
        try (SqlSession session = sqlSessionTemplate.getSqlSessionFactory().openSession(ExecutorType.BATCH, false)) {
            TopicMapper mapper = session.getMapper(TopicMapper.class);
            batchOperator(session, map.keySet(), FOREACH_SIZE, mapper::updateViewsBatch, key -> {
                Topic bean = new Topic();
                bean.setId(key);
                bean.setViews((long) map.get(key).get());
                bean.setSelfViews((long) selfMap.computeIfAbsent(key, it -> new AtomicInteger()).get());
                return bean;
            });
        } finally {
            map.clear();
            selfMap.clear();
        }
    }

    public void flushArticleViews() {
        Cache<Long, AtomicInteger> cache = getPreviousArticleViewsCache();
        Map<Long, AtomicInteger> map = cache.asMap();
        try (SqlSession session = sqlSessionTemplate.getSqlSessionFactory().openSession(ExecutorType.BATCH, false)) {
            ArticleBufferMapper mapper = session.getMapper(ArticleBufferMapper.class);
            batchOperator(session, map.keySet(), FOREACH_SIZE, mapper::updateBatch, key -> {
                ArticleBuffer bean = new ArticleBuffer();
                bean.setId(key);
                bean.setViews((long) map.get(key).get());
                return bean;
            });
        } finally {
            map.clear();
            cache.invalidateAll();
        }
    }

    public static <T, U> void batchOperator(SqlSession session, Collection<U> all, int foreachSize,
                                            Consumer<List<T>> operator, Function<U, T> prepare) {
        int totals = all.size();
        List<T> list = new ArrayList<>(foreachSize);
        int index = 0;
        for (U item : all) {
            list.add(prepare.apply(item));
            index += 1;
            if (index % foreachSize == 0 || index == totals) {
                operator.accept(list);
                list.clear();
            }
            if (index % BATCH_SIZE * foreachSize == 0 || index == totals) {
                session.flushStatements();
                session.commit();
                session.clearCache();
            }
        }
    }

    /**
     * 处理站点的今日访问、昨日访问、历史最高访问等数据，处理文章的今日访问量、本周访问量、本月访问量等数据
     */
    public void updateViewsStat() {
        LocalDate day = LocalDate.now();
        LocalDate week = day.with(ChronoField.DAY_OF_WEEK, 1);
        LocalDate month = day.withDayOfMonth(1);
        LocalDate quarter = day.withDayOfYear(Dates.dayOfQuarter(day));
        LocalDate year = day.withDayOfYear(1);
        GlobalViewCount viewCount = globalService.selectGlobalDataOrInit(new GlobalViewCount(), GlobalViewCount.class);
        if (viewCount.getDay().compareTo(day) < 0) {
            // 日需要更新，则全部更新。先更新全局数据，避免其它集群节点也同时进行更新。
            globalService.updateGlobalData(new GlobalViewCount(day, week, month, quarter, year));
            siteBufferService.updateStat();
            articleBufferService.resetDayViews();
        }
        if (viewCount.getWeek().compareTo(week) < 0) {
            articleBufferService.resetWeekViews();
        }
        if (viewCount.getMonth().compareTo(month) < 0) {
            articleBufferService.resetMonthViews();
        }
        if (viewCount.getQuarter().compareTo(quarter) < 0) {
            articleBufferService.resetQuarterViews();
        }
        if (viewCount.getYear().compareTo(year) < 0) {
            articleBufferService.resetYearViews();
        }
    }

    private static Map<Long, AtomicInteger> getCurrentSiteViewsMap() {
        return LocalDateTime.now().get(MINUTE_OF_HOUR) % 2 != 0 ? SITE_ODD_VIEWS_MAP : SITE_EVEN_VIEWS_MAP;
    }

    private static Map<Long, AtomicInteger> getPreviousSiteViewsMap() {
        return LocalDateTime.now().get(MINUTE_OF_HOUR) % 2 == 0 ? SITE_ODD_VIEWS_MAP : SITE_EVEN_VIEWS_MAP;
    }

    private static Map<Long, AtomicInteger> getCurrentSiteSelfViewsMap() {
        return LocalDateTime.now().get(MINUTE_OF_HOUR) % 2 != 0 ? SITE_SELF_ODD_VIEWS_MAP : SITE_SELF_EVEN_VIEWS_MAP;
    }

    private static Map<Long, AtomicInteger> getPreviousSiteSelfViewsMap() {
        return LocalDateTime.now().get(MINUTE_OF_HOUR) % 2 == 0 ? SITE_SELF_ODD_VIEWS_MAP : SITE_SELF_EVEN_VIEWS_MAP;
    }

    private static Map<Long, AtomicInteger> getCurrentChannelViewsMap() {
        return LocalDateTime.now().get(MINUTE_OF_HOUR) % 2 != 0 ? CHANNEL_ODD_VIEWS_MAP : CHANNEL_EVEN_VIEWS_MAP;
    }

    private static Map<Long, AtomicInteger> getPreviousChannelViewsMap() {
        return LocalDateTime.now().get(MINUTE_OF_HOUR) % 2 == 0 ? CHANNEL_ODD_VIEWS_MAP : CHANNEL_EVEN_VIEWS_MAP;
    }

    private static Map<Long, AtomicInteger> getCurrentChannelSelfViewsMap() {
        return LocalDateTime.now().get(MINUTE_OF_HOUR) % 2 != 0 ? CHANNEL_SELF_ODD_VIEWS_MAP : CHANNEL_SELF_EVEN_VIEWS_MAP;
    }

    private static Map<Long, AtomicInteger> getPreviousChannelSelfViewsMap() {
        return LocalDateTime.now().get(MINUTE_OF_HOUR) % 2 == 0 ? CHANNEL_SELF_ODD_VIEWS_MAP : CHANNEL_SELF_EVEN_VIEWS_MAP;
    }

    private static Map<Long, AtomicInteger> getCurrentTopicViewsMap() {
        return LocalDateTime.now().get(MINUTE_OF_HOUR) % 2 != 0 ? TOPIC_ODD_VIEWS_MAP : TOPIC_EVEN_VIEWS_MAP;
    }

    private static Map<Long, AtomicInteger> getPreviousTopicViewsMap() {
        return LocalDateTime.now().get(MINUTE_OF_HOUR) % 2 == 0 ? TOPIC_ODD_VIEWS_MAP : TOPIC_EVEN_VIEWS_MAP;
    }

    private static Map<Long, AtomicInteger> getCurrentTopicSelfViewsMap() {
        return LocalDateTime.now().get(MINUTE_OF_HOUR) % 2 != 0 ? TOPIC_SELF_ODD_VIEWS_MAP : TOPIC_SELF_EVEN_VIEWS_MAP;
    }

    private static Map<Long, AtomicInteger> getPreviousTopicSelfViewsMap() {
        return LocalDateTime.now().get(MINUTE_OF_HOUR) % 2 == 0 ? TOPIC_SELF_ODD_VIEWS_MAP : TOPIC_SELF_EVEN_VIEWS_MAP;
    }

    private static Cache<Long, AtomicInteger> getCurrentArticleViewsCache() {
        return LocalDateTime.now().get(MINUTE_OF_HOUR) % 2 != 0 ? ARTICLE_ODD_VIEWS_CACHE : ARTICLE_EVEN_VIEWS_CACHE;
    }

    private static Cache<Long, AtomicInteger> getPreviousArticleViewsCache() {
        return LocalDateTime.now().get(MINUTE_OF_HOUR) % 2 == 0 ? ARTICLE_ODD_VIEWS_CACHE : ARTICLE_EVEN_VIEWS_CACHE;
    }

    /**
     * 站点浏览次数（分钟数为奇数时）
     */
    private static final Map<Long, AtomicInteger> SITE_ODD_VIEWS_MAP = new ConcurrentHashMap<>(16);
    /**
     * 站点浏览次数（分钟数为偶数时）
     */
    private static final Map<Long, AtomicInteger> SITE_EVEN_VIEWS_MAP = new ConcurrentHashMap<>(16);
    /**
     * 站点自身浏览次数（分钟数为奇数时）
     */
    private static final Map<Long, AtomicInteger> SITE_SELF_ODD_VIEWS_MAP = new ConcurrentHashMap<>(16);
    /**
     * 站点自身浏览次数（分钟数为偶数时）
     */
    private static final Map<Long, AtomicInteger> SITE_SELF_EVEN_VIEWS_MAP = new ConcurrentHashMap<>(16);
    /**
     * 栏目浏览次数（分钟数为奇数时）
     */
    private static final Map<Long, AtomicInteger> CHANNEL_ODD_VIEWS_MAP = new ConcurrentHashMap<>(16);
    /**
     * 栏目浏览次数（分钟数为偶数时）
     */
    private static final Map<Long, AtomicInteger> CHANNEL_EVEN_VIEWS_MAP = new ConcurrentHashMap<>(16);
    /**
     * 栏目自身浏览次数（分钟数为奇数时）
     */
    private static final Map<Long, AtomicInteger> CHANNEL_SELF_ODD_VIEWS_MAP = new ConcurrentHashMap<>(16);
    /**
     * 栏目自身浏览次数（分钟数为偶数时）
     */
    private static final Map<Long, AtomicInteger> CHANNEL_SELF_EVEN_VIEWS_MAP = new ConcurrentHashMap<>(16);
    /**
     * 专题浏览次数（分钟数为奇数时）
     */
    private static final Map<Long, AtomicInteger> TOPIC_ODD_VIEWS_MAP = new ConcurrentHashMap<>(16);
    /**
     * 专题浏览次数（分钟数为偶数时）
     */
    private static final Map<Long, AtomicInteger> TOPIC_EVEN_VIEWS_MAP = new ConcurrentHashMap<>(16);
    /**
     * 专题自身浏览次数（分钟数为奇数时）
     */
    private static final Map<Long, AtomicInteger> TOPIC_SELF_ODD_VIEWS_MAP = new ConcurrentHashMap<>(16);
    /**
     * 专题自身浏览次数（分钟数为偶数时）
     */
    private static final Map<Long, AtomicInteger> TOPIC_SELF_EVEN_VIEWS_MAP = new ConcurrentHashMap<>(16);
    /**
     * 文章浏览次数（分钟数为奇数时）。缓存2分钟（2分钟内一定会被使用），缓存条数2000万，更多的就丢弃。
     */
    private static final Cache<Long, AtomicInteger> ARTICLE_ODD_VIEWS_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES).maximumSize(20_000_000).build();
    /**
     * 文章浏览次数（分钟数为偶数时）。缓存2分钟（2分钟内一定会被使用），缓存条数2000万，更多的就丢弃。
     */
    private static final Cache<Long, AtomicInteger> ARTICLE_EVEN_VIEWS_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES).maximumSize(20_000_000).build();
    private List<ArticleViewListener> viewListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setViewListeners(List<ArticleViewListener> viewListeners) {
        this.viewListeners = viewListeners;
    }

    /**
     * SQL语句循环条数。ArticleBuffer一条记录有13个参数，13*120=1560个参数。最多可支持17个参数（17*120=2040）。
     * SQL Server最多支持2100个参数。
     */
    private static final int FOREACH_SIZE = 120;
    /**
     * SQL批量处理条数
     */
    public static final int BATCH_SIZE = 1000;
}
