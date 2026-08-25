package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record AdminRolePermissionRecord(String roleId, String permissionId, String grantedBy,
    Instant grantedAt) {}
