// Handcrafted qualification probe only; no YAML loader or production runtime adapter.
import fs from 'node:fs';
import crypto from 'node:crypto';
import {isDeepStrictEqual} from 'node:util';

// Reviver source text preserves exact configuration numbers without a second JSON parser.
// Older engines cannot distinguish 1.0000000000000001 from 1 after parsing.
JSON.parse('0', (key, value, context) => {
  if (key === '' && context?.source !== '0') {
    throw new Error('This probe requires JSON.parse reviver source support (Node 22.23+).');
  }
  return value;
});
const numericLexemes = Symbol('probe configuration number lexemes');
const corpus = JSON.parse(fs.readFileSync(
  process.argv[2] ?? new URL('./vectors.json', import.meta.url), 'utf8'),
function rememberConfigurationNumber(key, value, context) {
  if (['schemaVersion', 'retryLimit', 'deliveries'].includes(key) && typeof value === 'number') {
    if (!Object.hasOwn(this, numericLexemes)) {
      Object.defineProperty(this, numericLexemes, {value: new Map(), enumerable: true});
    }
    this[numericLexemes].set(key, context.source);
  }
  return value;
});
let checks = 0;

// Stable identifiers let the runner distinguish an oracle mismatch from a crash.
function equal(actual, expected, id) {
  if (!isDeepStrictEqual(actual, expected)) {
    console.error(`CONFORMANCE_MISMATCH:${id}`);
    process.exit(1);
  }
  checks++;
}

function exactConfigNumber(object, key, expected) {
  if (typeof object[key] !== 'number') return false;
  const text = object[numericLexemes]?.get(key);
  if (text === undefined) return false;
  const match = /^(-?)([0-9]+)(?:\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?(?![\s\S])/.exec(text);
  if (!match || match[1] === '-') return false;
  const fraction = match[3] ?? '';
  const digits = (match[2] + fraction).replace(/^0+/, '');
  const significant = digits.replace(/0+$/, '');
  const trailingZeros = digits.length - significant.length;
  // Compare the exact positive count; decimal/exponent spelling is immaterial.
  const expectedDigits = String(expected);
  const expectedSignificant = expectedDigits.replace(/0+$/, '');
  return significant === expectedSignificant
    && BigInt(match[4] ?? '0') === BigInt(fraction.length - trailingZeros
      + expectedDigits.length - expectedSignificant.length);
}

function presence(vector) {
  const object = JSON.parse(vector.json);
  const state = !Object.hasOwn(object, 'value') ? 'ABSENT'
    : object.value === null ? 'NULL' : 'VALUE';
  const diagnostic = state === 'ABSENT' && !vector.optional ? 'MISSING_REQUIRED'
    : state === 'NULL' && !vector.nullable ? 'NULL_FORBIDDEN' : 'OK';
  return {state, diagnostic, object};
}

// Keep lexical evidence until validation: parsing first erases 1.0, 1e0 and -0.
const integerGrammar = /^-?(0|[1-9][0-9]*)(?![\s\S])/;
function number(vector) {
  const text = vector.lexeme;
  if (vector.kind === 'json-safe-integer') {
    if (!integerGrammar.test(text) || text === '-0') return ['UNSUPPORTED_NUMBER', ''];
    const value = BigInt(text);
    return value >= -9007199254740991n && value <= 9007199254740991n
      ? ['OK', text] : ['UNSAFE_NUMBER', ''];
  }
  if (vector.kind === 'candidate-integer-string') {
    // This grammar is already canonical; preserving it avoids bounded conversions.
    return integerGrammar.test(text) && text !== '-0'
      ? ['OK', text] : ['INVALID_NUMBER_LEXEME', ''];
  }
  if (vector.kind === 'candidate-decimal-string') {
    const valid = /^-?(0|[1-9][0-9]*)\.[0-9]+(?![\s\S])/.test(text);
    const negativeZero = text.startsWith('-') && !/[1-9]/.test(text);
    // Coefficient and scale stay in the string, including significant trailing zeros.
    return valid && !negativeZero ? ['OK', text] : ['INVALID_NUMBER_LEXEME', ''];
  }
  return ['UNKNOWN_NUMBER_KIND', ''];
}

function unionDiagnostic(vector) {
  if (!['count', 'label'].includes(vector.tag)) return 'UNKNOWN_VARIANT';
  if (vector.tag === 'count') {
    return number({kind: 'json-safe-integer', lexeme: vector.valueJson})[0] === 'OK'
      ? 'OK' : 'INVALID_VARIANT_VALUE';
  }
  return typeof JSON.parse(vector.valueJson) === 'string' ? 'OK' : 'INVALID_VARIANT_VALUE';
}

const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
// Java Character.isWhitespace, deliberately excluding U+0085, NBSP and FEFF.
const javaWhitespace = /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]*(?![\s\S])/;
function normalizePath(path) {
  if (path == null || javaWhitespace.test(path)) return '/';
  return path.startsWith('/') ? path : '/' + path;
}

function sign(signature) {
  const bodySha256 = sha(Buffer.from(signature.bodyBase64, 'base64'));
  const preimage = [signature.method, normalizePath(signature.path), signature.timestamp,
    signature.nonce, bodySha256].join('\n');
  const hmac = crypto.createHmac('sha256', Buffer.from(signature.secret, 'utf8'))
    .update(preimage, 'utf8').digest('hex');
  return {bodySha256, preimage, hmac};
}

function admit(vector) {
  if (!exactConfigNumber(vector, 'schemaVersion', 1)) return 'UNKNOWN_SCHEMA';
  if (vector.profile !== 'tpf-polyglot-probe/0') return 'UNKNOWN_PROFILE';
  if (!Array.isArray(vector.capabilities) || vector.capabilities.length !== 1) {
    return 'UNSUPPORTED_CAPABILITY';
  }
  if (vector.retryOwner !== 'local-invocation-shell' || !exactConfigNumber(vector, 'retryLimit', 2)) {
    return 'POLICY_CONFLICT';
  }
  if (vector.capabilities.some(capability => capability !== 'pure-unary')) return 'UNSUPPORTED_CAPABILITY';
  return vector.payloadType === 'probe.Value/1' ? 'OK' : 'UNKNOWN_PAYLOAD_TYPE';
}

function failureDiagnostic(failure) {
  if (failure === null || typeof failure !== 'object' || Array.isArray(failure)) {
    return 'MALFORMED_FAILURE';
  }
  const fields = ['code', 'message', 'retryable', 'owner', 'stepId'];
  if (!fields.every(key => Object.hasOwn(failure, key)) || typeof failure.retryable !== 'boolean'
      || typeof failure.message !== 'string' || typeof failure.code !== 'string'
      || typeof failure.owner !== 'string' || failure.stepId !== 'A') return 'MALFORMED_FAILURE';
  if (!['BUSINESS_REJECTION', 'TRANSIENT'].includes(failure.code)) return 'UNKNOWN_FAILURE';
  return failure.owner !== 'local-invocation-shell' || failure.retryable !== (failure.code === 'TRANSIENT')
    ? 'FAILURE_POLICY_CONFLICT' : 'OK';
}

function portableFailure(code, message, retryable) {
  return {code, message, retryable, owner: 'local-invocation-shell', stepId: 'A'};
}

async function execute(vector, context) {
  const state = {aAttempts: 0, bAttempts: 0, callerAttempts: 0, outcome: 'RETRY_EXHAUSTED', result: 0};
  const observations = [];
  const finish = () => context.capture ? {...state, observations} : state;
  const controller = new AbortController();
  const coordinator = vector.retryOwner === 'coordinator-per-delivery';
  const deliveries = vector.deliveries;
  if ((!coordinator && !['local-invocation-shell', 'callee-invocation-shell'].includes(vector.retryOwner))
      || !Number.isInteger(deliveries) || deliveries < 1 || deliveries > 2147483647
      || (!coordinator && deliveries !== 1) || !exactConfigNumber(vector, 'deliveries', deliveries)) {
    state.outcome = 'POLICY_CONFLICT';
    return finish();
  }

  async function stepA(value) {
    state.aAttempts++;
    if (vector.cancelDuringA) controller.abort();
    await Promise.resolve();
    observations.push({step: 'A', executionId: context.executionId, traceId: context.traceId});
    // Injected envelopes cross the same asynchronous failure boundary as real failures.
    if (Object.hasOwn(vector, 'failureEnvelope')) throw vector.failureEnvelope;
    if (vector.terminal) throw portableFailure('BUSINESS_REJECTION', 'input rejected', false);
    const deliveryAttempt = state.aAttempts - (state.callerAttempts - 1) * (vector.retryLimit + 1);
    if (deliveryAttempt <= vector.failuresBeforeSuccess) {
      throw portableFailure('TRANSIENT', 'temporary failure', true);
    }
    return value + 1;
  }

  async function stepB(value) {
    observations.push({step: 'B', executionId: context.executionId, traceId: context.traceId});
    state.bAttempts++;
    await Promise.resolve();
    return value * 2;
  }

  for (let delivery = 0; delivery < deliveries; delivery++) {
    state.callerAttempts++;
    for (let attempt = 0; attempt <= vector.retryLimit; attempt++) {
      try {
        const value = await stepA(vector.input);
        if (vector.cancelBeforeB) controller.abort();
        // A late result cannot start B after the invocation has been cancelled.
        if (controller.signal.aborted) {
          state.outcome = 'CANCELLED';
          return finish();
        }
        state.result = await stepB(value);
        state.outcome = 'COMPLETED';
        return finish();
      } catch (failure) {
        if (controller.signal.aborted) {
          state.outcome = 'CANCELLED';
          return finish();
        }
        // Validate before reading retry policy; malformed/unknown failures never retry.
        const diagnostic = failureDiagnostic(failure);
        if (diagnostic !== 'OK') {
          state.outcome = diagnostic;
          return finish();
        }
        if (!failure.retryable) {
          if (!Object.hasOwn(vector, 'failureEnvelope')) {
            equal(failure, corpus.failureShape, `executions/${vector.id}/failure`);
          }
          state.outcome = failure.code;
          return finish();
        }
      }
    }
  }
  return finish();
}

function encodeRoot(state) {
  if (state === 'ABSENT') throw new Error('ABSENT_ROOT');
  return JSON.stringify(state);
}

function validateContext(context) {
  return Object.keys(context).every(key => ['executionId', 'traceId'].includes(key))
    ? 'OK' : 'RESTRICTED_CONTEXT';
}

async function main() {
  equal(exactConfigNumber(corpus, 'schemaVersion', 1), true, 'corpus/schemaVersion');
  equal(corpus.profile, 'tpf-polyglot-probe/0', 'corpus/profile');
  let rootDiagnostic = 'OK';
  try { encodeRoot('ABSENT'); } catch (failure) { rootDiagnostic = failure.message; }
  equal(rootDiagnostic, 'ABSENT_ROOT', 'presence/root');

  for (const vector of corpus.presence) {
    const result = presence(vector);
    equal(result.state, vector.state, `presence/${vector.id}/state`);
    equal(result.diagnostic, vector.diagnostic, `presence/${vector.id}/diagnostic`);
    // Reparse the original oracle independently, so value loss cannot validate itself.
    equal(JSON.parse(JSON.stringify(result.object)), JSON.parse(vector.json), `presence/${vector.id}/roundtrip`);
  }
  for (const vector of corpus.numbers) {
    const [diagnostic, exact] = number(vector);
    equal(diagnostic, vector.diagnostic, `numbers/${vector.id}/diagnostic`);
    if (diagnostic === 'OK') equal(exact, vector.exact, `numbers/${vector.id}/exact`);
  }
  for (const [index, vector] of corpus.unions.entries()) {
    equal(unionDiagnostic(vector), vector.diagnostic, `unions/${vector.id ?? index}`);
  }
  for (const vector of corpus.bytes) {
    equal(sha(Buffer.from(vector.base64, 'base64')), vector.sha256, `bytes/${vector.id}`);
  }
  const signature = sign(corpus.signature);
  equal(signature.bodySha256, corpus.signature.bodySha256, 'signature/bodySha256');
  equal(signature.preimage, corpus.signature.preimage, 'signature/preimage');
  equal(signature.hmac, corpus.signature.hmac, 'signature/hmac');
  for (const vector of corpus.signaturePaths) {
    const actual = sign({...corpus.signature, path: vector.path});
    equal(actual.preimage, vector.preimage, `signaturePaths/${vector.id}/preimage`);
    equal(actual.hmac, vector.hmac, `signaturePaths/${vector.id}/hmac`);
  }
  for (const vector of corpus.authMutations) {
    const candidatePins = {...corpus.pins, [vector.field]: vector.value};
    const diagnostic = Object.hasOwn(corpus.pins, vector.field)
      ? Object.keys(corpus.pins).every(key => candidatePins[key] === corpus.pins[key]) ? 'OK' : 'IDENTITY_MISMATCH'
      : sign({...corpus.signature, [vector.field]: vector.value}).hmac !== corpus.signature.hmac ? 'BAD_SIGNATURE' : 'OK';
    equal(diagnostic, vector.diagnostic, `authentication/${vector.id ?? vector.field}`);
  }
  const nonces = new Set();
  function admitNonce(nonce) {
    if (nonces.has(nonce)) return 'NONCE_REPLAY';
    nonces.add(nonce);
    return 'OK';
  }
  equal(admitNonce(corpus.signature.nonce), 'OK', 'nonce/first');
  equal(admitNonce(corpus.signature.nonce), 'NONCE_REPLAY', 'nonce/replay');
  for (const vector of corpus.admission) {
    const diagnostic = admit(vector);
    let invoked = 0;
    if (diagnostic === 'OK') {
      invoked = (await execute(corpus.executions[0], {executionId: 'admitted', traceId: 'trace'})).aAttempts;
    }
    equal(diagnostic, vector.diagnostic, `admission/${vector.id}/diagnostic`);
    equal(invoked, vector.diagnostic === 'OK' ? 1 : 0, `admission/${vector.id}/invocations`);
  }
  for (const vector of corpus.failures) {
    equal(failureDiagnostic(vector.envelope), vector.diagnostic, `failures/${vector.id}`);
  }
  for (const key of corpus.forbiddenContextKeys) {
    equal(validateContext({executionId: 'x', [key]: 'secret'}), 'RESTRICTED_CONTEXT', `context/restricted/${key}`);
  }
  for (const vector of corpus.executions) {
    equal(await execute(vector, {executionId: vector.id, traceId: 'trace'}), vector.expected,
      `executions/${vector.id}/outcome`);
  }
  const interleaved = await Promise.all(corpus.contexts.map(async context => {
    equal(validateContext({executionId: context.executionId, traceId: context.traceId}), 'OK',
      `context/${context.executionId}/admission`);
    const state = await execute({...corpus.executions[0], input: context.input}, {...context, capture: true});
    return {executionId: context.executionId, result: state.result, observations: state.observations};
  }));
  const expected = corpus.contexts.map(context => ({
    executionId: context.expectedExecutionId, result: context.expected,
    observations: ['A', 'B'].map(step => ({step, executionId: context.expectedExecutionId, traceId: context.expectedTraceId}))
  }));
  equal(interleaved, expected, 'context/interleaved');
  console.log(JSON.stringify({language: 'node', checks, status: 'PASS', profile: corpus.profile,
    evidence: 'handcrafted-non-production'}));
}

await main();
