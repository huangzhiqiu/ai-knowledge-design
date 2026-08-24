# Type Mapping Template

Fill this out during Step 2b of db-researcher for the target DB.

## Type Mapping Table

| DB Native Type | Java Type | GraphQL Scalar | Notes / Edge cases |
|----------------|-----------|----------------|--------------------|
| INTEGER / INT  | Integer   | Int            |                    |
| BIGINT         | Long      | Int or String  | JS number overflow if > 2^53 |
| SMALLINT       | Integer   | Int            |                    |
| TINYINT        | Integer   | Int            | MySQL TINYINT(1) with tinyInt1isBit=false → Int not Boolean |
| DECIMAL/NUMERIC| BigDecimal| Float or String| Precision loss if using Float |
| FLOAT/REAL     | Float     | Float          |                    |
| DOUBLE         | Double    | Float          |                    |
| VARCHAR/TEXT   | String    | String         |                    |
| CHAR           | String    | String         |                    |
| BOOLEAN/BOOL   | Boolean   | Boolean        |                    |
| DATE           | LocalDate | String         |                    |
| TIMESTAMP      | LocalDateTime | String     |                    |
| DATETIME       | LocalDateTime | String     |                    |
| TIME           | LocalTime | String         |                    |
| JSON / JSONB   | JsonNode  | String (JSON)  | Return as serialized string or custom scalar |
| ARRAY          | List<?>   | [Type]         | PostgreSQL only natively |
| ENUM           | String    | String         | Expose as String in GraphQL, not GraphQL enum |
| UUID           | UUID/String | String       |                    |
| BINARY/BLOB    | byte[]    | String (base64)|                    |
| BIT            | Boolean/Integer | Boolean | BIT(1) → Boolean, BIT(n>1) → String |

## Unmappable / Problematic Types

List any DB types with no clean GraphQL equivalent:

| DB Type | Problem | Recommended handling |
|---------|---------|----------------------|
| ...     | ...     | ...                  |
