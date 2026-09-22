"""Opt-in anonymous pilot sessions with durable global and client quotas."""
import base64
import hashlib
import hmac
import json
import os
import secrets
import sqlite3
import time
from fastapi import HTTPException, Request


def enabled():
    return os.getenv('DOCMATE_PUBLIC_SESSIONS') == 'true'


def signing_key():
    key = os.getenv('DOCMATE_SESSION_SECRET', '')
    if len(key) < 32:
        raise HTTPException(503, 'AI service is not ready yet')
    return key.encode()


def client_id(request: Request):
    # Use the actual ASGI client supplied by the trusted proxy configuration.
    # Never trust arbitrary X-Forwarded-For headers in application code.
    address = request.client.host if request.client else 'unknown'
    return hmac.new(signing_key(), address.encode(), hashlib.sha256).hexdigest()


def quota(bucket, limit, period):
    now = int(time.time())
    window = now // period
    with sqlite3.connect(os.getenv('DOCMATE_QUOTA_DB', '/tmp/docmate-quota.sqlite3'), timeout=10) as db:
        db.execute('CREATE TABLE IF NOT EXISTS usage (bucket TEXT, window INTEGER, count INTEGER, PRIMARY KEY(bucket, window))')
        db.execute('BEGIN IMMEDIATE')
        db.execute('DELETE FROM usage WHERE window < ? AND bucket = ?', (window - 1, bucket))
        db.execute('INSERT OR IGNORE INTO usage VALUES (?, ?, 0)', (bucket, window))
        count = db.execute('SELECT count FROM usage WHERE bucket=? AND window=?', (bucket, window)).fetchone()[0]
        if count >= limit:
            raise HTTPException(429, 'Daily AI allowance reached. Please try again later.', headers={'Retry-After': str(period - now % period)})
        db.execute('UPDATE usage SET count=count+1 WHERE bucket=? AND window=?', (bucket, window))


def issue(request: Request):
    if not enabled() or not os.getenv('OPENROUTER_API_KEY'):
        raise HTTPException(503, 'AI service is not ready yet')
    identity = client_id(request)
    quota('session-global', 1000, 86400)
    quota('session-' + identity, 5, 3600)
    payload = {'sub': secrets.token_hex(16), 'exp': int(time.time()) + 86400}
    encoded = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip('=')
    signature = hmac.new(signing_key(), encoded.encode(), hashlib.sha256).hexdigest()
    return {'access_token': encoded + '.' + signature, 'expires_in': 86400}


def verify(token):
    if not enabled():
        return None
    try:
        encoded, signature = token.split('.')
        if len(token) > 1024:
            return None
        expected = hmac.new(signing_key(), encoded.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(signature, expected):
            return None
        data = json.loads(base64.urlsafe_b64decode(encoded + '=' * (-len(encoded) % 4)))
        if data['exp'] <= time.time() or not isinstance(data['sub'], str):
            return None
        return data['sub']
    except (ValueError, KeyError, TypeError):
        return None


def enforce(request, subject):
    # Reserve quota before provider calls, including failed calls/retries.
    quota('ai-global', int(os.getenv('DOCMATE_DAILY_REQUESTS', '100')), 86400)
    quota('ai-client-' + client_id(request), 20, 86400)
    quota('ai-session-' + subject, 5, 60)
