package com.ofwise.util.security;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SaltedAuthenticationInfo;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.apache.shiro.util.ByteSource;
import org.springframework.util.Assert;

/**
 * 证书加密适配器
 *
 * @author liufang
 */
public class CredentialsMatcherAdapter implements CredentialsMatcher {
    private CredentialsDigest credentialsDigest;

    public CredentialsMatcherAdapter(CredentialsDigest credentialsDigest) {
        Assert.notNull(credentialsDigest, "The argument must not be null");
        this.credentialsDigest = credentialsDigest;
    }

    @Override
    public boolean doCredentialsMatch(AuthenticationToken token, AuthenticationInfo info) {
        String plainCredentials, credentials;
        byte[] saltByte = null;
        plainCredentials = toStringCredentials(token.getCredentials());
        if (info instanceof SaltedAuthenticationInfo) {
            ByteSource salt = ((SaltedAuthenticationInfo) info).getCredentialsSalt();
            if (salt != null) {
                saltByte = salt.getBytes();
            }
        }
        credentials = toStringCredentials(info.getCredentials());
        return credentialsDigest.matches(credentials, plainCredentials, saltByte);
    }

    private static String toStringCredentials(Object credentials) {
        if (credentials == null) {
            return null;
        } else if (credentials instanceof String) {
            return (String) credentials;
        } else if (credentials instanceof char[]) {
            return new String((char[]) credentials);
        } else {
            throw new IllegalArgumentException("credentials only support String or char[].");
        }
    }
}
