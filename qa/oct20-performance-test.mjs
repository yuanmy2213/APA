import {createRequire} from 'node:module';
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const {JSDOM}=createRequire(import.meta.url)(process.env.REVIEW_QA_JSDOM||'jsdom');
const dom=new JSDOM(await fs.readFile(new URL('../frontend/index.html',import.meta.url),'utf8'),{url:'https://example.test/apa/',pretendToBeVisual:true});
for(const k of ['window','document','HTMLElement','Element','Node','SVGElement','Document','Event','MouseEvent','KeyboardEvent'])globalThis[k]=k==='window'?dom.window:dom.window[k];
globalThis.localStorage=window.localStorage;globalThis.requestAnimationFrame=window.requestAnimationFrame.bind(window);window.scrollTo=()=>{};
const errors=[];console.error=(...x)=>errors.push(x.join(' '));const calls=[];let draftRelease,holdDraft=true,slowNext=false;
const raw={id:'source-1',employeeId:'L00566785',userName:'测试',permissionName:'角色',authorizationId:'00007',description:'原描述',recordVersion:1};
const draft={id:'d1',productId:'P06',createdBy:'aa',baseReleaseId:'r1',userDatasetId:'ds1',revision:1,state:'VALIDATED'};
const sourceRow={recordId:'original1',kind:'user',productId:'P06',...raw};
globalThis.fetch=async(url,options={})=>{
 const parsed=new URL(url,document.baseURI);assert.ok(parsed.pathname.startsWith('/apa/api/'));calls.push({url,parsed,options});const p=parsed.pathname.replace('/apa/api','');let result=[];
 if(p==='/snapshots')result={id:'snap-1'};
 else if(p.startsWith('/dashboard'))result={createdAt:'2026-10-20T10:00:00+08:00',products:[{id:'P06',name:'财经法务产品',icon:'scale',releaseId:'r1',metrics:{total:1},userCoverage:{},matrixCoverage:{},summary:[]}],overall:{total:1},ruleDistribution:[]};
 else if(p==='/source-records/query'||p==='/findings/query'){
  if(slowNext){slowNext=false;return await new Promise((resolve,reject)=>{options.signal.addEventListener('abort',()=>reject(new DOMException('cancelled','AbortError')),{once:true});});}
  const body=JSON.parse(options.body);result={rows:[{...sourceRow,userName:body.q||'当前页'}],total:1,counts:{user:1,matrix:0}};
 }
 else if(p.startsWith('/source-records/'))result={...sourceRow,sourceOnly:true,raw:{...raw},auditedRaw:null};
 else if(p==='/products/P06/drafts'&&options.method==='POST'){
  if(holdDraft)await new Promise(resolve=>{draftRelease=resolve;});result={...draft};
 }
 else if(p==='/drafts/d1/record')result={draft:{...draft},source:{...raw}};
 else if(options.method==='PATCH'){
  const body=JSON.parse(options.body);Object.assign(raw,body.changes);raw.recordVersion++;draft.revision++;result={recordVersion:raw.recordVersion,draftRevision:draft.revision,affected:1};
 }
 return new Response(JSON.stringify(result),{headers:{'Content-Type':'application/json'}});
};
const {app}=await import('../frontend/app.js');const tick=ms=>new Promise(r=>setTimeout(r,ms??15));
app.me={name:'aa',canWrite:true,canPublish:true};app.ready=true;await app.refresh();app.page='sources';await tick(230);calls.length=0;
await app.openRecord(sourceRow,true);const opening=app.startEdit('source');await tick();await app.startEdit('source');
assert.equal(calls.filter(c=>c.parsed.pathname.endsWith('/products/P06/drafts')).length,1);assert.equal(app.editLoading,true);assert.ok(document.querySelector('.editor-loading'));
holdDraft=false;draftRelease();await opening;await tick();assert.equal(app.editing,true);assert.ok(!document.querySelector('input#raw-authorizationId'));
app.editReason='验证表单独立渲染';await tick();const instance=app.$,render=instance.render;let rootRenders=0;instance.render=function(...args){rootRenders++;return render.apply(this,args);};
for(let i=0;i<24;i++){const input=document.querySelector('#raw-description');input.value='新描述'+i;input.dispatchEvent(new Event('input',{bubbles:true}));await tick(1);}
assert.ok(rootRenders<=3,'Each keystroke re-rendered the full app: '+rootRenders);instance.render=render;
calls.length=0;await app.saveEdit();assert.equal(calls.filter(c=>c.options.method==='PATCH').length,1);assert.ok(!calls.some(c=>['/drafts','/import-jobs','/activity'].includes(c.parsed.pathname.replace('/apa/api',''))));
app.closeDrawer();await app.openRecord(sourceRow,true);await app.startEdit('source');assert.ok(!calls.some(c=>c.parsed.pathname.endsWith('/products/P06/drafts')));app.editing=false;app.closeDrawer();
app.page='details';await tick(230);calls.length=0;slowNext=true;const stale=app.loadDetails();await tick();const pending=calls.at(-1);for(let i=0;i<12;i++){app.detailSearch='筛选'+i;await tick(2);}await stale;await tick(240);
assert.equal(pending.options.signal.aborted,true);assert.equal(app.filteredDetails[0].userName,'筛选11');assert.ok(calls.filter(c=>c.parsed.pathname.endsWith('/findings/query')).length<=2);assert.deepEqual(errors,[]);
console.log(JSON.stringify({passed:true,contextPath:'/apa',duplicateDraftsPrevented:true,draftReuse:true,fullAppRendersFor24Keystrokes:rootRenders,saveWithoutWholePageReload:true,staleRequestAborted:true,debouncedQueries:true,vueErrors:0}));process.exit(0);
