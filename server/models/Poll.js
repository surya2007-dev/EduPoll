const mongoose = require('mongoose');

const ResponseSchema = new mongoose.Schema({
  student: { 
    type: mongoose.Schema.Types.ObjectId, 
    ref: 'User', 
    required: true 
  },
  selectedOption: { 
    type: String, 
    required: true 
  },
  score: { 
    type: Number, 
    default: null 
  }, // score out of 100 for graded quizzes
  timestamp: { 
    type: Date, 
    default: Date.now 
  }
});

const PollSchema = new mongoose.Schema({
  question: { 
    type: String, 
    required: true,
    trim: true
  },
  type: { 
    type: String, 
    enum: ['poll', 'activity'], 
    default: 'poll' 
  },
  classroom: { 
    type: mongoose.Schema.Types.ObjectId, 
    ref: 'Classroom', 
    required: true 
  },
  options: {
    type: [String],
    validate: [arrayMinSize, 'A poll must have at least 2 options']
  },
  targetStudents: [{ 
    type: mongoose.Schema.Types.ObjectId, 
    ref: 'User' 
  }],
  responses: [ResponseSchema],
  status: { 
    type: String, 
    enum: ['live', 'scheduled', 'completed'], 
    default: 'live' 
  },
  durationMinutes: { 
    type: Number, 
    default: 45 
  },
  endsAt: { 
    type: Date 
  }
}, { timestamps: true });

function arrayMinSize(val) {
  return val.length >= 2;
}

module.exports = mongoose.model('Poll', PollSchema);
