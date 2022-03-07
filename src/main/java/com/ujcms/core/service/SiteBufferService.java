package com.ujcms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ujcms.core.domain.SiteBuffer;
import com.ujcms.core.mapper.SiteBufferMapper;
import com.ofwise.util.query.QueryInfo;
import com.ofwise.util.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 站点缓冲 Service
 *
 * @author PONY
 */
@Service
public class SiteBufferService {
    private SiteBufferMapper mapper;

    private SeqService seqService;

    public SiteBufferService(SiteBufferMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(SiteBuffer bean) {
        bean.setId(seqService.getNextVal(SiteBuffer.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SiteBuffer bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public long updateViews(Integer id, int viewsToPlus) {
        SiteBuffer buffer = mapper.select(id);
        if (buffer == null) {
            return 0;
        }
        mapper.updateViews(id, viewsToPlus);
        return buffer.getViews() + viewsToPlus;
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
    public SiteBuffer select(Integer id) {
        return mapper.select(id);
    }

    public List<SiteBuffer> selectList(@Nullable Map<String, Object> queryMap) {
        QueryInfo queryInfo = QueryParser.parse(queryMap, SiteBuffer.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<SiteBuffer> selectList(@Nullable Map<String, Object> queryMap,
                                       @Nullable Integer offset, @Nullable Integer limit) {
        return PageHelper.offsetPage(offset == null ? 0 : offset, limit == null ? Integer.MAX_VALUE : limit, false)
                .doSelectPage(() -> selectList(queryMap));
    }

    public Page<SiteBuffer> selectPage(@Nullable Map<String, Object> queryMap, int page, int pageSize) {
        return PageHelper.startPage(page, pageSize).doSelectPage(() -> selectList(queryMap));
    }
}