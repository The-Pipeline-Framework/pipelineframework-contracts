"""Handcrafted qualification probe; no YAML loader or production runtime adapter."""

import asyncio
import base64
import decimal
import hashlib
import hmac
import json
import pathlib
import re
import sys


corpus_path = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path(__file__).with_name('vectors.json')
# Decimal prevents a fractional configuration number from rounding into an allowed value.
corpus = json.loads(corpus_path.read_text(encoding='utf-8'), parse_float=decimal.Decimal)
checks = 0


def equal(actual, expected, identifier):
    """Expose oracle mismatches separately from unexpected interpreter failures."""
    global checks
    if not same_json_value(actual, expected):
        print(f'CONFORMANCE_MISMATCH:{identifier}', file=sys.stderr)
        raise SystemExit(1)
    checks += 1


def same_json_value(actual, expected):
    # Python considers False == 0; canonical JSON presence must retain their types.
    if type(actual) is not type(expected):
        return False
    if isinstance(actual, dict):
        return actual.keys() == expected.keys() and all(
            same_json_value(actual[key], expected[key]) for key in actual)
    if isinstance(actual, list):
        return len(actual) == len(expected) and all(
            same_json_value(left, right) for left, right in zip(actual, expected))
    return actual == expected


def exact_config_number(value, expected):
    # bool subclasses int in Python, but it is not a JSON numeric configuration.
    return type(value) in (int, decimal.Decimal) and value == expected


def presence(vector):
    obj = json.loads(vector['json'])
    state = 'ABSENT' if 'value' not in obj else 'NULL' if obj['value'] is None else 'VALUE'
    if state == 'ABSENT' and not vector['optional']:
        diagnostic = 'MISSING_REQUIRED'
    elif state == 'NULL' and not vector['nullable']:
        diagnostic = 'NULL_FORBIDDEN'
    else:
        diagnostic = 'OK'
    return state, diagnostic, obj


def number(vector):
    text = vector['lexeme']
    integer = re.fullmatch(r'-?(0|[1-9][0-9]*)', text) and text != '-0'
    if vector['kind'] == 'json-safe-integer':
        if not integer:
            return 'UNSUPPORTED_NUMBER', ''
        # Compare canonical digits before conversion, avoiding Python's int digit limit.
        digits = text.removeprefix('-')
        if len(digits) > 16 or (len(digits) == 16 and digits > '9007199254740991'):
            return 'UNSAFE_NUMBER', ''
        return 'OK', text
    if vector['kind'] == 'candidate-integer-string':
        # Valid atoms are already canonical; no bounded integer conversion is needed.
        return ('OK', text) if integer else ('INVALID_NUMBER_LEXEME', '')
    if vector['kind'] == 'candidate-decimal-string':
        valid = re.fullmatch(r'-?(0|[1-9][0-9]*)\.[0-9]+', text)
        negative_zero = text.startswith('-') and not re.search(r'[1-9]', text)
        # Preserve coefficient, scale and trailing zeros without a binary float.
        return ('OK', text) if valid and not negative_zero else ('INVALID_NUMBER_LEXEME', '')
    return 'UNKNOWN_NUMBER_KIND', ''


def union_diagnostic(vector):
    if vector['tag'] not in ('count', 'label'):
        return 'UNKNOWN_VARIANT'
    if vector['tag'] == 'count':
        # Validate raw JSON spelling before native parsing erases 1.0, 1e0 or -0.
        diagnostic, _ = number(dict(kind='json-safe-integer', lexeme=vector['valueJson']))
        return 'OK' if diagnostic == 'OK' else 'INVALID_VARIANT_VALUE'
    return 'OK' if isinstance(json.loads(vector['valueJson']), str) else 'INVALID_VARIANT_VALUE'


def sha(body):
    return hashlib.sha256(body).hexdigest()


# Java Character.isWhitespace: Python strip() additionally removes U+0085/NBSP.
JAVA_WHITESPACE = re.compile('[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]*')


def normalize_path(path):
    if path is None or JAVA_WHITESPACE.fullmatch(path):
        return '/'
    return path if path.startswith('/') else '/' + path


def sign(signature):
    body = base64.b64decode(signature['bodyBase64'] + '===')
    body_sha256 = sha(body)
    preimage = '\n'.join([signature['method'], normalize_path(signature['path']),
                          signature['timestamp'], signature['nonce'], body_sha256])
    digest = hmac.new(signature['secret'].encode('utf-8'), preimage.encode('utf-8'), hashlib.sha256)
    return dict(bodySha256=body_sha256, preimage=preimage, hmac=digest.hexdigest())


def admit(vector):
    if not exact_config_number(vector.get('schemaVersion'), 1):
        return 'UNKNOWN_SCHEMA'
    if vector.get('profile') != 'tpf-polyglot-probe/0':
        return 'UNKNOWN_PROFILE'
    if not isinstance(vector.get('capabilities'), list) or len(vector['capabilities']) != 1:
        return 'UNSUPPORTED_CAPABILITY'
    if vector.get('retryOwner') != 'local-invocation-shell' or not exact_config_number(vector.get('retryLimit'), 2):
        return 'POLICY_CONFLICT'
    if any(capability != 'pure-unary' for capability in vector['capabilities']):
        return 'UNSUPPORTED_CAPABILITY'
    return 'OK' if vector.get('payloadType') == 'probe.Value/1' else 'UNKNOWN_PAYLOAD_TYPE'


def failure_diagnostic(failure):
    if not isinstance(failure, dict):
        return 'MALFORMED_FAILURE'
    fields = {'code', 'message', 'retryable', 'owner', 'stepId'}
    if (not fields <= failure.keys() or type(failure['retryable']) is not bool
            or not isinstance(failure['message'], str) or not isinstance(failure['code'], str)
            or not isinstance(failure['owner'], str) or failure['stepId'] != 'A'):
        return 'MALFORMED_FAILURE'
    if failure['code'] not in ('BUSINESS_REJECTION', 'TRANSIENT'):
        return 'UNKNOWN_FAILURE'
    if failure['owner'] != 'local-invocation-shell' or failure['retryable'] != (failure['code'] == 'TRANSIENT'):
        return 'FAILURE_POLICY_CONFLICT'
    return 'OK'


class Failure(Exception):
    def __init__(self, shape):
        super().__init__('portable probe failure')
        self.shape = shape


def portable_failure(code, message, retryable):
    return dict(code=code, message=message, retryable=retryable,
                owner='local-invocation-shell', stepId='A')


async def execute(vector, context):
    state = dict(aAttempts=0, bAttempts=0, callerAttempts=0, outcome='RETRY_EXHAUSTED', result=0)
    observations = []
    cancelled = asyncio.Event()

    def finish():
        return dict(state, observations=observations) if context.get('capture') else state

    owner = vector.get('retryOwner')
    coordinator = owner == 'coordinator-per-delivery'
    deliveries = vector.get('deliveries')
    if ((not coordinator and owner not in ('local-invocation-shell', 'callee-invocation-shell'))
            or type(deliveries) not in (int, decimal.Decimal) or not 1 <= deliveries <= 2147483647
            or (not coordinator and deliveries != 1) or deliveries != int(deliveries)):
        state['outcome'] = 'POLICY_CONFLICT'
        return finish()

    async def step_a(value):
        state['aAttempts'] += 1
        if vector.get('cancelDuringA'):
            cancelled.set()
        await asyncio.sleep(0)
        observations.append(dict(step='A', executionId=context['executionId'], traceId=context['traceId']))
        # Injection uses the ordinary asynchronous failure path, including a null envelope.
        if 'failureEnvelope' in vector:
            raise Failure(vector['failureEnvelope'])
        if vector['terminal']:
            raise Failure(portable_failure('BUSINESS_REJECTION', 'input rejected', False))
        delivery_attempt = state['aAttempts'] - (state['callerAttempts'] - 1) * (vector['retryLimit'] + 1)
        if delivery_attempt <= vector['failuresBeforeSuccess']:
            raise Failure(portable_failure('TRANSIENT', 'temporary failure', True))
        return value + 1

    async def step_b(value):
        observations.append(dict(step='B', executionId=context['executionId'], traceId=context['traceId']))
        state['bAttempts'] += 1
        await asyncio.sleep(0)
        return value * 2

    for _delivery in range(int(deliveries)):
        state['callerAttempts'] += 1
        for _attempt in range(vector['retryLimit'] + 1):
            try:
                value = await step_a(vector['input'])
                if vector['cancelBeforeB']:
                    cancelled.set()
                # A late result cannot start B after invocation cancellation.
                if cancelled.is_set():
                    state['outcome'] = 'CANCELLED'
                    return finish()
                state['result'] = await step_b(value)
                state['outcome'] = 'COMPLETED'
                return finish()
            except Failure as failure:
                if cancelled.is_set():
                    state['outcome'] = 'CANCELLED'
                    return finish()
                # Validate before reading policy: malformed/unknown failures never retry.
                diagnostic = failure_diagnostic(failure.shape)
                if diagnostic != 'OK':
                    state['outcome'] = diagnostic
                    return finish()
                if not failure.shape['retryable']:
                    if 'failureEnvelope' not in vector:
                        equal(failure.shape, corpus['failureShape'], f"executions/{vector['id']}/failure")
                    state['outcome'] = failure.shape['code']
                    return finish()
    return finish()


def encode_root(state):
    if state == 'ABSENT':
        raise ValueError('ABSENT_ROOT')
    return json.dumps(state)


def validate_context(context):
    return 'OK' if context.keys() <= {'executionId', 'traceId'} else 'RESTRICTED_CONTEXT'


async def main():
    equal(exact_config_number(corpus.get('schemaVersion'), 1), True, 'corpus/schemaVersion')
    equal(corpus.get('profile'), 'tpf-polyglot-probe/0', 'corpus/profile')
    root_diagnostic = 'OK'
    try:
        encode_root('ABSENT')
    except ValueError as failure:
        root_diagnostic = str(failure)
    equal(root_diagnostic, 'ABSENT_ROOT', 'presence/root')

    for vector in corpus['presence']:
        state, diagnostic, obj = presence(vector)
        equal(state, vector['state'], f"presence/{vector['id']}/state")
        equal(diagnostic, vector['diagnostic'], f"presence/{vector['id']}/diagnostic")
        # An independent parse of the input catches value loss in the observed object.
        equal(json.loads(json.dumps(obj)), json.loads(vector['json']), f"presence/{vector['id']}/roundtrip")
    for vector in corpus['numbers']:
        diagnostic, exact = number(vector)
        equal(diagnostic, vector['diagnostic'], f"numbers/{vector['id']}/diagnostic")
        if diagnostic == 'OK':
            equal(exact, vector['exact'], f"numbers/{vector['id']}/exact")
    for index, vector in enumerate(corpus['unions']):
        equal(union_diagnostic(vector), vector['diagnostic'], f"unions/{vector.get('id', index)}")
    for vector in corpus['bytes']:
        equal(sha(base64.b64decode(vector['base64'])), vector['sha256'], f"bytes/{vector['id']}")

    signature = sign(corpus['signature'])
    for field in ('bodySha256', 'preimage', 'hmac'):
        equal(signature[field], corpus['signature'][field], f'signature/{field}')
    for vector in corpus['signaturePaths']:
        actual = sign(dict(corpus['signature'], path=vector['path']))
        equal(actual['preimage'], vector['preimage'], f"signaturePaths/{vector['id']}/preimage")
        equal(actual['hmac'], vector['hmac'], f"signaturePaths/{vector['id']}/hmac")
    for vector in corpus['authMutations']:
        candidate_pins = dict(corpus['pins'], **{vector['field']: vector['value']})
        if vector['field'] in corpus['pins']:
            diagnostic = 'OK' if candidate_pins == corpus['pins'] else 'IDENTITY_MISMATCH'
        else:
            actual = sign(dict(corpus['signature'], **{vector['field']: vector['value']}))
            diagnostic = 'BAD_SIGNATURE' if actual['hmac'] != corpus['signature']['hmac'] else 'OK'
        equal(diagnostic, vector['diagnostic'], f"authentication/{vector.get('id', vector['field'])}")

    nonces = set()

    def admit_nonce(nonce):
        if nonce in nonces:
            return 'NONCE_REPLAY'
        nonces.add(nonce)
        return 'OK'

    equal(admit_nonce(corpus['signature']['nonce']), 'OK', 'nonce/first')
    equal(admit_nonce(corpus['signature']['nonce']), 'NONCE_REPLAY', 'nonce/replay')
    for vector in corpus['admission']:
        invoked = 0
        diagnostic = admit(vector)
        if diagnostic == 'OK':
            state = await execute(corpus['executions'][0], dict(executionId='admitted', traceId='trace'))
            invoked = state['aAttempts']
        equal(diagnostic, vector['diagnostic'], f"admission/{vector['id']}/diagnostic")
        equal(invoked, 1 if vector['diagnostic'] == 'OK' else 0, f"admission/{vector['id']}/invocations")
    for vector in corpus['failures']:
        equal(failure_diagnostic(vector['envelope']), vector['diagnostic'], f"failures/{vector['id']}")
    for key in corpus['forbiddenContextKeys']:
        equal(validate_context(dict(executionId='x', **{key: 'secret'})), 'RESTRICTED_CONTEXT', f'context/restricted/{key}')
    for vector in corpus['executions']:
        actual = await execute(vector, dict(executionId=vector['id'], traceId='trace'))
        equal(actual, vector['expected'], f"executions/{vector['id']}/outcome")

    async def run_context(context):
        equal(validate_context(dict(executionId=context['executionId'], traceId=context['traceId'])),
              'OK', f"context/{context['executionId']}/admission")
        state = await execute(dict(corpus['executions'][0], input=context['input']), dict(context, capture=True))
        return dict(executionId=context['executionId'], result=state['result'], observations=state['observations'])

    expected = [dict(executionId=context['expectedExecutionId'], result=context['expected'],
                     observations=[dict(step=step, executionId=context['expectedExecutionId'],
                                        traceId=context['expectedTraceId']) for step in ('A', 'B')])
                for context in corpus['contexts']]
    equal(await asyncio.gather(*(run_context(context) for context in corpus['contexts'])), expected, 'context/interleaved')
    print(json.dumps(dict(language='python', checks=checks, status='PASS', profile=corpus['profile'],
                         evidence='handcrafted-non-production')))


asyncio.run(main())
