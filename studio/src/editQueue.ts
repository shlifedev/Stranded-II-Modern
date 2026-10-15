/** Keep rapid clicks ordered; a failed edit cancels dependent queued requests. */
export class EditQueue {
  private jobs:{work:()=>Promise<boolean>;resolve:(ok:boolean)=>void}[]=[];
  active=false;
  enqueue(work:()=>Promise<boolean>):Promise<boolean>{
    return new Promise(resolve=>{this.jobs.push({work,resolve});if(!this.active)void this.drain();});
  }
  private async drain(){
    this.active=true;
    try{
      while(this.jobs.length){
        const job=this.jobs.shift()!;
        let ok=false;
        try{ok=await job.work();}catch{ok=false;}
        job.resolve(ok);
        if(!ok){for(const queued of this.jobs.splice(0))queued.resolve(false);break;}
      }
    }finally{this.active=false;}
  }
}
