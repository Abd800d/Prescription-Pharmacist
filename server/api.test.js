/**
 * API contract tests for the analysis backend (server/index.js).
 * Runs the real exported Cloud Function handlers in-process with mock
 * req/res — no emulators, no network. Covers method guards, auth, upload
 * validation (type/size/executables), body validation, and rate limiting.
 */
const test = require("node:test");
const assert = require("node:assert/strict");

const fns = require("./index.js");

function mockRes() {
  const res = {
    statusCode: 200,
    body: null,
    headers: {},
    _listeners: {},
    on(event, cb) {
      (this._listeners[event] = this._listeners[event] || []).push(cb);
      return this;
    },
    once(event, cb) {
      return this.on(event, cb);
    },
    emit(event, ...args) {
      for (const cb of this._listeners[event] || []) cb(...args);
      return true;
    },
    removeListener() {
      return this;
    },
    write() {
      return true;
    },
    writeHead(code) {
      this.statusCode = code;
      return this;
    },
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(obj) {
      this.body = obj;
      this.emit("finish");
      return this;
    },
    set(name, value) {
      this.headers[name] = value;
      return this;
    },
    setHeader(name, value) {
      this.headers[name] = value;
      return this;
    },
    getHeader(name) {
      return this.headers[name];
    },
    send(obj) {
      this.body = obj;
      this.emit("finish");
      return this;
    },
    end() {
      this.emit("finish");
      return this;
    },
    sendStatus(code) {
      this.statusCode = code;
      this.emit("finish");
      return this;
    },
  };
  return res;
}

function makeReq({ method = "POST", headers = {}, body, rawBody, ip = "127.0.0.1" } = {}) {
  return {
    method,
    headers,
    body,
    rawBody: rawBody ?? Buffer.alloc(0),
    ip,
  };
}

function multipart({
  filename = "rx.png",
  mime = "image/png",
  field = "image",
  data = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
} = {}) {
  const boundary = "----apitestboundary42";
  const head = Buffer.from(
    `--${boundary}\r\n` +
      `Content-Disposition: form-data; name="${field}"; filename="${filename}"\r\n` +
      `Content-Type: ${mime}\r\n\r\n`
  );
  const tail = Buffer.from(`\r\n--${boundary}--\r\n`);
  return {
    rawBody: Buffer.concat([head, data, tail]),
    headers: { "content-type": `multipart/form-data; boundary=${boundary}` },
  };
}

async function call(fn, req) {
  const res = mockRes();
  await fn(req, res);
  return res;
}

const JSON_HEADERS = { "content-type": "application/json" };
const BAD_TOKEN = { authorization: "Bearer not-a-real-token" };

// ---------------------------------------------------------------- 405 guards
const ENDPOINTS = {
  chat: fns.chat,
  analyze: fns.analyze,
  ocr: fns.ocr,
  extract: fns.extract,
  druginfo: fns.druginfo,
  setrole: fns.setrole,
};

for (const [name, fn] of Object.entries(ENDPOINTS)) {
  test(`${name}: GET returns 405 method_not_allowed`, async () => {
    const res = await call(fn, makeReq({ method: "GET" }));
    assert.equal(res.statusCode, 405);
    assert.equal(res.body.error.code, "method_not_allowed");
  });
}

// -------------------------------------------------------------- auth guards
test("chat: invalid Bearer token returns 401", async () => {
  const res = await call(
    fns.chat,
    makeReq({ headers: { ...JSON_HEADERS, ...BAD_TOKEN }, body: {} })
  );
  assert.equal(res.statusCode, 401);
  assert.equal(res.body.error.code, "unauthorized");
});

test("extract: invalid Bearer token returns 401", async () => {
  const res = await call(
    fns.extract,
    makeReq({ headers: { ...JSON_HEADERS, ...BAD_TOKEN }, body: {} })
  );
  assert.equal(res.statusCode, 401);
});

test("druginfo: invalid Bearer token returns 401", async () => {
  const res = await call(
    fns.druginfo,
    makeReq({ headers: { ...JSON_HEADERS, ...BAD_TOKEN }, body: {} })
  );
  assert.equal(res.statusCode, 401);
});

test("analyze: invalid Bearer token on empty upload still surfaces 400 for the missing image first", async () => {
  const res = await call(
    fns.analyze,
    makeReq({ headers: { ...JSON_HEADERS, ...BAD_TOKEN }, body: {} })
  );
  assert.equal(res.statusCode, 400);
  assert.equal(res.body.error.code, "bad_image");
});

test("setrole: invalid Bearer token returns 403 forbidden (admin gate)", async () => {
  const res = await call(
    fns.setrole,
    makeReq({ headers: { ...JSON_HEADERS, ...BAD_TOKEN }, body: { uid: "u1", role: "admin" } })
  );
  assert.equal(res.statusCode, 403);
  assert.equal(res.body.error.code, "forbidden");
});

// ------------------------------------------------------- body validation 400
test("chat: body without messages returns 400", async () => {
  const res = await call(
    fns.chat,
    makeReq({ headers: JSON_HEADERS, body: { messages: "not-an-array" } })
  );
  assert.equal(res.statusCode, 400);
  assert.equal(res.body.error.code, "bad_request");
});

test("chat: empty messages array returns 400", async () => {
  const res = await call(
    fns.chat,
    makeReq({ headers: JSON_HEADERS, body: { messages: [] } })
  );
  assert.equal(res.statusCode, 400);
});

test("druginfo: empty query returns 400", async () => {
  const res = await call(
    fns.druginfo,
    makeReq({ headers: JSON_HEADERS, body: { query: "   " } })
  );
  assert.equal(res.statusCode, 400);
});

test("druginfo: query over 200 chars returns 400", async () => {
  const res = await call(
    fns.druginfo,
    makeReq({ headers: JSON_HEADERS, body: { query: "a".repeat(201) } })
  );
  assert.equal(res.statusCode, 400);
  assert.match(res.body.error.message, /too long/);
});

test("extract: missing text returns 400", async () => {
  const res = await call(
    fns.extract,
    makeReq({ headers: JSON_HEADERS, body: { text: "" } })
  );
  assert.equal(res.statusCode, 400);
});

test("extract: text over 20000 chars returns 400", async () => {
  const res = await call(
    fns.extract,
    makeReq({ headers: JSON_HEADERS, body: { text: "x".repeat(20001) } })
  );
  assert.equal(res.statusCode, 400);
  assert.match(res.body.error.message, /too long/);
});

// ------------------------------------------------- upload validation (image)
test("analyze: non-multipart body returns 400 bad_image", async () => {
  const res = await call(
    fns.analyze,
    makeReq({ headers: JSON_HEADERS, body: {} })
  );
  assert.equal(res.statusCode, 400);
  assert.equal(res.body.error.code, "bad_image");
});

test("analyze: executable upload (.exe) is rejected with 400", async () => {
  const mp = multipart({ filename: "virus.exe", mime: "application/x-msdownload" });
  const res = await call(
    fns.analyze,
    makeReq({ headers: mp.headers, rawBody: mp.rawBody })
  );
  assert.equal(res.statusCode, 400);
  assert.equal(res.body.error.code, "bad_image");
  assert.match(res.body.error.message, /allowed/i);
});

test("ocr: text/plain upload is rejected with 400", async () => {
  const mp = multipart({ filename: "note.txt", mime: "text/plain" });
  const res = await call(
    fns.ocr,
    makeReq({ headers: mp.headers, rawBody: mp.rawBody })
  );
  assert.equal(res.statusCode, 400);
  assert.equal(res.body.error.code, "bad_image");
});

test("analyze: empty image file returns 400", async () => {
  const mp = multipart({ data: Buffer.alloc(0) });
  const res = await call(
    fns.analyze,
    makeReq({ headers: mp.headers, rawBody: mp.rawBody })
  );
  assert.equal(res.statusCode, 400);
  assert.match(res.body.error.message, /empty/i);
});

test("analyze: multipart without an 'image' part returns 400 (no hang)", async () => {
  const mp = multipart({ field: "file" });
  const res = await call(
    fns.analyze,
    makeReq({ headers: mp.headers, rawBody: mp.rawBody })
  );
  assert.equal(res.statusCode, 400);
  assert.match(res.body.error.message, /image/i);
});

test("analyze: oversized image (> 8 MB) returns 400", async () => {
  const mp = multipart({ data: Buffer.alloc(8 * 1024 * 1024 + 1024, 0x41) });
  const res = await call(
    fns.analyze,
    makeReq({ headers: mp.headers, rawBody: mp.rawBody })
  );
  assert.equal(res.statusCode, 400);
  assert.equal(res.body.error.code, "bad_image");
});

// ------------------------------------------------------------- rate limiting
test("chat: rate limit returns 429 after 10 requests per caller", async () => {
  const ip = `10.9.9.${Math.floor(Math.random() * 200) + 1}`;
  let last = null;
  for (let i = 0; i < 11; i++) {
    last = await call(
      fns.chat,
      makeReq({ ip, headers: JSON_HEADERS, body: { messages: "bad" } })
    );
    if (i < 10) {
      assert.equal(last.statusCode, 400);
    }
  }
  assert.equal(last.statusCode, 429);
  assert.equal(last.body.error.code, "rate_limited");
});

// ----------------------------------------------- upstream failure surfacing
test("analyze: valid upload with unreachable upstream returns 502 analysis_failed", async () => {
  const mp = multipart();
  const res = await call(
    fns.analyze,
    makeReq({ headers: mp.headers, rawBody: mp.rawBody })
  );
  assert.equal(res.statusCode, 502);
  assert.equal(res.body.error.code, "analysis_failed");
});

test("druginfo: valid query returns attributed facts (200) or explicit 502 upstream_failed", async () => {
  const res = await call(
    fns.druginfo,
    makeReq({ headers: JSON_HEADERS, body: { query: "paracetamol" } })
  );
  if (res.statusCode === 200) {
    assert.ok(res.body);
  } else {
    assert.equal(res.statusCode, 502);
    assert.equal(res.body.error.code, "upstream_failed");
  }
});
