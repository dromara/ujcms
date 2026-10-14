package com.ujcms.cms.core.marketplace;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.Constants;

/**
 * 连接持久化（D-C1-1）：JSON ↔ {@code ujcms_config.marketplace_settings_}。加密由挂在
 * ConfigMapper.xml 上的 {@code Sm4StringTypeHandler} 透明完成（内存明文、数据库密文）；
 * 写入走单列 {@code updateMarketplaceSettings}——通用配置整行保存不触碰本列，令牌轮换
 * 不会被后台「全局设置」的陈旧读回滚。
 */
@Component
public class MarketplaceConnectionStore {
    private static final Logger logger = LoggerFactory.getLogger(MarketplaceConnectionStore.class);

    private final ConfigService configService;

    public MarketplaceConnectionStore(ConfigService configService) {
        this.configService = configService;
    }

    /** 当前连接；未连接或历史数据不可解析（如密钥轮换后的密文残骸）返回 {@code null} */
    @Nullable
    public MarketplaceConnection load() {
        String settings = configService.getUnique().getMarketplaceSettings();
        if (settings == null || settings.isBlank()) {
            return null;
        }
        try {
            return Constants.MAPPER.readValue(settings, MarketplaceConnection.class);
        } catch (JsonProcessingException e) {
            logger.warn("Unparseable marketplace connection settings; treating as disconnected: {}", e.getMessage());
            return null;
        }
    }

    /** 持久化连接（令牌轮换语义：先落库、再作废旧令牌——调用方在本方法返回后才可使用新令牌） */
    public void save(MarketplaceConnection connection) {
        try {
            configService.updateMarketplaceSettings(Constants.MAPPER.writeValueAsString(connection));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize marketplace connection", e);
        }
    }

    /** 清除连接（断开） */
    public void clear() {
        configService.updateMarketplaceSettings(null);
    }
}
