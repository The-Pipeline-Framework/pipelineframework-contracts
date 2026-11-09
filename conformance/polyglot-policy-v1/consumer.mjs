#!/usr/bin/env node
/** Independent Node interpretation; accepted events model ordering, not real races. */
import {readFileSync} from 'node:fs';
import {isDeepStrictEqual} from 'node:util';
const REQUIRED = new Set('success b-budget-zero b-budget-one exhaust-preserves-failure business-rejection raw-host-exception wire-unclassified unknown-code malformed-failure missing-failure-field lying-eligibility spoofed-owner spoofed-step unclassified-unsanitised raw-null implicit-return empty missing-value nonnull-null nullable-tagged-null legacy-retry legacy-recovery cancel-prestart cancel-inflight-success cancel-inflight-failure cancel-handoff cancel-pending-retry cancel-after-b-start cancel-duplicate complete-before-cancel failure-before-cancel invalid-before-cancel context-missing context-absent context-blank context-type context-authority context-non-ascii unsupported-owner invalid-budget invalid-declaration budget-equivalent budget-rounded budget-negative budget-too-large budget-string missing-nullable absent-budgets null-budgets nonobject-budgets missing-owner missing-failure-envelope missing-legacy-retry missing-recovery string-budgets'.split(' '));
// Retain exact configuration tokens using Node's native JSON parser, as v0 does.
JSON.parse('0', (key, value, context) => {
  if (key === '' && context?.source !== '0') throw new Error('Node 22.23+ source tokens required');
  return value;
});
const numericLexemes = Symbol('configuration tokens');
function exactNumber(object, key, expected) {
  if (typeof object[key] !== 'number') return false;
  const match = /^(-?)([0-9]+)(?:\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?$/.exec(object[numericLexemes]?.get(key) ?? '');
  if (!match) return false;
  const fraction = match[3] ?? '';
  const digits = (match[2]+fraction).replace(/^0+/, '');
  if (expected === 0) return digits === ''; // configuration -0 is numerically zero
  if (match[1] === '-') return false;
  const significant = digits.replace(/0+$/, '');
  return significant === String(expected) && BigInt(match[4] ?? '0') === BigInt(fraction.length-(digits.length-significant.length));
}
const SANITISED = 'Unclassified business function failure';
const object = v => v !== null && typeof v === 'object' && !Array.isArray(v);
const keys = (v, expected) => object(v) && isDeepStrictEqual(Object.keys(v).sort(), [...expected].sort());
function check(id, actual, expected) {
  if (!isDeepStrictEqual(actual, expected)) {
    console.error(`CONFORMANCE_MISMATCH:${id}`);
    console.error(`Expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
    process.exit(1);
  }
}
class BoundaryRejection extends Error {}
function reject(code) { throw new BoundaryRejection(code); }
function bindContext(value) {
  // Deliberate allowlist and ASCII blank grammar, independent of JS trim().
  if (!keys(value, ['executionId','traceId']) || Object.values(value).some(v => typeof v !== 'string' || /^[ \t\r\n]*$/.test(v))) reject('INVALID_CONTEXT');
  return Object.freeze({...value});
}
function mapFailure(value, step) {
  if (!keys(value, ['code','message','owner','stepId','retryable']) || ['code','message','owner','stepId'].some(k => typeof value[k] !== 'string') || typeof value.retryable !== 'boolean') reject('MALFORMED_FAILURE');
  if (!['TRANSIENT','BUSINESS_REJECTION','UNCLASSIFIED'].includes(value.code)) reject('UNKNOWN_FAILURE_CODE');
  if (value.owner !== 'LOCAL_SHELL' || value.stepId !== step || value.retryable !== (value.code === 'TRANSIENT') || (value.code === 'UNCLASSIFIED' && value.message !== SANITISED)) reject('FAILURE_BINDING_MISMATCH');
  return {...value};
}
function mapResult(value, nullable) {
  if (keys(value, ['kind']) && value.kind === 'EMPTY') reject('UNSUPPORTED_EMPTY');
  if (!keys(value, ['kind','value']) || value.kind !== 'VALUE' || (value.value === null && !nullable)) reject('INVALID_VALUE');
  return {...value};
}
function execute(row) {
  const attempts = {A:0,B:0,C:0};
  const attemptInputs = {A:[],B:[],C:[]};
  let terminal, observations = 0, next = 'A', active, input = 3, context;
  const delivery = [];
  const finish = outcome => {
    terminal = outcome;
    observations++;
    if (outcome.kind !== 'CANCELLED') delivery.push(outcome);
  };
  try {
    context = bindContext(row.context); // ambient values are not authority
    if (['nullable','legacyRetry','recovery'].some(k => typeof row[k] !== 'boolean')) reject('INVALID_POLICY');
    if (row.owner !== 'LOCAL_SHELL' || row.legacyRetry || row.recovery) reject('POLICY_OWNERSHIP_CONFLICT');
    if (!keys(row.retryLimits, ['A','B','C']) || ['A','B','C'].some(k => ![0,1,2].some(v => exactNumber(row.retryLimits,k,v)))) reject('INVALID_POLICY');
  } catch (e) {
    if (!(e instanceof BoundaryRejection)) throw e;
    finish({kind:'REJECTED', diagnostic:e.message});
  }
  for (const event of row.events) {
    // A committed winner suppresses even malformed late data and dispatch requests.
    if (terminal) continue;
    if (event.kind === 'cancel') { finish({kind:'CANCELLED'}); continue; }
    const step = event.step;
    if (event.kind === 'dispatch') {
      if (active !== undefined || step !== next) throw new Error('Invalid test dispatch');
      active = step;
      attempts[step]++;
      attemptInputs[step].push(input);
      if (!isDeepStrictEqual(context, row.context)) throw new Error('Context changed across attempts');
      continue;
    }
    if (active !== step) throw new Error('Invalid test completion');
    active = undefined;
    try {
      if (event.kind === 'failure' || event.kind === 'throw') {
        let candidate = event.failure;
        if (event.kind === 'throw') {
          try { throw new Error('private exception detail must not escape'); }
          catch { candidate = {code:'UNCLASSIFIED', message:SANITISED, owner:'LOCAL_SHELL', stepId:step, retryable:false}; }
        }
        const failure = mapFailure(candidate, step);
        if (failure.code === 'TRANSIENT' && attempts[step] <= row.retryLimits[step]) next = step;
        else finish({kind:'FAILED', failure});
      } else if (event.kind === 'return') {
        // Model an implicit host return with actual undefined, distinct from VALUE(null).
        const outcome = mapResult(event.outcome?.kind === 'IMPLICIT' ? undefined : event.outcome, row.nullable);
        if (step === 'C') finish({kind:'COMPLETED', outcome});
        else { next = String.fromCharCode(step.charCodeAt(0)+1); input = outcome.value; }
      } else throw new Error('Unknown test event');
    } catch (e) {
      if (!(e instanceof BoundaryRejection)) throw e;
      finish({kind:'REJECTED', diagnostic:e.message});
    }
  }
  if (!terminal) throw new Error('Incomplete test history');
  return {attempts, attemptInputs, terminal, observations, delivery};
}
function isolation(inputs) {
  const callers = inputs.map(v => ({...v}));
  const contexts = callers.map(bindContext);
  callers.forEach(v => { v.executionId = 'caller-mutated'; });
  const seen = [];
  for (const context of contexts.reverse()) {
    for (let attempt=0; attempt<2; attempt++) {
      seen.push({...context});
      try { context.traceId = 'handler-mutated'; } catch (e) { if (!(e instanceof TypeError)) throw e; }
    }
  }
  return seen;
}
const source = process.argv[2] ?? new URL('./vectors.json', import.meta.url);
const corpus = JSON.parse(readFileSync(source, 'utf8'), function (key,value,context) {
  if (typeof value === 'number') {
    if (!Object.hasOwn(this,numericLexemes)) Object.defineProperty(this,numericLexemes,{value:new Map()});
    this[numericLexemes].set(key,context.source);
  }
  return value;
});
check('corpus/profile', corpus.profile, 'tpf-polyglot-unary-policy/1');
check('corpus/schema', exactNumber(corpus,'schemaVersion',1), true);
const ids = corpus.cases.map(row => row.id);
check('corpus/cases', ids.length === new Set(ids).size && isDeepStrictEqual(new Set(ids), REQUIRED), true);
for (const row of corpus.cases) check(`cases/${row.id}`, execute(row), row.expected);
check('context/isolation', isolation(corpus.isolation.inputs), corpus.isolation.expected);
console.log(JSON.stringify({language:'node', cases:ids.length, status:'PASS'}));
