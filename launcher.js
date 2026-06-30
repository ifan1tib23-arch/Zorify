const cp = require('child_process');
const fs = require('fs');
const path = require('path');

const logFile = path.join(__dirname, 'server.log');
const out = fs.openSync(logFile, 'a');
const err = fs.openSync(logFile, 'a');

console.log('Launching server.js...');

const child = cp.spawn('node', [path.join(__dirname, 'server.js')], {
  detached: true,
  stdio: [ 'ignore', out, err ]
});

child.unref();
console.log('Server launched in background successfully! PID:', child.pid);
process.exit(0);
