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
public class WeixinBaseOauthClient extends BaseOauthClient {
    public WeixinBaseOauthClient(String clientId, String clientSecret, String redirectUri) {
        super(PROVIDER_WEIXIN, clientId, clientSecret, redirectUri, DEFAULT_SCOPE);
    }

    @Override
    public String getAuthorizationUri() {
        return "https://open.weixin.qq.com/connect/qrconnect";
    }

    public static final String TOKEN_URI = "https://api.weixin.qq.com/sns/oauth2/access_token";
    public static final String USER_INFO_URI = "https://api.weixin.qq.com/sns/userinfo";

    @Override
    public String getClientIdParam() {
        return APPID;
    }

    @Override
    public OauthToken getOauthToken(String code) {
        try {
            URIBuilder builder = new URIBuilder(TOKEN_URI);
            builder.setParameter(APPID, clientId);
            builder.setParameter(SECRET, clientSecret);
            builder.setParameter(GRANT_TYPE, AUTHORIZATION_CODE);
            builder.setParameter(CODE, code);
            builder.setParameter(REDIRECT_URI, redirectUri);
            Map<String, String> map = executeHttpForMap(new HttpGet(builder.build()));
            if (map.get(ACCESS_TOKEN) == null) {
                throw new IllegalStateException("Weixin get_access_token error: access_token not exist");
            }
            OauthToken token = new OauthToken();
            token.setProvider(PROVIDER_WEIXIN);
            token.setAccessToken(map.get(ACCESS_TOKEN));
            token.setRefreshToken(map.get(REFRESH_TOKEN));
            token.setOpenid(map.get(OPENID));
            token.setUnionid(map.get(UNIONID));
            fillProfile(token);
            return token;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void fillProfile(OauthToken token) throws URISyntaxException, JsonProcessingException {
        Map<String, Object> map = getProfileMap(token, USER_INFO_URI, OPENID, null);
        if (map.get(NICKNAME) == null) {
            throw new IllegalStateException("Weixin get_userinfo error: nickname not exist");
        }
        token.setNickname((String) map.get(NICKNAME));
        int sex = ((Number) map.get(SEX)).intValue();
        int male = 1;
        int female = 2;
        if (sex == male) {
            token.setGender(GENDER_MALE);
        } else if (sex == female) {
            token.setGender(GENDER_FEMALE);
        } else {
            token.setGender(GENDER_NONE);
        }
        token.setAvatarUrl((String) map.get(HEADIMGURL));
        token.setLargeAvatarUrl((String) map.get(HEADIMGURL));
    }

    /**
     * 第三方登录提供者
     */
    public static final String PROVIDER_WEIXIN = "weixin";
    /**
     * client_id 在微信里面为 appid
     */
    public static final String APPID = "appid";
    /**
     * client_secret 在微信里面为 secret
     */
    public static final String SECRET = "secret";
    /**
     * 默认获取的权限
     */
    public static final String DEFAULT_SCOPE = "snsapi_login";
    /**
     * 普通用户的标识，对当前开发者帐号唯一
     */
    public static final String OPENID = "openid";
    /**
     * 用户统一标识。针对一个微信开放平台帐号下的应用，同一用户的 unionid 是唯一的。
     */
    public static final String UNIONID = "unionid";
    /**
     * 普通用户昵称
     */
    public static final String NICKNAME = "nickname";
    /**
     * 用户头像，最后一个数值代表正方形头像大小（有0、46、64、96、132数值可选，0代表640*640正方形头像），用户没有头像时该项为空
     */
    public static final String HEADIMGURL = "headimgurl";
    /**
     * 普通用户性别，1为男性，2为女性
     */
    public static final String SEX = "sex";
}
