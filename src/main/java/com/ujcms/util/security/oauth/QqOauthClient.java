package com.ujcms.util.security.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;

import java.net.URISyntaxException;
import java.util.Map;

/**
 * @author PONY
 */
public class QqOauthClient extends OauthClient {
    public QqOauthClient(String clientId, String clientSecret, String redirectUri) {
        super(PROVIDER, clientId, clientSecret, redirectUri, DEFAULT_SCOPE);
    }

    @Override
    public String getAuthorizationUri() {
        return "https://graph.qq.com/oauth2.0/authorize";
    }

    public static final String TOKEN_URI = "https://graph.qq.com/oauth2.0/token";
    public static final String OPENID_URI = "https://graph.qq.com/oauth2.0/me";
    public static final String USER_INFO_URI = "https://graph.qq.com/user/get_user_info";

    @Override
    public OauthToken getOauthToken(String code) throws URISyntaxException, JsonProcessingException {
        URIBuilder builder = new URIBuilder(TOKEN_URI);
        builder.setParameter(CLIENT_ID, clientId);
        builder.setParameter(CLIENT_SECRET, clientSecret);
        builder.setParameter(GRANT_TYPE, AUTHORIZATION_CODE);
        builder.setParameter(CODE, code);
        builder.setParameter(REDIRECT_URI, redirectUri);
        builder.setParameter(FORMAT, FORMAT_JSON);
        String response = executeHttp(new HttpGet(builder.build()));
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> map = objectMapper.readValue(response, new TypeReference<Map<String, String>>() {
        });
        if (map.get(ACCESS_TOKEN) == null) {
            throw new RuntimeException("QQ get_access_token error: " + response);
        }
        OauthToken token = new OauthToken();
        token.setProvider(PROVIDER);
        token.setAccessToken(map.get(ACCESS_TOKEN));
        token.setRefreshToken(map.get(REFRESH_TOKEN));
        token.setOpenid(getOpenid(token.getAccessToken()));
        fillProfile(token);
        return token;
    }

    private String getOpenid(String accessToken) throws URISyntaxException, JsonProcessingException {
        URIBuilder builder = new URIBuilder(OPENID_URI);
        builder.setParameter(ACCESS_TOKEN, accessToken);
        builder.setParameter(FORMAT, FORMAT_JSON);
        // 返回内容为 `callback( {"client_id":"YOUR_APPID","openid":"YOUR_OPENID"} ); `
        String response = executeHttp(new HttpGet(builder.build()));
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> map = objectMapper.readValue(response, new TypeReference<Map<String, String>>() {
        });
        return map.get(OPENID);
    }

    private void fillProfile(OauthToken token) throws URISyntaxException, JsonProcessingException {
        URIBuilder builder = new URIBuilder(USER_INFO_URI);
        builder.setParameter("oauth_consumer_key", clientId);
        builder.setParameter(ACCESS_TOKEN, token.getAccessToken());
        builder.setParameter(OPENID, token.getOpenid());
        String response = executeHttp(new HttpGet(builder.build()));
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> map = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {
        });
        if (map.get(NICKNAME) == null) {
            throw new RuntimeException("QQ get_user_info error: " + response);
        }
        token.setNickname((String) map.get(NICKNAME));
        String gender = (String) map.get(GENDER);
        if (GENDER_MALE.equalsIgnoreCase(gender)) {
            token.setGender("m");
        } else if (GENDER_FEMALE.equalsIgnoreCase(gender)) {
            token.setGender("f");
        } else {
            token.setGender("n");
        }
        token.setAvatarUrl((String) map.get(FIGUREURL_QQ_1));
        token.setLargeAvatarUrl((String) map.get(FIGUREURL_QQ_2));
    }

    public static final String PROVIDER = "qq";
    public static final String FORMAT = "fmt";
    public static final String FORMAT_JSON = "json";
    public static final String DEFAULT_SCOPE = "get_user_info";
    public static final String OPENID = "openid";
    public static final String GENDER_MALE = "男";
    public static final String GENDER_FEMALE = "女";
    public static final String NICKNAME = "nickname";
    /**
     * figureurl_qq_1 大小为40×40像素的QQ头像URL
     */
    public static final String FIGUREURL_QQ_1 = "figureurl_qq_1";
    /**
     * figureurl_qq_2 大小为100×100像素的QQ头像URL。需要注意，不是所有的用户都拥有QQ的100x100的头像，但40x40像素则是一定会有。
     */
    public static final String FIGUREURL_QQ_2 = "figureurl_qq_2";
}
