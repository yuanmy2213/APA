export const SNAPSHOT = '2026-10-16 18:30';
export const RULE_VERSION = '1.0.19';
export const WORKFLOWS = ['待核实','待人工确认','待复核','已确认风险','整改中','已整改','已确认误报'];
export const isCountedRisk = r => r.workflow !== '已确认误报';
export const LEVELS = ['高', '中', '低'];
export const RULES = [
  {id:'1', name:'IT人员不持有业务权限', content:'核查云山数字化部及下属组织人员，是否持有业务操作、业务查看或业务管理权限。', risk:'高', kind:'user', advice:'核实业务必要性，撤回不符合职责的业务权限，按岗位重新授权。', basis:'部门编码匹配云山数字化部体系；角色类型为业务权限。'},
  {id:'2', name:'业务人员不应持有IT权限', content:'核查非云山数字化部体系人员，是否持有系统管理或系统查询权限。', risk:'高', kind:'user', advice:'撤回系统管理或系统查询权限，按业务职责重新申请权限。', basis:'部门不属于云山数字化部体系；角色类型为系统管理或系统查询权限。'},
  {id:'3', name:'业务管理员不能有“修改业务数据”权限', content:'核查业务管理员是否具有修改、编辑或审批业务数据的能力；配置、审批配置能力除外。', risk:'高', kind:'user', advice:'通过权限点复核业务数据修改能力，将业务配置与业务操作职责分离。', basis:'功能描述涉及修改或审批业务数据；仅配置、审批配置不构成风险。', tentative:true},
  {id:'4', name:'业务管理员不能有“用户授权”权限', content:'核查业务管理员是否持有用户授权、分配权限等能力。', risk:'高', kind:'user', advice:'通过权限点复核授权能力，将用户授权从业务管理角色中分离。', basis:'功能描述或权限集描述包含用户授权、分配权限能力。', tentative:true},
  {id:'5', name:'业务非雇员不允许有系统权限', content:'识别业务非雇员，核查其持有的业务操作、业务查看或业务管理权限。', risk:'高', kind:'user', advice:'核实人员身份与权限必要性，清理业务非雇员不应持有的业务权限。', basis:'工号数字部分不以00开头；单字母加5至6位数字旧工号先补00判断。'},
  {id:'6', name:'IT特权账号遵循“一事一授权”原则，且不长期持有。', content:'核查IT特权账号是否按单项事项申请和审批授权，以及是否长期持有权限；授权有效期不得超过365天。', risk:'中', kind:'user', advice:'缩短授权有效期，补充一事一授权的申请与审批记录。', basis:'授权有效期超过365天。一事一授权需进一步核实申请及审批凭证。'},
  {id:'7', name:'IT运维查询账号按应用申请、按岗位授权、按流程授权', content:'核查运维查询账号是否按应用申请、按岗位及流程授权，缺少凭证时需人工确认。', risk:'低', kind:'user', advice:'补充应用、岗位与审批流程的关联凭证，由责任人确认授权合理性。', basis:'授权记录缺少可核实的岗位或流程关联凭证；需人工确认。', manual:true},
  {id:'8', name:'权限矩阵明细检查', content:'核查角色是否关联超出职责范围的权限集或权限点，覆盖8a—8f六条检查细则。', risk:'高', kind:'matrix', advice:'核实权限点实际能力，将冲突权限点从角色中解绑或拆分角色。', basis:'角色类型与权限细分类型冲突；需结合权限点语义核实。'}
];
export const MATRIX_RULES = [
  {id:'8a', name:'“系统管理权限”不允许绑定业务操作、业务管控权限点', role:'系统管理权限', forbidden:['业务查看','业务操作','业务审批','业务转审','业务管控']},
  {id:'8b', name:'“系统查询权限”角色不允许绑定业务操作、IT运维特权权限点', role:'系统查询权限', forbidden:['业务操作','业务审批','业务转审','业务管控','IT运维特权']},
  {id:'8c', name:'“业务管理权限”角色不允许绑定业务操作、IT运维特权权限点', role:'业务管理权限', forbidden:['业务操作','业务审批','业务转审','IT运维特权']},
  {id:'8d', name:'“业务查看权限”角色不允许绑定业务操作、IT运维特权、业务管控权限点', role:'业务查看权限', forbidden:['业务操作','业务审批','业务转审','IT运维特权','业务管控']},
  {id:'8e', name:'“业务操作权限”角色不允许绑定IT运维特权、业务管控权限点', role:'业务操作权限', forbidden:['IT配置','业务管控']},
  {id:'8f', name:'“审计权限”角色不允许绑定业务操作、IT运维特权权限点', role:'审计权限', forbidden:['业务操作','业务审批','业务转审','IT运维特权','业务管控']}
];
export const USER_COLUMNS = [
  ['checkItem','检查项',90],['checkName','检查项名称',360],['employeeId','用户工号',160],['userName','用户名称',110],['department','用户部门',205],['permissionName','权限名称',190],['roleType','角色类型',145],['permissionType','权限类型',135],['description','功能描述',260],['startAt','启用时间',135],['expiresAt','有效期',135],['authorizationId','授权ID',185],['risk','风险等级',105],['application','所属应用',165],['violation','风险明细说明',295],['advice','整改建议',295],['workflow','处理状态',130]
].map(([key,label,width])=>({key,label,width}));
export const MATRIX_COLUMNS = [
  ['subcheck','检查细项',110],['roleName','角色名称',180],['roleType','角色类型',145],['setName','权限集名称',180],['pointName','权限点名称',190],['pointId','权限点ID',180],['resourceType','资源类型',135],['subtype','权限细分类型',145],['violation','风险原因',280],['confidence','置信度',110],['evidence','判定依据',310],['risk','风险等级',105],['workflow','处理状态',130]
].map(([key,label,width])=>({key,label,width}));
export const DEFAULT_USER_COLUMNS = ['checkItem','employeeId','userName','department','permissionName','roleType','risk','workflow','violation'];
export const DEFAULT_MATRIX_COLUMNS = ['subcheck','roleName','roleType','pointName','subtype','violation','confidence','workflow'];
export const PRODUCT_DEFINITIONS = [
  ['财经前台产品','layout',18360,4080,62,'财经门户'],
  ['账务交易产品','receipt',16480,3760,104,'账务交易平台'],
  ['资金产品','wallet',14120,3120,88,'资金管理平台'],
  ['税务产品','file',11760,2460,63,'税务管理平台'],
  ['财报产品','chart',10340,2240,49,'财务报告平台'],
  ['财经法务产品','scale',9860,1980,96,'财经法务平台'],
  ['交易经营产品','briefcase',8510,1760,51,'交易经营平台'],
  ['资金收付安全产品','shield',7940,1620,44,'资金安全平台'],
  ['支付清算产品','arrows',6920,1470,36,'支付清算平台'],
  ['财经预算预测产品','trend',5180,1300,28,'预算预测平台'],
  ['财经辅助','grid',4560,1180,18,'财经辅助平台'],
  ['财经质量管理产品','check',3210,1030,17,'财经质量平台'],
  ['财经助手产品','spark',2840,970,12,'财经助手'],
  ['电商支付风控产品','lock',2460,860,26,'电商支付风控'],
  ['管报数据产品','database',2280,720,14,'管报数据平台'],
  ['财经分析产品','pie',1860,610,0,'财经分析平台'],
  ['业财数据产品','layers',1480,480,0,'业财数据平台'],
  ['财经公共产品','globe',960,320,0,'财经公共平台']
];
const names=['林川','陈屿','周宁','李然','徐清','沈言','许知','方予','王舒','赵禾','孙越','吴明','郑远','冯珂','程安','梁文'];
export function person(index){
  const bucket=index%100;
  const category=bucket<15?'it':bucket<94?'business':'external';
  return {index, category, employeeId:`DEMO${category==='external'?'93':'00'}${String(index+1).padStart(6,'0')}`,userName:names[index%names.length],department:category==='it'?'云山数字化部 / 财经应用架构组（076739）':category==='external'?'财经运营支持组（演示）':'集团财经 / 财务运营部（演示）'};
}
export function makeDemo(){
  let cursor=0;
  return PRODUCT_DEFINITIONS.map(([name,icon,authorizations,userCount,riskCount,application],p)=>{
    const members=Array.from({length:userCount},(_,i)=>person((cursor+i)%18642));
    cursor+=userCount;
    const it=members.filter(x=>x.category==='it'),business=members.filter(x=>x.category==='business'),external=members.filter(x=>x.category==='external');
    const id=`P${String(p+1).padStart(2,'0')}`;
    const userN=Math.round(riskCount*.7);
    const cycle=[1,2,3,4,5,1,2,3,4,5,6,7];
    const userRisks=Array.from({length:userN},(_,i)=>{
      const rid=String(cycle[i%cycle.length]);const rule=RULES.find(x=>x.id===rid);
      const pool=['1','6','7'].includes(rid)?it:rid==='5'?external:business;
      const u=pool[(i*7)%pool.length];
      const roleType=rid==='1'?'业务操作权限':rid==='2'?'系统管理权限':['3','4'].includes(rid)?'业务管理权限':rid==='5'?'业务查看权限':rid==='6'?'系统管理权限':'系统查询权限';
      const descriptions={'1':'录入、修改业务单据并提交审批','2':'管理系统配置、维护系统参数','3':'编辑与审批业务数据','4':'用户授权、分配角色权限','5':'查看业务报表及明细','6':'系统配置、数据修复及运维管理','7':'应用日志与运行状态查询'};
      const violation={'1':'IT人员持有业务操作权限，与人员职责不符。','2':'业务人员持有系统管理权限，与人员职责不符。','3':'功能描述涉及编辑与审批业务数据，需通过权限点复核。','4':'功能描述涉及用户授权与角色分配，需通过权限点复核。','5':'业务非雇员持有业务查看权限，需核实并清理。','6':'当前授权有效期为1095天，超过365天。','7':'未提供岗位及流程关联凭证，需人工确认。'};
      return {recordId:`${id}-U${i+1}`,kind:'user',checkItem:rid,checkName:rule.name,employeeId:u.employeeId,userName:u.userName,department:u.department,permissionName:rid==='2'?'System Admin':rid==='6'?'IT运维特权':rid==='7'?'IT运维查询':`${name.replace('产品','')}·${roleType.replace('权限','')}`,roleType,permissionType:roleType.startsWith('系统')?'IT权限':'业务权限',description:descriptions[rid],startAt:rid==='6'?'2025-10-16':'2026-01-16',expiresAt:rid==='6'?'2028-10-15':'2027-01-15',authorizationId:`DEMO-AUTH-${id}-${String(i+1).padStart(4,'0')}`,risk:rule.risk,application,violation:violation[rid],advice:rule.advice,workflow:rid==='7'?'待人工确认':'待核实',evidence:rule.basis};
    });
    const matrixRisks=Array.from({length:riskCount-userN},(_,i)=>{
      const m=MATRIX_RULES[i%6];const subtype=m.forbidden[i%m.forbidden.length];
      const pointName=({业务查看:'业务明细查询',业务操作:'业务单据编辑',业务审批:'业务单据审批',业务转审:'业务单据转审',业务管控:'业务管控策略维护',IT运维特权:'生产数据修复',IT配置:'系统参数配置'})[subtype];
      return {recordId:`${id}-M${i+1}`,kind:'matrix',subcheck:m.id,roleName:`${name.replace('产品','')}·${m.role.replace('权限','')}`,roleType:m.role,setName:`${subtype}权限集`,pointName,pointId:`DEMO-PERM-${id}-${String(i+1).padStart(4,'0')}`,resourceType:i%2?'接口':'功能菜单',subtype,violation:`${m.role}角色绑定了${subtype}能力，超出该角色职责范围。`,confidence:(.82+(i%17)/100).toFixed(2),evidence:`细则${m.id}：${m.role}不允许绑定${m.forbidden.join('、')}。权限点“${pointName}”标注为${subtype}。`,risk:'高',workflow:'待核实',application,advice:RULES[7].advice};
    });
    return {id,name,icon,authorizations,userCount,application,members,userRisks,matrixRisks,updatedAt:SNAPSHOT,imported:{user:null,matrix:null}};
  });
}
export const riskRows=p=>[...p.userRisks,...p.matrixRisks];
export const activeRiskRows=p=>riskRows(p).filter(isCountedRisk);
export function stats(p){
  const rows=activeRiskRows(p);return {total:rows.length,high:rows.filter(x=>x.risk==='高').length,medium:rows.filter(x=>x.risk==='中').length,low:rows.filter(x=>x.risk==='低').length,manual:rows.filter(x=>x.workflow==='待人工确认').length,recheck:rows.filter(x=>x.workflow==='待复核').length};
}
export function globalStats(products){
  const users=new Map();products.forEach(p=>p.members.forEach(u=>users.set(u.employeeId,u)));
  const records=products.flatMap(activeRiskRows);
  const people=[...users.values()];
  return {authorizations:products.reduce((n,p)=>n+p.authorizations,0),total:records.length,high:records.filter(x=>x.risk==='高').length,medium:records.filter(x=>x.risk==='中').length,low:records.filter(x=>x.risk==='低').length,users:users.size,it:people.filter(x=>x.category==='it').length,business:people.filter(x=>x.category==='business').length,external:people.filter(x=>x.category==='external').length,affected:products.filter(p=>activeRiskRows(p).length).length,recheck:records.filter(x=>x.workflow==='待复核').length};
}
export function ruleRows(p){
  return RULES.map(rule=>{
    const all=rule.kind==='matrix'?p.matrixRisks:p.userRisks.filter(r=>r.checkItem===rule.id);
    const rows=all.filter(isCountedRisk);
    const noRecordResult=all.length?'无待统计风险':p.imported?.[rule.kind]?'未检出记录':'通过';
    return {...rule,count:rows.length,result:!rows.length?noRecordResult:rule.manual?'需人工确认':rule.tentative?'存在风险':'风险'};
  });
}
export function applyFilters(rows,{q='',risk='',rule='',workflow='',headers={}}={}){
  const needle=q.trim().toLowerCase();
  return rows.filter(r=>(!needle||Object.values(r).some(v=>String(v).toLowerCase().includes(needle)))&&(!risk||r.risk===risk)&&(!rule||(r.kind==='matrix'?rule==='8'||r.subcheck===rule:r.checkItem===rule))&&(!workflow||r.workflow===workflow)&&Object.entries(headers).every(([k,v])=>!v||String(r[k]??'').toLowerCase().includes(v.toLowerCase())));
}
export function normalizeLevel(v){return String(v??'').trim().replace('风险','');}
export function validateImport(rows,kind,productId){
  const columns=(kind==='user'?USER_COLUMNS:MATRIX_COLUMNS).filter(c=>c.key!=='workflow');
  const required=kind==='user'?['checkItem','employeeId','permissionName','roleType','authorizationId','risk']:['subcheck','roleName','roleType','setName','pointName','pointId','subtype','violation','confidence','evidence'];
  const errors=[],keys=new Set(),output=[];
  if(!rows.length)return {records:[],errors:['文件中没有数据行。请使用下载的模板填写后导入。']};
  if(rows.length>20000)return {records:[],errors:['单次最多导入20,000条风险明细，请缩小导入文件。']};
  rows.forEach((row,i)=>{
    const r={};columns.forEach(c=>r[c.key]=String(row[c.label]??'').trim());
    for(const key of required)if(!r[key])errors.push(`第${i+2}行：${columns.find(c=>c.key===key).label}不能为空。`);
    r.risk=normalizeLevel(r.risk||(kind==='matrix'?'高':''));
    if(!LEVELS.includes(r.risk))errors.push(`第${i+2}行：风险等级须为高、中或低。`);
    if(kind==='user'){
      r.checkItem=r.checkItem.replace(/^检查项\s*/,'');
      const rule=RULES.find(x=>x.id===r.checkItem&&x.kind==='user');
      if(!rule)errors.push(`第${i+2}行：用户—角色的检查项须为1至7。`);
      r.checkName=rule?.name||'';
      for(const k of ['startAt','expiresAt'])if(r[k]){
        const match=/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$/.exec(r[k]);
        const valid=match&&new Date(Date.UTC(+match[1],+match[2]-1,+match[3]));
        if(!valid||valid.getUTCFullYear()!==+match[1]||valid.getUTCMonth()!==+match[2]-1||valid.getUTCDate()!==+match[3])errors.push(`第${i+2}行：${columns.find(c=>c.key===k).label}须为有效日期，如2026-10-16。`);
        else r[k]=`${match[1]}-${match[2].padStart(2,'0')}-${match[3].padStart(2,'0')}`;
      }
      if(r.startAt&&r.expiresAt&&r.expiresAt<r.startAt)errors.push(`第${i+2}行：有效期不能早于启用时间。`);
      r.evidence=rule?.basis||'';
      r.workflow=r.checkItem==='7'?'待人工确认':'待核实';
    }else{
      r.subcheck=r.subcheck.toLowerCase().replace(/^检查项\s*/,'');
      if(!MATRIX_RULES.some(x=>x.id===r.subcheck))errors.push(`第${i+2}行：检查细项须为8a至8f。`);
      let c=Number(r.confidence.replace('%',''));if(r.confidence.endsWith('%'))c/=100;
      if(!Number.isFinite(c)||c<0||c>1)errors.push(`第${i+2}行：置信度须为0—1的小数或百分数。`);
      r.confidence=Number.isFinite(c)?c.toFixed(2):r.confidence;r.workflow='待核实';r.advice=RULES[7].advice;
    }
    const unique=kind==='user'?`${r.checkItem}|${r.authorizationId}`:`${r.subcheck}|${r.roleName}|${r.setName}|${r.pointId}`;
    if(keys.has(unique))errors.push(`第${i+2}行：存在重复的风险记录。`);keys.add(unique);
    output.push({...r,recordId:`${productId}-${kind}-${i+1}`,kind});
  });
  return {records:output,errors};
}
