package com.ujcms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ofwise.util.query.CustomFieldQuery;
import com.ofwise.util.query.QueryInfo;
import com.ofwise.util.query.QueryParser;
import com.ujcms.core.domain.Site;
import com.ujcms.core.domain.SiteCustom;
import com.ujcms.core.mapper.SiteCustomMapper;
import com.ujcms.core.mapper.SiteMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 站点 Query Service
 *
 * @author PONY
 */
@Service
public class SiteQueryService {
    private AttachmentService attachmentService;
    private SiteMapper mapper;
    private SiteCustomMapper customMapper;

    public SiteQueryService(AttachmentService attachmentService, SiteMapper mapper, SiteCustomMapper customMapper) {
        this.attachmentService = attachmentService;
        this.mapper = mapper;
        this.customMapper = customMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Site bean, @Nullable List<SiteCustom> customList) {
        mapper.update(bean);
        if (customList != null) {
            customMapper.deleteBySiteId(bean.getId());
            customList.forEach(it -> {
                it.setSiteId(bean.getId());
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

    public List<Site> selectList(@Nullable Map<String, Object> queryMap,
                                 @Nullable Map<String, String> customsQueryMap) {
        QueryInfo queryInfo = QueryParser.parse(queryMap, Site.TABLE_NAME, "order,id");
        List<QueryInfo.WhereCondition> customsCondition = CustomFieldQuery.parse(customsQueryMap);
        return mapper.selectAll(queryInfo, customsCondition);
    }

    public List<Site> selectList(@Nullable Map<String, Object> queryMap,
                                 @Nullable Map<String, String> customsQueryMap,
                                 @Nullable Integer offset, @Nullable Integer limit) {
        return PageHelper.offsetPage(offset == null ? 0 : offset, limit == null ? Integer.MAX_VALUE : limit, false)
                .doSelectPage(() -> selectList(queryMap, customsQueryMap));
    }

    public Page<Site> selectPage(@Nullable Map<String, Object> queryMap, @Nullable Map<String, String> customsQueryMap,
                                 int page, int pageSize) {
        return PageHelper.startPage(page, pageSize).doSelectPage(() -> selectList(queryMap, customsQueryMap));
    }
}