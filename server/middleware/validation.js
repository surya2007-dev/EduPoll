const isString = val => typeof val === 'string';
const isArrayOfStrings = val => Array.isArray(val) && val.every(item => typeof item === 'string');

const isValidId = val => {
  if (!isString(val)) return false;
  const idRegex = /^[a-zA-Z0-9_\-]+$/;
  return idRegex.test(val) && val.length <= 64;
};

module.exports = {
  validateLogin: (req, res, next) => {
    const { secId, password, role } = req.body;
    if (!isString(secId) || !isString(password) || !isString(role)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (secId.length > 64 || password.length > 256 || role.length > 64) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    next();
  },

  validateFacultyRegister: (req, res, next) => {
    const { name, secId, email, department, password, gender } = req.body;
    if (!isString(name) || !isString(secId) || !isString(email) || !isString(department) || !isString(password) || !isString(gender)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (name.length > 100 || secId.length > 64 || email.length > 254 || department.length > 100 || password.length > 256 || gender.length > 64) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    next();
  },

  validateStudentRegister: (req, res, next) => {
    const { name, secId, year, section, department, classroom, description, password, gender, email } = req.body;
    if (!isString(name) || !isString(secId) || !isString(year) || !isString(section) || !isString(department) || !isString(password) || !isString(gender) || !isString(email)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (classroom && !isString(classroom)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (description && !isString(description)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (name.length > 100 || secId.length > 64 || year.length > 64 || section.length > 64 || department.length > 100 || password.length > 256 || gender.length > 64 || email.length > 254) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (classroom && classroom.length > 64) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (description && description.length > 1000) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    next();
  },

  validateProfileUpdate: (req, res, next) => {
    const { name, email, secId, department, password, profilePhoto, currentPassword } = req.body;
    if (!isString(name) || !isString(email) || !isString(secId) || !isString(department) || !isString(currentPassword)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (password && !isString(password)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (profilePhoto && !isString(profilePhoto)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (name.length > 100 || email.length > 254 || secId.length > 64 || department.length > 100 || currentPassword.length > 256) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (password && password.length > 256) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (profilePhoto && profilePhoto.length > 256) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    next();
  },

  validateMessageSend: (req, res, next) => {
    const { content, recipient, recipientGroup } = req.body;
    if (!isString(content) || (recipientGroup && !isString(recipientGroup))) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (recipient && !isString(recipient)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (content.length > 5000) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (recipient && recipient.length > 64) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (recipientGroup && recipientGroup.length > 64) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    next();
  },

  validateClassroomCreate: (req, res, next) => {
    const { name, subject, year, semester, department, description } = req.body;
    if (!isString(name) || !isString(subject) || !isString(year) || !isString(semester) || !isString(department)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (description && !isString(description)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (name.length > 100 || subject.length > 100 || year.length > 64 || semester.length > 64 || department.length > 100) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (description && description.length > 1000) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    next();
  },

  validatePollCreate: (req, res, next) => {
    const { type, classroom, question, options, targetStudents, targetGroup } = req.body;
    if (!isString(question) || !isString(classroom) || (type && !isString(type))) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (!options || !isArrayOfStrings(options)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (targetStudents && !isArrayOfStrings(targetStudents)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (targetGroup && !isString(targetGroup)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }

    if (question.length > 500 || classroom.length > 64 || (type && type.length > 64)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (options.length > 20 || options.some(opt => opt.length > 500)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (targetStudents && targetStudents.length > 200) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    next();
  },

  validatePollSubmit: (req, res, next) => {
    const { selectedOption } = req.body;
    if (!isString(selectedOption)) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    if (selectedOption.length > 500) {
      return res.status(400).json({ error: 'Invalid request data.' });
    }
    next();
  },

  validateIdParam: (paramName) => {
    return (req, res, next) => {
      const id = req.params[paramName];
      if (!isValidId(id)) {
        return res.status(400).json({ error: 'Invalid request data.' });
      }
      next();
    };
  }
};
