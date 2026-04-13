package com.ujcms.common.security.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URISyntaxException;
import java.util.Map;

import static com.ujcms.common.db.Constants.*;

/**
 * @author PONY
 */
public class QqOauthClient extends BaseOauthClient {
    public QqOauthClient(String clientId, String clientSecret, String redirectUri) {
        super(PROVIDER_QQ, clientId, clientSecret, redirectUri, DEFAULT_SCOPE);
    }

    @Override
    public String getAuthorizationUri() {
        return "https://graph.qq.com/oauth2.0/authorize";
    }

    @Override
    protected String getTokenUri() {
        return "https://graph.qq.com/oauth2.0/token";
    }

    @Override
    protected String getUserInfoUri() {
        return "https://graph.qq.com/user/get_user_info";
    }

    @Override
    protected String getOpenIdParam() {
        return OPENID;
    }

    @Override
    protected String getNicknameField() {
        return NICKNAME;
    }

    @Override
    protected String getClientIdForProfile() {
        return clientId;
    }

    @Override
    protected void appendTokenParams(URIBuilder builder) {
        builder.setParameter(FORMAT, FORMAT_JSON);
    }

    @Override
    protected void populateToken(OauthToken token, Map<String, String> map)
            throws URISyntaxException, JsonProcessingException {
        token.setRefreshToken(map.get(REFRESH_TOKEN));
        token.setOpenid(getOpenid(token.getAccessToken()));
    }

    @Override
    protected void mapGender(OauthToken token, Map<String, Object> map) {
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
    }

    @Override
    protected void mapAvatar(OauthToken token, Map<String, Object> map) {
        token.setAvatarUrl((String) map.get(FIGUREURL_QQ_1));
        token.setLargeAvatarUrl((String) map.get(FIGUREURL_QQ_2));
    }

    private String getOpenid(String accessToken) throws URISyntaxException, JsonProcessingException {
        URIBuilder builder = new URIBuilder(OPENID_URI);
        builder.setParameter(ACCESS_TOKEN, accessToken);
        builder.setParameter(FORMAT, FORMAT_JSON);
        Map<String, String> map = executeHttpForMap(new HttpGet(builder.build()));
        return map.get(OPENID);
    }

    public static final String PROVIDER_QQ = "qq";
    public static final String OPENID_URI = "https://graph.qq.com/oauth2.0/me";
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
