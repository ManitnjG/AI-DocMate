import os
import tempfile
import unittest
from unittest.mock import AsyncMock, patch
from fastapi.testclient import TestClient
from main import app
import sessions

class SessionsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.env = patch.dict(os.environ, {'DOCMATE_PUBLIC_SESSIONS': 'true', 'DOCMATE_SESSION_SECRET': 's' * 48, 'OPENROUTER_API_KEY': 'test-only', 'DOCMATE_QUOTA_DB': self.tmp.name + '/quota.db', 'DOCMATE_ACCESS_TOKENS': '', 'DOCMATE_DAILY_REQUESTS': '100'})
        self.env.start()
        self.client = TestClient(app)
    def tearDown(self):
        self.env.stop()
        self.tmp.cleanup()
    def token(self):
        r = self.client.post('/session')
        self.assertEqual(r.status_code, 200)
        return r.json()['access_token']
    def test_automatic_session_asks_without_owner_token(self):
        token = self.token()
        with patch('main.complete', new=AsyncMock(return_value=('Payment is due tomorrow [p.1]', 'openrouter'))):
            r = self.client.post('/ask', headers={'Authorization': 'Bearer ' + token}, json={'pages': [{'number': 1, 'text': 'Payment due tomorrow.'}], 'question': 'payment'})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()['provider'], 'openrouter')
    def test_tampering(self):
        self.assertIsNone(sessions.verify(self.token() + 'a'))
    def test_expiry(self):
        token = self.token()
        with patch('sessions.time.time', return_value=9999999999):
            self.assertIsNone(sessions.verify(token))
    def test_expired_session_responds_401(self):
        r = self.client.post('/ask', headers={'Authorization': 'Bearer invalid'}, json={'pages': [{'number': 1, 'text': 'Payment'}], 'question': 'payment'})
        self.assertEqual(r.status_code, 401)
    def test_missing_key_fails_closed(self):
        with patch.dict(os.environ, {'OPENROUTER_API_KEY': ''}):
            self.assertEqual(self.client.post('/session').status_code, 503)
    def test_public_sessions_opt_in(self):
        with patch.dict(os.environ, {'DOCMATE_PUBLIC_SESSIONS': 'false'}):
            self.assertEqual(self.client.post('/session').status_code, 503)
    def test_session_creation_limited(self):
        for _ in range(5): self.token()
        self.assertEqual(self.client.post('/session').status_code, 429)
    def test_global_budget_across_sessions(self):
        one, two = self.token(), self.token()
        with patch.dict(os.environ, {'DOCMATE_DAILY_REQUESTS': '1'}), patch('main.complete', new=AsyncMock(return_value=(None, None))):
            data = {'pages': [{'number': 1, 'text': 'Payment tomorrow'}], 'question': 'payment'}
            self.assertEqual(self.client.post('/ask', json=data, headers={'Authorization': 'Bearer ' + one}).status_code, 200)
            self.assertEqual(self.client.post('/ask', json=data, headers={'Authorization': 'Bearer ' + two}).status_code, 429)
