package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.cms.core.service.DictService;
import com.ujcms.cms.core.service.args.DictArgs;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code <@DictList>} 指令的站点范围（dictionary plan P1）：当前站点取自数据模型的 {@code site}，
 * 无论按 {@code type}（别名）还是 {@code typeId} 取用，可见范围恒为本站 ∪ 全局。
 */
class DictListDirectiveTest {
    private final DictService dictService = mock(DictService.class);

    private String render(String template) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setSharedVariable("DictList", new DictListDirective(dictService));
        StringTemplateLoader loader = new StringTemplateLoader();
        loader.putTemplate("t", template);
        cfg.setTemplateLoader(loader);
        Template fm = cfg.getTemplate("t");
        StringWriter out = new StringWriter();
        Map<String, Object> model = new HashMap<>();
        Site site = new Site();
        site.setId(7L);
        model.put("site", site);
        fm.process(model, out);
        return out.toString();
    }

    private DictArgs capturedArgs() {
        ArgumentCaptor<DictArgs> captor = ArgumentCaptor.forClass(DictArgs.class);
        verify(dictService).selectList(captor.capture(), anyInt(), anyInt());
        return captor.getValue();
    }

    private static void assertScoped(DictArgs args) {
        Map<String, Object> queryMap = args.getQueryMap();
        assertEquals(7L, queryMap.get("EQ_0scope_type@dictType-siteId"));
        assertEquals(EntityConstants.SCOPE_GLOBAL, queryMap.get("EQ_0scope_type@dictType-scope"));
    }

    @Test
    void aliasLookupIsScopedToCurrentSite() throws Exception {
        Dict dict = new Dict();
        dict.setValue("zh-CN");
        when(dictService.selectList(any(DictArgs.class), anyInt(), anyInt())).thenReturn(List.of(dict));

        String out = render("<@DictList type='sys_site_lang'; list><#list list as d>${d.value};</#list></@DictList>");

        assertEquals("zh-CN;", out);
        DictArgs args = capturedArgs();
        assertScoped(args);
        Object alias = args.getQueryMap().get("In_type@dictType-alias");
        assertTrue(alias instanceof Collection<?> c && c.contains("sys_site_lang"));
    }

    @Test
    void typeIdLookupIsScopedToo() throws Exception {
        when(dictService.selectList(any(DictArgs.class), anyInt(), anyInt())).thenReturn(List.of());

        render("<@DictList typeId=5; list>${list?size}</@DictList>");

        DictArgs args = capturedArgs();
        assertScoped(args);
        Object typeIds = args.getQueryMap().get("In_typeId_Long");
        assertTrue(typeIds instanceof Collection<?> c && c.contains(5L));
    }
}
