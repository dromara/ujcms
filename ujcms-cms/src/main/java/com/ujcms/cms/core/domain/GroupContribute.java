package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.generated.GeneratedGroupContribute;

/**
 * 用户组投稿权限实体类（docs/spec/member-contribute/plan.md）。栏目开启“允许投稿”后，本表无行表示全部用户组可投，
 * 有行则只有所列用户组可投
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class GroupContribute extends GeneratedGroupContribute {

    public GroupContribute() {
    }

    public GroupContribute(Long groupId, Long channelId, Long siteId) {
        setGroupId(groupId);
        setChannelId(channelId);
        setSiteId(siteId);
    }
}
