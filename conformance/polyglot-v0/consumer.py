"""Independent candidate conformance consumer; no YAML/runtime/provider implementation."""
import asyncio, base64, copy, decimal, hashlib, hmac, json, pathlib, re, sys
corpus=json.loads(pathlib.Path(sys.argv[1] if len(sys.argv)>1 else pathlib.Path(__file__).with_name('vectors.json')).read_text())
assert corpus['schemaVersion']==1 and corpus['profile']=='tpf-polyglot-probe/0'
checks=0
def equal(actual,expected):
 global checks
 assert actual==expected,(actual,expected)
 checks+=1
def presence(v):
 obj=json.loads(v['json']); state='ABSENT' if 'value' not in obj else 'NULL' if obj['value'] is None else 'VALUE'
 diagnostic='MISSING_REQUIRED' if state=='ABSENT' and not v['optional'] else 'NULL_FORBIDDEN' if state=='NULL' and not v['nullable'] else 'OK'
 return state,diagnostic,obj
def number(v):
 kind=v['kind']; text=v['lexeme']
 if kind=='json-safe-integer':
  if not re.fullmatch(r'-?(0|[1-9][0-9]*)',text) or text=='-0':return 'UNSUPPORTED_NUMBER',''
  n=json.loads(text)
  return ('OK',str(n)) if isinstance(n,int) and abs(n)<=9007199254740991 else ('UNSAFE_NUMBER','')
 if kind=='candidate-integer-string':return ('OK',str(int(text))) if re.fullmatch(r'-?(0|[1-9][0-9]*)',text) and text!='-0' else ('INVALID_NUMBER_LEXEME','')
 if kind=='candidate-decimal-string':
  if not re.fullmatch(r'-?(0|[1-9][0-9]*)\.[0-9]+',text) or (text.startswith('-') and decimal.Decimal(text).is_zero()):return 'INVALID_NUMBER_LEXEME',''
  return 'OK',format(decimal.Decimal(text),'f')
 return 'UNKNOWN_NUMBER_KIND',''
def sha(body): return hashlib.sha256(body).hexdigest()
def sign(s):
 path=s['path']; path='/' if not path.strip() else path if path.startswith('/') else '/'+path
 body=base64.b64decode(s['bodyBase64']+'===' )
 preimage='\n'.join([s['method'],path,s['timestamp'],s['nonce'],sha(body)])
 return dict(preimage=preimage,hmac=hmac.new(s['secret'].encode(),preimage.encode(),hashlib.sha256).hexdigest())
def admit(v):
 if v.get('schemaVersion')!=1:return 'UNKNOWN_SCHEMA'
 if v['profile']!='tpf-polyglot-probe/0': return 'UNKNOWN_PROFILE'
 if not isinstance(v.get('capabilities'),list) or len(v['capabilities'])!=1:return 'UNSUPPORTED_CAPABILITY'
 if v.get('retryOwner')!='local-invocation-shell' or v.get('retryLimit')!=2:return 'POLICY_CONFLICT'
 if any(c!='pure-unary' for c in v['capabilities']): return 'UNSUPPORTED_CAPABILITY'
 return 'OK' if v['payloadType']=='probe.Value/1' else 'UNKNOWN_PAYLOAD_TYPE'
class Failure(Exception):
 def __init__(self,shape): self.shape=shape
async def execute(v,context):
 state=dict(aAttempts=0,bAttempts=0,callerAttempts=0,outcome='RETRY_EXHAUSTED',result=0)
 observations=[]
 def finish():return dict(state,observations=observations) if context.get('capture') else state
 cancelled=asyncio.Event()
 async def a(value):
  state['aAttempts']+=1
  if v.get('cancelDuringA'):cancelled.set()
  await asyncio.sleep(0)
  observations.append(dict(step='A',executionId=context['executionId'],traceId=context.get('traceId','trace')))
  if v['terminal']: raise Failure(dict(code='BUSINESS_REJECTION',message='input rejected',retryable=False,owner='local-invocation-shell',stepId='A'))
  if state['aAttempts']-(state['callerAttempts']-1)*(v['retryLimit']+1)<=v['failuresBeforeSuccess']: raise Failure(dict(code='TRANSIENT',retryable=True))
  return value+1
 async def b(value):
  observations.append(dict(step='B',executionId=context['executionId'],traceId=context.get('traceId','trace')))
  state['bAttempts']+=1
  await asyncio.sleep(0)
  return value*2
 for delivery in range(v['deliveries']):
  state['callerAttempts']+=1
  for attempt in range(v['retryLimit']+1):
   try:
    value=await a(v['input'])
    if v['cancelBeforeB']: cancelled.set()
    if cancelled.is_set(): state['outcome']='CANCELLED'; return finish()
    state['result']=await b(value); state['outcome']='COMPLETED'; return finish()
   except Failure as failure:
    if not failure.shape['retryable']:
     equal(failure.shape,corpus['failureShape']); state['outcome']=failure.shape['code']; return finish()
 return finish()
def encode_root(state):
 if state=='ABSENT':raise ValueError('ABSENT_ROOT')
 return json.dumps(state)
async def main():
 try:encode_root('ABSENT');raise AssertionError('root absent encoded')
 except ValueError as failure:equal(str(failure),'ABSENT_ROOT')
 for v in corpus['presence']:
  state,diagnostic,obj=presence(v); equal(state,v['state']); equal(diagnostic,v['diagnostic']); equal(json.loads(json.dumps(obj)),obj)
 for v in corpus['numbers']:
  diagnostic,exact=number(v); equal(diagnostic,v['diagnostic'])
  if diagnostic=='OK': equal(exact,v['exact'])
 for v in corpus['unions']: equal('UNKNOWN_VARIANT' if v['tag'] not in ['count','label'] else 'OK' if (type(v['value']) is int and abs(v['value'])<=9007199254740991 if v['tag']=='count' else isinstance(v['value'],str)) else 'INVALID_VARIANT_VALUE',v['diagnostic'])
 for v in corpus['bytes']: equal(sha(base64.b64decode(v['base64'])),v['sha256'])
 equal(sign(corpus['signature']),dict(preimage=corpus['signature']['preimage'],hmac=corpus['signature']['hmac']))
 for v in corpus['authMutations']:
  candidate_pins=dict(corpus['pins'],**{v['field']:v['value']})
  diagnostic=('OK' if all(candidate_pins[k]==val for k,val in corpus['pins'].items()) else 'IDENTITY_MISMATCH') if v['field'] in corpus['pins'] else 'BAD_SIGNATURE' if sign(dict(corpus['signature'],**{v['field']:v['value']}))['hmac']!=corpus['signature']['hmac'] else 'OK'
  equal(diagnostic,v['diagnostic'])
 nonce_store=set()
 def admit_nonce(nonce):
  if nonce in nonce_store:return 'NONCE_REPLAY'
  nonce_store.add(nonce);return 'OK'
 equal(admit_nonce(corpus['signature']['nonce']),'OK');equal(admit_nonce(corpus['signature']['nonce']),'NONCE_REPLAY')
 for v in corpus['admission']:
  invoked=0;diagnostic=admit(v)
  if diagnostic=='OK':
   invoked=(await execute(corpus['executions'][0],dict(executionId='admitted',traceId='trace')))['aAttempts']
  equal(diagnostic,v['diagnostic']);equal(invoked,1 if v['diagnostic']=='OK' else 0)
 def failure_diagnostic(f):
  if not {'code','message','retryable','owner','stepId'}<=set(f) or type(f['retryable']) is not bool or not isinstance(f['message'],str) or f['stepId']!='A':return 'MALFORMED_FAILURE'
  if f['code'] not in ['BUSINESS_REJECTION','TRANSIENT']:return 'UNKNOWN_FAILURE'
  return 'FAILURE_POLICY_CONFLICT' if f['owner']!='local-invocation-shell' or f['retryable']!=(f['code']=='TRANSIENT') else 'OK'
 for v in corpus['failures']:equal(failure_diagnostic(v['envelope']),v['diagnostic'])
 def validate_context(c):return 'OK' if set(c)<= {'executionId','traceId'} else 'RESTRICTED_CONTEXT'
 for key in corpus['forbiddenContextKeys']:equal(validate_context(dict(executionId='x',**{key:'secret'})),'RESTRICTED_CONTEXT')
 for v in corpus['executions']:equal(await execute(v,dict(executionId=v['id'],expectedExecutionId=v['id'])),v['expected'])
 async def run_context(c):
  equal(validate_context(dict(executionId=c['executionId'],traceId=c['traceId'])),'OK')
  state=await execute(dict(corpus['executions'][0],input=c['input']),dict(executionId=c['executionId'],traceId=c['traceId'],capture=True))
  return dict(executionId=c['executionId'],result=state['result'],observations=state['observations'])
 equal(await asyncio.gather(*(run_context(c) for c in corpus['contexts'])),[dict(executionId=c['expectedExecutionId'],result=c['expected'],observations=[dict(step=step,executionId=c['expectedExecutionId'],traceId=c['expectedTraceId']) for step in ['A','B']]) for c in corpus['contexts']])
 for actual,mutated in [(presence(next(v for v in corpus['presence'] if v['state']=='NULL'))[0],'ABSENT'),(number(next(v for v in corpus['numbers'] if v['id']=='large-candidate'))[1],'9007199254740992'),((await execute(corpus['executions'][1],dict(executionId='m',expectedExecutionId='m')))['aAttempts'],2)]:
  assert actual!=mutated
  global checks
  checks+=1
 print(json.dumps(dict(language='python',checks=checks,status='PASS',profile=corpus['profile'],evidence='handcrafted-non-production')))
asyncio.run(main())
