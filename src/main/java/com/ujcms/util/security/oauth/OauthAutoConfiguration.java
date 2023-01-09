package com.ujcms.util.security.oauth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 验证码自动配置类
 *
 * @author PONY
 */
@Configuration
@EnableConfigurationProperties(OauthClientProperties.class)
public class OauthAutoConfiguration {
    @Bean(name = "qqOauthClient")
    @ConditionalOnProperty(prefix = "oauth.client.qq", name = "client-id")
    public QqOauthClient qqOauthClient(OauthClientProperties props) {
        OauthClientProperties.Client client = props.getClient().get("qq");
        return new QqOauthClient(client.getClientId(), client.getClientSecret(), client.getRedirectUri());
    }

    @Bean(name = "weixinOauthClient")
    @ConditionalOnProperty(prefix = "oauth.client.weixin", name = "client-id")
    public WeixinOauthClient weixinOauthClient(OauthClientProperties props) {
        OauthClientProperties.Client client = props.getClient().get("weixin");
        return new WeixinOauthClient(client.getClientId(), client.getClientSecret(), client.getRedirectUri());
    }

    @Bean(name = "weiboOauthClient")
    @ConditionalOnProperty(prefix = "oauth.client.weibo", name = "client-id")
    public WeiboOauthClient weiboOauthClient(OauthClientProperties props) {
        OauthClientProperties.Client client = props.getClient().get("weibo");
        return new WeiboOauthClient(client.getClientId(), client.getClientSecret(), client.getRedirectUri());
    }

}
