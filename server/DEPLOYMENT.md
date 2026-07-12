# EduPoll Render Deployment

This document provides step-by-step instructions for deploying the EduPoll Express.js monolith to Render, integrated with a MongoDB Atlas cloud database.

## Architecture

EduPoll is deployed as a single, unified Node.js process containing both the dynamic EJS HTML layout engine and the backend JSON API router. The client EJS templates communicate asynchronously with the backend server via `/api` routes on the same host port.

## Prerequisites

- **GitHub Repository:** Host the APPLICATION_ANTI monorepo in a private GitHub repository.
- **Render Account:** Sign up at [Render](https://render.com).
- **MongoDB Atlas Cluster:** Provision a free-tier M0 cluster or higher at [MongoDB Atlas](https://www.mongodb.com/cloud/atlas).

## MongoDB Atlas Setup

1. Create a MongoDB Atlas cluster and navigate to **Database Access**. Create a dedicated user with read-write access to the target database.
2. Navigate to **Network Access** and add IP access rules. (Add `0.0.0.0/0` to allow Render's dynamic outbound IPs, or configure private VPC Peering if using Render's enterprise tier).
3. Retrieve your connection string URI:
   - Select **Connect** -> **Drivers** -> Copy the connection string.
   - Replace `<username>` and `<password>` with the database user credentials.
   - Specify the target database name as `edupoll_prod`.

## Render Web Service Configuration

When creating a new Web Service on Render:

1. Connect your GitHub repository.
2. Configure the following service settings:
   - **Service Type:** Web Service
   - **Runtime:** Node
   - **Root Directory:** `server`
   - **Build Command:** `npm install`
   - **Start Command:** `npm start`

## Environment Variables

Configure these variables under the **Environment** tab on Render:

- `NODE_ENV`: `production`
- `SESSION_SECRET`: `[A cryptographically random string at least 32 characters in length]`
- `MONGO_URI`: `[Your MongoDB Atlas Connection String]`
- `APP_ORIGIN`: `https://[your-service-subdomain].onrender.com`

*Note: The `PORT` variable is automatically injected by Render and read by the application.*

## First Deployment

1. Click **Create Web Service**. Render will clone the repository, isolate the `server` directory, execute `npm install`, and launch the Express application.
2. Monitor the deployment output. The service is active once logs report:
   ```
   Connected to MongoDB database successfully.
   EduPoll backend server running at http://localhost:3000
   ```

## Health Verification

The application exposes a lightweight database health check endpoint:
- **Health Check Path:** `GET /api/status/db`
- **Expected Success Response:**
  - Status: `200 OK`
  - Body: `{"status":"connected","database":"mongodb"}`

If the database is unreachable, the endpoint returns `503 Service Unavailable`.

## Authentication Smoke Test

1. Navigate to your deployed homepage (`https://[your-app].onrender.com/`).
2. Log in using a pre-seeded account (if preloading was triggered) or your bootstrapped administrator credentials.
3. Verify that an incorrect password immediately returns a `401 Unauthorized` response with the body `{"error":"Invalid credentials."}`.

## Session Smoke Test

1. After logging in, verify that the browser cookie storage contains the `connect.sid` session token.
2. Confirm the cookie features the `Secure`, `HttpOnly`, and `SameSite=Lax` properties.
3. Log out and confirm the cookie is destroyed, and trying to access `/faculty/dashboard` returns a `302 Found` redirect back to the home page.

## Poll Smoke Test

1. Authenticate as a faculty member and navigate to `/faculty/polls/create`.
2. Design a new live poll targeting an enrolled classroom student.
3. Authenticate as the targeted student in an incognito window, navigate to `/student/polls`, and verify the poll is active.
4. Submit a vote and confirm that a second submit attempt returns a `400 Bad Request` `"Already responded."` validation error.

## Message Smoke Test

1. Log in as a faculty member and navigate to `/messages`.
2. Send a direct message to a student.
3. Log in as the student and verify the message appears in their inbox.
4. Send a test message with a spoofed `Origin` header (e.g. using curl or Postman) and verify it is rejected with a `403 Forbidden` response.

## Production Log Review

EduPoll outputs structured JSON log entries to `stdout` for all security-relevant operations. These logs are automatically indexed by Render.

Sample structured event:
```json
{"event":"LOGIN_SUCCESS","actorId":"66d0124312ab5643ef001122","role":"student","result":"success","ip":"192.168.1.1","timestamp":"2026-07-12T16:30:15.535Z"}
```

Ensure no raw passwords or secret tokens are present in the Render logs dashboard.

## Rollback

1. In the Render Dashboard, select your service and navigate to **Events**.
2. Identify a prior successful build event.
3. Click the options menu next to the build and select **Rollback to this deploy**.
4. The service will redeploy the code state corresponding to that specific git commit.

## Secret Rotation

### MongoDB Atlas Connection String
1. Update your database user password in MongoDB Atlas under **Database Access**.
2. Update the `MONGO_URI` variable under the **Environment** tab on Render.
3. Click **Save Changes**. Render will automatically trigger a rolling restart of the application container using the new URI.

### Session Secret
1. Generate a new cryptographically random 32+ character key.
2. Update the `SESSION_SECRET` variable under the **Environment** tab on Render.
3. Save changes.
4. **Warning:** Rotating the `SESSION_SECRET` will immediately invalidate all active user sessions, forcing all current users to log in again.
