package com.ujcms.cms.core.support;

import com.ujcms.common.freemarker.SimpleTemplateExceptionHandler;
import freemarker.core.Environment;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.Writer;

/**
 * 模板异常处理器（docs/spec/template-error-disclosure D1）：所有 profile 共用的唯一处理器，取代按 profile 在
 * {@code rethrow} 与 {@link SimpleTemplateExceptionHandler} 之间切换的 yaml 配置。
 * <p>
 * 每个错误先经 {@link TemplateErrorRecorder} 记录（编号 + 日志 + 环形缓冲），再按查看者决定呈现：
 * <ul>
 * <li>前台动态页面（{@link HardenedFreeMarkerView} 在渲染环境上置 {@link #INLINE_DETAILS}）且查看者是后台操作员
 * （{@link ErrorDisclosure}）：把带编号的错误信息内联写进页面并<b>继续渲染</b>，操作员一次看到整页的全部错误；</li>
 * <li>其它情况（匿名访客、前台会员、静态页生成、邮件等非页面渲染）：重新抛出，由 Spring 换成带错误编号的通用
 * 错误页——绝不把细节写进静态文件或访客页面。</li>
 * </ul>
 * {@code [#attempt]} 块内一律重新抛出，交还 FreeMarker 执行 {@code [#recover]}：在块内吞掉异常会让 recover 永不执行。
 *
 * @author PONY
 */
@Component
public class TemplateErrorHandler implements TemplateExceptionHandler {
    /**
     * {@link Environment} 自定义属性名：置 {@link Boolean#TRUE} 的渲染环境允许向操作员内联输出错误细节。
     * 只有前台动态页面视图置此标记；静态页生成、邮件模板等共享同一 {@code Configuration} 的渲染不置。
     */
    public static final String INLINE_DETAILS = "ujcms.inlineTemplateErrors";
    /**
     * 请求属性名：本次请求最近一次模板错误的编号，错误页（{@code DisclosureErrorAttributes}）复用它，
     * 使访客看到的编号与日志、控制台「最近错误」里的一致。
     */
    public static final String ERROR_ID_ATTRIBUTE = "ujcms.errorId";

    private final TemplateErrorRecorder recorder;
    private final ErrorDisclosure disclosure;

    public TemplateErrorHandler(TemplateErrorRecorder recorder, ErrorDisclosure disclosure) {
        this.recorder = recorder;
        this.disclosure = disclosure;
    }

    @Override
    public void handleTemplateException(TemplateException te, Environment env, Writer out) throws TemplateException {
        TemplateErrorRecorder.TemplateError error = recorder.record(te);
        exposeErrorId(error.id());
        if (env.isInAttemptBlock() || !inlineDetails(env) || !disclosure.canViewDetails()) {
            throw te;
        }
        SimpleTemplateExceptionHandler.printError(out, "[" + error.id() + "] " + te.getMessage());
    }

    private static boolean inlineDetails(Environment env) {
        return Boolean.TRUE.equals(env.getCustomAttribute(INLINE_DETAILS));
    }

    private static void exposeErrorId(String id) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(ERROR_ID_ATTRIBUTE, id, RequestAttributes.SCOPE_REQUEST);
        }
    }
}
