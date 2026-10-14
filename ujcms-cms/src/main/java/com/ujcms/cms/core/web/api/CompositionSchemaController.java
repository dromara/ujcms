package com.ujcms.cms.core.web.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ujcms.cms.core.composition.CompositionSchemas;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.ujcms.cms.core.support.UrlConstants.API;

/**
 * 已发布 JSON Schema 接口（§8.4）。编辑器通过 {@code $schema} 引用即可即时校验；
 * Spring 加载期与（未来的）Node CLI 使用同一组 Schema，无第二套校验规则可漂移。
 *
 * @author PONY
 */
@RestController
@RequestMapping(API + "/composition/schemas")
public class CompositionSchemaController {
    private final CompositionSchemas schemas;

    public CompositionSchemaController(CompositionSchemas schemas) {
        this.schemas = schemas;
    }

    @GetMapping
    public Map<String, JsonNode> schemas() {
        return schemas.getRawSchemas();
    }
}
