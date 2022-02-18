package core;

import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.collections.Pair;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author zzk
 */
// this corresponds to the "SDG - system dependence graph" in the literature
public class ProgramDependenceGraph {
  static private Map<Procedure, ProcedureDependenceGraph> procedureDependenceGraphMap = new HashMap<>();
  
  static private void computeSummaryEdges() {
    Set<Pair<Statement, Statement>> pathEdgeSet = new HashSet<>();
    LinkedList<Pair<Statement, Statement>> worklist = new LinkedList<>();
    for (ProcedureDependenceGraph procDepGraph : procedureDependenceGraphMap.values()) {
      Set<Statement> formalOutStmtSet = procDepGraph.getFormalOutStatementSet();
      for (Statement formalOutStmt : formalOutStmtSet) {
        Pair<Statement, Statement> edge = Pair.make(formalOutStmt, formalOutStmt);
        pathEdgeSet.add(edge);
        worklist.addLast(edge);
      }
    }
    
    // invariant: secStmt (i.e. w) is always a FormalOut!!!!!!!!
    while (!worklist.isEmpty()) {
      Pair<Statement, Statement> edge = worklist.pollFirst();
      Statement fstStmt = edge.fst; // v
      Statement secStmt = edge.snd; // w
      StatementType fstStmtType = fstStmt.getStatementType();
      if (fstStmtType == StatementType.Entry)
        continue;
      
      Set<Pair<Statement, Statement>> newEdgeSet = new HashSet<>();
      if (fstStmtType == StatementType.ActualOut) {
        Statement ctrlStmt = fstStmt.getControlStatement();
        newEdgeSet.add(Pair.make(ctrlStmt, secStmt));
        Set<Statement> sumStmtSet = fstStmt.getSummarySet();
        for (Statement sumStmt : sumStmtSet)
          newEdgeSet.add(Pair.make(sumStmt, secStmt));
      } else if (fstStmtType == StatementType.FormalIn) {
        //Procedure proc = secStmt.getOwner().getProcedure();
        //Set<Procedure> callerSet = proc.getCallerSet();
        Set<Statement> fstStmtDefStmtSet = fstStmt.getDefStatementSet();
        Set<Statement> secStmtDefStmtSet = secStmt.getDefStatementSet();
        for (Statement fstStmtDefStmt : fstStmtDefStmtSet) {
          if (fstStmtDefStmt.getStatementType() != StatementType.ActualIn)
            continue;
          for (Statement secStmtDefStmt : secStmtDefStmtSet) {
            if (secStmtDefStmt.getStatementType() != StatementType.ActualOut)
              continue;
            if (fstStmtDefStmt.getController() != secStmtDefStmt.getController())
              continue;
            // fstStmtDefStmt(i.e. x) ---summary-edge---> secStmtDefStmt(i.e. y)
            fstStmtDefStmt.addSummary(secStmtDefStmt);
            for (Pair<Statement, Statement> pathEdge : pathEdgeSet)
              if (pathEdge.fst == secStmtDefStmt)
                newEdgeSet.add(Pair.make(fstStmtDefStmt, pathEdge.snd));
          }
        }
      } else {
        Statement ctrlStmt = fstStmt.getControlStatement();
        newEdgeSet.add(Pair.make(ctrlStmt, secStmt));
        Set<Statement> defStmtSet = fstStmt.getDefStatementSet();
        for (Statement defStmt : defStmtSet)
          newEdgeSet.add(Pair.make(defStmt, secStmt));
      }
      
      for (Pair<Statement, Statement> newEdge : newEdgeSet) {
        boolean toAdd = true;
        for (Pair<Statement, Statement> pathEdge : pathEdgeSet)
          if (newEdge.fst == pathEdge.fst && newEdge.snd == pathEdge.snd) {
            toAdd = false;
            break;
          }
        if (toAdd) {
          pathEdgeSet.add(newEdge);
          worklist.addLast(newEdge);
        }
      }
    }
  }
  
  // call edges are implicitly captures by procedure callerSet/callNodeSetMap/calleeSetMap
  static public void makeProgramDependenceGraph() {
    LinkedList<Procedure> procPostOrderList = Program.getProcedurePostOrderList();
    while (true) {
      boolean stable = true;
      for (Procedure proc : procPostOrderList) {
        ProcedureDependenceGraph newProcDepGraph = new ProcedureDependenceGraph(proc);
        ProcedureDependenceGraph oldProcDepGraph = procedureDependenceGraphMap.get(proc);
        if (oldProcDepGraph == null || !newProcDepGraph.hasSameInOutModelsWith(oldProcDepGraph)) {
          stable = false;
          procedureDependenceGraphMap.put(proc, newProcDepGraph);
        }
//Printer.printFormalParameterModel(proc, true, false);
      }
      if (stable)
        break;
    }
    
    computeSummaryEdges();
  }
  
  static public ProcedureDependenceGraph getProcedureDependenceGraph(Procedure proc) {
    return procedureDependenceGraphMap.get(proc);
  }
  
  static public Set<Statement> sliceProgramForward(Procedure proc, SSAInstruction inst) {
    Set<Statement> stmtSet = new HashSet<>();
    ProcedureDependenceGraph procDepGraph = getProcedureDependenceGraph(proc);
    if (inst == null || proc.getNode(inst) == null || procDepGraph == null)
      return stmtSet;
    
    Statement seed = procDepGraph.requireInstructionStatement(inst);
    stmtSet.add(seed);
    // collect all the actual-out statements if an invoke instruction is met
    if (inst instanceof SSAInvokeInstruction) {
      SSAInvokeInstruction invokeInst = (SSAInvokeInstruction)inst;
      stmtSet.addAll(procDepGraph.getActualOutStatementSet(invokeInst));
    }
    
    // pass 1
    LinkedList<Statement> stmtQ = new LinkedList<>(stmtSet);
    while (!stmtQ.isEmpty()) {
      Statement stmt = stmtQ.pollFirst();
      StatementType stmtType = stmt.getStatementType();
      
      // control edge
      Set<Statement> slaveStmtSet = stmt.getSlaveStatementSet();
      for (Statement slaveStmt : slaveStmtSet) {
        if (stmtSet.contains(slaveStmt))
          continue;
        stmtSet.add(slaveStmt);
        stmtQ.addLast(slaveStmt);
      }
      
      // summary edge
      if (stmtType == StatementType.ActualIn) {
        Set<Statement> sumStmtSet = stmt.getSummarySet();
        for (Statement sumStmt : sumStmtSet) {
          if (stmtSet.contains(sumStmt))
            continue;
          stmtSet.add(sumStmt);
          stmtQ.addLast(sumStmt);
        }
      } else {
      // flow edge and parameter-out edge
        Set<Statement> useStmtSet = stmt.getUseStatementSet();
        for (Statement useStmt : useStmtSet) {
          if (stmtSet.contains(useStmt))
            continue;
          stmtSet.add(useStmt);
          stmtQ.addLast(useStmt);
        }
      }
    }
    
    // pass 2
    stmtQ.addAll(stmtSet);
    while (!stmtQ.isEmpty()) {
      Statement stmt = stmtQ.pollFirst();
      StatementType stmtType = stmt.getStatementType();
      
      // control edge
      Set<Statement> slaveStmtSet = stmt.getSlaveStatementSet();
      for (Statement slaveStmt : slaveStmtSet) {
        if (stmtSet.contains(slaveStmt))
          continue;
        stmtSet.add(slaveStmt);
        stmtQ.addLast(slaveStmt);
      }
      
      // call edge
      if (stmtType != StatementType.Entry) {
        Set<Statement> callStmtSet = stmt.getCallSet();
        for (Statement callStmt : callStmtSet) {
          if (stmtSet.contains(callStmt))
            continue;
          stmtSet.add(callStmt);
          stmtQ.addLast(callStmt);
        }
      }
      
      // summary edge
      if (stmtType == StatementType.ActualIn) {
        Set<Statement> sumStmtSet = stmt.getSummarySet();
        for (Statement sumStmt : sumStmtSet) {
          if (stmtSet.contains(sumStmt))
            continue;
          stmtSet.add(sumStmt);
          stmtQ.addLast(sumStmt);
        }
      }
      
      // flow edge and parameter-in edge
      if (stmtType != StatementType.FormalOut) {
        Set<Statement> useStmtSet = stmt.getUseStatementSet();
        for (Statement useStmt : useStmtSet) {
          if (stmtSet.contains(useStmt))
            continue;
          stmtSet.add(useStmt);
          stmtQ.addLast(useStmt);
        }
      }
    }
    
    return stmtSet;
  }
  
  static public Set<Statement> sliceProgramBackward(Procedure proc, SSAInstruction inst) {
    Set<Statement> fstStmtSet = new HashSet<>();
    ProcedureDependenceGraph procDepGraph = getProcedureDependenceGraph(proc);
    if (inst == null || proc.getNode(inst) == null || procDepGraph == null)
      return fstStmtSet;
    
    Statement seed = procDepGraph.requireInstructionStatement(inst);
    fstStmtSet.add(seed);
    // collect all the actual-in statements if an invoke instruction is met
    if (inst instanceof SSAInvokeInstruction) {
      SSAInvokeInstruction invokeInst = (SSAInvokeInstruction)inst;
      fstStmtSet.addAll(procDepGraph.getActualInStatementSet(invokeInst));
    }
    
    // pass 1
    LinkedList<Statement> stmtQ = new LinkedList<>(fstStmtSet);
    while (!stmtQ.isEmpty()) {
      Statement stmt = stmtQ.pollFirst();
      StatementType stmtType = stmt.getStatementType();
      
      // control edge
      if (stmtType != StatementType.Entry) {
        Statement ctrlStmt = stmt.getControlStatement();
        if (!fstStmtSet.contains(ctrlStmt)) {
          fstStmtSet.add(ctrlStmt);
          stmtQ.addLast(ctrlStmt);
        }
      }
      
      // call edge
      if (stmtType == StatementType.Entry) {
        Set<Statement> callStmtSet = stmt.getCallSet();
        for (Statement callStmt : callStmtSet) {
          if (fstStmtSet.contains(callStmt))
            continue;
          fstStmtSet.add(callStmt);
          stmtQ.addLast(callStmt);
        }
      } else if (stmtType == StatementType.ActualOut) {
      // summary edge
        Set<Statement> sumStmtSet = stmt.getSummarySet();
        for (Statement sumStmt : sumStmtSet) {
          if (fstStmtSet.contains(sumStmt))
            continue;
          fstStmtSet.add(sumStmt);
          stmtQ.addLast(sumStmt);
        }
      } else {
      // flow edge and parameter-in edge
        Set<Statement> defStmtSet = stmt.getDefStatementSet();
        for (Statement defStmt : defStmtSet) {
          if (fstStmtSet.contains(defStmt))
            continue;
          fstStmtSet.add(defStmt);
          stmtQ.addLast(defStmt);
        }
      }
    }
    
    Set<Statement> secStmtSet = new HashSet<>();
    for (Statement stmt : fstStmtSet)
      if (stmt.getStatementType() == StatementType.ActualOut)
        secStmtSet.add(stmt);
    
    // pass 2
    stmtQ.addAll(secStmtSet);
    while (!stmtQ.isEmpty()) {
      Statement stmt = stmtQ.pollFirst();
      StatementType stmtType = stmt.getStatementType();
      
      // control edge
      if (stmtType != StatementType.Entry) {
        Statement ctrlStmt = stmt.getControlStatement();
        if (!secStmtSet.contains(ctrlStmt)) {
          secStmtSet.add(ctrlStmt);
          stmtQ.addLast(ctrlStmt);
        }
      }
      
      // summary edge
      if (stmtType == StatementType.ActualOut) {
        Set<Statement> sumStmtSet = stmt.getSummarySet();
        for (Statement sumStmt : sumStmtSet) {
          if (secStmtSet.contains(sumStmt))
            continue;
          secStmtSet.add(sumStmt);
          stmtQ.addLast(sumStmt);
        }
      }
      
      // flow edge and parameter-out edge
      if (stmtType != StatementType.FormalIn) {
        Set<Statement> defStmtSet = stmt.getDefStatementSet();
        for (Statement defStmt : defStmtSet) {
          if (secStmtSet.contains(defStmt))
            continue;
          secStmtSet.add(defStmt);
          stmtQ.addLast(defStmt);
        }
      }
    }
    
    fstStmtSet.addAll(secStmtSet);
    return fstStmtSet;
  }
}
