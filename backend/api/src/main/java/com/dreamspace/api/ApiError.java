package com.dreamspace.api;

public record ApiError(String code, String message, Object details, String requestId) {}
