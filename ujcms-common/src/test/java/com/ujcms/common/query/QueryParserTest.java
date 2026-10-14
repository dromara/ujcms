package com.ujcms.common.query;

import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueryParser
 *
 * @author PONY
 */
class QueryParserTest {

    @Test
    void testParseWithNullParams() {
        QueryInfo result = QueryParser.parse(null, "article");
        assertNull(result);
    }

    @Test
    void testParseWithEmptyParams() {
        Map<String, Object> params = new HashMap<>();
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals("article", result.getMainTable());
        assertEquals("ujcms_", result.getTablePrefix());
        assertFalse(result.isDistinct());
        assertTrue(result.getWhereConditions().isEmpty());
        assertTrue(result.getJoinTables().isEmpty());
    }

    @Test
    void testParseDistinct() {
        Map<String, Object> params = new HashMap<>();
        params.put("Distinct", "true");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertTrue(result.isDistinct());
    }

    @Test
    void testParseOrderBy() {
        Map<String, Object> params = new HashMap<>();
        params.put("OrderBy", "createdDate_desc,id_asc");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(2, result.getOrderByConditions().size());
        assertEquals("t.created_date_", result.getOrderByConditions().get(0).getColumn());
        assertEquals("desc", result.getOrderByConditions().get(0).getDirection());
        assertEquals("t.id_", result.getOrderByConditions().get(1).getColumn());
        assertEquals("asc", result.getOrderByConditions().get(1).getDirection());
    }

    @Test
    void testParseOrderByWithType() {
        Map<String, Object> params = new HashMap<>();
        params.put("OrderBy", "price_Int_desc");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getOrderByConditions().size());
        assertEquals("t.price_", result.getOrderByConditions().get(0).getColumn());
        assertEquals("desc", result.getOrderByConditions().get(0).getDirection());
        assertEquals("Int", result.getOrderByConditions().get(0).getType());
    }

    @Test
    void testParseDefaultOrderBy() {
        Map<String, Object> params = new HashMap<>();
        QueryInfo result = QueryParser.parse(params, "article", "createdDate_desc");
        assertNotNull(result);
        assertEquals(1, result.getOrderByConditions().size());
        assertEquals("t.created_date_", result.getOrderByConditions().get(0).getColumn());
        assertEquals("desc", result.getOrderByConditions().get(0).getDirection());
    }

    @Test
    void testParseSimpleWhereCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_id_Int", "123");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getWhereConditions().size());
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("t.id_", condition.getColumn());
        assertEquals("=", condition.getOperator());
        assertEquals(123, condition.getValue());
    }

    @Test
    void testParseLikeCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("Like_title", "test");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getWhereConditions().size());
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("t.title_", condition.getColumn());
        assertEquals("LIKE", condition.getOperator());
        assertEquals("test", condition.getValue());
    }

    @Test
    void testParseContainsCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("Contains_name", "test");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getWhereConditions().size());
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("LIKE", condition.getOperator());
        assertEquals("%test%", condition.getValue());
    }

    @Test
    void testParseStartsWithCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("StartsWith_name", "test");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("test%", condition.getValue());
    }

    @Test
    void testParseEndsWithCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("EndsWith_name", "test");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("%test", condition.getValue());
    }

    @Test
    void testParseInCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("In_status_Int", "1,2,3");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("IN", condition.getOperator());
        assertTrue(condition.getValue() instanceof List);
        @SuppressWarnings("unchecked")
        List<Integer> values = (List<Integer>) condition.getValue();
        assertEquals(3, values.size());
        assertEquals(1, values.get(0));
        assertEquals(2, values.get(1));
        assertEquals(3, values.get(2));
    }

    @Test
    void testParseIsNullCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("IsNull_deletedDate", null);
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("IS NULL", condition.getOperator());
        assertNull(condition.getValue());
    }

    @Test
    void testParseIsNotNullCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("IsNotNull_deletedDate", null);
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("IS NOT NULL", condition.getOperator());
        assertNull(condition.getValue());
    }

    @Test
    void testParseComparisonOperators() {
        Map<String, Object> params = new HashMap<>();
        params.put("GT_price_Double", "100.5");
        params.put("GE_price_Double", "100.5");
        params.put("LT_price_Double", "200.5");
        params.put("LE_price_Double", "200.5");
        params.put("NE_price_Double", "150.5");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(5, result.getWhereConditions().size());
        // Check operators individually as order may vary
        boolean foundGT = false, foundGE = false, foundLT = false, foundLE = false, foundNE = false;
        for (QueryInfo.WhereCondition condition : result.getWhereConditions()) {
            if (">".equals(condition.getOperator())) foundGT = true;
            if (">=".equals(condition.getOperator())) foundGE = true;
            if ("<".equals(condition.getOperator())) foundLT = true;
            if ("<=".equals(condition.getOperator())) foundLE = true;
            if ("<>".equals(condition.getOperator())) foundNE = true;
        }
        assertTrue(foundGT && foundGE && foundLT && foundLE && foundNE);
    }

    @Test
    void testParseWithJoinTable() {
        Map<String, Object> params = new HashMap<>();
        params.put("Like_user-username", "test");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getJoinTables().size());
        QueryInfo.JoinTable joinTable = result.getJoinTables().get(0);
        assertEquals("ujcms_user", joinTable.getTableName());
        assertEquals("user_", joinTable.getTableAlias());
        assertEquals("t.user_id_", joinTable.getLeftId());
        assertEquals("user_.id_", joinTable.getRightId());
    }

    @Test
    void testParseWithMultipleJoinTables() {
        Map<String, Object> params = new HashMap<>();
        params.put("Like_user-userExt-username", "test");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(2, result.getJoinTables().size());
    }

    @Test
    void testParseWithOneToOneJoin() {
        Map<String, Object> params = new HashMap<>();
        params.put("Like_@userExt-username", "test");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getJoinTables().size());
        QueryInfo.JoinTable joinTable = result.getJoinTables().get(0);
        assertEquals("ujcms_user_ext", joinTable.getTableName());
        assertEquals("userExt_", joinTable.getTableAlias());
    }

    @Test
    void testParseWithOneToManyJoin() {
        Map<String, Object> params = new HashMap<>();
        params.put("Like_UserRole-roleName", "admin");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getJoinTables().size());
        QueryInfo.JoinTable joinTable = result.getJoinTables().get(0);
        assertEquals("ujcms_user_role", joinTable.getTableName());
    }

    @Test
    void testParseWithGroupedConditions() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_1_status_Int", "1");
        params.put("EQ_1_type_Int", "2");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertTrue(result.getWhereOrAndConditions().containsKey("1"));
        Map<String, List<QueryInfo.WhereCondition>> group = result.getWhereOrAndConditions().get("1");
        assertNotNull(group);
        assertTrue(group.size() >= 1);
    }

    @Test
    void testParseWithSubGroupedConditions() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_1-1_status_Int", "1");
        params.put("EQ_1-2_type_Int", "2");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertTrue(result.getWhereOrAndConditions().containsKey("1"));
        Map<String, List<QueryInfo.WhereCondition>> group = result.getWhereOrAndConditions().get("1");
        assertNotNull(group);
        assertTrue(group.containsKey("1"));
        assertTrue(group.containsKey("2"));
    }

    @Test
    void testParseWithJsonColumn() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_ext$customField", "value");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getWhereConditions().size());
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertNotNull(condition.getJsonColumn());
        assertEquals("customField", condition.getJsonColumn());
    }

    @Test
    void testParseWithCustomTablePrefix() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_id_Int", "123");
        QueryInfo result = QueryParser.parse(params, "article", null, "custom_");
        assertNotNull(result);
        assertEquals("custom_", result.getTablePrefix());
        assertEquals("custom_article", result.getTableName());
    }

    @Test
    void testParseIgnoresNullOrBlankValues() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_name", null);
        params.put("EQ_title", "");
        params.put("EQ_status_Int", "1");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        // Only status condition should be added
        assertEquals(1, result.getWhereConditions().size());
        assertEquals("t.status_", result.getWhereConditions().get(0).getColumn());
    }

    @Test
    void testParseInvalidKeyFormat() {
        Map<String, Object> params = new HashMap<>();
        params.put("InvalidKey", "value");
        assertThrows(IllegalArgumentException.class, () -> {
            QueryParser.parse(params, "article");
        });
    }

    @Test
    void testParseInvalidQueryKey() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ@invalid", "value");
        // validateQuery throws QueryException, but parseQueryKey throws IllegalArgumentException
        assertThrows(Exception.class, () -> {
            QueryParser.parse(params, "article");
        });
    }

    @Test
    void testParseWithDifferentTypes() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_price_Double", "99.99");
        params.put("EQ_count_Long", "1000");
        params.put("EQ_active_Boolean", "true");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(3, result.getWhereConditions().size());
        // Order may vary, so check each condition individually
        boolean foundPrice = false, foundCount = false, foundActive = false;
        for (QueryInfo.WhereCondition condition : result.getWhereConditions()) {
            if (condition.getColumn().contains("price")) {
                assertEquals(99.99, condition.getValue());
                foundPrice = true;
            } else if (condition.getColumn().contains("count")) {
                assertEquals(1000L, condition.getValue());
                foundCount = true;
            } else if (condition.getColumn().contains("active")) {
                assertEquals("1", condition.getValue()); // Boolean converts to "1" or "0"
                foundActive = true;
            }
        }
        assertTrue(foundPrice && foundCount && foundActive);
    }

    @Test
    void testParseOrderByWithJsonColumn() {
        Map<String, Object> params = new HashMap<>();
        params.put("OrderBy", "ext$customField_desc");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getOrderByConditions().size());
        QueryInfo.OrderByCondition orderBy = result.getOrderByConditions().get(0);
        assertNotNull(orderBy.getJsonColumn());
        assertEquals("customField", orderBy.getJsonColumn());
        assertEquals("desc", orderBy.getDirection());
    }

    @Test
    void testValidatePublicKeyAllowed() {
        assertDoesNotThrow(() -> QueryParser.validatePublicKey("ArrayEQ_@articleExt-mainsJson$color_Int"));
        assertDoesNotThrow(() -> QueryParser.validatePublicKey("ArrayEQ_@articleExt-mainsJson$phone"));
        assertDoesNotThrow(() -> QueryParser.validatePublicKey("LT_publishDate_DateTime"));
        assertDoesNotThrow(() -> QueryParser.validatePublicKey("In_status_Short"));
        assertDoesNotThrow(() -> QueryParser.validatePublicKey("EQ_userId_Long"));
        assertDoesNotThrow(() -> QueryParser.validatePublicKey("Like_1_title"));
        assertDoesNotThrow(() -> QueryParser.validatePublicKey("Like_channel-name"));
        assertDoesNotThrow(() -> QueryParser.validatePublicKey("Distinct"));
        assertDoesNotThrow(() -> QueryParser.validatePublicKey("OrderBy"));
    }

    @Test
    void testValidatePublicKeyDenied() {
        List<String> keys = List.of(
                // 关联敏感表
                "StartsWith_user-password", "EQ_user-username", "EQ_editUser@user-username",
                "Contains_@userExt-mobile", "Like_user-@userExt-realName", "EQ_1_user-username",
                "EQ_org@OrgTree@descendant-name", "IsNotNull_user-id", "EQ_channel-user-username",
                // 敏感字段
                "EQ_ip", "Contains_1_phone", "Contains_1-1_email", "Like_address", "EQ_contact",
                "IsNull_mobile", "EQ_passwordModified_DateTime");
        for (String key : keys) {
            assertThrows(Http400Exception.class, () -> QueryParser.validatePublicKey(key), key);
        }
    }

    @Test
    void testValidatePublicOrderBy() {
        assertDoesNotThrow(() -> QueryParser.validatePublicOrderBy(null));
        assertDoesNotThrow(() -> QueryParser.validatePublicOrderBy("publishDate_desc,id_desc"));
        assertDoesNotThrow(() -> QueryParser.validatePublicOrderBy("sticky_desc, order_asc"));
        assertDoesNotThrow(() -> QueryParser.validatePublicOrderBy("@articleExt-mainsJson$price_Int_desc"));
        for (String orderBy : List.of("user-password_desc", "user-password", "id_desc,editUser@user-username",
                "ip_desc", "@userExt-mobile_asc")) {
            assertThrows(Http400Exception.class, () -> QueryParser.validatePublicOrderBy(orderBy), orderBy);
        }
    }

    @Test
    void testGetPublicQueryMap() {
        Map<String, String> params = new HashMap<>();
        params.put("Q_LT_publishDate_DateTime", "2026-01-01");
        params.put("status", "1");
        params.put("orderBy", "publishDate_desc");
        Map<String, Object> queryMap = QueryUtils.getPublicQueryMap(params);
        assertEquals(1, queryMap.size());
        assertEquals("2026-01-01", queryMap.get("LT_publishDate_DateTime"));

        assertThrows(Http400Exception.class,
                () -> QueryUtils.getPublicQueryMap(Map.of("Q_StartsWith_user-password", "$2a")));
        assertThrows(Http400Exception.class,
                () -> QueryUtils.getPublicQueryMap(Map.of("Q_OrderBy", "user-password")));
        assertThrows(Http400Exception.class,
                () -> QueryUtils.getPublicQueryMap(Map.of("orderBy", "user-password_desc")));
    }

    // ---- 后台接口的下限（docs/spec/article-data-permission/plan.md D19）：凭证列、全局配置表不可查

    @Test
    void backendKeysTheConsoleSendsAreAllowed() {
        for (String key : List.of("EQ_status_Short", "Contains_1_username", "Contains_1_realName", "Like_user-username",
                "Like_modifiedUser@user-username", "Like_org-name", "GE_@userExt-created_DateTime",
                "Contains_@articleExt-title", "In_@ArticleTopic-topicId_Long", "Contains_@ArticleTag-tag-name",
                "ArrayIn_@articleExt-mainsJson$$color", "Contains_mainsJson$name", "Like_ip", "Like_loginName",
                "Contains_openid", "EQ_processKey", "GE_promptTokens_Int", "Contains_seoKeywords", "Distinct")) {
            assertDoesNotThrow(() -> QueryParser.validateBackendKey(key, "x"), key);
        }
        assertDoesNotThrow(() -> QueryParser.validateBackendKey("OrderBy",
                "user-username, @userExt-loginIp_desc,type@formType-name,modified_desc,promptTokens_Int_desc"));
    }

    @Test
    void backendKeysNamingACredentialColumnAreRefusedOnAnyTable() {
        for (String key : List.of("Like_password", "StartsWith_user-password", "IsNotNull_user-@userExt-historyPassword",
                "Like_modifiedUser@user-password", "Like_site-WechatAccount-appSecret", "Like_site-WechatAccount-token",
                "Like_site-WechatAccount-aesKey", "Like_installTokenHash", "Like_1_user-password",
                "Like_user-password$x", "GE_passwordModified_DateTime")) {
            assertThrows(Http400Exception.class, () -> QueryParser.validateBackendKey(key, "x"), key);
        }
        for (String orderBy : List.of("user-password", "password_desc", "created_desc,user-password_desc",
                "x_user-password_Int", "site-WechatAccount-appSecret_asc")) {
            assertThrows(Http400Exception.class, () -> QueryParser.validateBackendKey("OrderBy", orderBy), orderBy);
        }
    }

    @Test
    void backendKeysJoiningTheConfigTableAreRefused() {
        for (String key : List.of("Like_site@config-contextPath", "Like_@config-port", "Like_config-port",
                "Like_site-@config-channelUrl", "IsNotNull_defaultSite@Config-port")) {
            assertThrows(Http400Exception.class, () -> QueryParser.validateBackendKey(key, "x"), key);
        }
        assertThrows(Http400Exception.class, () -> QueryParser.validateBackendKey("OrderBy", "site@config-port"));
        // 主表的同名列不受影响：只拒绝关联
        assertDoesNotThrow(() -> QueryParser.validateBackendKey("Like_config", "x"));
    }

    @Test
    void getQueryMapFromTheRequestAppliesTheBackendFloor() {
        List<String> joins = List.of("user-username", "user-password", "site@config-port");
        Map<String, Object> queryMap = QueryUtils.getQueryMap(
                "Q_Like_user-username=admin&Q_OrderBy=created_desc&page=1", joins);
        assertEquals(2, queryMap.size());
        assertEquals("admin", queryMap.get("Like_user-username"));
        // 列入许可也无济于事：下限在许可名单之前
        assertThrows(Http400Exception.class, () -> QueryUtils.getQueryMap("Q_Like_user-password=x", joins));
        assertThrows(Http400Exception.class, () -> QueryUtils.getQueryMap("Q_OrderBy=user-password_desc", joins));
        assertThrows(Http400Exception.class, () -> QueryUtils.getQueryMap("Q_Like_site@config-port=1", joins));
        // 前台接口的入口另有更严的校验，与此无关
        assertThrows(Http400Exception.class, () -> QueryUtils.getPublicQueryMap(Map.of("Q_Like_org-name", "x")));
        assertDoesNotThrow(() -> QueryUtils.getQueryMap("Q_Like_org-name=x", List.of("org-name")));
    }

    // ---- 每个后台列表都声明它回答的关联，默认一个也没有（D24）

    @Test
    void theRequestEntryRefusesEveryJoinUnlessTheListDeclaresIt() {
        // 主表自身的列总是许可
        assertDoesNotThrow(() -> QueryUtils.getQueryMap("Q_Like_name=x&Q_EQ_mainsJson$age_Int=1&Q_OrderBy=created_desc"));
        // 任何关联：条件、排序都拒绝
        assertThrows(Http400Exception.class, () -> QueryUtils.getQueryMap("Q_Like_user-username=x"));
        assertThrows(Http400Exception.class, () -> QueryUtils.getQueryMap("Q_OrderBy=user-username_desc"));
        assertThrows(Http400Exception.class, () -> QueryUtils.getQueryMap("Q_Like_org-name=x"));
        // 列出的才许可；下限仍在
        List<String> joins = List.of("user-username", "org-name");
        assertDoesNotThrow(() -> QueryUtils.getQueryMap("Q_Like_user-username=x&Q_OrderBy=org-name_desc", joins));
        assertThrows(Http400Exception.class, () -> QueryUtils.getQueryMap("Q_Like_user-realName=x", joins));
        assertThrows(Http400Exception.class, () -> QueryUtils.getQueryMap("Q_Like_user-password=x",
                List.of("user-password")));
    }

    @Test
    void aWildcardAllowsEveryColumnOfAOneToOneExtensionButNotTheFloor() {
        List<String> joins = List.of("@articleExt-*", "channel-name");
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("Contains_@articleExt-title", "x");
        queryMap.put("GE_@articleExt-mainsJson$price_Int", "1");
        queryMap.put("Contains_1_@articleExt-text", "x");
        queryMap.put("Contains_channel-name", "x");
        queryMap.put("OrderBy", "@articleExt-views_desc,@articleExt-mainsJson$age_Int,channel-name");
        assertDoesNotThrow(() -> QueryParser.validateJoins(queryMap, joins));
        // 通配只及该关联自身的列，不及它再往下的关联；别的关联仍拒绝
        for (String key : List.of("Like_@articleExt-site-name", "Like_channel-slug", "Like_user-username")) {
            assertThrows(Http400Exception.class, () -> QueryParser.validateJoins(Map.of(key, "x"), joins), key);
        }
        // 键里写不了 * 本身：字符校验在前
        assertThrows(QueryException.class, () -> QueryParser.validateJoins(Map.of("Like_@articleExt-*", "x"), joins));
        // 下限不因通配而放开
        assertThrows(Http400Exception.class, () -> QueryUtils.getQueryMap("Q_Like_@userExt-historyPassword=x",
                List.of("@userExt-*")));
        assertDoesNotThrow(() -> QueryUtils.getQueryMap("Q_OrderBy=@userExt-loginIp_desc", List.of("@userExt-*")));
    }

    // ---- 一个列表只回答它展示的东西（D18）：关联路径按许可名单

    private static final List<String> FORM_JOINS = List.of(
            "@formExt-clobsJson", "type@formType-name", "org-name", "user-username", "modifiedUser@user-username");

    @Test
    void validateJoinsAllowsTheMainTableAndTheListedPaths() {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("EQ_status_Short", "0");
        queryMap.put("Contains_mainsJson$name", "x");
        queryMap.put("GE_created_DateTime", "2026-01-01");
        queryMap.put("Contains_@formExt-clobsJson$text", "x");
        queryMap.put("Like_user-username", "a");
        queryMap.put("Like_1_org-name", "a");
        queryMap.put("Like_modifiedUser@user-username", "a");
        queryMap.put("Distinct", "true");
        queryMap.put("OrderBy", "type@formType-name_desc, user-username,mainsJson$age_Int_desc,id_desc");
        assertDoesNotThrow(() -> QueryParser.validateJoins(queryMap, FORM_JOINS));
    }

    @Test
    void validateJoinsRefusesAnyOtherJoinAsConditionOrSort() {
        for (String key : List.of("Like_user-realName", "Like_user-mobile", "Like_site-name", "Like_user-org-name",
                "Like_type@formType-site-name", "Like_1_user-nickname", "Contains_user-Article-@articleExt-title",
                "Like_user-username$x-y", "IsNull_@formExt-id")) {
            assertThrows(Http400Exception.class, () -> QueryParser.validateJoins(Map.of(key, "x"), FORM_JOINS), key);
        }
        for (String orderBy : List.of("site-name", "user-realName_desc", "created_desc,user-mobile", "x_user-mobile_Int")) {
            assertThrows(Http400Exception.class,
                    () -> QueryParser.validateJoins(Map.of("OrderBy", orderBy), FORM_JOINS), orderBy);
        }
    }

    @Test
    void validateJoinsSeesThePathTheParserWillJoin() {
        // 许可的路径与解析器实际关联的表一致：校验通过的键，解析后只关联许可的表
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("Like_1_modifiedUser@user-username", "a");
        queryMap.put("OrderBy", "type@formType-name_desc");
        assertDoesNotThrow(() -> QueryParser.validateJoins(queryMap, FORM_JOINS));
        QueryInfo info = QueryParser.parse(queryMap, "form");
        assertNotNull(info);
        assertEquals(List.of("ujcms_form_type", "ujcms_user"),
                info.getJoinTables().stream().map(QueryInfo.JoinTable::getTableName).sorted().toList());
    }

    // ---- 分组命名空间（docs/spec/article-data-permission/plan.md D26）：请求的组 1 起，服务端的组以 0 开头

    @Test
    void requestGroupsStartAtOneOnBothEntries() {
        for (String key : List.of("Contains_1_username", "Contains_1-1_username", "EQ_12_status_Int",
                "Like_3-10_title", "In_1_status")) {
            assertDoesNotThrow(() -> QueryParser.validateBackendKey(key, "x"), key);
            assertDoesNotThrow(() -> QueryParser.validatePublicKey(key), key);
        }
    }

    @Test
    void serverGroupsAreRefusedFromRequests() {
        for (String key : List.of("GT_0scope_id_Long", "EQ_0tree_id_Long", "EQ_0_id_Long", "EQ_01_id_Long",
                "Contains_0q_username", "IsNull_0online_onlineDate_DateTime", "LE_0start_startDate_DateTime",
                "EQ_0scope-1_id_Long", "EQ_1-a_id_Long")) {
            assertThrows(Http400Exception.class, () -> QueryParser.validateBackendKey(key, "x"), key);
            assertThrows(Http400Exception.class, () -> QueryParser.validatePublicKey(key), key);
        }
        assertThrows(Http400Exception.class, () -> QueryUtils.getQueryMap("Q_GT_0scope_id_Long=0"));
        assertThrows(Http400Exception.class,
                () -> QueryUtils.getPublicQueryMap(Map.of("Q_GT_0scope_id_Long", "0")));
    }

    @Test
    void aServerGroupNeverSharesAnOrGroupWithARequestGroup() {
        Map<String, Object> params = new HashMap<>(QueryUtils.getQueryMap("Q_GT_1_id_Long=0"));
        params.put("EQ_0scope_siteId_Long", 1L);
        params.put("EQ_0scope_scope_Short", "2");
        QueryInfo info = QueryParser.parse(params, "ujcms_model");
        assertNotNull(info);
        assertEquals(2, info.getWhereOrAndConditions().get("0scope").size(), "site OR global, its own group");
        assertEquals(1, info.getWhereOrAndConditions().get("1").size(), "the request's group stays separate");
        assertTrue(info.getWhereOrAndConditions().get("0scope").values().stream().flatMap(List::stream)
                .map(QueryInfo.WhereCondition::getColumn)
                .allMatch(column -> column.equals("t.site_id_") || column.equals("t.scope_")));
    }

    @Test
    void publicKeysCannotReachUnindexedArticleBodies() {
        // 正文、Markdown 源、大字段型自定义字段没有索引：匿名请求不能拿它们做 LIKE 或排序（按表限定）
        for (String key : new String[]{"Contains_@articleExt-text", "Contains_@articleExt-markdown",
                "Contains_@articleExt-clobsJson", "Contains_@articleExt-clobsJson$summary",
                "Contains_1_@articleExt-text", "Like_article@articleExt-text"}) {
            assertThrows(Http400Exception.class, () -> QueryParser.validatePublicKey(key), key);
        }
        assertThrows(Http400Exception.class, () -> QueryParser.validatePublicOrderBy("@articleExt-text"));
        assertThrows(Http400Exception.class, () -> QueryParser.validatePublicOrderBy("id_desc,@articleExt-markdown"));
        // 标题、普通自定义字段照常；别的表里同名的字段不受波及
        assertDoesNotThrow(() -> QueryParser.validatePublicKey("Contains_@articleExt-title"));
        assertDoesNotThrow(() -> QueryParser.validatePublicKey("EQ_@articleExt-mainsJson$color"));
        assertDoesNotThrow(() -> QueryParser.validatePublicKey("Contains_text"));
        assertDoesNotThrow(() -> QueryParser.validatePublicKey("Contains_@commentExt-text"));
        assertDoesNotThrow(() -> QueryParser.validatePublicOrderBy("@articleExt-title"));
    }

    @Test
    void distinctFalseDoesNotEnableDistinct() {
        Map<String, Object> params = new HashMap<>();
        params.put("Distinct", "false");
        assertFalse(QueryParser.parse(params, "article").isDistinct());
        params.put("Distinct", false);
        assertFalse(QueryParser.parse(params, "article").isDistinct());
        params.put("Distinct", "true");
        assertTrue(QueryParser.parse(params, "article").isDistinct());
        // 只有键、值为空：仍然去重（控制台与模板的既有写法）
        params.put("Distinct", "");
        assertTrue(QueryParser.parse(params, "article").isDistinct());
    }
}
