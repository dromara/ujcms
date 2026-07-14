package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.listener.ArticleDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.ext.domain.Favorite;
import com.ujcms.cms.ext.domain.generated.GeneratedFavorite;
import com.ujcms.cms.ext.mapper.FavoriteMapper;
import com.ujcms.cms.ext.service.args.FavoriteArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收藏 Service
 *
 * @author PONY
 */
@Service
public class FavoriteService implements ArticleDeleteListener, SiteDeleteListener, UserDeleteListener {
    private final FavoriteMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public FavoriteService(FavoriteMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Favorite bean, Long userId) {
        bean.setId(snowflakeSequence.nextId());
        bean.setUserId(userId);
        mapper.insert(bean);
        mapper.addArticleFavorites(bean.getArticleId(), 1);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateGroupId(Long id, @Nullable Long groupId) {
        mapper.updateGroupId(id, groupId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        Favorite bean = mapper.select(id);
        if (bean == null) {
            return 0;
        }
        int count = mapper.delete(id);
        if (count > 0) {
            mapper.addArticleFavorites(bean.getArticleId(), -count);
        }
        return count;
    }

    @Nullable
    public Favorite select(Long id) {
        return mapper.select(id);
    }

    @Nullable
    public Favorite findByArticleIdAndUserId(Long articleId, Long userId) {
        return mapper.findByArticleIdAndUserId(articleId, userId);
    }

    public List<Favorite> selectList(FavoriteArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedFavorite.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Favorite> selectList(FavoriteArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Favorite> selectPage(FavoriteArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public long count(FavoriteArgs args) {
        return PageMethod.count(() -> selectList(args));
    }

    @Override
    public void preArticleDelete(Long articleId) {
        mapper.deleteByArticleId(articleId);
    }

    @Override
    public void preUserDelete(Long userId) {
        // 逐条删除，以便维护文章收藏次数
        selectList(FavoriteArgs.of().userId(userId)).forEach(favorite -> delete(favorite.getId()));
    }

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        // 收藏依赖文章(300)，须先于文章删除
        return 500;
    }
}
