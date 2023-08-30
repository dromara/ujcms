package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.service.SeqService;
import com.ujcms.cms.ext.domain.Example;
import com.ujcms.cms.ext.domain.base.ExampleBase;
import com.ujcms.cms.ext.mapper.ExampleMapper;
import com.ujcms.cms.ext.service.args.ExampleArgs;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;

import java.util.List;
import java.util.Objects;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 示例 Service
 *
 * @author PONY
 */
@Service
public class ExampleService {
    private final ExampleMapper mapper;
    private final SeqService seqService;

    public ExampleService(ExampleMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Example bean) {
        bean.setId(seqService.getNextVal(ExampleBase.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Example bean) {
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
    public Example select(Integer id) {
        return mapper.select(id);
    }

    public List<Example> selectList(ExampleArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), ExampleBase.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Example> selectList(ExampleArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Example> selectPage(ExampleArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }
}