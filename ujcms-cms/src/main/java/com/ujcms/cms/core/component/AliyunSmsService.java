package com.ujcms.cms.core.component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ujcms.common.sms.SmsError;
import com.ujcms.common.sms.SmsUtils;

import jakarta.xml.bind.DatatypeConverter;

/**
 * 阿里云短信服务
 * <p>
 * API文档: https://api.aliyun.com/document/Dysmsapi/2017-05-25/SendSms 签名机制:
 * https://help.aliyun.com/zh/sdk/product-overview/v3-request-structure-and-signature
 * 
 * @author PONY
 */
@Service
public class AliyunSmsService {
    /**
     * 服务商标识
     */
    public static final String PROVIDER = "aliyun";
    /** 仅用于解析 HTTP 错误响应体 */
    private static final ObjectMapper ERROR_BODY_MAPPER = new ObjectMapper();
    public static final String HOST = "dysmsapi.aliyuncs.com";
    private static final String ALGORITHM = "ACS3-HMAC-SHA256";
    private static final String SMS_ACTION = "SendSms";
    private static final String SMS_VERSION = "2017-05-25";
    private static final String SUCCESS_CODE = "OK";
    private static final String HTTP_METHOD = "POST";
    private static final String PATH = "/";
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String EMPTY_BODY = "";

    // 请求头名称常量
    private static final String HEADER_HOST = "host";
    private static final String HEADER_ACS_ACTION = "x-acs-action";
    private static final String HEADER_ACS_VERSION = "x-acs-version";
    private static final String HEADER_ACS_DATE = "x-acs-date";
    private static final String HEADER_ACS_NONCE = "x-acs-signature-nonce";
    private static final String HEADER_ACS_CONTENT_SHA256 = "x-acs-content-sha256";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "content-type";

    // 查询参数名称常量
    private static final String PARAM_PHONE_NUMBERS = "PhoneNumbers";
    private static final String PARAM_SIGN_NAME = "SignName";
    private static final String PARAM_TEMPLATE_CODE = "TemplateCode";
    private static final String PARAM_TEMPLATE_PARAM = "TemplateParam";

    // 响应字段名称常量
    private static final String RESPONSE_CODE = "Code";
    private static final String RESPONSE_MESSAGE = "Message";

    // 错误码对照官方文档：https://help.aliyun.com/zh/sms/developer-reference/api-error-codes
    // 账号级限额（isv.DAY_LIMIT_CONTROL、isv.MONTH_LIMIT_CONTROL）是管理员在阿里云控制台设置的总量阈值，
    // 终端用户稍后重试无效，归入其它错误。
    // 错误码：触发云通信流控限制。号码维度的发送频率上限（同一签名对同一号码，默认 1条/分钟、5条/小时、10条/天，按自然分钟、小时计）
    private static final String CODE_BUSINESS_LIMIT_CONTROL = "isv.BUSINESS_LIMIT_CONTROL";
    // 错误码：手机号码格式错误
    private static final String CODE_MOBILE_NUMBER_ILLEGAL = "isv.MOBILE_NUMBER_ILLEGAL";

    // 线程安全的日期格式化器（DateTimeFormatter是线程安全的，无需ThreadLocal）
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT)
            .withZone(ZoneOffset.UTC);

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
    public SmsError sendSms(String accessKeyId, String accessKeySecret, String signName, String templateCode,
            String codeName, String code, String mobile) {
        try {
            // 构建查询参数
            Map<String, Object> processedQueryParams = buildQueryParams(signName, templateCode, codeName, code, mobile);

            // 构建请求头
            Map<String, String> headers = buildHeaders();

            // 计算签名并添加到请求头
            SignatureResult signatureResult = calculateSignature(accessKeySecret, processedQueryParams, headers);
            addAuthorizationHeader(headers, accessKeyId, signatureResult.signature, signatureResult.signedHeaders);

            // 发送请求并解析响应
            JsonNode response = sendRequest(processedQueryParams, headers);
            return parseResponse(response);
        } catch (RestClientResponseException e) {
            // 凭证、签名类错误（如 InvalidAccessKeyId.NotFound）阿里云以 HTTP 4xx 返回，响应体同样带 Code / Message
            return parseErrorResponse(e);
        } catch (Exception e) {
            // 网络不通、超时等。与腾讯云实现一致：作为错误返回而非抛出，由调用方给出友好提示并记录日志，
            // 否则短信配置有误时，依赖验证码的流程（如双因子登录）只会得到一个 500
            return SmsError.other(PROVIDER, "SMS send error: " + e.getMessage());
        }
    }

    /**
     * 解析 HTTP 错误响应。响应体是阿里云的错误 JSON 时按错误码分类，否则只报告 HTTP 状态码
     */
    private SmsError parseErrorResponse(RestClientResponseException e) {
        try {
            JsonNode body = ERROR_BODY_MAPPER.readTree(e.getResponseBodyAsString());
            if (body != null && body.has(RESPONSE_CODE)) {
                SmsError error = parseResponse(body);
                if (error != null) {
                    return error;
                }
            }
        } catch (JsonProcessingException ignored) {
            // 响应体不是 JSON（如网关错误页）：按 HTTP 状态码报告
        }
        return SmsError.other(PROVIDER, "HTTP " + e.getStatusCode().value());
    }

    /**
     * 构建查询参数
     */
    private Map<String, Object> buildQueryParams(String signName, String templateCode, String codeName, String code,
            String mobile) {
        String templateParam = buildTemplateParamJson(codeName, code);

        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put(PARAM_PHONE_NUMBERS, mobile);
        queryParams.put(PARAM_SIGN_NAME, signName);
        queryParams.put(PARAM_TEMPLATE_CODE, templateCode);
        queryParams.put(PARAM_TEMPLATE_PARAM, templateParam);

        Map<String, Object> processedQueryParams = new TreeMap<>();
        processObject(processedQueryParams, "", queryParams);
        return processedQueryParams;
    }

    /**
     * 构建模板参数JSON
     */
    private String buildTemplateParamJson(String codeName, String code) {
        return "{\"" + codeName + "\":\"" + code + "\"}";
    }

    /**
     * 构建请求头
     */
    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new TreeMap<>();
        headers.put(HEADER_HOST, HOST);
        headers.put(HEADER_ACS_ACTION, SMS_ACTION);
        headers.put(HEADER_ACS_VERSION, SMS_VERSION);
        headers.put(HEADER_ACS_DATE, formatCurrentDate());
        headers.put(HEADER_ACS_NONCE, UUID.randomUUID().toString());

        String hashedRequestPayload = SmsUtils.sha256Hex(EMPTY_BODY);
        headers.put(HEADER_ACS_CONTENT_SHA256, hashedRequestPayload);
        return headers;
    }

    /**
     * 格式化当前日期为UTC时间
     */
    private String formatCurrentDate() {
        return DATE_FORMATTER.format(Instant.now());
    }

    /**
     * 计算签名
     */
    private SignatureResult calculateSignature(String accessKeySecret, Map<String, Object> processedQueryParams,
            Map<String, String> headers) {
        CanonicalHeadersResult canonicalHeadersResult = buildCanonicalHeaders(headers);
        String canonicalQueryString = buildCanonicalQueryString(processedQueryParams);
        String hashedRequestPayload = SmsUtils.sha256Hex(EMPTY_BODY);

        String canonicalRequest = buildCanonicalRequest(canonicalQueryString, canonicalHeadersResult,
                hashedRequestPayload);
        String stringToSign = buildStringToSign(canonicalRequest);

        byte[] signatureBytes = SmsUtils.hmac256(accessKeySecret.getBytes(StandardCharsets.UTF_8), stringToSign);
        String signature = DatatypeConverter.printHexBinary(signatureBytes).toLowerCase();
        return new SignatureResult(signature, canonicalHeadersResult.signedHeaders);
    }

    /**
     * 签名结果
     */
    private static class SignatureResult {
        final String signature;
        final String signedHeaders;

        SignatureResult(String signature, String signedHeaders) {
            this.signature = signature;
            this.signedHeaders = signedHeaders;
        }
    }

    /**
     * 构建规范请求
     */
    private String buildCanonicalRequest(String canonicalQueryString, CanonicalHeadersResult canonicalHeadersResult,
            String hashedRequestPayload) {
        return String.join("\n", HTTP_METHOD, PATH, canonicalQueryString, canonicalHeadersResult.canonicalHeaders,
                canonicalHeadersResult.signedHeaders, hashedRequestPayload);
    }

    /**
     * 构建待签名字符串
     */
    private String buildStringToSign(String canonicalRequest) {
        String hashedCanonicalRequest = SmsUtils.sha256Hex(canonicalRequest);
        return ALGORITHM + "\n" + hashedCanonicalRequest;
    }

    /**
     * 添加Authorization请求头
     */
    private void addAuthorizationHeader(Map<String, String> headers, String accessKeyId, String signature,
            String signedHeaders) {
        String authorization = String.format("%s Credential=%s,SignedHeaders=%s,Signature=%s", ALGORITHM, accessKeyId,
                signedHeaders, signature);
        headers.put(HEADER_AUTHORIZATION, authorization);
    }

    /**
     * 发送HTTP请求
     */
    @Nullable
    private JsonNode sendRequest(Map<String, Object> processedQueryParams, Map<String, String> headers) {
        URI uri = buildUri(processedQueryParams);
        RestClient.RequestBodySpec requestSpec = restClient.post().uri(uri);

        // 添加请求头
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestSpec = requestSpec.header(entry.getKey(), entry.getValue());
        }

        return requestSpec.retrieve().body(JsonNode.class);
    }

    /**
     * 构建URI（包含查询参数）
     */
    private URI buildUri(Map<String, Object> processedQueryParams) {
        StringBuilder uriBuilder = new StringBuilder(PATH);
        if (!processedQueryParams.isEmpty()) {
            uriBuilder.append("?");
            uriBuilder.append(processedQueryParams.entrySet().stream()
                    .map(entry -> percentCode(entry.getKey()) + "=" + percentCode(String.valueOf(entry.getValue())))
                    .collect(Collectors.joining("&")));
        }
        return URI.create(uriBuilder.toString());
    }

    /**
     * 处理复杂对象参数
     */
    private void processObject(Map<String, Object> map, String key, @Nullable Object value) {
        if (value == null) {
            return;
        }

        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); ++i) {
                processObject(map, key + "." + (i + 1), list.get(i));
            }
        } else if (value instanceof Map<?, ?> subMap) {
            for (Map.Entry<?, ?> entry : subMap.entrySet()) {
                processObject(map, key + "." + entry.getKey().toString(), entry.getValue());
            }
        } else {
            String finalKey = key.startsWith(".") ? key.substring(1) : key;

            if (value instanceof byte[] byteArray) {
                map.put(finalKey, new String(byteArray, StandardCharsets.UTF_8));
            } else {
                map.put(finalKey, String.valueOf(value));
            }
        }
    }

    /**
     * 构建规范查询字符串
     */
    private String buildCanonicalQueryString(Map<String, Object> queryParams) {
        return queryParams.entrySet().stream()
                .map(entry -> percentCode(entry.getKey()) + "=" + percentCode(String.valueOf(entry.getValue())))
                .collect(Collectors.joining("&"));
    }

    /**
     * 构建规范头部信息
     */
    private CanonicalHeadersResult buildCanonicalHeaders(Map<String, String> headers) {
        List<Map.Entry<String, String>> signedHeaders = headers.entrySet().stream()
                .filter(this::isSignedHeader)
                .sorted(Map.Entry.comparingByKey())
                .toList();

        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signedHeadersString = new StringBuilder();

        for (Map.Entry<String, String> entry : signedHeaders) {
            String lowerKey = entry.getKey().toLowerCase();
            String value = entry.getValue().trim();
            canonicalHeaders.append(lowerKey).append(":").append(value).append("\n");
            signedHeadersString.append(lowerKey).append(";");
        }

        // 移除最后的分号
        if (signedHeadersString.length() > 0) {
            signedHeadersString.setLength(signedHeadersString.length() - 1);
        }

        return new CanonicalHeadersResult(canonicalHeaders.toString(), signedHeadersString.toString());
    }

    /**
     * 判断是否为需要签名的请求头
     */
    private boolean isSignedHeader(Map.Entry<String, String> entry) {
        String key = entry.getKey().toLowerCase();
        return key.startsWith("x-acs-") || HEADER_HOST.equals(key) || HEADER_CONTENT_TYPE.equals(key);
    }

    private static class CanonicalHeadersResult {
        final String canonicalHeaders;
        final String signedHeaders;

        CanonicalHeadersResult(String canonicalHeaders, String signedHeaders) {
            this.canonicalHeaders = canonicalHeaders;
            this.signedHeaders = signedHeaders;
        }
    }


    /**
     * URL编码
     */
    private String percentCode(@Nullable String str) {
        if (str == null) {
            return "";
        }
        try {
            return URLEncoder.encode(str, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E",
                    "~");
        } catch (Exception e) {
            throw new IllegalStateException("UTF-8 encoding not supported", e);
        }
    }

    /**
     * 解析响应结果
     */
    @Nullable
    private SmsError parseResponse(@Nullable JsonNode response) {
        if (response == null) {
            return SmsError.other(PROVIDER, "Invalid response from SMS service");
        }

        String code = response.has(RESPONSE_CODE) ? response.get(RESPONSE_CODE).asText() : null;
        String message = response.has(RESPONSE_MESSAGE) ? response.get(RESPONSE_MESSAGE).asText() : null;

        if (SUCCESS_CODE.equals(code)) {
            return null; // 成功
        }

        return SmsError.of(classify(code), PROVIDER, code != null ? code : "Unknown", message);
    }

    /**
     * 错误码分类
     */
    private static SmsError.Kind classify(@Nullable String code) {
        if (CODE_BUSINESS_LIMIT_CONTROL.equals(code)) {
            return SmsError.Kind.RATE_LIMITED;
        }
        if (CODE_MOBILE_NUMBER_ILLEGAL.equals(code)) {
            return SmsError.Kind.INVALID_MOBILE;
        }
        return SmsError.Kind.OTHER;
    }
}