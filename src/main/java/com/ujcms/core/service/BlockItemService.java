package com.ujcms.core.service;

import com.github.pagehelper.PageHelper;
import com.ujcms.core.domain.BlockItem;
import com.ujcms.core.mapper.BlockItemMapper;
import com.ofwise.util.query.QueryInfo;
import com.ofwise.util.query.QueryParser;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 区块项 Service
 *
 * @author PONY
 */
@Service
public class BlockItemService {
    private AttachmentService attachmentService;
    private BlockItemMapper mapper;
    private SeqService seqService;

    public BlockItemService(AttachmentService attachmentService, BlockItemMapper mapper, SeqService seqService) {
        this.attachmentService = attachmentService;
        this.mapper = mapper;
        this.seqService = seqService;
    }

    public int countByBlockIdAndArticleId(@Param("blockId") Integer blockId, @Param("articleId") Integer articleId) {
        return mapper.countByBlockIdAndArticleId(blockId, articleId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(BlockItem bean) {
        bean.setId(seqService.getNextVal(BlockItem.TABLE_NAME));
        mapper.insert(bean);
        attachmentService.insertRefer(BlockItem.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(BlockItem bean) {
        mapper.update(bean);
        attachmentService.updateRefer(BlockItem.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<BlockItem> list) {
        short order = 1;
        for (BlockItem bean : list) {
            bean.setOrder(order);
            mapper.update(bean);
            order += 1;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        attachmentService.deleteRefer(BlockItem.TABLE_NAME, id);
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public BlockItem select(Integer id) {
        return mapper.select(id);
    }

    public List<BlockItem> selectList(@Nullable Map<String, Object> queryMap) {
        QueryInfo queryInfo = QueryParser.parse(queryMap, BlockItem.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<BlockItem> selectList(@Nullable Map<String, Object> queryMap,
                                      @Nullable Integer offset, @Nullable Integer limit) {
        return PageHelper.offsetPage(offset == null ? 0 : offset, limit == null ? Integer.MAX_VALUE : limit, false)
                .doSelectPage(() -> selectList(queryMap));
    }
}