import http.server, json, os, sys
FLAG = sys.argv[1]
class H(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        self.rfile.read(int(self.headers.get('Content-Length', 0)))
        mode = open(FLAG).read().strip() if os.path.exists(FLAG) else "ok"
        if mode == "busy":
            self.send_response(429); self.end_headers(); self.wfile.write(b'{"error":"rate"}'); return
        if mode == "slow":
            import time; time.sleep(1.5)
        if mode == "bad":
            self.send_response(400); self.end_headers(); self.wfile.write(b'{"error":"bad req"}'); return
        b = json.dumps({"choices":[{"message":{"role":"assistant","content":"hello from mock"}}]}).encode()
        self.send_response(200); self.send_header("Content-Type","application/json"); self.end_headers(); self.wfile.write(b)
    def log_message(self,*a): pass
http.server.HTTPServer(("127.0.0.1", 8799), H).serve_forever()
