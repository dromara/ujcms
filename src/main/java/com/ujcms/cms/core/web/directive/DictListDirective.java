package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.service.DictService;
import com.ujcms.cms.core.service.args.DictArgs;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.commons.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.apache.commons.collections.CollectionUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 字典列表 标签
 *
 * @author PONY
 */
public class DictListDirective implements TemplateDirectiveModel {
    /**
     * 类型ID。整型（Integer）。
     */
    private static final String TYPE_ID = "typeId";
    /**
     * 类型别名。字符串（String）。
     */
    private static final String TYPE = "type";
    /**
     * 是否启用。布尔型（Boolean）。可选值：all(全部), false(禁用)。默认值：启用。
     */
    private static final String IS_ENABLED = "isEnabled";

    public static List<Dict> query(Map<String, ?> params, DictService dictService) {
        DictArgs args = DictArgs.of();

        Collection<Long> typeId = Directives.getLongs(params, TYPE_ID);
        Collection<String> typeAlias = Directives.getStrings(params, TYPE);
        if (CollectionUtils.isNotEmpty(typeId)) {
            args.inTypeIds(typeId);
        } else if (CollectionUtils.isNotEmpty(typeAlias)) {
            args.inTypeAlias(Directives.getStrings(params, TYPE));
        } else {
            throw new IllegalArgumentException("Params typeId or typeAlias is required.");
        }

        Optional.ofNullable(Directives.getBooleanDefault(params, IS_ENABLED, true)).ifPresent(args::enabled);

        Directives.handleOrderBy(args.getQueryMap(), params);
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);

        return dictService.selectList(args, offset, limit);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        List<Dict> list = query(params, dictService);

        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }


    private final DictService dictService;

    public DictListDirective(DictService dictService) {
        this.dictService = dictService;
    }
}
