---
name: db-researcher
description: Research a database engine's capabilities, driver APIs, schema introspection, and how it maps to Excalibase's dynamic REST/GraphQL generation. Use before adding support for any new database target.
argument-hint: Database engine to research (e.g. "PostgreSQL 16", "MongoDB 7", "MySQL 8")
---

# DB Researcher

Target database: $ARGUMENTS

## Step 1 — Orientation: What Excalibase needs from a DB

Before researching the target DB, ground yourself in what Excalibase actually requires.

First, locate the existing DB adapter modules:
```
# Find existing adapter modules
ls modules/
# Read the closest existing adapter (e.g. MySQL or Postgres) to understand the interface
```

Read the existing adapter implementation files to extract the exact interface contract:
- Schema reflector (how tables, columns, types, PKs, FKs are discovered)
- Schema generator (how GraphQL types are built from schema)
- Fetcher (how query resolvers execute SELECT)
- Mutator (how mutations execute INSERT/UPDATE/DELETE)
- SQL constants (what queries are run against the DB)

Also read `MEMORY.md` for any prior integration notes or gotchas.

Document the exact interface contract Excalibase expects:
- Schema introspection API (tables, columns, types, PKs, FKs, indexes, views)
- Query execution API (parameterized queries, result mapping, filter operators)
- Mutation API (INSERT, UPDATE, DELETE, bulk insert)
- Connection/pool model (JDBC driver class, pool config)
- Naming conventions (aggregate field, bulk create, connection field names)

## Step 2 — Research the target database

For the target DB, answer every question below. Every answer must cite a source (official docs URL or driver version).

### 2a. Schema Introspection
- What system tables/views expose schema metadata? (e.g. `information_schema`, `pg_catalog`, driver metadata APIs)
- Can you introspect: table names, column names, data types, nullability, PKs, FKs, unique constraints, indexes, views?
- Are there any introspection gaps vs PostgreSQL (Excalibase's reference DB)?

### 2b. Data Type Mapping

Read `type-mapping-template.md` in this skill directory and fill it out for the target DB.

Key types to map: integers, decimals, strings, booleans, dates, timestamps, JSON, arrays, enums, binary, UUIDs.

Flag any types with no clean GraphQL scalar equivalent (spatial, custom composites, etc.).

### 2c. Query Capabilities
- Supported filter operators: =, !=, >, <, >=, <=, LIKE, IN, NOT IN, IS NULL, IS NOT NULL, BETWEEN, contains, startsWith, endsWith
- Pagination: LIMIT/OFFSET supported? Cursor-based?
- Sorting: multi-column? NULLs FIRST/LAST?
- Aggregations: COUNT, SUM, AVG, MIN, MAX, GROUP BY?
- Any non-standard SQL syntax to watch for?

### 2d. Driver and Connection
- Recommended Java driver (name, Maven coordinates, latest stable version)
- JDBC support? Any known gotchas with the driver (e.g. `tinyInt1isBit`, type coercions)?
- Connection pool compatibility (HikariCP)?
- Multi-schema support (how to scope to a specific schema)?

### 2e. Mutation Support
- INSERT ... RETURNING supported? Or need separate SELECT after INSERT?
- UPSERT syntax?
- Batch insert support?
- AUTO_INCREMENT / SERIAL / SEQUENCE behavior — how to fetch generated keys?

### 2f. Known Gotchas
List any driver-level or DB-level behaviors that differ from PostgreSQL and could cause silent bugs.

## Step 3 — Gap Analysis vs Excalibase PostgreSQL baseline

Read `gap-analysis-template.md` in this skill directory and fill it out.

For every gap: state whether it is a BLOCKER, WORKAROUND AVAILABLE, or NATIVE SUPPORT.

## Step 4 — Integration recommendation

Based on research, output one of:

- **TIER 1 — Full support**: All Excalibase features map cleanly. Recommend implementing.
- **TIER 2 — Partial support**: Core REST/GraphQL works, some features degraded (document which). Recommend with caveats.
- **TIER 3 — Not viable**: Fundamental gaps (no schema introspection, no JDBC). Do not recommend without major architectural changes.

State the recommended implementation approach:
- New module name and package
- Driver + connection pool config
- Schema introspection queries needed
- Estimated effort (S/M/L)

## Step 5 — Write research doc

Write findings to: `docs/db-research/$ARGUMENTS.md`

Output: **DB RESEARCH COMPLETE — READY FOR PLAN**
