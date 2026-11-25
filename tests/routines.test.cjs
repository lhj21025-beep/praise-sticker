const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync('index.html','utf8');
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

const ctx=vm.createContext({db,ensureAuth:async()=>{},currentRole:'parent',app:{userName:'엄빠'},validStickerDate:d=>d,stickerToday:()=>new Date(Date.now()+9*3600000).toISOString().slice(0,10),grantRewardIfEligible:async r=>completed.push(r),isBoardCompletionReward:i=>i.rewardSource==='board-completion'||i.type==='보상',console});
vm.runInContext(html.slice(html.indexOf('    const ROUTINE_NAMES'),html.indexOf('    let routineShown')),ctx);
function reset(){docs=new Map();serial=0;fail=false;completed=[];docs.set('meta/state',{routineStartDate:'2026-11-01',lifetimeScore:0,praiseReasons:[{name:'기상시간 준수',count:5}]});docs.set('boards/25',{round:25,status:'진행중',targetScore:30,luckyBoxes:[{step:5,text:'prizes'}]});}
(async()=>{
 assert.equal(ctx.dueRoutines('2026-11-23',new Date('2026-11-23T06:59:00+09:00'),new Set()).length,0);
 assert.equal(ctx.dueRoutines('2026-11-23',new Date('2026-11-23T23:00:00+09:00'),new Set()).length,6);
 assert.equal(ctx.dueRoutines('2026-11-23',new Date('2026-11-24T07:00:00+09:00'),new Set(['routine-2026-11-23-2'])).length,6);
 assert.equal(ctx.dueRoutines('2026-11-24',new Date('2026-11-24T21:59:00+09:00'),new Set()).length,5);
 assert.equal(ctx.dueRoutines('2026-11-24',new Date('2026-11-24T22:00:00+09:00'),new Set()).length,7);
 assert.equal(ctx.dueRoutines('2026-11-24',new Date('2026-11-24T22:00:00+09:00'),new Set(['routine-2026-11-24-5'])).some(x=>x.slot===6),true);
 reset();fail=true;await assert.rejects(()=>ctx.decideRoutine('2026-11-02',0,true));assert.equal(docs.size,2);fail=false;
 await ctx.decideRoutine('2026-11-02',0,true);
 assert.equal(docs.get('logs/routine-2026-11-02-0').score,5);
 assert.equal(docs.get('logs/routine-2026-11-02-0').effectiveDate,'2026-11-02');
 assert.equal(docs.get('logs/routine-2026-11-02-0-bonus-0').score,1);
 assert.equal(docs.get('items/routine-2026-11-02-0-lucky-5').prizesSnapshot,'prizes');
 const n=docs.size;await ctx.decideRoutine('2026-11-02',0,true);await ctx.decideRoutine('2026-11-02',0,false);
 assert.equal(docs.size,n);assert.equal(docs.get('meta/state').lifetimeScore,5);
 assert.equal(docs.get('meta/routine-2026-11-02-0').routineStatus,'approved');
 await ctx.decideRoutine('2026-11-02',1,false);await ctx.decideRoutine('2026-11-02',1,true);
 assert.equal(docs.has('logs/routine-2026-11-02-1'),false);
 ctx.currentRole='child';await assert.rejects(()=>ctx.decideRoutine('2026-11-02',2,true));
 ctx.currentRole='parent';await assert.rejects(()=>ctx.decideRoutine('2999-01-01',2,true));
 reset();docs.set('meta/state',{routineStartDate:'2026-11-01',lifetimeScore:28,praiseReasons:[]});
 docs.set('logs/base',{round:25,score:24,executor:'시스템',date:'2026-11-01T00:00:00Z',effectiveDate:'2026-11-01'});
 docs.set('logs/streak',{round:25,score:4,executor:'엄빠',date:'2026-11-01T01:00:00Z',effectiveDate:'2026-11-01'});
 const total=r=>[...docs.entries()].filter(([p,d])=>p.startsWith('logs/')&&d.round===r).reduce((n,[p,d])=>n+d.score,0);
 await ctx.decideRoutine('2026-11-02',0,true);assert.equal(total(25),30);
 await ctx.decideRoutine('2026-11-02',1,true);await ctx.decideRoutine('2026-11-02',2,true);
 assert.equal(total(25),30);assert.equal(total(26),2);assert.equal(docs.get('boards/26').targetScore,30);
 assert.equal(docs.get('boards/26').reward,docs.get('boards/25').reward);
 assert.equal(docs.get('meta/state').lifetimeScore,31);
 await ctx.decideRoutine('2026-11-02',2,true);assert.equal(total(26),2);
 reset();docs.set('logs/old',{round:25,score:95,executor:'엄빠',date:'2026-11-01T00:00:00Z',effectiveDate:'2026-11-01'});docs.get('meta/state').lifetimeScore=95;
 fail=true;await assert.rejects(()=>ctx.splitOverflow(25));fail=false;assert.equal(total(25),95);
 await ctx.splitOverflow(25);assert.deepEqual([25,26,27,28].map(total),[30,30,30,5]);assert.equal(docs.get('meta/state').lifetimeScore,95);
 assert.equal(docs.get('logs/old-carry-28-2').effectiveDate,'2026-11-01');
 const savedSize=docs.size;await ctx.splitOverflow(25);assert.equal(docs.size,savedSize);
 const configured={praiseReasons:[{name:'기상 시간 준수',count:2},{name:'등교준비시간 준수',count:3},{name:'상차리기 및 치우기',count:4},{name:'약속시간 준수 숙제',count:5},{name:'취침시간 준수',count:6},{name:'수학10문제 맞춤',count:3}]};
 assert.deepEqual(Array.from({length:7},(_,i)=>ctx.routineCount(configured,i)),[2,3,4,4,5,6,3]);
 assert.equal(ctx.routineCount({},6),3);
 configured.praiseReasons[5].count=7;assert.equal(ctx.routineCount(configured,6),7);
 assert.throws(()=>ctx.routineCount({praiseReasons:[{name:'수학10문제 맞춤',count:0}]},6));
 assert.throws(()=>ctx.routineCount({praiseReasons:[{name:'수학10문제 맞춤',count:3},{name:'수학연산 10문제 맞춤',count:1}]},6));
 reset();docs.get('meta/state').praiseReasons=[{name:'수학10문제 맞춤',count:3}];
 await ctx.decideRoutine('2026-11-24',6,true);assert.equal(docs.get('logs/routine-2026-11-24-6').score,3);assert.equal(docs.get('logs/routine-2026-11-24-6').reason,'수학10문제 맞춤');
 await ctx.decideRoutine('2026-11-24',6,true);assert.equal(docs.get('meta/state').lifetimeScore,3);
 console.log('PASS: routine time boundaries, backlog, atomic failure, reward effects, duplicate/skip protection and parent gate; no production writes');
})().catch(e=>{console.error(e);process.exitCode=1;});
