package com.ujcms.util.security.oauth;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * @author PONY
 */
public class OauthToken {
    private String provider = "";
    private String accessToken = "";
    private String refreshToken = "";
    private String openid = "";
    private String unionid = "";
    private String nickname = "";
    /**
     * m:男;f:女;n:未设置
     */
    private String gender = "n";
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

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
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
