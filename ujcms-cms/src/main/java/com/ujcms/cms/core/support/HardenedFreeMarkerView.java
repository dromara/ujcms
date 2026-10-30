package com.ujcms.cms.core.support;

import freemarker.core.Environment;
import freemarker.ext.jakarta.servlet.FreemarkerServlet;
import freemarker.template.SimpleHash;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.view.freemarker.FreeMarkerView;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Map;

/**
 * 加固版 {@link FreeMarkerView}：移除 Spring 默认注入模板的 Servlet 作用域变量
 * （{@code Application}、{@code Session}、{@code Request}、{@code RequestParameters}、{@code JspTaglibs}）。
 *
 * <p>这些变量会向模板暴露<strong>实时</strong>的 {@code ServletContext}、Spring 容器与 Tomcat
 * {@code InstanceManager} 引用，是模板注入（SSTI）提权为远程命令执行（RCE）的关键入口。静态化生成路径
 * （{@link Frontends#setDate}）已通过空 {@code Map} 屏蔽这些变量，此类使动态渲染路径与之保持一致。
 *
 * <p>本类属纵深防御：即便攻击者经其它途径（如 {@code AllHttpScopesHashModel} 对 ServletContext 属性的回退
 * 查找）取得敏感对象引用，{@link com.ujcms.common.freemarker.SecureMemberAccessPolicy} 仍会禁止其方法调用。
 *
 * <p>渲染先整页写入内存缓冲，再一次写出响应（docs/spec/template-error-disclosure D4）：模板中途出错时响应尚未
 * 提交，Tomcat 才能把它换成真正的 500 错误页；直接写响应则页面一过输出缓冲就以 200 发出半页，访客与开发者
 * 都看不到任何错误，且该半页还会按前台页的 Cache-Control 被浏览器缓存。同时在渲染环境上置
 * {@link TemplateErrorHandler#INLINE_DETAILS}——只有这条前台动态页面路径允许向操作员内联错误细节。
 *
 * @author PONY
 * @see Frontends#setDate
 * @see com.ujcms.common.freemarker.SecureMemberAccessPolicy
 * @see TemplateErrorHandler
 */
public class HardenedFreeMarkerView extends FreeMarkerView {
    /** 页面缓冲初始容量（字符）。默认主题的页面约 60 KB，一次扩容即可容纳。 */
    private static final int INITIAL_BUFFER = 32 * 1024;

    @Override
    protected SimpleHash buildTemplateModel(Map<String, Object> model, HttpServletRequest request,
                                            HttpServletResponse response) {
        SimpleHash fmModel = super.buildTemplateModel(model, request, response);
        Map<String, Object> empty = Collections.emptyMap();
        fmModel.put(FreemarkerServlet.KEY_APPLICATION, empty);
        fmModel.put(FreemarkerServlet.KEY_SESSION, empty);
        fmModel.put(FreemarkerServlet.KEY_REQUEST, empty);
        fmModel.put(FreemarkerServlet.KEY_REQUEST_PARAMETERS, empty);
        fmModel.put(FreemarkerServlet.KEY_JSP_TAGLIBS, empty);
        return fmModel;
    }

    @Override
    protected void processTemplate(Template template, SimpleHash model, HttpServletResponse response)
            throws IOException, TemplateException {
        StringWriter buffer = new StringWriter(INITIAL_BUFFER);
        Environment env = template.createProcessingEnvironment(model, buffer);
        env.setCustomAttribute(TemplateErrorHandler.INLINE_DETAILS, Boolean.TRUE);
        env.process();
        response.getWriter().write(buffer.toString());
    }
}
