package com.ujcms.cms.core.service;

import java.util.List;
import java.util.Optional;

import com.ujcms.common.web.exception.Http400Exception;
import org.apache.commons.lang3.StringUtils;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.component.SlugGenerator;
import com.ujcms.cms.core.domain.Topic;
import com.ujcms.cms.core.domain.generated.GeneratedTopic;
import com.ujcms.cms.core.listener.ModelDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.ArticleTopicMapper;
import com.ujcms.cms.core.mapper.TopicMapper;
import com.ujcms.cms.core.mapper.TopicTreeMapper;
import com.ujcms.cms.core.service.args.TopicArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.db.tree.TreeMoveType;
import com.ujcms.common.db.tree.TreeService;
import com.ujcms.common.db.tree.TreeSortEntity;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.exception.LogicException;

/**
 * 专题 Service
 *
 * <p>整体形状参考 {@link OrgService}（不带文章/栏目权限的简单树形 Service）。
 * 与 Org 的差异：专题带 Model 驱动的自定义字段，因此构造器额外注入
 * {@link ModelService} / {@link HtmlSanitizeService} 以便在 insert/update 时
 * 调用 {@code Topic#disassembleCustoms}。整张表自带 {@code text_ / markdown_ /
 * mains_json_ / clobs_json_}，不再拆分 {@code ujcms_topic_ext}。
 *
 * @author PONY
 */
@Service
public class TopicService implements ModelDeleteListener, SiteDeleteListener {
    private final TopicMapper mapper;
    private final TopicTreeMapper treeMapper;
    private final ArticleTopicMapper articleTopicMapper;
    private final ModelService modelService;
    private final HtmlSanitizeService sanitizeService;
    private final SnowflakeSequence snowflakeSequence;
    private final SlugGenerator slugGenerator;
    private final TreeService<Topic> treeService;

    public TopicService(TopicMapper mapper, TopicTreeMapper treeMapper, ArticleTopicMapper articleTopicMapper,
                        ModelService modelService, HtmlSanitizeService sanitizeService, SnowflakeSequence snowflakeSequence,
                        SlugGenerator slugGenerator) {
        this.mapper = mapper;
        this.treeMapper = treeMapper;
        this.articleTopicMapper = articleTopicMapper;
        this.modelService = modelService;
        this.sanitizeService = sanitizeService;
        this.snowflakeSequence = snowflakeSequence;
        this.slugGenerator = slugGenerator;
        this.treeService = new TreeService<>(mapper, treeMapper);
    }

    /**
     * 别名留空时由服务端生成（docs/spec/permalinks/plan.md D4）：标题 slugify → 拼音（站点开关）→ ID，站内唯一
     */
    private void fillSlug(Topic bean) {
        if (StringUtils.isBlank(bean.getSlug())) {
            // 站点行锁之下、以锁定读查重（docs/spec/article-data-permission/plan.md D22）
            bean.setSlug(slugGenerator.generate(bean.getSiteId(), bean.getTitle(), bean.getId(),
                    SlugGenerator.TOPIC_MAX_LENGTH, slug -> {
                        Long other = mapper.selectIdBySlugForUpdate(bean.getSiteId(), slug);
                        return other != null && !other.equals(bean.getId());
                    }));
        }
    }

    /**
     * 校验专题绑定的模型（docs/spec/article-data-permission/plan.md §13.5，与栏目、表单类型同一规则 D16）：存在、是专题模型、
     * 属于本站或全局共享。只在控制台新增、改换模型时调用
     */
    public void validateModel(Long siteId, Long modelId) {
        Model model = modelService.select(modelId);
        if (model == null || !Topic.MODEL_TYPE.equals(model.getType())
                || !(model.isGlobal() || siteId.equals(model.getSiteId()))) {
            throw new Http400Exception("error.topic.modelInvalid", String.valueOf(modelId));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Topic bean) {
        bean.setId(snowflakeSequence.nextId());
        fillSlug(bean);
        Model model = Optional.ofNullable(modelService.select(bean.getTopicModelId())).orElseThrow(() ->
                new IllegalArgumentException(Model.NOT_FOUND + bean.getTopicModelId()));
        bean.disassembleCustoms(model, sanitizeService);
        treeService.insert(bean, bean.getSiteId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Topic bean) {
        fillSlug(bean);
        Model model = Optional.ofNullable(modelService.select(bean.getTopicModelId())).orElseThrow(() ->
                new IllegalArgumentException(Model.NOT_FOUND + bean.getTopicModelId()));
        bean.disassembleCustoms(model, sanitizeService);
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void move(Topic from, Topic to, TreeMoveType type, Long siteId) {
        treeService.move(from, to, type, siteId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Topic bean) {
        // 与专题关联的文章引用，由 ujcms_article_topic 上 fk_article_topic_topic 的 ON DELETE CASCADE
        // 自动清理；这里显式调用一次以兼容部分国产库未严格执行级联删除的场景，并让单元测试无需依赖 FK。
        articleTopicMapper.deleteByTopicId(bean.getId());
        return treeService.delete(bean.getId(), bean.getOrder(), bean.getSiteId());
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        Topic bean = mapper.select(id);
        if (bean == null) {
            return 0;
        }
        return delete(bean);
    }

    public List<TreeSortEntity> toTree(List<Topic> list) {
        return treeService.toTree(list);
    }

    public List<Topic> listForTidy(Long siteId) {
        return mapper.listBySiteIdForTidy(siteId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void tidyTreeOrderAndDepth(List<TreeSortEntity> tree, int size) {
        treeService.tidyTreeOrderAndDepth(tree, size);
    }

    /**
     * 在调用 {@link #tidyTreeRelation} 之前清空当前站点的闭包表。
     * {@code TreeService.tidyTreeRelation} 通过 {@code saveBatch} 直接 INSERT，
     * 不是 UPSERT，因此必须先清理旧关系，否则会触发主键冲突。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRelation(Long siteId) {
        treeMapper.deleteBySiteId(siteId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void tidyTreeRelation(List<TreeSortEntity> tree, int size) {
        treeService.tidyTreeRelation(tree, size);
    }

    @Nullable
    public Topic select(Long id) {
        return mapper.select(id);
    }

    @Nullable
    public Topic findBySiteIdAndSlug(Long siteId, String slug) {
        return mapper.findBySiteIdAndSlug(siteId, slug);
    }

    public List<Topic> selectList(TopicArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedTopic.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo, args.getAncestorId(), args.isQueryHasChildren());
    }

    public List<Topic> selectList(TopicArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public com.github.pagehelper.Page<Topic> selectPage(TopicArgs args, int page, int pageSize) {
        try (com.github.pagehelper.Page<Topic> startPage = PageMethod.startPage(page, pageSize)) {
            return startPage.doSelectPage(() -> selectList(args));
        }
    }

    public List<Topic> listBySiteId(Long siteId) {
        return mapper.listBySiteId(siteId);
    }

    public List<Topic> listChildren(Long parentId) {
        return mapper.listChildren(parentId);
    }

    public boolean existsBySlug(String slug, Long siteId) {
        return mapper.existsBySlug(slug, siteId) > 0;
    }

    public boolean existsByModelId(Long modelId) {
        return mapper.existsByModelId(modelId) > 0;
    }

    @Override
    public void preModelDelete(Long modelId) {
        if (existsByModelId(modelId)) {
            throw new LogicException("error.refer.topic");
        }
    }

    @Override
    public void preSiteDelete(Long siteId) {
        treeMapper.deleteBySiteId(siteId);
        mapper.updateParentIdToNull(siteId);
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }
}
