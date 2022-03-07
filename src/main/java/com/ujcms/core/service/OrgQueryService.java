package com.ujcms.core.service;

import com.github.pagehelper.PageHelper;
import com.ujcms.core.domain.Org;
import com.ujcms.core.mapper.OrgMapper;
import com.ofwise.util.query.QueryInfo;
import com.ofwise.util.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 组织 QueryService
 *
 * @author PONY
 */
@Service
public class OrgQueryService {
    private OrgMapper mapper;

    public OrgQueryService(OrgMapper mapper) {
        this.mapper = mapper;
    }

    @Nullable
    public Org select(Integer id) {
        return mapper.select(id);
    }

    public List<Org> selectList(@Nullable Map<String, Object> queryMap) {
        QueryInfo queryInfo = QueryParser.parse(queryMap, Org.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Org> selectList(@Nullable Map<String, Object> queryMap, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(queryMap));
    }

    // private List<OrgDeleteListener> deleteListeners = Collections.emptyList();
    //
    // @Lazy
    // @Autowired(required = false)
    // public void setDeleteListeners(List<OrgDeleteListener> deleteListeners) {
    //     this.deleteListeners = deleteListeners;
    // }
}