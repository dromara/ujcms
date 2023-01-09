package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.GroupBase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户组实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Group extends GroupBase implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 访问权限，栏目ID列表。非数据库属性，用于接收前台请求。
     */
    @Schema(description = "访问权限，栏目ID列表。非数据库属性，用于接收前台请求。")
    private List<Integer> accessPermissions = new ArrayList<>();

    public List<Integer> getAccessPermissions() {
        return accessPermissions;
    }

    public void setAccessPermissions(List<Integer> accessPermissions) {
        this.accessPermissions = accessPermissions;
    }

    /**
     * 游客组ID
     */
    public static final Integer ANONYMOUS_ID = 1;
    /**
     * 默认会员组
     */
    public static final Integer MEMBER_GROUP_ID = 10;
}