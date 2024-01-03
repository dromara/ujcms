package com.ujcms.cms.core.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.base.BlockBase;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.BlockItemMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.cms.core.service.args.BlockArgs;
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
    private final SeqService seqService;

    public BlockService(BlockItemMapper itemMapper, BlockMapper mapper, SeqService seqService) {
        this.itemMapper = itemMapper;
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Block bean, Integer siteId) {
        bean.setId(seqService.getNextVal(BlockBase.TABLE_NAME));
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

    @Transactional(rollbackFor = Exception.class)
    public void copyBySite(Integer siteId, Integer fromSiteId) {
        for (Block block : listBySiteId(fromSiteId)) {
            insert(block, siteId);
        }
    }

    @Nullable
    public Block select(Integer id) {
        return mapper.select(id);
    }

    public List<Block> selectList(BlockArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), BlockBase.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Block> selectList(BlockArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public List<Block> listBySiteId(Integer siteId) {
        return selectList(BlockArgs.of().siteId(siteId));
    }

    public boolean existsByAlias(String alias, @Nullable Integer siteId) {
        return mapper.existsByAlias(alias, siteId) > 0;
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