const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');

const UserSchema = new mongoose.Schema({
  email: { 
    type: String, 
    unique: true, 
    sparse: true, 
    lowercase: true,
    trim: true
  },
  secId: { 
    type: String, 
    unique: true, 
    required: true,
    trim: true
  }, // SEC ID e.g. ADMIN001 or STUDENT_TEST_101 or S-0421
  password: { 
    type: String, 
    required: true 
  },
  role: { 
    type: String, 
    enum: ['admin', 'faculty', 'student'], 
    required: true 
  },
  name: { 
    type: String, 
    required: true 
  },
  gender: {
    type: String,
    enum: ['Male', 'Female', 'Other'],
    default: 'Male'
  },
  department: { 
    type: String, 
    default: 'Computer Science' 
  },
  year: { 
    type: String 
  }, // e.g. "Year 3", "2024"
  section: { 
    type: String 
  }, // e.g. "A"
  classroom: { 
    type: mongoose.Schema.Types.ObjectId, 
    ref: 'Classroom', 
    default: null 
  },
  // Faculty only: list of student mentees
  mentees: [{ 
    type: mongoose.Schema.Types.ObjectId, 
    ref: 'User' 
  }],
  // Student only: assigned faculty mentor
  mentor: { 
    type: mongoose.Schema.Types.ObjectId, 
    ref: 'User', 
    default: null 
  },
  description: { 
    type: String 
  },
  lastReadMessagesAt: { 
    type: Date, 
    default: new Date(0) 
  },
  lastActive: { 
    type: Date, 
    default: Date.now 
  },
  status: { 
    type: String, 
    enum: ['In Session', 'Offline', 'Active'], 
    default: 'Offline' 
  },
  profilePhoto: {
    type: String,
    default: ''
  }
}, { timestamps: true });

// Pre-save hook to hash passwords
UserSchema.pre('save', async function(next) {
  if (!this.isModified('password')) return next();
  try {
    const salt = await bcrypt.genSalt(10);
    this.password = await bcrypt.hash(this.password, salt);
    next();
  } catch (err) {
    next(err);
  }
});

// Helper method to compare passwords
UserSchema.methods.comparePassword = async function(candidatePassword) {
  return bcrypt.compare(candidatePassword, this.password);
};

module.exports = mongoose.model('User', UserSchema);
