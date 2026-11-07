import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const WEB_ROOT = path.join(__dirname, 'build', 'web');
const PORT = 5000;
const BACKEND_HOST = '127.0.0.1';
const BACKEND_PORT = 8080;

// Prevent any socket/stream error from terminating the server process
process.on('uncaughtException', (err) => {
  console.error('[SERVER UNCAUGHT EXCEPTION]:', err.message);
});
process.on('unhandledRejection', (reason) => {
  console.error('[SERVER UNHANDLED REJECTION]:', reason);
});

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.wasm': 'application/wasm',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.otf': 'font/otf',
};

const server = http.createServer((req, res) => {
  req.on('error', (err) => {
    console.error('[REQ ERROR]:', err.message);
  });
  res.on('error', (err) => {
    console.error('[RES ERROR]:', err.message);
  });

  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = url.pathname;

  // 1. Proxy API requests directly to Spring Boot backend
  if (pathname.startsWith('/api/') || pathname === '/api' || pathname.startsWith('/ws')) {
    const proxyHeaders = { ...req.headers };
    proxyHeaders.host = `${BACKEND_HOST}:${BACKEND_PORT}`;

    const proxyReq = http.request(
      {
        host: BACKEND_HOST,
        port: BACKEND_PORT,
        path: req.url,
        method: req.method,
        headers: proxyHeaders,
      },
      (proxyRes) => {
        res.writeHead(proxyRes.statusCode, proxyRes.headers);
        proxyRes.pipe(res);
        console.log(`[PROXY] ${req.method} ${pathname} -> ${proxyRes.statusCode}`);
      }
    );

    proxyReq.on('error', (err) => {
      console.error(`[PROXY ERROR] ${req.method} ${pathname}:`, err.message);
      if (!res.headersSent) {
        res.writeHead(502, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Backend unreachable', details: err.message }));
      }
    });

    req.pipe(proxyReq);
    return;
  }

  // 2. Serve static Flutter Web files
  let safePath = path.normalize(pathname).replace(/^(\.\.[\/\\])+/, '');
  if (safePath === '/' || safePath === '\\') safePath = '/index.html';
  let filePath = path.join(WEB_ROOT, safePath);

  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    filePath = path.join(WEB_ROOT, 'index.html');
  }

  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';
    res.writeHead(200, {
      'Content-Type': contentType,
      'Cache-Control': ext === '.html' ? 'no-cache' : 'public, max-age=3600',
    });
    fs.createReadStream(filePath).pipe(res);
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('Not Found');
});

// Proxy WebSocket upgrades to Spring Boot STOMP broker
server.on('upgrade', (req, socket, head) => {
  socket.on('error', (err) => {
    // Gracefully handle client socket abrupt disconnects
  });

  const proxySocket = net.connect(BACKEND_PORT, BACKEND_HOST, () => {
    proxySocket.write(
      `${req.method} ${req.url} HTTP/${req.httpVersion}\r\n` +
        Object.keys(req.headers)
          .map((h) => `${h}: ${req.headers[h]}`)
          .join('\r\n') +
        '\r\n\r\n'
    );
    proxySocket.pipe(socket);
    socket.pipe(proxySocket);
  });

  proxySocket.on('error', (err) => {
    socket.destroy();
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[CASEGen Mobile Web] Listening on http://0.0.0.0:${PORT}`);
  console.log(`[CASEGen Mobile Web] Proxying /api -> http://${BACKEND_HOST}:${BACKEND_PORT}`);
});
