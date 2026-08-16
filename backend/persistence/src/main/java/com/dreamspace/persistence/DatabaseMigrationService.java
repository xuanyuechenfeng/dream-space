package com.dreamspace.persistence;

import javax.sql.DataSource;

/** Compatibility facade for callers in the persistence root package. */
public final class DatabaseMigrationService extends com.dreamspace.persistence.database.DatabaseMigrationService {
  public DatabaseMigrationService(DataSource dataSource) { super(dataSource); }
}
