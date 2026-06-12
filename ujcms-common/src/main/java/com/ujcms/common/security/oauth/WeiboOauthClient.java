package com.ujcms.common.security.oauth;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URISyntaxException;
import java.util.Map;

import static com.ujcms.common.db.Constants.*;

/**
 * @author PONY
 */
public class WeiboOauthClient extends BaseOauthClient {
    public WeiboOauthClient(String clientId, String clientSecret, String redirectUri) {
        super(PROVIDER_WEIBO, clientId, clientSecret, redirectUri, null);
    }

    @Override
    public String getAuthorizationUri() {
        return "https://api.weibo.com/oauth2/authorize";
    }

    @Override
    protected String getTokenUri() {
        return "https://api.weibo.com/oauth2/access_token";
    }

    @Override
    protected String getUserInfoUri() {
        return "https://api.weibo.com/2/users/show.json";
    }

    @Override
    protected String getOpenIdParam() {
        return UID;
    }

    @Override
    protected String getNicknameField() {
        return SCREEN_NAME;
    }

    @Override
    protected String getClientIdForProfile() {
        return clientId;
    }

    /**
     * 微博要求该api必须使用http post请求，否则出错。
     */
    @Override
    protected ClassicHttpRequest createTokenRequest(URIBuilder builder) throws URISyntaxException {
        return new HttpPost(builder.build());
    }

    @Override
    protected void populateToken(OauthToken token, Map<String, String> map) {
        token.setOpenid(map.get(UID));
    }

    @Override
    protected void mapGender(OauthToken token, Map<String, Object> map) {
        String gender = (String) map.get(GENDER);
        String male = "m";
        String female = "f";
        if (male.equalsIgnoreCase(gender)) {
            token.setGender(GENDER_MALE);
        } else if (female.equalsIgnoreCase(gender)) {
            token.setGender(GENDER_FEMALE);
        } else {
            token.setGender(GENDER_NONE);
        }
    }

    @Override
    protected void mapAvatar(OauthToken token, Map<String, Object> map) {
        token.setAvatarUrl((String) map.get(PROFILE_IMAGE_URL));
        if (StringUtils.isNotBlank((String) map.get(AVATAR_HD))) {
            token.setLargeAvatarUrl((String) map.get(AVATAR_HD));
        } else if (StringUtils.isNotBlank((String) map.get(AVATAR_LARGE))) {
            token.setLargeAvatarUrl((String) map.get(AVATAR_LARGE));
        } else {
            token.setLargeAvatarUrl((String) map.get(PROFILE_IMAGE_URL));
        }
    }

    public static final String PROVIDER_WEIBO = "weibo";
    public static final String UID = "uid";
    public static final String SCREEN_NAME = "screen_name";
    /**
     * profile_image_url string 用户头像地址（中图），50×50像素
     */
    public static final String PROFILE_IMAGE_URL = "profile_image_url";
    /**
     * avatar_large string 用户头像地址（大图），180×180像素
     */
    public static final String AVATAR_LARGE = "avatar_large";
    /**
     * avatar_hd string 用户头像地址（高清），高清头像原图
     */
    public static final String AVATAR_HD = "avatar_hd";
}
