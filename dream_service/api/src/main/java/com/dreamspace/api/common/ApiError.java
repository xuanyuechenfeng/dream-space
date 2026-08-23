package com.dreamspace.api.common;

public record ApiError(String code, String message, Object details, String requestId) {}
