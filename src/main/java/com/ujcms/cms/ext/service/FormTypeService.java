package com.ujcms.cms.ext.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.ext.domain.FormType;
import com.ujcms.cms.ext.domain.base.FormTypeBase;
import com.ujcms.cms.ext.mapper.FormTypeMapper;
import com.ujcms.cms.ext.service.args.FormTypeArgs;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 表单类型 Service
 *
 * @author PONY
 */
@Service
public class FormTypeService {
    private final FormTypeMapper mapper;

    public FormTypeService(FormTypeMapper mapper) {
        this.mapper = mapper;
    }

    @Nullable
    public FormType select(Long id) {
        return mapper.select(id);
    }

    public List<FormType> selectList(FormTypeArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), FormTypeBase.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<FormType> selectList(FormTypeArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }
}