package com.ujcms.util.security.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;

import java.net.URISyntaxException;
import java.util.Map;

/**
 * @author PONY
 */
public class WeiboOauthClient extends OauthClient {
    public WeiboOauthClient(String clientId, String clientSecret, String redirectUri) {
        super(PROVIDER, clientId, clientSecret, redirectUri, null);
    }

    @Override
    public String getAuthorizationUri() {
        return "https://api.weibo.com/oauth2/authorize";
    }

    public static final String TOKEN_URI = "https://api.weibo.com/oauth2/access_token";
    public static final String USER_INFO_URI = "https://api.weibo.com/2/users/show.json";

    @Override
    public OauthToken getOauthToken(String code) throws URISyntaxException, JsonProcessingException {
        URIBuilder builder = new URIBuilder(TOKEN_URI);
        builder.setParameter(CLIENT_ID, clientId);
        builder.setParameter(CLIENT_SECRET, clientSecret);
        builder.setParameter(GRANT_TYPE, AUTHORIZATION_CODE);
        builder.setParameter(CODE, code);
        builder.setParameter(REDIRECT_URI, redirectUri);
        // 微博要求该api必须使用http post请求，否则出错。
        String response = executeHttp(new HttpPost(builder.build()));
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> map = objectMapper.readValue(response, new TypeReference<Map<String, String>>() {
        });
        if (map.get(ACCESS_TOKEN) == null) {
            throw new RuntimeException("Weibo get_access_token error: " + response);
        }
        OauthToken token = new OauthToken();
        token.setProvider(PROVIDER);
        token.setAccessToken(map.get(ACCESS_TOKEN));
        token.setOpenid(map.get(UID));
        fillUserInfo(token);
        return token;
    }

    private void fillUserInfo(OauthToken token) throws URISyntaxException, JsonProcessingException {
        URIBuilder builder = new URIBuilder(USER_INFO_URI);
        builder.setParameter("oauth_consumer_key", clientId);
        builder.setParameter(ACCESS_TOKEN, token.getAccessToken());
        builder.setParameter(UID, token.getOpenid());
        String response = executeHttp(new HttpGet(builder.build()));
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> map = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {
        });
        if (map.get(SCREEN_NAME) == null) {
            throw new RuntimeException("Weibo get_user_info error: " + response);
        }
        token.setNickname((String) map.get(SCREEN_NAME));
        token.setGender((String) map.get(GENDER));
        token.setAvatarUrl((String) map.get(PROFILE_IMAGE_URL));
        if (StringUtils.isNotBlank((String) map.get(AVATAR_HD))) {
            token.setLargeAvatarUrl((String) map.get(AVATAR_HD));
        } else if (StringUtils.isNotBlank((String) map.get(AVATAR_LARGE))) {
            token.setLargeAvatarUrl((String) map.get(AVATAR_LARGE));
        } else {
            token.setLargeAvatarUrl((String) map.get(PROFILE_IMAGE_URL));
        }
    }

    public static final String PROVIDER = "weibo";
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
