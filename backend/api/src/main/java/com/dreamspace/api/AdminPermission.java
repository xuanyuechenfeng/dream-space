package com.dreamspace.api;

import com.dreamspace.persistence.database.DatabaseEnums.AdminRole;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminPermission {
  AdminRole minimum() default AdminRole.VIEWER;
}
