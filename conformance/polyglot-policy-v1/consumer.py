#!/usr/bin/env python3
"""Independent Python interpretation of the bounded unary policy contract.

Events describe the order accepted by a shell, not thread timing or a durable log.
The tiny state machine is a test oracle consumer, never a production executor.
"""
import json
from decimal import Decimal
from pathlib import Path
import sys
from types import MappingProxyType

PROFILE = 'tpf-polyglot-unary-policy/1'
REQUIRED = set('success b-budget-zero b-budget-one exhaust-preserves-failure business-rejection raw-host-exception wire-unclassified unknown-code malformed-failure missing-failure-field lying-eligibility spoofed-owner spoofed-step unclassified-unsanitised raw-null implicit-return empty missing-value nonnull-null nullable-tagged-null legacy-retry legacy-recovery cancel-prestart cancel-inflight-success cancel-inflight-failure cancel-handoff cancel-pending-retry cancel-after-b-start cancel-duplicate complete-before-cancel failure-before-cancel invalid-before-cancel context-missing context-absent context-blank context-type context-authority context-non-ascii unsupported-owner invalid-budget invalid-declaration budget-equivalent budget-rounded budget-negative budget-too-large budget-string'.split())
SANITISED = 'Unclassified business function failure'


def same_value(actual, expected):
    # Python bool is an int subclass; fixture oracles must retain JSON types.
    if type(actual) is not type(expected):
        return False
    if isinstance(actual, dict):
        return actual.keys() == expected.keys() and all(same_value(actual[k], expected[k]) for k in actual)
    if isinstance(actual, list):
        return len(actual) == len(expected) and all(same_value(a,b) for a,b in zip(actual,expected))
    return actual == expected


def config_number(value, allowed):
    return type(value) in (int, Decimal) and value in allowed


def check(check_id, actual, expected):
    if not same_value(actual, expected):
        print(f'CONFORMANCE_MISMATCH:{check_id}', file=sys.stderr)
        print(f'expected {expected!r}, got {actual!r}', file=sys.stderr)
        raise SystemExit(1)


def context_snapshot(value):
    """Explicit immutable allowlist; ambient context is deliberately not consulted."""
    if not isinstance(value, dict) or set(value) != {'executionId', 'traceId'}:
        raise ValueError('INVALID_CONTEXT')
    if any(not isinstance(v, str) or not v.strip(' \t\r\n') for v in value.values()):
        raise ValueError('INVALID_CONTEXT')
    return MappingProxyType(dict(value))


def failure_mapping(value, step):
    if (not isinstance(value, dict) or set(value) != {'code','message','owner','stepId','retryable'}
            or any(not isinstance(value[k], str) for k in ('code','message','owner','stepId'))
            or type(value['retryable']) is not bool):
        raise ValueError('MALFORMED_FAILURE')
    if value['code'] not in ('TRANSIENT','BUSINESS_REJECTION','UNCLASSIFIED'):
        raise ValueError('UNKNOWN_FAILURE_CODE')
    if (value['owner'] != 'LOCAL_SHELL' or value['stepId'] != step
            or value['retryable'] != (value['code'] == 'TRANSIENT')
            or (value['code'] == 'UNCLASSIFIED' and value['message'] != SANITISED)):
        raise ValueError('FAILURE_BINDING_MISMATCH')
    return dict(value)


def result_mapping(value, nullable):
    # None from an untagged/implicit host return cannot establish canonical null.
    if isinstance(value, dict) and value.get('kind') == 'EMPTY' and set(value) == {'kind'}:
        raise ValueError('UNSUPPORTED_EMPTY')
    if (not isinstance(value, dict) or set(value) != {'kind','value'} or value['kind'] != 'VALUE'
            or (value['value'] is None and not nullable)):
        raise ValueError('INVALID_VALUE')
    return dict(value)


def execute(case):
    counts = dict.fromkeys('ABC', 0)
    inputs = {step: [] for step in 'ABC'}
    accepted_input = 3
    terminal = None
    delivery = []
    observations = 0
    next_step = 'A'
    active = None

    def finish(outcome):
        nonlocal terminal, observations
        terminal = outcome
        observations += 1
        # Cancellation is observed by the shell, not delivered as a fake value.
        if outcome['kind'] != 'CANCELLED':
            delivery.append(outcome)

    try:
        snapshot = context_snapshot(case.get('context'))
        if any(type(case[k]) is not bool for k in ('nullable','legacyRetry','recovery')):
            raise ValueError('INVALID_POLICY')
        if case['owner'] != 'LOCAL_SHELL' or case['legacyRetry'] or case['recovery']:
            raise ValueError('POLICY_OWNERSHIP_CONFLICT')
        if set(case['retryLimits']) != set('ABC') or any(not config_number(v, (0,1,2)) for v in case['retryLimits'].values()):
            raise ValueError('INVALID_POLICY')
    except ValueError as error:
        finish({'kind':'REJECTED','diagnostic':str(error)})

    for event in case['events']:
        if terminal is not None:
            continue  # late callbacks cannot change the winner or dispatch another step
        kind = event['kind']
        if kind == 'cancel':
            finish({'kind':'CANCELLED'})
            continue
        step = event['step']
        if kind == 'dispatch':
            if active is not None or step != next_step:
                raise RuntimeError('Invalid test history dispatch')
            active = step
            counts[step] += 1
            inputs[step].append(accepted_input)
            # An immutable invocation view is bound afresh on each attempt.
            if dict(snapshot) != case['context']:
                raise RuntimeError('Context changed across attempts')
            continue
        if active != step:
            raise RuntimeError('Invalid test history completion')
        active = None
        try:
            if kind in ('failure','throw'):
                if kind == 'throw':
                    try:
                        raise RuntimeError('private exception detail must not escape')
                    except RuntimeError:
                        candidate = {'code':'UNCLASSIFIED','message':SANITISED,'owner':'LOCAL_SHELL','stepId':step,'retryable':False}
                else:
                    candidate = event['failure']
                failure = failure_mapping(candidate, step)
                if failure['code'] == 'TRANSIENT' and counts[step] <= case['retryLimits'][step]:
                    next_step = step  # only this step retries; A stays materialised
                else:
                    finish({'kind':'FAILED','failure':failure})
            elif kind == 'return':
                returned = event.get('outcome')
                if isinstance(returned, dict) and returned.get('kind') == 'IMPLICIT':
                    returned = None  # a Python function without return yields None
                outcome = result_mapping(returned, case['nullable'])
                if step == 'C':
                    finish({'kind':'COMPLETED','outcome':outcome})
                else:
                    next_step = chr(ord(step)+1)
                    accepted_input = outcome['value']
            else:
                raise RuntimeError('Unknown test history event')
        except ValueError as error:
            finish({'kind':'REJECTED','diagnostic':str(error)})
    if terminal is None:
        raise RuntimeError('Incomplete test history')
    return {'attemptInputs':inputs,'attempts':counts,'terminal':terminal,'observations':observations,'delivery':delivery}


def isolation(inputs):
    """Bind overlapping invocations, mutate callers/handler views, resume in reverse."""
    originals = [dict(item) for item in inputs]
    bound = [context_snapshot(item) for item in originals]
    for item in originals:
        item['executionId'] = 'caller-mutated'
    seen = []
    for context in reversed(bound):
        for _ in range(2):  # same identity on retry
            seen.append(dict(context))
            try:
                context['traceId'] = 'handler-mutated'
            except TypeError:
                pass
    return seen


def main():
    source = Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).with_name('vectors.json')
    corpus = json.loads(source.read_text(encoding='utf-8'), parse_float=Decimal)
    check('corpus/profile', corpus.get('profile'), PROFILE)
    check('corpus/schema', config_number(corpus.get('schemaVersion'), (1,)), True)
    ids = [case['id'] for case in corpus['cases']]
    check('corpus/cases', len(ids)==len(set(ids)) and set(ids)==REQUIRED, True)
    for case in corpus['cases']:
        check('cases/'+case['id'], execute(case), case['expected'])
    check('context/isolation', isolation(corpus['isolation']['inputs']), corpus['isolation']['expected'])
    print(json.dumps({'language':'python','cases':len(ids),'status':'PASS'}))


if __name__ == '__main__':
    main()
