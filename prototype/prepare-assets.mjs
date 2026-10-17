import fs from 'node:fs/promises';
import {createHash} from 'node:crypto';
const root=new URL('./',import.meta.url);
const hashes=JSON.parse(await fs.readFile(new URL('asset-checksums.json',root),'utf8'));
const sources={'vue.esm-browser.prod.js':new URL('../frontend/vendor/vue.esm-browser.prod.js',root),'xlsx.mjs':new URL('node_modules/xlsx/xlsx.mjs',root)};
await fs.mkdir(new URL('vendor/',root),{recursive:true});
for(const [name,source] of Object.entries(sources)){
 const data=await fs.readFile(source);
 if(createHash('sha256').update(data).digest('hex')!==hashes[name])throw new Error('Dependency SHA-256 mismatch: '+name);
 await fs.writeFile(new URL('vendor/'+name,root),data);
}
console.log('Prototype assets restored; both SHA-256 values match the published prototype.');
