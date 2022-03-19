package com.ujcms.core.service;

import com.github.pagehelper.PageHelper;
import com.ofwise.util.query.QueryInfo;
import com.ofwise.util.query.QueryParser;
import com.ujcms.core.domain.Model;
import com.ujcms.core.mapper.ModelMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 模型 Service
 *
 * @author PONY
 */
@Service
public class ModelService {
    private ModelMapper mapper;

    private SeqService seqService;

    public ModelService(ModelMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }


    @Transactional(rollbackFor = Exception.class)
    public void insert(Model bean) {
        bean.setId(seqService.getNextVal(Model.TABLE_NAME));
        mapper.insert(bean);
    }


    @Transactional(rollbackFor = Exception.class)
    public void update(Model bean) {
        mapper.update(bean);
    }


    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Model> list) {
        short order = 1;
        for (Model bean : list) {
            bean.setOrder(order);
            mapper.update(bean);
            order += 1;
        }
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
    public Model select(Integer id) {
        return mapper.select(id);
    }

    public Model selectGlobalModel() {
        Model model = mapper.selectGlobalModel();
        if (model == null) {
            throw new IllegalStateException("Model not found ID: 1 (global model)");
        }
        return model;
    }

    public Model selectUserModel() {
        Model model = mapper.selectUserModel();
        if (model == null) {
            throw new IllegalStateException("Model not found ID: 2 (user model)");
        }
        return model;
    }

    public List<Model> selectList(@Nullable Map<String, Object> queryMap) {
        QueryInfo queryInfo = QueryParser.parse(queryMap, Model.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Model> selectList(@Nullable Map<String, Object> queryMap,
                                  @Nullable Integer offset, @Nullable Integer limit) {
        return PageHelper.offsetPage(offset == null ? 0 : offset, limit == null ? Integer.MAX_VALUE : limit, false)
                .doSelectPage(() ->selectList(queryMap));
    }
}