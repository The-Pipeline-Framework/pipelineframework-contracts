#!/usr/bin/env python3
"""Build/run test-only consumers without changing Maven artifact dependency graphs."""
import argparse, os, pathlib, subprocess, xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--skip-build',action='store_true');parser.add_argument('--vectors',type=pathlib.Path,default=root/'conformance/polyglot-v0/vectors.json');args=parser.parse_args()
def run(command):subprocess.run([str(x) for x in command],cwd=root,check=True)
if not args.skip_build:run(['./mvnw','-B','-pl','runtime-serialization,runtime-protocol','-am','package','-Dgpg.skip',f'-Dmaven.repo.local={root}/.m2/repository'])
ns={'m':'http://maven.apache.org/POM/4.0.0'}
pom=ET.parse(root/'pom.xml').getroot()
# Resolve only the exact published dependency coordinates already declared by this POM.
wanted={'jackson-annotations','jackson-core','jackson-databind','protobuf-java','protobuf-java-util'}
classpath=[root/m/'target/classes' for m in ['runtime-core','semantic-model','runtime-serialization','runtime-protocol']]
for dep in pom.findall('m:dependencyManagement/m:dependencies/m:dependency',ns):
 artifact=dep.findtext('m:artifactId',namespaces=ns)
 if artifact in wanted:
  group=dep.findtext('m:groupId',namespaces=ns);version=dep.findtext('m:version',namespaces=ns)
  jar=root/'.m2/repository'/group.replace('.','/')/artifact/version/f'{artifact}-{version}.jar'
  if not jar.is_file():raise SystemExit(f'Missing exact dependency {jar}; run without --skip-build')
  classpath.append(jar)
cp=os.pathsep.join(map(str,classpath));output=root/'target/polyglot-v0';output.mkdir(parents=True,exist_ok=True)
run(['javac','--release','21','-cp',cp,'-d',output,root/'conformance/polyglot-v0/java/PolyglotProbeConsumer.java'])
run(['java','-cp',os.pathsep.join([str(output),cp]),'PolyglotProbeConsumer',args.vectors])
run(['node',root/'conformance/polyglot-v0/consumer.mjs',args.vectors])
run(['python3',root/'conformance/polyglot-v0/consumer.py',args.vectors])
# Fixed mutation witnesses: all three independent consumers must reject changed oracles.
import copy, json, tempfile
original=json.loads(args.vectors.read_text())
mutations=[]
changed=copy.deepcopy(original);next(v for v in changed['presence'] if v['state']=='NULL')['state']='ABSENT';mutations.append(('null',changed))
changed=copy.deepcopy(original);next(v for v in changed['numbers'] if v['id']=='large-candidate')['exact']='9007199254740992';mutations.append(('precision',changed))
changed=copy.deepcopy(original);next(v for v in changed['executions'] if v['id']=='retry')['expected']['aAttempts']=2;mutations.append(('retry',changed))
commands=[['java','-cp',os.pathsep.join([str(output),cp]),'PolyglotProbeConsumer'],['node',str(root/'conformance/polyglot-v0/consumer.mjs')],['python3',str(root/'conformance/polyglot-v0/consumer.py')]]
with tempfile.TemporaryDirectory(prefix='tpf-polyglot-mutations-') as temporary:
 for name,changed in mutations:
  fixture=pathlib.Path(temporary)/f'{name}.json';fixture.write_text(json.dumps(changed))
  for command in commands:
   result=subprocess.run(command+[str(fixture)],cwd=root,capture_output=True,text=True)
   if result.returncode==0:raise SystemExit(f'Mutation {name} unexpectedly accepted by {command[0]}')
print(json.dumps(dict(mutationChecks=9,status='PASS',mutations=['null','precision','retry'])))
