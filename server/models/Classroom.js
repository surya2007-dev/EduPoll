const mongoose = require('mongoose');

const PendingFacultyRequestSchema = new mongoose.Schema({
  faculty: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  requestedAt: {
    type: Date,
    default: Date.now
  }
}, { _id: false });

const ClassroomSchema = new mongoose.Schema({
  name: { 
    type: String, 
    required: true,
    trim: true
  }, // e.g. "SEC25IT-A" or "CS-101A"
  subject: { 
    type: String, 
    required: true,
    trim: true
  }, // e.g. "Advanced Data Structures"
  department: { 
    type: String, 
    required: true,
    trim: true
  }, // e.g. "Information Technology"
  year: { 
    type: String, 
    required: true 
  }, // e.g. "Second Year"
  semester: { 
    type: String, 
    required: true 
  }, // e.g. "Semester 3"
  section: {
    type: String,
    default: 'A',
    trim: true
  }, // e.g. "A"
  code: { 
    type: String, 
    unique: true, 
    required: true,
    trim: true
  }, // Internal system code e.g. "CLASS002"
  joinCode: {
    type: String,
    unique: true,
    sparse: true,
    trim: true
  }, // Public join code e.g. "IT-A7K92"
  students: [{
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User'
  }],
  facultyMembers: [{
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User'
  }],
  pendingFacultyRequests: [PendingFacultyRequestSchema],
  studentCapacity: {
    type: Number,
    default: 63
  },
  facultyCapacity: {
    type: Number,
    default: 10
  },
  isActive: {
    type: Boolean,
    default: true
  },
  createdBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    default: null
  },
  studentsEnrolledCount: { 
    type: Number, 
    default: 0 
  }
}, { timestamps: true });

// Pre-save hook to keep studentsEnrolledCount synchronized with students array
ClassroomSchema.pre('save', function(next) {
  if (Array.isArray(this.students)) {
    this.studentsEnrolledCount = this.students.length;
  }
  next();
});

module.exports = mongoose.model('Classroom', ClassroomSchema);
