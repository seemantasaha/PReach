package core;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author zzk
 */
public class NewObjectAnalysis {
  static private Map<NewObject, List<NestedLoop>> newObjectNestedLoopListMap = new HashMap<>();
  
  static public void deriveNewObjectNestedLoopList() {
    //EscapeAnalysis.performEscapeAnalysis(this);
//printEscapeAnalysis();
    for (Procedure proc : Program.getProcedureSet()) {
      ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = proc.getCFG();
      Iterator<ISSABasicBlock> nodeIter = cfg.iterator();
      while (nodeIter.hasNext()) {
        ISSABasicBlock node = nodeIter.next();
        
        Iterator<SSAInstruction> instIter = node.iterator();
        while (instIter.hasNext()) {
          SSAInstruction inst = instIter.next();
          // OK, we find a new instruction in this node
          if (inst instanceof SSANewInstruction) {
            NewObject newObj = new NewObject(proc, (SSANewInstruction)inst);
            ISSABasicBlock newNode = newObj.getNewNode();
            
            List<NestedLoop> nestedLoopList = new ArrayList<>();
            nestedLoopList.addAll(NestedLoopAnalysis.getInterproceduralNestedLoopSet(proc, newNode));
            
            newObjectNestedLoopListMap.put(newObj, nestedLoopList);
          }
        }
      }
    }
  }
  
  static public Set<NewObject> getNewObjectSet() {
    return newObjectNestedLoopListMap.keySet();
  }
  
  static public List<NestedLoop> getInterproceduralNestedLoopList(NewObject newObj) {
    List<NestedLoop> nestedLoopList = newObjectNestedLoopListMap.get(newObj);
    if (nestedLoopList != null)
      return nestedLoopList;
    else
      return new ArrayList<>();
  }
}
