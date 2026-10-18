const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync('index.html', 'utf8');
for (const [, code] of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)) new vm.Script(code);
const source = html.slice(html.indexOf('    function isRouletteRetry('), html.indexOf('    // 🎮 기본은'));
const clone = v => v === undefined ? undefined : structuredClone(v);
let docs, serial, fail, completed;
function ref(path) { return { path, id: path.split('/')[1], get: async () => snap(path) }; }
function snap(path) { const data=clone(docs.get(path)); return {exists:!!data,id:path.split('/')[1],ref:ref(path),data:()=>data}; }
function collection(name, filter=()=>true, sort=null) {
 return { doc:(id)=>ref(`${name}/${id||`auto-${++serial}`}`),
 where:(field,op,value)=>collection(name,d=>filter(d)&&d[field]===value,sort),
 orderBy:field=>collection(name,filter,field),
 get:async()=>{let rows=[...docs.keys()].filter(p=>p.startsWith(name+'/')&&filter(docs.get(p))).map(snap);if(sort)rows.sort((a,b)=>a.data()[sort]-b.data()[sort]);return {docs:rows,forEach:cb=>rows.forEach(cb)};} };
}
const db={collection,runTransaction:async callback=>{
 const writes=[];await callback({get:r=>r.get(),set:(r,d)=>writes.push([r.path,d,false]),update:(r,d)=>writes.push([r.path,d,true])});
 if(fail)throw Error('network failure');for(const [p,d,merge]of writes)docs.set(p,merge?{...docs.get(p),...clone(d)}:clone(d));
}};
const ctx=vm.createContext({db,ensureAuth:async()=>{},getAppData:async()=>({}),grantRewardIfEligible:async round=>completed.push(round),console});
vm.runInContext(source,ctx);
function reset(){docs=new Map();serial=0;fail=false;completed=[];docs.set('boards/25',{round:25,status:'진행중',targetScore:30,luckyBoxes:[{step:5,text:'["A"]'}]});}
async function used(id='s'){docs.set('items/'+id,{round:24,name:'칭찬스티커 3개 찍어주기',type:'랜덤박스',status:'사용 전'});await ctx.useItemSplit(id,'아들',1,3);return [...docs.keys()].find(p=>p.startsWith('notifications/')&&docs.get(p).itemId===id).split('/')[1];}
(async()=>{
 reset();let n=await used();assert(!docs.has('logs/sticker-reward-s'));assert.equal(docs.get('items/s').name,'칭찬스티커 3개 찍어주기');
 await assert.rejects(()=>ctx.acknowledgeNotification(n,'child'));
 fail=true;await assert.rejects(()=>ctx.acknowledgeNotification(n,'parent'));assert.equal(docs.get('notifications/'+n).status,'대기 중');assert(!docs.has('logs/sticker-reward-s'));fail=false;
 docs.set('logs/before',{round:25,score:3});await ctx.acknowledgeNotification(n,'parent');assert.equal(docs.get('logs/sticker-reward-s').score,3);assert.equal(docs.get('logs/sticker-reward-s').round,25);assert(docs.has('items/sticker-reward-s-lucky-5'));assert.deepEqual(completed,[25]);
 const size=docs.size;await ctx.acknowledgeNotification(n,'parent');assert.equal(docs.size,size);
 docs.set('notifications/duplicate',{...docs.get('notifications/'+n),status:'대기 중'});docs.delete('logs/sticker-reward-s');await ctx.acknowledgeNotification('duplicate','parent');assert(!docs.has('logs/sticker-reward-s'));
 reset();n=await used();delete docs.get('notifications/'+n).itemId;await ctx.acknowledgeNotification(n,'parent');assert.equal(docs.get('logs/sticker-reward-s').score,3);
 reset();n=await used();docs.get('boards/25').status='보상 수령 완료';await assert.rejects(()=>ctx.acknowledgeNotification(n,'parent'));assert.equal(docs.get('notifications/'+n).status,'대기 중');
 reset();n=await used();docs.set('notifications/later',{status:'대기 중',kind:'roulette',text:'칭찬스티커 3장'});await ctx.dismissAllNotifications('parent',[n]);assert.equal(docs.get('notifications/later').status,'대기 중');await ctx.acknowledgeNotification('later','parent');assert.equal([...docs.keys()].filter(p=>p.startsWith('logs/')).length,1);
 reset();n=await used();delete docs.get('notifications/'+n).itemId;docs.set('items/ambiguous',clone(docs.get('items/s')));await assert.rejects(()=>ctx.acknowledgeNotification(n,'parent'));assert(!docs.has('logs/sticker-reward-s'));
 console.log('PASS: web syntax and sticker approval regressions; no production writes');
})().catch(e=>{console.error(e);process.exitCode=1;});
