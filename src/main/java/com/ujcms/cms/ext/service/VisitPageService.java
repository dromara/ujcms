package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.service.SeqService;
import com.ujcms.cms.ext.domain.VisitPage;
import com.ujcms.cms.ext.domain.base.VisitPageBase;
import com.ujcms.cms.ext.mapper.VisitPageMapper;
import com.ujcms.cms.ext.service.args.VisitPageArgs;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 受访页面 Service
 *
 * @author PONY
 */
@Service
public class VisitPageService implements SiteDeleteListener {
    private final VisitPageMapper mapper;
    private final SeqService seqService;

    public VisitPageService(VisitPageMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(VisitPage bean) {
        bean.setId(seqService.getNextLongVal(VisitPageBase.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(VisitPage bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteByDateString(String dateString, Short type) {
        mapper.deleteByDateString(dateString, type);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public VisitPage select(Long id) {
        return mapper.select(id);
    }

    public List<VisitPage> selectList(VisitPageArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), VisitPageBase.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<VisitPage> selectList(VisitPageArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<VisitPage> selectPage(VisitPageArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public Page<VisitPage> statByDate(Long siteId, Short type, @Nullable String begin, @Nullable String end,
                                      int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> mapper.statByDate(siteId, type, begin, end));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBeforeDateString(String dataString) {
        mapper.deleteBeforeDateString(dataString);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteBySiteId(siteId);
    }
}