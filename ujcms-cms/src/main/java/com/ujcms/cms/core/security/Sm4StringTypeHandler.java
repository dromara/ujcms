package com.ujcms.cms.core.security;

import com.ujcms.cms.core.support.StaticProps;
import com.ujcms.common.security.Secures;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.jspecify.annotations.Nullable;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 对整列字符串做国密 SM4-GCM 加解密的 MyBatis {@link org.apache.ibatis.type.TypeHandler}。
 * <p>
 * 仅<strong>显式</strong>挂在存放第三方凭据的列上（短信/邮件/对象存储配置，见
 * {@code ConfigMapper.xml}）。不要注册为 String 的全局处理器，否则会加密所有字符串列。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>实体内存中始终是<strong>明文</strong> JSON；只有数据库列存密文。读取时解密、写入时加密，
 *       对领域对象、管理接口、模板渲染完全透明。</li>
 *   <li>密文带 {@value #PREFIX} 前缀，用于：(1) 历史明文透明兼容——无前缀按明文原样返回，
 *       下次保存时自动加密（懒迁移）；(2) 幂等——已加密值不再二次加密。</li>
 *   <li>密钥来自 {@link StaticProps#getDataSecretKey()}（即 {@code ujcms.data-secret-key}，
 *       与数据库分离）。密钥变更将导致历史密文无法解密。</li>
 * </ul>
 *
 * @author PONY
 */
public class Sm4StringTypeHandler extends BaseTypeHandler<String> {
    /** 密文标记前缀。 */
    public static final String PREFIX = "{sm4}";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, encrypt(parameter));
    }

    @Override
    public @Nullable String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }

    @Override
    public @Nullable String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }

    @Override
    public @Nullable String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }

    /**
     * 加密待入库的明文。空值或已加密值原样返回（幂等）。
     */
    static String encrypt(String value) {
        if (StringUtils.isEmpty(value) || value.startsWith(PREFIX)) {
            return value;
        }
        return PREFIX + Secures.sm4Encrypt(value, StaticProps.getDataSecretKey());
    }

    /**
     * 解密入库密文。{@code null} 或无 {@value #PREFIX} 前缀（历史明文）原样返回。
     */
    @Nullable
    static String decrypt(@Nullable String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        String cipher = stored.substring(PREFIX.length());
        try {
            return Secures.sm4Decrypt(cipher, StaticProps.getDataSecretKey());
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new IllegalStateException("解密敏感配置列失败：ujcms.config-secret-key 是否在存储后发生过变更？", e);
        }
    }
}
