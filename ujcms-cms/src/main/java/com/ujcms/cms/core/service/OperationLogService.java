package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.OperationLog;
import com.ujcms.cms.core.domain.OperationLogExt;
import com.ujcms.cms.core.domain.generated.GeneratedOperationLog;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.mapper.OperationLogExtMapper;
import com.ujcms.cms.core.mapper.OperationLogMapper;
import com.ujcms.cms.core.security.AuditSignature;
import com.ujcms.cms.core.service.args.OperationLogArgs;
import com.ujcms.cms.core.support.SecurityProperties;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.exception.Http403Exception;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 操作日志 Service
 *
 * @author PONY
 */
@Service
public class OperationLogService implements SiteDeleteListener, UserDeleteListener {
    private final OperationLogExtMapper extMapper;
    private final OperationLogMapper mapper;
    private final SnowflakeSequence snowflakeSequence;
    private final SecurityProperties securityProperties;
    private final AuditSignature auditSignature;

    public OperationLogService(OperationLogExtMapper extMapper, OperationLogMapper mapper,
                               SnowflakeSequence snowflakeSequence, SecurityProperties securityProperties,
                               AuditSignature auditSignature) {
        this.extMapper = extMapper;
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
        this.securityProperties = securityProperties;
        this.auditSignature = auditSignature;
    }

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void asyncInsert(OperationLog bean, OperationLogExt ext) {
        bean.setId(snowflakeSequence.nextId());
        // 审计完整性开启时，写入前对该行计算 HMAC-SM3 签名（密评 日志完整性）。
        // 签名覆盖 created_，故先在 Java 设值（否则依赖库默认值，签名时取不到），保证签名值与入库值一致。
        if (securityProperties.isAuditIntegrity()) {
            if (bean.getCreated() == null) {
                bean.setCreated(OffsetDateTime.now());
            }
            // created_ 为 DATETIME（无小数秒精度）：带小数秒的值入库时会被四舍五入到整秒，导致回读值
            // 与签名时所用的值不一致而误报“被篡改”。签名前先截断到整秒，使被签名的值与入库值完全一致
            // （签名本就以秒级时间戳计入，截断不丢失签名信息）。
            bean.setCreated(bean.getCreated().withNano(0));
            bean.setSign(auditSignature.sign(bean));
        }
        mapper.insert(bean);
        ext.setId(bean.getId());
        extMapper.insert(ext);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        checkDeletable(id);
        extMapper.delete(id);
        return mapper.delete(id);
    }

    /**
     * 审计防删改 / 留存期校验：开启防删改则禁止删除；设置留存天数则拒绝删除留存期内的日志。
     */
    private void checkDeletable(Long id) {
        if (securityProperties.isAuditImmutable()) {
            throw new Http403Exception("error.auditLogImmutable");
        }
        int retainDays = securityProperties.getAuditRetainDays();
        if (retainDays > 0) {
            OperationLog bean = mapper.select(id);
            if (bean != null && bean.getCreated() != null
                    && bean.getCreated().isAfter(OffsetDateTime.now().minusDays(retainDays))) {
                throw new Http403Exception("error.auditLogRetained");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public OperationLog select(Long id) {
        return mapper.select(id);
    }

    public List<OperationLog> selectList(OperationLogArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedOperationLog.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<OperationLog> selectList(OperationLogArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<OperationLog> selectPage(OperationLogArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    @Override
    public void preSiteDelete(Long siteId) {
        // 审计防删改开启时，删除站点不得级联删除其历史审计日志（日志保留历史 siteId，无外键），否则成为绕过防删的后门
        if (securityProperties.isAuditImmutable()) {
            return;
        }
        extMapper.deleteBySiteId(siteId);
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }

    @Override
    public void preUserDelete(Long userId) {
        // 审计防删改开启时，删除用户不得级联删除其历史审计日志（日志保留历史 userId，无外键）
        if (securityProperties.isAuditImmutable()) {
            return;
        }
        extMapper.deleteByUserId(userId);
        mapper.deleteByUserId(userId);
    }
}