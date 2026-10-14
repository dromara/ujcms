package com.ujcms.cms.ext.service;

import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.ext.domain.Survey;
import com.ujcms.cms.ext.domain.SurveyItem;
import com.ujcms.cms.ext.domain.SurveyOption;
import com.ujcms.cms.ext.mapper.SurveyItemMapper;
import com.ujcms.cms.ext.mapper.SurveyMapper;
import com.ujcms.cms.ext.mapper.SurveyOptionMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.db.order.OrderEntityUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 调查问卷定义 Service（核心）：问卷、条目与选项的写入路径。调查问卷的功能面（后台管理、前台投放、
 * 投票、标签）属于 T2（{@code SurveyT2Service}），但问卷定义与所有版本共用一套 schema——主题演示数据在
 * 社区版/基础版同样完整落库，升级后无需补导（theme-seed-export D46）。
 *
 * @author PONY
 */
@Service
public class SurveyService implements SiteDeleteListener {
    private final SurveyMapper mapper;
    private final SurveyItemMapper itemMapper;
    private final SurveyOptionMapper optionMapper;
    private final SurveyFeedbackService feedbackService;
    private final SnowflakeSequence snowflakeSequence;

    public SurveyService(SurveyMapper mapper, SurveyItemMapper itemMapper, SurveyOptionMapper optionMapper,
                         SurveyFeedbackService feedbackService, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.itemMapper = itemMapper;
        this.optionMapper = optionMapper;
        this.feedbackService = feedbackService;
        this.snowflakeSequence = snowflakeSequence;
    }

    private void deleteItemBySurveyId(Long surveyId, List<SurveyItem> items) {
        List<Long> itemIds = items.stream().map(SurveyItem::getId).filter(id -> id > 0).toList();
        feedbackService.deleteItemBySurveyId(surveyId, itemIds);
        optionMapper.deleteBySurveyId(surveyId, itemIds);
        itemMapper.deleteBySurveyId(surveyId, itemIds);
    }

    private void updateItem(SurveyItem item, List<SurveyOption> options) {
        itemMapper.update(item);
        List<Long> optionIds = options.stream().map(SurveyOption::getId).filter(n -> n > 0).toList();
        feedbackService.deleteOptionByItemId(item.getId(), optionIds);
        optionMapper.deleteByItemId(item.getId(), optionIds);
        int order = 1;
        for (SurveyOption option : options) {
            option.setSiteId(item.getSiteId());
            option.setSurveyId(item.getSurveyId());
            option.setItemId(item.getId());
            option.setOrder(order);
            order += 1;
            if (option.getId() > 0) {
                // 更新
                Long origItemId = Optional.ofNullable(optionMapper.select(option.getId())).
                        map(SurveyOption::getItemId).orElse(null);
                if (!Objects.equals(origItemId, item.getId())) {
                    throw new IllegalStateException("SurveyOption itemId not equals SurveyItem id.");
                }
                optionMapper.update(option);
            } else {
                // 新增
                option.setId(snowflakeSequence.nextId());
                optionMapper.insert(option);
            }
        }
    }

    private void insertItem(SurveyItem item, List<SurveyOption> options) {
        item.setId(snowflakeSequence.nextId());
        itemMapper.insert(item);
        int order = 1;
        for (SurveyOption option : options) {
            option.setId(snowflakeSequence.nextId());
            option.setSiteId(item.getSiteId());
            option.setSurveyId(item.getSurveyId());
            option.setItemId(item.getId());
            option.setOrder(order);
            order += 1;
            optionMapper.insert(option);
        }
    }

    private void updateItems(Long siteId, Long surveyId, List<SurveyItem> items) {
        deleteItemBySurveyId(surveyId, items);
        int order = 1;
        for (SurveyItem item : items) {
            item.setSiteId(siteId);
            item.setSurveyId(surveyId);
            item.setOrder(order);
            order += 1;
            if (item.getId() > 0) {
                Long origSurveyId = Optional.ofNullable(itemMapper.select(item.getId())).
                        map(SurveyItem::getSurveyId).orElse(null);
                if (!Objects.equals(origSurveyId, surveyId)) {
                    throw new IllegalStateException("SurveyItem surveyId not equals Survey id.");
                }
                updateItem(item, item.getOptions());
            } else {
                insertItem(item, item.getOptions());
            }
        }
    }

    /**
     * 新增问卷（含条目与选项级联）
     *
     * @param bean  问卷（siteId 已由调用方设置）
     * @param items 条目列表（各自携带选项）
     */
    @Transactional(rollbackFor = Exception.class)
    public void insert(Survey bean, List<SurveyItem> items) {
        bean.setId(snowflakeSequence.nextId());
        bean.setOrder(bean.getId());
        mapper.insert(bean);
        updateItems(bean.getSiteId(), bean.getId(), items);
    }

    /**
     * 更新问卷（条目按缺席删除 / 按 id 更新 / 无 id 新增；被删条目与选项的反馈级联清理）
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Survey bean, List<SurveyItem> items) {
        mapper.update(bean);
        updateItems(bean.getSiteId(), bean.getId(), items);
    }

    @Transactional(rollbackFor = Exception.class)
    public void moveOrder(Long fromId, Long toId) {
        OrderEntityUtils.move(mapper, fromId, toId);
    }

    /**
     * 删除问卷（反馈、选项、条目级联）
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        feedbackService.deleteBySurveyId(id);
        optionMapper.deleteBySurveyId(id, List.of());
        itemMapper.deleteBySurveyId(id, List.of());
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Survey select(Long id) {
        return mapper.select(id);
    }

    @Override
    public int deleteListenerOrder() {
        return 200;
    }

    @Override
    public void preSiteDelete(Long siteId) {
        optionMapper.deleteBySiteId(siteId);
        itemMapper.deleteBySiteId(siteId);
        mapper.deleteBySiteId(siteId);
    }
}
