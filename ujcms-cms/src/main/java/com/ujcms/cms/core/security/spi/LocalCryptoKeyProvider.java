package com.ujcms.cms.core.security.spi;

import com.ujcms.cms.core.support.StaticProps;
import com.ujcms.common.security.Secures;
import com.ujcms.common.security.spi.CryptoKeyProvider;
import com.ujcms.common.web.Strings;
import org.springframework.stereotype.Component;

/**
 * 默认密钥提供者：使用 BouncyCastle 软算法，密钥派生自 {@code ujcms.data-secret-key}
 * （{@link StaticProps#getDataSecretKey()}），与 {@link com.ujcms.cms.core.security.PiiBlindIndex} 同一套派生方式。
 *
 * @author PONY
 */
@Component
public class LocalCryptoKeyProvider implements CryptoKeyProvider {
    @Override
    public String hmacSm3Hex(String domain, String data) {
        // key = SM3(dataSecretKey + ":" + domain)，每次重算不缓存，便于密钥变更后即时生效（同 PiiBlindIndex）
        byte[] key = Strings.toBytes(Secures.sm3Hex(StaticProps.getDataSecretKey() + ":" + domain));
        return Secures.hmacSm3Hex(key, Strings.toBytes(data));
    }
}
