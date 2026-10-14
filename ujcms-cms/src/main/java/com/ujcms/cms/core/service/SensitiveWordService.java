package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.component.WordScreeningService;
import com.ujcms.cms.core.domain.SensitiveWord;
import com.ujcms.cms.core.domain.generated.GeneratedSensitiveWord;
import com.ujcms.cms.core.mapper.SensitiveWordMapper;
import com.ujcms.cms.core.service.args.SensitiveWordArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.exception.Http400Exception;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.collections4.ListUtils;

import org.jspecify.annotations.Nullable;
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
    private final WordScreeningService wordScreeningService;

    public SensitiveWordService(SensitiveWordMapper mapper, SnowflakeSequence snowflakeSequence,
                                WordScreeningService wordScreeningService) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
        this.wordScreeningService = wordScreeningService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(SensitiveWord bean) {
        bean.setName(validName(bean.getName(), null));
        bean.setId(snowflakeSequence.nextId());
        mapper.insert(bean);
        wordScreeningService.refresh();
    }

    /**
     * 更新数据。词条未改动时不校验：存量数据可能有重复或空白词条，须仍能停用（列表开关提交的是整行）
     *
     * @param bean     实体对象
     * @param origName 修改前的词条
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(SensitiveWord bean, String origName) {
        if (!bean.getName().equals(origName)) {
            bean.setName(validName(bean.getName(), bean.getId()));
        }
        mapper.update(bean);
        wordScreeningService.refresh();
    }

    /**
     * 导入词条。逐条规范化；空白、超长的计为无效，词库已有或本批内重复的跳过（敏感词不区分大小写）
     *
     * @param names 待导入的词条
     * @return 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportOutcome importWords(List<String> names) {
        Set<String> keys = new HashSet<>();
        for (SensitiveWord word : selectList(SensitiveWordArgs.of())) {
            keys.add(WordScreeningService.foldCase(word.getName()));
        }
        List<SensitiveWord> list = new ArrayList<>();
        int duplicated = 0;
        int invalid = 0;
        for (String name : names) {
            String normalized = WordScreeningService.normalizeWord(name);
            if (normalized.isEmpty() || normalized.length() > NAME_MAX_LENGTH) {
                invalid++;
            } else if (!keys.add(WordScreeningService.foldCase(normalized))) {
                duplicated++;
            } else {
                SensitiveWord bean = new SensitiveWord();
                bean.setId(snowflakeSequence.nextId());
                bean.setName(normalized);
                list.add(bean);
            }
        }
        ListUtils.partition(list, BATCH_SIZE).forEach(mapper::insertBatch);
        if (!list.isEmpty()) {
            wordScreeningService.refresh();
        }
        return new ImportOutcome(list.size(), duplicated, invalid);
    }

    /**
     * 规范化并校验词条：不得为空白，不得与已有词条重复。敏感词匹配不区分大小写，重复判定同此规则。
     * 不设数据库唯一约束：各数据库的排序规则对大小写的处理不一，且存量数据可能已有重复
     */
    private String validName(String name, @Nullable Long excludeId) {
        String normalized = WordScreeningService.normalizeWord(name);
        if (normalized.isEmpty()) {
            throw new Http400Exception("error.sensitiveWord.blank");
        }
        String key = WordScreeningService.foldCase(normalized);
        for (SensitiveWord word : selectList(SensitiveWordArgs.of())) {
            if (!word.getId().equals(excludeId) && WordScreeningService.foldCase(word.getName()).equals(key)) {
                throw new Http400Exception("error.sensitiveWord.exists", word.getName());
            }
        }
        return normalized;
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        int count = mapper.delete(id);
        wordScreeningService.refresh();
        return count;
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
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedSensitiveWord.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<SensitiveWord> selectList(SensitiveWordArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<SensitiveWord> selectPage(SensitiveWordArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    /**
     * 词条最大长度，与 {@link GeneratedSensitiveWord} 的 {@code @Length} 一致
     */
    private static final int NAME_MAX_LENGTH = 30;
    /**
     * 批量插入时每条语句的行数
     */
    private static final int BATCH_SIZE = 500;

    /**
     * 导入结果
     *
     * @param created    新增条数
     * @param duplicated 跳过的重复条数（词库已有，或本批内重复）
     * @param invalid    无效条数（空白或超长）
     */
    public record ImportOutcome(int created, int duplicated, int invalid) {
    }
}