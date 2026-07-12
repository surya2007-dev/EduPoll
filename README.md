# EduPoll

EduPoll is a server-rendered classroom engagement and poll management platform designed to facilitate real-time interactions, feedback loops, and structured gradebook reporting for educational institutions.

## Overview

EduPoll is built as an Express.js server-rendered monolith using EJS templates for the views, combined with client-side AJAX/fetch API calls. It supports role-based dashboards (Admin, Faculty, and Student) to manage classrooms, register students, launch polls, submit responses, track scores, and coordinate messaging.

## Problem Statement

Traditional lecture environments struggle with low real-time student participation, lack of immediate comprehension feedback loops, and high administrative overhead in recording engagement scores. EduPoll addresses these challenges by offering a low-latency, secure in-lecture polling and direct messaging portal that synchronizes student performance directly to class reports.

## Features

- **Role-Based Authentication:** Authenticates admin, faculty, and student roles, normalizing timings to protect against database analysis attacks.
- **Admin Dashboard:** High-level overview displays total user, classroom, and faculty counts.
- **Faculty Management:** System administrator can dynamically register, inventory, and remove faculty accounts.
- **Student Management:** Faculty can view enrolled student rosters and register new students.
- **Classroom Management:** Faculty can organize subjects into classrooms and manage enrollment.
- **Mentee Management:** Faculty can link students as mentees.
- **Poll Creation:** Faculty can design active polls, defining duration limits and targets.
- **Student Targeting:** Enforces that only targeted classroom students can view or access specific polls.
- **Poll Response Submission:** Students vote on live polls through a dedicated interface.
- **Activity Scoring:** Automatically assigns performance grades to responses.
- **Duplicate Response Prevention:** Prevents double-voting via concurrency-locked queries.
- **Messaging:** Role-restricted messaging channels for direct communication or group announcements.
- **Profile Management:** Users can update profile names, emails, and passwords securely.
- **Session Management:** Enforces cookie signing, expiration limits, and user existence checks.
- **Security Audit Logging:** Logs key account activity events dynamically.

## Architecture

EduPoll is an **Express Server-Rendered Monolith** utilizing:
- **EJS SSR:** Dynamic HTML generation on the server.
- **AJAX Fetch APIs:** Browser-side Javascript communicates asynchronously with `/api` endpoints.
- **MongoDB / Mongoose:** Production data persistence layer.
- **Memory Fallback:** Volatile in-memory mock schema used only for local development if MongoDB is offline.

```
+─────────────────────────────────────────────────────────────+
│                         Web Browser                         │
+──────────────────────────────┬──────────────────────────────+
                               │ (Cookie, Hostile/Same Origin)
                               ▼
+─────────────────────────────────────────────────────────────+
│                       Express Backend                       │
│  - Middleware: CORS, CSP Headers, Session, Validation, CSRF  │
+──────────────────────────────┬──────────────────────────────+
                               │ (Mongoose Schema Commands)
                               ▼
+─────────────────────────────────────────────────────────────+
│                      MongoDB Database                       │
│  - Collections: Users, Polls, Classrooms, Messages, Sessions│
+─────────────────────────────────────────────────────────────+
```

## Technology Stack

- **Runtime Environment:** Node.js (v24.18)
- **Web Application Engine:** Express.js (v4.19.2)
- **View Renderer:** EJS Templates (v3.1.10)
- **Object Modeling / Database:** Mongoose ORM (v8.3.1)
- **Session Middleware:** express-session (v1.18.0)
- **Persistent Session Store:** connect-mongo (v5.1.0)
- **Input Filtering / Rate Limits:** express-rate-limit (v7.5.0)
- **Password Cryptography:** bcryptjs (v2.4.3)
- **Variables Parser:** dotenv (v16.4.5)

## Project Structure

EduPoll is organized as a monorepo:

- **`app/`**: Native Android Gradle/Kotlin mobile client skeleton.
- **`server/`**: The core Node.js/Express monolith service root (utilized for web deployment on Render).

## Web Application Structure

Within the `server/` directory:

- **`server.js`**: Core entry point, router mappings, and global middlewares.
- **`db.js`**: Database connector implementing the production Mongoose path and development memory fallback.
- **`middleware/`**: Auth validation, structured logger, and validation schemas.
- **`models/`**: Mongoose schemas for User, Classroom, Poll, and Message.
- **`views/`**: Dynamic EJS templates and partials.

## Request Flow

### HTML Page Request
```
Browser Request (GET) 
  → Express Router 
  → Authentication Middleware (requireLogin) 
  → Database Lookup (Mongoose) 
  → EJS Rendering Engine 
  → Compiled HTML Response 
  → Browser Render
```

### JSON Data Request
```
Browser Fetch (POST/DELETE) 
  → CSRF Validation (Origin Check) 
  → Session Verification (requireLogin) 
  → Input Type & Length Validation (validate middleware) 
  → Database Update (Mongoose Write) 
  → JSON Success/Error Response
```

## Security Controls

- **BCRYPT Password Hashing:** Encrypts user credentials with unique salt generations before database storage.
- **Session Regeneration:** Destroys old sessions on successful authentication to prevent session fixation.
- **MongoDB Session Store:** Stores active sessions in a dedicated MongoDB collection (`sessions`), avoiding local server memory state leaks.
- **Role Authorization:** Restricts page and API routes using explicit role validation checks.
- **Ownership Verification:** Validates that faculty can only view, message, or delete students assigned to them.
- **Origin-Based CSRF Protection:** Validates that incoming state-changing request origins match the configured host origin.
- **Strict Input Validation:** Enforces string data types and restricts characters/length parameters at the route gate.
- **Rate Limiting:** Prevents brute-force credential stuffing via total-rate and key-pair rate limits.
- **NoSQL Injection Resistance:** Rejects object-structured queries at the validation layer before they reach database drivers.
- **Mass-Assignment Protection:** Discards unrecognized input keys during user creation and profile updates.
- **Safe User Serialization:** Strips sensitive user passwords and hashes before API serialization.
- **Browser Security Headers:** Deploys XSS, MIME-sniffing, clickjacking, and referrer security headers on all responses.
- **No-Store Cache Controls:** Enforces that sensitive and authenticated pages are never cached in browser history.
- **Structured Audit Logging:** Registers critical lifecycle actions to standard output in clean JSON format.
- **MongoDB Unique Indexes:** Enforces database-level uniqueness constraints on `secId` and `email` collections.
- **Atomic Duplicate Response Prevention:** Employs document-level locks via `findOneAndUpdate` to reject duplicate votes.
- **Production Fail-Closed Configuration:** Aborts startup if environment secret variables are missing or invalid in production mode.

## Local Development

To run the application locally:

1. Navigate to the server folder:
   ```bash
   cd server
   ```
2. Install node dependencies:
   ```bash
   npm install
   ```
3. Seed the local database:
   ```bash
   npm run seed
   ```
4. Start the development server:
   ```bash
   npm run dev
   ```

## Environment Variables

- `PORT`: Port for the Express server (Default: 3000)
- `NODE_ENV`: Runtime mode (`development` or `production`)
- `MONGO_URI`: MongoDB connection string URI
- `SESSION_SECRET`: Cryptographically random cookie signer key (min 32 chars)
- `APP_ORIGIN`: Authorized application host URL for CSRF checks

## MongoDB

- **Production:** A live MongoDB database instance is required. Deploying via MongoDB Atlas is highly recommended.
- **Development:** If no connection parameters are specified, the database connector automatically boots using the volatile in-memory fallback.

## Deployment

The monorepo web application is designed to be deployed to:
- **Render Web Service:** Configured to build and serve from the `server/` directory root.
- **MongoDB Atlas:** Houses persistent data storage.

Vercel is not required or recommended for this architecture as EJS view routing and API processing are coupled inside the Node.js Express process.

## Security Validation

The application has passed thorough staged security validations verifying credential timing parity, concurrency locks under heavy simulated load, unique index integrity, NoSQL operator sanitization, stale session invalidation, and audit logging outputs.

## Known Technical Debt

- **CSP Inline Script Rules:** The Content-Security-Policy uses `'unsafe-inline'` to support external Tailwind CDN and Google Fonts scripts.
- **SameSite Lax Cookie Restrictions:** Session cookies use `SameSite=Lax`.

## Future Roadmap

- **CSP Nonce/Hash Migration:** Implement cryptographic nonces to remove unsafe-inline permissions.
- **Externalize Browser Scripts:** Extract inline client-side EJS Javascript code to separate static files.
- **Android Client Integration:** Hook the native Kotlin mobile skeleton into existing API routes.
- **Automated CI Security Regression Tests:** Script security assertions into automated pull-request validation pipelines.

## License

No license has been selected yet.
