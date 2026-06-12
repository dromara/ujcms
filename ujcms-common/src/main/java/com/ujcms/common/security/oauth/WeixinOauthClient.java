package com.ujcms.common.security.oauth;

import java.util.Map;

import static com.ujcms.common.db.Constants.*;

/**
 * @author PONY
 */
public class WeixinOauthClient extends BaseOauthClient {
    public WeixinOauthClient(String clientId, String clientSecret, String redirectUri) {
        super(PROVIDER_WEIXIN, clientId, clientSecret, redirectUri, DEFAULT_SCOPE);
    }

    @Override
    public String getAuthorizationUri() {
        return "https://open.weixin.qq.com/connect/qrconnect";
    }

    @Override
    protected String getTokenUri() {
        return "https://api.weixin.qq.com/sns/oauth2/access_token";
    }

    @Override
    protected String getUserInfoUri() {
        return "https://api.weixin.qq.com/sns/userinfo";
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
    public String getClientIdParam() {
        return APPID;
    }

    @Override
    protected String getClientSecretParam() {
        return SECRET;
    }

    @Override
    protected void populateToken(OauthToken token, Map<String, String> map) {
        token.setRefreshToken(map.get(REFRESH_TOKEN));
        token.setOpenid(map.get(OPENID));
        token.setUnionid(map.get(UNIONID));
    }

    @Override
    protected void mapGender(OauthToken token, Map<String, Object> map) {
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
    }

    @Override
    protected void mapAvatar(OauthToken token, Map<String, Object> map) {
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
