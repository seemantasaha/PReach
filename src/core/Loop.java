package core;

import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 *
 * @author zzk
 */
public class Loop {
  private Procedure                             procedure = null;
  private ISSABasicBlock                        loopHeader = null;
  private Set<ISSABasicBlock>                   loopBody = new HashSet<>();
  private Set<ISSABasicBlock>                   backNodeSet = new HashSet<>();
  private boolean                               suspicious = true;
  private int                                   loopLevel;
  private int                                   loopNumber;
  private Loop                                  parentLoop = null;
  private Set<Loop>                             nextLevelLoopSet = new HashSet<>();
  private Set<Loop>                             dependenceLoopSet = new HashSet<>();
  private Map<Loop, Set<ArrayList<Procedure>>>  subsidiaryLoopPathSetMap = new HashMap<>();
  private long                                  loopBound;
  
  public Loop(Procedure proc, ISSABasicBlock loopHeader) {
    this.procedure = proc;
    this.loopHeader = loopHeader;
    expandLoopBody(loopHeader);
  }
  
  final public boolean isInLoopBody(ISSABasicBlock node) {
    return this.loopBody.contains(node);
  }
  
  final public boolean isSubsidiary(Loop loop) {
    if (this.subsidiaryLoopPathSetMap.containsKey(loop))
      return true;
    
    // IMPORTANT: we assume there are no inter-dependent loops introduced ever!!!
    // OTHERWISE, there can be infinite recursion!
    boolean subFlag = false;
    for (Loop subLoop : this.subsidiaryLoopPathSetMap.keySet())
      subFlag = subFlag || subLoop.isSubsidiary(loop);
    return subFlag;
  }
  
  final public boolean isLoopSuspicious() {
    return this.suspicious;
  }
  
  final public void expandLoopBody(ISSABasicBlock node) {
    this.loopBody.add(node);
  }
  
  final public void labelBackNode(ISSABasicBlock node) {
    if (this.loopBody.contains(node))
      this.backNodeSet.add(node);
  }
  
  final public void addNextLevelLoop(Loop loop) {
    this.nextLevelLoopSet.add(loop);
    loop.parentLoop = this;
    // procedures where depLoop and subLoop locate in are not counted
    ArrayList<Procedure> path = new ArrayList<>();
    loop.addDependenceLoop(this, path);
  }
  
  final public void addDependenceLoop(Loop loop, ArrayList<Procedure> path) {
    this.dependenceLoopSet.add(loop);
    
    Set<ArrayList<Procedure>> subLoopPathSet = loop.subsidiaryLoopPathSetMap.get(this);
    if (subLoopPathSet == null) {
      subLoopPathSet = new HashSet<>();
      loop.subsidiaryLoopPathSetMap.put(this, subLoopPathSet);
    }
    // check whether this path already exists
    boolean insert = true;
    for (ArrayList<Procedure> subLoopPath : subLoopPathSet) {
      if (subLoopPath.equals(path))
        insert = false;
    }
    if (insert)
      subLoopPathSet.add(path);
  }
  
  final public void removeDependenceLoop(Loop loop) {
    this.dependenceLoopSet.remove(loop);
    loop.subsidiaryLoopPathSetMap.remove(this);
  }
  
  final public void propagateLoopLevel(int level) {
    this.loopLevel = level;
    for (Loop nextLevelLoop : this.nextLevelLoopSet)
      nextLevelLoop.propagateLoopLevel(level + 1);
  }
  
  final public void setLoopNumber(int loopNum) {
    this.loopNumber = loopNum;
  }
  
  final public void setLoopBound(long loopBnd) {
    this.loopBound = loopBnd;
  }
  
  final public Procedure getProcedure() {
    return this.procedure;
  }
  
  final public ISSABasicBlock getLoopHeader() {
    return this.loopHeader;
  }
  
  final public Set<ISSABasicBlock> getLoopBody() {
    return this.loopBody;
  }
  
  final public Set<ISSABasicBlock> getBackNodeSet() {
    return this.backNodeSet;
  }
  
  final public Loop getParentLoop() {
    return this.parentLoop;
  }
  
  final public Set<Loop> getNextLevelLoopSet() {
    return this.nextLevelLoopSet;
  }
  
  final public Set<Loop> getDependenceLoopSet() {
    return this.dependenceLoopSet;
  }
  
  final public Set<Loop> getSubsidiaryLoopSet() {
    return this.subsidiaryLoopPathSetMap.keySet();
  }
  
  final public Set<ArrayList<Procedure>> getSubsidiaryLoopPathSet(Loop loop) {
    Set<ArrayList<Procedure>> subLoopPathSet = this.subsidiaryLoopPathSetMap.get(loop);
    if (subLoopPathSet != null)
      return subLoopPathSet;
    else
      return new HashSet<>();
  }
  
  final public int getLoopLevel() {
    return this.loopLevel;
  }
  
  final public int getLoopNumber() {
    return this.loopNumber;
  }
  
  final public long getLoopBound() {
    return this.loopBound;
  }
  
  final public String getLoopIdentifier() {
    String id = "";
    Stack<Loop> loopStk = new Stack<>();
    Loop ancesterLoop = this.parentLoop;
    while (ancesterLoop != null) {
      loopStk.push(ancesterLoop);
      ancesterLoop = ancesterLoop.parentLoop;
    }
    
    while (!loopStk.empty()) {
      ancesterLoop = loopStk.pop();
      id += "L" + ancesterLoop.getLoopNumber() + "_";
    }
    id += "L" + this.loopNumber;
    
    return id;
  }
  
  //----------------------------------------------------------------------------
  private boolean stayInLoop(ISSABasicBlock node, Set<ISSABasicBlock> flagSet) {
    flagSet.add(node);
    
    Set<ISSABasicBlock> postDomSet = this.procedure.getPostDominatorSet(node);
    postDomSet.remove(node);
    postDomSet.retainAll(this.loopBody);
    // if there is some guy in the loop post-dominates this node, this node will not lead to an exit
    if (!postDomSet.isEmpty())
      return true;
    
    // because it's in a loop, any node should have some successors
    Collection<ISSABasicBlock> succNodeSet = this.procedure.getCFG().getNormalSuccessors(node);    
    for (ISSABasicBlock succNode : succNodeSet) {
      if (flagSet.contains(succNode))
        continue;
      if (!loopBody.contains(succNode))
        return false;
      if (!stayInLoop(succNode, flagSet))
        return false;
    }
    
    return true;
  }
  
  final public Set<SSAInstruction> getLoopExitConditionSet() {
    Set<SSAInstruction> loopExitCondSet = new HashSet<>();
    
    // use post-dominators to find whether a node controls a border-gate of the loop
    for (ISSABasicBlock node : this.loopBody) {
      Set<ISSABasicBlock> flagSet = new HashSet<>();
      if (stayInLoop(node, flagSet))
        continue;
      
      if (node.getLastInstructionIndex() < 0)
        continue;
      SSAInstruction lastSSAInst = node.getLastInstruction();
      if (lastSSAInst instanceof SSAConditionalBranchInstruction)
        loopExitCondSet.add(lastSSAInst);
    }
    
    return loopExitCondSet;
  }
  
  //----------------------------------------------------------------------------
  private Set<Statement> getReachableStatementsContainedInLoopBody() {
    Set<Statement> stmtSet = new HashSet<>();    
    ProcedureDependenceGraph procDepGraph = ProgramDependenceGraph.getProcedureDependenceGraph(this.procedure);
    for (ISSABasicBlock node : this.loopBody) {
      // loop body self
      for (SSAInstruction inst : node) {
        Statement stmt = procDepGraph.requireInstructionStatement(inst);
        stmtSet.add(stmt);
        if (inst instanceof SSAInvokeInstruction) {
          SSAInvokeInstruction invokeInst = (SSAInvokeInstruction)inst;
          stmtSet.addAll(procDepGraph.getActualInStatementSet(invokeInst));
          stmtSet.addAll(procDepGraph.getActualOutStatementSet(invokeInst));
        }
      }
      // reachable callees inside the loop body
      Set<Procedure> calleeSet = this.procedure.getReachableCalleeSet(node);
      for (Procedure callee : calleeSet) {
        ProcedureDependenceGraph calleeDepGraph = ProgramDependenceGraph.getProcedureDependenceGraph(callee);
        if (calleeDepGraph == null)
          continue;
        stmtSet.addAll(calleeDepGraph.getFormalInStatementSet());
        stmtSet.addAll(calleeDepGraph.getFormalOutStatementSet());
        stmtSet.addAll(calleeDepGraph.getInstructionStatementSet());
        Set<SSAInvokeInstruction> invokeInstSet = calleeDepGraph.getInvokeInstructionSet();
        for (SSAInvokeInstruction invokeInst : invokeInstSet) {
          stmtSet.addAll(calleeDepGraph.getActualInStatementSet(invokeInst));
          stmtSet.addAll(calleeDepGraph.getActualOutStatementSet(invokeInst));
        }
      }
    }
    return stmtSet;
  }
  
  private Set<Statement> getDataDependentStatements(Statement stmt, Set<Statement> withinSet) {
    Set<Statement> stmtSet = new HashSet<>();
    if (!withinSet.contains(stmt))
      return stmtSet;
    stmtSet.add(stmt);
    withinSet.remove(stmt);
    
    Set<Statement> defStmtSet = stmt.getDefStatementSet();
    for (Statement defStmt : defStmtSet) {
      Set<Statement> dataDepStmtSet = getDataDependentStatements(defStmt, withinSet);
      stmtSet.addAll(dataDepStmtSet);
    }
    
    return stmtSet;
  }
  
  final public void checkLoopExitCondition() {
    ProcedureDependenceGraph procDepGraph = ProgramDependenceGraph.getProcedureDependenceGraph(this.procedure);
    if (procDepGraph == null)
      return;
    Set<Statement> stmtSet = getReachableStatementsContainedInLoopBody();
    SymbolTable symTab = this.procedure.getIR().getSymbolTable();
    Set<SSAInstruction> loopExitCondSet = getLoopExitConditionSet();
    for (SSAInstruction loopExitCondInst : loopExitCondSet) {
      for (int i = 0; i < loopExitCondInst.getNumberOfUses(); i++) {
        int use = loopExitCondInst.getUse(i);
        if (symTab.isConstant(use))
          continue;
        SSAInstruction defInst = this.procedure.getDefinitionInstruction(use);
        Statement defInstStmt = procDepGraph.requireInstructionStatement(defInst);
        Set<Statement> withinSet = new HashSet<>(stmtSet);
        Set<Statement> dataDepStmtSet = getDataDependentStatements(defInstStmt, withinSet);
        for (Statement dataDepStmt : dataDepStmtSet) {
          if (dataDepStmt.getStatementType() != StatementType.Instruction)
            continue;
          SSAInstruction inst = (SSAInstruction)dataDepStmt.getContent();
          
          // some object is modified, and defInst is data dependent on this
          if (inst instanceof SSAPutInstruction)
            return;
          
          if (inst instanceof SSAInvokeInstruction) {
            SSAInvokeInstruction invokeInst = (SSAInvokeInstruction)inst;
            MethodReference mthRef = invokeInst.getDeclaredTarget();
            TypeReference clsRef = mthRef.getDeclaringClass();
            String procSig = clsRef.getName().toString() + "." + mthRef.getSelector().toString();
            if (Program.isApplicationMethodCalled(mthRef))
              continue;
            if (!invokeInst.isStatic() && LibrarySummary.isSelfModified(procSig))
              return;
          }
        }
      }
    }
    this.suspicious = false;
  }
}
