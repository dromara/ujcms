package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.ext.domain.Vote;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 单次导出运行的上下文（theme-seed-export E1）：选择/闭包/分类/发射各阶段共享的状态。
 * 阶段次序（{@link ThemeSeedExporter}）：载入栏目 → 构成扫描（收集引用ID）→ 内容导出
 * （填充 originId 映射）→ 构成发射（消费映射做反向令牌化）。
 *
 * @author PONY
 */
final class SeedExportContext {
    final Long siteId;
    final String themeName;
    /**
     * 本主题来源标记（{@code theme:<name>}）
     */
    final String origin;
    final SeedExportRequest request;
    final SeedAssetExtractor.Session assets;
    final OffsetDateTime exportTime;
    /**
     * 导出文件束（{@code seed/} 下相对路径 → 字节，字典序）
     */
    final TreeMap<String, byte[]> files = new TreeMap<>();
    /**
     * 闭包诊断（§3 / §8）
     */
    final List<ExportPlan.RefIssue> issues = new ArrayList<>();
    /**
     * 待认领行（§4.2：第 3 类行的铸造三元组候选——引擎只收集，回写属 {@link SeedOriginAdopter}）
     */
    final List<SeedAdoption> adoptions = new ArrayList<>();
    /**
     * 站点全部栏目（树序：兄弟按 order/id，父先于子）
     */
    final List<Channel> channelsInTreeOrder = new ArrayList<>();
    final Map<Long, Channel> channelById = new LinkedHashMap<>();
    final Map<Long, String> channelAliasById = new LinkedHashMap<>();
    final Map<String, Long> channelIdByAlias = new LinkedHashMap<>();
    /**
     * 导出的栏目ID → 铸造/保留的 originId（文章 channel 引用与 parent 引用用）
     */
    final Map<Long, String> channelOriginById = new LinkedHashMap<>();
    /**
     * 导出集内文章ID → originId（反向令牌化 {{article:}} 与专题/区块条目绑定用）
     */
    final Map<Long, String> articleOriginById = new LinkedHashMap<>();
    /**
     * 站点可见区块（本站 ∪ 全局；E4a——blockRef 反向令牌化与缺省选择闭包用）
     */
    final Map<Long, Block> blockById = new LinkedHashMap<>();
    final Map<Long, String> blockAliasById = new LinkedHashMap<>();
    final Map<String, Long> blockIdByAlias = new LinkedHashMap<>();
    /**
     * 导出的区块ID → 铸造/保留的 originId（E4a）
     */
    final Map<Long, String> blockOriginById = new LinkedHashMap<>();
    /**
     * 本站投票（E4b——voteRef 反向令牌化与缺省选择闭包用；投票恒本站私有）
     */
    final Map<Long, Vote> voteById = new LinkedHashMap<>();
    /**
     * 导出的投票ID → 铸造/保留的 originId（E4b）
     */
    final Map<Long, String> voteOriginById = new LinkedHashMap<>();
    /**
     * 导出的问卷ID → 铸造/保留的 originId（E4d——问卷无引用面，映射仅供发射；无收窄令牌化）
     */
    final Map<Long, String> surveyOriginById = new LinkedHashMap<>();
    /**
     * 导出的表单类型ID → 铸造/保留的 originId（E4e——表单无引用面，映射仅供发射；无收窄令牌化）
     */
    final Map<Long, String> formTypeOriginById = new LinkedHashMap<>();
    /**
     * 导出的链接组ID → 铸造/保留的 originId（E4f——友链无引用面，映射仅供发射）
     */
    final Map<Long, String> linkGroupOriginById = new LinkedHashMap<>();
    /**
     * 站点可见模型（本站 ∪ 全局；model-seeding D6——绑定闭包与发射的解析域）
     */
    final Map<Long, Model> modelById = new LinkedHashMap<>();
    /**
     * 导出的模型ID → 铸造/保留的 originId
     */
    final Map<Long, String> modelOriginById = new LinkedHashMap<>();
    /**
     * 站点可见字典类型（本站 ∪ 全局；D8——模型 dictTypeId 绑定闭包的解析域）
     */
    final Map<Long, DictType> dictTypeById = new LinkedHashMap<>();
    /**
     * 站点可见字典类型 ID → 别名（模型投影/发射的别名改写用，D11）
     */
    final Map<Long, String> dictAliasById = new LinkedHashMap<>();
    /**
     * 导出的字典类型ID → 铸造/保留的 originId
     */
    final Map<Long, String> dictTypeOriginById = new LinkedHashMap<>();
    /**
     * 构成扫描收集的引用（§3.2 缺省选择第 2 项的输入）
     */
    final Set<Long> referencedChannelIds = new LinkedHashSet<>();
    final Set<Long> referencedArticleIds = new LinkedHashSet<>();
    final Set<Long> referencedBlockIds = new LinkedHashSet<>();
    final Set<Long> referencedVoteIds = new LinkedHashSet<>();
    /**
     * 导出的栏目ID集合
     */
    final Set<Long> exportedChannelIds = new LinkedHashSet<>();
    /**
     * 导出的区块ID集合（D27：blockRef 仅对导出集内的区块令牌化。投票无此集合——
     * {@code voteOriginById} 键集即导出集，文章同法，D30）
     */
    final Set<Long> exportedBlockIds = new LinkedHashSet<>();
    /**
     * 仅经闭包/引用触达而未导出的栏目（D7 linked 跳过——计数去重）
     */
    final Set<Long> linkedSkippedChannelIds = new LinkedHashSet<>();

    final Tally channels = new Tally();
    final Tally articles = new Tally();
    final Tally topics = new Tally();
    final Tally blocks = new Tally();
    final Tally votes = new Tally();
    final Tally surveys = new Tally();
    final Tally forms = new Tally();
    final Tally linkGroups = new Tally();
    final Tally models = new Tally();
    final Tally dicts = new Tally();
    final Tally panels = new Tally();
    final Tally regions = new Tally();
    int blockItems;
    int linkItems;

    /**
     * 值改写器（在栏目载入后由编排器装配——引用 {@code channelAliasById}/{@code articleOriginById}）
     */
    @Nullable
    SeedExportValueRewriter rewriter;

    SeedExportContext(Long siteId, String themeName, String origin, SeedExportRequest request,
                      SeedAssetExtractor.Session assets, OffsetDateTime exportTime) {
        this.siteId = siteId;
        this.themeName = themeName;
        this.origin = origin;
        this.request = request;
        this.assets = assets;
        this.exportTime = exportTime;
    }

    SeedExportValueRewriter rewriter() {
        if (rewriter == null) {
            throw new IllegalStateException("rewriter not initialized");
        }
        return rewriter;
    }

    /**
     * 来源分类计数器（§4 四类 + linked 跳过的可变累加形）
     */
    static final class Tally {
        int originUnmodified;
        int originModified;
        int operatorNew;
        int foreignOrigin;
        int linkedSkipped;

        void count(SeedOriginClass cls) {
            switch (cls) {
                case ORIGIN_UNMODIFIED -> originUnmodified++;
                case ORIGIN_MODIFIED -> originModified++;
                case OPERATOR_NEW -> operatorNew++;
                case FOREIGN_ORIGIN -> foreignOrigin++;
            }
        }

        ExportPlan.CategoryCounts toCounts() {
            return new ExportPlan.CategoryCounts(originUnmodified, originModified, operatorNew,
                    foreignOrigin, linkedSkipped);
        }
    }
}
