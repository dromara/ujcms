package com.ujcms.commons.security.oauth;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.io.Serializable;

import static com.ujcms.commons.db.Constants.GENDER_NONE;

/**
 * @author PONY
 */
public class OauthToken implements Serializable {
    private static final long serialVersionUID = -5831436914799847004L;

    private String provider = "";
    private String accessToken = "";
    private String refreshToken = "";
    private String openid = "";
    private String unionid = "";
    private String nickname = "";
    /**
     * 1:男,2:女,0:未设置
     */
    private Short gender = GENDER_NONE;
    @Nullable
    private String avatarUrl;
    @Nullable
    private String largeAvatarUrl;

    public String getUnionidOrOpenId() {
        if (StringUtils.isNotBlank(getUnionid())) {
            return getUnionid();
        }
        return getOpenid();
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public String getUnionid() {
        return unionid;
    }

    public void setUnionid(String unionid) {
        this.unionid = unionid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Short getGender() {
        return gender;
    }

    public void setGender(Short gender) {
        this.gender = gender;
    }

    @Nullable
    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(@Nullable String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    @Nullable
    public String getLargeAvatarUrl() {
        return largeAvatarUrl;
    }

    public void setLargeAvatarUrl(@Nullable String largeAvatarUrl) {
        this.largeAvatarUrl = largeAvatarUrl;
    }
}
