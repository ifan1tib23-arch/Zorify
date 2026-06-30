const http = require('http');
const fs = require('fs');
const path = require('path');

let PORT = 3000;
const targetPorts = [];
if (process.env.PORT) targetPorts.push(parseInt(process.env.PORT, 10));
if (process.env.DEFAULT_APP_PORT) targetPorts.push(parseInt(process.env.DEFAULT_APP_PORT, 10));
targetPorts.push(3000);
targetPorts.push(8080);
const uniquePorts = [...new Set(targetPorts)];

const COMPILED_APK_PATH = path.join(__dirname, '.build-outputs', 'app-debug.apk');
const ASSETS_APK_PATH = path.join(__dirname, 'assets', 'Zorify.apk');

// Persistent JSON file paths for Sync server
const DB_PATHS = [
  path.join(__dirname, 'zorify_sync_data.json'),
  path.join('/tmp', 'zorify_sync_data.json')
];

let syncDb = {
  users: {
    "zora": {
      username: "zora",
      avatarColorHex: "#1DB954",
      customSongs: [],
      likedSongs: [],
      playlists: [],
      playHistory: []
    }
  }
};

// Load database from disk if available
function loadDb() {
  for (const dbPath of DB_PATHS) {
    try {
      if (fs.existsSync(dbPath)) {
        const raw = fs.readFileSync(dbPath, 'utf8');
        syncDb = JSON.parse(raw);
        console.log(`Successfully loaded database from ${dbPath}`);
        return;
      }
    } catch (e) {
      console.warn(`Could not read database from ${dbPath}: ${e.message}`);
    }
  }
}

// Save database to disk
function saveDb() {
  for (const dbPath of DB_PATHS) {
    try {
      // Ensure folder exists
      const dir = path.dirname(dbPath);
      if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
      }
      fs.writeFileSync(dbPath, JSON.stringify(syncDb, null, 2), 'utf8');
      console.log(`Successfully saved database to ${dbPath}`);
      return;
    } catch (e) {
      console.warn(`Could not write database to ${dbPath}: ${e.message}`);
    }
  }
}

loadDb();

const server = http.createServer((req, res) => {
  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = parsedUrl.pathname;

  console.log(`${new Date().toISOString()} - Request: ${req.method} ${req.url}`);

  // Handle CORS
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(200);
    res.end();
    return;
  }

  // --- DOWNLOAD APK ENDPOINT ---
  if (pathname === '/Zorify.apk' || pathname === '/download') {
    let apkPath = COMPILED_APK_PATH;
    if (!fs.existsSync(apkPath)) {
      apkPath = ASSETS_APK_PATH;
    }

    if (!fs.existsSync(apkPath)) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Error: APK file not found. Please compile the app first.');
      return;
    }

    const stat = fs.statSync(apkPath);
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Disposition': 'attachment; filename="Zorify.apk"',
      'Content-Length': stat.size,
      'Cache-Control': 'no-cache, no-store, must-revalidate',
      'Pragma': 'no-cache',
      'Expires': '0'
    });

    const readStream = fs.createReadStream(apkPath);
    readStream.pipe(res);
    return;
  }

  // --- SYNC API ENDPOINTS ---
  if (pathname === '/api/sync/pull') {
    const username = parsedUrl.searchParams.get('username');
    if (!username) {
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, message: "Username parameter is required" }));
      return;
    }

    const lowerUser = username.toLowerCase();
    const userData = syncDb.users[lowerUser] || {
      username: username,
      avatarColorHex: "#1DB954",
      customSongs: [],
      likedSongs: [],
      playlists: [],
      playHistory: []
    };

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      success: true,
      username: userData.username,
      avatarColorHex: userData.avatarColorHex,
      customSongs: userData.customSongs || [],
      likedSongs: userData.likedSongs || [],
      playlists: userData.playlists || [],
      playHistory: userData.playHistory || []
    }));
    return;
  }

  if (pathname === '/api/sync/push' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => {
      body += chunk.toString();
    });
    req.on('end', () => {
      try {
        const payload = JSON.parse(body);
        const username = payload.username;
        if (!username) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ success: false, message: "Username is required for sync" }));
          return;
        }

        const lowerUser = username.toLowerCase();
        
        // Merge strategy: Server keeps set of unique custom songs, playlists, liked songs, history
        let userRecord = syncDb.users[lowerUser];
        if (!userRecord) {
          userRecord = {
            username: username,
            avatarColorHex: payload.avatarColorHex || "#1DB954",
            customSongs: [],
            likedSongs: [],
            playlists: [],
            playHistory: []
          };
          syncDb.users[lowerUser] = userRecord;
        }

        // 1. Merge Custom Songs
        const clientCustomSongs = payload.customSongs || [];
        const serverCustomSongs = userRecord.customSongs || [];
        
        const mergedCustomSongs = [...serverCustomSongs];
        clientCustomSongs.forEach(clientSong => {
          const match = mergedCustomSongs.find(s => 
            s.title.toLowerCase() === clientSong.title.toLowerCase() &&
            s.artist.toLowerCase() === clientSong.artist.toLowerCase()
          );
          if (!match) {
            mergedCustomSongs.push(clientSong);
          }
        });
        userRecord.customSongs = mergedCustomSongs;

        // 2. Merge Liked Songs
        const clientLikedSongs = payload.likedSongs || [];
        const serverLikedSongs = userRecord.likedSongs || [];
        
        const mergedLikedSongs = [...serverLikedSongs];
        clientLikedSongs.forEach(clientSong => {
          const match = mergedLikedSongs.find(s => 
            s.title.toLowerCase() === clientSong.title.toLowerCase() &&
            s.artist.toLowerCase() === clientSong.artist.toLowerCase()
          );
          if (!match) {
            mergedLikedSongs.push(clientSong);
          }
        });
        userRecord.likedSongs = mergedLikedSongs;

        // 3. Merge Playlists
        const clientPlaylists = payload.playlists || [];
        const serverPlaylists = userRecord.playlists || [];
        
        const mergedPlaylists = [...serverPlaylists];
        clientPlaylists.forEach(clientPl => {
          const serverPlIndex = mergedPlaylists.findIndex(p => p.name.toLowerCase() === clientPl.name.toLowerCase());
          if (serverPlIndex === -1) {
            mergedPlaylists.push(clientPl);
          } else {
            // Merge songs in existing playlists
            const serverPl = mergedPlaylists[serverPlIndex];
            const serverSongs = serverPl.songs || [];
            const clientSongs = clientPl.songs || [];
            
            clientSongs.forEach(cs => {
              const hasSong = serverSongs.some(ss => 
                ss.title.toLowerCase() === cs.title.toLowerCase() &&
                ss.artist.toLowerCase() === cs.artist.toLowerCase()
              );
              if (!hasSong) {
                serverSongs.push(cs);
              }
            });
            serverPl.songs = serverSongs;
          }
        });
        userRecord.playlists = mergedPlaylists;

        // 4. Merge Play History
        const clientHistory = payload.playHistory || [];
        const serverHistory = userRecord.playHistory || [];
        
        const mergedHistory = [...serverHistory];
        clientHistory.forEach(clientSong => {
          const match = mergedHistory.find(s => 
            s.title.toLowerCase() === clientSong.title.toLowerCase() &&
            s.artist.toLowerCase() === clientSong.artist.toLowerCase()
          );
          if (!match) {
            mergedHistory.push(clientSong);
          }
        });
        userRecord.playHistory = mergedHistory;

        // Save DB to disk asynchronously
        saveDb();

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          success: true,
          username: userRecord.username,
          avatarColorHex: userRecord.avatarColorHex,
          customSongs: userRecord.customSongs,
          likedSongs: userRecord.likedSongs,
          playlists: userRecord.playlists,
          playHistory: userRecord.playHistory
        }));
      } catch (err) {
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, message: err.message }));
      }
    });
    return;
  }

  // --- SERVE GORGEOUS WEB PLAYER SPA ---
  const protocol = 'https';
  const host = req.headers['x-forwarded-host'] || req.headers['host'] || 'localhost:8080';
  const downloadApkUrl = `${protocol}://${host}/Zorify.apk`;

  const webPlayerHtml = `<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Zorify Web Player • Premium Music Experience</title>
    <!-- Google Fonts - Plus Jakarta Sans -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    <!-- Font Awesome Icons -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <style>
        :root {
            --spotify-green: #1db954;
            --spotify-green-hover: #1ed760;
            --bg-black: #000000;
            --bg-dark-grey: #121212;
            --bg-light-grey: #181818;
            --bg-hover-grey: #282828;
            --text-white: #ffffff;
            --text-grey: #b3b3b3;
            --transition-speed: 0.25s;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            scrollbar-width: thin;
            scrollbar-color: var(--bg-hover-grey) transparent;
        }

        /* Custom Scrollbar */
        *::-webkit-scrollbar {
            width: 8px;
            height: 8px;
        }
        *::-webkit-scrollbar-track {
            background: transparent;
        }
        *::-webkit-scrollbar-thumb {
            background-color: #3e3e3e;
            border-radius: 4px;
        }

        body {
            background-color: var(--bg-black);
            color: var(--text-white);
            height: 100vh;
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }

        /* --- LAYOUT CONTAINER --- */
        .app-container {
            flex: 1;
            display: flex;
            overflow: hidden;
            width: 100%;
        }

        /* --- SIDEBAR --- */
        .sidebar {
            width: 280px;
            background-color: var(--bg-black);
            padding: 24px 8px 8px 8px;
            display: flex;
            flex-direction: column;
            gap: 12px;
            flex-shrink: 0;
        }

        .sidebar-panel {
            background-color: var(--bg-dark-grey);
            border-radius: 8px;
            padding: 16px;
        }

        .logo-container {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 0 12px 16px 12px;
            color: var(--spotify-green);
            text-decoration: none;
        }

        .logo-container i {
            font-size: 32px;
            text-shadow: 0 0 15px rgba(29, 185, 84, 0.5);
        }

        .logo-text {
            font-size: 24px;
            font-weight: 800;
            letter-spacing: -0.5px;
            color: var(--text-white);
        }

        .nav-menu {
            list-style: none;
            display: flex;
            flex-direction: column;
            gap: 14px;
        }

        .nav-item {
            display: flex;
            align-items: center;
            gap: 16px;
            color: var(--text-grey);
            text-decoration: none;
            font-weight: 700;
            font-size: 14px;
            padding: 8px 12px;
            border-radius: 4px;
            cursor: pointer;
            transition: color var(--transition-speed), background-color var(--transition-speed);
        }

        .nav-item:hover, .nav-item.active {
            color: var(--text-white);
            background-color: var(--bg-hover-grey);
        }

        .nav-item.active i {
            color: var(--spotify-green);
        }

        .sidebar-library {
            flex: 1;
            display: flex;
            flex-direction: column;
            overflow-y: auto;
        }

        .library-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            color: var(--text-grey);
            padding: 8px 12px;
            font-weight: 700;
            font-size: 14px;
        }

        .library-header-title {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .create-playlist-btn {
            background: transparent;
            border: none;
            color: var(--text-grey);
            cursor: pointer;
            font-size: 16px;
            width: 32px;
            height: 32px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: color var(--transition-speed), background-color var(--transition-speed);
        }

        .create-playlist-btn:hover {
            color: var(--text-white);
            background-color: var(--bg-hover-grey);
        }

        .playlist-list-container {
            margin-top: 12px;
            display: flex;
            flex-direction: column;
            gap: 8px;
            overflow-y: auto;
            flex: 1;
        }

        .playlist-item {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 8px;
            border-radius: 6px;
            cursor: pointer;
            transition: background-color var(--transition-speed);
        }

        .playlist-item:hover {
            background-color: var(--bg-hover-grey);
        }

        .playlist-item img {
            width: 48px;
            height: 48px;
            border-radius: 4px;
            object-fit: cover;
            background-color: var(--bg-hover-grey);
        }

        .playlist-item-info {
            display: flex;
            flex-direction: column;
            gap: 4px;
            overflow: hidden;
        }

        .playlist-item-name {
            font-size: 13px;
            font-weight: 600;
            color: var(--text-white);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .playlist-item-desc {
            font-size: 11px;
            color: var(--text-grey);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        /* --- MAIN VIEWPORT --- */
        .main-viewport {
            flex: 1;
            background: linear-gradient(180deg, #1f2d22 0%, var(--bg-dark-grey) 350px);
            margin: 8px 8px 8px 0;
            border-radius: 8px;
            display: flex;
            flex-direction: column;
            overflow: hidden;
            position: relative;
        }

        .main-header {
            height: 64px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0 32px;
            background-color: rgba(0, 0, 0, 0.4);
            backdrop-filter: blur(10px);
            z-index: 10;
        }

        .nav-arrows {
            display: flex;
            gap: 10px;
        }

        .arrow-btn {
            width: 32px;
            height: 32px;
            background-color: rgba(0, 0, 0, 0.7);
            border: none;
            color: var(--text-grey);
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: not-allowed;
        }

        .header-auth {
            display: flex;
            align-items: center;
            gap: 16px;
        }

        .auth-profile {
            display: flex;
            align-items: center;
            gap: 10px;
            background-color: rgba(0, 0, 0, 0.7);
            padding: 4px 12px 4px 4px;
            border-radius: 20px;
            cursor: pointer;
            transition: transform var(--transition-speed);
        }

        .auth-profile:hover {
            transform: scale(1.03);
        }

        .auth-avatar {
            width: 28px;
            height: 28px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: bold;
            color: var(--bg-black);
            font-size: 14px;
        }

        .auth-username {
            font-size: 13px;
            font-weight: 700;
        }

        .btn-premium {
            background-color: var(--text-white);
            color: var(--bg-black);
            padding: 8px 16px;
            border-radius: 20px;
            border: none;
            font-weight: 700;
            font-size: 13px;
            cursor: pointer;
            text-decoration: none;
            transition: transform var(--transition-speed);
        }

        .btn-premium:hover {
            transform: scale(1.05);
        }

        .main-content {
            flex: 1;
            padding: 24px 32px;
            overflow-y: auto;
        }

        /* --- HOME SECTION --- */
        .mobile-greeting-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 24px;
            width: 100%;
        }

        .greeting-text-container {
            display: flex;
            flex-direction: column;
            gap: 2px;
        }

        .home-greeting {
            font-size: 32px;
            font-weight: 800;
            letter-spacing: -1px;
            margin-bottom: 0;
        }

        .home-subgreeting {
            font-size: 14px;
            color: var(--text-grey);
            font-weight: 500;
        }

        .mobile-greeting-header .header-actions {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .header-action-btn {
            background: rgba(255, 255, 255, 0.08);
            border: none;
            color: var(--text-white);
            width: 40px;
            height: 40px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
            cursor: pointer;
            transition: background-color 0.2s;
        }

        .header-action-btn:hover {
            background-color: rgba(255, 255, 255, 0.15);
        }

        .header-profile-avatar {
            width: 40px;
            height: 40px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 800;
            color: black;
            cursor: pointer;
            background-color: var(--spotify-green);
            font-size: 16px;
            box-shadow: 0 4px 10px rgba(0,0,0,0.3);
        }

        /* On desktop, hide header actions inside mobile-greeting-header as they are in top main-header */
        @media (min-width: 769px) {
            .mobile-greeting-header .header-actions {
                display: none !important;
            }
        }

        .quick-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
            gap: 16px;
            margin-bottom: 32px;
        }

        .quick-card {
            background-color: rgba(255, 255, 255, 0.05);
            border-radius: 6px;
            display: flex;
            align-items: center;
            overflow: hidden;
            cursor: pointer;
            transition: background-color var(--transition-speed);
            position: relative;
        }

        .quick-card:hover {
            background-color: rgba(255, 255, 255, 0.12);
        }

        .quick-card img {
            width: 80px;
            height: 80px;
            object-fit: cover;
        }

        .quick-card-title {
            padding: 16px;
            font-weight: 700;
            font-size: 14px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            flex: 1;
        }

        .quick-card .play-overlay-btn {
            position: absolute;
            right: 16px;
            width: 48px;
            height: 48px;
            background-color: var(--spotify-green);
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            color: var(--bg-black);
            font-size: 18px;
            opacity: 0;
            transform: translateY(10px);
            transition: opacity var(--transition-speed), transform var(--transition-speed);
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
        }

        .quick-card:hover .play-overlay-btn {
            opacity: 1;
            transform: translateY(0);
        }

        .section-title {
            font-size: 24px;
            font-weight: 700;
            margin-bottom: 16px;
        }

        /* Song List Table style */
        .song-table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 16px;
        }

        .song-table th {
            text-align: left;
            color: var(--text-grey);
            font-size: 12px;
            font-weight: 600;
            border-bottom: 1px solid rgba(255, 255, 255, 0.1);
            padding: 12px 16px;
            text-transform: uppercase;
        }

        .song-table tr {
            border-radius: 4px;
            transition: background-color var(--transition-speed);
            cursor: pointer;
        }

        .song-table tr:hover {
            background-color: rgba(255, 255, 255, 0.08);
        }

        .song-table td {
            padding: 10px 16px;
            font-size: 14px;
            color: var(--text-grey);
            vertical-align: middle;
        }

        .song-table td.song-title-col {
            color: var(--text-white);
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 16px;
        }

        .song-table td.song-title-col img {
            width: 40px;
            height: 40px;
            border-radius: 4px;
            object-fit: cover;
        }

        .song-details-mini {
            display: flex;
            flex-direction: column;
            gap: 4px;
        }

        .song-details-title {
            font-size: 14px;
            color: var(--text-white);
        }

        .song-details-artist {
            font-size: 12px;
            color: var(--text-grey);
        }

        .song-table tr:hover td.song-title-col .song-details-title {
            color: var(--spotify-green);
        }

        .song-index {
            width: 40px;
            text-align: center;
        }

        .song-liked-col i {
            cursor: pointer;
            transition: color var(--transition-speed);
        }

        .song-liked-col i.active {
            color: var(--spotify-green);
        }

        .song-actions-col {
            position: relative;
        }

        .action-menu-trigger {
            background: transparent;
            border: none;
            color: var(--text-grey);
            padding: 4px 8px;
            cursor: pointer;
            border-radius: 4px;
        }

        .action-menu-trigger:hover {
            color: var(--text-white);
        }

        .dropdown-menu {
            position: absolute;
            right: 0;
            top: 30px;
            background-color: #282828;
            border-radius: 4px;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
            display: none;
            flex-direction: column;
            z-index: 100;
            width: 180px;
            border: 1px solid rgba(255, 255, 255, 0.05);
        }

        .dropdown-item {
            padding: 10px 16px;
            font-size: 13px;
            color: var(--text-white);
            cursor: pointer;
            transition: background-color var(--transition-speed);
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .dropdown-item:hover {
            background-color: rgba(255, 255, 255, 0.1);
        }

        /* --- LYRICS OVERLAY PANELS --- */
        .lyrics-panel-container {
            position: absolute;
            top: 0;
            right: 0;
            width: 100%;
            height: calc(100% - 64px);
            margin-top: 64px;
            background-color: rgba(18, 18, 18, 0.95);
            z-index: 9;
            display: none;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 40px;
            text-align: center;
            overflow-y: auto;
            backdrop-filter: blur(20px);
            border-top: 1px solid rgba(255, 255, 255, 0.05);
        }

        .lyrics-title {
            font-size: 28px;
            font-weight: 800;
            margin-bottom: 8px;
        }

        .lyrics-artist {
            font-size: 16px;
            color: var(--text-grey);
            margin-bottom: 40px;
        }

        .lyrics-scroll-box {
            flex: 1;
            width: 100%;
            max-width: 600px;
            overflow-y: auto;
            display: flex;
            flex-direction: column;
            gap: 24px;
            padding-bottom: 100px;
            scroll-behavior: smooth;
        }

        .lyric-line {
            font-size: 22px;
            font-weight: 700;
            color: rgba(255, 255, 255, 0.3);
            cursor: pointer;
            transition: color 0.2s ease, transform 0.2s ease;
        }

        .lyric-line.active {
            color: var(--spotify-green);
            transform: scale(1.08);
            text-shadow: 0 0 15px rgba(29, 185, 84, 0.4);
        }

        /* --- WEB PLAYER BOTTOM BAR --- */
        .bottom-player-bar {
            height: 90px;
            background-color: #181818;
            border-top: 1px solid #282828;
            padding: 0 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            z-index: 20;
        }

        /* Left side - Track Info */
        .player-track-info {
            display: flex;
            align-items: center;
            gap: 14px;
            width: 30%;
            min-width: 180px;
        }

        .player-cover-art-container {
            position: relative;
            width: 56px;
            height: 56px;
        }

        .player-cover-art {
            width: 56px;
            height: 56px;
            border-radius: 4px;
            object-fit: cover;
            background-color: var(--bg-hover-grey);
            box-shadow: 0 4px 10px rgba(0, 0, 0, 0.3);
            transition: transform 0.5s ease;
        }

        .player-cover-art.playing {
            animation: spin 20s linear infinite;
            border-radius: 50%;
        }

        @keyframes spin {
            100% { transform: rotate(360deg); }
        }

        .player-track-details {
            display: flex;
            flex-direction: column;
            gap: 4px;
            overflow: hidden;
        }

        .player-track-title {
            font-size: 14px;
            font-weight: 600;
            color: var(--text-white);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .player-track-artist {
            font-size: 11px;
            color: var(--text-grey);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .player-track-like-btn {
            background: transparent;
            border: none;
            color: var(--text-grey);
            cursor: pointer;
            font-size: 16px;
            padding: 4px;
            transition: color var(--transition-speed);
        }

        .player-track-like-btn.liked {
            color: var(--spotify-green);
        }

        /* Center - Playback Controls */
        .player-controls-container {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 8px;
            width: 40%;
            max-width: 500px;
        }

        .control-buttons {
            display: flex;
            align-items: center;
            gap: 20px;
        }

        .control-btn {
            background: transparent;
            border: none;
            color: var(--text-grey);
            cursor: pointer;
            font-size: 16px;
            width: 32px;
            height: 32px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: color var(--transition-speed), transform var(--transition-speed);
        }

        .control-btn:hover {
            color: var(--text-white);
        }

        .control-btn.active {
            color: var(--spotify-green);
            position: relative;
        }

        .control-btn.active::after {
            content: '•';
            position: absolute;
            bottom: 0;
            font-size: 8px;
            color: var(--spotify-green);
        }

        .control-btn-play {
            width: 36px;
            height: 36px;
            background-color: var(--text-white);
            color: var(--bg-black);
            font-size: 18px;
            box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
        }

        .control-btn-play:hover {
            background-color: var(--text-white);
            transform: scale(1.06);
            color: var(--bg-black);
        }

        .progress-bar-container {
            display: flex;
            align-items: center;
            gap: 10px;
            width: 100%;
        }

        .time-label {
            font-size: 11px;
            color: var(--text-grey);
            width: 40px;
            text-align: center;
        }

        .seek-slider {
            flex: 1;
            height: 4px;
            background-color: #404040;
            border-radius: 2px;
            position: relative;
            cursor: pointer;
            transition: height var(--transition-speed);
        }

        .seek-slider:hover {
            height: 6px;
        }

        .seek-progress {
            height: 100%;
            background-color: var(--text-white);
            border-radius: 2px;
            width: 0%;
            position: relative;
        }

        .seek-slider:hover .seek-progress {
            background-color: var(--spotify-green);
        }

        .seek-handle {
            width: 12px;
            height: 12px;
            background-color: var(--text-white);
            border-radius: 50%;
            position: absolute;
            right: -6px;
            top: 50%;
            transform: translateY(-50%);
            display: none;
            box-shadow: 0 2px 4px rgba(0,0,0,0.4);
        }

        .seek-slider:hover .seek-handle {
            display: block;
        }

        /* Right side - System / Utility */
        .player-utilities {
            display: flex;
            align-items: center;
            gap: 16px;
            width: 30%;
            justify-content: flex-end;
            min-width: 180px;
        }

        .lyrics-toggle-btn {
            background: transparent;
            border: none;
            color: var(--text-grey);
            cursor: pointer;
            font-size: 14px;
            padding: 6px;
            transition: color var(--transition-speed);
        }

        .lyrics-toggle-btn.active {
            color: var(--spotify-green);
        }

        .volume-container {
            display: flex;
            align-items: center;
            gap: 10px;
            width: 120px;
        }

        .volume-slider {
            flex: 1;
            height: 4px;
            background-color: #404040;
            border-radius: 2px;
            cursor: pointer;
            position: relative;
        }

        .volume-progress {
            height: 100%;
            background-color: var(--text-white);
            border-radius: 2px;
            width: 100%;
        }

        .volume-slider:hover .volume-progress {
            background-color: var(--spotify-green);
        }

        /* --- AUTH LOGIN MODAL --- */
        .modal-overlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100vw;
            height: 100vh;
            background-color: rgba(0,0,0,0.8);
            z-index: 1000;
            display: none;
            align-items: center;
            justify-content: center;
        }

        .modal {
            background-color: var(--bg-dark-grey);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 12px;
            width: 100%;
            max-width: 420px;
            padding: 32px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.6);
            display: flex;
            flex-direction: column;
            gap: 20px;
        }

        .modal-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .modal-title {
            font-size: 20px;
            font-weight: 700;
        }

        .modal-close-btn {
            background: transparent;
            border: none;
            color: var(--text-grey);
            font-size: 18px;
            cursor: pointer;
        }

        .form-group {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }

        .form-label {
            font-size: 12px;
            font-weight: 700;
            color: var(--text-white);
        }

        .form-control {
            background-color: var(--bg-light-grey);
            border: 1px solid #404040;
            color: var(--text-white);
            padding: 12px 16px;
            border-radius: 4px;
            font-size: 14px;
            outline: none;
            transition: border-color var(--transition-speed);
        }

        .form-control:focus {
            border-color: var(--spotify-green);
        }

        .modal-btn {
            background-color: var(--spotify-green);
            color: var(--bg-black);
            padding: 14px;
            border: none;
            border-radius: 24px;
            font-weight: 700;
            font-size: 14px;
            cursor: pointer;
            margin-top: 10px;
            transition: transform var(--transition-speed);
        }

        .modal-btn:hover {
            transform: scale(1.02);
            background-color: var(--spotify-green-hover);
        }

        .modal-switch-mode {
            text-align: center;
            font-size: 12px;
            color: var(--text-grey);
            margin-top: 10px;
        }

        .modal-switch-mode span {
            color: var(--spotify-green);
            font-weight: 700;
            cursor: pointer;
        }

        /* --- CLOUD SYNC SCREEN SPECIFICS --- */
        .sync-card {
            background-color: var(--bg-light-grey);
            border-radius: 8px;
            padding: 24px;
            border: 1px solid rgba(255, 255, 255, 0.05);
            margin-bottom: 24px;
            display: flex;
            flex-direction: column;
            gap: 16px;
        }

        .sync-card-header {
            display: flex;
            align-items: center;
            gap: 16px;
        }

        .sync-card-icon {
            font-size: 32px;
            color: var(--spotify-green);
        }

        .sync-stats-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 12px;
        }

        .sync-stat-box {
            background-color: rgba(255,255,255,0.03);
            border-radius: 6px;
            padding: 16px;
            text-align: center;
            display: flex;
            flex-direction: column;
            gap: 4px;
        }

        .sync-stat-value {
            font-size: 24px;
            font-weight: 800;
            color: var(--spotify-green);
        }

        .sync-stat-label {
            font-size: 12px;
            color: var(--text-grey);
        }

        .sync-btn-large {
            background-color: var(--spotify-green);
            color: var(--bg-black);
            padding: 16px;
            border: none;
            border-radius: 30px;
            font-weight: 800;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 12px;
            font-size: 15px;
            transition: background-color var(--transition-speed), transform var(--transition-speed);
        }

        .sync-btn-large:hover {
            background-color: var(--spotify-green-hover);
            transform: scale(1.02);
        }

        /* --- APK DOWNLOAD SCREEN --- */
        .download-box {
            max-width: 600px;
            background: linear-gradient(135deg, #111e13 0%, #0c0c0c 100%);
            border: 1px solid var(--spotify-green);
            border-radius: 12px;
            padding: 32px;
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 20px;
            text-align: center;
            margin: 0 auto;
        }

        .download-icon {
            font-size: 64px;
            color: var(--spotify-green);
            text-shadow: 0 0 20px rgba(29, 185, 84, 0.4);
        }

        .download-btn {
            background-color: var(--spotify-green);
            color: var(--bg-black);
            padding: 16px 32px;
            font-size: 16px;
            font-weight: 800;
            border: none;
            border-radius: 30px;
            text-decoration: none;
            display: flex;
            align-items: center;
            gap: 12px;
            box-shadow: 0 8px 24px rgba(29, 185, 84, 0.3);
            transition: background-color var(--transition-speed), transform var(--transition-speed);
        }

        .download-btn:hover {
            background-color: var(--spotify-green-hover);
            transform: scale(1.05);
        }

        /* Vercel Deploy block */
        .vercel-card {
            background-color: #111111;
            border: 1px solid #333333;
            border-radius: 8px;
            padding: 20px;
            margin-top: 32px;
            display: flex;
            flex-direction: column;
            gap: 12px;
        }

        .vercel-code-box {
            background-color: #000;
            color: #1ed760;
            padding: 12px;
            border-radius: 4px;
            font-family: monospace;
            font-size: 13px;
            overflow-x: auto;
            text-align: left;
        }

        .d-none-desktop {
            display: none !important;
        }

        /* Exclusive Web Auth Container (Guard) */
        #web-auth-container {
            display: none;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            width: 100vw;
            background: linear-gradient(to bottom, #0F3E2F 0%, #121212 100%);
            position: fixed;
            top: 0;
            left: 0;
            z-index: 10000;
            padding: 24px;
            box-sizing: border-box;
            overflow-y: auto;
        }

        body.not-logged-in #web-auth-container {
            display: flex !important;
        }

        body.not-logged-in .app-container,
        body.not-logged-in .bottom-player-bar,
        body.not-logged-in .sidebar {
            display: none !important;
        }

        .auth-card {
            background-color: var(--bg-dark-grey);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 16px;
            width: 100%;
            max-width: 400px;
            padding: 32px;
            box-shadow: 0 16px 40px rgba(0,0,0,0.8);
            display: flex;
            flex-direction: column;
            gap: 20px;
            text-align: center;
        }

        .auth-logo {
            font-size: 48px;
            color: var(--spotify-green);
            margin-bottom: 8px;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 12px;
            font-weight: 800;
        }

        .auth-logo i {
            filter: drop-shadow(0 0 12px rgba(29, 185, 84, 0.4));
        }

        .auth-subtitle {
            color: var(--text-grey);
            font-size: 13px;
            line-height: 1.5;
            margin-bottom: 12px;
        }

        /* Responsive Mobile Bottom Navigation & Adaptive Design */
        @media (max-width: 768px) {
            body {
                background: linear-gradient(to bottom, #072a1e 0%, #121212 100%) !important;
            }

            .app-container {
                flex-direction: column-reverse;
                height: 100vh;
                overflow: hidden;
            }

            .sidebar {
                width: 100% !important;
                height: 64px !important;
                flex-direction: row !important;
                padding: 0 !important;
                justify-content: space-around !important;
                background-color: #0c0c0c !important;
                border-top: 1px solid #1a1a1a !important;
                gap: 0 !important;
                z-index: 999 !important;
                position: fixed !important;
                bottom: 0 !important;
                left: 0 !important;
                display: flex !important;
            }

            .sidebar-panel {
                background-color: transparent !important;
                padding: 0 !important;
                width: 100% !important;
                display: flex !important;
                justify-content: space-around !important;
            }

            .logo-container, .sidebar-library, .nav-menu {
                display: none !important;
            }

            .mobile-nav-item {
                display: flex !important;
                flex-direction: column !important;
                align-items: center !important;
                gap: 4px !important;
                color: var(--text-grey) !important;
                text-decoration: none !important;
                font-size: 11px !important;
                font-weight: 500 !important;
                flex: 1 !important;
                justify-content: center !important;
                cursor: pointer !important;
                padding: 6px 0 !important;
                transition: color 0.2s ease !important;
            }

            .mobile-nav-item i {
                font-size: 18px !important;
                padding: 4px 16px !important;
                border-radius: 16px !important;
                transition: background-color 0.2s ease, color 0.2s ease !important;
            }

            .mobile-nav-item.active {
                color: var(--spotify-green) !important;
                font-weight: 700 !important;
            }

            .mobile-nav-item.active i {
                background-color: rgba(29, 185, 84, 0.15) !important;
                color: var(--spotify-green) !important;
            }

            .main-viewport {
                margin: 0 !important;
                border-radius: 0 !important;
                background: transparent !important;
                height: calc(100vh - 64px - 72px) !important;
                overflow-y: auto !important;
                padding-bottom: 24px !important;
            }

            .main-header {
                display: none !important; /* Hide standard desktop main header on mobile to get rid of bulky buttons */
            }

            .main-content {
                padding: 16px 16px 100px 16px !important;
            }

            /* Bottom Player Bar - Native Android Floating Pill Style */
            .bottom-player-bar {
                height: 64px !important;
                position: fixed !important;
                bottom: 72px !important; /* sits beautifully above bottom nav */
                left: 8px !important;
                right: 8px !important;
                width: calc(100% - 16px) !important;
                background-color: #121212 !important;
                border: 1px solid rgba(255, 255, 255, 0.08) !important;
                border-radius: 12px !important;
                z-index: 998 !important;
                padding: 0 12px !important;
                box-shadow: 0 8px 24px rgba(0,0,0,0.6) !important;
                display: flex !important;
                align-items: center !important;
                justify-content: space-between !important;
            }

            .player-track-info {
                width: 65% !important;
                gap: 10px !important;
                display: flex !important;
                align-items: center !important;
            }

            .player-track-info img {
                width: 40px !important;
                height: 40px !important;
                border-radius: 6px !important;
                box-shadow: 0 4px 10px rgba(0,0,0,0.3) !important;
            }

            .player-track-details {
                display: flex !important;
                flex-direction: column !important;
                gap: 1px !important;
                min-width: 0 !important;
                flex: 1 !important;
            }

            .player-track-title {
                font-size: 13px !important;
                font-weight: 700 !important;
                color: white !important;
                white-space: nowrap !important;
                overflow: hidden !important;
                text-overflow: ellipsis !important;
            }

            .player-track-artist {
                font-size: 11px !important;
                color: #b3b3b3 !important;
                white-space: nowrap !important;
                overflow: hidden !important;
                text-overflow: ellipsis !important;
            }

            .player-track-like-btn {
                display: none !important;
            }

            .player-controls-container {
                width: 30% !important;
                display: flex !important;
                flex-direction: row !important;
                justify-content: flex-end !important;
                align-items: center !important;
            }

            .progress-bar-container {
                display: none !important;
            }

            .control-buttons {
                display: flex !important;
                align-items: center !important;
                gap: 16px !important;
            }

            .control-buttons .control-btn {
                display: none !important;
            }

            .control-buttons #ctrl-play-pause,
            .control-buttons .control-btn:nth-child(4) {
                display: flex !important;
                background: transparent !important;
                border: none !important;
                color: white !important;
                font-size: 18px !important;
                align-items: center !important;
                justify-content: center !important;
                width: 36px !important;
                height: 36px !important;
                cursor: pointer !important;
            }

            .control-buttons #ctrl-play-pause {
                font-size: 20px !important;
            }

            .player-utilities {
                display: none !important;
            }

            /* Song table styling adapted beautifully for Mobile standard list views */
            .song-table {
                display: block !important;
                width: 100% !important;
                border-collapse: separate !important;
                border-spacing: 0 !important;
            }

            .song-table thead {
                display: none !important;
            }

            .song-table tbody {
                display: flex !important;
                flex-direction: column !important;
                gap: 10px !important;
                width: 100% !important;
            }

            .song-table tr {
                display: flex !important;
                align-items: center !important;
                justify-content: space-between !important;
                background-color: rgba(255, 255, 255, 0.03) !important;
                padding: 10px 12px !important;
                border-radius: 8px !important;
                width: 100% !important;
                box-sizing: border-box !important;
                border: 1px solid rgba(255, 255, 255, 0.05) !important;
            }

            .song-table tr:hover {
                background-color: rgba(255, 255, 255, 0.08) !important;
            }

            .song-table td {
                padding: 0 !important;
                border: none !important;
                background: transparent !important;
            }

            .song-index {
                display: none !important;
            }

            .song-table td.song-title-col {
                flex: 1 !important;
                min-width: 0 !important;
                display: flex !important;
                align-items: center !important;
                gap: 12px !important;
                text-align: left !important;
            }

            .song-table td.song-title-col img {
                width: 48px !important;
                height: 48px !important;
                border-radius: 6px !important;
                box-shadow: 0 4px 10px rgba(0,0,0,0.3) !important;
                flex-shrink: 0 !important;
            }

            .song-table td:nth-child(3) {
                display: none !important; /* Hide redundant desktop artist col */
            }

            .song-details-artist-mobile {
                display: block !important;
                font-size: 11px !important;
                color: #b3b3b3 !important;
                font-weight: 400 !important;
                margin-top: 3px !important;
                white-space: nowrap !important;
                overflow: hidden !important;
                text-overflow: ellipsis !important;
            }

            .song-liked-col, .song-actions-col {
                display: flex !important;
                align-items: center !important;
                justify-content: center !important;
                width: 40px !important;
                height: 40px !important;
                cursor: pointer !important;
            }

            .song-liked-col i {
                font-size: 16px !important;
            }

            .song-actions-col button {
                background: none !important;
                border: none !important;
                color: var(--text-grey) !important;
                font-size: 16px !important;
                width: 100% !important;
                height: 100% !important;
                display: flex !important;
                align-items: center !important;
                justify-content: center !important;
            }

            /* Pemutaran Cepat Grid styling */
            .quick-grid {
                grid-template-columns: repeat(2, 1fr) !important;
                gap: 8px !important;
            }

            .quick-card {
                height: 56px !important;
                border-radius: 6px !important;
                background-color: rgba(255, 255, 255, 0.05) !important;
                border: 1px solid rgba(255, 255, 255, 0.03) !important;
                display: flex !important;
                align-items: center !important;
                overflow: hidden !important;
            }

            .quick-card img {
                width: 56px !important;
                height: 56px !important;
                border-radius: 0 !important;
                object-fit: cover !important;
                flex-shrink: 0 !important;
            }

            .quick-card-details {
                display: flex !important;
                flex-direction: column !important;
                justify-content: center !important;
                padding: 0 10px !important;
                flex: 1 !important;
                min-width: 0 !important;
            }

            .quick-card-title {
                font-size: 12px !important;
                font-weight: bold !important;
                color: white !important;
                white-space: nowrap !important;
                overflow: hidden !important;
                text-overflow: ellipsis !important;
            }

            .quick-card-artist {
                font-size: 10px !important;
                color: var(--text-grey) !important;
                white-space: nowrap !important;
                overflow: hidden !important;
                text-overflow: ellipsis !important;
                margin-top: 2px !important;
            }

            .play-overlay-btn {
                display: none !important;
            }
        }

        /* Helper styles */
        .d-none { display: none !important; }
        .d-flex { display: flex !important; }
    </style>
</head>
<body>

    <!-- --- EXCLUSIVE WEB AUTH GUARD CONTAINER --- -->
    <div id="web-auth-container">
        <div class="auth-card">
            <div class="auth-logo">
                <i class="fa-brands fa-spotify"></i>
                <span>Zorify</span>
            </div>
            <h2 id="exc-auth-title" style="font-size: 22px; font-weight: 800; margin-bottom: 8px;">Masuk ke Zorify</h2>
            <p class="auth-subtitle" id="exc-auth-subtitle">Gunakan akun Google atau Zorify Anda untuk masuk dan menyinkronkan daftar putar.</p>
            
            <div class="form-group" style="text-align: left; margin-bottom: 12px; display: flex; flex-direction: column; gap: 8px;">
                <label class="form-label" style="font-size: 12px; color: var(--text-grey); font-weight: 600;">Username</label>
                <input type="text" class="form-control" id="exc-auth-username" placeholder="Masukkan username Anda..." style="background-color: #242424; border: 1px solid #333; padding: 12px; border-radius: 8px; color: white; width: 100%;">
            </div>
            <div class="form-group" style="text-align: left; margin-bottom: 16px; display: flex; flex-direction: column; gap: 8px;">
                <label class="form-label" style="font-size: 12px; color: var(--text-grey); font-weight: 600;">Password</label>
                <input type="password" class="form-control" id="exc-auth-password" placeholder="Masukkan password..." style="background-color: #242424; border: 1px solid #333; padding: 12px; border-radius: 8px; color: white; width: 100%;">
            </div>
            
            <button class="modal-btn" id="exc-auth-submit-btn" onclick="submitExclusiveAuthForm()" style="background-color: var(--spotify-green); color: black; font-weight: bold; padding: 14px; border-radius: 30px; border: none; cursor: pointer; transition: background-color 0.2s; font-size: 14px; letter-spacing: 1px; width: 100%;">MASUK</button>
            
            <div style="display: flex; align-items: center; margin: 12px 0;">
                <hr style="flex: 1; border: none; border-top: 1px solid rgba(255,255,255,0.15);">
                <span style="padding: 0 10px; color: #888; font-size: 11px; font-weight: bold;">ATAU</span>
                <hr style="flex: 1; border: none; border-top: 1px solid rgba(255,255,255,0.15);">
            </div>
            
            <button onclick="openGoogleChooser()" class="modal-btn" style="background-color: white; color: black; display: flex; align-items: center; justify-content: center; gap: 8px; margin-top: 0; padding: 12px; font-size: 14px; border-radius: 30px; border: none; font-weight: bold; cursor: pointer; width: 100%;">
                <span style="color: #4285F4; font-weight: 900; font-size: 18px; margin-right: 4px;">G</span> Masuk dengan Google
            </button>
            
            <p class="modal-switch-mode" id="exc-auth-switch-prompt" style="font-size: 13px; color: var(--text-grey); margin-top: 16px;">
                Belum punya akun? <span onclick="toggleExclusiveAuthMode(true)" style="color: var(--spotify-green); cursor: pointer; text-decoration: underline; font-weight: bold;">Daftar Gratis</span>
            </p>
        </div>
    </div>

    <div class="app-container">
        <!-- --- SIDEBAR PANEL (Desktop Navigation) --- -->
        <aside class="sidebar">
            <div class="sidebar-panel">
                <a href="#" class="logo-container">
                    <i class="fa-brands fa-spotify"></i>
                    <span class="logo-text">Zorify</span>
                </a>
                <ul class="nav-menu">
                    <li><div class="nav-item active" onclick="switchTab('home')"><i class="fa-solid fa-house"></i> Home</div></li>
                    <li><div class="nav-item" onclick="switchTab('search')"><i class="fa-solid fa-magnifying-glass"></i> Cari</div></li>
                    <li><div class="nav-item" onclick="switchTab('liked')"><i class="fa-solid fa-heart"></i> Lagu Disukai</div></li>
                    <li><div class="nav-item" onclick="switchTab('playlists')"><i class="fa-solid fa-music"></i> Daftar Putar</div></li>
                    <li><div class="nav-item" onclick="switchTab('history')"><i class="fa-solid fa-clock-rotate-left"></i> Histori</div></li>
                    <li><div class="nav-item" onclick="switchTab('sync')"><i class="fa-solid fa-cloud-arrow-up"></i> Cloud Sync</div></li>
                    <li><div class="nav-item" onclick="switchTab('admin')"><i class="fa-solid fa-user-gear"></i> Admin</div></li>
                    <li><div class="nav-item" onclick="switchTab('download')"><i class="fa-solid fa-circle-down"></i> Unduh APK</div></li>
                </ul>
            </div>

            <!-- Library View inside Sidebar -->
            <div class="sidebar-panel sidebar-library">
                <div class="library-header">
                    <div class="library-header-title">
                        <i class="fa-solid fa-lines-leaning"></i> Koleksi Anda
                    </div>
                    <button class="create-playlist-btn" onclick="openCreatePlaylistModal()" title="Buat Playlist">
                        <i class="fa-solid fa-plus"></i>
                    </button>
                </div>
                <div class="playlist-list-container" id="sidebar-playlists-list">
                    <!-- Loaded dynamically -->
                </div>
            </div>
        </aside>

        <!-- --- MAIN VIEWPORT PANEL --- -->
        <main class="main-viewport">
            <!-- Header bar with Navigation Arrows and Auth Button -->
            <header class="main-header">
                <div class="nav-arrows">
                    <button class="arrow-btn"><i class="fa-solid fa-chevron-left"></i></button>
                    <button class="arrow-btn"><i class="fa-solid fa-chevron-right"></i></button>
                </div>
                <div class="header-auth">
                    <button class="btn-premium" onclick="switchTab('download')">Instal Aplikasi</button>
                    <div class="auth-profile" id="user-profile-header" onclick="openAuthModal()">
                        <div class="auth-avatar" style="background-color: var(--spotify-green)">G</div>
                        <span class="auth-username">Guest / Masuk</span>
                    </div>
                </div>
            </header>

            <!-- Views / Pages -->
            <div class="main-content">
                <!-- 1. Home View -->
                <section id="tab-home" class="tab-content">
                    <!-- Dynamic Header with Native Look and Feel on Mobile -->
                    <div class="mobile-greeting-header">
                        <div class="greeting-text-container">
                            <h1 class="home-greeting" id="greeting-text">Selamat Datang</h1>
                            <p class="home-subgreeting" id="subgreeting-text">Halo, Guest!</p>
                        </div>
                        <div class="header-actions">
                            <button class="header-action-btn" onclick="switchTab('history')"><i class="fa-solid fa-clock-rotate-left"></i></button>
                            <div class="header-profile-avatar" onclick="openAuthModal()" id="home-avatar">G</div>
                        </div>
                    </div>
                    
                    <h2 class="section-title">Berdasarkan yang baru didengar</h2>
                    <div class="quick-grid" id="home-quick-grid">
                        <!-- Filled dynamically -->
                    </div>

                    <h2 class="section-title">Daftar Putar Anda</h2>
                    <div class="empty-playlists-banner" style="background-color: rgba(255, 255, 255, 0.05); border: 1px solid rgba(255,255,255,0.04); border-radius: 12px; padding: 20px; font-size: 14px; color: var(--text-grey); margin-bottom: 24px; text-align: left; font-family: 'Plus Jakarta Sans', sans-serif;">
                        Mulai buat daftar putarmu di tab Koleksi!
                    </div>

                    <h2 class="section-title">Direkomendasikan Untukmu</h2>
                    <div class="recommendation-banner" style="display: flex; align-items: center; background-color: rgba(255, 255, 255, 0.05); border: 1px solid rgba(255,255,255,0.04); border-radius: 12px; padding: 16px; margin-bottom: 24px; gap: 16px; cursor: pointer; transition: background-color 0.2s;" onclick="playDiscoverWeekly()">
                        <img src="https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&auto=format&fit=crop&q=60" alt="Discover Weekly" style="width: 72px; height: 72px; border-radius: 8px; box-shadow: 0 4px 15px rgba(0,0,0,0.4); flex-shrink: 0;">
                        <div style="flex: 1; min-width: 0;">
                            <h3 style="font-size: 15px; font-weight: 800; color: white;">Discover Weekly</h3>
                            <p style="font-size: 12px; color: var(--text-grey); margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">Pilihan lagu segar terbaik disinkronkan otomatis sesuai seleramu.</p>
                        </div>
                        <button class="play-btn-circle" style="background-color: var(--spotify-green); border: none; width: 44px; height: 44px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: black; font-size: 18px; cursor: pointer; box-shadow: 0 4px 10px rgba(29, 185, 84, 0.3); flex-shrink: 0;"><i class="fa-solid fa-play" style="margin-left: 2px;"></i></button>
                    </div>

                    <h2 class="section-title">Semua Musik Zorify</h2>
                    <table class="song-table">
                        <thead>
                            <tr>
                                <th class="song-index">#</th>
                                <th>Judul</th>
                                <th>Artis</th>
                                <th style="width: 40px;"></th>
                                <th style="width: 40px;"></th>
                            </tr>
                        </thead>
                        <tbody id="home-song-list">
                            <!-- Populated dynamically -->
                        </tbody>
                    </table>
                </section>

                <!-- 2. Search View -->
                <section id="tab-search" class="tab-content d-none">
                    <h1 class="section-title">Cari Musik</h1>
                    <div class="form-group" style="margin-bottom: 24px;">
                        <input type="text" class="form-control" id="search-input" placeholder="Cari lagu, artis, atau album..." oninput="filterSongs()">
                    </div>
                    <div class="quick-grid" id="search-results-grid">
                        <!-- Filled dynamically -->
                    </div>
                </section>

                <!-- 3. Liked Songs View -->
                <section id="tab-liked" class="tab-content d-none">
                    <div style="background: linear-gradient(180deg, #301934 0%, transparent 100%); margin: -24px -32px 24px -32px; padding: 40px 32px 24px 32px; display: flex; align-items: flex-end; gap: 24px;">
                        <div style="width: 160px; height: 160px; background: linear-gradient(135deg, #450e75 0%, #110224 100%); border-radius: 8px; display: flex; align-items: center; justify-content: center; box-shadow: 0 8px 24px rgba(0,0,0,0.5);">
                            <i class="fa-solid fa-heart" style="font-size: 64px; color: var(--text-white);"></i>
                        </div>
                        <div>
                            <div style="font-size: 12px; font-weight: 700; text-transform: uppercase;">Playlist</div>
                            <h1 style="font-size: 56px; font-weight: 800; margin: 8px 0; letter-spacing: -2px;">Lagu Disukai</h1>
                            <div style="font-size: 14px; color: var(--text-grey);" id="liked-songs-count">0 lagu</div>
                        </div>
                    </div>
                    <table class="song-table">
                        <thead>
                            <tr>
                                <th class="song-index">#</th>
                                <th>Judul</th>
                                <th>Artis</th>
                                <th style="width: 40px;"></th>
                                <th style="width: 40px;"></th>
                            </tr>
                        </thead>
                        <tbody id="liked-song-list">
                            <!-- Populated dynamically -->
                        </tbody>
                    </table>
                </section>

                <!-- 4. Playlists View -->
                <section id="tab-playlists" class="tab-content d-none">
                    <h1 class="section-title">Daftar Putar Anda</h1>
                    <div class="quick-grid" id="playlists-grid">
                        <!-- Loaded dynamically -->
                    </div>

                    <!-- Single Playlist Detail View -->
                    <div id="playlist-detail-view" class="d-none" style="margin-top: 32px;">
                        <hr style="border: 1px solid rgba(255,255,255,0.05); margin-bottom: 24px;">
                        <h2 class="section-title" id="active-playlist-title">Nama Playlist</h2>
                        <table class="song-table">
                            <thead>
                                <tr>
                                    <th class="song-index">#</th>
                                    <th>Judul</th>
                                    <th>Artis</th>
                                    <th style="width: 40px;"></th>
                                    <th style="width: 40px;"></th>
                                </tr>
                            </thead>
                            <tbody id="playlist-song-list">
                                <!-- Populated dynamically -->
                            </tbody>
                        </table>
                    </div>
                </section>

                <!-- 5. History View -->
                <section id="tab-history" class="tab-content d-none">
                    <h1 class="section-title">Histori Putar</h1>
                    <table class="song-table">
                        <thead>
                            <tr>
                                <th class="song-index">#</th>
                                <th>Judul</th>
                                <th>Artis</th>
                                <th style="width: 40px;"></th>
                            </tr>
                        </thead>
                        <tbody id="history-song-list">
                            <!-- Populated dynamically -->
                        </tbody>
                    </table>
                </section>

                <!-- 6. Cloud Sync View -->
                <section id="tab-sync" class="tab-content d-none">
                    <h1 class="section-title">Sinkronisasi Cloud Zorify</h1>
                    <div class="sync-card">
                        <div class="sync-card-header">
                            <i class="fa-solid fa-cloud-arrow-up sync-card-icon"></i>
                            <div>
                                <h3 style="font-size: 18px; font-weight: 700;">Status Sinkronisasi</h3>
                                <p style="font-size: 13px; color: var(--text-grey); margin-top: 4px;" id="sync-status-text">Hubungkan akun Anda untuk mengaktifkan sinkronisasi awan.</p>
                            </div>
                        </div>

                        <div class="sync-stats-grid">
                            <div class="sync-stat-box">
                                <span class="sync-stat-value" id="stat-playlists">0</span>
                                <span class="sync-stat-label">Daftar Putar</span>
                            </div>
                            <div class="sync-stat-box">
                                <span class="sync-stat-value" id="stat-custom">0</span>
                                <span class="sync-stat-label">Lagu Kustom</span>
                            </div>
                            <div class="sync-stat-box">
                                <span class="sync-stat-value" id="stat-liked">0</span>
                                <span class="sync-stat-label">Lagu Disukai</span>
                            </div>
                            <div class="sync-stat-box">
                                <span class="sync-stat-value" id="stat-history">0</span>
                                <span class="sync-stat-label">Histori Putar</span>
                            </div>
                        </div>

                        <button class="sync-btn-large" onclick="triggerSyncWithServer()" id="btn-sync-action">
                            <i class="fa-solid fa-rotate"></i> SINKRONKAN DATA SEKARANG
                        </button>
                    </div>

                    <div style="background-color: var(--bg-light-grey); border-radius: 8px; padding: 20px; font-size: 14px; line-height: 1.6;">
                        <h4 style="font-weight: bold; margin-bottom: 8px;"><i class="fa-solid fa-circle-info" style="color: var(--spotify-green)"></i> Bagaimana cara menyinkronkannya ke HP?</h4>
                        <ol style="padding-left: 20px; display: flex; flex-direction: column; gap: 8px;">
                            <li>Buka aplikasi <b>Zorify</b> di HP Android Anda.</li>
                            <li>Masuk dengan <b>username yang sama</b> di layar Profil.</li>
                            <li>Salin URL web player ini: <b id="sync-helper-url" style="color: var(--spotify-green);"></b></li>
                            <li>Tempel di bagian <b>"Alamat Server Sinkronisasi"</b> pada tab Profil di HP Anda.</li>
                            <li>Ketuk tombol <b>"SINKRONKAN SEKARANG"</b> di HP Anda. Semua daftar putar, kesukaan, dan lagu kustom akan disinkronkan secara instan!</li>
                        </ol>
                    </div>
                </section>

                <!-- 7. Admin View -->
                <section id="tab-admin" class="tab-content d-none">
                    <h1 class="section-title">Admin Dashboard • Tambah Musik</h1>
                    <div class="sync-card" style="max-width: 600px; margin: 0 auto;">
                        <div class="form-group">
                            <label class="form-label">Judul Lagu *</label>
                            <input type="text" class="form-control" id="admin-title" placeholder="Masukkan judul lagu...">
                        </div>
                        <div class="form-group">
                            <label class="form-label">Artis / Penyanyi *</label>
                            <input type="text" class="form-control" id="admin-artist" placeholder="Masukkan nama penyanyi...">
                        </div>
                        <div class="form-group">
                            <label class="form-label">Audio Stream URL (Dropbox, Direct MP3, dll) *</label>
                            <input type="text" class="form-control" id="admin-url" placeholder="https://www.dropbox.com/s/.../music.mp3?dl=1">
                        </div>
                        <div class="form-group">
                            <label class="form-label">Cover Gambar URL</label>
                            <input type="text" class="form-control" id="admin-cover" placeholder="https://images.unsplash.com/...">
                        </div>
                        <div class="form-group">
                            <label class="form-label">Lirik Lagu (Format LRC / Text)</label>
                            <textarea class="form-control" id="admin-lyrics" rows="5" placeholder="[00:00.00] Lirik baris pertama&#10;[00:05.00] Lirik baris kedua" style="resize: vertical; min-height: 100px;"></textarea>
                        </div>
                        <button class="sync-btn-large" onclick="adminAddSong()"><i class="fa-solid fa-circle-plus"></i> Simpan Lagu Baru</button>
                    </div>
                </section>

                <!-- 8. Download APK View -->
                <section id="tab-download" class="tab-content d-none">
                    <div class="download-box">
                        <i class="fa-solid fa-circle-down download-icon"></i>
                        <h1 style="font-size: 28px; font-weight: 800;">Unduh Aplikasi Zorify untuk HP Android</h1>
                        <p style="color: var(--text-grey); font-size: 15px; max-width: 480px; margin: 0 auto; line-height: 1.6;">
                            Pasang aplikasi native Zorify di HP Anda untuk mendengarkan lagu secara offline dengan kualitas audio hi-fi, sinkronisasi notifikasi, dan lirik yang berputar otomatis.
                        </p>
                        <a href="${downloadApkUrl}" class="download-btn">
                            <i class="fa-brands fa-android"></i> UNDUH APK SEKARANG (GRATIS)
                        </a>

                        <div class="vercel-card" style="width: 100%; max-width: 500px; text-align: left;">
                            <h3 style="font-weight: 700; font-size: 14px;"><i class="fa-solid fa-triangle-exclamation" style="color: yellow"></i> Panduan Instalasi:</h3>
                            <ul style="padding-left: 20px; font-size: 13px; color: var(--text-grey); display: flex; flex-direction: column; gap: 8px; margin-top: 8px;">
                                <li>Ketuk tombol di atas untuk mengunduh file <b>Zorify.apk</b>.</li>
                                <li>Buka file tersebut, izinkan <b>"Sumber tidak dikenal"</b> jika diminta oleh peramban/browser Anda.</li>
                                <li>Pilih <b>Instal</b> dan buka aplikasi.</li>
                            </ul>
                        </div>
                    </div>

                    <!-- Vercel Deploy Guide -->
                    <div class="vercel-card" style="max-width: 600px; margin: 32px auto 0 auto;">
                        <h3 style="font-size: 16px; font-weight: 700; color: var(--text-white);"><i class="fa-solid fa-server" style="color: var(--spotify-green)"></i> Deploy Web Player Ini ke Vercel (1-Click)</h3>
                        <p style="font-size: 13px; color: var(--text-grey); line-height: 1.5; margin-top: 4px;">
                            Anda bisa mendeploy Zorify Web Player dan server sinkronisasi ini secara gratis ke akun Vercel Anda sendiri dengan sangat mudah.
                        </p>
                        <div class="vercel-code-box">
                            npm install -g vercel<br>
                            vercel --prod
                        </div>
                        <p style="font-size: 12px; color: var(--text-grey);">
                            File <b style="color: var(--text-white);">vercel.json</b> sudah kami siapkan di dalam root direktori sehingga proses build serverless akan berjalan secara otomatis di cloud Vercel!
                        </p>
                    </div>
                </section>
            </div>

            <!-- --- REAL-TIME LYRICS PANEL OVERLAY --- -->
            <div class="lyrics-panel-container" id="lyrics-overlay-panel">
                <button style="position: absolute; top: 24px; right: 32px; background: transparent; border: none; color: var(--text-grey); font-size: 24px; cursor: pointer;" onclick="toggleLyricsPanel()">
                    <i class="fa-solid fa-circle-xmark"></i>
                </button>
                <div class="lyrics-title" id="lyrics-panel-title">Judul Lagu</div>
                <div class="lyrics-artist" id="lyrics-panel-artist">Artis</div>
                <div class="lyrics-scroll-box" id="lyrics-panel-scroll">
                    <!-- Populated dynamically based on playback -->
                </div>
            </div>
        </main>
    </div>

    <!-- --- WEB PLAYER BOTTOM CONTROLLER BAR --- -->
    <footer class="bottom-player-bar">
        <!-- Track info left -->
        <div class="player-track-info">
            <div class="player-cover-art-container">
                <img src="https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=150" alt="Cover" class="player-cover-art" id="player-cover">
            </div>
            <div class="player-track-details">
                <span class="player-track-title" id="player-title">Tidak Memutar Musik</span>
                <span class="player-track-artist" id="player-artist">Ketuk lagu untuk mulai</span>
            </div>
            <button class="player-track-like-btn" id="player-like-btn" onclick="toggleLikeCurrentSong()">
                <i class="fa-regular fa-heart"></i>
            </button>
        </div>

        <!-- Central Player Controls -->
        <div class="player-controls-container">
            <div class="control-buttons">
                <button class="control-btn" id="ctrl-shuffle" onclick="toggleShuffle()" title="Acak"><i class="fa-solid fa-shuffle"></i></button>
                <button class="control-btn" onclick="playPrev()" title="Sebelumnya"><i class="fa-solid fa-backward-step"></i></button>
                <button class="control-btn control-btn-play" id="ctrl-play-pause" onclick="togglePlayPause()" title="Putar"><i class="fa-solid fa-play"></i></button>
                <button class="control-btn" onclick="playNext()" title="Berikutnya"><i class="fa-solid fa-forward-step"></i></button>
                <button class="control-btn" id="ctrl-repeat" onclick="toggleRepeat()" title="Ulang lagu"><i class="fa-solid fa-repeat"></i></button>
            </div>

            <!-- Progress Elapsed Bar -->
            <div class="progress-bar-container">
                <span class="time-label" id="time-elapsed">0:00</span>
                <div class="seek-slider" id="player-seek-slider" onclick="seekAudio(event)">
                    <div class="seek-progress" id="player-seek-progress">
                        <div class="seek-handle"></div>
                    </div>
                </div>
                <span class="time-label" id="time-duration">0:00</span>
            </div>
        </div>

        <!-- Utility right (Volume and Lyrics panel toggle) -->
        <div class="player-utilities">
            <button class="lyrics-toggle-btn" id="player-lyrics-btn" onclick="toggleLyricsPanel()" title="Lirik"><i class="fa-solid fa-microphone-lines"></i></button>
            <div class="volume-container">
                <i class="fa-solid fa-volume-high" style="color: var(--text-grey);"></i>
                <div class="volume-slider" id="player-volume-slider" onclick="changeVolume(event)">
                    <div class="volume-progress" id="player-volume-progress"></div>
                </div>
            </div>
        </div>
    </footer>

    <!-- --- MOBILE NAVIGATION TAB BAR (Shows on screens < 768px) --- -->
    <div class="sidebar d-none" style="position: fixed; bottom: 0; left: 0; width: 100%; height: 60px; z-index: 100; border-top: 1px solid #282828;">
        <div class="sidebar-panel">
            <div class="mobile-nav-item active" id="mob-home" onclick="switchTab('home')"><i class="fa-solid fa-house"></i><span>Home</span></div>
            <div class="mobile-nav-item" id="mob-search" onclick="switchTab('search')"><i class="fa-solid fa-magnifying-glass"></i><span>Cari</span></div>
            <div class="mobile-nav-item" id="mob-playlists" onclick="switchTab('playlists')"><i class="fa-solid fa-lines-leaning"></i><span>Koleksi</span></div>
            <div class="mobile-nav-item" id="mob-sync" onclick="switchTab('sync')"><i class="fa-regular fa-user"></i><span>Akun</span></div>
        </div>
    </div>

    <!-- --- AUTHENTICATION MODAL (Masuk/Daftar) --- -->
    <div class="modal-overlay" id="auth-modal">
        <div class="modal">
            <div class="modal-header">
                <h2 class="modal-title" id="auth-modal-title">Masuk ke Zorify</h2>
                <button class="modal-close-btn" onclick="closeAuthModal()"><i class="fa-solid fa-xmark"></i></button>
            </div>
            <div class="form-group">
                <label class="form-label">Username</label>
                <input type="text" class="form-control" id="auth-username" placeholder="Masukkan username Anda...">
            </div>
            <div class="form-group">
                <label class="form-label">Password</label>
                <input type="password" class="form-control" id="auth-password" placeholder="Masukkan password...">
            </div>
            <button class="modal-btn" id="auth-submit-btn" onclick="submitAuthForm()">MASUK</button>
            
            <div style="display: flex; align-items: center; margin: 10px 0;">
                <hr style="flex: 1; border: none; border-top: 1px solid rgba(255,255,255,0.15);">
                <span style="padding: 0 10px; color: #888; font-size: 11px; font-weight: bold;">ATAU</span>
                <hr style="flex: 1; border: none; border-top: 1px solid rgba(255,255,255,0.15);">
            </div>
            
            <button onclick="openGoogleChooser()" class="modal-btn" style="background-color: white; color: black; display: flex; align-items: center; justify-content: center; gap: 8px; margin-top: 0; padding: 12px; font-size: 14px;">
                <span style="color: #4285F4; font-weight: 900; font-size: 18px; margin-right: 4px;">G</span> Masuk dengan Google
            </button>
            
            <p class="modal-switch-mode" id="auth-switch-prompt">Belum punya akun? <span onclick="toggleAuthMode(true)">Daftar Gratis</span></p>
        </div>
    </div>

    <!-- --- GOOGLE ACCOUNT CHOOSER MODAL --- -->
    <div class="modal-overlay" id="google-modal">
        <div class="modal" style="text-align: center; max-width: 380px;">
            <div class="modal-header" style="justify-content: center; position: relative;">
                <h2 class="modal-title" style="color: #4285F4; font-size: 32px; font-weight: 900; margin-bottom: 4px;">G</h2>
                <button class="modal-close-btn" onclick="closeGoogleChooser()" style="position: absolute; right: 0; top: 0;"><i class="fa-solid fa-xmark"></i></button>
            </div>
            <h3 style="font-size: 18px; font-weight: bold; margin-bottom: 4px;">Pilih Akun Google</h3>
            <p style="color: #888; font-size: 12px; margin-bottom: 20px;">untuk melanjutkan ke Zorify Web Player</p>
            
            <div style="display: flex; flex-direction: column; gap: 12px; text-align: left;" id="google-accounts-list">
                <!-- Account 1 -->
                <div onclick="selectGoogleAccount('zzora7174@gmail.com')" style="display: flex; align-items: center; gap: 12px; padding: 12px; background-color: #242424; border-radius: 8px; cursor: pointer; transition: background-color 0.2s;">
                    <div style="width: 36px; height: 36px; border-radius: 50%; background-color: #1DB954; color: black; display: flex; align-items: center; justify-content: center; font-weight: bold;">Z</div>
                    <div style="display: flex; flex-direction: column;">
                        <span style="font-weight: bold; font-size: 13px; color: white;">Zora User</span>
                        <span style="font-size: 11px; color: #aaa;">zzora7174@gmail.com</span>
                    </div>
                </div>
                <!-- Account 2 -->
                <div onclick="selectGoogleAccount('zorify.music@gmail.com')" style="display: flex; align-items: center; gap: 12px; padding: 12px; background-color: #242424; border-radius: 8px; cursor: pointer; transition: background-color 0.2s;">
                    <div style="width: 36px; height: 36px; border-radius: 50%; background-color: #9D4EDD; color: white; display: flex; align-items: center; justify-content: center; font-weight: bold;">Z</div>
                    <div style="display: flex; flex-direction: column;">
                        <span style="font-weight: bold; font-size: 13px; color: white;">Zorify Premium</span>
                        <span style="font-size: 11px; color: #aaa;">zorify.music@gmail.com</span>
                    </div>
                </div>
            </div>
            
            <div style="margin-top: 10px;" id="google-use-another-container">
                <span onclick="toggleGoogleEmailInput(true)" style="color: #1DB954; font-size: 12px; font-weight: bold; cursor: pointer;">Gunakan akun lain</span>
            </div>
            
            <div id="google-custom-email-form" class="d-none" style="flex-direction: column; gap: 12px; text-align: left; margin-top: 10px;">
                <label style="font-size: 12px; font-weight: bold; color: white;">Masukkan Email Google Anda:</label>
                <input type="email" id="google-custom-email-input" class="form-control" placeholder="nama@gmail.com">
                <div style="display: flex; justify-content: space-between; margin-top: 8px;">
                    <button onclick="toggleGoogleEmailInput(false)" class="modal-btn" style="background-color: transparent; color: #888; margin-top: 0; padding: 8px 16px;">Kembali</button>
                    <button onclick="submitGoogleCustomEmail()" class="modal-btn" style="margin-top: 0; padding: 8px 16px;">Masuk</button>
                </div>
            </div>
        </div>
    </div>

    <!-- --- CREATE PLAYLIST MODAL --- -->
    <div class="modal-overlay" id="playlist-modal">
        <div class="modal">
            <div class="modal-header">
                <h2 class="modal-title">Buat Daftar Putar Baru</h2>
                <button class="modal-close-btn" onclick="closeCreatePlaylistModal()"><i class="fa-solid fa-xmark"></i></button>
            </div>
            <div class="form-group">
                <label class="form-label">Nama Playlist *</label>
                <input type="text" class="form-control" id="playlist-name-input" placeholder="Masukkan nama playlist...">
            </div>
            <div class="form-group">
                <label class="form-label">Deskripsi</label>
                <input type="text" class="form-control" id="playlist-desc-input" placeholder="Masukkan deskripsi pendek...">
            </div>
            <div class="form-group">
                <label class="form-label">Gambar URL (Opsional)</label>
                <input type="text" class="form-control" id="playlist-cover-input" placeholder="https://images.unsplash.com/...">
            </div>
            <button class="modal-btn" onclick="submitCreatePlaylist()">BUAT PLAYLIST</button>
        </div>
    </div>

    <!-- Hidden native audio element -->
    <audio id="native-audio" preload="auto"></audio>

    <script>
        // --- DATA STATE ---
        const DEFAULT_SONGS = [
            {
                id: 1,
                title: "Halu",
                artist: "Feby Putri",
                remoteUrl: "https://www.dropbox.com/scl/fi/j7ddf65rwrkp6ifk2m1zz/Halu-Feby-Putri-Official-Music-Video-Feby-Putri-NC.mp3?rlkey=lqh6405a319exv53d6ksuhnug&st=r153vdio&dl=1",
                coverUrl: "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRK8WYYgsHZg91_JxvNhJG_KvNf_p8-KOIc8ZFvuR2Gkg&s",
                lyrics: "[00:00.00]Zorify High Quality Audio\\n[00:03.00]Halu - Feby Putri\\n[00:07.00]Ku tersenyum saat kau hadir dalam lamunanku\\n[00:15.00]Menari-nari indah di sudut bayanganku\\n[00:23.00]Rasa yang aneh bersemi di dalam dada ini\\n[00:30.00]Mengisi ruang kosong yang t'lah lama sepi\\n[00:38.00]Ku melayang tinggi dalam halusinasi\\n[00:46.00]Membayangkan dirimu selalu ada di sini\\n[00:54.00]Menanti senyuman yang tak kunjung kembali...\\n[01:03.00]Zorify Premium • Terima kasih telah mendengarkan"
            },
            {
                id: 2,
                title: "Apa Artinya Cinta",
                artist: "Zinu Arashi",
                remoteUrl: "https://www.dropbox.com/scl/fi/8bcp7jci5ctsapjjtotvj/Apa-Artinya-Cinta-Video-Lirik-Cover-Viral-TikTok-YouTube-Zinu-Arashi.mp3?rlkey=27z6xff02v2qiehjkciis5mbf&st=ion466up&dl=1",
                coverUrl: "https://i.scdn.co/image/ab67616d00001e027496de43aa02afd149919060",
                lyrics: "[00:00.00]Zorify High Quality Audio\\n[00:02.00]Apa Artinya Cinta - Zinu Arashi\\n[00:06.00]Mencari arti cinta di sunyinya malam yang kelabu\\n[00:14.00]Menatap deretan bintang-bintang di atas sana\\n[00:22.00]Apakah ini rasa tulus yang selalu kunanti?\\n[00:30.00]Atau sekadar mimpi yang akan hilang lagi?\\n[00:38.00]Cinta sejati tak akan pernah berpaling\\n[00:46.00]Dia akan setia bernyanyi di relung batin...\\n[00:55.00]Zorify - High-Fidelity Audio Experience"
            },
            {
                id: 3,
                title: "Maha Melihat",
                artist: "Opick feat. Amanda",
                remoteUrl: "https://www.dropbox.com/scl/fi/adadrizmar6dvfb2c18mm/Maha-Melihat-Opick-feat-Amanda-Cover-by-PI7U-PI7U.mp3?rlkey=e0oa4dokzi66vo7xbntensy1f&st=fjihg0rw&dl=1",
                coverUrl: "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?q=80&w=200",
                lyrics: "[00:00.00]Zorify High Quality Audio\\n[00:03.00]Maha Melihat - Opick feat. Amanda\\n[00:08.00]Sering kali hamba ini lalai dan lupa akan-Mu\\n[00:16.00]Saat tawa bahagia menyelimuti langkahku\\n[00:24.00]Namun Engkau selalu sabar menanti hamba kembali\\n[00:32.00]Maha Melihat segala duka lara di hati ini\\n[00:40.00]Ampunilah salah dan khilaf hamba-Mu ini\\n[00:48.00]Tuntunlah raga ini menuju ridho-Mu yang suci...\\n[00:57.00]Zorify - Musik yang Menyentuh Jiwa"
            },
            {
                id: 4,
                title: "Santai Malam",
                artist: "Indie Chill",
                remoteUrl: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                coverUrl: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=150",
                lyrics: "[00:00.00]Zorify High Quality Audio\\n[00:02.00]Indie Chill - Santai Malam\\n[00:06.00]Menikmati sejuknya angin malam yang berbisik lirih\\n[00:15.00]Duduk santai di teras ditemani cangkir kopi hangat\\n[00:24.00]Alunan instrumen mengalir lambat menenangkan pikiran\\n[00:34.00]Melupakan seluruh penat kesibukan hari ini\\n[00:44.00]Damai terasa mengalir menyentuh sukma...\\n[00:54.00]Zorify Chill Lounge - Santai Malam Anda"
            }
        ];

        let localData = {
            currentUser: null,
            customSongs: [],
            likedSongs: [], // Stores object of format {title: "", artist: ""}
            playlists: [], // Stores { name, description, coverUrl, songs: [] }
            playHistory: [], // Stores recently played song objects
        };

        // Current Active Playback State
        let queue = [];
        let currentQueueIndex = -1;
        let isPlaying = false;
        let isShuffle = false;
        let repeatMode = 0; // 0 = off, 1 = repeat all, 2 = repeat one
        let parsedLyrics = [];

        // HTML audio elements
        const audio = document.getElementById('native-audio');

        // Init App Data from LocalStorage
        function initApp() {
            // Fill current page URL for sync helper
            document.getElementById('sync-helper-url').innerText = window.location.origin + '/';

            const stored = localStorage.getItem('zorify_local_data');
            if (stored) {
                try {
                    localData = JSON.parse(stored);
                } catch(e) {}
            }

            // Always populate default queue with default songs
            refreshAllUIs();

            // Setup audio events
            audio.addEventListener('timeupdate', updatePlaybackProgress);
            audio.addEventListener('durationchange', updatePlaybackDuration);
            audio.addEventListener('ended', onSongEnded);

            // Set greeting based on time
            const hour = new Date().getHours();
            let greeting = "Selamat Malam";
            if (hour < 12) greeting = "Selamat Pagi";
            else if (hour < 15) greeting = "Selamat Siang";
            else if (hour < 18) greeting = "Selamat Sore";
            document.getElementById('greeting-text').innerText = greeting + ", Pecinta Musik!";
        }

        function saveData() {
            localStorage.setItem('zorify_local_data', JSON.stringify(localData));
        }

        // --- CORE NAVIGATION ---
        function switchTab(tabId) {
            // Remove active classes
            document.querySelectorAll('.tab-content').forEach(el => el.classList.add('d-none'));
            document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
            document.querySelectorAll('.mobile-nav-item').forEach(el => el.classList.remove('active'));

            // Show and set active
            const targetSection = document.getElementById('tab-' + tabId);
            if (targetSection) {
                targetSection.classList.remove('d-none');
            }

            // Sidebar highlighting
            const desktopItems = document.querySelectorAll('.nav-item');
            desktopItems.forEach(item => {
                if (item.getAttribute('onclick') && item.getAttribute('onclick').includes(tabId)) {
                    item.classList.add('active');
                }
            });

            // Mobile tab highlighting
            const mobHome = document.getElementById('mob-home');
            const mobSearch = document.getElementById('mob-search');
            const mobPlaylists = document.getElementById('mob-playlists');
            const mobSync = document.getElementById('mob-sync');

            if (tabId === 'home' && mobHome) mobHome.classList.add('active');
            if (tabId === 'search' && mobSearch) mobSearch.classList.add('active');
            if (tabId === 'playlists' && mobPlaylists) mobPlaylists.classList.add('active');
            if (tabId === 'sync' && mobSync) mobSync.classList.add('active');
        }

        // --- DATA RENDERING FUNCTIONS ---
        function refreshAllUIs() {
            if (!localData.currentUser) {
                document.body.classList.add('not-logged-in');
            } else {
                document.body.classList.remove('not-logged-in');
            }
            renderHeaderUser();
            renderHomeSongs();
            renderSearchSongs();
            renderLikedSongs();
            renderPlaylists();
            renderHistorySongs();
            renderSyncStatus();
        }

        let isExcRegistering = false;
        function toggleExclusiveAuthMode(reg) {
            isExcRegistering = reg;
            document.getElementById('exc-auth-title').innerText = reg ? "Daftar Akun Zorify" : "Masuk ke Zorify";
            document.getElementById('exc-auth-submit-btn').innerText = reg ? "DAFTAR SEKARANG" : "MASUK";
            document.getElementById('exc-auth-switch-prompt').innerHTML = reg ? 
                "Sudah punya akun? <span onclick='toggleExclusiveAuthMode(false)' style='color: var(--spotify-green); cursor: pointer; text-decoration: underline; font-weight: bold;'>Masuk di sini</span>" :
                "Belum punya akun? <span onclick='toggleExclusiveAuthMode(true)' style='color: var(--spotify-green); cursor: pointer; text-decoration: underline; font-weight: bold;'>Daftar Gratis</span>";
        }

        function submitExclusiveAuthForm() {
            const user = document.getElementById('exc-auth-username').value.trim();
            const pass = document.getElementById('exc-auth-password').value;

            if (!user || !pass) {
                alert("Harap isi semua kolom!");
                return;
            }

            localData.currentUser = {
                username: user,
                avatarColorHex: "#1DB954",
                lastSync: "Menghubungkan..."
            };
            saveData();
            refreshAllUIs();
            alert(isExcRegistering ? ("Pendaftaran berhasil! Anda masuk sebagai " + user) : ("Berhasil masuk sebagai " + user));
            triggerSyncWithServer();
        }

        function renderHeaderUser() {
            const profileHeader = document.getElementById('user-profile-header');
            const subGreeting = document.getElementById('subgreeting-text');
            const homeAvatar = document.getElementById('home-avatar');
            
            if (localData.currentUser) {
                profileHeader.innerHTML = \`
                    <div class="auth-avatar" style="background-color: \${localData.currentUser.avatarColorHex || '#1DB954'}">\${localData.currentUser.username[0].toUpperCase()}</div>
                    <span class="auth-username">\${localData.currentUser.username} (Keluar)</span>
                \`;
                if (subGreeting) subGreeting.innerText = \`Halo, \${localData.currentUser.username}!\`;
                if (homeAvatar) {
                    homeAvatar.innerText = localData.currentUser.username[0].toUpperCase();
                    homeAvatar.style.backgroundColor = localData.currentUser.avatarColorHex || '#1DB954';
                }
            } else {
                profileHeader.innerHTML = \`
                    <div class="auth-avatar" style="background-color: var(--spotify-green)">G</div>
                    <span class="auth-username">Guest / Masuk</span>
                \`;
                if (subGreeting) subGreeting.innerText = "Halo, Guest!";
                if (homeAvatar) {
                    homeAvatar.innerText = "G";
                    homeAvatar.style.backgroundColor = '#1DB954';
                }
            }
        }

        function getAllSongs() {
            return [...DEFAULT_SONGS, ...localData.customSongs];
        }

        function renderHomeSongs() {
            const container = document.getElementById('home-song-list');
            const quickGrid = document.getElementById('home-quick-grid');
            container.innerHTML = '';
            quickGrid.innerHTML = '';

            const allSongs = getAllSongs();

            // Populate quick grid (first 6 songs)
            allSongs.slice(0, 6).forEach(song => {
                const card = document.createElement('div');
                card.className = 'quick-card';
                card.onclick = () => playSongDirectly(song);
                card.innerHTML = \`
                    <img src="\${song.coverUrl}" alt="Cover">
                    <div class="quick-card-title">\${song.title}</div>
                    <button class="play-overlay-btn"><i class="fa-solid fa-play"></i></button>
                \`;
                quickGrid.appendChild(card);
            });

            // Populate Full table
            allSongs.forEach((song, idx) => {
                const tr = document.createElement('tr');
                tr.onclick = () => playSongDirectly(song);
                
                const isLiked = isSongLiked(song);
                const heartClass = isLiked ? 'fa-solid fa-heart active' : 'fa-regular fa-heart';

                tr.innerHTML = \`
                    <td class="song-index">\${idx + 1}</td>
                    <td class="song-title-col">
                        <img src="\${song.coverUrl}" alt="Cover">
                        <div class="song-details-mini">
                            <span class="song-details-title">\${song.title}</span>
                            <span class="song-details-artist-mobile d-none-desktop">\${song.artist}</span>
                        </div>
                    </td>
                    <td>\${song.artist}</td>
                    <td class="song-liked-col" onclick="event.stopPropagation(); toggleLikeSongFromList(event, \${idx}, 'home')">
                        <i class="\${heartClass}"></i>
                    </td>
                    <td class="song-actions-col" onclick="event.stopPropagation();">
                        <button class="action-menu-trigger" onclick="toggleDropdownMenu(event, \${song.id})"><i class="fa-solid fa-ellipsis-vertical"></i></button>
                        <div class="dropdown-menu" id="dropdown-\${song.id}">
                            <div class="dropdown-item" onclick="addSongToPlaylistWorkflow(\${song.id})"><i class="fa-solid fa-plus"></i> Tambah ke Playlist</div>
                            <div class="dropdown-item" onclick="deleteCustomSong(\${song.id})" style="color: #ff5555;"><i class="fa-solid fa-trash"></i> Hapus Lagu</div>
                        </div>
                    </td>
                \`;
                container.appendChild(tr);
            });
        }

        function renderSearchSongs() {
            filterSongs();
        }

        function filterSongs() {
            const query = document.getElementById('search-input').value.toLowerCase();
            const grid = document.getElementById('search-results-grid');
            grid.innerHTML = '';

            const allSongs = getAllSongs();
            const filtered = allSongs.filter(s => s.title.toLowerCase().includes(query) || s.artist.toLowerCase().includes(query));

            if (filtered.length === 0) {
                grid.innerHTML = '<div style="color: var(--text-grey); padding: 16px;">Tidak ada lagu yang cocok ditemukan.</div>';
                return;
            }

            filtered.forEach(song => {
                const card = document.createElement('div');
                card.className = 'quick-card';
                card.onclick = () => playSongDirectly(song);
                card.innerHTML = \`
                    <img src="\${song.coverUrl}" alt="Cover">
                    <div class="quick-card-title">
                        <div style="font-weight: 700; color: white;">\${song.title}</div>
                        <div style="font-size:12px; color: var(--text-grey); margin-top:4px;">\${song.artist}</div>
                    </div>
                    <button class="play-overlay-btn"><i class="fa-solid fa-play"></i></button>
                \`;
                grid.appendChild(card);
            });
        }

        function isSongLiked(song) {
            return localData.likedSongs.some(s => s.title.toLowerCase() === song.title.toLowerCase() && s.artist.toLowerCase() === song.artist.toLowerCase());
        }

        function renderLikedSongs() {
            const container = document.getElementById('liked-song-list');
            container.innerHTML = '';

            const likedCount = localData.likedSongs.length;
            document.getElementById('liked-songs-count').innerText = \`\${likedCount} lagu\`;

            if (likedCount === 0) {
                container.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--text-grey); padding: 32px;">Belum ada lagu yang Anda sukai. Ketuk ikon hati untuk menyukai!</td></tr>';
                return;
            }

            const allSongs = getAllSongs();
            const likedList = allSongs.filter(song => isSongLiked(song));

            likedList.forEach((song, idx) => {
                const tr = document.createElement('tr');
                tr.onclick = () => playSongDirectly(song);

                tr.innerHTML = \`
                    <td class="song-index">\${idx + 1}</td>
                    <td class="song-title-col">
                        <img src="\${song.coverUrl}" alt="Cover">
                        <div class="song-details-mini">
                            <span class="song-details-title">\${song.title}</span>
                            <span class="song-details-artist-mobile d-none-desktop">\${song.artist}</span>
                        </div>
                    </td>
                    <td>\${song.artist}</td>
                    <td class="song-liked-col" onclick="event.stopPropagation(); removeLikedSongDirectly(\${song.id})">
                        <i class="fa-solid fa-heart active"></i>
                    </td>
                    <td class="song-actions-col"></td>
                \`;
                container.appendChild(tr);
            });
        }

        function renderPlaylists() {
            const grid = document.getElementById('playlists-grid');
            const sidebarList = document.getElementById('sidebar-playlists-list');
            grid.innerHTML = '';
            sidebarList.innerHTML = '';

            if (localData.playlists.length === 0) {
                grid.innerHTML = '<div style="color: var(--text-grey); padding: 16px;">Belum ada playlist. Buat daftar putar pertama Anda!</div>';
            }

            localData.playlists.forEach((playlist, idx) => {
                const cover = playlist.coverUrl || "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=150";
                
                // Desktop Sidebar item
                const sidebarItem = document.createElement('div');
                sidebarItem.className = 'playlist-item';
                sidebarItem.onclick = () => { switchTab('playlists'); openPlaylistDetails(idx); };
                sidebarItem.innerHTML = \`
                    <img src="\${cover}" alt="Cover">
                    <div class="playlist-item-info">
                        <span class="playlist-item-name">\${playlist.name}</span>
                        <span class="playlist-item-desc">\${playlist.songs.length} lagu</span>
                    </div>
                \`;
                sidebarList.appendChild(sidebarItem);

                // Grid Main item
                const card = document.createElement('div');
                card.className = 'quick-card';
                card.onclick = () => openPlaylistDetails(idx);
                card.innerHTML = \`
                    <img src="\${cover}" alt="Cover">
                    <div class="quick-card-title">
                        <div style="font-weight: 700;">\${playlist.name}</div>
                        <div style="font-size: 11px; color: var(--text-grey); margin-top: 4px;">\${playlist.description || 'Daftar Putar Kustom'}</div>
                    </div>
                \`;
                grid.appendChild(card);
            });
        }

        function openPlaylistDetails(idx) {
            const playlist = localData.playlists[idx];
            if (!playlist) return;

            const detailView = document.getElementById('playlist-detail-view');
            detailView.classList.remove('d-none');
            document.getElementById('active-playlist-title').innerText = playlist.name;

            const listContainer = document.getElementById('playlist-song-list');
            listContainer.innerHTML = '';

            if (playlist.songs.length === 0) {
                listContainer.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--text-grey); padding: 24px;">Playlist ini kosong. Tambahkan lagu dari beranda!</td></tr>';
                return;
            }

            playlist.songs.forEach((song, songIdx) => {
                const tr = document.createElement('tr');
                tr.onclick = () => playSongDirectly(song);
                tr.innerHTML = \`
                    <td class="song-index">\${songIdx + 1}</td>
                    <td class="song-title-col">
                        <img src="\${song.coverUrl}" alt="Cover">
                        <div class="song-details-mini">
                            <span class="song-details-title">\${song.title}</span>
                            <span class="song-details-artist-mobile d-none-desktop">\${song.artist}</span>
                        </div>
                    </td>
                    <td>\${song.artist}</td>
                    <td></td>
                    <td onclick="event.stopPropagation(); removeSongFromPlaylist(\${idx}, \${songIdx})" style="color: #ff5555; text-align: center;"><i class="fa-solid fa-circle-minus"></i></td>
                \`;
                listContainer.appendChild(tr);
            });
        }

        function renderHistorySongs() {
            const container = document.getElementById('history-song-list');
            container.innerHTML = '';

            if (localData.playHistory.length === 0) {
                container.innerHTML = '<tr><td colspan="4" style="text-align: center; color: var(--text-grey); padding: 32px;">Mulai dengarkan musik untuk mengisi histori putar Anda!</td></tr>';
                return;
            }

            localData.playHistory.forEach((song, idx) => {
                const tr = document.createElement('tr');
                tr.onclick = () => playSongDirectly(song);
                tr.innerHTML = \`
                    <td class="song-index">\${idx + 1}</td>
                    <td class="song-title-col">
                        <img src="\${song.coverUrl}" alt="Cover">
                        <div class="song-details-mini">
                            <span class="song-details-title">\${song.title}</span>
                            <span class="song-details-artist-mobile d-none-desktop">\${song.artist}</span>
                        </div>
                    </td>
                    <td>\${song.artist}</td>
                    <td></td>
                \`;
                container.appendChild(tr);
            });
        }

        function renderSyncStatus() {
            const statusText = document.getElementById('sync-status-text');
            const statPlaylists = document.getElementById('stat-playlists');
            const statCustom = document.getElementById('stat-custom');
            const statLiked = document.getElementById('stat-liked');
            const statHistory = document.getElementById('stat-history');

            statPlaylists.innerText = localData.playlists.length;
            statCustom.innerText = localData.customSongs.length;
            statLiked.innerText = localData.likedSongs.length;
            statHistory.innerText = localData.playHistory.length;

            if (localData.currentUser) {
                statusText.innerHTML = \`Terhubung sebagai <b style="color: var(--spotify-green)">\${localData.currentUser.username}</b>. Terakhir sinkronisasi: \${localData.currentUser.lastSync || 'Belum pernah'}\`;
            } else {
                statusText.innerText = 'Gunakan akun Zorify untuk menyinkronkan data antar HP dan Web Player.';
            }
        }

        // --- AUTH DIALOG LOGIC ---
        function openAuthModal() {
            if (localData.currentUser) {
                if (confirm(\`Apakah Anda ingin keluar dari akun \${localData.currentUser.username}?\`)) {
                    localData.currentUser = null;
                    saveData();
                    refreshAllUIs();
                    alert("Berhasil keluar dari akun!");
                }
                return;
            }
            document.getElementById('auth-modal').style.display = 'flex';
        }

        function closeAuthModal() {
            document.getElementById('auth-modal').style.display = 'none';
        }

        function openGoogleChooser() {
            closeAuthModal();
            document.getElementById('google-modal').style.display = 'flex';
        }

        function closeGoogleChooser() {
            document.getElementById('google-modal').style.display = 'none';
        }

        function toggleGoogleEmailInput(show) {
            if (show) {
                document.getElementById('google-accounts-list').classList.add('d-none');
                document.getElementById('google-use-another-container').classList.add('d-none');
                document.getElementById('google-custom-email-form').style.display = 'flex';
                document.getElementById('google-custom-email-form').classList.remove('d-none');
            } else {
                document.getElementById('google-accounts-list').classList.remove('d-none');
                document.getElementById('google-use-another-container').classList.remove('d-none');
                document.getElementById('google-custom-email-form').style.display = 'none';
                document.getElementById('google-custom-email-form').classList.add('d-none');
            }
        }

        function selectGoogleAccount(email) {
            closeGoogleChooser();
            localData.currentUser = {
                username: email,
                avatarColorHex: "#1DB954",
                lastSync: "Menghubungkan..."
            };
            saveData();
            refreshAllUIs();
            alert('Berhasil masuk dengan Google: ' + email);
            
            // Trigger automatic cloud sync immediately to pull all existing playlists and songs for this user!
            triggerSyncWithServer();
        }

        function submitGoogleCustomEmail() {
            const email = document.getElementById('google-custom-email-input').value.trim();
            if (!email || !email.includes('@')) {
                alert("Harap masukkan email Google yang valid!");
                return;
            }
            selectGoogleAccount(email);
        }

        let isRegistering = false;
        function toggleAuthMode(reg) {
            isRegistering = reg;
            document.getElementById('auth-modal-title').innerText = reg ? "Daftar Akun Zorify" : "Masuk ke Zorify";
            document.getElementById('auth-submit-btn').innerText = reg ? "DAFTAR SEKARANG" : "MASUK";
            document.getElementById('auth-switch-prompt').innerHTML = reg ? 
                "Sudah punya akun? <span onclick='toggleAuthMode(false)'>Masuk di sini</span>" :
                "Belum punya akun? <span onclick='toggleAuthMode(true)'>Daftar Gratis</span>";
        }

        function submitAuthForm() {
            const user = document.getElementById('auth-username').value.trim();
            const pass = document.getElementById('auth-password').value;

            if (!user || !pass) {
                alert("Harap isi semua kolom!");
                return;
            }

            if (isRegistering) {
                // Since this is client-first sync architecture, register stores locally and is pushed to serverless sync DB
                localData.currentUser = {
                    username: user,
                    avatarColorHex: "#1DB954",
                    lastSync: "Belum pernah"
                };
                saveData();
                closeAuthModal();
                refreshAllUIs();
                alert(\`Pendaftaran berhasil! Akun \${user} dibuat.\`);
                // Automatically sync after registration
                triggerSyncWithServer();
            } else {
                localData.currentUser = {
                    username: user,
                    avatarColorHex: "#1DB954",
                    lastSync: "Tersambung"
                };
                saveData();
                closeAuthModal();
                refreshAllUIs();
                alert(\`Selamat datang kembali, \${user}!\`);
                // Pull data automatically on login
                triggerSyncWithServer();
            }
        }

        // --- PLAYBACK SYSTEM CONTROLS ---
        function playDiscoverWeekly() {
            const allSongs = getAllSongs();
            if (allSongs.length > 0) {
                const randomIndex = Math.floor(Math.random() * allSongs.length);
                playSongDirectly(allSongs[randomIndex]);
                alert("Memutar playlist Discover Weekly Anda!");
            }
        }

        function playSongDirectly(song) {
            // Check if song matches current song
            const playerTitle = document.getElementById('player-title');
            const playerCover = document.getElementById('player-cover');

            if (audio.src !== song.remoteUrl) {
                audio.src = song.remoteUrl;
                parsedLyrics = parseLRC(song.lyrics);
                renderLyricsPanel(song);
            }

            document.getElementById('player-title').innerText = song.title;
            document.getElementById('player-artist').innerText = song.artist;
            document.getElementById('player-cover').src = song.coverUrl;

            // Highlight liked button
            const isLiked = isSongLiked(song);
            const playerLikeBtn = document.getElementById('player-like-btn');
            if (isLiked) {
                playerLikeBtn.classList.add('liked');
                playerLikeBtn.innerHTML = '<i class="fa-solid fa-heart"></i>';
            } else {
                playerLikeBtn.classList.remove('liked');
                playerLikeBtn.innerHTML = '<i class="fa-regular fa-heart"></i>';
            }

            audio.play()
                .then(() => {
                    isPlaying = true;
                    document.getElementById('ctrl-play-pause').innerHTML = '<i class="fa-solid fa-pause"></i>';
                    document.getElementById('player-cover').classList.add('playing');
                })
                .catch(e => {
                    console.error("Playback error:", e);
                });

            // Save to play history
            const history = localData.playHistory;
            const updated = history.filter(s => s.title !== song.title || s.artist !== song.artist);
            updated.unshift(song);
            localData.playHistory = updated.slice(0, 50); // Keep max 50 songs
            saveData();
            renderHistorySongs();

            // Set current index in queue
            const allSongs = getAllSongs();
            queue = [...allSongs];
            currentQueueIndex = queue.findIndex(s => s.title === song.title && s.artist === song.artist);
        }

        function togglePlayPause() {
            if (getAllSongs().length === 0) return;
            
            if (currentQueueIndex === -1) {
                // Play first song
                playSongDirectly(getAllSongs()[0]);
                return;
            }

            if (isPlaying) {
                audio.pause();
                isPlaying = false;
                document.getElementById('ctrl-play-pause').innerHTML = '<i class="fa-solid fa-play"></i>';
                document.getElementById('player-cover').classList.remove('playing');
            } else {
                audio.play()
                    .then(() => {
                        isPlaying = true;
                        document.getElementById('ctrl-play-pause').innerHTML = '<i class="fa-solid fa-pause"></i>';
                        document.getElementById('player-cover').classList.add('playing');
                    })
                    .catch(e => console.error(e));
            }
        }

        function playNext() {
            if (queue.length === 0) return;
            let nextIndex = currentQueueIndex + 1;

            if (isShuffle) {
                nextIndex = Math.floor(Math.random() * queue.length);
            } else if (nextIndex >= queue.length) {
                nextIndex = 0; // Wrap around
            }

            if (queue[nextIndex]) {
                playSongDirectly(queue[nextIndex]);
            }
        }

        function playPrev() {
            if (queue.length === 0) return;
            let prevIndex = currentQueueIndex - 1;

            if (isShuffle) {
                prevIndex = Math.floor(Math.random() * queue.length);
            } else if (prevIndex < 0) {
                prevIndex = queue.length - 1; // Wrap to last
            }

            if (queue[prevIndex]) {
                playSongDirectly(queue[prevIndex]);
            }
        }

        function toggleShuffle() {
            isShuffle = !isShuffle;
            const shuffleBtn = document.getElementById('ctrl-shuffle');
            if (isShuffle) {
                shuffleBtn.classList.add('active');
            } else {
                shuffleBtn.classList.remove('active');
            }
        }

        function toggleRepeat() {
            repeatMode = (repeatMode + 1) % 3;
            const repeatBtn = document.getElementById('ctrl-repeat');
            if (repeatMode === 1) {
                repeatBtn.classList.add('active');
                repeatBtn.title = "Ulangi Semua Lagu";
                repeatBtn.innerHTML = '<i class="fa-solid fa-repeat"></i>';
            } else if (repeatMode === 2) {
                repeatBtn.classList.add('active');
                repeatBtn.title = "Ulangi Satu Lagu";
                repeatBtn.innerHTML = '<i class="fa-solid fa-repeat" style="position:relative;"></i><span style="position:absolute; font-size:9px; top:-2px; right:8px; font-weight:bold;">1</span>';
            } else {
                repeatBtn.classList.remove('active');
                repeatBtn.title = "Ulang lagu";
                repeatBtn.innerHTML = '<i class="fa-solid fa-repeat"></i>';
            }
        }

        function onSongEnded() {
            if (repeatMode === 2) {
                // Repeat one
                audio.currentTime = 0;
                audio.play();
            } else if (repeatMode === 1 || currentQueueIndex < queue.length - 1) {
                playNext();
            } else {
                // End of queue
                isPlaying = false;
                document.getElementById('ctrl-play-pause').innerHTML = '<i class="fa-solid fa-play"></i>';
                document.getElementById('player-cover').classList.remove('playing');
            }
        }

        // --- VOLUMES & SEEK SLIDERS ---
        function updatePlaybackProgress() {
            if (!audio.duration) return;
            const progressPct = (audio.currentTime / audio.duration) * 100;
            document.getElementById('player-seek-progress').style.width = progressPct + '%';

            // Format times
            document.getElementById('time-elapsed').innerText = formatTime(audio.currentTime);

            // Update scrolling lyric line
            highlightActiveLyricLine();
        }

        function updatePlaybackDuration() {
            if (!audio.duration) return;
            document.getElementById('time-duration').innerText = formatTime(audio.duration);
        }

        function formatTime(secs) {
            const mins = Math.floor(secs / 60);
            const remaining = Math.floor(secs % 60);
            return mins + ':' + (remaining < 10 ? '0' : '') + remaining;
        }

        function seekAudio(event) {
            const rect = event.currentTarget.getBoundingClientRect();
            const clickPosition = (event.clientX - rect.left) / rect.width;
            if (audio.duration) {
                audio.currentTime = clickPosition * audio.duration;
            }
        }

        function changeVolume(event) {
            const rect = event.currentTarget.getBoundingClientRect();
            const clickPosition = (event.clientX - rect.left) / rect.width;
            const volume = Math.max(0, Math.min(1, clickPosition));
            audio.volume = volume;
            document.getElementById('player-volume-progress').style.width = (volume * 100) + '%';
        }

        // --- LYRIC PLAYER LRC PARSING & ANIMATION ---
        function parseLRC(lrcText) {
            if (!lrcText) return [];
            const lines = lrcText.split('\\n');
            const result = [];
            
            const regex = /\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)/;

            lines.forEach(line => {
                const match = line.match(regex);
                if (match) {
                    const mins = parseInt(match[1]);
                    const secs = parseInt(match[2]);
                    const ms = parseInt(match[3]);
                    const text = match[4].trim();

                    const timeMs = (mins * 60 + secs) * 1000 + ms * 10;
                    result.push({ timeMs, text });
                } else if (line.trim().length > 0) {
                    // Line without stamp
                    result.push({ timeMs: 0, text: line });
                }
            });

            return result.sort((a,b) => a.timeMs - b.timeMs);
        }

        function renderLyricsPanel(song) {
            const scrollBox = document.getElementById('lyrics-panel-scroll');
            scrollBox.innerHTML = '';

            document.getElementById('lyrics-panel-title').innerText = song.title;
            document.getElementById('lyrics-panel-artist').innerText = song.artist;

            if (parsedLyrics.length === 0) {
                scrollBox.innerHTML = '<div style="font-size:18px; color:var(--text-grey);">Tidak ada lirik untuk lagu ini.</div>';
                return;
            }

            parsedLyrics.forEach((lyric, idx) => {
                const lineDiv = document.createElement('div');
                lineDiv.className = 'lyric-line';
                lineDiv.id = 'lyric-line-' + idx;
                lineDiv.innerText = lyric.text;
                lineDiv.onclick = () => {
                    if (lyric.timeMs > 0) {
                        audio.currentTime = lyric.timeMs / 1000;
                    }
                };
                scrollBox.appendChild(lineDiv);
            });
        }

        function highlightActiveLyricLine() {
            if (parsedLyrics.length === 0) return;
            const currentTimeMs = audio.currentTime * 1000;

            let activeIndex = -1;
            for (let i = 0; i < parsedLyrics.length; i++) {
                if (parsedLyrics[i].timeMs <= currentTimeMs) {
                    activeIndex = i;
                } else {
                    break;
                }
            }

            if (activeIndex !== -1) {
                // Clear previous actives
                document.querySelectorAll('.lyric-line').forEach(el => el.classList.remove('active'));
                
                // Set current active
                const activeLine = document.getElementById('lyric-line-' + activeIndex);
                if (activeLine) {
                    activeLine.classList.add('active');
                    
                    // Smoothly scroll active lyric to vertical center
                    const scrollBox = document.getElementById('lyrics-panel-scroll');
                    const scrollBoxHeight = scrollBox.clientHeight;
                    const activeLineOffsetTop = activeLine.offsetTop;
                    const activeLineHeight = activeLine.clientHeight;

                    scrollBox.scrollTop = activeLineOffsetTop - (scrollBoxHeight / 2) + (activeLineHeight / 2);
                }
            }
        }

        let isLyricsPanelOpen = false;
        function toggleLyricsPanel() {
            isLyricsPanelOpen = !isLyricsPanelOpen;
            const panel = document.getElementById('lyrics-overlay-panel');
            const btn = document.getElementById('player-lyrics-btn');

            if (isLyricsPanelOpen) {
                panel.style.display = 'flex';
                btn.classList.add('active');
                if (queue[currentQueueIndex]) {
                    renderLyricsPanel(queue[currentQueueIndex]);
                }
            } else {
                panel.style.display = 'none';
                btn.classList.remove('active');
            }
        }

        // --- PLAYLIST CREATOR DIALOG ---
        function openCreatePlaylistModal() {
            document.getElementById('playlist-modal').style.display = 'flex';
        }

        function closeCreatePlaylistModal() {
            document.getElementById('playlist-modal').style.display = 'none';
        }

        function submitCreatePlaylist() {
            const name = document.getElementById('playlist-name-input').value.trim();
            const desc = document.getElementById('playlist-desc-input').value.trim();
            const cover = document.getElementById('playlist-cover-input').value.trim();

            if (!name) {
                alert("Nama playlist harus diisi!");
                return;
            }

            localData.playlists.push({
                name: name,
                description: desc,
                coverUrl: cover,
                songs: []
            });

            saveData();
            closeCreatePlaylistModal();
            refreshAllUIs();
            alert("Daftar Putar berhasil dibuat!");
        }

        function removeSongFromPlaylist(plIdx, songIdx) {
            localData.playlists[plIdx].songs.splice(songIdx, 1);
            saveData();
            refreshAllUIs();
            openPlaylistDetails(plIdx);
        }

        let songToAddToPlaylist = null;
        function addSongToPlaylistWorkflow(songId) {
            const all = getAllSongs();
            const song = all.find(s => s.id === songId);
            if (!song) return;

            if (localData.playlists.length === 0) {
                alert("Anda belum memiliki daftar putar. Silakan buat daftar putar baru dari Koleksi!");
                return;
            }

            const names = localData.playlists.map((p, i) => \`\${i + 1}. \${p.name}\`).join('\\n');
            const choice = prompt(\`Pilih playlist untuk ditambahkan lagu "\${song.title}":\\n\\n\${names}\\n\\nMasukkan nomor:\`);
            
            if (choice) {
                const idx = parseInt(choice) - 1;
                const pl = localData.playlists[idx];
                if (pl) {
                    // Check if duplicate
                    const dup = pl.songs.some(s => s.title === song.title && s.artist === song.artist);
                    if (dup) {
                        alert("Lagu sudah ada di dalam playlist ini!");
                        return;
                    }
                    pl.songs.push(song);
                    saveData();
                    refreshAllUIs();
                    alert(\`Lagu ditambahkan ke daftar putar "\${pl.name}"!\`);
                } else {
                    alert("Pilihan tidak valid.");
                }
            }
        }

        function deleteCustomSong(songId) {
            if (songId <= 4) {
                alert("Lagu bawaan sistem tidak bisa dihapus!");
                return;
            }

            if (confirm("Apakah Anda yakin ingin menghapus lagu ini secara permanen dari Web Player?")) {
                localData.customSongs = localData.customSongs.filter(s => s.id !== songId);
                saveData();
                refreshAllUIs();
                alert("Lagu berhasil dihapus!");
            }
        }

        // --- LIKE BUTTON LOGICS ---
        function toggleLikeCurrentSong() {
            if (currentQueueIndex === -1) return;
            const song = queue[currentQueueIndex];
            
            const isLiked = isSongLiked(song);
            const btn = document.getElementById('player-like-btn');

            if (isLiked) {
                // Unlike
                localData.likedSongs = localData.likedSongs.filter(s => s.title.toLowerCase() !== song.title.toLowerCase() || s.artist.toLowerCase() !== song.artist.toLowerCase());
                btn.classList.remove('liked');
                btn.innerHTML = '<i class="fa-regular fa-heart"></i>';
            } else {
                // Like
                localData.likedSongs.push({ title: song.title, artist: song.artist });
                btn.classList.add('liked');
                btn.innerHTML = '<i class="fa-solid fa-heart"></i>';
            }

            saveData();
            refreshAllUIs();
        }

        function toggleLikeSongFromList(event, index, source) {
            const song = getAllSongs()[index];
            if (!song) return;

            const isLiked = isSongLiked(song);
            if (isLiked) {
                localData.likedSongs = localData.likedSongs.filter(s => s.title.toLowerCase() !== song.title.toLowerCase() || s.artist.toLowerCase() !== song.artist.toLowerCase());
            } else {
                localData.likedSongs.push({ title: song.title, artist: song.artist });
            }

            saveData();
            refreshAllUIs();
        }

        function removeLikedSongDirectly(songId) {
            const all = getAllSongs();
            const song = all.find(s => s.id === songId);
            if (!song) return;

            localData.likedSongs = localData.likedSongs.filter(s => s.title.toLowerCase() !== song.title.toLowerCase() || s.artist.toLowerCase() !== song.artist.toLowerCase());
            saveData();
            refreshAllUIs();
        }

        // --- ADMIN ADD SONG LOGIC ---
        function adminAddSong() {
            const title = document.getElementById('admin-title').value.trim();
            const artist = document.getElementById('admin-artist').value.trim();
            const url = document.getElementById('admin-url').value.trim();
            const cover = document.getElementById('admin-cover').value.trim();
            const lyrics = document.getElementById('admin-lyrics').value.trim();

            if (!title || !artist || !url) {
                alert("Judul, Artis, dan URL Audio wajib diisi!");
                return;
            }

            const all = getAllSongs();
            const nextId = all.length > 0 ? Math.max(...all.map(s => s.id)) + 1 : 5;

            const newSong = {
                id: nextId,
                title: title,
                artist: artist,
                remoteUrl: url,
                coverUrl: cover || "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=150",
                lyrics: lyrics || "[00:00.00] Zorify Premium Audio • " + title + "\\n[00:05.00] Terima kasih telah mendengarkan."
            };

            localData.customSongs.push(newSong);
            saveData();

            // Clear inputs
            document.getElementById('admin-title').value = '';
            document.getElementById('admin-artist').value = '';
            document.getElementById('admin-url').value = '';
            document.getElementById('admin-cover').value = '';
            document.getElementById('admin-lyrics').value = '';

            refreshAllUIs();
            alert("Lagu baru '" + title + "' sukses disimpan!");
            switchTab('home');
        }

        // --- DROPDOWN ACTION MENU ---
        let openDropdownId = null;
        function toggleDropdownMenu(event, id) {
            event.stopPropagation();
            const menu = document.getElementById('dropdown-' + id);
            
            // Close existing
            if (openDropdownId && openDropdownId !== id) {
                const prevMenu = document.getElementById('dropdown-' + openDropdownId);
                if (prevMenu) prevMenu.style.display = 'none';
            }

            if (menu.style.display === 'flex') {
                menu.style.display = 'none';
                openDropdownId = null;
            } else {
                menu.style.display = 'flex';
                openDropdownId = id;
            }
        }

        // Close dropdowns on document click
        document.addEventListener('click', () => {
            if (openDropdownId) {
                const menu = document.getElementById('dropdown-' + openDropdownId);
                if (menu) menu.style.display = 'none';
                openDropdownId = null;
            }
        });

        // --- SYNCHRONIZATION WITH CLOUD SERVER ---
        function triggerSyncWithServer() {
            if (!localData.currentUser) {
                alert("Harap masuk akun terlebih dahulu sebelum melakukan sinkronisasi awan!");
                openAuthModal();
                return;
            }

            const btn = document.getElementById('btn-sync-action');
            btn.disabled = true;
            btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> MENYINKRONKAN...';

            const payload = {
                username: localData.currentUser.username,
                avatarColorHex: localData.currentUser.avatarColorHex,
                customSongs: localData.customSongs,
                likedSongs: localData.likedSongs,
                playlists: localData.playlists,
                playHistory: localData.playHistory
            };

            fetch('/api/sync/push', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            })
            .then(res => {
                if (!res.ok) throw new Error("HTTP status " + res.status);
                return res.json();
            })
            .then(data => {
                if (data.success) {
                    // Update Local State with server's merged state!
                    localData.customSongs = data.customSongs || [];
                    localData.likedSongs = data.likedSongs || [];
                    localData.playlists = data.playlists || [];
                    localData.playHistory = data.playHistory || [];
                    
                    const now = new Date();
                    localData.currentUser.lastSync = now.toLocaleDateString('id-ID') + ' ' + now.toLocaleTimeString('id-ID');
                    
                    saveData();
                    refreshAllUIs();
                    alert("Sinkronisasi Awan Berhasil! Lagu, playlist, dan favorit Anda kini sinkron.");
                } else {
                    alert("Error dari server: " + data.message);
                }
            })
            .catch(err => {
                console.error("Sync error:", err);
                alert("Koneksi gagal: " + err.message + ". Pastikan server sinkronisasi menyala.");
            })
            .finally(() => {
                btn.disabled = false;
                btn.innerHTML = '<i class="fa-solid fa-rotate"></i> SINKRONKAN DATA SEKARANG';
            });
        }

        // --- WINDOW ONLOAD INITIALIZER ---
        window.onload = initApp;
    </script>
</body>
</html>
`;

  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(webPlayerHtml);
});

function startServer(portIndex) {
  if (portIndex >= uniquePorts.length) {
    console.error('Error: Could not start server on any target port.');
    process.exit(1);
  }

  const currentPort = uniquePorts[portIndex];
  PORT = currentPort;

  console.log(`Trying to start Zorify downloader and sync server on port ${currentPort}...`);

  server.once('error', (err) => {
    if (err.code === 'EADDRINUSE') {
      console.warn(`Port ${currentPort} is already in use, trying next port...`);
      startServer(portIndex + 1);
    } else {
      console.error(`Failed to start server on port ${currentPort}:`, err);
      startServer(portIndex + 1);
    }
  });

  server.listen(currentPort, '0.0.0.0', () => {
    server.removeAllListeners('error');
    console.log(`Zorify downloader and sync server running at http://0.0.0.0:${currentPort}`);
  });
}

startServer(0);
