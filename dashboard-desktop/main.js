const { app, BrowserWindow } = require('electron');
const { spawn } = require('child_process');
const path = require('path');

let apiProcess;
let mainWindow;

function startApiServer() {
    console.log('Starting dashboard API server...');
    const apiPath = path.join(__dirname, '..', 'dashboard-api');
    
    // Use shell: true to avoid Issues with node command on Windows
    apiProcess = spawn('node', ['index.js'], {
        cwd: apiPath,
        stdio: 'inherit',
        shell: true
    });

    apiProcess.on('error', (err) => {
        console.error('Failed to start API process:', err);
    });
    
    apiProcess.on('close', (code) => {
        console.log(`API process exited with code ${code}`);
    });
}

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1280,
        height: 800,
        minWidth: 1024,
        minHeight: 768,
        title: 'Flipping Friend - AI Learning Monitor',
        icon: path.join(__dirname, 'icon.png'), // Optional icon if you add one later
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true
        }
    });

    // Remove the default menu bar
    mainWindow.setMenuBarVisibility(false);

    const targetUrl = 'http://localhost:3001';
    
    function loadUrlWithRetry(retries = 10) {
        mainWindow.loadURL(targetUrl).catch((err) => {
            if (retries > 0) {
                console.log(`Failed to load ${targetUrl}, retrying in 1s... (${retries} retries left)`);
                setTimeout(() => loadUrlWithRetry(retries - 1), 1000);
            } else {
                console.error('Failed to load URL after retries:', err);
            }
        });
    }

    // Give the API an initial 1 second, then retry if it isn't ready
    setTimeout(() => loadUrlWithRetry(), 1000);
}

app.whenReady().then(() => {
    startApiServer();
    createWindow();

    app.on('activate', function () {
        if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
});

app.on('window-all-closed', function () {
    if (process.platform !== 'darwin') app.quit();
});

// Clean up child process when electron exits
app.on('before-quit', () => {
    if (apiProcess) {
        console.log('Killing API process...');
        // On Windows, child_process.kill doesn't always kill children of the shell
        // We can use taskkill to be sure
        const spawnSync = require('child_process').spawnSync;
        spawnSync('taskkill', ['/pid', apiProcess.pid, '/f', '/t']);
    }
});
