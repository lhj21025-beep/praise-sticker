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
 reset();
 const game = {round:25,name:'게임 60분 (이월)',type:'보상',status:'사용 전',obtainedDate:'2026-11-15T00:00:00Z',gameRollover:true,gameSettlementDate:'2026-11-15',gameMinutes:60};
 docs.set('items/game',clone(game));
 await assert.rejects(()=>ctx.parentUseGame('game',20,'child'));
 for(const minutes of [0,-1,61,1.5,NaN]) await assert.rejects(()=>ctx.parentUseGame('game',minutes,'parent'));
 assert.equal(docs.get('items/game').status,'사용 전');
 fail=true;await assert.rejects(()=>ctx.parentUseGame('game',20,'parent'));fail=false;
 assert.equal(docs.size,2);
 await ctx.parentUseGame('game',20,'parent');
 assert.equal(docs.get('items/game').name,'게임 20분 (이월)');
 assert.equal(docs.get('items/game').parentUseNoticeStatus,'대기 중');
 assert.equal(docs.get('items/game').usedByRole,'parent');
 const remainder=docs.get('items/parent-remainder-game');
 assert.equal(remainder.gameMinutes,40);
 assert.equal(remainder.gameSettlementDate,game.gameSettlementDate);
 assert.equal(remainder.obtainedDate,game.obtainedDate);
 assert.equal(remainder.parentUseNoticeStatus,undefined);
 await assert.rejects(()=>ctx.parentUseGame('game',20,'parent'));
 assert.equal(docs.get('items/parent-remainder-game').gameMinutes,40);
 await assert.rejects(()=>ctx.dismissChildGameNotices('parent',['game']));
 docs.set('items/later',{...game,usedByRole:'parent',parentUseNoticeStatus:'대기 중'});
 fail=true;await assert.rejects(()=>ctx.dismissChildGameNotices('child',['game']));fail=false;
 assert.equal(docs.get('items/game').parentUseNoticeStatus,'대기 중');
 await ctx.dismissChildGameNotices('child',['game','game']);
 assert.equal(docs.get('items/game').parentUseNoticeStatus,'확인함');
 assert.equal(docs.get('items/later').parentUseNoticeStatus,'대기 중');
 await ctx.parentUseGame('parent-remainder-game',40,'parent');
 assert.equal(docs.get('items/parent-remainder-game').parentUseNoticeStatus,'대기 중');
 assert(!docs.has('items/parent-remainder-parent-remainder-game'));
 docs.set('items/stickers',{...game,name:'칭찬스티커 3장'});
 await assert.rejects(()=>ctx.parentUseGame('stickers',1,'parent'));
 assert.equal(docs.get('items/stickers').status,'사용 전');
 // Child wins a race: parent's stale action must not subtract again.
 docs.set('items/child-first',{...game,status:'사용 후'});
 await assert.rejects(()=>ctx.parentUseGame('child-first',20,'parent'));
 assert.equal(docs.get('items/child-first').parentUseNoticeStatus,undefined);
 console.log('PASS: parent game redemption and child acknowledgement regressions; no production writes');
})().catch(e=>{console.error(e);process.exitCode=1;});
