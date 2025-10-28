package com.ujcms.cms.core.component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.ujcms.commons.sms.SmsUtils;

import jakarta.xml.bind.DatatypeConverter;

/**
 * 阿里云短信服务
 * <p>
 * API文档: https://api.aliyun.com/document/Dysmsapi/2017-05-25/SendSms
 * 签名机制: https://help.aliyun.com/zh/sdk/product-overview/v3-request-structure-and-signature
 * 
 * @author PONY
 */
@Service
public class AliyunSmsService {
    public static final String ACTION = "SendSms";
    public static final String VERSION = "2017-05-25";
    public static final String ALGORITHM = "ACS3-HMAC-SHA256";
    public static final String HOST = "dysmsapi.aliyuncs.com";
    public static final String CONTENT_TYPE = "application/json";

    private static final String SUCCESS_CODE = "OK";
    private static final String CODE_KEY = "Code";
    private static final String MESSAGE_KEY = "Message";
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    private final RestClient restClient;

    public AliyunSmsService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("https://" + HOST).build();
    }

    /**
     * 发送短信
     * 
     * @param accessKeyId     阿里云 AccessKey ID
     * @param accessKeySecret 阿里云 AccessKey Secret
     * @param signName        短信签名
     * @param templateCode    短信模板CODE
     * @param codeName        模板参数名称
     * @param code            验证码
     * @param mobile          手机号
     * @return 返回 {@code null} 代表成功，否则返回错误信息
     */
    @Nullable
    public String sendSms(String accessKeyId, String accessKeySecret, String signName, String templateCode,
            String codeName, String code, String mobile) {
        try {
            // 生成时间戳和日期
            String timestamp = getUtcTimestamp();
            String nonce = UUID.randomUUID().toString();

            // 构建查询参数
            String queryString = buildQueryString(signName, templateCode, codeName, code, mobile);

            // 空请求体的 SHA256 值
            String emptyBodySha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

            // 构建签名和授权信息
            String authorization = buildAuthorization(accessKeyId, accessKeySecret, timestamp, nonce, 
                    emptyBodySha256, queryString);

            // 发送请求
            JsonNode response = sendRequest(timestamp, nonce, emptyBodySha256, authorization, queryString);

            // 处理响应
            return processResponse(response);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return "Signature error: " + e.getMessage();
        } catch (Exception e) {
            return "SMS send error: " + e.getMessage();
        }
    }

    /**
     * 获取 UTC 时间戳 (ISO 8601 格式)
     */
    private String getUtcTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    /**
     * 构建查询参数字符串
     */
    private String buildQueryString(String signName, String templateCode, String codeName, 
            String code, String mobile) {
        // 构建模板参数 JSON
        String templateParam = "{\"" + codeName + "\":\"" + code + "\"}";

        // 按参数名称字母序排序构建查询字符串
        return "PhoneNumbers=" + urlEncode(mobile) + 
               "&SignName=" + urlEncode(signName) + 
               "&TemplateCode=" + urlEncode(templateCode) + 
               "&TemplateParam=" + urlEncode(templateParam);
    }

    /**
     * URL 编码
     */
    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, UTF8.name())
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            throw new IllegalStateException("URL encode error", e);
        }
    }

    /**
     * 构建授权信息
     */
    private String buildAuthorization(String accessKeyId, String accessKeySecret, String timestamp, 
            String nonce, String contentSha256, String queryString)
            throws NoSuchAlgorithmException, InvalidKeyException {
        
        // ************* 步骤 1：拼接规范请求串 *************
        String canonicalHeaders = "host:" + HOST + "\n" + 
                                 "x-acs-action:" + ACTION.toLowerCase() + "\n" +
                                 "x-acs-content-sha256:" + contentSha256 + "\n" +
                                 "x-acs-date:" + timestamp + "\n" +
                                 "x-acs-signature-nonce:" + nonce + "\n" +
                                 "x-acs-version:" + VERSION + "\n";
        
        String signedHeaders = "host;x-acs-action;x-acs-content-sha256;x-acs-date;x-acs-signature-nonce;x-acs-version";
        
        // CanonicalRequest = HTTPMethod + '\n' + CanonicalURI + '\n' + CanonicalQueryString + '\n' + 
        //                    CanonicalHeaders + '\n' + SignedHeaders + '\n' + HashedRequestPayload
        String canonicalRequest = "POST\n" +
                                 "/\n" +
                                 queryString + "\n" +
                                 canonicalHeaders + "\n" +
                                 signedHeaders + "\n" +
                                 contentSha256;

        // ************* 步骤 2：拼接待签名字符串 *************
        String hashedCanonicalRequest = SmsUtils.sha256Hex(canonicalRequest);
        String stringToSign = ALGORITHM + "\n" + hashedCanonicalRequest;

        // ************* 步骤 3：计算签名 *************
        String signature = calculateSignature(accessKeySecret, stringToSign);

        // ************* 步骤 4：拼接 Authorization *************
        return ALGORITHM + " Credential=" + accessKeyId + 
               ",SignedHeaders=" + signedHeaders + 
               ",Signature=" + signature;
    }

    /**
     * 计算签名
     */
    private String calculateSignature(String accessKeySecret, String stringToSign)
            throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] signatureBytes = SmsUtils.hmac256(accessKeySecret.getBytes(UTF8), stringToSign);
        return DatatypeConverter.printHexBinary(signatureBytes).toLowerCase();
    }

    /**
     * 发送 HTTP 请求
     */
    @Nullable
    private JsonNode sendRequest(String timestamp, String nonce, String contentSha256, 
            String authorization, String queryString) {
        try {
            return restClient.post()
                    .uri("/?" + queryString)
                    .header("Authorization", authorization)
                    .header("Content-Type", CONTENT_TYPE)
                    .header("host", HOST)
                    .header("x-acs-action", ACTION)
                    .header("x-acs-version", VERSION)
                    .header("x-acs-date", timestamp)
                    .header("x-acs-signature-nonce", nonce)
                    .header("x-acs-content-sha256", contentSha256)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send SMS request", e);
        }
    }

    /**
     * 处理响应结果
     */
    @Nullable
    private String processResponse(@Nullable JsonNode response) {
        // 检查响应码
        if (response != null && response.has(CODE_KEY)) {
            String responseCode = response.get(CODE_KEY).asText();
            
            // 成功状态码为 "OK"
            if (SUCCESS_CODE.equalsIgnoreCase(responseCode)) {
                return null; // null means success
            }

            // 返回错误信息
            String message = response.has(MESSAGE_KEY) ? 
                    response.get(MESSAGE_KEY).asText() : "Unknown error";
            return "code: " + responseCode + ", message: " + message;
        }

        return "Invalid response format";
    }
}