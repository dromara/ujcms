package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.ext.domain.generated.GeneratedFavoriteGroup;

/**
 * 收藏夹实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class FavoriteGroup extends GeneratedFavoriteGroup {
    public static final String NOT_FOUND = "FavoriteGroup not found. ID: ";
}
