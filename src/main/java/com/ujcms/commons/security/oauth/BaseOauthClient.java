package com.ujcms.commons.security.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.lang.Nullable;

import javax.servlet.http.HttpServletResponse;
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
     * 获取 OauthToken
     *
     * @param code 认证服务器返回的code
     * @return OauthToken对象
     */
    public abstract OauthToken getOauthToken(String code);

    public String getClientIdParam() {
        return CLIENT_ID;
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

    protected static <T> Map<String, T> executeHttpForMap(HttpUriRequest request) throws JsonProcessingException {
        String response = executeHttp(request);
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(response, new TypeReference<Map<String, T>>() {
        });
    }

    protected static String executeHttp(HttpUriRequest request) {
        try (CloseableHttpClient httpclient = HttpClients.createDefault();
             CloseableHttpResponse response = httpclient.execute(request)) {
            if (response.getStatusLine().getStatusCode() != HttpServletResponse.SC_OK) {
                throw new IllegalStateException(String.format("Response status code: %d. request uri: %s",
                        response.getStatusLine().getStatusCode(), request.getURI()));
            }
            return EntityUtils.toString(response.getEntity(), UTF_8);
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
