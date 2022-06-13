package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.service.DictService;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.web.directive.DictListDirective;
import com.ujcms.util.query.QueryUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 字典前台 接口
 *
 * @author PONY
 */
@RestController
@RequestMapping(UrlConstants.API + "/dict")
public class DictController {
    private DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping
    public List<Dict> list(HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        return DictListDirective.query(params, dictService);
    }

}
