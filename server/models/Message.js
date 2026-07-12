const mongoose = require('mongoose');

const MessageSchema = new mongoose.Schema({
  sender: { 
    type: mongoose.Schema.Types.ObjectId, 
    ref: 'User', 
    required: true 
  },
  recipient: { 
    type: mongoose.Schema.Types.ObjectId, 
    ref: 'User' 
  }, // if null, it's a group broadcast
  recipientGroup: { 
    type: String, 
    enum: ['all_faculty', 'all_students', 'mentees_only', 'direct'], 
    default: 'direct' 
  },
  // For mentees_only messages: stores the faculty's ID for efficient lookup
  senderFacultyId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    default: null
  },
  content: { 
    type: String, 
    required: true,
    trim: true
  },
  timestamp: { 
    type: Date, 
    default: Date.now 
  }
}, { timestamps: true });

module.exports = mongoose.model('Message', MessageSchema);
