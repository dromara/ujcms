package com.ujcms.cms.core.service.args;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.query.QueryUtils;
import com.ujcms.common.web.exception.Http400Exception;

/**
 * 服务端范围条件与请求条件永不同组（docs/spec/article-data-permission/plan.md D26）：以前 {@code ?Q_GT_1_id_Long=0}
 * 并入 {@code scopeSiteId} / {@code parentIdAndSelf} 的 OR 组 {@code 1}，把角色、组织、区块、字典类型、字典、栏目、专题、
 * 站点列表扩到其它站点与组织。现在服务端的组以 0 开头，请求的组 1 起，请求入口拒绝 0 开头的组
 */
class ScopeGroupIsolationTest {
    private static final String WIDENING = "Q_GT_1_id_Long=0";

    private static QueryInfo parse(Map<String, Object> queryMap) {
        QueryInfo info = QueryParser.parse(queryMap, "ujcms_x");
        assertNotNull(info);
        return info;
    }

    /** 服务端组恰有两个条件（各自成子组：OR），请求组只含请求自己的条件 */
    private static void assertIsolated(Map<String, Object> queryMap, String serverGroup) {
        Map<String, Map<String, List<QueryInfo.WhereCondition>>> groups = parse(queryMap).getWhereOrAndConditions();
        assertEquals(2, groups.size(), "server group and request group");
        assertEquals(2, groups.get(serverGroup).size(), serverGroup + " keeps its two OR branches");
        assertEquals(1, groups.get("1").size(), "the request's group holds only the request's condition");
    }

    @Test
    void scopeSiteIdKeepsItsOwnGroup() {
        assertIsolated(ModelArgs.of(QueryUtils.getQueryMap(WIDENING)).scopeSiteId(7L).getQueryMap(), "0scope");
        assertIsolated(BlockArgs.of(QueryUtils.getQueryMap(WIDENING)).scopeSiteId(7L).getQueryMap(), "0scope");
        assertIsolated(RoleArgs.of(QueryUtils.getQueryMap(WIDENING)).scopeSiteId(7L).getQueryMap(), "0scope");
        assertIsolated(DictTypeArgs.of(QueryUtils.getQueryMap(WIDENING)).scopeSiteId(7L).getQueryMap(), "0scope");
        assertIsolated(DictArgs.of(QueryUtils.getQueryMap(WIDENING)).scopeSiteId(7L).getQueryMap(), "0scope");
    }

    @Test
    void parentIdAndSelfKeepsItsOwnGroup() {
        assertIsolated(OrgArgs.of(QueryUtils.getQueryMap(WIDENING)).parentIdAndSelf(7L).getQueryMap(), "0tree");
        ChannelArgs channelArgs = ChannelArgs.of(QueryUtils.getQueryMap(WIDENING));
        channelArgs.parentIdAndSelf(7L);
        assertIsolated(channelArgs.getQueryMap(), "0tree");
        assertIsolated(TopicArgs.of(QueryUtils.getQueryMap(WIDENING)).parentIdAndSelf(7L).getQueryMap(), "0tree");
        assertIsolated(SiteArgs.of(QueryUtils.getQueryMap(WIDENING)).parentIdAndSelf(7L).getQueryMap(), "0tree");
    }

    @Test
    void aRequestCannotNameAServerGroup() {
        for (String query : List.of("Q_GT_0scope_id_Long=0", "Q_GT_0tree_id_Long=0", "Q_EQ_0_id_Long=1",
                "Q_Contains_0q_username=a")) {
            assertThrows(Http400Exception.class, () -> QueryUtils.getQueryMap(query), query);
        }
    }
}
