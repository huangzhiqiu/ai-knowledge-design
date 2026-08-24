---
name: mongodb-patterns
description: MongoDB schema design, query patterns, aggregation pipeline, indexing, and driver gotchas. Use when working with MongoDB collections, queries, or configuring a MongoDB connection.
---

# MongoDB Patterns

Quick reference for MongoDB best practices. For a new integration, run `/db-researcher` first.

## When to Activate

- Designing MongoDB document schemas
- Writing queries, filters, or aggregation pipelines
- Creating indexes for performance
- Configuring MongoDB drivers (Node.js, Java, Python)
- Debugging slow queries or unexpected behavior

## Document Design

**Embed vs Reference:**

| Embed when | Reference when |
|------------|----------------|
| Data is always read together | Data is large or unbounded |
| 1:1 or 1:few relationship | Many-to-many relationship |
| Child data changes rarely | Child data changes frequently |
| Array is bounded (< few hundred) | Array grows without bound |

```js
// Embed — order always needs its items
{
  _id: ObjectId("..."),
  userId: ObjectId("..."),
  status: "active",
  total: 99.99,
  items: [                        // embed — bounded, read together
    { productId: ObjectId("..."), name: "Widget", qty: 2, price: 49.99 }
  ],
  createdAt: ISODate("2026-01-01")
}

// Reference — user profile is large and read separately
{
  _id: ObjectId("..."),
  userId: ObjectId("..."),        // reference — don't duplicate user data
  status: "active"
}
```

## Indexes

```js
// Single field
db.orders.createIndex({ userId: 1 })

// Compound — equality first, sort second, range last
db.orders.createIndex({ userId: 1, status: 1, createdAt: -1 })

// Text search
db.products.createIndex({ name: "text", description: "text" })

// TTL — auto-delete after N seconds
db.sessions.createIndex({ createdAt: 1 }, { expireAfterSeconds: 3600 })

// Unique
db.users.createIndex({ email: 1 }, { unique: true })

// Sparse — only index documents where field exists
db.profiles.createIndex({ phoneNumber: 1 }, { sparse: true })

// Check index usage
db.orders.explain("executionStats").find({ userId: ObjectId("...") })
```

## Common Query Patterns

```js
// Filter
db.orders.find({ userId: ObjectId("..."), status: "active" })

// Comparison operators
db.orders.find({ total: { $gt: 50, $lte: 200 } })

// Array contains
db.products.find({ tags: "electronics" })           // element match
db.products.find({ tags: { $in: ["a", "b"] } })     // any of

// Nested field
db.orders.find({ "address.city": "Hanoi" })

// Regex
db.users.find({ name: { $regex: /^john/i } })

// Projection — only return needed fields
db.users.find({ status: "active" }, { name: 1, email: 1, _id: 0 })

// Sort, skip, limit
db.orders.find().sort({ createdAt: -1 }).skip(20).limit(10)

// Count
db.orders.countDocuments({ status: "active" })

// Distinct values
db.orders.distinct("status")
```

## CRUD

```js
// Insert
db.orders.insertOne({ userId, total, status: "pending", createdAt: new Date() })
db.orders.insertMany([{ ... }, { ... }])

// Update
db.orders.updateOne(
  { _id: ObjectId("...") },
  {
    $set: { status: "active", updatedAt: new Date() },
    $inc: { attempts: 1 },
    $push: { history: { event: "activated", at: new Date() } }
  }
)

// Upsert
db.users.updateOne(
  { email: "user@example.com" },
  { $set: { name: "John", updatedAt: new Date() }, $setOnInsert: { createdAt: new Date() } },
  { upsert: true }
)

// Delete
db.orders.deleteOne({ _id: ObjectId("...") })
db.orders.deleteMany({ status: "cancelled", createdAt: { $lt: cutoffDate } })

// Find and modify atomically
db.orders.findOneAndUpdate(
  { status: "pending" },
  { $set: { status: "processing" } },
  { returnDocument: "after", sort: { createdAt: 1 } }
)
```

## Aggregation Pipeline

```js
db.orders.aggregate([
  // Stage 1: filter
  { $match: { status: "completed", createdAt: { $gte: startDate } } },

  // Stage 2: join (lookup)
  { $lookup: {
    from: "users",
    localField: "userId",
    foreignField: "_id",
    as: "user"
  }},
  { $unwind: "$user" },

  // Stage 3: group
  { $group: {
    _id: "$user.country",
    totalRevenue: { $sum: "$total" },
    orderCount: { $count: {} },
    avgOrder: { $avg: "$total" }
  }},

  // Stage 4: sort and limit
  { $sort: { totalRevenue: -1 } },
  { $limit: 10 },

  // Stage 5: reshape output
  { $project: {
    country: "$_id",
    totalRevenue: { $round: ["$totalRevenue", 2] },
    orderCount: 1,
    avgOrder: { $round: ["$avgOrder", 2] },
    _id: 0
  }}
])
```

## Transactions (Replica Set Required)

```js
const session = client.startSession();
try {
  session.startTransaction();
  await accounts.updateOne({ _id: from }, { $inc: { balance: -amount } }, { session });
  await accounts.updateOne({ _id: to }, { $inc: { balance: amount } }, { session });
  await session.commitTransaction();
} catch (err) {
  await session.abortTransaction();
  throw err;
} finally {
  session.endSession();
}
```

## Connection (Node.js)

```js
const { MongoClient, ObjectId } = require('mongodb');

const client = new MongoClient(process.env.MONGODB_URI, {
  maxPoolSize: 20,
  minPoolSize: 2,
  serverSelectionTimeoutMS: 5000,
  connectTimeoutMS: 10000,
});

// Connect once at startup, reuse client
await client.connect();
const db = client.db('mydb');
```

## Connection (Spring Boot / Java)

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/mydb
      # or with auth:
      uri: mongodb://user:pass@localhost:27017/mydb?authSource=admin
```

## Known Gotchas

| Gotcha | Fix |
|--------|-----|
| `_id` is `ObjectId`, not string | Use `ObjectId("...")` in queries, not raw string |
| Array `$push` grows unbounded | Cap with `$push: { $each: [...], $slice: -100 }` |
| `find()` returns cursor, not array | Await `.toArray()` or use `for await` |
| Transactions require replica set | Use `--replSet rs0` even in local dev |
| `updateMany` without filter deletes all | Always double-check filter before `updateMany`/`deleteMany` |
| Schema-less ≠ no validation | Use `$jsonSchema` validator or Mongoose schema |
| `countDocuments` is slow on large collections | Use estimated count for UI, exact for logic |
| Regex without index does full scan | Add text index or use `$regex` with `^` prefix anchor |
