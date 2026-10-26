package com.ujcms.cms.core.support;

import com.ujcms.cms.core.domain.Site;
import freemarker.template.TemplateException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模板错误记录器（docs/spec/template-error-disclosure）。
 * <p>
 * 每个模板渲染错误分配一个 8 位十六进制<b>错误编号</b>：以一条 ERROR 日志（编号、站点、模板位置、请求路径、
 * FreeMarker 错误信息）落盘，并保留在进程内的环形缓冲里，供控制台「模板管理 › 最近错误」读取。
 * 访客错误页、操作员页内的错误框与日志里出现的是同一个编号，技术支持凭编号即可在日志中定位同一次错误。
 * 缓冲只是排障便利：按进程保存、重启即清空，日志才是持久记录。
 * <p>
 * 请求路径只记 URI，不记查询串——查询串可能携带 {@code _ptoken} 预览令牌。
 *
 * @author PONY
 */
@Component
public class TemplateErrorRecorder {
    private static final Logger logger = LoggerFactory.getLogger(TemplateErrorRecorder.class);
    /** 环形缓冲容量。 */
    public static final int CAPACITY = 100;

    private final Deque<TemplateError> entries = new ConcurrentLinkedDeque<>();

    /**
     * 记录一次模板错误：分配编号、写日志、入缓冲。
     *
     * @param e 模板异常
     * @return 记录（含编号）
     */
    public TemplateError record(TemplateException e) {
        String id = newId();
        Long siteId = Optional.ofNullable(Contexts.findCurrentSite()).map(Site::getId).orElse(null);
        TemplateError error = new TemplateError(id, OffsetDateTime.now(), siteId, e.getTemplateSourceName(),
                e.getLineNumber(), e.getColumnNumber(), e.getMessage(), currentPath());
        entries.addFirst(error);
        while (entries.size() > CAPACITY) {
            entries.pollLast();
        }
        logger.error("Template error [{}] site={} template={} line={} column={} path={}\n{}", id, siteId,
                error.template(), error.line(), error.column(), error.path(), error.message());
        return error;
    }

    /**
     * 最近的错误，新的在前。
     *
     * @param siteId 站点 ID；{@code null} 不过滤。站点未知的记录（如错误页自身出错）对所有站点可见
     */
    public List<TemplateError> list(@Nullable Long siteId) {
        return entries.stream()
                .filter(it -> siteId == null || it.siteId() == null || siteId.equals(it.siteId()))
                .toList();
    }

    /**
     * 新的错误编号：8 位小写十六进制。只是关联日志与页面的标识，不是凭据。
     */
    public static String newId() {
        return String.format("%08x", ThreadLocalRandom.current().nextInt());
    }

    @Nullable
    private static String currentPath() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest().getRequestURI();
        }
        return null;
    }

    /**
     * 一次模板错误。
     *
     * @param id       错误编号
     * @param time     发生时间
     * @param siteId   站点 ID（渲染时无站点上下文则为 {@code null}）
     * @param template 模板名（FreeMarker 源名，如 {@code 1/default/templates/index.html}）
     * @param line     行号
     * @param column   列号
     * @param message  FreeMarker 错误信息（含 FTL 栈，不含 Java 堆栈）
     * @param path     请求 URI（无请求上下文则为 {@code null}）
     */
    public record TemplateError(String id, OffsetDateTime time, @Nullable Long siteId, @Nullable String template,
                                @Nullable Integer line, @Nullable Integer column, String message,
                                @Nullable String path) {
    }
}
