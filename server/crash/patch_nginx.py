#!/usr/bin/env python3
"""Idempotently insert the 橘子岛 /crash location into the example.com nginx site.

Adds a `location = /crash { ... }` block immediately before each `location / {` in the
config (once per :80 and :443 server block). Safe to re-run: does nothing if /crash is
already present.
"""
import sys

PATH = "/etc/nginx/sites-available/example.com"
ANCHOR = "    location / {"
BLOCK = """    location = /crash {
        limit_except POST { deny all; }
        client_max_body_size 64k;
        proxy_pass http://127.0.0.1:8092/;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

"""

with open(PATH, "r", encoding="utf-8") as f:
    text = f.read()

if "location = /crash" in text:
    print("already present; no change")
    sys.exit(0)

if ANCHOR not in text:
    print("ERROR: anchor not found", file=sys.stderr)
    sys.exit(2)

patched = text.replace(ANCHOR, BLOCK + ANCHOR)
with open(PATH, "w", encoding="utf-8") as f:
    f.write(patched)
print("patched %d block(s)" % patched.count("location = /crash"))
