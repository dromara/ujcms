package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.SensitiveWord;
import com.ujcms.cms.core.domain.base.SensitiveWordBase;
import com.ujcms.cms.core.mapper.SensitiveWordMapper;
import com.ujcms.cms.core.service.args.SensitiveWordArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;

import java.util.List;
import java.util.Objects;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 敏感词 Service
 *
 * @author PONY
 */
@Service
public class SensitiveWordService {
    private final SensitiveWordMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public SensitiveWordService(SensitiveWordMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(SensitiveWord bean) {
        bean.setId(snowflakeSequence.nextId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SensitiveWord bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public SensitiveWord select(Long id) {
        return mapper.select(id);
    }

    public List<SensitiveWord> selectList(SensitiveWordArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), SensitiveWordBase.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<SensitiveWord> selectList(SensitiveWordArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<SensitiveWord> selectPage(SensitiveWordArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }
}