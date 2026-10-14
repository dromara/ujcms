package com.ujcms.cms.core.service;

import com.ujcms.common.web.exception.Http400Exception;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.generated.GeneratedBlock;
import com.ujcms.cms.core.listener.ModelDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.BlockItemMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.cms.core.service.args.BlockArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.exception.LogicException;
import org.jspecify.annotations.Nullable;
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
public class BlockService implements ModelDeleteListener, SiteDeleteListener {
    private final BlockItemMapper itemMapper;
    private final BlockMapper mapper;
    private final ModelService modelService;
    private final SnowflakeSequence snowflakeSequence;

    public BlockService(BlockItemMapper itemMapper, BlockMapper mapper, ModelService modelService,
                        SnowflakeSequence snowflakeSequence) {
        this.itemMapper = itemMapper;
        this.mapper = mapper;
        this.modelService = modelService;
        this.snowflakeSequence = snowflakeSequence;
    }

    /**
     * 校验区块项模型的绑定（docs/spec/model-management/plan.md M11）：模型须存在、是区块项模型；本站区块可用本站或全局共享的
     * 模型，全局区块只可用全局共享的模型（区块项分属各站，模型须对每个站点都可见）。只在控制台新增、改换模型或改变共享范围时
     * 调用——种子导入按名称在本站内解析模型，另有保证，不经此处
     *
     * @param siteId  区块所属站点。全局区块为 null
     * @param modelId 区块项模型ID
     */
    public void validateModel(@Nullable Long siteId, Long modelId) {
        Model model = modelService.select(modelId);
        if (model == null || !Block.MODEL_TYPE.equals(model.getType())
                || !(model.isGlobal() || (siteId != null && siteId.equals(model.getSiteId())))) {
            throw new Http400Exception("error.block.modelInvalid", String.valueOf(modelId));
        }
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

    /**
     * 区块列表。全局区块在前、本站区块在后，组内按 order：排序按共享范围分组维护（控制台逐组提交），
     * 两组的 order 各自从 1 计，混排无意义（与模型、字典类型一致）。
     */
    public List<Block> selectList(BlockArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedBlock.TABLE_NAME, "scope_desc,order,id");
        return mapper.selectAll(queryInfo, args.getItemCountSiteId());
    }

    public List<Block> selectList(BlockArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public boolean existsByAlias(String alias, @Nullable Long siteId) {
        return mapper.existsByAlias(alias, siteId) > 0;
    }

    /**
     * 按站点可见范围 + 别名查找区块（本站 ∪ 全局共享，与控制台区块目录同口径）。
     * 供面板设置 {@code blockRef} 的别名形态解析（component-settings §5）。
     */
    @Nullable
    public Block findByScopeSiteIdAndAlias(Long siteId, String alias) {
        List<Block> list = selectList(BlockArgs.of().scopeSiteId(siteId).alias(alias), 0, 1);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<String> listCategories(@Nullable Long siteId) {
        return mapper.listCategories(siteId);
    }

    public boolean existsByModelId(Long modelId) {
        return mapper.existsByModelId(modelId) > 0;
    }

    @Override
    public void preModelDelete(Long modelId) {
        if (existsByModelId(modelId)) {
            throw new LogicException("error.refer.block");
        }
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