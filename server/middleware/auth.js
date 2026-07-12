const { auditLog } = require('./logger');

function requireLogin(req, res, next) {
  if (req.user) {
    return next();
  }
  auditLog('AUTHORIZATION_DENIED', req, 'anonymous', 'anonymous', 'failure');
  if (req.session && req.session.userId) {
    req.session.destroy(err => {
      if (err) console.error('Failed to destroy stale session in requireLogin:', err);
    });
  }
  return res.redirect('/login');
}

function requireRole(role) {
  return (req, res, next) => {
    if (req.user && req.user.role === role) {
      return next();
    }
    auditLog('AUTHORIZATION_DENIED', req, req.user?._id, req.user?.role, 'failure');
    // Redirect using DB-backed user role
    if (req.user && req.user.role) {
      if (req.user.role === 'admin') {
        return res.redirect('/admin/dashboard');
      } else if (req.user.role === 'faculty') {
        return res.redirect('/faculty/dashboard');
      } else {
        return res.redirect('/student/polls');
      }
    }
    // Invalidate session if stale
    if (req.session && req.session.userId) {
      req.session.destroy(err => {
        if (err) console.error('Failed to destroy stale session in requireRole:', err);
      });
    }
    return res.redirect('/login');
  };
}

module.exports = {
  requireLogin,
  requireRole
};
