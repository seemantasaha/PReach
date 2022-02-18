package core;

import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 *
 * @author zzk
 */
public class Statement {
  private StatementType             statementType = null;
  private Object                    content = null;
  private ISSABasicBlock            controller = null;
  private ProcedureDependenceGraph  owner = null;
  
  private Set<Statement>            useStatementSet = new HashSet<>();
  private Set<Statement>            defStatementSet = new HashSet<>();
  private Set<Statement>            callSet = new HashSet<>();
  private Set<Statement>            summarySet = new HashSet<>();
  
  public Statement(StatementType type, Object content, ISSABasicBlock controller, ProcedureDependenceGraph owner) {
    this.statementType = type;
    this.content = content;
    this.controller = controller;
    this.owner = owner;
  }
  
  final public StatementType getStatementType() {
    return this.statementType;
  }
  
  final public Object getContent() {
    return this.content;
  }
  
  final public ISSABasicBlock getController() {
    return this.controller;
  }
  
  final public ProcedureDependenceGraph getOwner() {
    return this.owner;
  }
  
  final public Statement getControlStatement() {
    if (this.controller == this.owner.getProcedure().getCFG().entry())
      return this.owner.getEntryStatement();
    if (this.controller.getLastInstructionIndex() < 0)
      return this.owner.getEntryStatement();
    SSAInstruction lastInst = this.controller.getLastInstruction();
    return this.owner.requireInstructionStatement(lastInst);
  }
  
  final public Set<Statement> getSlaveStatementSet() {
    Set<Statement> slaveStmtSet = new HashSet<>();
    if (this.owner == null)
      return slaveStmtSet;
    
    if (this.statementType == StatementType.Entry) {
      slaveStmtSet.addAll(this.owner.getFormalInStatementSet());
      slaveStmtSet.addAll(this.owner.getFormalOutStatementSet());
      Set<ISSABasicBlock> depNodeSet = this.owner.getBaseLevelNodeSet();
      for (ISSABasicBlock depNode : depNodeSet) {
        Iterator<SSAInstruction> depInstIter = depNode.iterator();
        while (depInstIter.hasNext()) {
          SSAInstruction depInst = depInstIter.next();
          Statement stmt = this.owner.requireInstructionStatement(depInst);
          slaveStmtSet.add(stmt);
        }
      }
    } else if (this.statementType == StatementType.Instruction && this.content != null) {
      SSAInstruction inst = (SSAInstruction)this.content;
      if (inst instanceof SSAInvokeInstruction) {
        SSAInvokeInstruction invokeInst = (SSAInvokeInstruction)inst;
        slaveStmtSet.addAll(this.owner.getActualInStatementSet(invokeInst));
        slaveStmtSet.addAll(this.owner.getActualOutStatementSet(invokeInst));
      }
      
      ISSABasicBlock node = this.owner.getProcedure().getNode(inst);
      if (inst == node.getLastInstruction()) {
        List<Set<ISSABasicBlock>> depNodeSetList = this.owner.getControlDependentNodeSetList(node);
        for (Set<ISSABasicBlock> depNodeSet : depNodeSetList) {
          for (ISSABasicBlock depNode : depNodeSet) {
            Iterator<SSAInstruction> depInstIter = depNode.iterator();
            while (depInstIter.hasNext()) {
              SSAInstruction depInst = depInstIter.next();
              Statement stmt = this.owner.requireInstructionStatement(depInst);
              slaveStmtSet.add(stmt);
            }
          }
        }
      }
    }
    
    return slaveStmtSet;
  }
  
  final public void flowDataTo(Statement stmt) {
    //System.out.println("From : " + this.getContent() + "   To : " + stmt.getContent());
    this.useStatementSet.add(stmt);
    stmt.defStatementSet.add(this);
  }
  
  final public Set<Statement> getUseStatementSet() {
    return this.useStatementSet;
  }
  
  final public Set<Statement> getDefStatementSet() {
    return this.defStatementSet;
  }
  
  final public void addInvocation(Statement stmt) {
    this.callSet.add(stmt);
    stmt.callSet.add(this);
  }
  
  final public Set<Statement> getCallSet() {
    return this.callSet;
  }
  
  final public void addSummary(Statement stmt) {
    this.summarySet.add(stmt);
    stmt.summarySet.add(this);
  }
  
  final public Set<Statement> getSummarySet() {
    return this.summarySet;
  }
}
