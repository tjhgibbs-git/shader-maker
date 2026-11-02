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
  for(const settings of [defaults,{...defaults,...presets['Golden dusk']},{...defaults,exposure:1.3,gamma:1.2,black:.05,white:.93,shoulder:1,vibrance:.6,vignette:.35,opacity:.7}]){
   const fragment=ShaderPipeline.fragment(settings).replace('#version 330','#version 300 es\nprecision highp float;').replace('#extension GL_ARB_separate_shader_objects : require','').replace('layout(location = 0) in vec2','in vec2');
   const program=gl.createProgram();gl.attachShader(program,shader(gl.VERTEX_SHADER,vertex));gl.attachShader(program,shader(gl.FRAGMENT_SHADER,fragment));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));gl.useProgram(program);gl.uniform1i(gl.getUniformLocation(program,'InSampler'),0);gl.drawArrays(gl.TRIANGLES,0,3);
   const out=new Uint8Array(input.length);gl.readPixels(0,0,16,16,gl.RGBA,gl.UNSIGNED_BYTE,out);
   for(let y=0;y<16;y++)for(let x=0;x<16;x++){const i=(y*16+x)*4;const expected=ShaderPipeline.pixel(input[i]/255,input[i+1]/255,input[i+2]/255,(x+.5)/16*2-1,(y+.5)/16*2-1,settings,settings.opacity);for(let j=0;j<3;j++)worst=Math.max(worst,Math.abs(out[i+j]-Math.round(expected[j]*255)));if(out[i+3]!==128)throw Error('Alpha changed')}
  }
  return {worst};
 });
 assert.ok(result.worst<=1,JSON.stringify(result));
 await page.click('[data-tab=advanced]');await page.check('[data-key=altitudeEnabled]');await page.click('#export');assert.match(await page.locator('#export-status').innerText(),/cannot preserve/);
 await page.uncheck('[data-key=altitudeEnabled]');let download=page.waitForEvent('download');await page.click('#export');await (await download).saveAs(path.join(root,'out/browser-resources.zip'));
 await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(root,'out/editor-mobile.png'),fullPage:true});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 assert.deepEqual(errors,[]);console.log('Browser, export gating, responsive layout and GPU parity passed',result);await browser.close();server.close();
})().catch(e=>{console.error(e);process.exit(1)});
