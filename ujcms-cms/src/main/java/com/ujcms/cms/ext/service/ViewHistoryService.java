package com.ujcms.cms.ext.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.listener.ArticleDeleteListener;
import com.ujcms.cms.core.listener.ArticleViewListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.ext.domain.ViewHistory;
import com.ujcms.cms.ext.domain.generated.GeneratedViewHistory;
import com.ujcms.cms.ext.mapper.ViewHistoryMapper;
import com.ujcms.cms.ext.service.args.ViewHistoryArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 浏览历史 Service（见 docs/spec/membership/view-history.md）。
 * <p>
 * 记录仅由服务端浏览监听器写入：每 (用户, 文章) 一行，重复浏览更新最后浏览日期；
 * 内存节流合并短时间内的重复浏览，写入后按每用户上限裁剪，表规模有界。
 *
 * @author PONY
 */
@Service
public class ViewHistoryService implements ArticleViewListener, ArticleDeleteListener,
        SiteDeleteListener, UserDeleteListener {
    private final ViewHistoryMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public ViewHistoryService(ViewHistoryMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Override
    public void afterArticleView(Article article, Long userId) {
        // 节流：同一用户同一文章短时间内的重复浏览不再写库
        ThrottleKey key = new ThrottleKey(userId, article.getId());
        if (THROTTLE.getIfPresent(key) != null) {
            return;
        }
        THROTTLE.put(key, Boolean.TRUE);
        record(article, userId);
    }

    /**
     * 记录一次会员浏览：已有记录则更新最后浏览日期，否则插入并裁剪该用户的过旧记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void record(Article article, Long userId) {
        OffsetDateTime now = OffsetDateTime.now();
        ViewHistory existing = mapper.findByArticleIdAndUserId(article.getId(), userId);
        if (existing != null) {
            mapper.touch(existing.getId(), now);
            return;
        }
        ViewHistory bean = new ViewHistory();
        bean.setId(snowflakeSequence.nextId());
        bean.setSiteId(article.getSiteId());
        bean.setArticleId(article.getId());
        bean.setUserId(userId);
        bean.setCreated(now);
        bean.setUpdated(now);
        try {
            mapper.insert(bean);
        } catch (DuplicateKeyException e) {
            // 并发重复浏览。唯一索引兜底，事务已回滚，视为已记录即可
            return;
        }
        prune(userId);
    }

    /**
     * 裁剪：以第 (上限+1) 新的记录为截止，删除该用户更早的记录。updated_ 相同的并列记录会保留（上限为近似值）
     */
    private void prune(Long userId) {
        List<ViewHistory> cutoff = selectList(ViewHistoryArgs.of().userId(userId), MAX_PER_USER, 1);
        if (!cutoff.isEmpty()) {
            mapper.deleteStale(userId, cutoff.get(0).getUpdated());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteByUserId(Long userId) {
        return mapper.deleteByUserId(userId);
    }

    @Nullable
    public ViewHistory select(Long id) {
        return mapper.select(id);
    }

    @Nullable
    public ViewHistory findByArticleIdAndUserId(Long articleId, Long userId) {
        return mapper.findByArticleIdAndUserId(articleId, userId);
    }

    public List<ViewHistory> selectList(ViewHistoryArgs args) {
        // 默认按最后浏览日期倒序（最近浏览语义），而非收藏的 id_desc。updated_ 只精确到秒，同一秒内的记录
        // 再按 id 倒序，否则并列记录的先后不确定，分页会重复或漏行
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedViewHistory.TABLE_NAME,
                "updated_desc,id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<ViewHistory> selectList(ViewHistoryArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<ViewHistory> selectPage(ViewHistoryArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public long count(ViewHistoryArgs args) {
        return PageMethod.count(() -> selectList(args));
    }

    @Override
    public void preArticleDelete(Long articleId) {
        mapper.deleteByArticleId(articleId);
    }

    @Override
    public void preUserDelete(Long userId) {
        mapper.deleteByUserId(userId);
    }

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        // 浏览历史依赖文章(300)，须先于文章删除
        return 500;
    }

    /**
     * 节流键：用户 + 文章
     */
    private record ThrottleKey(Long userId, Long articleId) {
    }

    /**
     * 浏览写入节流：同一 (用户, 文章) 写后 30 分钟内不再写库，容量上限 10 万条
     */
    private static final Cache<ThrottleKey, Boolean> THROTTLE = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES).maximumSize(100_000).build();

    /**
     * 每用户浏览历史上限（近似值，updated_ 并列时可略超）。与推荐排除集上限对齐
     */
    public static final int MAX_PER_USER = 200;
}
