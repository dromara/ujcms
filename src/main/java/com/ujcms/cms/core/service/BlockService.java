package com.ujcms.cms.core.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.base.BlockBase;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.BlockItemMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.cms.core.service.args.BlockArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 区块 Service
 *
 * @author PONY
 */
@Service
public class BlockService implements SiteDeleteListener {
    private final BlockItemMapper itemMapper;
    private final BlockMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public BlockService(BlockItemMapper itemMapper, BlockMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.itemMapper = itemMapper;
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Block bean, Long siteId) {
        bean.setId(snowflakeSequence.nextId());
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Block bean, Long siteId) {
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Block> list) {
        int order = 1;
        for (Block bean : list) {
            bean.setOrder(order);
            mapper.update(bean);
            order += 1;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        itemMapper.deleteByBlockId(id);
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Block select(Long id) {
        return mapper.select(id);
    }

    public List<Block> selectList(BlockArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), BlockBase.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Block> selectList(BlockArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public boolean existsByAlias(String alias, @Nullable Long siteId) {
        return mapper.existsByAlias(alias, siteId) > 0;
    }

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        // 栏目(100), **区块(200)**, 文章(300)、区块项(400)
        return 200;
    }
}