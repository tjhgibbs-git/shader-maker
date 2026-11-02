/* Disposable UI; curve math and project data are independent of this view. */
class CurveEditor {
  constructor(host,getState,change,commit,view={}) {
    this.getState=getState;this.change=change;this.commit=commit;this.channel=view.channel||'master';this.selected=view.selected||0;
    host.innerHTML=`<div class="curve-toolbar"><label for="curve-channel">Channel</label><select id="curve-channel"><option value="master">Master RGB</option><option value="red">Red</option><option value="green">Green</option><option value="blue">Blue</option></select></div>
      <canvas id="curve-graph" width="512" height="512" tabindex="0" role="img" aria-label="Tone curve. Click to add a point; drag to adjust. Use point controls below for keyboard editing."></canvas>
      <p class="curve-hint">Input → output. Click to add; drag a point. Arrow keys move the selected point; Shift moves 10 steps. Delete removes an interior point.</p>
      <label for="curve-point">Selected point</label><select id="curve-point"></select>
      <div class="curve-values"><label>Input <input id="curve-input" type="number" min="0" max="255" step="1"></label><label>Output <input id="curve-output" type="number" min="0" max="255" step="1"></label></div>
      <div class="curve-actions"><button id="curve-add">Add point</button><button id="curve-delete">Remove point</button><button id="curve-reset">Reset channel</button></div>
      <label for="curve-preset">Master curve starting point</label><select id="curve-preset"><option value="">Choose…</option><option value="contrast">Gentle S-curve</option><option value="lift">Lift shadows</option><option value="matte">Matte blacks</option></select>`;
    this.host=host;this.canvas=host.querySelector('canvas');this.ctx=this.canvas.getContext('2d');
    this.el=id=>host.querySelector('#'+id);
    this.el('curve-channel').onchange=e=>{this.channel=e.target.value;this.selected=0;this.refresh()};
    this.el('curve-point').onchange=e=>{this.selected=Number(e.target.value);this.refresh()};
    for(const [id,axis] of [['curve-input',0],['curve-output',1]])this.el(id).onchange=e=>{if(!e.target.value||!Number.isFinite(e.target.valueAsNumber)){this.refresh();return}this.move(axis,e.target.valueAsNumber/255);this.commit()};
    this.el('curve-add').onclick=()=>{const points=this.points;let index=0;for(let i=1;i<points.length-1;i++)if(points[i+1][0]-points[i][0]>points[index+1][0]-points[index][0])index=i;const x=(points[index][0]+points[index+1][0])/2;this.insert(x,ShaderCurves.evaluate(ShaderCurves.compile(points),x));this.commit()};
    this.el('curve-delete').onclick=()=>{this.remove();this.commit()};
    this.el('curve-reset').onclick=()=>{this.replace([[0,0],[1,1]]);this.selected=0;this.refresh();this.commit()};
    this.el('curve-preset').onchange=e=>{const presets={contrast:[[0,0],[.25,.19],[.75,.81],[1,1]],lift:[[0,0],[.25,.36],[.6,.68],[1,1]],matte:[[0,.07],[.25,.27],[.75,.78],[1,.97]]};if(!presets[e.target.value])return;this.channel='master';this.el('curve-channel').value='master';this.selected=0;this.replace(presets[e.target.value]);e.target.value='';this.refresh();this.commit()};
    this.canvas.onpointerdown=e=>{
      if(e.button!==0)return;e.preventDefault();this.canvas.focus();const [x,y]=this.position(e),rect=this.canvas.getBoundingClientRect();
      let index=-1,distance=Infinity;this.points.forEach((p,i)=>{const d=Math.hypot((x-p[0])*rect.width,(y-p[1])*rect.height);if(d<distance){distance=d;index=i}});
      if(distance<=14)this.selected=index;else if(!this.insert(x,y))return;
      this.dragging=true;this.canvas.setPointerCapture(e.pointerId);this.refresh();
    };
    this.canvas.onpointermove=e=>{if(!this.dragging)return;const [x,y]=this.position(e);this.updatePoint(x,y)};
    const finish=()=>{if(this.dragging){this.dragging=false;this.commit()}};
    this.canvas.onpointerup=finish;this.canvas.onpointercancel=finish;this.canvas.onlostpointercapture=finish;
    this.canvas.onkeydown=e=>{
      if(e.ctrlKey||e.metaKey||e.altKey)return;
      if(['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(e.key)){
        e.preventDefault();const step=(e.shiftKey?10:1)/255,p=this.points[this.selected];
        this.updatePoint(p[0]+(e.key==='ArrowRight'?step:e.key==='ArrowLeft'?-step:0),p[1]+(e.key==='ArrowUp'?step:e.key==='ArrowDown'?-step:0));this.commit();
      }else if(e.key==='Delete'||e.key==='Backspace'){e.preventDefault();this.remove();this.commit()}
    };
    this.el('curve-channel').value=this.channel;this.refresh();
  }
  get points(){return this.getState().curves[this.channel]}
  replace(points){const curves=structuredClone(this.getState().curves);curves[this.channel]=points;this.change(curves)}
  insert(x,y){const points=structuredClone(this.points);if(points.length>=16||x<=0||x>=1||points.some(p=>Math.abs(p[0]-x)<1/255))return false;points.push([x,y]);points.sort((a,b)=>a[0]-b[0]);this.selected=points.findIndex(p=>p[0]===x);this.replace(points);this.refresh();return true}
  remove(){if(this.selected===0||this.selected===this.points.length-1)return;const points=structuredClone(this.points);points.splice(this.selected,1);this.selected--;this.replace(points);this.refresh()}
  position(e){const r=this.canvas.getBoundingClientRect();return [Math.max(0,Math.min(1,(e.clientX-r.left)/r.width)),Math.max(0,Math.min(1,1-(e.clientY-r.top)/r.height))]}
  move(axis,v){const p=this.points[this.selected];this.updatePoint(axis===0?v:p[0],axis===1?v:p[1])}
  updatePoint(x,y){const points=structuredClone(this.points),i=this.selected;points[i]=[i===0?0:i===points.length-1?1:Math.max(points[i-1][0]+1/255,Math.min(points[i+1][0]-1/255,x)),Math.max(0,Math.min(1,y))];this.replace(points);this.refresh()}
  refresh(){
    this.selected=Math.min(this.selected,this.points.length-1);
    const points=this.points,p=points[this.selected];
    this.el('curve-point').innerHTML=points.map((p,i)=>`<option value="${i}">Point ${i+1} · ${Math.round(p[0]*255)} → ${Math.round(p[1]*255)}</option>`).join('');this.el('curve-point').value=this.selected;
    this.el('curve-input').value=Math.round(p[0]*255);this.el('curve-input').disabled=this.selected===0||this.selected===points.length-1;
    this.el('curve-output').value=Math.round(p[1]*255);this.el('curve-delete').disabled=this.selected===0||this.selected===points.length-1;this.el('curve-add').disabled=points.length>=16;
    const ctx=this.ctx,w=this.canvas.width,h=this.canvas.height,colors={master:'#d4c4ff',red:'#f58e90',green:'#8ed7a4',blue:'#8fbaff'};
    ctx.fillStyle='#10131b';ctx.fillRect(0,0,w,h);ctx.strokeStyle='#303644';ctx.lineWidth=1;
    for(let i=1;i<4;i++){ctx.beginPath();ctx.moveTo(w*i/4,0);ctx.lineTo(w*i/4,h);ctx.moveTo(0,h*i/4);ctx.lineTo(w,h*i/4);ctx.stroke()}
    ctx.setLineDash([7,7]);ctx.beginPath();ctx.moveTo(0,h);ctx.lineTo(w,0);ctx.stroke();ctx.setLineDash([]);
    const segments=ShaderCurves.compile(points);ctx.strokeStyle=colors[this.channel];ctx.lineWidth=4;ctx.beginPath();for(let i=0;i<=512;i++){const y=(1-ShaderCurves.evaluate(segments,i/512))*h;i?ctx.lineTo(i,y):ctx.moveTo(i,y)}ctx.stroke();
    points.forEach((p,i)=>{const x=Math.max(7,Math.min(w-7,p[0]*w)),y=Math.max(7,Math.min(h-7,(1-p[1])*h));ctx.beginPath();ctx.arc(x,y,i===this.selected?8:6,0,Math.PI*2);ctx.fillStyle=i===this.selected?'#ffffff':colors[this.channel];ctx.fill()});
  }
}
