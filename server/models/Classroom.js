const mongoose = require('mongoose');

const ClassroomSchema = new mongoose.Schema({
  name: { 
    type: String, 
    required: true,
    trim: true
  }, // e.g. "CS-101A"
  subject: { 
    type: String, 
    required: true,
    trim: true
  }, // e.g. "Advanced Data Structures"
  department: { 
    type: String, 
    required: true,
    trim: true
  }, // e.g. "CS Dept."
  year: { 
    type: String, 
    required: true 
  }, // e.g. "Year 3"
  semester: { 
    type: String, 
    required: true 
  }, // e.g. "Fall 2024"
  code: { 
    type: String, 
    unique: true, 
    required: true,
    trim: true
  }, // e.g. "CLASS002"
  studentsEnrolledCount: { 
    type: Number, 
    default: 0 
  }
}, { timestamps: true });

module.exports = mongoose.model('Classroom', ClassroomSchema);
