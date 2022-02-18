package core;

import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import core.escape.EscapeSummary;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author zzk
 */
public class Printer {
  static private void printLoopDependence(Loop loop, int indent, Set<Loop> flagSet) {
    if (flagSet.contains(loop))
      return;
    flagSet.add(loop);
    
    for (int i = 0; i < indent; i++)
      System.out.print("\t");
    System.out.println(loop.getLoopHeader());
    Set<Loop> depLoopSet = loop.getDependenceLoopSet();
    for (Loop depLoop : depLoopSet)
      printLoopDependence(depLoop, indent + 1, flagSet);
  }
  
  static public void printLoopExitCondition(Loop loop) {
    Set<SSAInstruction> condInstSet = loop.getLoopExitConditionSet();
    for (SSAInstruction condInst : condInstSet)
      System.out.println(condInst);
    System.out.println("\n");
  }
  
  static public void printLoopGroup() {
    for (Procedure proc : Program.getProcedureSet()) {
      Set<Loop> loopSet = proc.getLoopSet();
      for (Loop loop : loopSet) {
        System.out.println("\nLoop");
        //Set<Loop> flagSet = new HashSet<>();
        //printLoopDependence(loop, 0, flagSet);
        printLoopExitCondition(loop);
      }
    }
  }
  
  static public void printArgumentType() {
    for (Procedure proc : Program.getProcedureSet()) {
      System.out.println(proc.getFullSignature());
      int paramCnt = proc.getIR().getNumberOfParameters();
      for (int i = 0; i < paramCnt; i++) {
        System.out.println("\t param " + i + " corresponds to argument types: ");
        Set<Argument> argSet = proc.getArgumentSet(i);
        for (Argument arg : argSet) {
          System.out.println("\t\t" + arg.getArgumentType());
          Set<Argument> onThisSet = arg.getArgumentSetForMethodCalledOnThis();
          for (Argument onThis : onThisSet)
            System.out.println("\t\t\t" + onThis.getArgumentType());
        }
      }
    }
  }
  
  /*****************************************************************************
   * SDG, PDG, CDG, DDG related
   ****************************************************************************/
  final public void printControlDependenceGraph(Procedure proc) {
    ProcedureDependenceGraph procDepGraph = ProgramDependenceGraph.getProcedureDependenceGraph(proc);
    System.out.println(proc.getFullSignature());
    
    LinkedList<ISSABasicBlock> nodeList = proc.getNodeListForward();
    for (ISSABasicBlock node : nodeList) {
      List<Set<ISSABasicBlock>> ctrlDepNodeSetList = procDepGraph.getControlDependentNodeSetList(node);
      if (ctrlDepNodeSetList.isEmpty())
        continue;
      System.out.println(node.getNumber());
      int i = 0;
      for (Set<ISSABasicBlock> ctrlDepNodeSet : ctrlDepNodeSetList) {
        System.out.println("\tbranch:" + i++);
        for (ISSABasicBlock ctrlDepNode : ctrlDepNodeSet)
          System.out.println("\t\t" + ctrlDepNode.getNumber());
      }
    }
  }
  
  static public void printAccessPathSet(Procedure proc) {
    ProcedureDependenceGraph procDepGraph = ProgramDependenceGraph.getProcedureDependenceGraph(proc);
    System.out.println(proc.getFullSignature());
    SymbolTable symTab = proc.getIR().getSymbolTable();
    
    // when vn is 0, it represents the access-paths that are referred to in the callees of this procedure
    for (int vn = 0; vn <= symTab.getMaxValueNumber(); vn++) {
      Set<String> accessPathSet = procDepGraph.getAccessPathSetAliasToThisVN(vn);
      if (accessPathSet.isEmpty())
        continue;
      System.out.println("\tv" + vn + " may correspond to access path");
      for (String accessPath : accessPathSet)
        System.out.println("\t\t" + accessPath);
    }
  }
  
  static public void printFormalParameterModel(Procedure proc, boolean in, boolean out) {
    ProcedureDependenceGraph procDepGraph = ProgramDependenceGraph.getProcedureDependenceGraph(proc);
    System.out.println(proc.getFullSignature());
    
    if (in) {
      Set<Statement> stmtSet = procDepGraph.getFormalInStatementSet();
      Set<String> formalInSet = new TreeSet<>();
      for (Statement stmt : stmtSet)
        formalInSet.add((String)stmt.getContent());
      for (String formalIn : formalInSet)
        System.out.println("\t" + formalIn);
    }
    
    if (out) {
      Set<Statement> stmtSet = procDepGraph.getFormalOutStatementSet();
      Set<String> formalOutSet = new TreeSet<>();
      for (Statement stmt : stmtSet)
        formalOutSet.add((String)stmt.getContent());
      for (String formalOut : formalOutSet)
        System.out.println("\t" + formalOut);
    }
  }
  
  /*****************************************************************************
   * escape analysis related
   ****************************************************************************/
  static public void printEscapeAnalysis() {
    for (Procedure proc : Program.getProcedureSet()) {
      System.out.println(proc.getFullSignature());
      EscapeSummary escapeSum = proc.getEscapeSummary();
      
      System.out.println("\twhich newed objects will globally escape");
      Set<String> globalObjNodeNameSet = escapeSum.getGlobalObjectNodeNameSet();
      for (String globalObjNodeName : globalObjNodeNameSet)
        System.out.println("\t\t" + globalObjNodeName);
      
      System.out.println("\twhich newed objects will procedurally escape");
      Set<String> proceduralObjNodeNameSet = escapeSum.getProceduralObjectNodeNameSet();
      for (String proceduralObjNodeName : proceduralObjNodeNameSet)
        System.out.println("\t\t" + proceduralObjNodeName);
      
      System.out.println("\twhich called procedures will have their objects globally escape");
      Set<String> globalSumNameSet = escapeSum.getGlobalSummaryNameSet();
      for (String globalSumName : globalSumNameSet)
        System.out.println("\t\t" + globalSumName);

      System.out.println("\twhich called procedures will have their objects procedurally escape");
      Set<String> proceduralSumNameSet = escapeSum.getProceduralSummaryNameSet();
      for (String proceduralSumName : proceduralSumNameSet)
        System.out.println("\t\t" + proceduralSumName);
    }
  }
  
  /*****************************************************************************
   * pointer analysis related
   ****************************************************************************/
  static public void printIntraproceduralAliases(Procedure proc) {
    System.out.println(proc.getFullSignature());
    SymbolTable symTab = proc.getIR().getSymbolTable();
    for (int i = 1; i <= symTab.getMaxValueNumber(); i++) {
      System.out.println("\tv" + i + " may be alias with");
      Set<Integer> aliasSet = proc.getAliasSet(i);
      for (Integer alias : aliasSet)
        System.out.println("\t\t" + alias);
    }
  }
}
