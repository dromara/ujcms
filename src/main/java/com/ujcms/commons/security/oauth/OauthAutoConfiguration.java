package com.ujcms.commons.security.oauth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OAuth自动配置类
 *
 * @author PONY
 */
@Configuration
@EnableConfigurationProperties(OauthClientProperties.class)
public class OauthAutoConfiguration {
    @Bean(name = "qqOauthClient")
    @ConditionalOnProperty(prefix = "oauth.client.qq", name = "client-id")
    public QqBaseOauthClient qqOauthClient(OauthClientProperties props) {
        OauthClientProperties.Client client = props.getClient().get("qq");
        return new QqBaseOauthClient(client.getClientId(), client.getClientSecret(), client.getRedirectUri());
    }

    @Bean(name = "weixinOauthClient")
    @ConditionalOnProperty(prefix = "oauth.client.weixin", name = "client-id")
    public WeixinBaseOauthClient weixinOauthClient(OauthClientProperties props) {
        OauthClientProperties.Client client = props.getClient().get("weixin");
        return new WeixinBaseOauthClient(client.getClientId(), client.getClientSecret(), client.getRedirectUri());
    }

    @Bean(name = "weiboOauthClient")
    @ConditionalOnProperty(prefix = "oauth.client.weibo", name = "client-id")
    public WeiboBaseOauthClient weiboOauthClient(OauthClientProperties props) {
        OauthClientProperties.Client client = props.getClient().get("weibo");
        return new WeiboBaseOauthClient(client.getClientId(), client.getClientSecret(), client.getRedirectUri());
    }

}
