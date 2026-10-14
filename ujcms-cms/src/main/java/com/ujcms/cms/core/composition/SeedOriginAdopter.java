package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.DictTypeMapper;
import com.ujcms.cms.core.mapper.ModelMapper;
import com.ujcms.cms.core.mapper.PageRegionMapper;
import com.ujcms.cms.core.mapper.PlacementMapper;
import com.ujcms.cms.core.mapper.TopicMapper;
import com.ujcms.cms.ext.mapper.FormTypeMapper;
import com.ujcms.cms.ext.mapper.LinkGroupMapper;
import com.ujcms.cms.ext.mapper.SurveyMapper;
import com.ujcms.cms.ext.mapper.VoteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 来源认领器（theme-seed-export §4.2 adopt 模式）——导出只读原则（D2）的<b>唯一豁免通道</b>：
 * {@code adoptOrigin=true} 时把引擎铸造的来源三元组回写到第 3 类行（操作员行）。设计站点自此
 * 转为种子管辖：后续导出把未改动行归入第 1 类、改动行第 2 类；「移除演示数据」与主题更新的
 * {@code syncUnmodified} 语义对其生效。
 *
 * <p>内容三类与构成两类（面板/区域）一并认领（§11 决定）：只认领内容会让设计站点的构成行
 * 永远归第 3 类——「移除演示数据」摸不到、导出计数恒虚高。单事务（与种子导入同法）；
 * SQL 侧附带「无来源标记才更新」条件，他源行在并发下也绝不被认领（D3）。
 *
 * @author PONY
 */
@Component
public class SeedOriginAdopter {
    private static final Logger logger = LoggerFactory.getLogger(SeedOriginAdopter.class);

    private final ChannelMapper channelMapper;
    private final ArticleMapper articleMapper;
    private final TopicMapper topicMapper;
    private final BlockMapper blockMapper;
    private final VoteMapper voteMapper;
    private final SurveyMapper surveyMapper;
    private final FormTypeMapper formTypeMapper;
    private final LinkGroupMapper linkGroupMapper;
    private final ModelMapper modelMapper;
    private final DictTypeMapper dictTypeMapper;
    private final PlacementMapper placementMapper;
    private final PageRegionMapper pageRegionMapper;

    public SeedOriginAdopter(ChannelMapper channelMapper, ArticleMapper articleMapper,
                             TopicMapper topicMapper, BlockMapper blockMapper, VoteMapper voteMapper,
                             SurveyMapper surveyMapper, FormTypeMapper formTypeMapper,
                             LinkGroupMapper linkGroupMapper,
                             ModelMapper modelMapper, DictTypeMapper dictTypeMapper,
                             PlacementMapper placementMapper, PageRegionMapper pageRegionMapper) {
        this.channelMapper = channelMapper;
        this.articleMapper = articleMapper;
        this.topicMapper = topicMapper;
        this.blockMapper = blockMapper;
        this.voteMapper = voteMapper;
        this.surveyMapper = surveyMapper;
        this.formTypeMapper = formTypeMapper;
        this.linkGroupMapper = linkGroupMapper;
        this.modelMapper = modelMapper;
        this.dictTypeMapper = dictTypeMapper;
        this.placementMapper = placementMapper;
        this.pageRegionMapper = pageRegionMapper;
    }

    /**
     * 回写认领三元组（单事务）。
     *
     * @param origin    本主题来源标记（{@code theme:<name>}）
     * @param adoptions 引擎收集的认领候选（{@link SeedExportBundle#adoptions()}）
     * @return 实际认领行数（SQL 条件拒绝的行不计——如导出后被并发打上来源标记的行）
     */
    @Transactional(rollbackFor = Exception.class)
    public int adopt(String origin, List<SeedAdoption> adoptions) {
        int adopted = 0;
        for (SeedAdoption adoption : adoptions) {
            adopted += switch (adoption.category()) {
                case CHANNEL -> channelMapper.adoptOrigin(adoption.id(), origin,
                        adoption.originId(), adoption.originHash());
                case ARTICLE -> articleMapper.adoptOrigin(adoption.id(), origin,
                        adoption.originId(), adoption.originHash());
                case TOPIC -> topicMapper.adoptOrigin(adoption.id(), origin,
                        adoption.originId(), adoption.originHash());
                case BLOCK -> blockMapper.adoptOrigin(adoption.id(), origin,
                        adoption.originId(), adoption.originHash());
                case VOTE -> voteMapper.adoptOrigin(adoption.id(), origin,
                        adoption.originId(), adoption.originHash());
                case SURVEY -> surveyMapper.adoptOrigin(adoption.id(), origin,
                        adoption.originId(), adoption.originHash());
                case FORM_TYPE -> formTypeMapper.adoptOrigin(adoption.id(), origin,
                        adoption.originId(), adoption.originHash());
                case LINK_GROUP -> linkGroupMapper.adoptOrigin(adoption.id(), origin,
                        adoption.originId(), adoption.originHash());
                case MODEL -> modelMapper.adoptOrigin(adoption.id(), origin,
                        adoption.originId(), adoption.originHash());
                case DICT -> dictTypeMapper.adoptOrigin(adoption.id(), origin,
                        adoption.originId(), adoption.originHash());
                case PANEL -> placementMapper.adoptOrigin(adoption.id(), origin,
                        adoption.originId(), adoption.originHash());
                case REGION -> pageRegionMapper.adoptOrigin(adoption.id(), origin,
                        adoption.originHash());
            };
        }
        if (adopted != adoptions.size()) {
            logger.warn("Seed origin adopt: {} of {} rows adopted (rows tagged concurrently are skipped)",
                    adopted, adoptions.size());
        }
        return adopted;
    }
}
