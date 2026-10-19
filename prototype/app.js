import {createApp,ref,reactive,computed,watch,nextTick,onMounted,h} from './vendor/vue.esm-browser.prod.js';
import {SNAPSHOT,RULE_VERSION,RULES,MATRIX_RULES,LEVELS,WORKFLOWS,isCountedRisk,activeRiskRows,USER_COLUMNS,MATRIX_COLUMNS,DEFAULT_USER_COLUMNS,DEFAULT_MATRIX_COLUMNS,makeDemo,stats,globalStats,riskRows,ruleRows,applyFilters} from './data.js?v=1.2.0';
import {makeWorkbook,readUpload} from './workbook.js';

const iconPaths={
  shield:'<path d="M12 3 20 6v6c0 5-4 8-8 10-4-2-8-5-8-10V6z"/><path d="m8.5 12 2.3 2.3 4.7-5"/>',
  shieldAlert:'<path d="M12 3 20 6v6c0 5-4 8-8 10-4-2-8-5-8-10V6z"/><path d="M12 8v5m0 3h.01"/>',
  layout:'<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 9v12"/>',
  grid:'<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  chart:'<path d="M4 3v17h17M8 16v-5m5 5V6m5 10v-7"/>',
  pie:'<path d="M12 3a9 9 0 1 0 9 9h-9z"/><path d="M16 3a7 7 0 0 1 5 5h-5z"/>',
  file:'<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M8 12h8m-8 4h6"/>',
  receipt:'<path d="m5 3 2 1 2-1 3 1 3-1 2 1 2-1v18l-2-1-2 1-3-1-3 1-2-1-2 1zM9 8h6m-6 4h6m-6 4h4"/>',
  wallet:'<path d="M20 8V5a2 2 0 0 0-2-2H5a3 3 0 0 0 0 6h15v11H5a3 3 0 0 1-3-3V6"/><path d="M20 12h-5v4h5m-3-2h.01"/>',
  scale:'<path d="M12 3v18M5 21h14M4 7l8-3 8 3M4 7l-3 7h6zm16 0-3 7h6z"/>',
  briefcase:'<rect x="3" y="7" width="18" height="14" rx="2"/><path d="M8 7V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v3M3 12a20 20 0 0 0 18 0m-9 0v3"/>',
  arrows:'<path d="M4 7h16l-4-4m4 14H4l4 4M20 7l-4 4M4 17l4-4"/>',
  trend:'<path d="m3 17 6-6 4 4 8-10m-6 0h6v6"/>',
  check:'<path d="m5 12 4 4L19 6"/>',
  circleCheck:'<circle cx="12" cy="12" r="9"/><path d="m8 12 2.5 2.5L16 9"/>',
  spark:'<path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5zM20 2v4m-2-2h4"/>',
  lock:'<rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V6a4 4 0 0 1 8 0v4m-4 5v2"/>',
  database:'<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/>',
  layers:'<path d="m12 3 10 5-10 5L2 8zm-10 9 10 5 10-5M2 16l10 5 10-5"/>',
  globe:'<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c-5 5-5 13 0 18 5-5 5-13 0-18"/>',
  key:'<circle cx="8" cy="8" r="5"/><path d="m12 12 9 9m-4-4 3-3m-6 0 3-3"/>',
  users:'<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-3.9M16 3a4 4 0 0 1 0 8"/><circle cx="9" cy="7" r="4"/>',
  userCheck:'<circle cx="9" cy="7" r="4"/><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m14-10 2 2 4-4"/>',
  scan:'<path d="M3 8V4a1 1 0 0 1 1-1h4m8 0h4a1 1 0 0 1 1 1v4m0 8v4a1 1 0 0 1-1 1h-4M8 21H4a1 1 0 0 1-1-1v-4M7 12h10m-5-5v10"/>',
  focus:'<path d="M3 8V4h5m8 0h5v4m0 8v5h-5M8 21H3v-5"/><circle cx="12" cy="12" r="4"/>',
  search:'<circle cx="10.5" cy="10.5" r="7.5"/><path d="m16 16 5 5"/>',
  chevronRight:'<path d="m9 5 7 7-7 7"/>',chevronLeft:'<path d="m15 5-7 7 7 7"/>',arrowRight:'<path d="M4 12h16m-6-6 6 6-6 6"/>',arrowLeft:'<path d="M20 12H4m6-6-6 6 6 6"/>',arrowUpRight:'<path d="M6 18 18 6M6 6h12v12"/>',
  download:'<path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/>',upload:'<path d="M12 16V4m-5 5 5-5 5 5M4 16v5h16v-5"/>',
  info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10h.01"/>',clock:'<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 2"/>',columns:'<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16m6-16v16"/>',filter:'<path d="M3 4h18l-7 8v6l-4 3v-9z"/>',edit:'<path d="m16 3 5 5-11 11-6 1 1-6zM14 5l5 5"/>',x:'<path d="m6 6 12 12M6 18 18 6"/>',menu:'<path d="M3 6h18M3 12h18M3 18h18"/>',monitor:'<rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8m-4-4v4"/>'
};
const UiIcon={props:{name:String,size:{type:Number,default:18}},setup:props=>()=>h('svg',{width:props.size,height:props.size,viewBox:'0 0 24 24',fill:'none',stroke:'currentColor','stroke-width':1.6,'stroke-linecap':'round','stroke-linejoin':'round',class:'ui-icon','aria-hidden':'true',innerHTML:iconPaths[props.name]||iconPaths.grid})};
const STORAGE_KEY='finance-permission-ui-1';
const routeNames={overview:'全域概览',summary:'产品审查',details:'风险明细',data:'数据管理',rules:'审查规则'};
const timeNow=()=>new Date().toLocaleString('sv-SE',{timeZone:'Asia/Shanghai'}).slice(0,16);
const clone=obj=>JSON.parse(JSON.stringify(obj));

export const app=createApp({
  components:{UiIcon},
  setup(){
    const products=reactive(makeDemo());const activity=ref([]);
    let restoreError=false;
    try{
      const cache=JSON.parse(localStorage.getItem(STORAGE_KEY)||'null');
      if(cache?.version===1&&Array.isArray(cache.products)){
        cache.products.forEach(saved=>{
          const p=products.find(p=>p.id===saved.id);
          if(p&&Array.isArray(saved.userRisks)&&Array.isArray(saved.matrixRisks)&&saved.userRisks.length<=20000&&saved.matrixRisks.length<=20000){
            p.userRisks=saved.userRisks.map(r=>({...r,checkName:RULES.find(x=>x.id===r.checkItem)?.name||r.checkName}));p.matrixRisks=saved.matrixRisks.map(r=>{
              if(saved.imported?.matrix)return r;
              const original=p.matrixRisks.find(x=>x.recordId===r.recordId&&x.pointId===r.pointId);
              if(!original)return r;
              // Repair only exact demo reasons generated by the mistaken 19 October change.
              const mistakenTypes=[['业务操作','业务管控'],['业务操作','IT运维特权'],['业务操作','IT运维特权'],['业务操作','IT运维特权','业务管控'],['IT运维特权','业务管控'],['业务操作','IT运维特权']];
              const index=Number(r.recordId.match(/-M(\d+)$/)?.[1])-1;
              if(!Number.isInteger(index)||index<0)return r;
              const types=mistakenTypes[index%6];
              const mistakenReason=`${original.roleType}角色绑定了${types[index%types.length]}能力，超出该角色职责范围。`;
              return r.violation===mistakenReason?{...r,violation:original.violation}:r;
            });p.updatedAt=saved.updatedAt||SNAPSHOT;p.imported=saved.imported||{user:null,matrix:null};
          }
        });
        if(Array.isArray(cache.activity))activity.value=cache.activity;
      }
    }catch{restoreError=true;}
    const page=ref('overview'),selectedProduct=ref('P06'),detailKind=ref('user'),mobileNav=ref(false);
    const productSearch=ref(''),productFilter=ref('all'),productSort=ref('risk'),onlyRiskRules=ref(false);
    const detailSearch=ref(''),ruleFilter=ref(''),riskFilter=ref(''),workflowFilter=ref(''),headerFilters=reactive({}),activeHeader=ref(''),columnsOpen=ref(false);
    const userVisible=ref([...DEFAULT_USER_COLUMNS]),matrixVisible=ref([...DEFAULT_MATRIX_COLUMNS]);
    const detailPage=ref(1),pageSize=ref(10);
    const toastMessage=ref(''),toastError=ref(false);let toastTimer;
    function toast(message,error=false){clearTimeout(toastTimer);toastMessage.value=message;toastError.value=error;toastTimer=setTimeout(()=>toastMessage.value='',error?6500:3800);}
    const currentProduct=computed(()=>products.find(p=>p.id===selectedProduct.value)||products[5]);
    const currentStats=computed(()=>stats(currentProduct.value));
    const overall=computed(()=>globalStats(products));
    const pageName=computed(()=>routeNames[page.value]);
    const navigation=[{id:'overview',label:'全域概览',icon:'layout'},{id:'summary',label:'产品审查',icon:'grid'},{id:'details',label:'风险明细',icon:'shieldAlert'}];
    const management=[{id:'data',label:'数据管理',icon:'database'},{id:'rules',label:'审查规则',icon:'file'}];
    const ruleDistribution=computed(()=>RULES.map((rule,i)=>{const count=products.flatMap(p=>[...p.userRisks,...p.matrixRisks]).filter(isCountedRisk).filter(r=>rule.id==='8'?r.kind==='matrix':r.kind==='user'&&r.checkItem===rule.id).length;return {...rule,count,share:overall.value.total?count/overall.value.total:0,color:['#4779b8','#7596c4','#5c91a1','#82aab5','#82946d','#c6a16d','#9b8ab4','#4c637f'][i]};}));
    const rankedRiskRules=computed(()=>[...ruleDistribution.value].filter(r=>r.count>0).sort((a,b)=>b.count-a.count||Number(a.id)-Number(b.id)));
    const chartRiskRules=computed(()=>rankedRiskRules.value.map((r,i)=>({...r,color:['#d58989','#d8b077','#86a3ca','#c8d5e3','#d4dde7','#dcd5e1','#dce2e8','#e6e9ee'][i]})));
    const topRiskRules=computed(()=>chartRiskRules.value.slice(0,3));
    const topRuleShare=computed(()=>overall.value.total?Math.round(topRiskRules.value.reduce((n,r)=>n+r.count,0)/overall.value.total*100):0);
    const shareLabel=v=>((Number(v)||0)*100).toFixed(1)+'%';
    function goDistributionRule(rule){selectedProduct.value='all';detailKind.value=rule.kind;clearDetails();ruleFilter.value=rule.id;navigate('details');}
    const levelData=computed(()=>[{key:'高',count:overall.value.high,color:'#d58989'},{key:'中',count:overall.value.medium,color:'#d8b077'},{key:'低',count:overall.value.low,color:'#86a3ca'}]);
    const topProducts=computed(()=>[...products].sort((a,b)=>stats(b).total-stats(a).total).slice(0,3));
    const topShare=computed(()=>overall.value.total?Math.round(topProducts.value.reduce((n,p)=>n+stats(p).total,0)/overall.value.total*100):0);
    const filteredProducts=computed(()=>{
      let result=products.filter(p=>p.name.includes(productSearch.value.trim())&&(productFilter.value==='all'||(productFilter.value==='risk'?stats(p).total>0:stats(p).total===0)));
      if(productSort.value==='risk')result=[...result].sort((a,b)=>stats(b).total-stats(a).total);else if(productSort.value==='auth')result=[...result].sort((a,b)=>b.authorizations-a.authorizations);
      return result;
    });
    const summaryRules=computed(()=>ruleRows(currentProduct.value).filter(r=>!onlyRiskRules.value||r.count));
    const detailProducts=computed(()=>selectedProduct.value==='all'?products:[currentProduct.value]);
    const detailCounts=computed(()=>({user:detailProducts.value.reduce((n,p)=>n+p.userRisks.filter(isCountedRisk).length,0),matrix:detailProducts.value.reduce((n,p)=>n+p.matrixRisks.filter(isCountedRisk).length,0)}));
    const detailAllRows=computed(()=>detailProducts.value.flatMap(p=>p[detailKind.value==='user'?'userRisks':'matrixRisks']));
    const excludedCount=computed(()=>detailAllRows.value.filter(r=>!isCountedRisk(r)).length);
    const filteredDetails=computed(()=>applyFilters(workflowFilter.value==='已确认误报'||workflowFilter.value==='全部记录'?detailAllRows.value:detailAllRows.value.filter(isCountedRisk),{q:detailSearch.value,risk:riskFilter.value,rule:ruleFilter.value,workflow:workflowFilter.value==='全部记录'?'':workflowFilter.value,headers:headerFilters}));
    const pageCount=computed(()=>Math.max(1,Math.ceil(filteredDetails.value.length/pageSize.value)));
    const pagedDetails=computed(()=>filteredDetails.value.slice((detailPage.value-1)*pageSize.value,detailPage.value*pageSize.value));
    const detailColumns=computed(()=>detailKind.value==='user'?USER_COLUMNS:MATRIX_COLUMNS);
    const visibleKeys=computed(()=>detailKind.value==='user'?userVisible.value:matrixVisible.value);
    const shownColumns=computed(()=>detailColumns.value.filter(c=>visibleKeys.value.includes(c.key)));
    const detailTableWidth=computed(()=>shownColumns.value.reduce((n,c)=>n+c.width,125)+(selectedProduct.value==='all'?155:0));
    const allColumnsVisible=computed(()=>visibleKeys.value.length===detailColumns.value.length);
    const anyDetailFilter=computed(()=>!!(detailSearch.value||ruleFilter.value||riskFilter.value||workflowFilter.value||Object.values(headerFilters).some(Boolean)));
    function clearHeaders(){Object.keys(headerFilters).forEach(k=>delete headerFilters[k]);activeHeader.value='';}
    function clearDetails(){detailSearch.value='';ruleFilter.value='';riskFilter.value='';workflowFilter.value='';clearHeaders();}
    function changeKind(kind){detailKind.value=kind;clearDetails();columnsOpen.value=false;}
    function toggleColumn(key){const r=detailKind.value==='user'?userVisible:matrixVisible;if(r.value.includes(key)){if(r.value.length===1)return;r.value=r.value.filter(k=>k!==key);}else r.value=[...r.value,key];}
    function resetColumns(){if(detailKind.value==='user')userVisible.value=[...DEFAULT_USER_COLUMNS];else matrixVisible.value=[...DEFAULT_MATRIX_COLUMNS];}
    function toggleAllColumns(){if(allColumnsVisible.value)resetColumns();else if(detailKind.value==='user')userVisible.value=USER_COLUMNS.map(c=>c.key);else matrixVisible.value=MATRIX_COLUMNS.map(c=>c.key);}
    function navigate(to){if(!routeNames[to])return;if(to==='summary'&&selectedProduct.value==='all')selectedProduct.value='P06';page.value=to;mobileNav.value=false;activeHeader.value='';columnsOpen.value=false;window.scrollTo?.({top:0,behavior:'instant'});}
    function openProduct(id){selectedProduct.value=id;onlyRiskRules.value=false;navigate('summary');}
    function openRule(rule){clearDetails();detailKind.value=rule.kind;ruleFilter.value=rule.id;navigate('details');}
    function goAllDetails(level){selectedProduct.value='all';detailKind.value='user';clearDetails();riskFilter.value=level;navigate('details');}
    function parseRoute(){const [p,id,kind]=window.location.hash.replace('#','').split('/');if(routeNames[p])page.value=p;if(products.some(x=>x.id===id)||id==='all')selectedProduct.value=id;if(['user','matrix'].includes(kind))detailKind.value=kind;if(page.value==='summary'&&selectedProduct.value==='all')selectedProduct.value='P06';}
    parseRoute();
    watch([page,selectedProduct,detailKind],()=>{document.title=`财经权限审查 · ${pageName.value}`;const hash=`#${page.value}${['summary','details'].includes(page.value)?'/'+selectedProduct.value:''}${page.value==='details'?'/'+detailKind.value:''}`;window.history.replaceState(null,'',hash);});
    watch([detailSearch,ruleFilter,riskFilter,workflowFilter,selectedProduct,detailKind,pageSize,()=>JSON.stringify(headerFilters)],()=>detailPage.value=1);
    watch(pageCount,()=>detailPage.value=Math.min(detailPage.value,pageCount.value));
    watch(selectedProduct,()=>{clearHeaders();});
    const fmt=v=>Number(v||0).toLocaleString('en-US');
    const confidence=v=>`${Math.round(Number(v)*100)}%`;
    const levelClass=v=>v==='高'?'level-high':v==='中'?'level-medium':'level-low';
    const resultClass=v=>v==='通过'?'status-pass':v==='风险'?'status-risk':v==='需人工确认'?'status-manual':['未检出记录','无待统计风险'].includes(v)?'status-neutral':'status-potential';
    function productOf(record){return products.find(p=>p.userRisks.some(r=>r.recordId===record.recordId)||p.matrixRisks.some(r=>r.recordId===record.recordId))||currentProduct.value;}
    function ruleForRecord(record){return RULES.find(r=>r.id===(record.kind==='matrix'?'8':record.checkItem))||RULES[0];}
    function commit(nextProducts,logs){
      const value={version:1,products:nextProducts.map(p=>({id:p.id,userRisks:p.userRisks,matrixRisks:p.matrixRisks,updatedAt:p.updatedAt,imported:p.imported})),activity:logs.slice(0,100)};
      try{localStorage.setItem(STORAGE_KEY,JSON.stringify(value));}catch{toast('浏览器存储空间不足或不可用，数据未修改。请减少导入条数后重试。',true);return false;}
      nextProducts.forEach(next=>{const target=products.find(p=>p.id===next.id);if(target!==next){target.userRisks=next.userRisks;target.matrixRisks=next.matrixRisks;target.updatedAt=next.updatedAt;target.imported=next.imported;}});
      activity.value=logs.slice(0,100);return true;
    }

    const drawer=ref(null),drawerEl=ref(null),editing=ref(false),draft=ref({}),editReason=ref(''),editError=ref('');
    const statusEditing=ref(false),statusDraft=ref(''),statusReason=ref(''),statusError=ref('');
    const statusChanged=computed(()=>!!drawer.value&&statusDraft.value!==drawer.value.workflow);
    function startStatusEdit(){if(editing.value)return;statusDraft.value=drawer.value.workflow;statusReason.value='';statusError.value='';statusEditing.value=true;}
    function saveStatus(){
      if(!drawer.value||!statusChanged.value)return;
      if(!WORKFLOWS.includes(statusDraft.value)){statusError.value='请选择有效的处理状态。';return;}
      if(!statusReason.value.trim()){statusError.value='请填写状态变更原因。';return;}
      const d=drawer.value,p=productOf(d),key=d.kind==='user'?'userRisks':'matrixRisks',now=timeNow(),reason=statusReason.value.trim();
      const history=[...(d.statusHistory||[]),{from:d.workflow,to:statusDraft.value,reason,time:now}];
      const next=p[key].map(r=>r.recordId===d.recordId?{...r,workflow:statusDraft.value,statusHistory:history,statusReason:reason,statusUpdatedAt:now}:r);
      const patch={...p,[key]:next,updatedAt:now};
      const logs=[{title:'修改处理状态 · '+p.name,detail:`${d.authorizationId||d.pointId}；${d.workflow} → ${statusDraft.value}。原因：${reason}`,time:now},...activity.value];
      if(!commit(products.map(x=>x.id===p.id?patch:x),logs))return;
      drawer.value=products.find(x=>x.id===p.id)[key].find(r=>r.recordId===d.recordId);statusEditing.value=false;
      toast(statusDraft.value==='已确认误报'?'已确认误报，已从全部风险统计中剔除；记录仍可筛选查看。':'处理状态已保存，风险统计已同步更新。');
    }
    const rawColumns=computed(()=>{
      if(!drawer.value)return [];
      const keys=drawer.value.kind==='user'?['employeeId','userName','department','permissionName','roleType','permissionType','description','startAt','expiresAt','authorizationId','application']:['roleName','roleType','setName','pointName','pointId','resourceType','subtype'];
      return (drawer.value.kind==='user'?USER_COLUMNS:MATRIX_COLUMNS).filter(c=>keys.includes(c.key));
    });
    const rawRecord=computed(()=>drawer.value?.raw||drawer.value||{});
    const hasChanges=computed(()=>rawColumns.value.some(c=>String(draft.value[c.key]??'')!==String(rawRecord.value[c.key]??'')));
    function openRecord(r){drawer.value=r;editing.value=false;statusEditing.value=false;editError.value='';}
    function closeDrawer(){if((editing.value&&hasChanges.value)||(statusEditing.value&&(statusChanged.value||statusReason.value.trim()))){toast('有未保存修改，请先保存或点击“取消”结束编辑。',true);return;}drawer.value=null;editing.value=false;statusEditing.value=false;}
    function startEdit(){if(statusEditing.value)return;draft.value=Object.fromEntries(rawColumns.value.map(c=>[c.key,rawRecord.value[c.key]||'']));editReason.value='';editError.value='';editing.value=true;}
    function saveEdit(){
      if(!drawer.value||!editReason.value.trim()||!hasChanges.value)return;
      const d=drawer.value,p=productOf(d),kind=d.kind==='user'?'userRisks':'matrixRisks';
      const required=d.kind==='user'?['employeeId','permissionName','roleType','authorizationId']:['roleName','roleType','setName','pointName','pointId','subtype'];
      if(required.some(k=>!String(draft.value[k]||'').trim())){editError.value='工号、角色、权限与标识等关键字段不能为空。';return;}
      if(d.kind==='user'&&draft.value.startAt&&draft.value.expiresAt&&draft.value.expiresAt<draft.value.startAt){editError.value='有效期不能早于启用时间。';return;}
      const matches=r=>d.kind==='user'?r.authorizationId===d.authorizationId:r.roleName===d.roleName&&r.setName===d.setName&&r.pointId===d.pointId;
      const affected=p[kind].filter(matches).length;
      const now=timeNow(),reason=editReason.value.trim(),raw=clone(draft.value);
      const next=p[kind].map(r=>matches(r)?{...r,raw,workflow:'待复核',changeReason:reason,modifiedAt:now}:r);
      const patch={...p,[kind]:next,updatedAt:now};
      const logs=[{title:`修改原始${d.kind==='user'?'授权':'权限矩阵'}数据 · ${p.name}`,detail:`${d.authorizationId||d.pointId}；${affected}条关联风险待复核。原因：${reason}`,time:now},...activity.value];
      if(!commit(products.map(x=>x.id===p.id?patch:x),logs))return;
      drawer.value=products.find(x=>x.id===p.id)[kind].find(r=>r.recordId===d.recordId);editing.value=false;toast(`原始数据已保存，${affected}条关联风险进入待复核。`);
    }

    const importOpen=ref(false),importEl=ref(null),importKind=ref('user'),importProduct=ref('P06'),importFilename=ref(''),importRecords=ref([]),importErrors=ref([]),importConfirmed=ref(false),importBusy=ref(false),importDragging=ref(false),importFileKey=ref(0);let fileSequence=0;
    const importTarget=computed(()=>products.find(p=>p.id===importProduct.value)||products[5]);
    const importExisting=computed(()=>importTarget.value[importKind.value==='user'?'userRisks':'matrixRisks'].length);
    const canImport=computed(()=>!importBusy.value&&importRecords.value.length>0&&!importErrors.value.length&&importConfirmed.value);
    const importPreviewColumns=computed(()=>importKind.value==='user'?USER_COLUMNS.filter(c=>['checkItem','employeeId','userName','permissionName','risk'].includes(c.key)):MATRIX_COLUMNS.filter(c=>['subcheck','roleName','pointName','subtype','confidence'].includes(c.key)));
    function resetImportFile(){fileSequence++;importFilename.value='';importRecords.value=[];importErrors.value=[];importConfirmed.value=false;importBusy.value=false;importFileKey.value++;}
    function openImport(kind){importKind.value=kind;importProduct.value=selectedProduct.value==='all'?'P06':selectedProduct.value;resetImportFile();importOpen.value=true;}
    function closeImport(){fileSequence++;importOpen.value=false;importDragging.value=false;importBusy.value=false;}
    async function loadFile(file){
      resetImportFile();if(!file)return;const seq=fileSequence;importFilename.value=file.name;
      if(!/\.(xlsx|xls|csv)$/i.test(file.name)){importErrors.value=['请选择XLSX、XLS或CSV文件。'];return;}
      if(file.size>15*1024*1024){importErrors.value=['文件超过15MB，请减少数据后再上传。'];return;}
      importBusy.value=true;const kind=importKind.value,id=importProduct.value;
      try{const result=await readUpload(await file.arrayBuffer(),kind,id);if(seq!==fileSequence)return;importRecords.value=result.records;importErrors.value=result.errors;}catch(e){if(seq===fileSequence)importErrors.value=[`无法读取文件：${e.message||'请检查文件格式或加密状态。'}`];}finally{if(seq===fileSequence)importBusy.value=false;}
    }
    function onFileInput(e){loadFile(e.target.files?.[0]);}
    function onFileDrop(e){importDragging.value=false;loadFile(e.dataTransfer?.files?.[0]);}
    function confirmImport(){
      if(!canImport.value)return;const p=importTarget.value,key=importKind.value==='user'?'userRisks':'matrixRisks',now=timeNow();
      const records=clone(importRecords.value).map(r=>({...r,application:r.application||p.application}));
      const imported={...p.imported,[importKind.value]:{filename:importFilename.value,time:now,count:records.length}};
      const next={...p,[key]:records,imported,updatedAt:now};
      const type=importKind.value==='user'?'用户—角色':'权限矩阵';
      const logs=[{title:`导入${type}风险明细 · ${p.name}`,detail:`${importFilename.value}；${importExisting.value}条替换为${records.length}条。`,time:now},...activity.value];
      if(!commit(products.map(x=>x.id===p.id?next:x),logs))return;
      selectedProduct.value=p.id;detailKind.value=importKind.value;clearDetails();closeImport();navigate('details');toast(`已替换${p.name}的${records.length}条${type}风险明细。`);
    }
    async function download(sheets,name){
      try{const data=await makeWorkbook(sheets);const blob=new Blob([data],{type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=name;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),3000);toast('文件已生成，请查看浏览器下载。');}catch(e){toast(`文件生成失败：${e.message}`,true);}
    }
    const stamp=()=>timeNow().replace(/[-: ]/g,'');
    function downloadTemplate(kind){
      const columns=(kind==='user'?USER_COLUMNS:MATRIX_COLUMNS).filter(c=>c.key!=='workflow');
      const required=kind==='user'?'检查项、用户工号、权限名称、角色类型、授权ID、风险等级':'检查细项、角色名称、角色类型、权限集名称、权限点名称、权限点ID、权限细分类型、风险原因、置信度、判定依据';
      const notes=[['填写项','要求'],['用途','导入已完成审查的风险结果，不执行审查脚本。'],['工作表','在第一个“风险明细”工作表中填写，每行一条风险。不要修改表头。'],['必填字段',required],['编号',kind==='user'?'检查项为1—7。检查项名称可留空自动补齐。':'检查细项为8a—8f。'],['风险等级',kind==='user'?'填写高、中或低。':'默认高；可填写高、中或低。'],['工号与标识','按文本填写，保留前导0；不要输入Excel公式。'],['日期','启用时间、有效期按YYYY-MM-DD填写。'],['置信度','0—1的小数或百分数，例如0.95或95%。'],['去重',kind==='user'?'检查项与授权ID的组合不可重复。':'检查细项、角色、权限集、权限点ID的组合不可重复。'],['替换范围','仅替换上传时选择的产品和数据类型。'],['数据留存','当前为本地交互原型，导入结果保存在当前浏览器。']];
      download([{name:'风险明细',rows:[],columns},{name:'填写说明',rows:notes}],`财经权限审查_${kind==='user'?'用户角色':'权限矩阵'}_导入模板.xlsx`);
    }
    const exportNotes=()=>({name:'说明',rows:[['项目','说明'],['数据性质','交互原型演示。内置记录为虚构数据；本地导入数据沿用其上传内容。'],['规则版本',RULE_VERSION],['授权快照',SNAPSHOT],['统计口径','授权及用户量来自授权快照。总用户跨产品去重；云山用户、业务雇员、业务非雇员为互斥分类。'],['风险口径','按检查项命中记录计数；已确认误报从所有风险统计中剔除，原记录保留，可在明细中按处理状态筛选查看和导出。其他处理状态仍计入风险统计。'],['修改待复核','导出风险明细保留本轮审查字段；原始数据修改后为待复核，不自动消除风险。']]});
    function exportOverview(){
      const o=overall.value;const overview=[['指标','数值'],['授权记录',o.authorizations],['总用户数（去重）',o.users],['云山用户',o.it],['业务雇员',o.business],['业务非雇员',o.external],['风险记录',o.total],['高风险',o.high],['中风险',o.medium],['低风险',o.low]];
      const summaries=products.map(p=>({name:p.name,authorizations:p.authorizations,users:p.userCount,...stats(p)}));
      const col=[['name','产品',220],['authorizations','授权记录',110],['users','涉及用户',110],['total','风险记录',110],['high','高风险',110],['medium','中风险',110],['low','低风险',110]].map(([key,label,width])=>({key,label,width}));
      const addProduct=arr=>products.flatMap(p=>p[arr].filter(isCountedRisk).map(r=>({...r,product:p.name})));
      download([{name:'全域概况',rows:overview},{name:'产品审查结果',rows:summaries,columns:col},{name:'用户角色风险',rows:addProduct('userRisks'),columns:[{key:'product',label:'所属产品',width:220},...USER_COLUMNS]},{name:'权限矩阵风险',rows:addProduct('matrixRisks'),columns:[{key:'product',label:'所属产品',width:220},...MATRIX_COLUMNS]},exportNotes()],`财经权限审查报告_${stamp()}.xlsx`);
    }
    function exportProduct(){
      const p=currentProduct.value;const cols=[['id','检查项',90],['name','检查项名称',220],['content','检查内容',300],['result','检查结果',100],['count','风险记录数',100],['risk','风险等级',100]].map(([key,label,width])=>({key,label,width}));
      download([{name:'审查结果汇总',rows:ruleRows(p),columns:cols},{name:'用户角色风险',rows:p.userRisks.filter(isCountedRisk),columns:USER_COLUMNS},{name:'权限矩阵风险',rows:p.matrixRisks.filter(isCountedRisk),columns:MATRIX_COLUMNS},exportNotes()],`${p.name}_权限审查结果_${stamp()}.xlsx`);
    }
    function exportDetails(){const rows=filteredDetails.value.map(r=>({...r,product:productOf(r).name}));download([{name:'风险明细',rows,columns:[{key:'product',label:'所属产品',width:220},...detailColumns.value]},exportNotes()],`${selectedProduct.value==='all'?'全域':currentProduct.value.name}_${detailKind.value==='user'?'用户角色':'权限矩阵'}_风险明细_${stamp()}.xlsx`);}

    const searchOpen=ref(false),globalQuery=ref(''),searchInput=ref(null),searchEl=ref(null);
    const searchProducts=computed(()=>products.filter(p=>p.name.includes(globalQuery.value.trim())).slice(0,globalQuery.value?18:5));
    const searchRules=computed(()=>RULES.filter(r=>r.name.includes(globalQuery.value.trim())||r.id===globalQuery.value.trim()).slice(0,globalQuery.value?8:3));
    function openSearch(){globalQuery.value='';searchOpen.value=true;nextTick(()=>searchInput.value?.focus());}
    const anyModal=computed(()=>!!drawer.value||importOpen.value||searchOpen.value);let previousFocus;
    watch(anyModal,active=>{
      document.body.style.overflow=active?'hidden':'';
      if(active){previousFocus=document.activeElement;nextTick(()=>{const root=searchOpen.value?searchEl.value:importOpen.value?importEl.value:drawerEl.value;root?.querySelector('button,input,select')?.focus();});}
      else previousFocus?.focus?.();
    });
    function onKey(e){
      if((e.metaKey||e.ctrlKey)&&e.key.toLowerCase()==='k'){e.preventDefault();if(!drawer.value&&!importOpen.value)openSearch();}
      if(e.key==='Escape'){if(searchOpen.value)searchOpen.value=false;else if(importOpen.value)closeImport();else if(drawer.value)closeDrawer();else{activeHeader.value='';columnsOpen.value=false;mobileNav.value=false;}}
      if(e.key==='Tab'&&anyModal.value){const root=searchOpen.value?searchEl.value:importOpen.value?importEl.value:drawerEl.value;const all=root?.querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href]');if(!all?.length)return;const first=all[0],last=all[all.length-1];if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}}
    }
    onMounted(()=>{document.addEventListener('keydown',onKey);window.addEventListener('hashchange',parseRoute);if(restoreError)toast('未能恢复本地数据，当前显示内置演示。',true);});
    return {chartRiskRules,topRiskRules,topRuleShare,ruleDistribution,shareLabel,goDistributionRule,workflows:WORKFLOWS,isCountedRisk,excludedCount,statusEditing,statusDraft,statusReason,statusError,statusChanged,startStatusEdit,saveStatus,products,activity,page,selectedProduct,detailKind,mobileNav,productSearch,productFilter,productSort,onlyRiskRules,currentProduct,currentStats,overall,pageName,navigation,management,levelData,topProducts,topShare,filteredProducts,summaryRules,detailSearch,ruleFilter,riskFilter,workflowFilter,headerFilters,activeHeader,columnsOpen,detailPage,pageSize,pageCount,pagedDetails,detailAllRows,filteredDetails,detailCounts,detailColumns,visibleKeys,shownColumns,detailTableWidth,allColumnsVisible,anyDetailFilter,clearDetails,changeKind,toggleColumn,resetColumns,toggleAllColumns,navigate,openProduct,openRule,goAllDetails,fmt,confidence,levelClass,resultClass,stats,productOf,ruleForRecord,drawer,drawerEl,editing,draft,editReason,editError,rawColumns,rawRecord,hasChanges,openRecord,closeDrawer,startEdit,saveEdit,importOpen,importEl,importKind,importProduct,importFilename,importRecords,importErrors,importConfirmed,importBusy,importDragging,importFileKey,importTarget,importExisting,canImport,importPreviewColumns,resetImportFile,openImport,closeImport,onFileInput,onFileDrop,loadFile,confirmImport,downloadTemplate,exportOverview,exportProduct,exportDetails,searchOpen,globalQuery,searchInput,searchEl,searchProducts,searchRules,openSearch,toastMessage,toastError,anyModal,snapshot:SNAPSHOT,ruleVersion:RULE_VERSION,rules:RULES,matrixRules:MATRIX_RULES,levels:LEVELS};
  }
}).mount('#app');
