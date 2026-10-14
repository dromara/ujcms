package com.ujcms.cms.core.service.args;

import com.ujcms.cms.core.domain.generated.GeneratedDict;
import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DictArgs#scopeSiteId} 的查询形状（dictionary plan P1）：经 {@code type@dictType} 联接
 * 字典类型表，站点条件与全局条件落入同一 AND 组的两个子组（组内 OR），其余条件（别名）
 * 保持普通 AND；值为 {@link Long}/{@link Short} 对象而非字符串（PostgreSQL 类型严格）。
 */
class DictArgsScopeTest {

    @Test
    void scopeSiteIdJoinsDictTypeAndOrsSiteWithGlobal() {
        DictArgs args = DictArgs.of().typeAlias("video_area").scopeSiteId(7L);
        QueryInfo info = QueryParser.parse(args.getQueryMap(), GeneratedDict.TABLE_NAME, "order,id");

        assertTrue(info.getJoinTables().stream().anyMatch(j -> "ujcms_dict_type".equals(j.getTableName())),
                "dict type joined");
        assertEquals(1, info.getWhereOrAndConditions().size(), "exactly one OR group");
        Map<String, List<QueryInfo.WhereCondition>> group = info.getWhereOrAndConditions().get("0scope");
        assertNotNull(group);
        assertEquals(2, group.size(), "site condition and global condition in separate sub-groups");
        List<Object> values = group.values().stream().map(conditions -> {
            assertEquals(1, conditions.size());
            return conditions.get(0).getValue();
        }).toList();
        assertTrue(values.stream().anyMatch(v -> v instanceof Long l && l == 7L), "siteId bound as Long");
        assertTrue(values.stream().anyMatch(v -> v instanceof Short s && s == EntityConstants.SCOPE_GLOBAL),
                "global scope bound as Short");
        // 别名是普通 AND 条件，不在 OR 组里
        assertTrue(info.getWhereConditions().stream().anyMatch(c -> "video_area".equals(c.getValue())));
    }

    @Test
    void nullSiteIdAddsNoCondition() {
        assertTrue(DictArgs.of().scopeSiteId(null).getQueryMap().isEmpty());
    }
}
