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
    def test_provider_deadline_returns_excerpts(self):
        import asyncio
        async def slow(*args):
            await asyncio.sleep(1)
            return 'Late answer [p.1]', 'openrouter'
        with patch('main.complete', new=slow), patch('main.AI_DEADLINE_SECONDS', 0.01):
            response = self.client.post('/ask', json=self.payload, headers=self.headers)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()['provider'], 'extractive')
        self.assertEqual(response.json()['sources'][0]['page'], 1)

    def test_followup_retrieves_evidence_from_previous_question(self):
        self.payload.update(question='What about it?', history=[{'question':'When is payment due?', 'answer':'10 October [p.1]'}])
        with patch('main.complete', new=AsyncMock(return_value=('The payment is due on 10 October [p.1]', 'openrouter'))) as provider:
            result=self.client.post('/ask',json=self.payload,headers=self.headers)
        self.assertEqual(result.status_code,200)
        self.assertEqual(result.json()['sources'][0]['page'],1)
        self.assertIn('CURRENT question',provider.call_args.args[1])

    def test_conversation_history_is_bounded(self):
        self.payload['history']=[{'question':'Q','answer':'A'}]*5
        self.assertEqual(self.client.post('/ask',json=self.payload,headers=self.headers).status_code,422)

    def test_fast_provider_wins_and_slow_provider_is_cancelled(self):
        import asyncio
        from main import complete
        cancelled=[]
        async def provider(context,question,language,name,*args):
            if name=='groq':
                return 'Payment is due [p.1]'
            try:
                await asyncio.sleep(5)
            except asyncio.CancelledError:
                cancelled.append(name)
                raise
        with patch.dict(os.environ,{'OPENROUTER_API_KEY':'test','GROQ_API_KEY':'test'}), patch('main.complete_with_provider',new=provider):
            result=asyncio.run(complete('[p.1] Payment due tomorrow','When?','English'))
        self.assertEqual(result[1],'groq')
        self.assertIn('openrouter',cancelled)

    def test_rate_limit(self):
        self.payload['question'] = 'bananas'
        for _ in range(10):
            self.client.post('/ask', json=self.payload, headers=self.headers)
        self.assertEqual(self.client.post('/ask', json=self.payload, headers=self.headers).status_code, 429)

if __name__ == '__main__': unittest.main()
