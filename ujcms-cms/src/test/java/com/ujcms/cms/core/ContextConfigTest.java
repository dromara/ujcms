package com.ujcms.cms.core;

import com.ujcms.cms.core.component.LangAlternateService;
import com.ujcms.cms.core.composition.RegionTemplateSupport;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.security.TemplateMemberAccessPolicy;
import com.ujcms.cms.core.service.*;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.ErrorDisclosure;
import com.ujcms.cms.core.support.TemplateErrorHandler;
import com.ujcms.cms.core.support.TemplateErrorRecorder;
import com.ujcms.cms.core.web.directive.*;
import com.ujcms.common.freemarker.*;
import freemarker.core.TemplateClassResolver;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ResourceLoader;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@link ContextConfig} 注册守卫测试：
 * <ul>
 * <li>每个共享变量名必须绑定到对应的指令/方法类，防止复制粘贴注册错误再次发生
 * （曾出现 "ModelList"→ModelDirective、"Model"→ArticleNextDirective 的双重错绑，design-review-2026-07 T2）</li>
 * <li>{@code new_builtin_class_resolver} 沙箱必须由 Java 代码固定为 ALLOWS_NOTHING_RESOLVER，
 * 不依赖 yaml 配置（design-review-2026-07 T1）</li>
 * </ul>
 */
class ContextConfigTest {

    private final Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);

    @BeforeEach
    void setUp() {
        ContextConfig contextConfig = new ContextConfig(
                mock(SiteService.class), mock(ChannelService.class),
                mock(ArticleLucene.class), mock(ArticleService.class), mock(ArticleRelatedService.class),
                mock(ModelService.class),
                mock(TagService.class), mock(DictService.class), mock(BlockItemService.class),
                mock(BlockService.class), mock(RegionTemplateSupport.class),
                mock(LangAlternateService.class),
                configuration, mock(Props.class), mock(ResourceLoader.class),
                // 真实处理器：匿名查看者路径重新抛出异常（Mockito mock 会吞掉异常，使 ?new 的拒绝无法观测）
                new TemplateErrorHandler(new TemplateErrorRecorder(), new ErrorDisclosure(mock(UserService.class))));
        contextConfig.afterPropertiesSet();
    }

    @Test
    void sharedVariablesBindToExpectedClasses() throws TemplateException {
        Map<String, Class<?>> expected = new LinkedHashMap<>(64);
        expected.put("SiteList", SiteListDirective.class);
        expected.put("Site", SiteDirective.class);
        expected.put("LangSiteList", LangSiteListDirective.class);
        expected.put("ChannelList", ChannelListDirective.class);
        expected.put("Channel", ChannelDirective.class);
        expected.put("ArticleList", ArticleListDirective.class);
        expected.put("ArticlePage", ArticlePageDirective.class);
        expected.put("Article", ArticleDirective.class);
        expected.put("ArticlePrev", ArticlePrevDirective.class);
        expected.put("ArticleNext", ArticleNextDirective.class);
        expected.put("ModelList", ModelListDirective.class);
        expected.put("Model", ModelDirective.class);
        expected.put("DictList", DictListDirective.class);
        expected.put("BlockItemList", BlockItemListDirective.class);
        expected.put("BlockList", BlockListDirective.class);
        expected.put("region", RegionDirective.class);
        expected.put("TagList", TagListDirective.class);
        expected.put("TagPage", TagPageDirective.class);
        expected.put("EsArticleList", EsArticleListDirective.class);
        expected.put("EsArticlePage", EsArticlePageDirective.class);
        expected.put("A", AnchorDirective.class);
        expected.put("substring", SubstringMethod.class);
        expected.put("bbcode", BbCodeMethod.class);
        expected.put("format", FormatMethod.class);
        expected.put("paging", PagingMethod.class);
        expected.put("addParam", AddParamMethod.class);

        expected.forEach((name, clazz) -> assertThat(configuration.getSharedVariable(name))
                .as("共享变量 [%s] 应绑定 %s", name, clazz.getSimpleName())
                .isInstanceOf(clazz));
    }

    @Test
    void newBuiltinClassResolverPinnedToAllowsNothing() {
        assertThat(configuration.getNewBuiltinClassResolver())
                .isSameAs(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
    }

    /**
     * 对象包装器必须装配模板可达面策略（design-review-2026-07 T5）。少了这一步，Config/User 上由
     * Sm4StringTypeHandler 解密出的凭据与 PII 就会重新对模板可见——拒绝表本身的正确性由
     * {@code TemplateMemberAccessPolicyTest} 保证，这里只钉住"策略确实挂上了"。
     */
    @Test
    void objectWrapperCarriesTemplateMemberAccessPolicy() {
        assertThat(configuration.getObjectWrapper()).isInstanceOf(BeansWrapper.class);
        assertThat(((BeansWrapper) configuration.getObjectWrapper()).getMemberAccessPolicy())
                .isInstanceOf(TemplateMemberAccessPolicy.class);
    }

    @Test
    void newBuiltinRejectedAtRender() throws Exception {
        // 与生产配置一致（yaml: tag_syntax: square_bracket），否则中括号标签被当作纯文本
        configuration.setTagSyntax(Configuration.SQUARE_BRACKET_TAG_SYNTAX);
        Template template = new Template("scratch",
                new StringReader("[#assign ex='freemarker.template.utility.Execute'?new()]"), configuration);
        assertThatThrownBy(() -> template.process(Map.of(), new StringWriter()))
                .isInstanceOf(TemplateException.class)
                .hasMessageContaining("not allowed in the template for security reasons");
    }
}
