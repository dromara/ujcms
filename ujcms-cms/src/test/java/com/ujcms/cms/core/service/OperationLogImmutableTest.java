package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.OperationLog;
import com.ujcms.cms.core.mapper.OperationLogExtMapper;
import com.ujcms.cms.core.mapper.OperationLogMapper;
import com.ujcms.cms.core.security.AuditSignature;
import com.ujcms.cms.core.security.spi.LocalCryptoKeyProvider;
import com.ujcms.cms.core.support.SecurityProperties;
import com.ujcms.common.web.exception.Http403Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证审计防删改 / 留存：开启防删改时删除（含级联）被拒/跳过；关闭时正常删除；留存期内拒绝、期外放行。
 */
class OperationLogImmutableTest {
    private OperationLogExtMapper extMapper;
    private OperationLogMapper mapper;
    private SecurityProperties securityProperties;
    private OperationLogService service;

    @BeforeEach
    void setUp() {
        extMapper = mock(OperationLogExtMapper.class);
        mapper = mock(OperationLogMapper.class);
        securityProperties = new SecurityProperties();
        service = new OperationLogService(extMapper, mapper, null, securityProperties,
                new AuditSignature(new LocalCryptoKeyProvider()));
    }

    @Test
    void immutableBlocksDelete() {
        securityProperties.setAuditImmutable(true);
        assertThrows(Http403Exception.class, () -> service.delete(5L));
        verify(mapper, never()).delete(anyLong());
        verify(extMapper, never()).delete(anyLong());
    }

    @Test
    void immutableBlocksCascadeUserAndSiteDelete() {
        securityProperties.setAuditImmutable(true);
        service.preUserDelete(7L);
        service.preSiteDelete(3L);
        verify(mapper, never()).deleteByUserId(anyLong());
        verify(extMapper, never()).deleteByUserId(anyLong());
        verify(mapper, never()).deleteBySiteId(anyLong());
        verify(extMapper, never()).deleteBySiteId(anyLong());
    }

    @Test
    void deleteProceedsWhenDisabled() {
        // 默认全部关闭：删除照常进行
        service.delete(5L);
        verify(extMapper).delete(5L);
        verify(mapper).delete(5L);
    }

    @Test
    void cascadeProceedsWhenDisabled() {
        service.preUserDelete(7L);
        verify(extMapper).deleteByUserId(7L);
        verify(mapper).deleteByUserId(7L);
    }

    @Test
    void retentionBlocksRecentRow() {
        securityProperties.setAuditRetainDays(30);
        OperationLog recent = new OperationLog();
        recent.setCreated(OffsetDateTime.now());
        when(mapper.select(5L)).thenReturn(recent);
        assertThrows(Http403Exception.class, () -> service.delete(5L));
        verify(mapper, never()).delete(anyLong());
    }

    @Test
    void retentionAllowsOldRow() {
        securityProperties.setAuditRetainDays(30);
        OperationLog old = new OperationLog();
        old.setCreated(OffsetDateTime.now().minusDays(60));
        when(mapper.select(5L)).thenReturn(old);
        service.delete(5L);
        verify(mapper).delete(5L);
    }
}
