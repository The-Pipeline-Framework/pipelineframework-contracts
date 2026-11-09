#!/usr/bin/env python3
"""Run three independent policy consumers and require named falsification failures."""
import argparse
import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import sys
sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[2]
PROBE = Path(__file__).resolve().parent
# Reuse build plumbing only; no shared execution/mapping implementation.
spec = importlib.util.spec_from_file_location('v0_runner', ROOT/'conformance/polyglot-v0/run.py')
v0 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v0)


def mutations(original):
    """Corrupt one fixed answer per changed axis, without changing its input."""
    for case_id, field, value in [
        ('b-budget-one','attempts',{'A':2,'B':2,'C':1}),
        ('b-budget-one','attemptInputs',{'A':[3],'B':[4,99],'C':[8]}),
        ('raw-host-exception','terminal',{'kind':'FAILED','failure':{'code':'TRANSIENT'}}),
        ('unknown-code','terminal',{'kind':'FAILED','failure':{'code':'UNCLASSIFIED'}}),
        ('nullable-tagged-null','terminal',{'kind':'COMPLETED','outcome':{'kind':'EMPTY'}}),
        ('cancel-handoff','delivery',[{'kind':'CANCELLED'}]),
        ('context-absent','attempts',{'A':1,'B':0,'C':0}),
        ('success','attempts',{'A':True,'B':1,'C':1}),
    ]:
        changed = copy.deepcopy(original)
        next(row for row in changed['cases'] if row['id']==case_id)['expected'][field] = value
        yield case_id+'-'+field, 'cases/'+case_id, changed
    for name, value in [('schema-narrowing',4294967297),('schema-rounding',v0.NumberLexeme('1.0000000000000001')),('schema-boolean',True)]:
        changed = copy.deepcopy(original)
        changed['schemaVersion'] = value
        yield name,'corpus/schema',changed
    changed = copy.deepcopy(original)
    changed['isolation']['expected'][0]['executionId'] = 'leaked'
    yield 'context-isolation', 'context/isolation', changed
    for name in ('missing','duplicate'):
        changed = copy.deepcopy(original)
        if name == 'missing': changed['cases'].pop()
        else: changed['cases'].append(copy.deepcopy(changed['cases'][0]))
        yield name, 'corpus/cases', changed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--vectors', type=Path, default=PROBE/'vectors.json')
    args = parser.parse_args()
    vectors = args.vectors.resolve()
    if not args.skip_build:
        v0.run(['./mvnw','-B','-pl','runtime-serialization,runtime-protocol','-am','package','-Dgpg.skip',f'-Dmaven.repo.local={ROOT}/.m2/repository'])
    cp = v0.java_classpath()
    out = ROOT/'target/polyglot-policy-v1'
    out.mkdir(parents=True, exist_ok=True)
    v0.run(['javac','--release','21','-cp',cp,'-d',out,PROBE/'java/PolicyConsumer.java'])
    commands = {
        'java':['java','-cp',os.pathsep.join([str(out),cp]),'PolicyConsumer'],
        'node':['node',str(PROBE/'consumer.mjs')],
        'python':['python3',str(PROBE/'consumer.py')],
    }
    for command in commands.values(): v0.run([*command,vectors])
    count = 0
    with tempfile.TemporaryDirectory(prefix='tpf-policy-mutations-') as directory:
        for name, check_id, changed in mutations(json.loads(vectors.read_text(encoding='utf-8'),parse_float=v0.NumberLexeme)):
            path = Path(directory)/(name+'.json')
            path.write_text(v0.fixture_json(changed),encoding='utf-8')
            for language, command in commands.items():
                result = subprocess.run([*command,str(path)],cwd=ROOT,capture_output=True,text=True,encoding='utf-8',check=False,timeout=60)
                # Reuse the v0 runner's exact exit-1 + single named-marker guard.
                v0.require_expected_rejection(result,check_id,language+'/'+name)
                count += 1
    print(json.dumps({'status':'PASS','mutationChecks':count}))


if __name__ == '__main__':
    main()
