package com.dreamspace.common;

import java.time.Instant;

public record ServiceHealth(String service, String status, Instant timestamp) {
}
