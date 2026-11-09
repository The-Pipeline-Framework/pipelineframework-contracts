"""The policy runner must challenge every changed axis and preserve the input."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('policy_runner', HERE/'run.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class RunnerTest(unittest.TestCase):
    def test_mutations_preserve_original_and_have_distinct_names(self):
        original = json.loads((HERE/'vectors.json').read_text(), parse_float=runner.v0.NumberLexeme)
        saved = copy.deepcopy(original)
        mutations = list(runner.mutations(original))
        self.assertEqual(original, saved)
        self.assertEqual(len(mutations), 14)
        self.assertEqual(len({name for name, _, _ in mutations}), 14)
        for name, check, changed in mutations:
            self.assertNotEqual(runner.v0.fixture_json(changed), runner.v0.fixture_json(original), name)
            self.assertTrue(check.startswith(('cases/','corpus/','context/')))
            if name not in ('missing','duplicate'):
                # Strip only oracle fields. Context, policy, events and isolation
                # inputs must all survive, including exact numeric spellings.
                inputs = []
                for document in (original, changed):
                    input_only = copy.deepcopy(document)
                    for row in input_only['cases']:
                        row.pop('expected')
                    input_only['isolation'].pop('expected')
                    if name.startswith('schema-'):
                        input_only.pop('schemaVersion')
                    inputs.append(runner.v0.fixture_json(input_only))
                self.assertEqual(inputs[0], inputs[1], name)

    def test_missing_and_duplicate_really_change_membership(self):
        original = json.loads((HERE/'vectors.json').read_text(), parse_float=runner.v0.NumberLexeme)
        mutations = {name: changed for name, _, changed in runner.mutations(original)}
        self.assertEqual(len(mutations['missing']['cases']),len(original['cases'])-1)
        ids = [row['id'] for row in mutations['duplicate']['cases']]
        self.assertEqual(len(ids),len(set(ids))+1)


if __name__ == '__main__':
    unittest.main()
