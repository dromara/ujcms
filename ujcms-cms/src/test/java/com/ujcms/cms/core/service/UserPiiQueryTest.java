package com.ujcms.cms.core.service;

import com.ujcms.cms.core.security.PiiBlindIndex;
import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 用户列表里针对手机号 / 邮箱的条件改写到盲索引上（docs/spec/article-data-permission/plan.md §13.5）：两列整列加密，
 * 按密文筛选永远筛不到。值按精确匹配，操作符不论；空值即“没有填”；按它们排序拒绝；其它条件原样
 */
class UserPiiQueryTest {
    @Test
    void mobileAndEmailConditionsBecomeBlindIndexLookups() {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("Contains_mobile", "138 0013 8000");
        queryMap.put("EQ_email", " Admin@Example.com ");
        queryMap.put("Like_username", "ad");

        UserService.rewritePiiQuery(queryMap);

        assertFalse(queryMap.containsKey("Contains_mobile"));
        assertFalse(queryMap.containsKey("EQ_email"));
        assertEquals(PiiBlindIndex.mobileIndex("13800138000"), queryMap.get("EQ_mobileIndex"));
        assertEquals(PiiBlindIndex.emailIndex("admin@example.com"), queryMap.get("EQ_emailIndex"));
        assertEquals("ad", queryMap.get("Like_username"));
    }

    @Test
    void aGroupAndATypeSuffixAreKeptAndABlankValueMeansNotFilledIn() {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("EQ_1_mobile_String", " ");
        UserService.rewritePiiQuery(queryMap);
        assertEquals(Map.of("IsNull_1_mobileIndex", "true"), queryMap);
    }

    @Test
    void sortingOnTheEncryptedColumnsIsRefusedAndOtherSortsPass() {
        Map<String, Object> ok = new HashMap<>(Map.of("OrderBy", "username_desc,@userExt-created_desc,mobileIndex"));
        UserService.rewritePiiQuery(ok);
        assertEquals("username_desc,@userExt-created_desc,mobileIndex", ok.get("OrderBy"));
        for (String orderBy : new String[]{"mobile", "email_desc", "created, mobile_asc", "1_email"}) {
            Map<String, Object> queryMap = new HashMap<>(Map.of("OrderBy", orderBy));
            assertThrows(Http400Exception.class, () -> UserService.rewritePiiQuery(queryMap), orderBy);
        }
    }

    @Test
    void columnsThatMerelyContainTheWordAreLeftAlone() {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("Like_mobileIndex", "x");
        queryMap.put("Like_org-email", "x");
        UserService.rewritePiiQuery(queryMap);
        assertEquals(2, queryMap.size());
    }
}
