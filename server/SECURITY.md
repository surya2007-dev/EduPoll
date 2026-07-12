# EduPoll Security Architecture

This document describes the security controls, boundary verifications, and architectural safeguards implemented in the EduPoll web monolith application.

## Security Model

EduPoll is designed with defensive boundaries at each layer. It operates under a strict principle of least privilege, mapping user credentials and classroom associations dynamically. 

```
               [Browser Clients]
                      │
            (TLS / HTTPS Session)
                      ▼
        [CSRF Origin Verification Gate]
                      ▼
        [Session Cookie Validation Gate]
                      ▼
         [Type & Length Sanitizer]
                      ▼
        [Role & Ownership Checker]
                      ▼
          [Database ORM (Mongoose)]
```

## Authentication Controls

- **Salted Password Hashing:** User passwords are encrypted before database insertion using `bcryptjs` with a cost factor of 10. Passwords are never stored in plain text.
- ** Timing Oracle Parity:** During user authentication, if a target `secId` is not found, a dummy password comparison is executed using a static hash. This ensures that response times remain uniform, preventing attackers from checking database usernames using timing differentials.
- **Normalized Response Output:** All failed authentication attempts return the identical response message `"Invalid credentials."` with an HTTP `401 Unauthorized` status.

## Session Controls

- **Safe Session Life Cycle:** Active sessions are stored securely in a MongoDB `sessions` collection using `connect-mongo`.
- **Session Fixation Prevention:** The session identifier is regenerated (`req.session.regenerate()`) upon every successful authentication.
- **Stale Cookie Revocation:** Session checking includes verification of user account existence in the database. Deleting a user immediately invalidates their active session cookie.

## Authorization Controls

- **Role Boundaries:** Middleware checks the authenticated user's `role` property (`admin`, `faculty`, or `student`) before delegating access to restricted routes.
- **Mentorship Data Partitioning:** Faculty members are restricted to performing operations (viewing, messaging, deleting) only on students who are assigned as their direct mentees.

## CSRF Protection

- **Origin Check Middleware:** All state-changing methods (`POST`, `PUT`, `PATCH`, `DELETE`) require a request origin header (`Origin` or `Referer`) that matches the configured `APP_ORIGIN` parameter.
- **Reverse Proxy Header Support:** The middleware utilizes Express's `trust proxy` configuration in production mode to retrieve the expected origin schema dynamically.

## Input Validation

- **Boundary Schema Enforcements:** All route entry points validate parameters using schema assertions in `middleware/validation.js`.
- **Type Parity Gates:** Explicitly verifies that incoming inputs are primitive string types, immediately rejecting object or array queries to block NoSQL operator injection payloads.
- **Data Range Bounds:** Restricts length on credentials (e.g. password <= 256, secId <= 64) and message text payloads at the gate.

## Database Integrity

- **Database Constraint Verification:** Unique constraints are compiled directly onto database indexes for `secId` and `email` collections.
- **Schema Sanitization:** Prevents mass assignment or privilege mutation by discarding unrecognized input fields.

## Concurrency Protection

- **Atomic Vote Isolation:** Prevent double-voting by executing poll responses using `Poll.findOneAndUpdate` with filter validation rules:
  ```javascript
  {
    _id: pollId,
    status: 'live',
    targetStudents: studentId,
    'responses.student': { $ne: studentId }
  }
  ```
  This query guarantees that if multiple concurrent vote requests are received from the same student session, only one will match the query and commit the response, while the others will fail atomically.

## Browser Security Headers

The application configures HTTP security headers on all responses:
- `Content-Security-Policy`: Restricts base URIs, frame ancestors, and object sources.
- `X-Content-Type-Options`: Set to `nosniff` to prevent browser MIME-sniffing.
- `X-Frame-Options`: Set to `DENY` to protect against clickjacking attacks.
- `Referrer-Policy`: Set to `no-referrer` to prevent referrer leaks.
- `Permissions-Policy`: Restricts access to device capabilities (geolocation, camera, microphone).

## Audit Logging

Structured log entries are sent to standard output (`stdout`) in JSON format.
- **Coverage:** Logs are generated for login states, registrations, profile adjustments, deletions, and access violations.
- **Sanitization:** Log outputs contain timestamps, request IPs, actor IDs, and event descriptions. Secrets, credentials, cookies, and password hashes are excluded from log outputs.

## Production Fail-Closed Behavior

The backend enforces strict parameter validation on startup:
- Refuses to start if `SESSION_SECRET` is missing, short, or invalid in production mode.
- Refuses to start if `MONGO_URI` is missing or unreachable in production mode.
- Volatile memory fallback is disabled in production mode.

## Security Validation Scope

Validation covered these domains:
1. **Authentication:** timings, timing oracle protection, timing parity.
2. **Authorization:** role restriction boundaries and mentee ownership.
3. **Session Management:** invalidation, regeneration, stale session blockages.
4. **Input Handling:** NoSQL injection, mass assignment, range overflows.
5. **Concurrency:** atomic database operations and unique constraint index conflicts.

## Residual Technical Debt

- **CSP Inline Script Rules:** The Content-Security-Policy uses `'unsafe-inline'` to support external Tailwind CDN and Google Fonts.
- **SameSite Lax Cookie Restrictions:** Cookies use `SameSite=Lax`.

## Reporting Security Issues

To report a security vulnerability, please contact the repository administrator directly. Do not open public issues.
