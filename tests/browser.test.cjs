const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'..');
fs.mkdirSync(path.join(root,'out'),{recursive:true});
(async()=>{
 const server=require('http').createServer((req,res)=>{const file=path.join(root,'tools/temporary-visual-review',req.url==='/'?'index.html':req.url);try{res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html');res.end(fs.readFileSync(file))}catch{res.writeHead(404);res.end()}});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 const page=await browser.newPage({viewport:{width:1440,height:1100},acceptDownloads:true});let errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:'+server.address().port);
 await page.selectOption('#preset','Golden dusk');
 await page.click('#split');
 await page.screenshot({path:path.join(root,'out/editor-26.3.png'),fullPage:true});
 const result=await page.evaluate(()=>{
  const c=document.createElement('canvas');c.width=16;c.height=16;const gl=c.getContext('webgl2');if(!gl)throw Error('No WebGL2');
  const vertex='#version 300 es\nprecision highp float;out vec2 texCoord;void main(){vec2 uv=vec2((gl_VertexID << 1)&2,gl_VertexID&2);gl_Position=vec4(uv*2.0-1.0,0,1);texCoord=uv;}';
  function shader(type,source){const x=gl.createShader(type);gl.shaderSource(x,source);gl.compileShader(x);if(!gl.getShaderParameter(x,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(x));return x}
  let worst=0;
  const input=new Uint8Array(16*16*4);for(let i=0;i<input.length;i+=4){input[i]=(i*7)%256;input[i+1]=(i*11+40)%256;input[i+2]=(i*13+70)%256;input[i+3]=128}
  const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,16,16,0,gl.RGBA,gl.UNSIGNED_BYTE,input);
  for(const settings of [defaults,{...defaults,...presets['Golden dusk']},{...defaults,exposure:1.3,gamma:1.2,black:.05,white:.93,shoulder:1,vibrance:.6,vignette:.35,opacity:.7,curves:{master:[[0,.02],[.23,.15],[.72,.85],[1,.98]],red:[[0,0],[.5,.63],[1,1]],green:[[0,.03],[1,.92]],blue:[[0,1],[.3,.2],[.6,.8],[1,0]]}},{...defaults,curves:{...ShaderCurves.identity(),master:Array.from({length:16},(_,i)=>[i/15,(i/15)**.7])}}]){
   const fragment=ShaderPipeline.fragment(settings).replace('#version 330','#version 300 es\nprecision highp float;').replace('#extension GL_ARB_separate_shader_objects : require','').replace('layout(location = 0) in vec2','in vec2');
   const program=gl.createProgram();gl.attachShader(program,shader(gl.VERTEX_SHADER,vertex));gl.attachShader(program,shader(gl.FRAGMENT_SHADER,fragment));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));gl.useProgram(program);gl.uniform1i(gl.getUniformLocation(program,'InSampler'),0);gl.drawArrays(gl.TRIANGLES,0,3);
   const out=new Uint8Array(input.length);gl.readPixels(0,0,16,16,gl.RGBA,gl.UNSIGNED_BYTE,out);
   for(let y=0;y<16;y++)for(let x=0;x<16;x++){const i=(y*16+x)*4;const expected=ShaderPipeline.pixel(input[i]/255,input[i+1]/255,input[i+2]/255,(x+.5)/16*2-1,(y+.5)/16*2-1,settings,settings.opacity);for(let j=0;j<3;j++)worst=Math.max(worst,Math.abs(out[i+j]-Math.round(expected[j]*255)));if(out[i+3]!==128)throw Error('Alpha changed')}
  }
  return {worst};
 });
 assert.ok(result.worst<=1,JSON.stringify(result));
 await page.click('[data-tab=advanced]');
 await page.selectOption('#curve-preset','contrast');
 assert.equal(await page.locator('#curve-point option').count(),4);
 await page.selectOption('#curve-point','1');
 await page.locator('#curve-output').fill('80');await page.locator('#curve-output').press('Tab');
 assert.equal(await page.evaluate(()=>state.curves.master[1][1]),80/255);
 await page.click('#undo');assert.equal(await page.evaluate(()=>state.curves.master[1][1]),.19);
 await page.click('#redo');assert.equal(await page.evaluate(()=>state.curves.master[1][1]),80/255);
 await page.selectOption('#curve-channel','blue');
 await page.click('#curve-add');assert.equal(await page.locator('#curve-point option').count(),3);
 await page.locator('#curve-graph').focus();await page.keyboard.press('ArrowUp');
 assert.ok(await page.evaluate(()=>state.curves.blue[1][1]>.5));
 await page.keyboard.press('Delete');assert.equal(await page.locator('#curve-point option').count(),2);
 await page.keyboard.press('Control+z');assert.equal(await page.locator('#curve-point option').count(),3);
 // A curve drag is a single undo step, regardless of pointermove count.
 await page.locator('#curve-graph').scrollIntoViewIfNeeded();
 const graph=await page.locator('#curve-graph').boundingBox();
 const beforeDrag=await page.evaluate(()=>JSON.stringify(state.curves));
 await page.mouse.move(graph.x+graph.width*.5,graph.y+graph.height*.496);
 await page.mouse.down();await page.mouse.move(graph.x+graph.width*.57,graph.y+graph.height*.32,{steps:4});await page.mouse.up();
 assert.notEqual(await page.evaluate(()=>JSON.stringify(state.curves)),beforeDrag);
 await page.click('#undo');assert.equal(await page.evaluate(()=>JSON.stringify(state.curves)),beforeDrag);
 await page.click('#redo');
 await page.check('#show-clipping');
 assert.match(await page.locator('#clipping-stats').innerText(),/Original:.*Graded:/);
 await page.screenshot({path:path.join(root,'out/curves-editor.png'),fullPage:true});
 // Viewing diagnostics and split/compare must not be baked into PNG output.
 const expectedHash=await page.evaluate(()=>{const old={compare,split,showClipping};compare=false;split=false;showClipping=false;draw();const d=ctx.getImageData(0,0,canvas.width,canvas.height).data;let hash=0;for(const v of d)hash=(Math.imul(hash,31)+v)|0;({compare,split,showClipping}=old);draw();return hash});
 const pngDownload=page.waitForEvent('download');await page.click('#png');const pngFile=path.join(root,'out/curve-preview.png');await(await pngDownload).saveAs(pngFile);
 const actualHash=await page.evaluate(async base64=>{const image=new Image();image.src='data:image/png;base64,'+base64;await image.decode();const c=document.createElement('canvas');c.width=image.width;c.height=image.height;const cx=c.getContext('2d');cx.drawImage(image,0,0);let hash=0;for(const v of cx.getImageData(0,0,c.width,c.height).data)hash=(Math.imul(hash,31)+v)|0;return hash},fs.readFileSync(pngFile).toString('base64'));
 assert.equal(actualHash,expectedHash);
 // Project round trip keeps curves; old projects migrate to identity.
 let saved=page.waitForEvent('download');await page.click('#save');const projectDownload=await saved;
 const projectFile=path.join(root,'out/curve-project.json');await projectDownload.saveAs(projectFile);
 const savedState=await page.evaluate(()=>JSON.stringify(state));
 assert.equal(JSON.parse(fs.readFileSync(projectFile)).version,3);
 await page.click('#reset');await page.locator('#open').setInputFiles(projectFile);
 await page.waitForFunction(expected=>JSON.stringify(state)===expected,savedState);
 for(const version of [1,2]) {
  const legacy={format:'shader-maker-project',version,state:{exposure:.4}};
  await page.locator('#open').setInputFiles({name:'legacy.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(legacy))});
  await page.waitForFunction(()=>state.exposure===.4);
  assert.equal(await page.evaluate(()=>JSON.stringify(state.curves)),await page.evaluate(()=>JSON.stringify(ShaderCurves.identity())));
 }
 await page.locator('#open').setInputFiles(projectFile);await page.waitForFunction(expected=>JSON.stringify(state)===expected,savedState);
 const invalid={format:'shader-maker-project',version:3,state:{curves:{master:[[0,0],[1,1]]}}};
 page.once('dialog',d=>d.accept());
 await page.locator('#open').setInputFiles({name:'bad.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(invalid))});
 await page.waitForTimeout(100);assert.equal(await page.evaluate(()=>JSON.stringify(state)),savedState);
 await page.check('[data-key=altitudeEnabled]');await page.click('#export');assert.match(await page.locator('#export-status').innerText(),/cannot preserve/);
 await page.uncheck('[data-key=altitudeEnabled]');let download=page.waitForEvent('download');await page.click('#export');await (await download).saveAs(path.join(root,'out/browser-resources.zip'));
 await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(root,'out/editor-mobile.png'),fullPage:true});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 assert.deepEqual(errors,[]);console.log('Browser, export gating, responsive layout and GPU parity passed',result);await browser.close();server.close();
})().catch(e=>{console.error(e);process.exit(1)});
