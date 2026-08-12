package com.widyu.pay.config;

import com.widyu.pay.infrastructure.PaymentGatewayException;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentErrorDecoder implements ErrorDecoder {

    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    @Override
    public Exception decode(String methodKey, Response response) {
        String body = readBody(response);
        return new PaymentGatewayException(response.status(), extractErrorCode(body), body);
    }

    private String readBody(Response response) {
        if (response.body() == null) {
            return "";
        }
        try (java.io.InputStream bodyStream = response.body().asInputStream()) {
            return new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private String extractErrorCode(String body) {
        Matcher matcher = ERROR_CODE_PATTERN.matcher(body);
        if (!matcher.find()) {
            return "UNKNOWN_PAYMENT_ERROR";
        }
        return matcher.group(1);
    }
}
