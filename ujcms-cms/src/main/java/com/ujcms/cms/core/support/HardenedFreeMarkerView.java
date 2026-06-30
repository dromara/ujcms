package com.ujcms.cms.core.support;

import freemarker.ext.jakarta.servlet.FreemarkerServlet;
import freemarker.template.SimpleHash;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.view.freemarker.FreeMarkerView;

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
 * @author PONY
 * @see Frontends#setDate
 * @see com.ujcms.common.freemarker.SecureMemberAccessPolicy
 */
public class HardenedFreeMarkerView extends FreeMarkerView {
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
}
