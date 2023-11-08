# 2.0.0

- [ BREAKING ] `datascript.storage.sql.core/make` now accepts `javax.sql.DataSource` instead of `java.sql.Connection`
- [ BREAKING ] Removed `datascript.storage.sql.core/close`
- Added simple connection pool `datascript.storage.sql.core/pool`

# 1.0.0

- Support `:h2`, `:mysql`, `:postgresql` dbtypes

# 0.1.0

- Initial `:sqlite` support
