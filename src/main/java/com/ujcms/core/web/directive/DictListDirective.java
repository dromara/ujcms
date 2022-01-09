package com.ujcms.core.web.directive;

import com.ujcms.core.domain.Dict;
import com.ujcms.core.service.DictService;
import com.ujcms.core.web.support.Directives;
import com.ofwise.util.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final String TYPE_ALIAS = "typeAlias";
    /**
     * 是否启用。布尔型（Boolean）。可选值：all(全部), false(禁用)。默认值：启用。
     */
    private static final String ENABLED = "isEnabled";

    public static List<Dict> query(Map<String, ?> params, DictService dictService) {
        Map<String, Object> queryMap = new HashMap<>(16);

        Integer typeId = Directives.getInteger(params, TYPE_ID);
        String typeAlias = Directives.getString(params, TYPE_ALIAS);
        if (typeId != null) {
            queryMap.put("EQ_typeId_Int", typeId);
        } else if (StringUtils.isNotBlank(typeAlias)) {
            queryMap.put("EQ_dictType-alias", typeAlias);
        } else {
            throw new IllegalArgumentException("Params typeId or typeAlias is required.");
        }

        Boolean enabled = Directives.getDefaultBoolean(params, ENABLED, true);
        if (enabled != null) {
            queryMap.put("EQ_enabled_Boolean", enabled);
        }

        Directives.handleOrderBy(queryMap, params);
        Integer offset = Directives.findOffset(params);
        Integer limit = Directives.findLimit(params);

        return dictService.selectList(queryMap, offset, limit);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.checkLoopVars(loopVars);
        Freemarkers.checkBody(body);

        List<Dict> list = query(params, dictService);

        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }


    private DictService dictService;

    public DictListDirective(DictService dictService) {
        this.dictService = dictService;
    }
}
