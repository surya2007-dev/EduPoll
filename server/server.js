const express = require('express');
const session = require('express-session');
const MongoStore = require('connect-mongo');
const cookieParser = require('cookie-parser');
const cors = require('cors');
const path = require('path');
const { rateLimit } = require('express-rate-limit');
const bcrypt = require('bcryptjs');
require('dotenv').config();

const db = require('./db');
const { requireLogin, requireRole } = require('./middleware/auth');
const {
  validateLogin,
  validateFacultyRegister,
  validateStudentRegister,
  validateProfileUpdate,
  validateMessageSend,
  validateClassroomCreate,
  validatePollCreate,
  validatePollSubmit,
  validateIdParam
} = require('./middleware/validation');
const { auditLog } = require('./middleware/logger');
const app = express();
app.disable('x-powered-by');
const PORT = process.env.PORT || 3000;

// Connect to Database (auto fallbacks to In-Memory if MongoDB is offline)
const MONGO_URI = process.env.MONGO_URI || 'mongodb://127.0.0.1:27017/edupoll';

// View Engine
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

// Middlewares
app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ limit: '10mb', extended: true }));
app.use(cookieParser());

// Browser Hardening Security Headers Middleware
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('Permissions-Policy', 'geolocation=(), microphone=(), camera=()');
  
  if (process.env.NODE_ENV === 'production') {
    res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
  }

  // Minimum transitional CSP compatible with the current frontend EJS view dependencies
  res.setHeader('Content-Security-Policy', 
    "default-src 'self'; " +
    "script-src 'self' 'unsafe-inline' https://cdn.tailwindcss.com; " +
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
    "font-src 'self' https://fonts.gstatic.com; " +
    "img-src 'self' data:; " +
    "connect-src 'self'; " +
    "object-src 'none'; " +
    "base-uri 'self'; " +
    "frame-ancestors 'none'; " +
    "form-action 'self';"
  );

  next();
});

// Cache Hardening for Authenticated and Sensitive Routes
app.use((req, res, next) => {
  const path = req.path;
  if (
    path.startsWith('/faculty') ||
    path.startsWith('/student') ||
    path.startsWith('/admin') ||
    (path.startsWith('/api') && path !== '/api/status/db')
  ) {
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, proxy-revalidate');
    res.setHeader('Pragma', 'no-cache');
    res.setHeader('Expires', '0');
  }
  next();
});

app.use(express.static(path.join(__dirname, 'public')));

const NODE_ENV = process.env.NODE_ENV || 'development';
const SESSION_SECRET = process.env.SESSION_SECRET;
if (!SESSION_SECRET || !SESSION_SECRET.trim()) {
  console.error('Missing required environment variable: SESSION_SECRET');
  process.exit(1);
}
if (SESSION_SECRET.trim().length < 32) {
  console.error('SESSION_SECRET must be at least 32 characters');
  process.exit(1);
}

if (NODE_ENV === 'production' && !process.env.MONGO_URI) {
  console.error('Missing required environment variable in production: MONGO_URI');
  process.exit(1);
}

let sessionStore;
if (process.env.MONGO_URI || NODE_ENV === 'production') {
  sessionStore = MongoStore.create({
    mongoUrl: process.env.MONGO_URI || MONGO_URI,
    collectionName: 'sessions',
    ttl: 24 * 60 * 60 // 24 hours (aligned with cookie maxAge)
  });
} else {
  console.warn('WARNING: Using in-memory session store in development. Sessions will not persist across restarts.');
  sessionStore = undefined;
}

const isProduction = NODE_ENV === 'production';
if (isProduction) {
  app.set('trust proxy', 1);
}

app.use(session({
  store: sessionStore,
  secret: SESSION_SECRET,
  resave: false,
  saveUninitialized: false,
  cookie: {
    httpOnly: true,
    secure: isProduction,
    sameSite: 'lax',
    maxAge: 24 * 60 * 60 * 1000, // 24 hours
    path: '/'
  }
}));

// Inject user variables globally into views
app.use(async (req, res, next) => {
  res.locals.path = req.path;
  res.locals.unreadMessagesCount = 0;
  res.locals.recentMessages = [];
  res.locals.mentor = null;
  if (req.session && req.session.userId) {
    try {
      const user = await db.findUserById(req.session.userId);
      if (user) {
        req.user = user;
        res.locals.user = user;
        
        // Sync role if desynchronized
        if (req.session.userRole !== user.role) {
          req.session.userRole = user.role;
        }

        if (user.role === 'student') {
          const info = await db.getMentorInfo(user._id);
          res.locals.mentor = info ? info.mentor : null;
        }
        const messages = await db.getMessagesForUser(user._id, user.role);
        const lastRead = user.lastReadMessagesAt ? new Date(user.lastReadMessagesAt) : new Date(0);
        const receivedMessages = messages.filter(m => {
          const senderId = (m.sender?._id || m.sender);
          return senderId && senderId.toString() !== user._id.toString();
        });
        res.locals.unreadMessagesCount = receivedMessages.filter(m => new Date(m.timestamp) > lastRead).length;
        res.locals.recentMessages = receivedMessages.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp)).slice(0, 10);
        next();
      } else {
        // Stale session invalidation: User deleted from database
        res.locals.user = null;
        req.user = null;
        req.session.destroy((err) => {
          if (err) {
            console.error('Failed to destroy stale session in global loader:', err);
          }
          next();
        });
      }
    } catch (err) {
      console.error('Error loading session user:', err);
      next();
    }
  } else {
    res.locals.user = null;
    next();
  }
});

// Global CSRF Origin/Referer Defense Middleware for Authenticated State-Changing Requests
app.use((req, res, next) => {
  const stateChangingMethods = ['POST', 'PUT', 'PATCH', 'DELETE'];
  if (!stateChangingMethods.includes(req.method)) {
    return next();
  }
  if (!req.session || !req.session.userId) {
    return next();
  }

  function getNormalizedOrigin(urlString) {
    if (!urlString || typeof urlString !== 'string') return null;
    try {
      const parsed = new URL(urlString);
      return parsed.origin;
    } catch (e) {
      return null;
    }
  }

  const hostHeader = req.get('host');
  if (!hostHeader) {
    return res.status(403).json({ error: 'Invalid request origin.' });
  }

  const expectedOrigin = getNormalizedOrigin(
    process.env.APP_ORIGIN || `${req.protocol}://${hostHeader}`
  );
  if (!expectedOrigin) {
    return res.status(403).json({ error: 'Invalid request origin.' });
  }

  const originHeader = req.get('origin');
  const refererHeader = req.get('referer');

  if (originHeader) {
    const requestOrigin = getNormalizedOrigin(originHeader);
    if (!requestOrigin || requestOrigin !== expectedOrigin) {
      return res.status(403).json({ error: 'Invalid request origin.' });
    }
    return next();
  }

  if (refererHeader) {
    const requestOrigin = getNormalizedOrigin(refererHeader);
    if (!requestOrigin || requestOrigin !== expectedOrigin) {
      return res.status(403).json({ error: 'Invalid request origin.' });
    }
    return next();
  }

  return res.status(403).json({ error: 'Invalid request origin.' });
});

app.get('/api/status/db', (req, res) => {
  if (process.env.NODE_ENV === 'production') {
    return res.json({ status: 'ok' });
  }
  return res.json({ status: 'ok', isMemory: db.isMemory() });
});

// ==========================================
// AUTH ROUTE APIS
// ==========================================

// Render Login Gate
app.get('/login', (req, res) => {
  if (req.session.userId) {
    if (req.session.userRole === 'admin') return res.redirect('/admin/dashboard');
    if (req.session.userRole === 'faculty') return res.redirect('/faculty/dashboard');
    return res.redirect('/student/polls');
  }
  res.render('index');
});

app.get('/', (req, res) => {
  res.redirect('/login');
});

// Layer 1: Per-Credential + Source IP Limiter (Brute-Force Protection)
const credentialLoginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  limit: 10, // Max 10 failed login attempts per secId + IP
  skipSuccessfulRequests: true,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: (req) => {
    const secIdKey = (req.body && req.body.secId && typeof req.body.secId === 'string')
      ? req.body.secId.trim().toUpperCase()
      : '';
    return secIdKey ? `${req.ip}_${secIdKey}` : req.ip;
  },
  handler: (req, res, next, options) => {
    res.status(options.statusCode).json({
      error: 'Too many login attempts. Please try again later.'
    });
  }
});

// Layer 2: Broad Source IP Limiter (Password Spraying Protection)
const broadLoginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  limit: 100, // Max 100 failed login attempts per source IP (accommodating shared NAT/Wi-Fi)
  skipSuccessfulRequests: true,
  standardHeaders: true,
  legacyHeaders: false,
  handler: (req, res, next, options) => {
    res.status(options.statusCode).json({
      error: 'Too many login attempts. Please try again later.'
    });
  }
});

// Rate Limiter for Profile Password Verification (Brute-Force Protection)
const profileLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  limit: 5, // Max 5 failed attempts per authenticated user + IP
  skipSuccessfulRequests: true,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: (req) => {
    const userId = req.user ? req.user._id.toString() : 'anonymous';
    return `${req.ip}_${userId}`;
  },
  handler: (req, res, next, options) => {
    res.status(options.statusCode).json({
      error: 'Too many profile update attempts. Please try again later.'
    });
  }
});

// Login API
app.post('/api/auth/login', broadLoginLimiter, credentialLoginLimiter, validateLogin, async (req, res) => {
  const { secId, password, role } = req.body;
  if (!secId || !password || !role) {
    return res.status(400).json({ error: 'Please provide all login credentials.' });
  }

  try {
    const cleanSecId = secId.trim();
    const secIdRegex = new RegExp(`^${cleanSecId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`, 'i');
    const query = role === 'faculty' 
      ? { secId: secIdRegex, role: { $in: ['faculty', 'admin'] } } 
      : { secId: secIdRegex, role: 'student' };

    const user = await db.findUser(query);
    let isMatch = false;
    if (user) {
      isMatch = await db.comparePassword(user, password);
    } else {
      // Execute dummy comparison to mitigate timing attacks (approx. 80ms)
      await bcrypt.compare(password, '$2y$10$123456789012345678901u5L.G59e/xJ.0dKj6y/h4f/uWw3t9u3S');
    }

    if (!user || !isMatch) {
      auditLog('LOGIN_FAILURE', req, 'anonymous', 'anonymous', 'failure');
      return res.status(401).json({ error: 'Invalid credentials.' });
    }

    // Regenerate session to prevent session fixation attacks
    req.session.regenerate((err) => {
      if (err) {
        console.error('Session regeneration failed during login:', err);
        return res.status(500).json({ error: 'Internal Server Error' });
      }

      // Set session details after successful regeneration
      req.session.userId = user._id;
      req.session.userRole = user.role;

      // Explicitly save the session before returning success to prevent race conditions during immediate client redirect
      req.session.save(async (saveErr) => {
        if (saveErr) {
          console.error('Session save failed during login:', saveErr);
          return res.status(500).json({ error: 'Internal Server Error' });
        }

        try {
          await db.updateUserStatus(user._id, 'In Session');
        } catch (statusErr) {
          console.error('User status update failed during login:', statusErr);
          return req.session.destroy((destroyErr) => {
            if (destroyErr) {
              console.error('Session rollback failed during login error cleanup:', destroyErr);
              delete req.session.userId;
              delete req.session.userRole;
              req.session = null;
              if (req.sessionStore && typeof req.sessionStore.destroy === 'function' && req.sessionID) {
                return req.sessionStore.destroy(req.sessionID, (storeErr) => {
                  if (storeErr) {
                    console.error('Fallback session store deletion failed during login error cleanup:', storeErr);
                  }
                  return res.status(500).json({ error: 'Internal Server Error' });
                });
              }
            }
            return res.status(500).json({ error: 'Internal Server Error' });
          });
        }

        auditLog('LOGIN_SUCCESS', req, user._id, user.role, 'success');
        return res.json({ success: true, role: user.role });
      });
    });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
});

// Logout API
app.get('/api/auth/logout', async (req, res) => {
  if (req.session.userId) {
    try {
      await db.updateUserStatus(req.session.userId, 'Offline');
    } catch(e) {
      console.error(e);
    }
    auditLog('LOGOUT', req, req.session.userId, req.session.userRole, 'success');
  }

  req.session.destroy(err => {
    if (err) {
      console.error(err);
    }
    res.redirect('/login');
  });
});

// ==========================================
// ADMINISTRATOR ROUTES & APIS
// ==========================================

// Render Admin Dashboard Portal
app.get('/admin/dashboard', requireLogin, requireRole('admin'), async (req, res) => {
  try {
    const faculties = await db.allFaculty();
    const students = await db.allStudents();
    const classrooms = await db.allClassrooms();

    res.render('admin-dashboard', {
      faculties,
      stats: {
        totalFaculty: faculties.length,
        totalStudents: students.length,
        totalClassrooms: classrooms.length
      }
    });
  } catch(e) {
    console.error(e);
    res.status(500).send('Error rendering administrator dashboard.');
  }
});
// Render Admin Faculties Page
app.get('/admin/faculties', requireLogin, requireRole('admin'), async (req, res) => {
  try {
    const faculties = await db.allFaculty();
    res.render('admin-faculties', { faculties });
  } catch(e) {
    console.error(e);
    res.status(500).send('Error loading faculty roster.');
  }
});

// Render Admin Classrooms Page
app.get('/admin/classrooms', requireLogin, requireRole('admin'), async (req, res) => {
  try {
    const classrooms = await db.allClassrooms();
    res.render('admin-classrooms', { classrooms });
  } catch(e) {
    console.error(e);
    res.status(500).send('Error loading classroom roster.');
  }
});

// Render Admin Students Page
app.get('/admin/students', requireLogin, requireRole('admin'), async (req, res) => {
  try {
    const students = await db.allStudents();
    res.render('admin-students', { students });
  } catch(e) {
    console.error(e);
    res.status(500).send('Error loading student roster.');
  }
});
// Admin Register Faculty API
app.post('/api/faculty/register', requireLogin, requireRole('admin'), validateFacultyRegister, async (req, res) => {
  const { name, secId, email, department, password, gender } = req.body;
  if (!name || !secId || !email || !department || !password || !gender) {
    return res.status(400).json({ error: 'All fields marked with * are required.' });
  }

  try {
    const existing = await db.findUser({ secId });
    if (existing) {
      auditLog('SECID_CONFLICT', req, req.user?._id, req.user?.role, 'failure');
      return res.status(400).json({ error: 'User with this Faculty ID already exists.' });
    }

    const newFaculty = await db.createUser({
      name,
      secId,
      email,
      department,
      password,
      gender,
      role: 'faculty'
    });

    auditLog('FACULTY_CREATED', req, req.user?._id, req.user?.role, 'success');
    return res.json({ success: true, faculty: newFaculty });
  } catch(e) {
    console.error(e);
    return res.status(500).json({ error: 'Failed to register faculty member.' });
  }
});

// Admin Delete Faculty API
app.delete('/api/faculty/:id', requireLogin, requireRole('admin'), validateIdParam('id'), async (req, res) => {
  try {
    await db.deleteFaculty(req.params.id);
    return res.json({ success: true });
  } catch(e) {
    console.error(e);
    return res.status(500).json({ error: 'Failed to delete faculty member.' });
  }
});

// ==========================================
// MENTEE MANAGEMENT APIS  
// ==========================================

// Add Mentee API — Faculty adds a student as their mentee
app.post('/api/students/:id/mentee', requireLogin, requireRole('faculty'), validateIdParam('id'), async (req, res) => {
  try {
    const result = await db.addMentee(req.user._id, req.params.id);
    return res.json({ success: true, message: 'Student added as your mentee.' });
  } catch(e) {
    console.error(e);
    return res.status(400).json({ error: e.message || 'Failed to add mentee.' });
  }
});

// Remove Mentee API — Faculty removes a student from mentee list
app.delete('/api/students/:id/mentee', requireLogin, requireRole('faculty'), validateIdParam('id'), async (req, res) => {
  try {
    await db.removeMentee(req.user._id, req.params.id);
    return res.json({ success: true, message: 'Student removed from your mentee list.' });
  } catch(e) {
    console.error(e);
    return res.status(500).json({ error: 'Failed to remove mentee.' });
  }
});

// Student Mentee Community Page
app.get('/student/mentees', requireLogin, requireRole('student'), async (req, res) => {
  try {
    const { mentor, fellowMentees } = await db.getMentorInfo(req.user._id);
    const menteeMessages = mentor 
      ? (await db.getMessagesForUser(req.user._id, 'student'))
          .filter(m => m.recipientGroup === 'mentees_only')
      : [];
    res.render('student-mentees', { mentor, fellowMentees, menteeMessages });
  } catch(e) {
    console.error(e);
    res.status(500).send('Error loading mentee community page.');
  }
});

// ==========================================
// FACULTY DASHBOARD ROUTE
// ==========================================

app.get('/faculty/dashboard', requireLogin, requireRole('faculty'), async (req, res) => {
  try {
    const totalStudents = await db.countStudents();
    const activePollsCount = await db.countActivePolls();

    // Aggregate completed and pending responses
    const allPolls = await db.allPolls();
    let completedResponses = 0;
    let pendingResponses = 0;

    allPolls.forEach(p => {
      completedResponses += p.responses.length;
      if (p.status === 'live') {
        const answeredIds = p.responses.map(r => (r.student?._id || r.student).toString());
        const pendingCount = p.targetStudents.filter(s => !answeredIds.includes((s._id || s).toString())).length;
        pendingResponses += pendingCount;
      }
    });

    // Find the latest active poll
    const activePoll = await db.findActivePoll();

    res.render('dashboard', {
      stats: {
        totalStudents,
        activePolls: activePollsCount,
        completedResponses,
        pendingResponses
      },
      activePoll
    });
  } catch (err) {
    console.error(err);
    res.status(500).send('Error rendering dashboard page');
  }
});

// Render Faculty & Admin Profile Page
app.get('/faculty/profile', requireLogin, async (req, res) => {
  try {
    const faculties = await db.allFaculty();
    const students = await db.allStudents();
    res.render('profile', { faculties, students });
  } catch (err) {
    console.error(err);
    res.status(500).send('Error rendering profile page');
  }
});

// Render Faculty Help Page
app.get('/faculty/help', requireLogin, async (req, res) => {
  res.render('help');
});

// Render Faculty Mentees Community Page
app.get('/faculty/mentees', requireLogin, requireRole('faculty'), async (req, res) => {
  try {
    const mentees = await db.getMentees(req.user._id);
    const menteeMessages = (await db.getMessagesForUser(req.user._id, 'faculty'))
      .filter(m => m.recipientGroup === 'mentees_only');
    res.render('mentees', { mentees, menteeMessages });
  } catch (err) {
    console.error(err);
    res.status(500).send('Error rendering mentee community page');
  }
});

// Get current faculty's mentee list (JSON API)
app.get('/api/faculty/mentees', requireLogin, requireRole('faculty'), async (req, res) => {
  try {
    const mentees = await db.getMentees(req.user._id);
    return res.json(mentees);
  } catch(e) {
    console.error(e);
    return res.status(500).json({ error: 'Failed to retrieve mentee list.' });
  }
});

// Update Faculty & Admin Profile API
app.post('/api/faculty/profile', requireLogin, profileLimiter, validateProfileUpdate, async (req, res) => {
  const { name, email, secId, department, password, profilePhoto, currentPassword } = req.body;
  if (!name || !email || !secId || !department || !currentPassword) {
    return res.status(400).json({ error: 'All fields marked with * are required, including your current password.' });
  }

  try {
    const user = await db.findUserById(req.user._id);
    if (!user) {
      return res.status(404).json({ error: 'User account not found.' });
    }

    const isMatch = await db.comparePassword(user, currentPassword);
    if (!isMatch) {
      return res.status(401).json({ error: 'Invalid current password. Unauthorized to save changes.' });
    }

    const updatedUser = await db.updateFacultyProfile(req.user._id, { name, email, secId, department, password, profilePhoto });
    auditLog('PROFILE_CHANGED', req, req.user?._id, req.user?.role, 'success');
    if (password) {
      auditLog('PASSWORD_CHANGED', req, req.user?._id, req.user?.role, 'success');
    }
    return res.json({ success: true, user: updatedUser });
  } catch(e) {
    console.error(e);
    if (e.code === 'SECID_CONFLICT') {
      auditLog('SECID_CONFLICT', req, req.user?._id, req.user?.role, 'failure');
      return res.status(409).json({ error: 'SEC ID is already in use.' });
    }
    return res.status(500).json({ error: 'Failed to update profile.' });
  }
});

// ==========================================
// MESSAGING & COMMUNICATION PORTAL
// ==========================================

// Render Chat Panel
app.get('/messages', requireLogin, async (req, res) => {
  try {
    await db.markMessagesAsRead(req.user._id);
    const rawMessages = await db.getMessagesForUser(req.user._id, req.user.role);
    
    let faculties = [];
    let students = [];
    let adminUser = null;
    let mentees = [];

    if (req.user.role === 'admin') {
      faculties = await db.allFaculty();
    } else if (req.user.role === 'faculty') {
      adminUser = await db.findUser({ role: 'admin' });
      students = await db.allStudents();
      mentees = await db.getMentees(req.user._id);
    } else if (req.user.role === 'student') {
      faculties = await db.allFaculty();
    }

    res.render('messages', {
      messages: rawMessages,
      faculties,
      students,
      adminUser,
      mentees
    });
  } catch(e) {
    console.error(e);
    res.status(500).send('Error loading messaging portal.');
  }
});

// Read All API — Marks all messages as read
app.post('/api/messages/read-all', requireLogin, async (req, res) => {
  try {
    await db.markMessagesAsRead(req.user._id);
    return res.json({ success: true });
  } catch (e) {
    console.error(e);
    return res.status(500).json({ error: 'Failed to mark messages read.' });
  }
});

// Send Message API with role verification checks
app.post('/api/messages/send', requireLogin, validateMessageSend, async (req, res) => {
  const { content, recipient, recipientGroup } = req.body;
  if (!content || (!recipient && recipientGroup === 'direct')) {
    return res.status(400).json({ error: 'Message content and recipient are required.' });
  }

  try {
    // Role Communication Checks
    if (req.user.role === 'student') {
      // Students can ONLY direct message/reply to faculty
      if (recipientGroup !== 'direct' || !recipient) {
        return res.status(403).json({ error: 'Students are not authorized to broadcast.' });
      }
      const recipientUser = await db.findUserById(recipient);
      if (!recipientUser || recipientUser.role !== 'faculty') {
        return res.status(403).json({ error: 'Students can only message faculties.' });
      }
    } else if (req.user.role === 'faculty') {
      // Faculty can send to Admin, specific student, broadcast to students, or mentees only
      if (recipientGroup === 'direct') {
        const recipientUser = await db.findUserById(recipient);
        if (!recipientUser || (recipientUser.role !== 'admin' && recipientUser.role !== 'student')) {
          return res.status(403).json({ error: 'Faculty can only message administrators or students.' });
        }
      } else if (recipientGroup !== 'all_students' && recipientGroup !== 'mentees_only') {
        return res.status(403).json({ error: 'Invalid message group target.' });
      }
    } else if (req.user.role === 'admin') {
      // Admin can send to specific faculty, or broadcast to all faculty
      if (recipientGroup === 'direct') {
        const recipientUser = await db.findUserById(recipient);
        if (!recipientUser || recipientUser.role !== 'faculty') {
          return res.status(403).json({ error: 'Administrators can only message faculty members.' });
        }
      } else if (recipientGroup !== 'all_faculty') {
        return res.status(403).json({ error: 'Invalid message group target.' });
      }
    }

    const newMsg = await db.createMessage({
      sender: req.user._id,
      recipient: recipientGroup === 'direct' ? recipient : null,
      recipientGroup: recipientGroup || 'direct',
      senderFacultyId: req.user.role === 'faculty' ? req.user._id : undefined,
      content
    });

    return res.json({ success: true, message: newMsg });
  } catch(e) {
    console.error(e);
    return res.status(500).json({ error: 'Failed to dispatch message.' });
  }
});

// ==========================================
// CLASSROOMS ROUTES & APIS
// ==========================================

app.get('/faculty/classrooms', requireLogin, requireRole('faculty'), async (req, res) => {
  try {
    const classrooms = await db.allClassrooms();
    res.render('classroom', { classrooms });
  } catch (err) {
    console.error(err);
    res.status(500).send('Error loading classrooms directory');
  }
});

// Create Classroom API
app.post('/api/classrooms', requireLogin, requireRole('faculty'), validateClassroomCreate, async (req, res) => {
  const { name, subject, year, semester, department, description } = req.body;
  if (!name || !subject || !year || !semester || !department) {
    return res.status(400).json({ error: 'All fields marked with * are required.' });
  }

  try {
    const newRoom = await db.createClassroom({
      name,
      subject,
      year,
      semester,
      department,
      description
    });
    return res.json({ success: true, classroom: newRoom });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Failed to create classroom.' });
  }
});

// Get Classroom Students API
app.get('/api/classrooms/:classId/students', requireLogin, requireRole('faculty'), async (req, res) => {
  try {
    const students = await db.allStudents(req.params.classId);
    return res.json(students);
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Failed to retrieve student lists.' });
  }
});

// Delete Classroom API
app.delete('/api/classrooms/:id', requireLogin, requireRole('faculty'), validateIdParam('id'), async (req, res) => {
  try {
    await db.deleteClassroom(req.params.id);
    return res.json({ success: true });
  } catch(e) {
    console.error(e);
    return res.status(500).json({ error: 'Failed to delete classroom.' });
  }
});

// ==========================================
// STUDENTS ROUTES & APIS
// ==========================================

// Render Faculty Students Page — Pass mentee IDs so the view can highlight mentees
app.get('/faculty/students', requireLogin, requireRole('faculty'), async (req, res) => {
  try {
    const classId = req.query.classId || null;
    const students = await db.allStudents(classId);
    const mentees = await db.getMentees(req.user._id);
    const menteeIds = mentees.map(m => (m._id || m).toString());
    res.render('students', { students, menteeIds });
  } catch (err) {
    console.error(err);
    res.status(500).send('Error rendering student lists');
  }
});

// Render student registration form
app.get('/faculty/students/register', requireLogin, requireRole('faculty'), async (req, res) => {
  try {
    const classrooms = await db.allClassrooms();
    res.render('students-register', { classrooms });
  } catch (err) {
    console.error(err);
    res.status(500).send('Error loading registration page');
  }
});

// Register Student API
app.post('/api/students/register', requireLogin, requireRole('faculty'), validateStudentRegister, async (req, res) => {
  const { name, secId, year, section, department, classroom, description, password, gender, email } = req.body;
  if (!name || !secId || !year || !section || !department || !password || !gender || !email) {
    return res.status(400).json({ error: 'All fields marked with * are required.' });
  }

  try {
    const existing = await db.findUser({ secId });
    if (existing) {
      auditLog('SECID_CONFLICT', req, req.user?._id, req.user?.role, 'failure');
      return res.status(400).json({ error: 'Student with this SEC ID is already registered.' });
    }

    const newStudent = await db.createUser({
      name,
      secId,
      year,
      section,
      department,
      classroom: classroom || null,
      description,
      password,
      gender,
      email,
      role: 'student'
    });

    auditLog('STUDENT_CREATED', req, req.user?._id, req.user?.role, 'success');
    return res.json({ success: true, student: newStudent });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Student registration failed.' });
  }
});

// Delete Student API
app.delete('/api/students/:id', requireLogin, requireRole('faculty'), validateIdParam('id'), async (req, res) => {
  try {
    const check = await db.isStudentMentoredBy(req.params.id, req.user._id);
    if (!check.exists) {
      auditLog('STUDENT_DELETED', req, req.user._id, req.user.role, 'failure');
      return res.status(404).json({ error: 'Student account not found.' });
    }
    if (!check.isMentored) {
      auditLog('STUDENT_DELETED', req, req.user._id, req.user.role, 'failure');
      return res.status(403).json({ error: 'Faculty is not mentor of target student.' });
    }
    await db.deleteStudent(req.params.id);
    auditLog('STUDENT_DELETED', req, req.user._id, req.user.role, 'success');
    return res.json({ success: true });
  } catch(e) {
    console.error(e);
    return res.status(500).json({ error: 'Failed to delete student.' });
  }
});

// ==========================================
// POLLS ROUTES & APIS
// ==========================================

app.get('/faculty/polls', requireLogin, requireRole('faculty'), async (req, res) => {
  try {
    const classrooms = await db.allClassrooms();
    const polls = await db.allPolls();
    res.render('polls', { classrooms, polls });
  } catch(e) {
    console.error(e);
    res.status(500).send('Error loading polls list');
  }
});

app.get('/faculty/polls/create', requireLogin, requireRole('faculty'), async (req, res) => {
  try {
    const classrooms = await db.allClassrooms();
    res.render('poll-create', { classrooms });
  } catch(e) {
    console.error(e);
    res.status(500).send('Error loading poll creation page');
  }
});

// Create Poll API — updated to support 'mentees_only' targetGroup
app.post('/api/polls', requireLogin, requireRole('faculty'), validatePollCreate, async (req, res) => {
  const { type, classroom, question, options, targetStudents, targetGroup } = req.body;
  if (!classroom || !question || !options || options.length < 2) {
    return res.status(400).json({ error: 'Question, Classroom, and at least 2 options are required.' });
  }

  try {
    let resolvedTargets = targetStudents;

    // If targeting mentees, auto-resolve from faculty's mentee list
    if (targetGroup === 'mentees_only') {
      const mentees = await db.getMentees(req.user._id);
      resolvedTargets = mentees.map(m => (m._id || m).toString());
      if (resolvedTargets.length === 0) {
        return res.status(400).json({ error: 'You have no mentees assigned. Please add mentees first.' });
      }
    }

    const newPoll = await db.createPoll({ type, classroom, question, options, targetStudents: resolvedTargets });
    return res.json({ success: true, poll: newPoll });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Failed to create active poll.' });
  }
});

// Delete Poll API
app.delete('/api/polls/:id', requireLogin, requireRole('faculty'), validateIdParam('id'), async (req, res) => {
  try {
    await db.deletePoll(req.params.id);
    return res.json({ success: true });
  } catch(e) {
    console.error(e);
    return res.status(500).json({ error: 'Failed to delete poll.' });
  }
});

// Nudge Students API
app.post('/api/polls/:pollId/nudge', requireLogin, requireRole('faculty'), validateIdParam('pollId'), async (req, res) => {
  const { studentId, all } = req.body;
  return res.json({ success: true, message: all ? 'All pending students nudged.' : `Student ${studentId} nudged.` });
});

// ==========================================
// STUDENT VIEW ROUTES & APIS
// ==========================================

app.get('/student/polls', requireLogin, requireRole('student'), async (req, res) => {
  try {
    const classId = req.user.classroom?._id || req.user.classroom;
    const activePoll = await db.findStudentActivePoll(classId, req.user._id);

    let hasResponded = false;
    let myResponse = null;

    if (activePoll) {
      myResponse = activePoll.responses.find(r => (r.student?._id || r.student).toString() === req.user._id.toString());
      hasResponded = !!myResponse;
    }

    res.render('student-view', { activePoll, hasResponded, myResponse });
  } catch (err) {
    console.error(err);
    res.status(500).send('Error loading student polls page');
  }
});

// Student Profile Page
app.get('/student/profile', requireLogin, requireRole('student'), async (req, res) => {
  try {
    const info = await db.getMentorInfo(req.user._id);
    res.render('student-profile', { mentor: info ? info.mentor : null });
  } catch (err) {
    console.error(err);
    res.status(500).send('Error loading student profile page');
  }
});

// Student Poll History Page
app.get('/student/history', requireLogin, requireRole('student'), async (req, res) => {
  try {
    const history = await db.getStudentPollHistory(req.user._id);
    const totalCount = history.length;
    const attendedCount = history.filter(p => p.attended).length;
    const missedCount = totalCount - attendedCount;
    const attendanceRate = totalCount > 0 ? Math.round((attendedCount / totalCount) * 100) : 0;
    res.render('student-history', {
      history,
      stats: { totalCount, attendedCount, missedCount, attendanceRate }
    });
  } catch (err) {
    console.error(err);
    res.status(500).send('Error loading student poll history');
  }
});

// Submit Answer API
app.post('/api/polls/:pollId/submit', requireLogin, requireRole('student'), validateIdParam('pollId'), validatePollSubmit, async (req, res) => {
  const { selectedOption } = req.body;
  if (!selectedOption) {
    return res.status(400).json({ error: 'Answer choice is required.' });
  }

  try {
    await db.submitResponse(req.params.pollId, req.user._id, selectedOption);
    return res.json({ success: true });
  } catch (err) {
    console.error(err);
    if (err.message === 'Student is not targeted by poll.') {
      return res.status(403).json({ error: err.message });
    }
    if (err.message === 'Poll not found.') {
      return res.status(404).json({ error: err.message });
    }
    if (err.message === 'Poll not active.' || err.message === 'Already responded.') {
      return res.status(400).json({ error: err.message });
    }
    return res.status(500).json({ error: 'Failed to record poll submission.' });
  }
});

// ==========================================
// REPORTS ROUTES & APIS
// ==========================================

app.get('/faculty/reports', requireLogin, requireRole('faculty'), async (req, res) => {
  try {
    const polls = await db.allPolls();
    
    // Aggregated statistics
    let totalTargets = 0;
    let totalResponses = 0;
    let activePollsCount = 0;

    polls.forEach(p => {
      totalTargets += p.targetStudents.length;
      totalResponses += p.responses.length;
      if (p.status === 'live') activePollsCount++;
    });

    const avgParticipation = totalTargets > 0 ? Math.round((totalResponses / totalTargets) * 100) : 0;

    res.render('reports', {
      polls,
      avgParticipation,
      activePollsCount,
      totalResponsesCount: totalResponses
    });
  } catch(e) {
    console.error(e);
    res.status(500).send('Error loading reports dashboard');
  }
});

// Export CSV Report
app.get('/api/reports/export', requireLogin, requireRole('faculty'), async (req, res) => {
  try {
    const polls = await db.allPolls();
    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', 'attachment; filename=edupoll_report.csv');

    let csvContent = 'Poll ID,Question,Classroom,Status,Target Students,Total Responses,Created At\n';
    polls.forEach(p => {
      csvContent += `"${p._id}","${p.question.replace(/"/g, '""')}","${p.classroom.name}","${p.status}",${p.targetStudents.length},${p.responses.length},"${p.createdAt.toISOString()}"\n`;
    });

    return res.send(csvContent);
  } catch(e) {
    console.error(e);
    return res.status(500).json({ error: 'Failed to generate report export.' });
  }
});

// Centralized Production-Safe Error Handling Middleware
app.use((err, req, res, next) => {
  if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
    return res.status(400).json({ error: 'Invalid request data.' });
  }
  console.error('Unhandled Error:', err);
  return res.status(500).json({ error: 'Internal server error.' });
});

// ==========================================
// INITIALIZE SERVER
// ==========================================
db.connect(MONGO_URI).then(() => {
  app.listen(PORT, () => {
    console.log(`EduPoll backend server running at http://localhost:${PORT}`);
  });
}).catch(() => {
  console.error('Application failed to initialize database connection. Terminating process.');
  process.exit(1);
});
