package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.UserOpenidBase;
import com.ujcms.commons.security.oauth.OauthToken;

import java.io.Serializable;

/**
 * 用户Openid实体类
 *
 * @author PONY
 */
public class UserOpenid extends UserOpenidBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public UserOpenid() {
    }

    public UserOpenid(Long id, String provider) {
        setUserId(id);
        setProvider(provider);
    }

    public UserOpenid(Long userId, OauthToken token) {
        setUserId(userId);
        setProvider(token.getProvider());
        setOpenid(token.getUnionidOrOpenId());
        setNickname(token.getNickname());
        setGender(token.getGender());
        setAvatarUrl(token.getAvatarUrl());
        setLargeAvatarUrl(token.getLargeAvatarUrl());
    }
}