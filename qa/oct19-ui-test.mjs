import {createRequire} from 'node:module';
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const {JSDOM}=createRequire(import.meta.url)(process.env.REVIEW_QA_JSDOM||'jsdom');
const dom=new JSDOM(await fs.readFile(new URL('../frontend/index.html',import.meta.url),'utf8'),{url:'http://localhost/',pretendToBeVisual:true});
for(const k of ['window','document','HTMLElement','Element','Node','SVGElement','Document','Event','MouseEvent','KeyboardEvent'])globalThis[k]=k==='window'?dom.window:dom.window[k];
globalThis.localStorage=window.localStorage;globalThis.requestAnimationFrame=window.requestAnimationFrame.bind(window);window.scrollTo=()=>{};
const errors=[];console.error=(...x)=>errors.push(x.join(' '));
const requests=[];globalThis.fetch=async(url,opt={})=>{const body=opt.body?JSON.parse(opt.body):null;requests.push({url,...opt,body});let result=[];
 if(url.includes('/drafts/')&&url.includes('/record?'))result={draft:{id:'d1',revision:1},finding:{id:'f1',recordVersion:1,workflow:'待核实',violation:'原风险原因，保持原样'},source:null};
 else if(url.endsWith('/drafts')&&opt.method==='POST')result={id:'d1'};
 else if(opt.method==='PATCH')result={draftRevision:2,recordVersion:2};
 else if(url.includes('/query'))result={rows:[],total:0,counts:{user:0,matrix:0}};
 return new Response(JSON.stringify(result),{headers:{'Content-Type':'application/json'}});
};
const {app}=await import('../frontend/app.js');const {RULES,MATRIX_RULES}=await import('../frontend/data.js');
app.me={name:'aa',canWrite:true,canPublish:true,demo:true};app.ready=true;
app.products.push({id:'P06',name:'财经法务产品',releaseId:'r1',icon:'scale',metrics:{total:1},userCoverage:{},matrixCoverage:{},summary:[]});
const tick=()=>new Promise(r=>setTimeout(r,15));
assert.equal(RULES.length,8);assert.equal(RULES[7].name,'权限矩阵明细检查');
for(const kind of ['user','matrix']){app.page='details';app.changeKind(kind);await tick();const select=document.querySelector('[aria-label="筛选处理状态"]');assert.deepEqual([...select.options].map(o=>o.textContent),['全部记录（不含误报）','待人工确认','待复核','已确认风险','已整改','全部记录（含误报）']);
 app.workflowFilter='全部记录';let q=app.queryPayload();assert.equal(q.includeFalsePositive,true);assert.ok(!q.filters.some(f=>f.field==='workflow'));
 for(const state of ['待人工确认','待复核','已确认风险','已整改']){app.workflowFilter=state;q=app.queryPayload();assert.ok(q.filters.some(f=>f.field==='workflow'&&f.value===state));assert.equal(q.includeFalsePositive,false);}
 app.workflowFilter='';assert.equal(app.queryPayload().includeFalsePositive,false);
 if(kind==='matrix'){await tick();const names=[...document.querySelector('[aria-label="筛选检查项"]').options].map(o=>o.textContent);for(const m of MATRIX_RULES)assert.ok(names.includes(m.id+' · '+m.name));}
 app.drawer={kind,recordId:'f1',productId:'P06',workflow:'待核实',violation:'原风险原因，保持原样',roleName:'角色',userName:'用户',permissionName:'权限'};
 await app.startEdit('status');await tick();assert.equal(app.editing,true);assert.deepEqual(app.rawColumns.map(c=>c.key),['workflow']);assert.ok(document.querySelector('.raw-field select'));
 app.draft.workflow='已确认误报';app.editReason='核实原始依据';await app.saveEdit();const patch=requests.filter(r=>r.method==='PATCH').at(-1);assert.deepEqual(patch.body.changes,{workflow:'已确认误报'});assert.equal(app.drawer.violation,'原风险原因，保持原样');assert.equal(app.drawer.draftPending,true);app.closeDrawer();
}
for(const page of ['overview','summary','details','sources','data','rules']){app.page=page;await tick();assert.ok(document.querySelector('main'));assert.ok(!document.querySelector('main').textContent.includes('违规'));}
assert.ok(!document.querySelector('.brand').textContent.includes('审查报告系统'));assert.ok(!document.body.textContent.includes('FINANCE'));assert.deepEqual(errors,[]);
console.log(JSON.stringify({passed:true,pages:6,statusSelectors:2,statusEdits:2,matrixNames:6,vueErrors:0}));process.exit(0);
