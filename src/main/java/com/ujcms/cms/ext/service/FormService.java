package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.ext.domain.Form;
import com.ujcms.cms.ext.domain.base.FormBase;
import com.ujcms.cms.ext.mapper.FormMapper;
import com.ujcms.cms.ext.service.args.FormArgs;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 表单 Service
 *
 * @author PONY
 */
@Service
public class FormService {
    private final FormMapper mapper;

    public FormService(FormMapper mapper) {
        this.mapper = mapper;
    }

    @Nullable
    public Form select(Long id) {
        return mapper.select(id);
    }

    public List<Form> listByIds(Collection<Long> ids) {
        return mapper.listByIds(ids);
    }

    public List<Form> selectList(FormArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), FormBase.TABLE_NAME, "order_desc,id_desc");
        return mapper.selectAll(queryInfo, args.getOrgIds(), args.getOrgRoleIds(), args.getOrgPermIds());
    }

    public long count(FormArgs args) {
        return PageMethod.count(() -> selectList(args));
    }

    public List<Form> selectList(FormArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Form> selectPage(FormArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }
}