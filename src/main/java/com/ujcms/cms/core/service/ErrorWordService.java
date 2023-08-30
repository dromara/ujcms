package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.ErrorWord;
import com.ujcms.cms.core.domain.base.ErrorWordBase;
import com.ujcms.cms.core.mapper.ErrorWordMapper;
import com.ujcms.cms.core.service.args.ErrorWordArgs;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;

import java.util.List;
import java.util.Objects;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 易错词 Service
 *
 * @author PONY
 */
@Service
public class ErrorWordService {
    private final ErrorWordMapper mapper;

    private final SeqService seqService;

    public ErrorWordService(ErrorWordMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(ErrorWord bean) {
        bean.setId(seqService.getNextVal(ErrorWordBase.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ErrorWord bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public ErrorWord select(Integer id) {
        return mapper.select(id);
    }

    public List<ErrorWord> selectList(ErrorWordArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), ErrorWordBase.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<ErrorWord> selectList(ErrorWordArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<ErrorWord> selectPage(ErrorWordArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }
}