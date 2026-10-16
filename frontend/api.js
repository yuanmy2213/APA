export class ReviewApi {
  auth=''; csrf=null;
  async request(path,options={}) {
    const headers=new Headers(options.headers||{});
    if(this.auth)headers.set('Authorization',this.auth);
    if(options.body && !(options.body instanceof FormData) && typeof options.body!=='string') {headers.set('Content-Type','application/json');options={...options,body:JSON.stringify(options.body)};}
    if(!['GET','HEAD'].includes(options.method||'GET')&&this.csrf)headers.set(this.csrf.headerName,this.csrf.token);
    const response=await fetch('/api'+path,{...options,headers,credentials:'same-origin',cache:'no-store'});
    if(!response.ok){let e;try{e=await response.json();}catch{e={message:response.status===404?'请启动 Java 运行版后访问工作台。':'服务暂时不可用，请稍后重试。'};}const error=new Error(e.message||`请求失败 (${response.status})`);error.status=response.status;error.code=e.code;throw error;}
    if(options.download)return response.blob();
    if(response.status===204)return null;return response.json();
  }
  async login(name,password){this.auth='Basic '+btoa(unescape(encodeURIComponent(name+':'+password)));try{const me=await this.request('/me');this.csrf=await this.request('/csrf');return me;}catch(e){this.auth='';this.csrf=null;throw e;}}
  logout(){this.auth='';this.csrf=null;}
}
export const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
export function saveBlob(blob,name){const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1500);}
