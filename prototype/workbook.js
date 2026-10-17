import {USER_COLUMNS,MATRIX_COLUMNS,validateImport} from './data.js';
export const excel=()=>import('./vendor/xlsx.mjs');
export async function readUpload(buffer,kind,productId){
  const X=await excel();
  const book=X.read(buffer,{type:'array',cellNF:true,cellDates:false});
  const sheet=book.Sheets[book.SheetNames[0]];
  if(!sheet)throw new Error('未找到可读取的工作表。');
  const byRow=new Map();
  for(const addr of Object.keys(sheet)){
    if(addr.startsWith('!'))continue;
    const cell=sheet[addr];if(cell.v==null||String(cell.v).trim()==='')continue;
    const pos=X.utils.decode_cell(addr);
    if(!byRow.has(pos.r))byRow.set(pos.r,new Map());
    let value=X.utils.format_cell(cell);
    if(cell.t==='n'&&cell.z&&X.SSF.is_date(cell.z)){
      const date=X.SSF.parse_date_code(cell.v);
      if(date)value=`${date.y}-${String(date.m).padStart(2,'0')}-${String(date.d).padStart(2,'0')}`;
    }
    byRow.get(pos.r).set(pos.c,String(value).trim());
    if(byRow.size>20001)throw new Error('单次最多导入20,000条非空数据行。');
  }
  const order=[...byRow.keys()].sort((a,b)=>a-b);
  if(!order.length)return {records:[],errors:['工作表为空，请按模板填写后上传。']};
  const headers=byRow.get(order[0]);
  const labels=[...headers.values()];
  if(new Set(labels).size!==labels.length)return {records:[],errors:['表头存在重复列名，请使用标准模板。']};
  const cols=kind==='user'?USER_COLUMNS:MATRIX_COLUMNS;
  const required=kind==='user'?['checkItem','employeeId','permissionName','roleType','authorizationId','risk']:['subcheck','roleName','roleType','setName','pointName','pointId','subtype','violation','confidence','evidence'];
  const missing=cols.filter(c=>required.includes(c.key)&&!labels.includes(c.label));
  if(missing.length)return {records:[],errors:[`缺少必填列：${missing.map(c=>c.label).join('、')}。请使用对应类型的模板。`]};
  const rows=order.slice(1).map(n=>Object.fromEntries([...headers].map(([c,label])=>[label,byRow.get(n).get(c)||''])));
  return validateImport(rows,kind,productId);
}
export async function makeWorkbook(sheets){
  const X=await excel();const book=X.utils.book_new();
  sheets.forEach(({name,rows,columns})=>{
    const data=columns?[columns.map(c=>c.label),...rows.map(r=>columns.map(c=>r[c.key]??''))]:rows;
    const sheet=X.utils.aoa_to_sheet(data);
    sheet['!cols']=(columns||data[0]||[]).map(c=>({wch:typeof c==='object'?Math.min(44,Math.max(15,c.width/6)):26}));
    sheet['!autofilter']={ref:sheet['!ref']||'A1'};
    X.utils.book_append_sheet(book,sheet,name);
  });
  return X.write(book,{bookType:'xlsx',type:'array',compression:true});
}
