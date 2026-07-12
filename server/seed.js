const mongoose = require('mongoose');
const User = require('./models/User');
const Classroom = require('./models/Classroom');
const Poll = require('./models/Poll');
const Message = require('./models/Message');
require('dotenv').config();

if (process.env.NODE_ENV === 'production') {
  console.error('ERROR: Demo seed script is disabled in production.');
  process.exit(1);
}

const MONGO_URI = process.env.MONGO_URI || 'mongodb://127.0.0.1:27017/edupoll';

async function seedData() {
  try {
    console.log('Connecting to database...');
    await mongoose.connect(MONGO_URI);
    console.log('Connected to MongoDB.');

    // Clear existing data
    console.log('Clearing existing collections...');
    await User.deleteMany({});
    await Classroom.deleteMany({});
    await Poll.deleteMany({});
    await Message.deleteMany({});
    console.log('Cleared existing collections.');

    // Create default Administrator
    console.log('Seeding default administrator user account...');
    const adminUser = new User({
      name: 'Principal Admin',
      email: 'admin@university.edu',
      secId: 'ADMINISTRATOR',
      password: 'admin123',
      role: 'admin',
      department: 'Administration',
      status: 'Active'
    });

    await adminUser.save();
    console.log('Administrator user account seeded successfully.');

    console.log('Database seeding completed successfully.');
    await mongoose.connection.close();
    process.exit(0);
  } catch (err) {
    console.error('Database seeding failed:', err);
    process.exit(1);
  }
}

seedData();
