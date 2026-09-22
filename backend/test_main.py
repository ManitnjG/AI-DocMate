import os
import unittest
from unittest.mock import AsyncMock, patch
from fastapi.testclient import TestClient
from main import app, requests

class ApiTests(unittest.TestCase):
    def setUp(self):
        os.environ['DOCMATE_ACCESS_TOKENS'] = 'test-token'
        requests.clear()
        self.client = TestClient(app)
        self.headers = {'Authorization': 'Bearer test-token'}
        self.payload = {'pages': [{'number': 1, 'text': 'Payment of INR 500 is due on 10 October.'}], 'question': 'When is payment due?'}
    def test_requires_auth(self):
        self.assertEqual(self.client.post('/ask', json=self.payload).status_code, 401)
    def test_no_matching_evidence(self):
        self.payload['question'] = 'apples bananas'
        r = self.client.post('/ask', json=self.payload, headers=self.headers)
        self.assertEqual(r.json()['sources'], [])
    def test_fallback_and_pages(self):
        with patch('main.complete', new=AsyncMock(return_value=(None, None))):
            r = self.client.post('/ask', json=self.payload, headers=self.headers)
        self.assertEqual(r.json()['provider'], 'extractive')
        self.assertEqual(r.json()['sources'][0]['page'], 1)
    def test_invalid_citation_rejected(self):
        with patch('main.complete', new=AsyncMock(return_value=('October [p.99]', 'test-provider'))):
            r = self.client.post('/ask', json=self.payload, headers=self.headers)
        self.assertEqual(r.json()['provider'], 'extractive')
    def test_valid_provider_reported(self):
        with patch('main.complete', new=AsyncMock(return_value=('Payment is due on 10 October [p.1]', 'test-provider'))):
            r = self.client.post('/ask', json=self.payload, headers=self.headers)
        self.assertEqual(r.json()['provider'], 'test-provider')
    def test_late_page_retrieved(self):
        self.payload['pages'] = [{'number': 1, 'text': 'Introduction. ' * 6000}, {'number': 80, 'text': 'Payment due tomorrow.'}]
        with patch('main.complete', new=AsyncMock(return_value=(None, None))):
            r = self.client.post('/ask', json=self.payload, headers=self.headers)
        self.assertEqual(r.json()['sources'][0]['page'], 80)
    def test_long_summary_explicit_error(self):
        self.payload.update(mode='summary', pages=[{'number': 1, 'text': 'abc ' * 15000}])
        self.assertEqual(self.client.post('/ask', json=self.payload, headers=self.headers).status_code, 422)
    def test_rate_limit(self):
        self.payload['question'] = 'bananas'
        for _ in range(10):
            self.client.post('/ask', json=self.payload, headers=self.headers)
        self.assertEqual(self.client.post('/ask', json=self.payload, headers=self.headers).status_code, 429)

if __name__ == '__main__': unittest.main()
