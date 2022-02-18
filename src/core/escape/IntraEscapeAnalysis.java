package core.escape;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import core.Procedure;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

/**
 *
 * @author zzk
 */
public class IntraEscapeAnalysis {
  private EscapeSummary escapeSummary = null;
  
  public IntraEscapeAnalysis(Procedure proc) {
    // when intraprocedural escape analysis is summarized, GC reclaims this space!
    Map<ISSABasicBlock, ConnectionGraph> dataValMap = new HashMap<>();
    
    LinkedList<ISSABasicBlock> BBList = proc.getNodeListForward();
    LinkedList<ISSABasicBlock> workList = new LinkedList<>(BBList);
    
    ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = proc.getCFG();
    int paramCnt = proc.getIR().getNumberOfParameters();
    while (!workList.isEmpty()) {
      ISSABasicBlock BB = workList.pollFirst();
      
      ConnectionGraph oldDataVal = dataValMap.get(BB);
      ConnectionGraph newDataVal = BB == cfg.entry() ? new ConnectionGraph(paramCnt) : new ConnectionGraph(0);
      
      //Collection<ISSABasicBlock> predBBSet = cfg.getNormalPredecessors(BB);
      //for (ISSABasicBlock predBB : predBBSet) {
      Iterator<ISSABasicBlock> predBBIter = cfg.getPredNodes(BB);
      while (predBBIter.hasNext()) {
        ISSABasicBlock predBB = predBBIter.next();
        ConnectionGraph predDataVal = dataValMap.get(predBB);
        if (predDataVal == null)
          continue;
        newDataVal.mergeConnectionGraph(predDataVal);
      }
      
      newDataVal.updateConnectionGraph(proc, BB);
      if (oldDataVal == null || !newDataVal.isEqual(oldDataVal)) {
        dataValMap.put(BB, newDataVal);
        //Collection<ISSABasicBlock> succBBSet = cfg.getNormalSuccessors(BB);
        //for (ISSABasicBlock succBB : succBBSet)
        Iterator<ISSABasicBlock> succBBIter = cfg.getSuccNodes(BB);
        while (succBBIter.hasNext()) {
          ISSABasicBlock succBB = succBBIter.next();
          if (!workList.contains(succBB))
            workList.addLast(succBB);
        }
      }
    }
    
    ISSABasicBlock exitBB = cfg.exit();
    ConnectionGraph exitDataVal = dataValMap.get(exitBB);
    this.escapeSummary = new EscapeSummary(proc);
    exitDataVal.summarizeConnectionGraph(this.escapeSummary);
  }
  
  final public EscapeSummary getEscapeSummary() {
    return this.escapeSummary;
  }
}
