const auditLog = (event, req, actorId, role, result) => {
  console.log(JSON.stringify({
    event,
    actorId: actorId || 'anonymous',
    role: role || 'anonymous',
    result,
    ip: req?.ip || 'unknown',
    timestamp: new Date().toISOString()
  }));
};

module.exports = { auditLog };
