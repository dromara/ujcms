package com.ujcms.common.security.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.springframework.lang.Nullable;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author PONY
 */
public abstract class BaseOauthClient {
    protected String provider;
    protected String clientId;
    protected String clientSecret;
    protected String redirectUri;
    @Nullable
    protected String scope;

    protected BaseOauthClient(String provider, String clientId, String clientSecret, String redirectUri,
                              @Nullable String scope) {
        this.provider = provider;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.scope = scope;
    }

    /**
     * 获取认证uri地址
     *
     * @return 认证uri地址
     */
    public abstract String getAuthorizationUri();

    /**
     * 获取Token请求uri地址
     *
     * @return Token请求uri地址
     */
    protected abstract String getTokenUri();

    /**
     * 获取用户信息uri地址
     *
     * @return 用户信息uri地址
     */
    protected abstract String getUserInfoUri();

    /**
     * 获取openid参数名
     *
     * @return openid参数名
     */
    protected abstract String getOpenIdParam();

    /**
     * 获取昵称字段名
     *
     * @return 昵称字段名
     */
    protected abstract String getNicknameField();

    /**
     * 从Token响应中填充OauthToken的openid、unionid、refreshToken等字段
     *
     * @param token OauthToken对象
     * @param map   Token响应Map
     */
    protected abstract void populateToken(OauthToken token, Map<String, String> map)
            throws URISyntaxException, JsonProcessingException;

    /**
     * 映射性别
     *
     * @param token OauthToken对象
     * @param map   用户信息Map
     */
    protected abstract void mapGender(OauthToken token, Map<String, Object> map);

    /**
     * 映射头像
     *
     * @param token OauthToken对象
     * @param map   用户信息Map
     */
    protected abstract void mapAvatar(OauthToken token, Map<String, Object> map);

    /**
     * 获取 OauthToken
     *
     * @param code 认证服务器返回的code
     * @return OauthToken对象
     */
    public OauthToken getOauthToken(String code) {
        try {
            URIBuilder builder = new URIBuilder(getTokenUri());
            builder.setParameter(getClientIdParam(), clientId);
            builder.setParameter(getClientSecretParam(), clientSecret);
            builder.setParameter(GRANT_TYPE, AUTHORIZATION_CODE);
            builder.setParameter(CODE, code);
            builder.setParameter(REDIRECT_URI, redirectUri);
            appendTokenParams(builder);

            Map<String, String> map = executeHttpForMap(createTokenRequest(builder));
            if (map.get(ACCESS_TOKEN) == null) {
                throw new IllegalStateException(provider + " get_access_token error: access_token not exist");
            }

            OauthToken token = new OauthToken();
            token.setProvider(provider);
            token.setAccessToken(map.get(ACCESS_TOKEN));
            populateToken(token, map);

            fillProfile(token);
            return token;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected void fillProfile(OauthToken token) throws URISyntaxException, JsonProcessingException {
        Map<String, Object> map = getProfileMap(token, getUserInfoUri(), getOpenIdParam(), getClientIdForProfile());
        String nicknameField = getNicknameField();
        if (map.get(nicknameField) == null) {
            throw new IllegalStateException(provider + " get_user_info error: " + nicknameField + " not exist");
        }
        token.setNickname((String) map.get(nicknameField));
        mapGender(token, map);
        mapAvatar(token, map);
    }

    public String getClientIdParam() {
        return CLIENT_ID;
    }

    protected String getClientSecretParam() {
        return CLIENT_SECRET;
    }

    /**
     * 追加Token请求的额外参数。子类可覆盖。
     */
    protected void appendTokenParams(URIBuilder builder) {
        // 默认无额外参数
    }

    /**
     * 创建Token请求。默认使用GET方式，子类可覆盖为POST。
     */
    protected ClassicHttpRequest createTokenRequest(URIBuilder builder) throws URISyntaxException {
        return new HttpGet(builder.build());
    }

    /**
     * 获取用户信息请求中传递的clientId。默认为null，子类可覆盖。
     */
    @Nullable
    protected String getClientIdForProfile() {
        return null;
    }

    public String getAuthorizationUrl(String state) {
        try {
            URIBuilder builder = new URIBuilder(getAuthorizationUri())
                    .setParameter(RESPONSE_TYPE, RESPONSE_TYPE_CODE)
                    .setParameter(getClientIdParam(), clientId)
                    .setParameter(REDIRECT_URI, redirectUri);
            if (scope != null) {
                builder.setParameter(SCOPE_PARAM, scope);
            }
            builder.setParameter(STATE, state);
            return builder.build().toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static Map<String, Object> getProfileMap(
            OauthToken token, String uri, String openIdParam, @Nullable String clientId)
            throws URISyntaxException, JsonProcessingException {
        URIBuilder builder = new URIBuilder(uri);
        if (clientId != null) {
            builder.setParameter("oauth_consumer_key", clientId);
        }
        builder.setParameter(ACCESS_TOKEN, token.getAccessToken());
        builder.setParameter(openIdParam, token.getOpenid());
        return executeHttpForMap(new HttpGet(builder.build()));
    }

    protected static <T> Map<String, T> executeHttpForMap(ClassicHttpRequest request) throws JsonProcessingException {
        String response = executeHttp(request);
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(response, new TypeReference<Map<String, T>>() {
        });
    }

    protected static String executeHttp(ClassicHttpRequest request) {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            return httpclient.execute(request, response -> {
                if (response.getCode() != HttpServletResponse.SC_OK) {
                    throw new IllegalStateException(String.format("Response status code: %d. request uri: %s",
                            response.getCode(), request.getRequestUri()));
                }
                return EntityUtils.toString(response.getEntity(), UTF_8);
            });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getProvider() {
        return provider;
    }

    public static final String SCOPE_PARAM = "scope";
    public static final String ACCESS_TOKEN = "access_token";
    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String REDIRECT_URI = "redirect_uri";
    public static final String CODE = "code";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String GRANT_TYPE = "grant_type";
    public static final String AUTHORIZATION_CODE = "authorization_code";
    public static final String STATE = "state";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String RESPONSE_TYPE = "response_type";
    public static final String RESPONSE_TYPE_CODE = "code";

    public static final String DISPLAY_NAME = "display_name";
    public static final String GENDER = "gender";
}
