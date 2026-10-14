package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.mapper.UserMapper;
import com.ujcms.cms.core.security.PiiBlindIndex;
import com.ujcms.cms.core.service.args.UserArgs;
import com.ujcms.cms.core.support.StaticProps;
import com.ujcms.common.query.QueryInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@code UserService.selectByMobile/selectByEmail} 走盲索引：传给 mapper 的是对规范化值算出的
 * 索引（而非原值），且不同书写经规范化后命中同一索引；空白值短路、不查库（NULL 索引处理）。
 */
class UserServicePiiLookupTest {
    private UserMapper mapper;
    private UserService service;

    @BeforeEach
    void setUp() {
        StaticProps.setDataSecretKey("0123456789abcdef");
        mapper = mock(UserMapper.class);
        // 仅 mapper 参与本测试，其余依赖不被触达，传 null 即可
        service = new UserService(null, null, null, null, mapper, null, null, null, null, null, null, null);
    }

    @AfterEach
    void tearDown() {
        StaticProps.setDataSecretKey(StaticProps.DEFAULT_DATA_SECRET_KEY);
    }

    @Test
    void selectByMobileQueriesByIndex() {
        String mobile = "138 0000 1111";
        String expectedIndex = PiiBlindIndex.mobileIndex(mobile);
        User stub = new User();
        when(mapper.selectByMobile(expectedIndex)).thenReturn(stub);

        assertSame(stub, service.selectByMobile(mobile));
        // 传给 mapper 的是索引，不是原值
        verify(mapper).selectByMobile(expectedIndex);
    }

    @Test
    void selectByMobileNormalizesBeforeIndexing() {
        // 不同分隔符书写应命中同一索引 → mapper 收到与规范化值一致的索引
        String expectedIndex = PiiBlindIndex.mobileIndex("13800001111");
        service.selectByMobile("138-0000-1111");
        verify(mapper).selectByMobile(expectedIndex);
    }

    @Test
    void selectByEmailQueriesByIndexCaseInsensitive() {
        String expectedIndex = PiiBlindIndex.emailIndex("user@example.com");
        User stub = new User();
        when(mapper.selectByEmail(expectedIndex)).thenReturn(stub);

        assertSame(stub, service.selectByEmail("USER@Example.com"));
        verify(mapper).selectByEmail(expectedIndex);
    }

    @Test
    void blankMobileShortCircuitsWithoutQuery() {
        assertNull(service.selectByMobile("   "));
        verify(mapper, never()).selectByMobile(any());
    }

    @Test
    void blankEmailShortCircuitsWithoutQuery() {
        assertNull(service.selectByEmail(""));
        verify(mapper, never()).selectByEmail(any());
    }

    @Test
    void selectListRewritesMobileSearchToIndexExactMatch() {
        String expectedIndex = PiiBlindIndex.mobileIndex("13800001111");
        Map<String, Object> q = new HashMap<>();
        q.put("Contains_mobile", "138 0000 1111"); // 控制台发来的模糊查询项
        when(mapper.selectAll(any(), any())).thenReturn(Collections.emptyList());

        service.selectList(UserArgs.of(q));

        ArgumentCaptor<QueryInfo> captor = ArgumentCaptor.forClass(QueryInfo.class);
        verify(mapper).selectAll(captor.capture(), any());
        QueryInfo info = captor.getValue();
        // 改写为对盲索引列 mobile_index_ 的精确匹配，值为规范化手机号的盲索引
        boolean indexEq = info.getWhereConditions().stream()
                .anyMatch(c -> c.getColumn().endsWith("mobile_index_") && expectedIndex.equals(c.getValue()));
        assertTrue(indexEq, "应按 mobile_index_ 精确匹配规范化后手机号的盲索引");
        // 不再对密文列 mobile_ 生成（永远无法命中的）LIKE 条件
        boolean rawMobile = info.getWhereConditions().stream()
                .anyMatch(c -> c.getColumn().endsWith(".mobile_"));
        assertFalse(rawMobile, "不应再对密文列 mobile_ 生成查询条件");
    }
}
