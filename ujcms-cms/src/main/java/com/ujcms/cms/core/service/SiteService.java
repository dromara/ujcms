package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.generated.GeneratedSite;
import com.ujcms.cms.core.listener.ModelDeleteListener;
import com.ujcms.cms.core.mapper.OrgMapper;
import com.ujcms.cms.core.mapper.SiteMapper;
import com.ujcms.cms.core.service.args.SiteArgs;
import com.ujcms.cms.core.support.SubDirRouteWords;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.LogicException;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 站点 Service
 *
 * @author PONY
 */
@Service
public class SiteService implements ModelDeleteListener {
    private static final Logger logger = LoggerFactory.getLogger(SiteService.class);

    private final HtmlSanitizeService sanitizeService;
    private final AttachmentService attachmentService;
    private final ModelService modelService;
    private final SiteMapper mapper;
    private final OrgMapper orgMapper;
    /**
     * 前台路由保留字，由 Web 层提供。惰性获取：路由映射本身（经拦截器）依赖本 Service，直接注入会形成循环依赖
     */
    @Nullable
    private ObjectProvider<SubDirRouteWords> routeWordsProvider;

    public SiteService(HtmlSanitizeService sanitizeService, AttachmentService attachmentService,
                       ModelService modelService, SiteMapper mapper, OrgMapper orgMapper) {
        this.sanitizeService = sanitizeService;
        this.attachmentService = attachmentService;
        this.modelService = modelService;
        this.mapper = mapper;
        this.orgMapper = orgMapper;
    }

    @Autowired
    public void setRouteWordsProvider(ObjectProvider<SubDirRouteWords> routeWordsProvider) {
        this.routeWordsProvider = routeWordsProvider;
    }

    /**
     * 站点设置模型仍被站点绑定时拒绝删除（此前只有外键兜底，报的是原始数据库错误）
     */
    @Override
    public void preModelDelete(Long modelId) {
        if (mapper.existsByModelId(modelId) > 0) {
            throw new LogicException("error.refer.site");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Site bean) {
        Model model = Optional.ofNullable(modelService.select(bean.getModelId())).orElseThrow(() ->
                new IllegalArgumentException(Model.NOT_FOUND + bean.getModelId()));
        bean.disassembleCustoms(model, sanitizeService);
        mapper.update(bean);
        attachmentService.updateRefer(GeneratedSite.TABLE_NAME, bean.getId(), bean.getAttachmentUrls(model));
    }

    /**
     * 校验站点设置模型的绑定（docs/spec/model-management/plan.md M11）：模型须存在、是站点设置模型、属于该站点或全局共享。
     * 只在控制台改换模型时调用（站点管理、站点设置的基础设置）；建站复制为新站点复制一份源站点的模型，不经此处
     */
    public void validateModel(Long siteId, Long modelId) {
        Model model = modelService.select(modelId);
        if (model == null || !Model.MODEL_TYPE_SITE.equals(model.getType())
                || !(model.isGlobal() || siteId.equals(model.getSiteId()))) {
            throw new Http400Exception("error.site.modelInvalid", String.valueOf(modelId));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateBase(Site bean) {
        mapper.updateBase(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDomain(Long id, String protocol, String domain) {
        mapper.updateDomain(id, protocol, domain);
    }

    @Nullable
    public Site select(Long id) {
        return mapper.select(id);
    }

    public Site getDefaultSite(Long defaultSiteId) {
        return Optional.ofNullable(select(defaultSiteId))
                .orElseThrow(() -> new IllegalStateException("default site not exist. id: " + defaultSiteId));
    }

    /**
     * 校验站点地址（域名+子目录）唯一性与语言组完整性，并将空白子目录规范化为 {@code null}
     * （保护 {@code findByDomain} 的 {@code sub_dir_ is null} 判定）。
     * 必须在两个写入口保存前调用：核心站点设置 {@code SiteSettingsController.updateBase}、
     * 商业版站点增改 {@code SiteT2Controller} create/update。
     *
     * <p>唯一性作用域：单域名模式下所有站点共享一个主机与一棵静态页目录树，子目录必须全局唯一；
     * 多域名模式下地址按 (domain_, sub_dir_) 组合唯一（详见 docs/spec/multilingual/addressing-core.md §3）。
     *
     * <p>路由保留字：子目录不能与前台路由的首个路径段（{@code search}、{@code login}、自定义栏目/文章 URL 前缀等）
     * 同名。字面路径段的匹配优先级高于 {@code /{subDir}}，同名会使该站点首页不可达且没有任何报错。
     *
     * @param bean        待保存的站点（可能被修改：空白子目录置为 {@code null}）
     * @param multiDomain 是否开启多域名（{@code Config.multiDomain}）
     */
    public void validateAddress(Site bean, boolean multiDomain) {
        if (StringUtils.isBlank(bean.getSubDir())) {
            bean.setSubDir(null);
        }
        String subDir = bean.getSubDir();
        validateSubDirNotRouteWord(subDir);
        Long excludeId = bean.getId();
        if (multiDomain) {
            if (mapper.countByDomainAndSubDir(bean.getDomain(), subDir, excludeId) > 0) {
                if (subDir != null) {
                    throw new Http400Exception("error.siteSubDirExist", subDir);
                }
                throw new Http400Exception("error.siteDomainExist", bean.getDomain());
            }
        } else if (subDir != null && mapper.countBySubDir(subDir, excludeId) > 0) {
            throw new Http400Exception("error.siteSubDirExist", subDir);
        }
        validateLangGroup(bean);
    }

    private void validateSubDirNotRouteWord(@Nullable String subDir) {
        if (subDir == null || routeWordsProvider == null) {
            return;
        }
        SubDirRouteWords routeWords = routeWordsProvider.getIfAvailable();
        if (routeWords != null && routeWords.routeWords().contains(subDir.toLowerCase(Locale.ROOT))) {
            throw new Http400Exception("error.siteSubDirReserved", subDir);
        }
    }

    /**
     * 校验站点归属组织：须为已存在的后台组织。前台组织(0)为会员注册保留组织，站点归属其下
     * 会被控制台站点选择器（按管理员组织权限过滤）排除而"消失"；Site 域默认 orgId=0，
     * 请求体未携带 orgId 时同样落 0，一并拒绝。
     *
     * @param bean 待保存的站点
     */
    public void validateOrg(Site bean) {
        Long orgId = bean.getOrgId();
        if (orgId == null || orgId == 0L) {
            throw new Http400Exception("error.siteOrgRequired");
        }
        if (orgMapper.select(orgId) == null) {
            throw new Http400Exception("error.orgIdNotExist", String.valueOf(orgId));
        }
    }

    private void validateLangGroup(Site bean) {
        Long langParentId = bean.getLangParentId();
        if (langParentId == null) {
            return;
        }
        if (langParentId.equals(bean.getId())) {
            throw new Http400Exception(String.format("Site langParentId cannot reference itself. ID: %s",
                    bean.getId()));
        }
        Site master = mapper.selectRefer(langParentId);
        if (master == null) {
            throw new Http400Exception(String.format("Site langParentId not found: %s", langParentId));
        }
        // 语言组仅一层：目标必须是组主站点，不允许变体再挂变体（链）
        if (!master.isLangMaster()) {
            throw new Http400Exception(String.format(
                    "Site langParentId must reference a lang master (no chains). ID: %s, its langParentId: %s",
                    langParentId, master.getLangParentId()));
        }
        // 同一语言组内（含主站点）语言不可重复
        if (mapper.countByLangGroupAndLang(langParentId, bean.getLang(), bean.getId()) > 0) {
            throw new Http400Exception(String.format(
                    "Site lang already exists in lang group. lang: %s, master site ID: %s",
                    bean.getLang(), langParentId));
        }
    }

    /**
     * 删除前校验语言组完整性：仍有语言变体的组主站点不允许删除，防止变体悬挂、
     * 以及站点删除时的静态页目录递归清理误删变体产物。先删除（或改挂）全部变体后方可删除主站点。
     *
     * @param bean 待删除的站点
     */
    public void validateLangGroupBeforeDelete(Site bean) {
        int variants = mapper.countByLangParentId(bean.getId());
        if (variants > 0) {
            throw new Http400Exception(String.format(
                    "Cannot delete site(id=%s): it is the master of a lang group with %s variant(s). "
                            + "Delete the variants first.", bean.getId(), variants));
        }
    }

    @Nullable
    public Site findByDomain(String domain) {
        return mapper.findByDomain(domain);
    }

    /**
     * 根据 (域名, 子目录) 组合查找站点。组合寻址（multilingual P2）的权威匹配
     *
     * @param domain 域名
     * @param subDir 子目录
     * @return 站点。没有找到数据，则返回 {@code null}
     */
    @Nullable
    public Site findByDomainAndSubDir(String domain, String subDir) {
        return mapper.findByDomainAndSubDir(domain, subDir);
    }

    /**
     * 语言组成员列表（含主站点与自身；仅正常状态；按 order_, id_ 排序）。
     * 不在任何组且没有变体时短路返回自身，避免绝大多数（未分组）站点的额外查询
     *
     * @param site 站点
     * @return 语言组成员站点列表
     */
    public List<Site> listByLangGroup(Site site) {
        if (!site.isInLangGroup() && !hasLangVariants(site)) {
            return Collections.singletonList(site);
        }
        return mapper.listByLangGroup(site.getLangGroupMasterId());
    }

    private boolean hasLangVariants(Site site) {
        return mapper.countByLangParentId(site.getId()) > 0;
    }

    /**
     * 组内指定语言的站点。精确匹配优先，其次主语言前缀匹配（en-US ~ en）
     *
     * @param masterId 语言组主站点ID
     * @param lang     语言(BCP-47)
     * @return 站点。没有匹配语言，则返回 {@code null}
     */
    @Nullable
    public Site findByLangGroupAndLang(Long masterId, String lang) {
        List<Site> members = mapper.listByLangGroup(masterId);
        for (Site member : members) {
            if (lang.equalsIgnoreCase(member.getLang())) {
                return member;
            }
        }
        String primary = StringUtils.substringBefore(lang, "-");
        for (Site member : members) {
            if (primary.equalsIgnoreCase(StringUtils.substringBefore(member.getLang(), "-"))) {
                return member;
            }
        }
        return null;
    }

    /**
     * 根据子目录全局查找站点（遗留语义，作为组合寻址的第二级回退）：
     * 0条→{@code null}；1条→该站点；多条→{@code null} 并 WARN。
     * 跨域名重复子目录出现后，全局查找天然歧义——返回 {@code null}（上层404）并提示运维补全 domain_，
     * 好过确定性地返回错误站点。配置正确的部署中此路径不会命中（组合查找先命中）
     *
     * @param subDir 子目录
     * @return 站点。没有找到数据或存在歧义，则返回 {@code null}
     */
    @Nullable
    public Site findBySubDir(String subDir) {
        List<Site> list = mapper.listBySubDir(subDir);
        if (list.size() > 1) {
            logger.warn("Global sub-dir lookup ambiguous: {} sites share subDir '{}'."
                    + " Fill in correct domain_ on each site so the composite (domain, subDir) lookup applies.",
                    list.size(), subDir);
            return null;
        }
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 与根站点同一主机下的子目录站点（正常状态）。爬虫只读主机根的 robots.txt，
     * 根站点据此为子目录站点补 {@code Sitemap:} 行（docs/spec/ai/geo.md §6）
     *
     * @param root 根站点（无子目录）
     * @return 子目录站点列表；根站点自身带子目录时为空
     */
    public List<Site> listSubDirSites(Site root) {
        if (StringUtils.isNotBlank(root.getSubDir())) {
            return Collections.emptyList();
        }
        boolean multiDomain = Boolean.TRUE.equals(root.getConfig().getMultiDomain());
        return mapper.listSubDirSites(multiDomain ? root.getDomain() : null);
    }

    public List<Site> selectList(SiteArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedSite.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo, args.isQueryHasChildren(), args.getFullOrgId());
    }

    public List<Site> selectList(SiteArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Site> selectPage(SiteArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public List<Site> listByUserIdAndOrgId(Long userId, Long orgId) {
        return mapper.listByUserIdAndOrgId(userId, orgId);
    }

    @Nullable
    public Site findFirstByUserIdAndOrgId(Long userId, Long orgId) {
        List<Site> list = PageMethod.offsetPage(0, 1).doSelectPage(() -> mapper.listByUserIdAndOrgId(userId, orgId));
        return list.stream().findFirst().orElse(null);
    }
}