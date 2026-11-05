// Candidate conformance/probe only. No YAML parser, provider, coordinator or runtime adapter.
import fs from 'node:fs';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const corpus = JSON.parse(fs.readFileSync(process.argv[2] ?? new URL('./vectors.json', import.meta.url), 'utf8'));
let checks = 0;
assert.equal(corpus.schemaVersion,1); assert.equal(corpus.profile,'tpf-polyglot-probe/0');
function equal(actual, expected) { assert.deepEqual(actual, expected); checks++; }
function presence(v) {
  const object = JSON.parse(v.json);
  const state = !Object.hasOwn(object, 'value') ? 'ABSENT' : object.value === null ? 'NULL' : 'VALUE';
  const diagnostic = state === 'ABSENT' && !v.optional ? 'MISSING_REQUIRED' : state === 'NULL' && !v.nullable ? 'NULL_FORBIDDEN' : 'OK';
  return {state, diagnostic, object};
}
function number(v) {
  if (v.kind === 'json-safe-integer') {
    if (!/^-?(0|[1-9]\d*)(?![\s\S])/.test(v.lexeme) || v.lexeme === '-0') return ['UNSUPPORTED_NUMBER',''];
    const n = JSON.parse(v.lexeme);
    return Number.isSafeInteger(n) ? ['OK', String(n)] : ['UNSAFE_NUMBER', ''];
  }
  if (v.kind === 'candidate-integer-string') return /^-?(0|[1-9]\d*)(?![\s\S])/.test(v.lexeme) && v.lexeme!=='-0' ? ['OK', BigInt(v.lexeme).toString()] : ['INVALID_NUMBER_LEXEME',''];
  if (v.kind === 'candidate-decimal-string') {
    const match = /^(-?)(0|[1-9]\d*)\.(\d+)(?![\s\S])/.exec(v.lexeme);
    if (!match || (match[1]==='-' && BigInt(match[2]+match[3])===0n)) return ['INVALID_NUMBER_LEXEME',''];
    // A decimal is coefficient + scale; never binary float.
    const coefficient = BigInt(match[2] + match[3]);
    const digits = coefficient.toString().padStart(match[3].length + 1, '0');
    return ['OK', match[1] + digits.slice(0, -match[3].length) + '.' + digits.slice(-match[3].length)];
  }
  return ['UNKNOWN_NUMBER_KIND', ''];
}
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
function sign(s, body = Buffer.from(s.bodyBase64, 'base64')) {
  const path = s.path.trim() === '' ? '/' : s.path.startsWith('/') ? s.path : '/' + s.path;
  const preimage = [s.method, path, s.timestamp, s.nonce, sha(body)].join('\n');
  return {preimage, hmac: crypto.createHmac('sha256', s.secret).update(preimage).digest('hex')};
}
function admit(v) {
  if (v.schemaVersion !== 1) return 'UNKNOWN_SCHEMA';
  if (v.profile !== 'tpf-polyglot-probe/0') return 'UNKNOWN_PROFILE';
  if (!Array.isArray(v.capabilities) || v.capabilities.length !== 1) return 'UNSUPPORTED_CAPABILITY';
  if (v.retryOwner !== 'local-invocation-shell' || v.retryLimit !== 2) return 'POLICY_CONFLICT';
  if (v.capabilities.some(c => c !== 'pure-unary')) return 'UNSUPPORTED_CAPABILITY';
  return v.payloadType === 'probe.Value/1' ? 'OK' : 'UNKNOWN_PAYLOAD_TYPE';
}
async function execute(v, context) {
  const state = {aAttempts: 0, bAttempts: 0, callerAttempts: 0, outcome: 'RETRY_EXHAUSTED', result: 0};
  const observations = [];
  const finish = () => context.capture ? {...state,observations} : state;
  const controller = new AbortController();
  const a = async value => {
    state.aAttempts++;
    if (v.cancelDuringA) controller.abort();
    await Promise.resolve();
    observations.push({step:'A',executionId:context.executionId,traceId:context.traceId});
    if (v.terminal) throw {code: 'BUSINESS_REJECTION', message: 'input rejected', retryable: false, owner: 'local-invocation-shell',stepId:'A'};
    if (state.aAttempts - (state.callerAttempts - 1) * (v.retryLimit + 1) <= v.failuresBeforeSuccess) throw {code: 'TRANSIENT', retryable: true};
    return value + 1;
  };
  const b = async value => { observations.push({step:'B',executionId:context.executionId,traceId:context.traceId}); state.bAttempts++; await Promise.resolve(); return value * 2; };
  for (let delivery = 0; delivery < v.deliveries; delivery++) {
    state.callerAttempts++;
    for (let attempt = 0; attempt <= v.retryLimit; attempt++) {
      try {
        const value = await a(v.input);
        if (v.cancelBeforeB) controller.abort();
        if (controller.signal.aborted) { state.outcome = 'CANCELLED'; return finish(); }
        state.result = await b(value); state.outcome = 'COMPLETED'; return finish();
      } catch (failure) {
        if (!failure.retryable) { equal(failure, corpus.failureShape); state.outcome = failure.code; return finish(); }
      }
    }
  }
  return finish();
}
function encodeRoot(state) { if (state === 'ABSENT') throw new Error('ABSENT_ROOT'); return JSON.stringify(state); }
assert.throws(() => encodeRoot('ABSENT'), /ABSENT_ROOT/); checks++;
for (const v of corpus.presence) {
  const p = presence(v); equal(p.state, v.state); equal(p.diagnostic, v.diagnostic);
  equal(JSON.parse(JSON.stringify(p.object)), p.object);
}
for (const v of corpus.numbers) { const [diagnostic, exact] = number(v); equal(diagnostic, v.diagnostic); if (diagnostic === 'OK') equal(exact, v.exact); }
for (const v of corpus.unions) equal(!['count','label'].includes(v.tag) ? 'UNKNOWN_VARIANT' : (v.tag==='count' ? Number.isSafeInteger(v.value) : typeof v.value==='string') ? 'OK' : 'INVALID_VARIANT_VALUE', v.diagnostic);
for (const v of corpus.bytes) equal(sha(Buffer.from(v.base64, 'base64')), v.sha256);
equal(sign(corpus.signature), {preimage: corpus.signature.preimage, hmac: corpus.signature.hmac});
for (const v of corpus.authMutations) {
  const candidatePins = {...corpus.pins, [v.field]: v.value};
  const diagnostic = Object.hasOwn(corpus.pins, v.field) ? (Object.keys(corpus.pins).every(key => candidatePins[key] === corpus.pins[key]) ? 'OK' : 'IDENTITY_MISMATCH') : sign({...corpus.signature, [v.field]: v.value}).hmac !== corpus.signature.hmac ? 'BAD_SIGNATURE' : 'OK';
  equal(diagnostic, v.diagnostic);
}
const nonceStore = new Set();
const admitNonce = nonce => { if (nonceStore.has(nonce)) return 'NONCE_REPLAY'; nonceStore.add(nonce); return 'OK'; };
equal(admitNonce(corpus.signature.nonce), 'OK'); equal(admitNonce(corpus.signature.nonce), 'NONCE_REPLAY');
for (const v of corpus.admission) {
  let invoked=0; const diagnostic=admit(v); if(diagnostic==='OK') invoked=(await execute(corpus.executions[0],{executionId:'admitted',traceId:'trace'})).aAttempts;
  equal(diagnostic,v.diagnostic); equal(invoked,v.diagnostic==='OK'?1:0);
}
function failureDiagnostic(f) {
 if (!['code','message','retryable','owner','stepId'].every(k=>Object.hasOwn(f,k)) || typeof f.retryable!=='boolean' || typeof f.message!=='string' || f.stepId!=='A') return 'MALFORMED_FAILURE';
 if (!['BUSINESS_REJECTION','TRANSIENT'].includes(f.code)) return 'UNKNOWN_FAILURE';
 return f.owner!=='local-invocation-shell' || f.retryable!==(f.code==='TRANSIENT') ? 'FAILURE_POLICY_CONFLICT' : 'OK';
}
for(const v of corpus.failures) equal(failureDiagnostic(v.envelope),v.diagnostic);
const validateContext = c => Object.keys(c).every(k => ['executionId','traceId'].includes(k)) ? 'OK' : 'RESTRICTED_CONTEXT';
for (const key of corpus.forbiddenContextKeys) equal(validateContext({executionId: 'x', [key]: 'secret'}), 'RESTRICTED_CONTEXT');
for (const v of corpus.executions) equal(await execute(v, {executionId: v.id, expectedExecutionId: v.id}), v.expected);
const interleaved = await Promise.all(corpus.contexts.map(async c => {
  equal(validateContext({executionId:c.executionId, traceId:c.traceId}), 'OK');
  const state = await execute({...corpus.executions[0], input:c.input}, {executionId:c.executionId,traceId:c.traceId,capture:true});
  return {executionId:c.executionId, result:state.result,observations:state.observations};
}));
equal(interleaved, corpus.contexts.map(c => ({executionId:c.expectedExecutionId,result:c.expected,observations:['A','B'].map(step=>({step,executionId:c.expectedExecutionId,traceId:c.expectedTraceId}))})));
// Mutation witnesses: each changed oracle must fail equality.
for (const [actual, mutated] of [[presence(corpus.presence.find(v=>v.state==='NULL')).state,'ABSENT'], [number(corpus.numbers.find(v=>v.id==='large-candidate'))[1],'9007199254740992'], [(await execute(corpus.executions[1],{executionId:'m',expectedExecutionId:'m'})).aAttempts,2]]) {
  assert.notDeepEqual(actual, mutated); checks++;
}
console.log(JSON.stringify({language:'node',checks,status:'PASS',profile:corpus.profile,evidence:'handcrafted-non-production'}));
