package com.ujcms.commons.security.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;

import java.net.URISyntaxException;
import java.util.Map;

import static com.ujcms.commons.db.Constants.*;

/**
 * @author PONY
 */
public class QqBaseOauthClient extends BaseOauthClient {
    public QqBaseOauthClient(String clientId, String clientSecret, String redirectUri) {
        super(PROVIDER_QQ, clientId, clientSecret, redirectUri, DEFAULT_SCOPE);
    }

    @Override
    public String getAuthorizationUri() {
        return "https://graph.qq.com/oauth2.0/authorize";
    }

    public static final String TOKEN_URI = "https://graph.qq.com/oauth2.0/token";
    public static final String OPENID_URI = "https://graph.qq.com/oauth2.0/me";
    public static final String USER_INFO_URI = "https://graph.qq.com/user/get_user_info";

    @Override
    public OauthToken getOauthToken(String code) {
        try {
            URIBuilder builder = new URIBuilder(TOKEN_URI);
            builder.setParameter(CLIENT_ID, clientId);
            builder.setParameter(CLIENT_SECRET, clientSecret);
            builder.setParameter(GRANT_TYPE, AUTHORIZATION_CODE);
            builder.setParameter(CODE, code);
            builder.setParameter(REDIRECT_URI, redirectUri);
            builder.setParameter(FORMAT, FORMAT_JSON);
            Map<String, String> map = executeHttpForMap(new HttpGet(builder.build()));
            if (map.get(ACCESS_TOKEN) == null) {
                throw new IllegalStateException("QQ get_access_token error: access_token not exist");
            }
            OauthToken token = new OauthToken();
            token.setProvider(PROVIDER_QQ);
            token.setAccessToken(map.get(ACCESS_TOKEN));
            token.setRefreshToken(map.get(REFRESH_TOKEN));
            token.setOpenid(getOpenid(token.getAccessToken()));
            fillProfile(token);
            return token;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String getOpenid(String accessToken) throws URISyntaxException, JsonProcessingException {
        URIBuilder builder = new URIBuilder(OPENID_URI);
        builder.setParameter(ACCESS_TOKEN, accessToken);
        builder.setParameter(FORMAT, FORMAT_JSON);
        Map<String, String> map = executeHttpForMap(new HttpGet(builder.build()));
        return map.get(OPENID);
    }

    private void fillProfile(OauthToken token) throws URISyntaxException, JsonProcessingException {
        Map<String, Object> map = getProfileMap(token, USER_INFO_URI, OPENID, clientId);
        if (map.get(NICKNAME) == null) {
            throw new IllegalStateException("QQ get_user_info error: nickname not exist");
        }
        token.setNickname((String) map.get(NICKNAME));
        String gender = (String) map.get(GENDER);
        String male = "男";
        String female = "女";
        if (male.equalsIgnoreCase(gender)) {
            token.setGender(GENDER_MALE);
        } else if (female.equalsIgnoreCase(gender)) {
            token.setGender(GENDER_FEMALE);
        } else {
            token.setGender(GENDER_NONE);
        }
        token.setAvatarUrl((String) map.get(FIGUREURL_QQ_1));
        token.setLargeAvatarUrl((String) map.get(FIGUREURL_QQ_2));
    }

    public static final String PROVIDER_QQ = "qq";
    public static final String FORMAT = "fmt";
    public static final String FORMAT_JSON = "json";
    public static final String DEFAULT_SCOPE = "get_user_info";
    public static final String OPENID = "openid";
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
