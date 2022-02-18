package core;

import com.ibm.wala.analysis.pointers.HeapGraph;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSANewInstruction;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author zzk
 */
public class NewObject {
  private Procedure         procedure;
  private SSANewInstruction newInstruction;
  private Set<Procedure>    liveProcedureSet = new HashSet<>();
  private Set<Loop>         liveLoopSet = new HashSet<>();
  
  public NewObject(Procedure proc, SSANewInstruction newInst) {
    this.procedure = proc;
    this.newInstruction = newInst;
    this.liveProcedureSet.add(proc);
  }
  
  final public void analyzePointer(HeapGraph hg) {
    //newInst.getNewSite();
    //hg.getHeapModel().getInstanceKeyForAllocation(null, null)
  }
  
  final public Procedure getNewProcedure() {
    return this.procedure;
  }
    
  final public ISSABasicBlock getNewNode() {
    IR ir = this.procedure.getIR();
    return ir.getBasicBlockForInstruction(this.newInstruction);
  }
  
  final public SSANewInstruction getNewInstruction() {
    return this.newInstruction;
  }
}
