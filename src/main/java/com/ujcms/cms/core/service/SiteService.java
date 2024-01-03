package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.domain.base.SiteBase;
import com.ujcms.cms.core.domain.base.SiteCustomBase;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.SiteCustomMapper;
import com.ujcms.cms.core.mapper.SiteMapper;
import com.ujcms.cms.core.mapper.SiteTreeMapper;
import com.ujcms.cms.core.service.args.SiteArgs;
import com.ujcms.commons.db.tree.TreeService;
import com.ujcms.commons.file.FileHandler;
import com.ujcms.commons.query.CustomFieldQuery;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import com.ujcms.commons.web.PathResolver;
import org.apache.commons.lang3.StringUtils;
import org.owasp.html.PolicyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 站点 Service
 *
 * @author PONY
 */
@Service
public class SiteService {
    private final PolicyFactory policyFactory;
    private final PathResolver pathResolver;
    private final AttachmentService attachmentService;
    private final ModelService modelService;
    private final BlockService blockService;
    private final DictTypeService dictTypeService;
    private final ChannelService channelService;
    private final SeqService seqService;
    private final SiteMapper mapper;
    private final SiteTreeMapper treeMapper;
    private final SiteCustomMapper customMapper;
    private final TreeService<Site, SiteTree> treeService;

    public SiteService(PolicyFactory policyFactory, PathResolver pathResolver, AttachmentService attachmentService,
                       ModelService modelService, BlockService blockService, DictTypeService dictTypeService,
                       ChannelService channelService, SeqService seqService, SiteMapper mapper,
                       SiteTreeMapper treeMapper, SiteCustomMapper customMapper) {
        this.policyFactory = policyFactory;
        this.pathResolver = pathResolver;
        this.attachmentService = attachmentService;
        this.modelService = modelService;
        this.blockService = blockService;
        this.dictTypeService = dictTypeService;
        this.channelService = channelService;
        this.seqService = seqService;
        this.mapper = mapper;
        this.treeMapper = treeMapper;
        this.customMapper = customMapper;
        this.treeService = new TreeService<>(mapper, treeMapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void copy(Site site) {
        Integer copyFromId = site.getCopyFromId();
        if (copyFromId == null) {
            throw new IllegalArgumentException("Site copyFromId cannot be null");
        }
        Site from = Optional.ofNullable(select(copyFromId)).orElseThrow(
                () -> new IllegalArgumentException("Site copyFromId not found: " + copyFromId));
        // 复制站点模型
        Model modelOfSite = modelService.copyOfSite(from.getModel());
        site.setModelId(modelOfSite.getId());
        // 保存站点
        insert(site, from.getCustomList());
        if (!modelOfSite.isGlobal()) {
            // 更新站点模型的站点ID
            modelService.update(modelOfSite, site.getId());
        }
        // 是否复制模板
        if (!site.getCopyData().contains(Site.COPY_DATA_TEMPLATE)) {
            return;
        }
        // 复制区块
        blockService.copyBySite(site.getId(), from.getId());
        // 设置模板主题
        String fromBasePath = from.getBasePath("");
        String siteBasePath = site.getBasePath("");
        site.setTheme(StringUtils.replaceOnce(from.getTheme(), fromBasePath, siteBasePath));
        site.setMobileTheme(StringUtils.replaceOnce(from.getMobileTheme(), fromBasePath, siteBasePath));
        mapper.update(site);
        // 复制模板
        FileHandler templateFileHandler = from.getConfig().getTemplateStorage().getFileHandler(pathResolver);
        templateFileHandler.copy(fromBasePath, siteBasePath);
        // 是否复制模型
        if (!site.getCopyData().contains(Site.COPY_DATA_MODEL)) {
            return;
        }
        // 复制字典
        dictTypeService.copyBySite(site.getId(), from.getId());
        // 复制模型
        Map<Integer, Integer> modelPairMap = modelService.copyBySite(site.getId(), from.getId());
        // 是否复制栏目
        if (!site.getCopyData().contains(Site.COPY_DATA_CHANNEL)) {
            return;
        }
        // 复制栏目
        channelService.copyBySite(site.getId(), from.getId(), modelPairMap);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Site bean, List<SiteCustom> customList) {
        bean.setId(seqService.getNextVal(SiteBase.TABLE_NAME));
        treeService.insert(bean);
        insertCustoms(customList, bean.getId());
        attachmentService.insertRefer(SiteBase.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Site bean, @Nullable Integer parentId, @Nullable List<SiteCustom> customList) {
        treeService.update(bean, parentId);
        if (customList != null) {
            customMapper.deleteBySiteId(bean.getId());
            insertCustoms(customList, bean.getId());
        }
        attachmentService.updateRefer(SiteBase.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    private void insertCustoms(List<SiteCustom> customList, Integer siteId) {
        customList.forEach(it -> {
            it.setId(seqService.getNextLongVal(SiteCustomBase.TABLE_NAME));
            it.setSiteId(siteId);
            if (it.isRichEditor()) {
                it.setValue(policyFactory.sanitize(it.getValue()));
            }
            customMapper.insert(it);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Site> list) {
        treeService.updateOrder(list);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        // 先查询获取数据，以免删除后无法获取
        Site site = select(id);
        if (site == null) {
            return 0;
        }
        return delete(site);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Site bean) {
        Config config = bean.getConfig();
        FileHandler templateFileHandler = config.getTemplateStorage().getFileHandler(pathResolver);
        FileHandler uploadFileHandler = config.getUploadStorage().getFileHandler(pathResolver);

        deleteListeners.forEach(it -> it.preSiteDelete(bean.getId()));
        customMapper.deleteBySiteId(bean.getId());
        int result = treeService.delete(bean.getId(), bean.getOrder());
        // 删除模型
        modelService.deleteBySiteId(bean.getId());
        // 删除模板、附件
        String basePath = bean.getBasePath("");
        templateFileHandler.deleteDirectory(basePath);
        uploadFileHandler.deleteDirectory(basePath);
        // 删除静态页。当开启多域名或者非根目录站点，可以删除静态页。
        if (Boolean.TRUE.equals(config.getMultiDomain()) || StringUtils.isNotBlank(bean.getSubDir())) {
            FileHandler htmlFileHandler = config.getHtmlStorage().getFileHandler(pathResolver);
            htmlFileHandler.deleteDirectory(bean.getStaticBase());
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Site bean, @Nullable List<SiteCustom> customList) {
        mapper.update(bean);
        if (customList != null) {
            customMapper.deleteBySiteId(bean.getId());
            customList.forEach(it -> {
                it.setId(seqService.getNextLongVal(SiteCustomBase.TABLE_NAME));
                it.setSiteId(bean.getId());
                if (it.isRichEditor()) {
                    it.setValue(policyFactory.sanitize(it.getValue()));
                }
                customMapper.insert(it);
            });
        }
        attachmentService.updateRefer(SiteBase.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Site bean) {
        update(bean, null);
    }

    @Nullable
    public Site select(Integer id) {
        return mapper.select(id);
    }

    public Site getDefaultSite(Integer defaultSiteId) {
        return Optional.ofNullable(select(defaultSiteId))
                .orElseThrow(() -> new IllegalStateException("default site not exist. id: " + defaultSiteId));
    }

    @Nullable
    public Site findByDomain(String domain) {
        return mapper.findByDomain(domain);
    }

    @Nullable
    public Site findBySubDir(String subDir) {
        return mapper.findBySubDir(subDir);
    }

    public List<Site> selectList(SiteArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), SiteBase.TABLE_NAME, "order,id");
        List<QueryInfo.WhereCondition> customsCondition = CustomFieldQuery.parse(args.getCustomsQueryMap());
        return mapper.selectAll(queryInfo, customsCondition, args.isQueryHasChildren(), args.getFullOrgId());
    }

    public List<Site> selectList(SiteArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Site> selectPage(SiteArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public List<Site> listByOrgId(Integer orgId) {
        return mapper.listByOrgId(orgId);
    }

    public List<Integer> listIdByOrgId(Integer orgId) {
        return mapper.listIdByOrgId(orgId);
    }

    public List<Integer> listByAncestorId(Integer ancestorId) {
        return treeMapper.listByAncestorId(ancestorId);
    }

    @Nullable
    public Site findFirstByOrgId(Integer orgId) {
        List<Site> list = PageMethod.offsetPage(0, 1).doSelectPage(() -> mapper.listByOrgId(orgId));
        return list.stream().findFirst().orElse(null);
    }

    private List<SiteDeleteListener> deleteListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setDeleteListeners(List<SiteDeleteListener> deleteListeners) {
        List<SiteDeleteListener> listeners = new ArrayList<>(deleteListeners);
        listeners.sort(Comparator.comparingInt(SiteDeleteListener::deleteListenerOrder).reversed());
        this.deleteListeners = listeners;
    }
}