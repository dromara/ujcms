package com.ujcms.cms.ext.service;

import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.ext.domain.FavoriteGroup;
import com.ujcms.cms.ext.domain.generated.GeneratedFavoriteGroup;
import com.ujcms.cms.ext.mapper.FavoriteGroupMapper;
import com.ujcms.cms.ext.mapper.FavoriteMapper;
import com.ujcms.cms.ext.service.args.FavoriteGroupArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收藏夹 Service
 *
 * @author PONY
 */
@Service
public class FavoriteGroupService implements UserDeleteListener {
    private final FavoriteGroupMapper mapper;
    private final FavoriteMapper favoriteMapper;
    private final SnowflakeSequence snowflakeSequence;

    public FavoriteGroupService(FavoriteGroupMapper mapper, FavoriteMapper favoriteMapper,
                                SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.favoriteMapper = favoriteMapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(FavoriteGroup bean, Long userId) {
        bean.setId(snowflakeSequence.nextId());
        bean.setUserId(userId);
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(FavoriteGroup bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        // 收藏夹内的收藏移至未分组
        favoriteMapper.clearGroupId(id);
        return mapper.delete(id);
    }

    @Nullable
    public FavoriteGroup select(Long id) {
        return mapper.select(id);
    }

    public List<FavoriteGroup> selectList(FavoriteGroupArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedFavoriteGroup.TABLE_NAME, "id_asc");
        return mapper.selectAll(queryInfo);
    }

    @Override
    public void preUserDelete(Long userId) {
        // 用户删除监听器无顺序保证，先清除收藏对收藏夹的引用，使删除顺序无关
        favoriteMapper.clearGroupIdByUserId(userId);
        mapper.deleteByUserId(userId);
    }
}
