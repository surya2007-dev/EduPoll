# EduPoll Backend Server Engine

This is the functional JavaScript backend engine (Node.js/Express) and MongoDB database structure for **EduPoll**, integrated with all 9 designed pages using EJS templates. 

## Features
- **Unified Authentication Gateway**: Role switcher supporting Faculty & Admin vs Student Access.
- **Faculty Dashboard**: Real-time response chart visualizations, countdown timers, response lists, and nudge events.
- **Classrooms & Students Management**: Dynamic enrollment tracking, classroom creating, and student registrations.
- **Polls & Activities Creator**: Real-time polling with targeting logic, dynamic option controls, and checklist challenges.
- **Reports & Analytics**: Average participation tracking, status audits, and mock CSV downloads.
- **Student Portal**: Live classroom poll voting and response logging.

---

## Prerequisites
- **Node.js** (v16+)
- **MongoDB** (running locally on port 27017 or a custom URI)

---

## Getting Started

### 1. Install Dependencies
Change into the `server` directory and install the required Node modules:
```bash
npm install
```

### 2. Configure Environment Variables
You can configure options in the `.env` file:
```env
PORT=3000
MONGO_URI=mongodb://127.0.0.1:27017/edupoll
SESSION_SECRET=edupoll-enterprise-secret-key-998877
```

### 3. Development Bootstrap Seed Data
When running locally in development mode (`NODE_ENV=development`), you can populate default classrooms, student profiles, and demo poll states:
```bash
npm run seed
```

### 4. Production Administrator Bootstrap
Automatic demo account seeding is disabled in production (`NODE_ENV=production`). To provision the first administrator account in a fresh production database:
1. Configure your production `MONGO_URI`.
2. Configure bootstrap-only admin environment variables:
   - `BOOTSTRAP_ADMIN_SEC_ID`
   - `BOOTSTRAP_ADMIN_NAME`
   - `BOOTSTRAP_ADMIN_EMAIL`
   - `BOOTSTRAP_ADMIN_PASSWORD` (min 12 characters)
3. Run the bootstrap command exactly once:
```bash
npm run bootstrap-admin
```
4. Remove the bootstrap password and environment variables after successful creation.
5. Start or deploy the application normally (`npm start`). Normal application startup never seeds demo users.

---

## Development / Demo Credentials
After running `npm run seed` in local development mode, you can log in with:

- **Faculty & Admin Login**:
  - Email/Id: `admin@university.edu`
  - Password: `admin123`
  
- **Student Login**:
  - Email/Id: `STUDENT_TEST_101`
  - Password: `student123`
