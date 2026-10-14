package com.ujcms.cms.core.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.composition.CompositionCacheSync;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.generated.GeneratedBlockItem;
import com.ujcms.cms.core.listener.ChannelDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.BlockItemMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.cms.core.service.args.BlockItemArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.db.order.OrderEntityUtils;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import org.apache.ibatis.annotations.Param;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 区块项 Service
 *
 * @author PONY
 */
@Service
public class BlockItemService implements SiteDeleteListener, ChannelDeleteListener {
    private final AttachmentService attachmentService;
    private final ModelService modelService;
    private final BlockItemMapper mapper;
    private final BlockMapper blockMapper;
    private final HtmlSanitizeService sanitizeService;
    private final SnowflakeSequence snowflakeSequence;
    /**
     * 构成缓存作废（区块桥接来源的插槽内容随区块项变更，composition-invalidation 计划 §1——
     * 此前区块项编辑只能靠 TTL 兜底，为 §19.1 记录在案的缺口）
     */
    private final CompositionCacheSync cacheSync;

    public BlockItemService(AttachmentService attachmentService, ModelService modelService,
                            BlockItemMapper mapper, BlockMapper blockMapper,
                            HtmlSanitizeService sanitizeService, SnowflakeSequence snowflakeSequence,
                            CompositionCacheSync cacheSync) {
        this.attachmentService = attachmentService;
        this.modelService = modelService;
        this.mapper = mapper;
        this.blockMapper = blockMapper;
        this.sanitizeService = sanitizeService;
        this.snowflakeSequence = snowflakeSequence;
        this.cacheSync = cacheSync;
    }

    private static final String INVALIDATE_REASON = "block-item";

    public boolean countByBlockIdAndArticleId(@Param("blockId") Long blockId, @Param("articleId") Long articleId) {
        return PageMethod.offsetPage(0, 1, false).<Number>doSelectPage(() ->
                mapper.countByBlockIdAndArticleId(blockId, articleId)).iterator().next().intValue() > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(BlockItem bean) {
        bean.setId(snowflakeSequence.nextId());
        bean.setOrder(bean.getId());
        disassembleCustomsIfModelled(bean);
        mapper.insert(bean);
        attachmentService.insertRefer(GeneratedBlockItem.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
        cacheSync.invalidateAll(INVALIDATE_REASON);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(BlockItem bean) {
        disassembleCustomsIfModelled(bean);
        mapper.update(bean);
        attachmentService.updateRefer(GeneratedBlockItem.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
        cacheSync.invalidateAll(INVALIDATE_REASON);
    }

    /**
     * 当父 Block 设置了 blockItemModelId 时，调用 {@link BlockItem#disassembleCustoms} 把
     * {@code customs} map 序列化到 {@code mainsJson} / {@code clobsJson}。Block 未配置 Model
     * 时不动 JSON 列 — 现存自定义字段值得到保留，仅在编辑端不再展示，与计划文档第 8 步行为一致。
     */
    private void disassembleCustomsIfModelled(BlockItem bean) {
        Block block = blockMapper.select(bean.getBlockId());
        if (block == null || block.getBlockItemModelId() == null) {
            return;
        }
        Model model = modelService.select(block.getBlockItemModelId());
        if (model == null) {
            return;
        }
        bean.getBlock().setBlockItemModel(model);
        bean.disassembleCustoms(model, sanitizeService);
    }

    @Transactional(rollbackFor = Exception.class)
    public void moveOrder(Long fromId, Long toId) {
        OrderEntityUtils.move(mapper, fromId, toId);
        cacheSync.invalidateAll(INVALIDATE_REASON);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        int count = doDelete(id);
        cacheSync.invalidateAll(INVALIDATE_REASON);
        return count;
    }

    /**
     * 批量删除只作废一次（逐项走 {@link #delete(Long)} 会按项数递增戳记、逐项发 webhook）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        int count = ids.stream().filter(Objects::nonNull).mapToInt(this::doDelete).sum();
        cacheSync.invalidateAll(INVALIDATE_REASON);
        return count;
    }

    private int doDelete(Long id) {
        attachmentService.deleteRefer(GeneratedBlockItem.TABLE_NAME, id);
        return mapper.delete(id);
    }

    @Nullable
    public BlockItem select(Long id) {
        return mapper.select(id);
    }

    public List<BlockItem> selectList(BlockItemArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedBlockItem.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<BlockItem> selectList(BlockItemArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    /**
     * 一批文章的区块项，带上所属区块的 ID 与名称（文章列表用）
     */
    public List<BlockItem> listByArticleIds(Collection<Long> articleIds) {
        return articleIds.isEmpty() ? Collections.emptyList() : mapper.listByArticleIds(articleIds);
    }

    public boolean existsByBlockId(Long blockId, Long notSiteId) {
        return mapper.existsByBlockId(blockId, notSiteId) > 0;
    }

    public int deleteByArticleId(Long articleId) {
        int count = mapper.deleteByArticleId(articleId);
        if (count > 0) {
            cacheSync.invalidateAll(INVALIDATE_REASON);
        }
        return count;
    }

    @Override
    public void preChannelDelete(Long channelId) {
        if (mapper.deleteByChannelId(channelId) > 0) {
            cacheSync.invalidateAll(INVALIDATE_REASON);
        }
    }

    @Override
    public void preSiteDelete(Long siteId) {
        if (mapper.deleteBySiteId(siteId) > 0) {
            cacheSync.invalidateAll(INVALIDATE_REASON);
        }
    }

    @Override
    public int deleteListenerOrder() {
        // 栏目(100), 区块(200), 文章(300)、**区块项(400)**
        return 400;
    }
}
