package com.ujcms.cms.core.service;

import com.github.pagehelper.PageHelper;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.BlockItemMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.cms.core.service.args.BlockArgs;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;
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
    private final SeqService seqService;

    public BlockService(BlockItemMapper itemMapper, BlockMapper mapper, SeqService seqService) {
        this.itemMapper = itemMapper;
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Block bean, Integer siteId) {
        bean.setId(seqService.getNextVal(Block.TABLE_NAME));
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Block bean, Integer siteId) {
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Block> list) {
        short order = 1;
        for (Block bean : list) {
            bean.setOrder(order);
            mapper.update(bean);
            order += 1;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        itemMapper.deleteByBlockId(id);
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Block select(Integer id) {
        return mapper.select(id);
    }

    public List<Block> selectList(BlockArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), Block.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Block> selectList(BlockArgs args, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public boolean existsByAlias(String alias, @Nullable Integer siteId) {
        return PageHelper.offsetPage(0, 1, false).<Number>doSelectPage(() ->
                mapper.countByAlias(alias, siteId)).iterator().next().intValue() > 0;
    }

    @Override
    public void preSiteDelete(Integer siteId) {
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        // 栏目(100), **区块(200)**, 文章(300)、区块项(400)
        return 200;
    }
}