require('dotenv').config();
const mongoose = require('mongoose');
const User = require('../models/User');

const MONGO_URI = process.env.MONGO_URI;
const BOOTSTRAP_ADMIN_SEC_ID = process.env.BOOTSTRAP_ADMIN_SEC_ID;
const BOOTSTRAP_ADMIN_NAME = process.env.BOOTSTRAP_ADMIN_NAME;
const BOOTSTRAP_ADMIN_EMAIL = process.env.BOOTSTRAP_ADMIN_EMAIL;
const BOOTSTRAP_ADMIN_PASSWORD = process.env.BOOTSTRAP_ADMIN_PASSWORD;

async function bootstrapAdmin() {
  if (!MONGO_URI) {
    console.error('ERROR: Missing required environment variable: MONGO_URI');
    process.exit(1);
  }
  if (!BOOTSTRAP_ADMIN_SEC_ID || !BOOTSTRAP_ADMIN_SEC_ID.trim()) {
    console.error('ERROR: Missing required environment variable: BOOTSTRAP_ADMIN_SEC_ID');
    process.exit(1);
  }
  if (!BOOTSTRAP_ADMIN_NAME || !BOOTSTRAP_ADMIN_NAME.trim()) {
    console.error('ERROR: Missing required environment variable: BOOTSTRAP_ADMIN_NAME');
    process.exit(1);
  }
  if (!BOOTSTRAP_ADMIN_PASSWORD) {
    console.error('ERROR: Missing required environment variable: BOOTSTRAP_ADMIN_PASSWORD (no fallback allowed)');
    process.exit(1);
  }
  if (BOOTSTRAP_ADMIN_PASSWORD.length < 12) {
    console.error('ERROR: BOOTSTRAP_ADMIN_PASSWORD must be at least 12 characters long');
    process.exit(1);
  }

  try {
    mongoose.set('bufferCommands', false);
    await mongoose.connect(MONGO_URI, { serverSelectionTimeoutMS: 5000 });
    console.log('Connected to MongoDB.');

    // Check if any admin account already exists
    const existingAdmin = await User.findOne({ role: 'admin' });
    if (existingAdmin) {
      console.error('ERROR: An administrator account already exists. Refusing bootstrap.');
      await mongoose.connection.close();
      process.exit(1);
    }

    const adminUser = new User({
      secId: BOOTSTRAP_ADMIN_SEC_ID.trim(),
      name: BOOTSTRAP_ADMIN_NAME.trim(),
      email: BOOTSTRAP_ADMIN_EMAIL ? BOOTSTRAP_ADMIN_EMAIL.trim() : undefined,
      password: BOOTSTRAP_ADMIN_PASSWORD,
      role: 'admin',
      department: 'Administration',
      status: 'Offline'
    });

    await adminUser.save();
    console.log(`Successfully created bootstrap administrator account (SEC ID: ${BOOTSTRAP_ADMIN_SEC_ID.trim()}).`);
    await mongoose.connection.close();
    process.exit(0);
  } catch (err) {
    console.error('ERROR: Bootstrap administrator creation failed:', err.message);
    try {
      await mongoose.connection.close();
    } catch (closeErr) {}
    process.exit(1);
  }
}

bootstrapAdmin();
