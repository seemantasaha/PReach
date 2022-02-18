package cmd;

import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import core.Procedure;
import core.ProcedureDependenceGraph;
import core.ProgramDependenceGraph;
import core.Reporter;
import core.Statement;
import core.StatementType;
import java.util.Set;

/**
 *
 * @author zzk
 */
public class PDG extends BaseGraph<Statement> {
  private Procedure procedure;
  
  public PDG(Procedure proc) {
    this.procedure = proc;
    
    ProcedureDependenceGraph procDepGraph = ProgramDependenceGraph.getProcedureDependenceGraph(proc);
    
    // formal-in
    Set<Statement> formalInStmtSet = procDepGraph.getFormalInStatementSet();
    for (Statement formalInStmt : formalInStmtSet) {
      if (getVertex(formalInStmt) == null)
        constructStatementVertex(formalInStmt);
      colorVertex(formalInStmt, "yellow");
      
      Set<Statement> useStmtSet = formalInStmt.getUseStatementSet();
      for (Statement useStmt : useStmtSet) {
        if (getVertex(useStmt) == null)
          constructStatementVertex(useStmt);
        if (getEdge(formalInStmt, useStmt) == null)
          addEdge(formalInStmt, useStmt, null);
      }
      
      Statement ctrlStmt = formalInStmt.getControlStatement();
      if (getVertex(ctrlStmt) == null)
        constructStatementVertex(ctrlStmt);
      addDash(ctrlStmt, formalInStmt, null);
    }
    
    // formal-out
    Set<Statement> formalOutStmtSet = procDepGraph.getFormalOutStatementSet();
    for (Statement formalOutStmt : formalOutStmtSet) {
      if (getVertex(formalOutStmt) == null)
        constructStatementVertex(formalOutStmt);
      colorVertex(formalOutStmt, "red");
      
      Set<Statement> defStmtSet = formalOutStmt.getDefStatementSet();
      for (Statement defStmt : defStmtSet) {
        if (getVertex(defStmt) == null)
          constructStatementVertex(defStmt);
        if (getEdge(defStmt, formalOutStmt) == null)
          addEdge(defStmt, formalOutStmt, null);
      }
      
      Statement ctrlStmt = formalOutStmt.getControlStatement();
      if (getVertex(ctrlStmt) == null)
        constructStatementVertex(ctrlStmt);
      addDash(ctrlStmt, formalOutStmt, null);
    }
    
    Set<SSAInvokeInstruction> invokeInstSet = procDepGraph.getInvokeInstructionSet();
    for (SSAInvokeInstruction invokeInst : invokeInstSet) {
      // actual-in
      Set<Statement> actualInStmtSet = procDepGraph.getActualInStatementSet(invokeInst);
      for (Statement actualInStmt : actualInStmtSet) {
        if (getVertex(actualInStmt) == null)
          constructStatementVertex(actualInStmt);
        colorVertex(actualInStmt, "orange");
        
        Set<Statement> defStmtSet = actualInStmt.getDefStatementSet();
        for (Statement defStmt : defStmtSet) {
          if (getVertex(defStmt) == null)
            constructStatementVertex(defStmt);
          if (getEdge(defStmt, actualInStmt) == null)
            addEdge(defStmt, actualInStmt, null);
        }
        
        Statement ctrlStmt = actualInStmt.getControlStatement();
        if (getVertex(ctrlStmt) == null)
          constructStatementVertex(ctrlStmt);
        addDash(ctrlStmt, actualInStmt, null);
      }
      
      // actual-out
      Set<Statement> actualOutStmtSet = procDepGraph.getActualOutStatementSet(invokeInst);
      for (Statement actualOutStmt : actualOutStmtSet) {
        if (getVertex(actualOutStmt) == null)
          constructStatementVertex(actualOutStmt);
        colorVertex(actualOutStmt, "pink");
        
        Set<Statement> useStmtSet = actualOutStmt.getUseStatementSet();
        for (Statement useStmt : useStmtSet) {
          if (getVertex(useStmt) == null)
            constructStatementVertex(useStmt);
          if (getEdge(actualOutStmt, useStmt) == null)
            addEdge(actualOutStmt, useStmt, null);
        }
        
        Statement ctrlStmt = actualOutStmt.getControlStatement();
        if (getVertex(ctrlStmt) == null)
          constructStatementVertex(ctrlStmt);
        addDash(ctrlStmt, actualOutStmt, null);
      }
    }
    
    // instruction
    Set<Statement> instStmtSet = procDepGraph.getInstructionStatementSet();
    for (Statement instStmt : instStmtSet) {
      if (getVertex(instStmt) == null)
        constructStatementVertex(instStmt);
      
      Set<Statement> useStmtSet = instStmt.getUseStatementSet();
      for (Statement useStmt : useStmtSet) {
        if (getVertex(useStmt) == null)
          constructStatementVertex(useStmt);
        if (getEdge(instStmt, useStmt) == null)
          addEdge(instStmt, useStmt, null);
      }
      
      Statement ctrlStmt = instStmt.getControlStatement();
      if (getVertex(ctrlStmt) == null)
        constructStatementVertex(ctrlStmt);
      addDash(ctrlStmt, instStmt, null);
    }
    
    layoutGraph();    
  }
  
  private void constructStatementVertex(Statement stmt) {
    StatementType stmtType = stmt.getStatementType();
    if (stmtType == StatementType.Entry) {
      String str = "ENTRY";
      addVertex(stmt, 1, str);
    } else if (stmtType == StatementType.Instruction) {
      SSAInstruction inst = (SSAInstruction)stmt.getContent();
      String str = Reporter.getSSAInstructionString(inst);
      addVertex(stmt, 1, str);
    } else {
      String str = (String)stmt.getContent();
      addVertex(stmt, 1, str);
    }
  }
  
  final public Procedure getProcedure() {
    return this.procedure;
  }
}
