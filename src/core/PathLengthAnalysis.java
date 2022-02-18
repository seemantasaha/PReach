package core;

import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SymbolTable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author zzk
 */
public class PathLengthAnalysis {
  static public class Decision {
    Procedure       procedure = null;
    SSAInstruction  branchInstruction = null;
    
    public Decision(Procedure proc, SSAInstruction branchInst) {
      this.procedure = proc;
      this.branchInstruction = branchInst;
    }
    
    public Procedure getProcedure() {
      return this.procedure;
    }
    
    public SSAInstruction getBranchInstruction() {
      return this.branchInstruction;
    }
  }
  
  static private class LengthSummary {
    long longest = 0;
    long shortest = 0;
  }
  
  static private Map<Procedure, LengthSummary>            procedureLengthSummaryMap = new HashMap<>();
  static private Map<Decision, ArrayList<LengthSummary>>  decisionLengthSummaryListMap = new HashMap<>();
  
  static private int getInstructionCost(Procedure proc, SSAInstruction inst) {
    int instCost = 1;
    SymbolTable symTab = proc.getIR().getSymbolTable();
    if (inst instanceof SSAInvokeInstruction) {
      SSAInvokeInstruction invokeInst = (SSAInvokeInstruction)inst;
      String target = invokeInst.getDeclaredTarget().getSignature();
      if (target.equals("java.lang.Thread.sleep(J)V")) {
        int param = invokeInst.getUse(0);
        if (symTab.isConstant(param)) {
          long ms = symTab.getLongValue(param);
          instCost += ms * 10000;
        }
      }
    }
    return instCost;
  }
  
  static private LengthSummary analyzeNode(Procedure proc, ISSABasicBlock node, Set<ISSABasicBlock> nodeFlagSet) {
    ProcedureDependenceGraph pdg = ProgramDependenceGraph.getProcedureDependenceGraph(proc);
    LengthSummary nodeLenSum = new LengthSummary();
    
    int nodeCost = 0;
    for (SSAInstruction inst : node)
      nodeCost += getInstructionCost(proc, inst);
    //((SSACFG.BasicBlock)node).getAllInstructions().size();
    nodeLenSum.longest = nodeCost;
    nodeLenSum.shortest = nodeCost;
    
    // in the case of a loop, loop header is often control dependent on itself or a conditional-break
    // here using flag set, we suppose back edge can only be executed once
    if (nodeFlagSet.contains(node))
      return nodeLenSum;
    nodeFlagSet.add(node);
    
    List<Set<ISSABasicBlock>> ctrlDepNodeSetList = pdg.getControlDependentNodeSetList(node);
    if (!ctrlDepNodeSetList.isEmpty()) {
      long branchLongest = 0;
      long branchShortest = 0;
      boolean branchInit = true;
      
      SSAInstruction branchInst = node.getLastInstruction();
      Decision decision = new Decision(proc, branchInst);
      
      ArrayList<LengthSummary> branchLenSumList = new ArrayList<>();
      for (Set<ISSABasicBlock> ctrlDepNodeSet : ctrlDepNodeSetList) {
        LengthSummary branchLenSum = new LengthSummary();
        for (ISSABasicBlock ctrlDepNode : ctrlDepNodeSet) {
          LengthSummary ctrlDepNodeLenSum = analyzeNode(proc, ctrlDepNode, nodeFlagSet);
          branchLenSum.longest += ctrlDepNodeLenSum.longest;
          branchLenSum.shortest += ctrlDepNodeLenSum.shortest;
        }        
        branchLenSumList.add(branchLenSum);
        
        if (branchInit) {
          branchLongest = branchLenSum.longest;
          branchShortest = branchLenSum.shortest;
          branchInit = false;
          continue;
        }
        
        if (branchLongest < branchLenSum.longest)
          branchLongest = branchLenSum.longest;
        if (branchShortest > branchLenSum.shortest)
          branchShortest = branchLenSum.shortest;
      }
      
      if (ctrlDepNodeSetList.size() == 1) {
        LengthSummary branchLenSum = new LengthSummary();
        branchLenSumList.add(branchLenSum);
        branchShortest = 0;
      }
      
      decisionLengthSummaryListMap.put(decision, branchLenSumList);
      
      nodeLenSum.longest += branchLongest;
      nodeLenSum.shortest += branchShortest;
    }
    
    // WHAT IF this node is a call node? we need to check all its callee's path length summary
    // since we summarize the procedures in their POST-ORDER, its callees should be summarized already
    // note that: recursion is an exception, in which case we assume recusion depth is 0
    long calleeLongest = 0;
    long calleeShortest = 0;
    boolean calleeInit = true;
    Set<Procedure> calleeSet = proc.getCalleeSet(node);
    for (Procedure callee : calleeSet) {
      LengthSummary calleeLenSum = procedureLengthSummaryMap.get(callee);
      if (calleeLenSum == null)
        continue;
      
      if (calleeInit) {
        calleeLongest = calleeLenSum.longest;
        calleeShortest = calleeLenSum.shortest;
        calleeInit = false;
        continue;
      }
      
      if (calleeLongest < calleeLenSum.longest)
        calleeLongest = calleeLenSum.longest;
      if (calleeShortest > calleeLenSum.shortest)
        calleeShortest = calleeLenSum.shortest;
    }
    nodeLenSum.longest += calleeLongest;
    nodeLenSum.shortest += calleeShortest;
    
    return nodeLenSum;
  }
  
  static public void summarizeProgram() {
    LinkedList<Procedure> procPostOrderList = Program.getProcedurePostOrderList();
    for (Procedure proc : procPostOrderList) {
      ProcedureDependenceGraph pdg = ProgramDependenceGraph.getProcedureDependenceGraph(proc);
      LengthSummary procLenSum = new LengthSummary();
      
      Set<ISSABasicBlock> baseLevelNodeSet = pdg.getBaseLevelNodeSet();
      for (ISSABasicBlock baseLevelNode : baseLevelNodeSet) {
        Set<ISSABasicBlock> nodeFlagSet = new HashSet<>();
        LengthSummary nodeLenSum = analyzeNode(proc, baseLevelNode, nodeFlagSet);
        procLenSum.longest += nodeLenSum.longest;
        procLenSum.shortest += nodeLenSum.shortest;
      }
      procedureLengthSummaryMap.put(proc, procLenSum);
    }
    
//for (Map.Entry<Procedure, LengthSummary> procLenSumEnt : procedureLengthSummaryMap.entrySet()) {
  //System.out.println(procLenSumEnt.getKey().getProcedureName());
  //LengthSummary procLenSum = procLenSumEnt.getValue();
  //System.out.println("\t" + procLenSum.longest + " " + procLenSum.shortest);
//}
  }
  
  static public Set<Decision> getDecisionSet(long instCntDiff) {
    Set<Decision> decisionSet = new HashSet<>();
    for (Map.Entry<Decision, ArrayList<LengthSummary>> mapEnt : decisionLengthSummaryListMap.entrySet()) {
      Decision decision = mapEnt.getKey();
      ArrayList<LengthSummary> lenSumList = mapEnt.getValue();
      for (int i = 0; i < lenSumList.size(); i++)
        for (int j = 0; j < lenSumList.size(); j++) {
          if (j == i)
            continue;
          long longest = lenSumList.get(i).longest;
          long shortest = lenSumList.get(j).shortest;
          if (longest - shortest >= instCntDiff)
            decisionSet.add(decision);
        }
    }
    return decisionSet;
  }
}
