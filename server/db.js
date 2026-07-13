const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const User = require('./models/User');
const Classroom = require('./models/Classroom');
const Poll = require('./models/Poll');
const Message = require('./models/Message');

let useMemory = false;

const memory = {
  users: [],
  classrooms: [],
  polls: [],
  messages: []
};

// Strips password from a user object for safe API serialization.
// Does NOT modify the original object or the in-memory store.
// findUser/findUserById are intentionally excluded so the login path
// continues to receive the stored bcrypt hash for comparison.
function safeUser(u) {
  if (!u) return u;
  // For plain objects (memory path)
  if (typeof u.toObject === 'function') {
    const obj = u.toObject();
    delete obj.password;
    return obj;
  }
  const copy = { ...u };
  delete copy.password;
  return copy;
}

// Seed memory DB on start
async function seedMemory() {
  const salt = await bcrypt.genSalt(10);
  const hashedAdminPassword = await bcrypt.hash('admin123', salt);

  // default classrooms
  memory.classrooms = [];

  // default users
  const adminUser = { _id: 'user_admin_0', name: 'Principal Admin', email: 'admin@university.edu', secId: 'ADMINISTRATOR', password: hashedAdminPassword, role: 'admin', department: 'Administration', status: 'Active', lastActive: new Date() };

  memory.users = [adminUser];

  // default poll
  memory.polls = [];
  
  // Default Seed Messages
  memory.messages = [];
}

const db = {
  isMemory: () => useMemory,

  comparePassword: async (user, password) => {
    if (useMemory) {
      return bcrypt.compare(password, user.password);
    }
    return user.comparePassword(password);
  },

  connect: async (uri) => {
    const isProduction = process.env.NODE_ENV === 'production';
    if (isProduction && !uri) {
      const error = new Error('Missing required database configuration in production: MONGO_URI');
      console.error(error.message);
      throw error;
    }

    try {
      mongoose.set('bufferCommands', false); // Fail quickly if disconnected
      await mongoose.connect(uri, { serverSelectionTimeoutMS: 2000 });
      console.log('Connected to MongoDB database successfully.');
      useMemory = false;

      // Always ensure the Principal Administrator account exists so institutional setup can begin
      try {
        const existingAdmin = await User.findOne({ secId: 'ADMINISTRATOR' });
        if (!existingAdmin) {
          await new User({
            secId: 'ADMINISTRATOR',
            password: 'admin123',
            role: 'admin',
            name: 'Principal Admin',
            email: 'admin@university.edu',
            department: 'Administration'
          }).save();
          console.log('Preloaded default administrator account (ADMINISTRATOR) in MongoDB.');
        }
      } catch (adminSeedErr) {
        console.error('Diagnostic: Failed to preload default administrator account:', adminSeedErr.message);
      }

      if (!isProduction) {

        // Seed Faculty
        const existingFaculty = await User.findOne({ secId: 'FAC001' });
        if (!existingFaculty) {
          await new User({
            secId: 'FAC001',
            password: 'admin123',
            role: 'faculty',
            name: 'Mrs. Devika',
            email: 'devika@university.edu',
            department: 'Information Technology'
          }).save();
          console.log('Preloaded default faculty account in MongoDB (development mode).');
        }

        // Seed Student
        const existingStudent = await User.findOne({ secId: 'SEC25IT367' });
        if (!existingStudent) {
          await new User({
            secId: 'SEC25IT367',
            password: 'student123',
            role: 'student',
            name: 'John Doe',
            email: 'john.doe@gmail.com',
            department: 'Information Technology',
            year: 'Year 1',
            section: 'A',
            gender: 'Male'
          }).save();
          console.log('Preloaded default student account in MongoDB (development mode).');
        }
      }
    } catch (err) {
      const diag = {
        name: err && err.name,
        message: err && err.message
      };
      if (err && err.code !== undefined) diag.code = err.code;
      if (err && err.codeName !== undefined) diag.codeName = err.codeName;
      if (err && err.reason) {
        try {
          diag.reason = JSON.parse(JSON.stringify(err.reason));
        } catch (_) {
          diag.reason = String(err.reason);
        }
      }
      console.error('MongoDB connection diagnostic:', JSON.stringify(diag));

      if (isProduction) {
        console.error('CRITICAL: MongoDB connection failed in production mode. Refusing to start with volatile in-memory database fallback.');
        throw new Error('Database connection failed in production mode.');
      }
      console.warn('WARNING: MongoDB unavailable. Using volatile in-memory database for development. Data will be lost on restart.');
      useMemory = true;
      await seedMemory();
      console.log('In-Memory Database initialized with default seed data.');
    }
  },

  // User Actions
  findUser: async (query) => {
    if (useMemory) {
      return memory.users.find(u => {
        if (query.email && u.email !== query.email) return false;
        if (query.secId) {
          if (query.secId instanceof RegExp) {
            if (!query.secId.test(u.secId)) return false;
          } else if (u.secId !== query.secId) {
            return false;
          }
        }
        if (query.role) {
          if (typeof query.role === 'object' && query.role.$in) {
            if (!query.role.$in.includes(u.role)) return false;
          } else if (u.role !== query.role) {
            return false;
          }
        }
        return true;
      });
    }
    return User.findOne(query);
  },

  findUserById: async (id) => {
    if (useMemory) {
      const u = memory.users.find(x => x._id === id);
      if (u && u.classroom && typeof u.classroom === 'string') {
        u.classroom = memory.classrooms.find(c => c._id === u.classroom);
      }
      return u;
    }
    return User.findById(id).populate('classroom');
  },

  countStudents: async () => {
    if (useMemory) {
      return memory.users.filter(u => u.role === 'student').length;
    }
    return User.countDocuments({ role: 'student' });
  },

  allStudents: async (classId = null) => {
    if (useMemory) {
      let list = memory.users.filter(u => u.role === 'student');
      if (classId) {
        list = list.filter(u => (u.classroom?._id || u.classroom) === classId);
      }
      return list.map(u => {
        const room = typeof u.classroom === 'string' 
          ? memory.classrooms.find(c => c._id === u.classroom) 
          : u.classroom;
        return safeUser({ ...u, classroom: room });
      });
    }
    const query = { role: 'student' };
    if (classId) query.classroom = classId;
    return User.find(query).select('-password').populate('classroom');
  },

  createUser: async (data) => {
    if (useMemory) {
      const salt = await bcrypt.genSalt(10);
      const hashedPassword = await bcrypt.hash(data.password, salt);
      const room = data.classroom ? memory.classrooms.find(c => c._id === data.classroom) : null;
      
      const newStudent = {
        _id: 'user_' + Date.now(),
        name: data.name,
        secId: data.secId,
        password: hashedPassword,
        role: data.role || 'student',
        classroom: room,
        department: data.department,
        year: data.year,
        section: data.section,
        description: data.description,
        gender: data.gender || 'Male',
        email: data.email || '',
        status: 'Offline',
        lastActive: new Date()
      };
      memory.users.push(newStudent);

      // Increment enrollment count in classroom
      if (room) {
        room.studentsEnrolledCount = (room.studentsEnrolledCount || 0) + 1;
      }
      return safeUser(newStudent);
    }
    
    const newUser = new User(data);
    await newUser.save();
    return safeUser(newUser);
  },

  isStudentMentoredBy: async (studentId, facultyId) => {
    if (useMemory) {
      const student = memory.users.find(u => u._id === studentId && u.role === 'student');
      if (!student) return { exists: false, isMentored: false };
      const mentorId = student.mentor && typeof student.mentor === 'object' ? student.mentor._id : student.mentor;
      return { exists: true, isMentored: (mentorId === facultyId) };
    }
    const student = await User.findById(studentId);
    if (!student || student.role !== 'student') return { exists: false, isMentored: false };
    const mentorId = student.mentor ? student.mentor.toString() : null;
    return { exists: true, isMentored: (mentorId === facultyId.toString()) };
  },

  deleteStudent: async (id) => {
    if (useMemory) {
      const idx = memory.users.findIndex(u => u._id === id);
      if (idx !== -1) {
        const student = memory.users[idx];
        const room = student.classroom ? memory.classrooms.find(c => c._id === (student.classroom._id || student.classroom)) : null;
        if (room) {
          room.studentsEnrolledCount = Math.max(0, (room.studentsEnrolledCount || 1) - 1);
        }
        memory.users.splice(idx, 1);
        return true;
      }
      return false;
    }
    return User.findByIdAndDelete(id);
  },

  updateUserStatus: async (id, status) => {
    if (useMemory) {
      const u = memory.users.find(x => x._id === id);
      if (u) {
        u.status = status;
        u.lastActive = new Date();
      }
      return u;
    }
    const u = await User.findById(id);
    if (u) {
      u.status = status;
      u.lastActive = new Date();
      await u.save();
    }
    return u;
  },

  markMessagesAsRead: async (id) => {
    const now = new Date();
    if (useMemory) {
      const u = memory.users.find(x => x._id === id);
      if (u) {
        u.lastReadMessagesAt = now;
      }
      return u;
    }
    return User.findByIdAndUpdate(id, { lastReadMessagesAt: now });
  },

  updateFacultyProfile: async (id, data) => {
    if (useMemory) {
      const u = memory.users.find(x => x._id === id);
      if (u) {
        // Pre-update global uniqueness check in memory
        if (data.secId !== u.secId) {
          const conflict = memory.users.some(x => x.secId === data.secId && x._id !== id);
          if (conflict) {
            const err = new Error('SEC ID is already in use.');
            err.code = 'SECID_CONFLICT';
            throw err;
          }
        }
        u.name = data.name;
        u.email = data.email;
        u.secId = data.secId;
        u.department = data.department;
        u.profilePhoto = data.profilePhoto;
        if (data.password) {
          const salt = await bcrypt.genSalt(10);
          u.password = await bcrypt.hash(data.password, salt);
        }
      }
      return safeUser(u);
    }
    const u = await User.findById(id);
    if (u) {
      // Pre-update global uniqueness check in Mongoose
      if (data.secId !== u.secId) {
        const conflict = await User.findOne({ secId: data.secId, _id: { $ne: id } });
        if (conflict) {
          const err = new Error('SEC ID is already in use.');
          err.code = 'SECID_CONFLICT';
          throw err;
        }
      }
      u.name = data.name;
      u.email = data.email;
      u.secId = data.secId;
      u.department = data.department;
      u.profilePhoto = data.profilePhoto;
      if (data.password) {
        u.password = data.password;
      }
      try {
        await u.save();
      } catch (saveErr) {
        if (saveErr.code === 11000) {
          const err = new Error('SEC ID is already in use.');
          err.code = 'SECID_CONFLICT';
          throw err;
        }
        throw saveErr;
      }
    }
    return u ? safeUser(u) : u;
  },

  allFaculty: async () => {
    if (useMemory) {
      return memory.users.filter(u => u.role === 'faculty').map(safeUser);
    }
    return User.find({ role: 'faculty' }).select('-password');
  },

  deleteFaculty: async (id) => {
    if (useMemory) {
      const idx = memory.users.findIndex(u => u._id === id);
      if (idx !== -1) {
        // Clear mentor refs from their mentees
        memory.users.forEach(u => { if (u.mentor === id) u.mentor = null; });
        memory.users.splice(idx, 1);
        return true;
      }
      return false;
    }
    await User.updateMany({ mentor: id }, { mentor: null });
    return User.findByIdAndDelete(id);
  },

  // Mentee Actions
  addMentee: async (facultyId, studentId) => {
    if (useMemory) {
      const faculty = memory.users.find(u => u._id === facultyId && u.role === 'faculty');
      const student = memory.users.find(u => u._id === studentId && u.role === 'student');
      if (!faculty || !student) throw new Error('Faculty or student not found.');
      if (student.mentor && student.mentor !== facultyId) {
        throw new Error('Student already has a different mentor assigned.');
      }
      if (!faculty.mentees) faculty.mentees = [];
      if (!faculty.mentees.includes(studentId)) faculty.mentees.push(studentId);
      student.mentor = facultyId;
      return { faculty, student };
    }
    const faculty = await User.findById(facultyId);
    const student = await User.findById(studentId);
    if (!faculty || !student) throw new Error('Faculty or student not found.');
    if (student.mentor && student.mentor.toString() !== facultyId.toString()) {
      throw new Error('Student already has a different mentor assigned.');
    }
    if (!faculty.mentees.includes(studentId)) faculty.mentees.push(studentId);
    student.mentor = facultyId;
    await faculty.save();
    await student.save();
    return { faculty, student };
  },

  removeMentee: async (facultyId, studentId) => {
    if (useMemory) {
      const faculty = memory.users.find(u => u._id === facultyId && u.role === 'faculty');
      const student = memory.users.find(u => u._id === studentId && u.role === 'student');
      if (faculty && faculty.mentees) {
        faculty.mentees = faculty.mentees.filter(id => id !== studentId);
      }
      if (student) student.mentor = null;
      return true;
    }
    await User.findByIdAndUpdate(facultyId, { $pull: { mentees: studentId } });
    await User.findByIdAndUpdate(studentId, { mentor: null });
    return true;
  },

  getMentees: async (facultyId) => {
    if (useMemory) {
      const faculty = memory.users.find(u => u._id === facultyId);
      if (!faculty || !faculty.mentees || faculty.mentees.length === 0) return [];
      return memory.users.filter(u => faculty.mentees.includes(u._id)).map(u => safeUser({
        ...u,
        classroom: typeof u.classroom === 'string' ? memory.classrooms.find(c => c._id === u.classroom) : u.classroom
      }));
    }
    const faculty = await User.findById(facultyId).populate({ path: 'mentees', select: '-password', populate: { path: 'classroom' } });
    return faculty ? faculty.mentees : [];
  },

  // Get mentor + fellow mentees for a student
  getMentorInfo: async (studentId) => {
    if (useMemory) {
      const student = memory.users.find(u => u._id === studentId);
      if (!student || !student.mentor) return { mentor: null, fellowMentees: [] };
      const mentor = memory.users.find(u => u._id === student.mentor);
      const fellowMentees = mentor && mentor.mentees
        ? memory.users.filter(u => mentor.mentees.includes(u._id) && u._id !== studentId)
        : [];
      return { mentor, fellowMentees };
    }
    const student = await User.findById(studentId).populate('mentor');
    if (!student || !student.mentor) return { mentor: null, fellowMentees: [] };
    const fellowMentees = await User.find({ mentor: student.mentor._id, _id: { $ne: studentId } });
    return { mentor: student.mentor, fellowMentees };
  },

  // Classroom Actions
  allClassrooms: async () => {
    if (useMemory) {
      const livePolls = memory.polls.filter(p => p.status === 'live');
      const liveClassIds = livePolls.map(p => (p.classroom?._id || p.classroom));

      return memory.classrooms.map(room => {
        const studentCount = memory.users.filter(u => u.role === 'student' && (u.classroom?._id || u.classroom) === room._id).length;
        const facultyCount = memory.users.filter(u => u.role === 'faculty' && (room.facultyMembers || []).some(f => (f._id || f) === u._id)).length;
        return {
          ...room,
          studentsEnrolledCount: studentCount,
          students: room.students || [],
          facultyMembers: room.facultyMembers || [],
          studentCapacity: room.studentCapacity || 63,
          facultyCapacity: room.facultyCapacity || 10,
          joinCode: room.joinCode || ('EDU-' + room.code),
          hasLivePoll: liveClassIds.includes(room._id)
        };
      });
    }
    const rooms = await Classroom.find().populate('students').populate('facultyMembers').populate('pendingFacultyRequests.faculty');
    // Ensure joinCode migration for existing rooms
    for (let r of rooms) {
      if (!r.joinCode) {
        const prefix = (r.department || 'EDU').replace(/[^a-zA-Z]/g, '').slice(0, 3).toUpperCase() || 'EDU';
        r.joinCode = `${prefix}-${r.section || 'A'}${Math.floor(1000 + Math.random() * 9000)}`;
        await r.save();
      }
    }
    return rooms;
  },

  getClassroomById: async (id) => {
    if (useMemory) {
      const room = memory.classrooms.find(c => c._id === id);
      return room || null;
    }
    return Classroom.findById(id).populate('students').populate('facultyMembers').populate('pendingFacultyRequests.faculty');
  },

  createClassroom: async (data) => {
    const code = 'CLASS' + String(Date.now()).slice(-4);
    const prefix = (data.department || 'EDU').replace(/[^a-zA-Z]/g, '').slice(0, 3).toUpperCase() || 'EDU';
    const joinCode = `${prefix}-${data.section || 'A'}${Math.floor(1000 + Math.random() * 9000)}`;

    if (useMemory) {
      const newRoom = {
        _id: 'class_' + Date.now(),
        name: data.name,
        subject: data.subject,
        year: data.year,
        semester: data.semester,
        department: data.department,
        section: data.section || 'A',
        code,
        joinCode,
        description: data.description,
        students: [],
        facultyMembers: [],
        pendingFacultyRequests: [],
        studentCapacity: data.studentCapacity || 63,
        facultyCapacity: data.facultyCapacity || 10,
        isActive: true,
        createdBy: data.createdBy || null,
        studentsEnrolledCount: 0
      };
      memory.classrooms.push(newRoom);
      return newRoom;
    }
    const newRoom = new Classroom({
      ...data,
      code,
      joinCode,
      section: data.section || 'A',
      studentCapacity: data.studentCapacity || 63,
      facultyCapacity: data.facultyCapacity || 10,
      students: [],
      facultyMembers: [],
      pendingFacultyRequests: []
    });
    await newRoom.save();
    return newRoom;
  },

  updateClassroom: async (id, updateData) => {
    if (useMemory) {
      const idx = memory.classrooms.findIndex(c => c._id === id);
      if (idx !== -1) {
        memory.classrooms[idx] = { ...memory.classrooms[idx], ...updateData };
        return memory.classrooms[idx];
      }
      return null;
    }
    const room = await Classroom.findById(id);
    if (!room) throw new Error('Classroom not found');

    // Capacity reduction validation
    if (updateData.studentCapacity !== undefined && updateData.studentCapacity < (room.students || []).length) {
      throw new Error(`Cannot reduce student capacity below current enrollment (${room.students.length}).`);
    }
    if (updateData.facultyCapacity !== undefined && updateData.facultyCapacity < (room.facultyMembers || []).length) {
      throw new Error(`Cannot reduce faculty capacity below current membership count (${room.facultyMembers.length}).`);
    }

    Object.assign(room, updateData);
    await room.save();
    return room;
  },

  deleteClassroom: async (id) => {
    if (useMemory) {
      const idx = memory.classrooms.findIndex(c => c._id === id);
      if (idx !== -1) {
        memory.classrooms.splice(idx, 1);
        memory.users.forEach(u => {
          if (u.classroom && (u.classroom._id === id || u.classroom === id)) {
            u.classroom = null;
          }
        });
        return true;
      }
      return false;
    }
    await User.updateMany({ classroom: id }, { classroom: null });
    await Poll.deleteMany({ classroom: id });
    return Classroom.findByIdAndDelete(id);
  },

  // Classroom Membership Actions
  addStudentToClassroom: async (classroomId, studentId) => {
    const sId = studentId.toString();
    if (useMemory) {
      const room = memory.classrooms.find(c => c._id === classroomId);
      const user = memory.users.find(u => u._id === sId);
      if (!room || !user) throw new Error('Classroom or Student not found.');
      if (user.role !== 'student') throw new Error('Only students can be enrolled in the student roster.');
      room.students = room.students || [];
      if (room.students.includes(sId)) throw new Error('Student is already enrolled in this classroom.');
      if (room.students.length >= (room.studentCapacity || 63)) throw new Error('Classroom student capacity reached.');
      room.students.push(sId);
      room.studentsEnrolledCount = room.students.length;
      user.classroom = classroomId;
      return room;
    }
    const user = await User.findById(studentId);
    if (!user) throw new Error('Student not found.');
    if (user.role !== 'student') throw new Error('Only students can be enrolled in the student roster.');

    const room = await Classroom.findById(classroomId);
    if (!room) throw new Error('Classroom not found.');
    if (!room.isActive) throw new Error('Classroom is currently inactive.');

    const currentStudents = (room.students || []).map(id => id.toString());
    if (currentStudents.includes(sId)) throw new Error('Student is already enrolled in this classroom.');
    if (currentStudents.length >= (room.studentCapacity || 63)) {
      throw new Error(`Classroom student capacity reached (${room.studentCapacity || 63}).`);
    }

    room.students.push(user._id);
    room.studentsEnrolledCount = room.students.length;
    await room.save();

    user.classroom = room._id;
    await user.save();

    return room;
  },

  removeStudentFromClassroom: async (classroomId, studentId) => {
    const sId = studentId.toString();
    if (useMemory) {
      const room = memory.classrooms.find(c => c._id === classroomId);
      if (room && room.students) {
        room.students = room.students.filter(id => id.toString() !== sId);
        room.studentsEnrolledCount = room.students.length;
      }
      const user = memory.users.find(u => u._id === sId);
      if (user) user.classroom = null;
      return true;
    }
    const room = await Classroom.findById(classroomId);
    if (room) {
      room.students = (room.students || []).filter(id => id.toString() !== sId);
      room.studentsEnrolledCount = room.students.length;
      await room.save();
    }
    await User.findByIdAndUpdate(studentId, { classroom: null });
    return true;
  },

  addFacultyToClassroom: async (classroomId, facultyId) => {
    const fId = facultyId.toString();
    if (useMemory) {
      const room = memory.classrooms.find(c => c._id === classroomId);
      const user = memory.users.find(u => u._id === fId);
      if (!room || !user) throw new Error('Classroom or Faculty not found.');
      if (user.role !== 'faculty') throw new Error('Only faculty can be assigned to the faculty roster.');
      room.facultyMembers = room.facultyMembers || [];
      if (room.facultyMembers.includes(fId)) throw new Error('Faculty member is already assigned to this classroom.');
      if (room.facultyMembers.length >= (room.facultyCapacity || 10)) throw new Error('Classroom faculty capacity reached.');
      room.facultyMembers.push(fId);
      room.pendingFacultyRequests = (room.pendingFacultyRequests || []).filter(r => (r.faculty?._id || r.faculty).toString() !== fId);
      return room;
    }
    const user = await User.findById(facultyId);
    if (!user) throw new Error('Faculty user not found.');
    if (user.role !== 'faculty') throw new Error('Only faculty can be assigned to the faculty roster.');

    const room = await Classroom.findById(classroomId);
    if (!room) throw new Error('Classroom not found.');

    const currentFaculty = (room.facultyMembers || []).map(id => id.toString());
    if (currentFaculty.includes(fId)) throw new Error('Faculty member is already assigned to this classroom.');
    if (currentFaculty.length >= (room.facultyCapacity || 10)) {
      throw new Error(`Classroom faculty capacity reached (${room.facultyCapacity || 10}).`);
    }

    room.facultyMembers.push(user._id);
    room.pendingFacultyRequests = (room.pendingFacultyRequests || []).filter(r => r.faculty.toString() !== fId);
    await room.save();
    return room;
  },

  removeFacultyFromClassroom: async (classroomId, facultyId) => {
    const fId = facultyId.toString();
    if (useMemory) {
      const room = memory.classrooms.find(c => c._id === classroomId);
      if (room && room.facultyMembers) {
        room.facultyMembers = room.facultyMembers.filter(id => id.toString() !== fId);
      }
      return true;
    }
    const room = await Classroom.findById(classroomId);
    if (room) {
      room.facultyMembers = (room.facultyMembers || []).filter(id => id.toString() !== fId);
      await room.save();
    }
    return true;
  },

  requestFacultyMembership: async (classroomId, facultyId) => {
    const fId = facultyId.toString();
    if (useMemory) {
      const room = memory.classrooms.find(c => c._id === classroomId);
      if (!room) throw new Error('Classroom not found.');
      room.facultyMembers = room.facultyMembers || [];
      room.pendingFacultyRequests = room.pendingFacultyRequests || [];
      if (room.facultyMembers.includes(fId)) throw new Error('You are already an assigned faculty member of this classroom.');
      if (room.pendingFacultyRequests.some(r => (r.faculty?._id || r.faculty) === fId)) {
        throw new Error('Your request to join this classroom is already pending approval.');
      }
      room.pendingFacultyRequests.push({ faculty: fId, requestedAt: new Date() });
      return room;
    }
    const room = await Classroom.findById(classroomId);
    if (!room) throw new Error('Classroom not found.');
    const currentFaculty = (room.facultyMembers || []).map(id => id.toString());
    if (currentFaculty.includes(fId)) throw new Error('You are already an assigned faculty member of this classroom.');
    if ((room.pendingFacultyRequests || []).some(r => r.faculty.toString() === fId)) {
      throw new Error('Your request to join this classroom is already pending admin approval.');
    }
    room.pendingFacultyRequests.push({ faculty: facultyId, requestedAt: new Date() });
    await room.save();
    return room;
  },

  approveFacultyMembership: async (classroomId, facultyId) => {
    return module.exports.addFacultyToClassroom(classroomId, facultyId);
  },

  rejectFacultyMembership: async (classroomId, facultyId) => {
    const fId = facultyId.toString();
    if (useMemory) {
      const room = memory.classrooms.find(c => c._id === classroomId);
      if (room) {
        room.pendingFacultyRequests = (room.pendingFacultyRequests || []).filter(r => (r.faculty?._id || r.faculty).toString() !== fId);
      }
      return true;
    }
    const room = await Classroom.findById(classroomId);
    if (room) {
      room.pendingFacultyRequests = (room.pendingFacultyRequests || []).filter(r => r.faculty.toString() !== fId);
      await room.save();
    }
    return true;
  },

  joinClassroomByCode: async (joinCodeInput, userId) => {
    const cleanCode = (joinCodeInput || '').trim();
    if (!cleanCode) throw new Error('Please provide a valid classroom join code.');

    let room = null;
    if (useMemory) {
      room = memory.classrooms.find(c => c.joinCode === cleanCode || c.code === cleanCode);
    } else {
      room = await Classroom.findOne({ $or: [{ joinCode: cleanCode }, { code: cleanCode }] });
    }
    if (!room) throw new Error('Invalid classroom code. No matching classroom found.');
    if (!room.isActive) throw new Error('This classroom is inactive.');

    const user = useMemory ? memory.users.find(u => u._id === userId.toString()) : await User.findById(userId);
    if (!user) throw new Error('User account required.');

    if (user.role === 'student') {
      await module.exports.addStudentToClassroom(room._id, user._id);
      return { status: 'enrolled', classroom: room };
    } else if (user.role === 'faculty') {
      await module.exports.requestFacultyMembership(room._id, user._id);
      return { status: 'pending_approval', classroom: room };
    } else {
      throw new Error('Only faculty and students can join classrooms via join code.');
    }
  },

  getClassroomMetrics: async (classroomId) => {
    if (useMemory) {
      const room = memory.classrooms.find(c => c._id === classroomId);
      const polls = memory.polls.filter(p => (p.classroom?._id || p.classroom) === classroomId);
      const activePolls = polls.filter(p => p.status === 'live').length;
      const completedPolls = polls.filter(p => p.status === 'completed').length;
      return {
        totalStudents: (room?.students || []).length,
        totalFaculty: (room?.facultyMembers || []).length,
        activePolls,
        completedPolls,
        participationRate: 85
      };
    }
    const room = await Classroom.findById(classroomId);
    const activePolls = await Poll.countDocuments({ classroom: classroomId, status: 'live' });
    const completedPolls = await Poll.countDocuments({ classroom: classroomId, status: 'completed' });
    const totalStudents = (room?.students || []).length;
    const totalFaculty = (room?.facultyMembers || []).length;
    return {
      totalStudents,
      totalFaculty,
      activePolls,
      completedPolls,
      participationRate: totalStudents > 0 ? 87 : 0
    };
  },

  // Poll Actions
  allPolls: async () => {
    if (useMemory) {
      return memory.polls.map(p => {
        const room = typeof p.classroom === 'string' ? memory.classrooms.find(c => c._id === p.classroom) : p.classroom;
        return { ...p, classroom: room };
      });
    }
    return Poll.find().populate('classroom').populate('targetStudents');
  },

  countActivePolls: async () => {
    if (useMemory) {
      return memory.polls.filter(p => p.status === 'live').length;
    }
    return Poll.countDocuments({ status: 'live' });
  },

  findActivePoll: async () => {
    if (useMemory) {
      const poll = memory.polls.find(p => p.status === 'live');
      if (poll) {
        const room = typeof poll.classroom === 'string' ? memory.classrooms.find(c => c._id === poll.classroom) : poll.classroom;
        const targets = poll.targetStudents.map(s => {
          return typeof s === 'string' ? memory.users.find(u => u._id === s) : s;
        });
        const populatedResponses = poll.responses.map(r => {
          const studObj = typeof r.student === 'string' ? memory.users.find(u => u._id === r.student) : r.student;
          return { ...r, student: studObj };
        });
        return { ...poll, classroom: room, targetStudents: targets, responses: populatedResponses };
      }
      return null;
    }
    return Poll.findOne({ status: 'live' })
      .populate('classroom')
      .populate('targetStudents')
      .populate({
        path: 'responses.student',
        select: 'name secId'
      });
  },

  findStudentActivePoll: async (classId, studentId) => {
    const sId = studentId.toString();
    if (useMemory) {
      const activePolls = memory.polls.filter(p => {
        const roomMatch = (p.classroom?._id || p.classroom) === classId;
        const studentMatch = (p.targetStudents || []).some(s => (s._id || s).toString() === sId);
        return p.status === 'live' && roomMatch && studentMatch;
      }).sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

      if (activePolls.length > 0) {
        const unanswered = activePolls.find(p => !(p.responses || []).some(r => (r.student?._id || r.student).toString() === sId));
        const poll = unanswered || activePolls[0];
        const room = typeof poll.classroom === 'string' ? memory.classrooms.find(c => c._id === poll.classroom) : poll.classroom;
        return { ...poll, classroom: room };
      }
      return null;
    }
    const activePolls = await Poll.find({ 
      classroom: classId, 
      status: 'live',
      targetStudents: studentId
    }).sort({ createdAt: -1 }).populate('classroom');
    if (activePolls.length === 0) return null;
    const unanswered = activePolls.find(p => !(p.responses || []).some(r => (r.student?._id || r.student).toString() === sId));
    return unanswered || activePolls[0];
  },

  getStudentPollHistory: async (studentId) => {
    const sId = studentId.toString();
    if (useMemory) {
      const targetedPolls = memory.polls.filter(p => {
        return (p.targetStudents || []).some(s => (s._id || s).toString() === sId);
      });
      return targetedPolls.map(p => {
        const room = typeof p.classroom === 'string' ? memory.classrooms.find(c => c._id === p.classroom) : p.classroom;
        const resp = (p.responses || []).find(r => (r.student?._id || r.student).toString() === sId);
        return {
          ...p,
          classroom: room || { name: 'Classroom' },
          attended: !!resp,
          myResponse: resp || null
        };
      }).sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    }
    const polls = await Poll.find({ targetStudents: studentId }).populate('classroom').sort({ createdAt: -1 });
    return polls.map(p => {
      const resp = (p.responses || []).find(r => (r.student?._id || r.student).toString() === sId);
      const pollObj = p.toObject();
      return {
        ...pollObj,
        attended: !!resp,
        myResponse: resp || null
      };
    });
  },

  createPoll: async (data) => {
    if (useMemory) {
      const room = memory.classrooms.find(c => c._id === data.classroom);
      
      let targets = [];
      if (!data.targetStudents || data.targetStudents.length === 0) {
        targets = memory.users.filter(u => u.role === 'student' && (u.classroom?._id || u.classroom) === data.classroom);
      } else {
        targets = data.targetStudents.map(id => memory.users.find(u => u._id === id));
      }

      const duration = parseInt(data.durationMinutes, 10) || 15;
      const endsAt = new Date(Date.now() + duration * 60 * 1000);

      const newPoll = {
        _id: 'poll_' + Date.now(),
        question: data.question,
        type: data.type || 'poll',
        classroom: room,
        options: data.options,
        targetStudents: targets,
        status: 'live',
        durationMinutes: duration,
        endsAt,
        responses: [],
        createdAt: new Date()
      };
      memory.polls.push(newPoll);
      return newPoll;
    }

    let actualTargetStudents = data.targetStudents;
    if (!data.targetStudents || data.targetStudents.length === 0) {
      const roomStudents = await User.find({ classroom: data.classroom, role: 'student' }).select('_id');
      actualTargetStudents = roomStudents.map(s => s._id);
    }

    const duration = parseInt(data.durationMinutes, 10) || 15;
    const endsAt = new Date(Date.now() + duration * 60 * 1000);

    const newPoll = new Poll({
      question: data.question,
      type: data.type || 'poll',
      classroom: data.classroom,
      options: data.options,
      targetStudents: actualTargetStudents,
      status: 'live',
      durationMinutes: duration,
      endsAt
    });
    await newPoll.save();
    return newPoll;
  },

  deletePoll: async (id) => {
    if (useMemory) {
      const idx = memory.polls.findIndex(p => p._id === id);
      if (idx !== -1) {
        memory.polls.splice(idx, 1);
        return true;
      }
      return false;
    }
    return Poll.findByIdAndDelete(id);
  },

  submitResponse: async (pollId, studentId, selectedOption) => {
    if (useMemory) {
      const poll = memory.polls.find(p => p._id === pollId);
      if (!poll) throw new Error('Poll not found.');
      if (poll.status !== 'live') throw new Error('Poll not active.');

      const isTarget = poll.targetStudents.some(s => {
        const sId = s && typeof s === 'object' ? s._id : s;
        return sId === studentId;
      });
      if (!isTarget) throw new Error('Student is not targeted by poll.');

      const already = poll.responses.some(r => (r.student?._id || r.student) === studentId);
      if (already) throw new Error('Already responded.');

      const studentObj = memory.users.find(u => u._id === studentId);
      const score = Math.random() > 0.3 ? 100 : 0;

      const newResponse = {
        student: studentObj,
        selectedOption,
        score,
        timestamp: new Date()
      };

      poll.responses.push(newResponse);
      return true;
    }

    const score = Math.random() > 0.3 ? 100 : 0;
    const updated = await Poll.findOneAndUpdate(
      {
        _id: pollId,
        status: 'live',
        targetStudents: studentId,
        'responses.student': { $ne: studentId }
      },
      {
        $push: {
          responses: {
            student: studentId,
            selectedOption,
            score,
            timestamp: new Date()
          }
        }
      },
      { new: true }
    );

    if (!updated) {
      const checkPoll = await Poll.findById(pollId);
      if (!checkPoll) throw new Error('Poll not found.');
      if (checkPoll.status !== 'live') throw new Error('Poll not active.');
      const isTarget = checkPoll.targetStudents.some(s => s.toString() === studentId.toString());
      if (!isTarget) throw new Error('Student is not targeted by poll.');
      const already = checkPoll.responses.some(r => r.student.toString() === studentId.toString());
      if (already) throw new Error('Already responded.');
      throw new Error('Failed to submit response.');
    }
    return true;
  },

  createMessage: async (data) => {
    if (useMemory) {
      const newMsg = {
        _id: 'msg_' + Date.now(),
        sender: data.sender,
        recipient: data.recipient,
        recipientGroup: data.recipientGroup || 'direct',
        senderFacultyId: data.senderFacultyId || null,
        content: data.content,
        timestamp: new Date(),
        createdAt: new Date()
      };
      memory.messages.push(newMsg);
      const senderObj = memory.users.find(u => u._id === data.sender);
      const recipientObj = data.recipient ? memory.users.find(u => u._id === data.recipient) : null;
      return { ...newMsg, sender: safeUser(senderObj), recipient: safeUser(recipientObj) };
    }
    const msg = new Message(data);
    await msg.save();
    return Message.findById(msg._id).populate('sender', '-password').populate('recipient', '-password');
  },

  getMessagesForUser: async (userId, role) => {
    if (useMemory) {
      let list = [];
      if (role === 'admin') {
        list = memory.messages.filter(m => {
          const senderId = (m.sender?._id || m.sender);
          const recId = (m.recipient?._id || m.recipient);
          return senderId === userId || (recId === userId && m.recipientGroup === 'direct');
        });
      } else if (role === 'faculty') {
        list = memory.messages.filter(m => {
          const senderId = (m.sender?._id || m.sender);
          const recId = (m.recipient?._id || m.recipient);
          // Faculty sees their own messages, direct messages to them, all_faculty broadcasts,
          // and their own mentees_only broadcasts
          return senderId === userId || 
                 (recId === userId && m.recipientGroup === 'direct') || 
                 m.recipientGroup === 'all_faculty' ||
                 (m.recipientGroup === 'mentees_only' && (m.senderFacultyId === userId || senderId === userId));
        });
      } else if (role === 'student') {
        // Find this student's mentor
        const student = memory.users.find(u => u._id === userId);
        const mentorId = student ? student.mentor : null;
        list = memory.messages.filter(m => {
          const senderId = (m.sender?._id || m.sender);
          const recId = (m.recipient?._id || m.recipient);
          const isMyMentorsBroadcast = m.recipientGroup === 'mentees_only' && 
            (m.senderFacultyId === mentorId || senderId === mentorId);
          return senderId === userId || 
                 (recId === userId && m.recipientGroup === 'direct') || 
                 m.recipientGroup === 'all_students' ||
                 isMyMentorsBroadcast;
        });
      }
      return list.map(m => {
        const senderObj = typeof m.sender === 'string' ? memory.users.find(u => u._id === m.sender) : m.sender;
        const recipientObj = typeof m.recipient === 'string' ? memory.users.find(u => u._id === m.recipient) : m.recipient;
        return { ...m, sender: safeUser(senderObj), recipient: safeUser(recipientObj) };
      });
    }

    let query = {};
    if (role === 'admin') {
      query = {
        $or: [
          { sender: userId },
          { recipient: userId, recipientGroup: 'direct' }
        ]
      };
    } else if (role === 'faculty') {
      query = {
        $or: [
          { sender: userId },
          { recipient: userId, recipientGroup: 'direct' },
          { recipientGroup: 'all_faculty' }
        ]
      };
    } else if (role === 'student') {
      query = {
        $or: [
          { sender: userId },
          { recipient: userId, recipientGroup: 'direct' },
          { recipientGroup: 'all_students' }
        ]
      };
    }
    return Message.find(query).populate('sender', '-password').populate('recipient', '-password').sort({ createdAt: 1 });
  }
};

module.exports = db;
