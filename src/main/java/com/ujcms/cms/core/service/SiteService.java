package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.base.SiteBase;
import com.ujcms.cms.core.mapper.SiteMapper;
import com.ujcms.cms.core.service.args.SiteArgs;
import com.ujcms.commons.query.CustomFieldQuery;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.owasp.html.PolicyFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 站点 Service
 *
 * @author PONY
 */
@Service
public class SiteService {
    private final PolicyFactory policyFactory;
    private final AttachmentService attachmentService;
    private final ModelService modelService;
    private final SiteMapper mapper;

    public SiteService(PolicyFactory policyFactory, AttachmentService attachmentService,
                       ModelService modelService, SiteMapper mapper) {
        this.policyFactory = policyFactory;
        this.attachmentService = attachmentService;
        this.modelService = modelService;
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Site bean) {
        Model model = Optional.ofNullable(modelService.select(bean.getModelId())).orElseThrow(() ->
                new IllegalArgumentException(Model.NOT_FOUND + bean.getModelId()));
        bean.disassembleCustoms(model, policyFactory);
        mapper.update(bean);
        attachmentService.updateRefer(SiteBase.TABLE_NAME, bean.getId(), bean.getAttachmentUrls(model));
    }

    @Nullable
    public Site select(Long id) {
        return mapper.select(id);
    }

    public Site getDefaultSite(Long defaultSiteId) {
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

    public List<Site> listByUserIdAndOrgId(Long userId, Long orgId) {
        return mapper.listByUserIdAndOrgId(userId, orgId);
    }

    @Nullable
    public Site findFirstByUserIdAndOrgId(Long userId, Long orgId) {
        List<Site> list = PageMethod.offsetPage(0, 1).doSelectPage(() -> mapper.listByUserIdAndOrgId(userId, orgId));
        return list.stream().findFirst().orElse(null);
    }
}