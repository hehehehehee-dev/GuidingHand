"""Exercise the pinned Firestore SDK; mock only its network boundary."""
import copy
from decimal import Decimal
import unittest
from unittest.mock import Mock

from google.auth.credentials import AnonymousCredentials
from google.cloud import firestore
from google.cloud.firestore_v1 import _helpers, types

from gateway import FirestoreBudget
from speech_input import SpeechInput
from test_speech_input import frame


class FirestoreSdkTests(unittest.TestCase):
    def setUp(self):
        self.client = firestore.Client(project='fixture-project', database='guidinghand',
                                       credentials=AnonymousCredentials())
        self.api = Mock()
        self.client._firestore_api_internal = self.api
        self.api.begin_transaction.return_value = types.BeginTransactionResponse(transaction=b'fixture')
        self.api.batch_get_documents.side_effect = self.read
        self.api.commit.side_effect = self.commit
        self.api.rollback.return_value = None
        self.saved = None
        self.budget = FirestoreBudget('guidinghand', limit=Decimal('5'),
                                     client=self.client, firestore_module=firestore)

    def read(self, request, **kwargs):
        path = request['documents'][0]
        if self.saved is None:
            return iter([types.BatchGetDocumentsResponse(missing=path)])
        return iter([types.BatchGetDocumentsResponse(found=types.Document(
            name=path, fields=_helpers.encode_dict(self.saved)))])

    def commit(self, request, **kwargs):
        self.saved = _helpers.decode_dict(request['writes'][0].update.fields, self.client)
        return types.CommitResponse(write_results=[types.WriteResult()])

    def test_real_sdk_transaction_get_returns_iterator(self):
        tx = self.client.transaction()
        tx._id = b'fixture'
        result = tx.get(self.budget.document)
        self.assertFalse(hasattr(result, 'exists'))
        self.assertFalse(next(result).exists)

    def test_speech_reserves_and_settles_through_real_sdk(self):
        calls = []
        def provider(*args):
            calls.append(True)
            return {'transcript': 'Mở Zalo'}
        result = SpeechInput('fixture-not-a-key', self.budget, provider).transcribe(frame())
        self.assertEqual('transcribed', result['status'], result.get('reason'))
        self.assertEqual([True], calls)
        self.assertEqual({}, self.saved['pending'])
        self.assertEqual(Decimal('0.00005'), Decimal(self.saved['spent_usd']))

    def test_existing_ledger_is_preserved_and_budget_blocks_before_provider(self):
        self.saved = {'spent_usd': '4.999', 'pending': {'existing': '.001'}, 'requests': 7}
        original = copy.deepcopy(self.saved)
        calls = []
        result = SpeechInput('fixture', self.budget, lambda *args: calls.append(True)).transcribe(frame())
        self.assertEqual('blocked', result['status'])
        self.assertEqual('budget_limit', result['reason'])
        self.assertEqual([], calls)
        self.assertEqual(original, self.saved)

    def test_firestore_failure_is_visible_without_private_exception_details(self):
        self.api.batch_get_documents.side_effect = RuntimeError('private-database-details')
        calls = []
        with self.assertLogs('guidinghand.voice', level='WARNING') as logs:
            result = SpeechInput('fixture', self.budget, lambda *args: calls.append(True)).transcribe(frame())
        self.assertEqual('budget_reservation_failed', result['reason'])
        self.assertEqual([], calls)
        self.assertNotIn('private-database-details', str(result) + str(logs.output))
        self.assertIn('stage=budget_reserve', logs.output[0])


if __name__ == '__main__':
    unittest.main()
