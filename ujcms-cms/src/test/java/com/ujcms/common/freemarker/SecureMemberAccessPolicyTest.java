package com.ujcms.common.freemarker;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import no.api.freemarker.java8.Java8ObjectWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SecureMemberAccessPolicy} 回归测试。
 *
 * <p>复现 CNVD 通报的 FreeMarker 模板注入(SSTI)提权为 RCE 的核心利用环节，验证加固策略将其阻断，
 * 同时不影响普通领域对象的 getter 访问。
 *
 * @author PONY
 */
class SecureMemberAccessPolicyTest {

    private static Configuration configuration(boolean secure) {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        Java8ObjectWrapper wrapper = new Java8ObjectWrapper(Configuration.VERSION_2_3_31);
        if (secure) {
            wrapper.setMemberAccessPolicy(new SecureMemberAccessPolicy(Configuration.VERSION_2_3_31));
        }
        cfg.setObjectWrapper(wrapper);
        cfg.setLogTemplateExceptions(false);
        return cfg;
    }

    private static String process(Configuration cfg, String tpl, Map<String, Object> model) throws Exception {
        Template template = new Template("test", new StringReader(tpl), cfg);
        StringWriter out = new StringWriter();
        template.process(model, out);
        return out.toString();
    }

    /** 普通 POJO 的 getter 不应被策略拦截。 */
    @Test
    void allowsNormalBeanGetter() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("bean", new SampleBean());
        assertEquals("ok", process(configuration(true), "${bean.name}", model));
    }

    /** 不启用策略时，SpEL 解析器的 parseExpression/getValue 可被调用——即漏洞利用链可达。 */
    @Test
    void spelReachableWithoutPolicy() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("p", new SpelExpressionParser());
        assertEquals("2", process(configuration(false), "${p.parseExpression('1+1').value?c}", model));
    }

    /** 启用策略后，SpEL 解析器的全部成员被禁止，利用链在 parseExpression 处被阻断。 */
    @Test
    void blocksSpelExpressionParser() {
        Map<String, Object> model = new HashMap<>();
        model.put("p", new SpelExpressionParser());
        assertThrows(TemplateException.class,
                () -> process(configuration(true), "${p.parseExpression('1+1').value?c}", model));
    }

    /** 启用策略后，Spring 容器的 getBean/containsBean 被禁止，无法借容器取得任意 Bean。 */
    @Test
    void blocksApplicationContextAccess() {
        Map<String, Object> model = new HashMap<>();
        model.put("ctx", new StaticApplicationContext());
        assertThrows(TemplateException.class,
                () -> process(configuration(true), "${ctx.containsBean('x')?c}", model));
    }

    public static class SampleBean {
        public String getName() {
            return "ok";
        }
    }
}
