package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.SeedExportContext.Tally;
import com.ujcms.cms.core.composition.ThemeRegionSeedExporter.RegionExport;
import com.ujcms.cms.core.component.ThemeInstallValidator;
import com.ujcms.cms.core.composition.fields.FieldTypeRegistry;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.ChannelExt;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.mapper.ChannelExtMapper;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.DictTypeService;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.service.args.BlockArgs;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.cms.core.service.args.DictTypeArgs;
import com.ujcms.cms.core.service.args.ModelArgs;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.service.VoteService;
import com.ujcms.cms.ext.service.args.VoteArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主题种子导出引擎（theme-seed-export E1；{@link ThemeSeedOrchestrator} 的镜像编排）。
 * 把站点的主题域内容图谱 + 构成导出为可安装的种子文件束（{@link SeedExportBundle}）：
 *
 * <pre>
 * 载入栏目（树序）→ 构成扫描（收集引用，§3.2）→ 内容导出（选择/闭包/分类/铸造/发射）
 *   → 构成发射（反向令牌化，§6）→ 资源汇入 → 计划汇总（§8）
 * </pre>
 *
 * <p><b>只读</b>（D2）：引擎绝不写库、不写存储——provenance 回写（adopt 模式）与打包落盘
 * （ZIP / refreshSeedDir）属 E2。<b>确定性</b>（D4）：同一站点内容两次导出字节一致——
 * 序列化经 {@link SeedExportBundle#canonicalJsonBytes}（{@link SeedHash} 规范口径），文件布局
 * 与遍历序全部确定（§7.1）。<b>fail-loud</b>（§5.1）：构造时断言容器内全部 Shape 字段类型
 * 已在 {@link SeedExportFieldRules} 声明——商业模块贡献新字段类型而未声明时启动即失败。
 *
 * @author PONY
 */
@Component
public class ThemeSeedExporter {
    private static final Logger logger = LoggerFactory.getLogger(ThemeSeedExporter.class);

    private final ThemeContentSeedExporter contentSeedExporter;
    private final ThemeRegionSeedExporter regionSeedExporter;
    private final SeedAssetExtractor assetExtractor;
    private final ChannelService channelService;
    private final ChannelExtMapper channelExtMapper;
    private final BlockService blockService;
    private final VoteService voteService;
    private final ModelService modelService;
    private final DictTypeService dictTypeService;
    private final ConfigService configService;

    /**
     * 安装侧大小上限（与 {@code ThemeInstallService} 同一属性；超限的导出不可安装，§7.3）
     */
    @Value("${ujcms.theme.install.maxSizeMb:" + ThemeInstallValidator.DEFAULT_MAX_SIZE_MB + "}")
    private int maxSizeMb = ThemeInstallValidator.DEFAULT_MAX_SIZE_MB;

    public ThemeSeedExporter(ThemeContentSeedExporter contentSeedExporter,
                             ThemeRegionSeedExporter regionSeedExporter, SeedAssetExtractor assetExtractor,
                             ChannelService channelService, ChannelExtMapper channelExtMapper,
                             BlockService blockService,
                             VoteService voteService, ModelService modelService,
                             DictTypeService dictTypeService, ConfigService configService,
                             FieldTypeRegistry fieldTypeRegistry) {
        this.contentSeedExporter = contentSeedExporter;
        this.regionSeedExporter = regionSeedExporter;
        this.assetExtractor = assetExtractor;
        this.channelService = channelService;
        this.channelExtMapper = channelExtMapper;
        this.blockService = blockService;
        this.voteService = voteService;
        this.modelService = modelService;
        this.dictTypeService = dictTypeService;
        this.configService = configService;
        // §5.1 完备性断言：每个字段类型必须声明能否承载文件 URL（fail-loud，启动即失败）
        SeedExportFieldRules.assertShapeTypesDeclared(fieldTypeRegistry.names());
    }

    /**
     * 导出站点的主题种子（只读，D2）。
     *
     * @param siteId    站点ID
     * @param themeName 主题名（目录名；来源标记 {@code theme:<name>} 与 {@code {{theme}}} 反向的锚点）
     * @param request   选择与范围
     * @return 文件束 + 计划报告
     */
    public SeedExportBundle export(Long siteId, String themeName, SeedExportRequest request) {
        String origin = ThemeSeedImporter.originOf(themeName);
        SeedAssetExtractor.Session assets = assetExtractor.open(siteId, themeName);
        SeedExportContext ctx = new SeedExportContext(siteId, themeName, origin, request, assets,
                OffsetDateTime.now());
        loadChannels(ctx);
        loadBlocks(ctx);
        loadVotes(ctx);
        loadModels(ctx);
        loadDicts(ctx);
        // D19/D27：内容在导出范围内时 channelRef/blockRef 仅对导出集内的行令牌化（发射在内容导出之后，
        // 集合已就绪）；仅构成导出无内容集可依，传 null——全部可解析别名照常令牌化（数据包的迟绑就是全部意义）
        ctx.rewriter = new SeedExportValueRewriter(assets, themePrefix(siteId, themeName),
                ctx.channelAliasById, ctx.articleOriginById, ctx.blockAliasById, ctx.voteOriginById,
                request.includeContent() ? ctx.exportedChannelIds : null,
                request.includeContent() ? ctx.exportedBlockIds : null, ctx.issues);

        // 构成扫描先行：引用ID喂给内容侧的缺省选择（§3.2）；发射在内容导出之后（originId 映射就绪）
        List<RegionExport> regionExports = request.includeComposition()
                ? regionSeedExporter.scan(ctx) : List.of();
        if (request.includeContent()) {
            contentSeedExporter.export(ctx);
        }
        if (request.includeComposition()) {
            regionSeedExporter.emit(ctx, regionExports);
        }
        // 构成引用但未导出的栏目：D7 linked 跳过（{{channel:别名}} 迟绑，不断链——只计数）
        for (Long channelId : ctx.referencedChannelIds) {
            if (!ctx.exportedChannelIds.contains(channelId) && ctx.channelById.containsKey(channelId)) {
                ctx.linkedSkippedChannelIds.add(channelId);
            }
        }
        assets.assets().forEach((relpath, bytes) -> ctx.files.put("content/assets/" + relpath, bytes));

        SeedExportBundle bundle = new SeedExportBundle(ctx.files, buildPlan(ctx), List.copyOf(ctx.adoptions));
        logger.info("Theme seed exported: site={}, theme={}, files={}, bytes={}, plan={}",
                siteId, themeName, bundle.files().size(), bundle.plan().totalBytes(), bundle.plan());
        return bundle;
    }

    /**
     * 站点栏目全量载入（树序：兄弟按 order/id，父先于子——铸造与发射的确定性遍历序）。
     */
    private void loadChannels(SeedExportContext ctx) {
        List<Channel> channels = channelService.selectList(ChannelArgs.of().siteId(ctx.siteId));
        // 列表查询不带 ext（列表 resultMap 无 ext 关联）——投影含 ext.text，缺挂会静默丢正文
        // 及其内嵌资源引用，且散列必错判为「操作员已改」；批量补挂
        Map<Long, ChannelExt> extById = new LinkedHashMap<>();
        for (ChannelExt ext : channelExtMapper.listBySiteId(ctx.siteId)) {
            extById.put(ext.getId(), ext);
        }
        for (Channel channel : channels) {
            ChannelExt ext = extById.get(channel.getId());
            if (ext != null) {
                channel.setExt(ext);
            }
        }
        Map<Long, List<Channel>> byParent = new LinkedHashMap<>(channels.size());
        List<Channel> roots = new ArrayList<>();
        for (Channel channel : channels) {
            ctx.channelById.put(channel.getId(), channel);
        }
        for (Channel channel : channels) {
            Long parentId = channel.getParentId();
            if (parentId == null || !ctx.channelById.containsKey(parentId)) {
                roots.add(channel);
            } else {
                byParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(channel);
            }
        }
        Comparator<Channel> siblingOrder = Comparator.comparing(Channel::getOrder)
                .thenComparing(Channel::getId);
        roots.sort(siblingOrder);
        appendDepthFirst(ctx, roots, byParent, siblingOrder);
        for (Channel channel : ctx.channelsInTreeOrder) {
            ctx.channelAliasById.put(channel.getId(), channel.getSlug());
            ctx.channelIdByAlias.putIfAbsent(channel.getSlug(), channel.getId());
        }
    }

    private void appendDepthFirst(SeedExportContext ctx, List<Channel> level,
                                  Map<Long, List<Channel>> byParent, Comparator<Channel> siblingOrder) {
        for (Channel channel : level) {
            ctx.channelsInTreeOrder.add(channel);
            List<Channel> children = byParent.get(channel.getId());
            if (children != null) {
                children.sort(siblingOrder);
                appendDepthFirst(ctx, children, byParent, siblingOrder);
            }
        }
    }

    /**
     * 站点可见区块全量载入（本站 ∪ 全局，E4a）：blockRef 反向令牌化与缺省选择闭包的解析域。
     * 别名映射本站行优先（与 {@code {{block:}}} 展开的胜出规则一致）。
     */
    private void loadBlocks(SeedExportContext ctx) {
        List<Block> blocks = blockService.selectList(BlockArgs.of().scopeSiteId(ctx.siteId));
        for (Block block : blocks) {
            ctx.blockById.put(block.getId(), block);
            ctx.blockAliasById.put(block.getId(), block.getAlias());
        }
        // 本站行优先登记别名（全局同名不覆盖）
        blocks.stream().filter(block -> ctx.siteId.equals(block.getSiteId()))
                .forEach(block -> ctx.blockIdByAlias.putIfAbsent(block.getAlias(), block.getId()));
        blocks.forEach(block -> ctx.blockIdByAlias.putIfAbsent(block.getAlias(), block.getId()));
    }

    /**
     * 本站投票全量载入（E4b）：voteRef 反向令牌化与缺省选择闭包的解析域（投票恒本站私有，
     * 无别名——解析仅按ID）。
     */
    private void loadVotes(SeedExportContext ctx) {
        for (Vote vote : voteService.selectList(VoteArgs.of().siteId(ctx.siteId))) {
            ctx.voteById.put(vote.getId(), vote);
        }
    }

    /**
     * 站点可见模型全量载入（本站 ∪ 全局，model-seeding D6）：绑定闭包（channelModelId/
     * articleModelId/topicModelId/blockItemModelId）与发射的解析域。
     */
    private void loadModels(SeedExportContext ctx) {
        for (Model model : modelService.selectList(ModelArgs.of().scopeSiteId(ctx.siteId))) {
            ctx.modelById.put(model.getId(), model);
        }
    }

    /**
     * 站点可见字典类型全量载入（本站 ∪ 全局，D8）：模型字段集 dictTypeId 绑定闭包与
     * 别名改写（D11——投影/发射共用）的解析域。
     */
    private void loadDicts(SeedExportContext ctx) {
        for (DictType dictType : dictTypeService.selectList(DictTypeArgs.of().scopeSiteId(ctx.siteId))) {
            ctx.dictTypeById.put(dictType.getId(), dictType);
            ctx.dictAliasById.put(dictType.getId(), dictType.getAlias());
        }
    }

    private String themePrefix(Long siteId, String themeName) {
        String url = configService.getUnique().getTemplateStorage().getUrl();
        String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        return base + "/" + siteId + "/" + themeName;
    }

    private ExportPlan buildPlan(SeedExportContext ctx) {
        Tally channels = ctx.channels;
        channels.linkedSkipped = ctx.linkedSkippedChannelIds.size();
        List<ExportPlan.RefIssue> issues = new ArrayList<>(ctx.issues);
        issues.addAll(ctx.assets.issues());
        long totalBytes = ctx.files.values().stream().mapToLong(bytes -> bytes.length).sum();
        long maxBytes = ThemeInstallValidator.megabytesToBytes(maxSizeMb);
        return new ExportPlan(channels.toCounts(), ctx.articles.toCounts(), ctx.topics.toCounts(),
                ctx.blocks.toCounts(), ctx.votes.toCounts(), ctx.surveys.toCounts(),
                ctx.forms.toCounts(), ctx.linkGroups.toCounts(), ctx.models.toCounts(),
                ctx.dicts.toCounts(),
                ctx.panels.toCounts(), ctx.regions.toCounts(), ctx.blockItems, ctx.linkItems,
                ctx.assets.assetBytes(), ctx.assets.largestAssets(10), ctx.assets.externalUrls(),
                issues, totalBytes, maxBytes, totalBytes > maxBytes);
    }

    void setMaxSizeMb(int maxSizeMb) {
        this.maxSizeMb = maxSizeMb;
    }
}
