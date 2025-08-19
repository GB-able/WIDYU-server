package com.widyu.global.log;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ExceptionLogEntry {

    private String timestamp;
    private String exceptionType;
    private String message;
    private String requestUri;
    private String stackTrace;

    public String toLogString() {
        return String.format("""
                
                [EXCEPTION]
                  Time          : %s
                  ExceptionType : %s
                  Message       : %s
                  Request URI   : %s
                ----------------- Stack Trace -----------------
                %s
                ------------------------------------------------
                """,
                timestamp,
                exceptionType,
                message,
                requestUri,
                stackTrace
        );
    }
}
