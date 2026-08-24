---
name: integration-testing
description: API and server integration testing with Jest. Use when writing tests that call a live HTTP/GraphQL/REST server (not browser UI). For Playwright browser tests use /ui-testing.
argument-hint: [service or endpoint to test]
---

# Integration Testing

Test a running API server end-to-end using Jest and HTTP clients. No browser — this is about testing your server's behavior against real requests.

## When to Use

- Testing a GraphQL server with real queries
- Testing a REST API end-to-end
- Testing against a live DB via docker-compose
- Any test that requires a running service (not mocked)

For browser/UI tests use `/ui-testing` (Playwright).

## Project Setup

```bash
# GraphQL
npm install --save-dev jest graphql-request graphql

# REST
npm install --save-dev jest supertest
# or axios
npm install --save-dev jest axios
```

```json
// package.json
{
  "scripts": {
    "test": "jest --forceExit",
    "test:postgres": "jest postgres.test.js --forceExit",
    "test:mysql": "jest mysql.test.js --forceExit"
  },
  "jest": {
    "testEnvironment": "node",
    "testTimeout": 60000,
    "verbose": true
  }
}
```

## Wait for API Helper

Always wait for the server to be ready before tests run. Never hardcode `setTimeout`.

```js
// e2e/client.js
const { GraphQLClient } = require('graphql-request');

async function waitForApi(url, { maxRetries = 15, delayMs = 5000 } = {}) {
  for (let i = 1; i <= maxRetries; i++) {
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query: '{ __typename }' }),
        signal: AbortSignal.timeout(5000),
      });
      if (res.status < 500) return; // up — even 400 means the server parsed the request
    } catch (_) {}
    if (i < maxRetries) {
      console.log(`[INFO] API not ready, attempt ${i}/${maxRetries} — retrying in ${delayMs / 1000}s...`);
      await new Promise(r => setTimeout(r, delayMs));
    }
  }
  throw new Error(`API at ${url} not ready after ${maxRetries} attempts`);
}

function createClient(url, headers = {}) {
  return new GraphQLClient(url, { headers });
}

module.exports = { waitForApi, createClient };
```

For REST:
```js
async function waitForApi(url, { maxRetries = 15, delayMs = 5000 } = {}) {
  for (let i = 1; i <= maxRetries; i++) {
    try {
      const res = await fetch(url + '/health', { signal: AbortSignal.timeout(5000) });
      if (res.status < 500) return;
    } catch (_) {}
    if (i < maxRetries) await new Promise(r => setTimeout(r, delayMs));
  }
  throw new Error(`API at ${url} not ready after ${maxRetries} attempts`);
}
```

## GraphQL Test Structure

```js
const { gql } = require('graphql-request');
const { waitForApi, createClient } = require('./client');

const API_URL = process.env.API_URL || 'http://localhost:10000/graphql';
let client;

beforeAll(async () => {
  await waitForApi(API_URL);
  client = createClient(API_URL);
});

describe('Schema introspection', () => {
  test('queryType is Query', async () => {
    const data = await client.request(gql`{ __schema { queryType { name } } }`);
    expect(data.__schema.queryType.name).toBe('Query');
  });
});

describe('Basic queries', () => {
  test('get all items with limit', async () => {
    const data = await client.request(gql`
      { items(limit: 5) { id name } }
    `);
    expect(data.items.length).toBeLessThanOrEqual(5);
    data.items.forEach(item => {
      expect(item.id).toBeDefined();
      expect(item.name).toBeDefined();
    });
  });

  test('filter by field', async () => {
    const data = await client.request(gql`
      { items(where: { status: { eq: "active" } }) { id status } }
    `);
    data.items.forEach(item => expect(item.status).toBe('active'));
  });
});
```

## REST Test Structure

```js
const axios = require('axios');
const { waitForApi } = require('./client');

const API_URL = process.env.API_URL || 'http://localhost:3000';

beforeAll(async () => {
  await waitForApi(API_URL);
});

describe('GET /items', () => {
  test('returns list', async () => {
    const { data, status } = await axios.get(`${API_URL}/items`);
    expect(status).toBe(200);
    expect(Array.isArray(data)).toBe(true);
  });

  test('filter by query param', async () => {
    const { data } = await axios.get(`${API_URL}/items?status=active`);
    data.forEach(item => expect(item.status).toBe('active'));
  });
});

describe('POST /items', () => {
  test('creates and returns item', async () => {
    const { data, status } = await axios.post(`${API_URL}/items`, { name: 'test' });
    expect(status).toBe(201);
    expect(data.id).toBeDefined();
    expect(data.name).toBe('test');
  });
});
```

## Docker Compose Setup

Start services before running tests:

```yaml
# docker-compose.test.yml
services:
  db:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: testdb
      POSTGRES_USER: test
      POSTGRES_PASSWORD: test
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U test"]
      interval: 5s
      retries: 10

  app:
    build: .
    ports:
      - "10000:10000"
    depends_on:
      db:
        condition: service_healthy
```

```bash
# Run tests against docker-compose
docker-compose -f docker-compose.test.yml up -d
npm test
docker-compose -f docker-compose.test.yml down
```

## Assertion Patterns

**Before writing any assertion — read the schema/API contract first (use `/db-researcher` or introspect).**

```js
// Count assertions — be careful, earlier tests may insert rows
expect(data.items.length).toBeGreaterThanOrEqual(1); // safe
expect(data.items.length).toBe(3); // fragile — only if you control all test data

// Nullable fields
expect(data.item.description).toBeDefined(); // could be null — this passes for null
expect(data.item.description).not.toBeNull(); // only if column is NOT NULL

// Type assertions
expect(typeof data.item.id).toBe('number');
expect(data.item.createdAt).toMatch(/^\d{4}-\d{2}-\d{2}/); // ISO date format

// forEach — only assert what is true for ALL rows
data.items.forEach(item => {
  expect(item.id).toBeDefined();    // safe — id is always present
  // Don't assert on optional/nullable fields in forEach
});
```

## CI/CD Integration

```yaml
# .github/workflows/integration.yml
name: Integration Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 20 }
      - run: docker-compose up -d
      - run: npm ci
      - run: npm test
        env:
          API_URL: http://localhost:10000/graphql
      - run: docker-compose down
```

## Gotchas

- Always `--forceExit` in Jest — open DB connections will hang the process
- `testTimeout: 60000` — services can be slow to start even after `waitForApi`
- Use `process.env.API_URL` so tests work locally and in CI with different ports
- `status < 500` in waitForApi — a 400 means the server is up and parsed the request
- Run per-service test files separately when testing multiple DB backends
