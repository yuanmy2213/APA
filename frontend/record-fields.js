// Keep keystroke-driven rendering inside the editor, independent of the large results table.
export const RecordFields={
 props:{columns:Array,editing:Boolean,draft:Object,record:Object,workflows:Array},
 data:()=>({roleTypes:['系统管理权限','系统查询权限','业务管理权限','业务查看权限','业务操作权限','审计权限']}),
 methods:{locked(key){return ['authorizationId','permissionCombinationId'].includes(key)&&!!this.record?.[key];}},
 template:`<div class="raw-grid">
  <div v-for="c in columns" :key="c.key" :class="['raw-field',{wide:['description','department','evidence'].includes(c.key)}]">
   <label :for="'raw-'+c.key">{{c.label}}</label>
   <template v-if="editing&&!locked(c.key)">
    <select v-if="['risk','workflow'].includes(c.key)" :id="'raw-'+c.key" v-model="draft[c.key]" class="form-input"><option v-for="v in c.key==='risk'?['高','中','低']:workflows">{{v}}</option></select>
    <select v-else-if="c.key==='roleType'" :id="'raw-'+c.key" v-model="draft[c.key]" class="form-input"><option v-for="r in roleTypes">{{r}}</option><option v-if="!roleTypes.includes(draft[c.key])">{{draft[c.key]}}</option></select>
    <input v-else :id="'raw-'+c.key" v-model="draft[c.key]" :type="['startAt','expiresAt'].includes(c.key)?'date':'text'" class="form-input">
   </template>
   <template v-else><p :id="'raw-'+c.key" :class="{'mono':/Id$/.test(c.key)}">{{(editing?draft:record)?.[c.key]||'—'}}</p><small v-if="editing&&locked(c.key)" class="muted">关联标识保持不变</small></template>
  </div>
 </div>`
};
