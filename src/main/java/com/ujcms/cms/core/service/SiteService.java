package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ujcms.cms.core.domain.ArticleImage;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.SiteCustom;
import com.ujcms.cms.core.mapper.SiteCustomMapper;
import com.ujcms.cms.core.mapper.SiteMapper;
import com.ujcms.cms.core.service.args.SiteArgs;
import com.ujcms.util.query.CustomFieldQuery;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;
import org.owasp.html.PolicyFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 站点 Query Service
 *
 * @author PONY
 */
@Service
public class SiteService {
    private PolicyFactory policyFactory;
    private AttachmentService attachmentService;
    private SeqService seqService;
    private SiteMapper mapper;
    private SiteCustomMapper customMapper;

    public SiteService(PolicyFactory policyFactory, AttachmentService attachmentService,
                       SeqService seqService, SiteMapper mapper, SiteCustomMapper customMapper) {
        this.policyFactory = policyFactory;
        this.attachmentService = attachmentService;
        this.seqService = seqService;
        this.mapper = mapper;
        this.customMapper = customMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Site bean, @Nullable List<SiteCustom> customList) {
        mapper.update(bean);
        if (customList != null) {
            customMapper.deleteBySiteId(bean.getId());
            customList.forEach(it -> {
                it.setId(seqService.getNextValLong(SiteCustom.TABLE_NAME));
                it.setSiteId(bean.getId());
                it.setValue(policyFactory.sanitize(it.getValue()));
                customMapper.insert(it);
            });
        }
        attachmentService.updateRefer(Site.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    @Nullable
    public Site select(Integer id) {
        return mapper.select(id);
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
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), Site.TABLE_NAME, "order,id");
        List<QueryInfo.WhereCondition> customsCondition = CustomFieldQuery.parse(args.getCustomsQueryMap());
        return mapper.selectAll(queryInfo, customsCondition, args.getParentId(), args.getFullOrgId());
    }

    public List<Site> selectList(SiteArgs args, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Site> selectPage(SiteArgs args, int page, int pageSize) {
        return PageHelper.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public List<Site> listByOrgId(Integer orgId) {
        return mapper.listByOrgId(orgId);
    }

    @Nullable
    public Site findFirstByOrgId(Integer orgId) {
        List<Site> list = PageHelper.offsetPage(0, 1).doSelectPage(() -> mapper.listByOrgId(orgId));
        return list.stream().findFirst().orElse(null);
    }
}