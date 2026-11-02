(function(root){
'use strict';
class EditHistory {
  constructor(initial,limit=100){this.entries=[structuredClone(initial)];this.index=0;this.limit=limit}
  commit(state){if(JSON.stringify(state)===JSON.stringify(this.entries[this.index]))return false;this.entries.splice(this.index+1);this.entries.push(structuredClone(state));if(this.entries.length>this.limit)this.entries.shift();this.index=this.entries.length-1;return true}
  get canUndo(){return this.index>0}
  get canRedo(){return this.index<this.entries.length-1}
  undo(){if(this.canUndo)this.index--;return structuredClone(this.entries[this.index])}
  redo(){if(this.canRedo)this.index++;return structuredClone(this.entries[this.index])}
}
root.EditHistory=EditHistory;
if(typeof module!=='undefined')module.exports=EditHistory;
})(globalThis);
