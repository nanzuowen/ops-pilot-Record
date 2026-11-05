package com.example.opspilot.exception;

public enum LlmFailureCategory {

    INVALID_REQUEST,

    AUTHENTICATION,

    PERMISSION_DENIED,

    RATE_LIMITED,

    UPSTREAM_UNAVAILABLE,

    TIMEOUT,

    INVALID_RESPONSE,

    UNKNOWN
}
