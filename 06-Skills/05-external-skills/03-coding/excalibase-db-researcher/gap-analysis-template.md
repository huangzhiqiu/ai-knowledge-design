# Gap Analysis Template

Fill this out during Step 3 of db-researcher.

## Gap Analysis vs Excalibase PostgreSQL Baseline

| Excalibase Feature | PostgreSQL (baseline) | Target DB support | Status | Notes |
|--------------------|-----------------------|-------------------|--------|-------|
| Schema introspection — tables | information_schema.tables | ? | ? | |
| Schema introspection — columns + types | information_schema.columns | ? | ? | |
| Schema introspection — PKs | information_schema.key_column_usage | ? | ? | |
| Schema introspection — FKs | information_schema.referential_constraints | ? | ? | |
| Schema introspection — views | information_schema.views | ? | ? | |
| Schema introspection — enums | pg_type + pg_enum | ? | ? | |
| Schema introspection — composite types | pg_type + pg_class relkind='c' | ? | ? | |
| Schema introspection — domains | pg_type typtype='d' | ? | ? | |
| Filter operators (14: eq,neq,gt,gte,lt,lte,contains,startsWith,endsWith,like,isNull,isNotNull,in,notIn) | SQL WHERE | ? | ? | |
| Pagination (LIMIT/OFFSET) | LIMIT/OFFSET | ? | ? | |
| Cursor pagination (Connection) | LIMIT/OFFSET + totalCount | ? | ? | |
| Ordering (multi-column) | ORDER BY | ? | ? | |
| Aggregates (count,sum,avg,min,max) | COUNT/SUM/AVG/MIN/MAX | ? | ? | |
| Mutations — create | INSERT RETURNING | ? | ? | |
| Mutations — update | UPDATE RETURNING | ? | ? | |
| Mutations — delete | DELETE RETURNING | ? | ? | |
| Mutations — bulk create | INSERT multiple rows | ? | ? | |
| FK relationships — forward | JOIN on FK column | ? | ? | |
| FK relationships — reverse | JOIN from referenced table | ? | ? | |
| Views — read-only (no mutations) | pg_views | ? | ? | |
| CDC / real-time | WAL logical replication | ? | ? | |
| Computed fields | SQL functions/expressions | ? | ? | |
| Multi-tenant schema isolation | search_path | ? | ? | |

## Status Legend
- **NATIVE** — supported natively, no workaround needed
- **WORKAROUND** — supported with a workaround (describe it)
- **PARTIAL** — partially supported (describe the limitation)
- **BLOCKER** — not supported, blocks core Excalibase functionality
- **N/A** — not applicable for this DB
