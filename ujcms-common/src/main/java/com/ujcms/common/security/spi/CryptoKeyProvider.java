package com.ujcms.common.security.spi;

/**
 * 密钥与密码运算 SPI（密评 密码产品 / 密钥管理接缝）。
 * <p>
 * 默认实现 {@code LocalCryptoKeyProvider}（cms 模块）使用 BouncyCastle 软算法 + 内置的
 * {@code ujcms.data-secret-key} 派生密钥，行为与现有逻辑一致。后续若客户提供 SDF（GM/T 0018）
 * 密码机或 KMS，可新增实现替换，无需改动调用点（Phase 5）。本接缝于 Phase 2 引入，当前仅暴露
 * 审计日志签名所需的 HMAC-SM3；SM4 加解密等运算待密码机适配落地时再扩展。
 *
 * @author PONY
 */
public interface CryptoKeyProvider {
    /**
     * 计算带领域隔离的 HMAC-SM3，返回 64 个十六进制字符。
     * <p>
     * 密钥按领域派生（{@code key = SM3(dataSecretKey + ":" + domain)}），不同用途互不重叠；
     * 与 {@code data-secret-key} 同源同生命周期——轮换该密钥会使历史签名失效。
     *
     * @param domain 用途领域（如 {@code "audit"}）
     * @param data   待签名数据（已规范化的规范串）
     * @return HMAC-SM3 十六进制串（64 字符）
     */
    String hmacSm3Hex(String domain, String data);
}
