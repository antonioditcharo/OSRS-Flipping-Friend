const Database = require('better-sqlite3');
const os = require('os');
const path = require('path');
const dbPath = path.join(os.homedir(), '.runelite', 'osrs-flipping-friend', 'companion', 'flipping-friend.db');
const db = new Database(dbPath, { readonly: true });
console.log(db.prepare("SELECT payload FROM event_log WHERE event_type='BOUGHT' LIMIT 1").get()?.payload);
console.log(db.prepare("SELECT payload FROM portfolio_plan ORDER BY created_at DESC LIMIT 1").get()?.payload);
