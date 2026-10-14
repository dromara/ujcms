package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.component.WordScreeningService;
import com.ujcms.cms.core.domain.ErrorWord;
import com.ujcms.cms.core.domain.generated.GeneratedErrorWord;
import com.ujcms.cms.core.mapper.ErrorWordMapper;
import com.ujcms.cms.core.service.args.ErrorWordArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.exception.Http400Exception;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.collections4.ListUtils;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 易错词 Service
 *
 * @author PONY
 */
@Service
public class ErrorWordService {
    private final ErrorWordMapper mapper;
    private final SnowflakeSequence snowflakeSequence;
    private final WordScreeningService wordScreeningService;

    public ErrorWordService(ErrorWordMapper mapper, SnowflakeSequence snowflakeSequence,
                            WordScreeningService wordScreeningService) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
        this.wordScreeningService = wordScreeningService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(ErrorWord bean) {
        normalizeAndValidate(bean, true);
        bean.setId(snowflakeSequence.nextId());
        mapper.insert(bean);
        wordScreeningService.refresh();
    }

    /**
     * 更新数据。易错词与正确词均未改动时不校验：存量数据可能有重复或空白词条，须仍能停用（列表开关提交的是整行）
     *
     * @param bean        实体对象
     * @param origName    修改前的易错词
     * @param origCorrect 修改前的正确词
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(ErrorWord bean, String origName, String origCorrect) {
        boolean nameChanged = !bean.getName().equals(origName);
        if (nameChanged || !bean.getCorrect().equals(origCorrect)) {
            normalizeAndValidate(bean, nameChanged);
        }
        mapper.update(bean);
        wordScreeningService.refresh();
    }

    /**
     * 导入词条。逐条规范化；空白、超长、易错词与正确词相同的计为无效，词库已有或本批内重复的跳过。
     * 已有词条的正确词与导入的不同时不覆盖，记入冲突清单由操作员自行核对
     *
     * @param words 待导入的词条（只取易错词与正确词）
     * @return 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportOutcome importWords(List<ErrorWord> words) {
        Map<String, String> corrections = new HashMap<>(256);
        for (ErrorWord word : selectList(ErrorWordArgs.of())) {
            corrections.putIfAbsent(word.getName(), word.getCorrect());
        }
        List<ErrorWord> list = new ArrayList<>();
        Set<String> conflicts = new LinkedHashSet<>();
        int duplicated = 0;
        int invalid = 0;
        for (ErrorWord word : words) {
            String name = WordScreeningService.normalizeWord(word == null ? null : word.getName());
            String correct = WordScreeningService.normalizeWord(word == null ? null : word.getCorrect());
            if (name.isEmpty() || correct.isEmpty() || name.equals(correct)
                    || name.length() > WORD_MAX_LENGTH || correct.length() > WORD_MAX_LENGTH) {
                invalid++;
                continue;
            }
            String known = corrections.putIfAbsent(name, correct);
            if (known != null) {
                duplicated++;
                if (!known.equals(correct)) {
                    conflicts.add(name);
                }
                continue;
            }
            ErrorWord bean = new ErrorWord();
            bean.setId(snowflakeSequence.nextId());
            bean.setName(name);
            bean.setCorrect(correct);
            list.add(bean);
        }
        ListUtils.partition(list, BATCH_SIZE).forEach(mapper::insertBatch);
        if (!list.isEmpty()) {
            wordScreeningService.refresh();
        }
        return new ImportOutcome(list.size(), duplicated, invalid, List.copyOf(conflicts));
    }

    /**
     * 规范化并校验词条：两个词均不得为空白，不得相同（相同的词条修正前后一样，没有意义）；
     * 易错词改动时不得与已有词条重复。易错词精确匹配，重复判定同此规则
     */
    private void normalizeAndValidate(ErrorWord bean, boolean checkDuplicate) {
        String name = WordScreeningService.normalizeWord(bean.getName());
        String correct = WordScreeningService.normalizeWord(bean.getCorrect());
        if (name.isEmpty() || correct.isEmpty()) {
            throw new Http400Exception("error.errorWord.blank");
        }
        if (name.equals(correct)) {
            throw new Http400Exception("error.errorWord.sameAsCorrect", name);
        }
        if (checkDuplicate) {
            for (ErrorWord word : selectList(ErrorWordArgs.of())) {
                if (!word.getId().equals(bean.getId()) && word.getName().equals(name)) {
                    throw new Http400Exception("error.errorWord.exists", name);
                }
            }
        }
        bean.setName(name);
        bean.setCorrect(correct);
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
    public ErrorWord select(Long id) {
        return mapper.select(id);
    }

    public List<ErrorWord> selectList(ErrorWordArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedErrorWord.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<ErrorWord> selectList(ErrorWordArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<ErrorWord> selectPage(ErrorWordArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    /**
     * 词条最大长度，与 {@link GeneratedErrorWord} 的 {@code @Length} 一致
     */
    private static final int WORD_MAX_LENGTH = 30;
    /**
     * 批量插入时每条语句的行数
     */
    private static final int BATCH_SIZE = 500;

    /**
     * 导入结果
     *
     * @param created    新增条数
     * @param duplicated 跳过的重复条数（词库已有，或本批内重复）
     * @param invalid    无效条数（空白、超长或易错词与正确词相同）
     * @param conflicts  已存在且正确词与导入的不同的易错词（未修改）
     */
    public record ImportOutcome(int created, int duplicated, int invalid, List<String> conflicts) {
    }
}