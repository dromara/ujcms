package com.ujcms.commons.security.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;

import java.net.URISyntaxException;
import java.util.Map;

import static com.ujcms.commons.db.Constants.*;

/**
 * @author PONY
 */
public class WeiboBaseOauthClient extends BaseOauthClient {
    public WeiboBaseOauthClient(String clientId, String clientSecret, String redirectUri) {
        super(PROVIDER_WEIBO, clientId, clientSecret, redirectUri, null);
    }

    @Override
    public String getAuthorizationUri() {
        return "https://api.weibo.com/oauth2/authorize";
    }

    public static final String TOKEN_URI = "https://api.weibo.com/oauth2/access_token";
    public static final String USER_INFO_URI = "https://api.weibo.com/2/users/show.json";

    @Override
    public OauthToken getOauthToken(String code) {
        try {
            URIBuilder builder = new URIBuilder(TOKEN_URI);
            builder.setParameter(CLIENT_ID, clientId);
            builder.setParameter(CLIENT_SECRET, clientSecret);
            builder.setParameter(GRANT_TYPE, AUTHORIZATION_CODE);
            builder.setParameter(CODE, code);
            builder.setParameter(REDIRECT_URI, redirectUri);
            // 微博要求该api必须使用http post请求，否则出错。
            Map<String, String> map = executeHttpForMap(new HttpPost(builder.build()));
            if (map.get(ACCESS_TOKEN) == null) {
                throw new IllegalStateException("Weibo get_access_token error: access_token not exist");
            }
            OauthToken token = new OauthToken();
            token.setProvider(PROVIDER_WEIBO);
            token.setAccessToken(map.get(ACCESS_TOKEN));
            token.setOpenid(map.get(UID));
            fillProfile(token);
            return token;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void fillProfile(OauthToken token) throws URISyntaxException, JsonProcessingException {
        Map<String, Object> map = getProfileMap(token, USER_INFO_URI, UID, clientId);
        if (map.get(SCREEN_NAME) == null) {
            throw new IllegalStateException("Weibo get_user_info error: screen_name not exist");
        }
        token.setNickname((String) map.get(SCREEN_NAME));
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
