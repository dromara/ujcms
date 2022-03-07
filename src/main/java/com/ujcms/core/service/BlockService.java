package com.ujcms.core.service;

import com.github.pagehelper.PageHelper;
import com.ujcms.core.domain.Block;
import com.ujcms.core.mapper.BlockMapper;
import com.ofwise.util.query.QueryInfo;
import com.ofwise.util.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 区块 Service
 *
 * @author PONY
 */
@Service
public class BlockService {
    private BlockMapper mapper;

    private SeqService seqService;

    public BlockService(BlockMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Block bean) {
        bean.setId(seqService.getNextVal(Block.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Block bean) {
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

    public List<Block> selectList(@Nullable Map<String, Object> queryMap) {
        QueryInfo queryInfo = QueryParser.parse(queryMap, Block.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Block> selectList(@Nullable Map<String, Object> queryMap,
                                  @Nullable Integer offset, @Nullable Integer limit) {
        return PageHelper.offsetPage(offset == null ? 0 : offset, limit == null ? Integer.MAX_VALUE : limit, false)
                .doSelectPage(() -> selectList(queryMap));
    }
}