package core;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;

import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 *
 * @author zzk
 */
public class ProcedureDependenceGraph {
  enum InOutModelType {
    Formal,
    Actual
  }
  
  class InOutModel {
    private InOutModelType                  modelType = null;
    private ISSABasicBlock                  controller = null;
    private ProcedureDependenceGraph        owner = null;
    private Map<String, Statement>          inStatementMap = new HashMap<>();
    private Map<String, Statement>          outStatementMap = new HashMap<>();
    private Map<Statement, Set<Statement>>  fieldSetMap = new HashMap<>();
    private Map<Statement, Statement>       objectMap = new HashMap<>();
    
    public InOutModel(InOutModelType type, ISSABasicBlock controller, ProcedureDependenceGraph procDepGraph) {
      this.modelType = type;
      this.controller = controller;
      this.owner = procDepGraph;
    }
    
    final public Statement requireInStatement(String in) {
      Statement stmt = this.inStatementMap.get(in);
      if (stmt == null) {
        if (this.modelType == InOutModelType.Formal)
          stmt = new Statement(StatementType.FormalIn, in, this.controller, this.owner);
        else
          stmt = new Statement(StatementType.ActualIn, in, this.controller, this.owner);
        this.inStatementMap.put(in, stmt);
      }
      
      // if this statment corresponds to a field in an object, and has been met before
      if (this.objectMap.containsKey(stmt))
        return stmt;
      
      int lastDotIndex = in.lastIndexOf('.');
      if (lastDotIndex >= 0) {
        String inObj = in.substring(0, lastDotIndex);
        Statement inObjStmt = requireInStatement(inObj);
        
        this.objectMap.put(stmt, inObjStmt);
        
        Set<Statement> fieldSet = this.fieldSetMap.get(inObjStmt);
        if (fieldSet == null) {
          fieldSet = new HashSet<>();
          this.fieldSetMap.put(inObjStmt, fieldSet);
        }
        fieldSet.add(stmt);
      }
      
      return stmt;
    }
    
    final public Statement requireOutStatement(String out) {
      Statement stmt = this.outStatementMap.get(out);
      if (stmt == null) {
        if (this.modelType == InOutModelType.Formal)
          stmt = new Statement(StatementType.FormalOut, out, this.controller, this.owner);
        else
          stmt = new Statement(StatementType.ActualOut, out, this.controller, this.owner);
        this.outStatementMap.put(out, stmt);
      }
      
      // if this statment corresponds to a field in an object, and has been met before
      if (this.objectMap.containsKey(stmt))
        return stmt;
      
      int lastDotIndex = out.lastIndexOf('.');
      if (lastDotIndex >= 0) {
        String outObj = out.substring(0, lastDotIndex);
        Statement outObjStmt = requireOutStatement(outObj);
        
        this.objectMap.put(stmt, outObjStmt);
        
        Set<Statement> fieldSet = this.fieldSetMap.get(outObjStmt);
        if (fieldSet == null) {
          fieldSet = new HashSet<>();
          this.fieldSetMap.put(outObjStmt, fieldSet);
        }
        fieldSet.add(stmt);
      }
      
      return stmt;
    }
    
    final public boolean isIdentical(InOutModel model) {
      if (this.modelType != model.modelType)
        return false;
      if (this.controller != model.controller)
        return false;
      Set<String> thisInSet = this.inStatementMap.keySet();
      Set<String> otherInSet = model.inStatementMap.keySet();
      if (!thisInSet.equals(otherInSet))
        return false;
      Set<String> thisOutSet = this.outStatementMap.keySet();
      Set<String> otherOutSet = model.outStatementMap.keySet();
      if (!thisOutSet.equals(otherOutSet))
        return false;
      return true;
    }
  }
  
  private Procedure                                           procedure = null;
  
  private Map<ISSABasicBlock, List<Set<ISSABasicBlock>>>      controlDependentNodeSetListMap = new HashMap<>();
  private Map<ISSABasicBlock, ISSABasicBlock>                 controlPredicateNodeMap = new HashMap<>();
  
  private InOutModel                                          formalInOutModel = null;
  private Map<SSAInvokeInstruction, InOutModel>               actualInOutModelMap = new HashMap<>();
  
  private Map<SSAFieldAccessInstruction, String>              accessPathMap = new HashMap<>();
  private Map<String, Set<String>>                            accessPathSetMap = new HashMap<>();
  private Map<String, Set<String>>                            fieldAliasSetMap = new HashMap<>();
  
  // it maps a formal-access-path to a map which maps a caller's invocation to a set of actual-access-paths
  private Map<String, Map<SSAInvokeInstruction, Set<String>>> actualAccessPathSetMap = new HashMap<>();
  
  private Statement                                           entryStatement = new Statement(StatementType.Entry, null, null, this);
  private Map<SSAInstruction, Statement>                      instrucionStatementMap = new HashMap<>();
  
  public ProcedureDependenceGraph(Procedure proc) {
    this.procedure = proc;
    this.formalInOutModel = new InOutModel(InOutModelType.Formal, proc.getCFG().entry(), this);
    //constructControlDependenceGraph();
    captureAccessedFields();
    constructDataDependenceGraph();
  }
  
  private void constructControlDependenceGraph() {
    ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = this.procedure.getCFG();    
    for (ISSABasicBlock node : cfg) {
      Set<ISSABasicBlock> postDomSet = this.procedure.getPostDominatorSet(node);
      postDomSet.remove(node);
      
      // since postDomSet removed the current node, if there is a self-loop, it can be captured
      Iterator<ISSABasicBlock> succNodeIter = cfg.getSuccNodes(node);
      while (succNodeIter.hasNext()) {
        ISSABasicBlock succNode = succNodeIter.next();
        if (postDomSet.contains(succNode))
          continue;
        
        // node --> succNode where succNode doesn't post-dominate node
        List<Set<ISSABasicBlock>> ctrlDepNodeSetList = this.controlDependentNodeSetListMap.get(node);
        if (ctrlDepNodeSetList == null) {
          ctrlDepNodeSetList = new LinkedList<>();
          this.controlDependentNodeSetListMap.put(node, ctrlDepNodeSetList);
        }
        
        Set<ISSABasicBlock> ctrlDepNodeSet = new HashSet<>();
        ISSABasicBlock ctrlDepNode = succNode;
        do {
          this.controlPredicateNodeMap.put(ctrlDepNode, node);
          ctrlDepNodeSet.add(ctrlDepNode);
          ctrlDepNode = this.procedure.getImmediatePostDominator(ctrlDepNode);
        } while (ctrlDepNode != null && !postDomSet.contains(ctrlDepNode));
        ctrlDepNodeSetList.add(ctrlDepNodeSet);
      }
    }
    
    // collect all the nodes that will be executed upon the invocation of this procedure
    // since entry only has one edge to 1st node (i.e. it postdominates entry), we are safe to have new ctrlDepNodeSetList for entry 
    ISSABasicBlock entry = cfg.entry();
    Set<ISSABasicBlock> baseLevelNodeSet = this.procedure.getPostDominatorSet(entry);
    baseLevelNodeSet.remove(entry);
    for (ISSABasicBlock baseLevelNode : baseLevelNodeSet)
      this.controlPredicateNodeMap.put(baseLevelNode, entry);
    List<Set<ISSABasicBlock>> ctrlDepNodeSetList = new LinkedList<>();
    this.controlDependentNodeSetListMap.put(entry, ctrlDepNodeSetList);
    Set<ISSABasicBlock> ctrlDepNodeSet = new HashSet<>(baseLevelNodeSet);
    ctrlDepNodeSetList.add(ctrlDepNodeSet);
  }
  
  private String trimAccessPath(String accessPath) {
    String[] tokenArray = accessPath.split("[.]");
    int len = tokenArray.length;
    if (len <= 2)
      return accessPath;
    
    LinkedList<Pair<String, TypeReference>> list = new LinkedList<>();
    for (int i = 0; i < len; i++) {
      String token = tokenArray[i];
      if (i == 0) {
        // let us check whether the first one is a class?
        if (token.startsWith("L")) {
          IClass cls = Program.getClass(token);
          TypeReference typeRef = cls.getReference();
          list.add(Pair.make(token, typeRef));
        } else {
          Scanner in = new Scanner(token).useDelimiter("[^0-9]+");
          int vn = in.nextInt();
          TypeReference typeRef = this.procedure.getTypeReference(vn);
          list.add(Pair.make(token, typeRef));
        }
      } else {
        TypeReference prevTypeRef = list.getLast().snd;
        IClass cls = Program.getClass(prevTypeRef);
        boolean added = false;
        if (cls != null) {
          for (IField field : cls.getAllFields()) {
            String fieldName = field.getName().toString();
            TypeReference fieldTypeRef = field.getFieldTypeReference();
            if (fieldName.equals(token)) {
              boolean toAdd = true;
              for (Pair<String, TypeReference> pair : list) {
                String name = pair.fst;
                TypeReference typeRef = pair.snd;
                if (typeRef == fieldTypeRef && name.equals(token)) {
                  toAdd = false;
                  break;
                }
              }
              if (toAdd) {
                list.add(Pair.make(token, fieldTypeRef));
                added = true;
                break;
              }
            }
          }
        }
        if (!added)
          break;
      }
    }
    
    String newAccessPath = "";
    for (Pair<String, TypeReference> pair : list) {
      if (newAccessPath.length() != 0)
        newAccessPath += ".";
      newAccessPath += pair.fst;
    }
    return newAccessPath;
  }
  
  private void trackFormalActualAccessPathRelation(String formalAccessPath, SSAInvokeInstruction invokeInst, String actualAccessPath) {
    Map<SSAInvokeInstruction, Set<String>> tracker = this.actualAccessPathSetMap.get(formalAccessPath);
    if (tracker == null) {
      tracker = new HashMap<>();
      this.actualAccessPathSetMap.put(formalAccessPath, tracker);
    }
    
    Set<String> actualAccessPathSet = tracker.get(invokeInst);
    if (actualAccessPathSet == null) {
      actualAccessPathSet = new HashSet<>();
      tracker.put(invokeInst, actualAccessPathSet);
    }
    
    actualAccessPathSet.add(actualAccessPath);
  }
  
  private void captureAccessedFields() {
    IR ir = this.procedure.getIR();
    for (int i = 0; i < ir.getNumberOfParameters(); i++) {
      int formalIn = ir.getParameter(i);
      String formalInVar = "v" + formalIn;
      Set<Integer> flagSet = new HashSet<>();
      collectAccessPathSet(formalIn, formalInVar, flagSet);
    }
    
    SSAInstruction[] instArray = ir.getInstructions();
    for (SSAInstruction inst : instArray) {
      if (inst instanceof SSANewInstruction) {
        SSANewInstruction newInst = (SSANewInstruction)inst;
        int def = newInst.getDef();
        String defVar = "v" + def;
        Set<Integer> flagSet = new HashSet<>();
        collectAccessPathSet(def, defVar, flagSet);
      } else if (inst instanceof SSAInvokeInstruction) {
        SSAInvokeInstruction invokeInst = (SSAInvokeInstruction)inst;
        int def = invokeInst.getDef();
        String defVar = "v" + def;
        Set<Integer> flagSet = new HashSet<>();
        collectAccessPathSet(def, defVar, flagSet);
      }
      else if (inst instanceof SSAFieldAccessInstruction) {
        SSAFieldAccessInstruction fieldAccessInst = (SSAFieldAccessInstruction)inst;
        if (!fieldAccessInst.isStatic())
          continue;
        FieldReference field = fieldAccessInst.getDeclaredField();
        String accessPath = field.getDeclaringClass().getName().toString() + "." + field.getName().toString();
        int vn = inst instanceof SSAGetInstruction ? 
            ((SSAGetInstruction)inst).getDef() : 
            ((SSAPutInstruction)inst).getVal();
        Set<Integer> flagSet = new HashSet<>();
        // keep accessPath of this field access instruction
        this.accessPathMap.put(fieldAccessInst, accessPath);
        collectAccessPathSet(vn, accessPath, flagSet);
      }
    }
  }
  
  private void collectAccessPathSet(int vn, String accessPath, Set<Integer> flagSet) {
    if (vn > 0) {
      if (flagSet.contains(vn))
        return;
      flagSet.add(vn);
    }
    
    if (accessPath.contains(".")) {
      accessPath = trimAccessPath(accessPath);
      
      String var = "v" + vn;
      
      Set<String> accessPathSet = this.accessPathSetMap.get(var);
      if (accessPathSet == null) {
        accessPathSet = new HashSet<>();
        this.accessPathSetMap.put(var, accessPathSet);
      }
      accessPathSet.add(accessPath);
      
      Set<String> fieldAliasSet = this.fieldAliasSetMap.get(accessPath);
      if (fieldAliasSet == null) {
        fieldAliasSet = new HashSet<>();
        this.fieldAliasSetMap.put(accessPath, fieldAliasSet);
      }
      fieldAliasSet.add(var);
    }
    
    if (vn <= 0)
      return;
    
    Set<SSAInstruction> useInstSet = this.procedure.getUseInstructionSet(vn);
    for (SSAInstruction useInst : useInstSet) {
      if (useInst instanceof SSAPhiInstruction) {
        SSAPhiInstruction phiInst = (SSAPhiInstruction)useInst;
        int def = phiInst.getDef();
        collectAccessPathSet(def, accessPath, flagSet);
      } else if (useInst instanceof SSACheckCastInstruction) {
        SSACheckCastInstruction castInst = (SSACheckCastInstruction)useInst;
        int def = castInst.getDef();
        collectAccessPathSet(def, accessPath, flagSet);
      } else if (useInst instanceof SSAInvokeInstruction) {
        SSAInvokeInstruction invokeInst = (SSAInvokeInstruction)useInst;
        ISSABasicBlock callNode = this.procedure.getNode(useInst);
        Set<Procedure> calleeSet = this.procedure.getCalleeSet(callNode);
        for (Procedure callee : calleeSet) {
          ProcedureDependenceGraph calleeDepGraph = ProgramDependenceGraph.getProcedureDependenceGraph(callee);
          if (calleeDepGraph == null)
            continue;
          for (int i = 0; i < invokeInst.getNumberOfUses(); i++) {
            int actualIn = invokeInst.getUse(i);
            if (actualIn != vn)
              continue;
            String formalInVar = "v" + (i + 1);
            Set<String> formalInAccessPathSet = calleeDepGraph.getAccessPathSetStartingWith(formalInVar);
            for (String formalInAccessPath : formalInAccessPathSet) {
              int fstDot = formalInAccessPath.indexOf('.');
              String formalInAccessPathTrail = formalInAccessPath.substring(fstDot);
              String actualInAccessPath = accessPath + formalInAccessPathTrail;
              calleeDepGraph.trackFormalActualAccessPathRelation(formalInAccessPath, invokeInst, actualInAccessPath);
              collectAccessPathSet(0, actualInAccessPath, flagSet);
            }
          }
        }
      } else if (useInst instanceof SSAFieldAccessInstruction) {
        SSAFieldAccessInstruction fieldAccessInst = (SSAFieldAccessInstruction)useInst;
        int ref = fieldAccessInst.getRef();
        if (ref != vn)
          continue;
        FieldReference field = fieldAccessInst.getDeclaredField();
        String newAccessPath = accessPath + "." + field.getName().toString();
        int newVn = useInst instanceof SSAGetInstruction ? 
            ((SSAGetInstruction)useInst).getDef() : 
            ((SSAPutInstruction)useInst).getVal();
        // keep accessPath of this field access instruction
        this.accessPathMap.put(fieldAccessInst, newAccessPath);
        collectAccessPathSet(newVn, newAccessPath, flagSet);
      }
    }
  }
  
  private Set<String> getAccessPathSetStartingWith(String var) {
    Set<String> accessPathSet = new HashSet<>();
    String partPath = var + ".";
    for (String accessPath : this.fieldAliasSetMap.keySet())
      if (accessPath.startsWith(partPath))
        accessPathSet.add(accessPath);
    return accessPathSet;
  }
  
  private InOutModel requireActualInOutModel(SSAInvokeInstruction invokeInst) {
    InOutModel inOutModel = this.actualInOutModelMap.get(invokeInst);
    if (inOutModel == null) {
      ISSABasicBlock callNode = this.procedure.getNode(invokeInst);
      inOutModel = new InOutModel(InOutModelType.Actual, callNode, this);
      this.actualInOutModelMap.put(invokeInst, inOutModel);
    }
    return inOutModel;
  }
  
  private void constructDataDependenceGraph() {
    ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = this.procedure.getCFG();
    ISSABasicBlock entry = cfg.entry();
    ISSABasicBlock exit = cfg.exit();
      
    //System.out.println("Procedure Name and CLass : " + this.getProcedure().getProcedureName() + " " + this.getProcedure().getClassName());
    
    Map<ISSABasicBlock, Map<String, Set<Statement>>> reachDef = new HashMap<>();
    Set<SSAReturnInstruction> retInstSet = new HashSet<>();
    
    // initialize formal-in parameter statements
    Map<String, Set<Statement>> entryDefStmtSetMap = new HashMap<>();
    IR ir = this.procedure.getIR();
    for (int i = 0; i < ir.getNumberOfParameters(); i++) {
      String formalInVar = "v" + ir.getParameter(i);
      Statement formalInVarStmt = this.formalInOutModel.requireInStatement(formalInVar);
      newDefinition(entryDefStmtSetMap, formalInVar, formalInVarStmt);
      
      Set<String> formalInAccessPathSet = getAccessPathSetStartingWith(formalInVar);
      for (String formalInAccessPath : formalInAccessPathSet) {
        Statement formalInAccessPathStmt = this.formalInOutModel.requireInStatement(formalInAccessPath);
        newDefinition(entryDefStmtSetMap, formalInAccessPath, formalInAccessPathStmt);
      }
    }
    for (String accessPath : this.fieldAliasSetMap.keySet())
      if (accessPath.startsWith("L")) {
        Statement formalInAccessPathStmt = this.formalInOutModel.requireInStatement(accessPath);
        newDefinition(entryDefStmtSetMap, accessPath, formalInAccessPathStmt);
      }
    reachDef.put(entry, entryDefStmtSetMap);
    
    // since entry's defStmtSetMap is fixed, we don't care the entry
    Deque<ISSABasicBlock> worklist = new LinkedList<>(this.procedure.getNodeListForward());
    worklist.pollFirst();
    //Deque<ISSABasicBlock> worklist = new LinkedList<>();
    //Iterator<ISSABasicBlock> entrySuccIter = cfg.getSuccNodes(entry);
    //while (entrySuccIter.hasNext())
      //worklist.add(entrySuccIter.next());
    
    while (!worklist.isEmpty()) {
      ISSABasicBlock node = worklist.pollFirst();
      
      // combine the def-set maps of predecessors
      Iterator<ISSABasicBlock> predNodeIter = cfg.getPredNodes(node);
      while (predNodeIter.hasNext()) {
        ISSABasicBlock predNode = predNodeIter.next();
        Map<String, Set<Statement>> predDefStmtSetMap = reachDef.get(predNode);
        if (predDefStmtSetMap == null)
          continue;
        for (Map.Entry<String, Set<Statement>> predDefSetMapEnt : predDefStmtSetMap.entrySet()) {
          String var = predDefSetMapEnt.getKey();
          Set<Statement> predDefStmtSet = predDefSetMapEnt.getValue();
          Set<Statement> defStmtSet = this.procedure.defStmtSetMap.get(var);
          if (defStmtSet == null) {
            defStmtSet = new HashSet<>();
            this.procedure.defStmtSetMap.put(var, defStmtSet);
          }
          defStmtSet.addAll(predDefStmtSet);
        }
      }
      
      // analyze this node
      for (SSAInstruction inst : node) {
        Statement instStmt = requireInstructionStatement(inst);
        //System.out.println(this.getProcedure().getProcedureName() + " Instruction Statement : " + instStmt.getContent());
        //boolean flagObjModified = false;
        if (inst instanceof SSAInvokeInstruction) {
          SSAInvokeInstruction invokeInst = (SSAInvokeInstruction)inst;
          MethodReference mthRef = invokeInst.getDeclaredTarget();
          TypeReference clsRef = mthRef.getDeclaringClass();
          String procSig = clsRef.getName().toString() + "." + mthRef.getSelector().toString();
          Procedure callee = Program.getProcedure(procSig);
          //if (Program.isApplicationMethodCalled(mthRef)) {
          if (callee != null) {
            ProcedureDependenceGraph calleeDepGraph = ProgramDependenceGraph.getProcedureDependenceGraph(callee);
            if (calleeDepGraph != null) {
              Statement calleeEntryStmt = calleeDepGraph.getEntryStatement();
              instStmt.addInvocation(calleeEntryStmt);
              
              InOutModel actualInOutModel = requireActualInOutModel(invokeInst);

              //System.out.println("Procedure Name : " + this.getProcedure().getProcedureName());
              //System.out.println("Instruction Statement : " + instStmt.getContent());
              // deal with IN
              Set<Statement> formalInStmtSet = calleeDepGraph.getFormalInStatementSet();
              for (Statement formalInStmt : formalInStmtSet) {
                String content = (String)formalInStmt.getContent();
                //System.out.println("IN content : " + content);
                // let actual-in-argument have its corresponding formal-in-parameter name!!!!
                Statement actualInStmt = actualInOutModel.requireInStatement(content);
                //System.out.println("actualInStmt : " + actualInStmt.getContent());
                //System.out.println("From : " + actualInStmt.getContent() + "    To : " + formalInStmt.getContent());
                //actualInStmt.flowDataTo(formalInStmt);
                
                if (!content.contains(".") && !content.startsWith("L")) {
                  Scanner in = new Scanner(content).useDelimiter("[^0-9]+");
                  int formalIn = in.nextInt();
                  int actualIn = invokeInst.getUse(formalIn - 1);
                  String actualInVar = "v" + actualIn;
                  Set<Statement> defStmtSet = getDefinitionStatementSet(this.procedure.defStmtSetMap, actualInVar);
                  for (Statement defStmt : defStmtSet) {
                    //System.out.println("From (if) : " + defStmt.getContent() + "    To : " + actualInStmt.getContent());
                    //defStmt.flowDataTo(actualInStmt);
                  }
                } else if (content.contains(".") && !content.startsWith("L")) {
                  Set<String> actualInAccessPathSet = calleeDepGraph.getActualAccessPathSet(content, invokeInst);
                  for (String actualInAccessPath : actualInAccessPathSet) {
                    //System.out.println("actualInAccessPath : " + actualInAccessPath);
                    //newDefinition(this.procedure.defStmtSetMap, actualInAccessPath, instStmt);
                    //Set<Statement> defStmtSet = getDefinitionStatementSet(this.procedure.defStmtSetMap, actualInAccessPath);
                    //for (Statement defStmt : defStmtSet) {
                      //System.out.println("From (else if 1) : " + defStmt.getContent() + "    To : " + actualInStmt.getContent());
                      //defStmt.flowDataTo(actualInStmt);
                      //System.out.println("From : " + instStmt.getContent() + "    To : " + actualInStmt.getContent());
                      //instStmt.flowDataTo(actualInStmt);
                    //}
                  }
                } else if (content.contains(".") && content.startsWith("L")) {
                  Set<Statement> defStmtSet = getDefinitionStatementSet(callee.defStmtSetMap, content);
                  for (Statement defStmt : defStmtSet) {
                    //System.out.println("From (else if 2) : " + defStmt.getContent() + "    To : " + actualInStmt.getContent());
                    //defStmt.flowDataTo(actualInStmt);
                  }
                }
              }
              
              
              
              // deal with OUT
              Set<Statement> formalOutStmtSet = calleeDepGraph.getFormalOutStatementSet();
              for (Statement formalOutStmt : formalOutStmtSet) {
                String content = (String)formalOutStmt.getContent();
                // let actual-out-argument have its corresponding formal-out-parameter name!!!!
                Statement actualOutStmt = actualInOutModel.requireOutStatement(content);
                //System.out.println("From : " + formalOutStmt.getContent() + "    To : " + actualOutStmt.getContent());
                //formalOutStmt.flowDataTo(actualOutStmt);
                //System.out.println("OUT content : " + content);
                if (!content.contains(".") && !content.startsWith("L")) {
                  Scanner in = new Scanner(content).useDelimiter("[^0-9]+");
                  int formalOut = in.nextInt();
                  int actualOut = formalOut == 0 ? invokeInst.getReturnValue(0) : invokeInst.getUse(formalOut - 1);
                  if (actualOut > 0) {
                    String actualOutVar = "v" + actualOut;
                    //if (actualOutStmt.getContent().equals("v0")) {
                      //System.out.println("New Definition(if) var : " + actualOutVar + "    Statement : " + actualOutStmt.getContent());
                      //flagObjModified = true;
                      //newDefinition(callee.defStmtSetMap, actualOutVar, actualOutStmt);
                      if (instStmt.getContent().toString().contains(" > ")) {
                        String requiredObject = "v" + instStmt.getContent().toString().split(" > ")[1].split(",")[0];
                        //System.out.println("requiredObject : " + requiredObject);
                        if (!requiredObject.equals("v1")) {
                            Set<Statement> defStmtSet = getDefinitionStatementSet(this.procedure.defStmtSetMap, requiredObject);
                            for (Statement defStmt : defStmtSet) {
                              //System.out.println("From : " + instStmt.getContent() + "    To : " + defStmt.getContent());
                              instStmt.flowDataTo(defStmt);
                            }
                        }
                      }
                    //}
                  }
                } else if (content.contains(".") && !content.startsWith("L")) {
                  Set<String> actualOutAccessPathSet = calleeDepGraph.getActualAccessPathSet(content, invokeInst);
                  for (String actualOutAccessPath : actualOutAccessPathSet) {
                    //System.out.println("New Definition(else if 1) access path : " + actualOutAccessPath + "    Statement : " + actualOutStmt.getContent());
                    //newDefinition(callee.defStmtSetMap, actualOutAccessPath, actualOutStmt);
                    //System.out.println("actualOutAccessPath : " + actualOutAccessPath);
                    //System.out.println("New Definition : " + actualOutAccessPath + "    Statement : " + instStmt.getContent());
                    newDefinition(this.procedure.defStmtSetMap, actualOutAccessPath, instStmt);
                  }
                } else if (content.contains(".") && content.startsWith("L")) {
                  //System.out.println("New Definition(else if 2) content : " + content + "    Statement : " + actualOutStmt.getContent());
                  //newDefinition(callee.defStmtSetMap, content, actualOutStmt);
                }
              }
               
              for (int i = 0; i < inst.getNumberOfUses(); i++) {
                if (inst.getUse(i) == 1) continue; // to get rid of flowing data to "this" object
                String useVar = "v" + inst.getUse(i);
                //System.out.println("Used Variable : " + useVar);
                Set<Statement> defStmtSet = getDefinitionStatementSet(this.procedure.defStmtSetMap, useVar);
                for (Statement defStmt : defStmtSet) {
                  //if (defStmt.getControlStatement().getContent() != null)
                  //System.out.println("SSAInvokeInstruction Other 1");
                  //System.out.println("From : " + defStmt.getContent() + "    To : " + instStmt.getContent());
                  defStmt.flowDataTo(instStmt);
                }
              }
              // to setup definition statement
              if (inst.hasDef()) {
                int def = inst.getDef();
                String defVar = "v" + def;
                //System.out.println("newDefinition : " + defVar + " for " + instStmt.getContent());
                newDefinition(this.procedure.defStmtSetMap, defVar, instStmt);
              }
            }
            else {
                //System.out.println("callee dependency graph is null");
            }
          } else {
            
            //System.out.println("callee is null");  
            
            for (int i = 0; i < inst.getNumberOfUses(); i++) {
              if (inst.getUse(i) == 1) continue; // to get rid of flowing data to "this" object
              String useVar = "v" + inst.getUse(i);
              //System.out.println("useVar : " + useVar);
              Set<Statement> defStmtSet = getDefinitionStatementSet(this.procedure.defStmtSetMap, useVar);
              for (Statement defStmt : defStmtSet) {
                //System.out.println("From : " + defStmt.getContent() + "    To : " + instStmt.getContent());
                defStmt.flowDataTo(instStmt);
              }
            }
            
            if (inst.hasDef()) {
              int def = inst.getDef();
              String defVar = "v" + def;
              //System.out.println("new definition for: " + defVar + "statement : " + instStmt);
              newDefinition(this.procedure.defStmtSetMap, defVar, instStmt);
            }
            
            LibrarySummary.checkExistence(procSig);
            if (!invokeInst.isStatic() && LibrarySummary.isSelfModified(procSig)) {
              int self = invokeInst.getReceiver();
              Set<String> varSet = getReachingAliasSet(this.procedure.defStmtSetMap, self);
              for (String var : varSet){
                //System.out.println("modifyWholeObject var 1: " + var + "statement : " + instStmt);
                modifyWholeObject(this.procedure.defStmtSetMap, var, instStmt);
              }
            }
            /*int i = invokeInst.isStatic() ? 0 : 1;
            int j = 0;
            while (i < invokeInst.getNumberOfUses()) {
              int param = invokeInst.getUse(i++);
              TypeReference typeRef = this.procedure.getTypeReference(param);
              if (LibrarySummary.isParameterModified(procSig, j++) && typeRef != null && typeRef.isReferenceType()) {
                Set<String> varSet = getReachingAliasSet(this.procedure.defStmtSetMap, param);
                for (String var : varSet) {
                  System.out.println("modifyWholeObject var 2: " + var);
                  modifyWholeObject(this.procedure.defStmtSetMap, var, instStmt);
                }
              }
            }*/
          }
        } else {
          // establist use-def & def-use chains for instructions other than SSAInvokeInstruction
          for (int i = 0; i < inst.getNumberOfUses(); i++) {
            String useVar = "v" + inst.getUse(i);
            //System.out.println("Used variable : " + useVar);
            //if(useVar.equals("v1"))
            //    continue;
            Set<Statement> defStmtSet = getDefinitionStatementSet(this.procedure.defStmtSetMap, useVar);
            for (Statement defStmt : defStmtSet) {
              //System.out.println("From : " + defStmt.getContent() + "    To : " + instStmt.getContent());
              defStmt.flowDataTo(instStmt);
            }
            
            if (inst instanceof SSAPutInstruction) {
                String accessPath = this.accessPathMap.get((SSAFieldAccessInstruction)inst);
                String accessObject = null;
                if (accessPath != null) {
                    //System.out.println("accessPath for SSAPutInstruction: " + accessPath);
                    if (accessPath.contains(".")) {
                        accessObject = accessPath.split("\\.")[0];
                    }
                    
                    if (accessObject.equals(useVar)) {
                        Set<Statement> defStmtSetForAccessObject = getDefinitionStatementSet(this.procedure.defStmtSetMap, accessObject);
                        for (Statement defAOStmt : defStmtSetForAccessObject) {
                            //System.out.println("From : " + instStmt.getContent() + "    To : " + defAOStmt.getContent());
                            instStmt.flowDataTo(defAOStmt);
                        }
                        continue;
                    }
                    
                    Set<Statement> defStmtSetForAccessPath = getDefinitionStatementSet(this.procedure.defStmtSetMap, accessPath);
                    for (Statement defAPStmt : defStmtSetForAccessPath) {
                        for (Statement defStmt : defStmtSet) {
                            //System.out.println("From : " + defStmt.getContent() + "    To : " + defAPStmt.getContent());
                            defStmt.flowDataTo(defAPStmt);
                        }
                    }
                    if (accessObject != null) {
                        Set<Statement> defStmtSetForAccessObject = getDefinitionStatementSet(this.procedure.defStmtSetMap, accessObject);
                        for (Statement defAOStmt : defStmtSetForAccessObject) {
                            for (Statement defStmt : defStmtSet) {
                                //System.out.println("From : " + defStmt.getContent() + "    To : " + defAOStmt.getContent());
                                defStmt.flowDataTo(defAOStmt);
                            }
                        }
                    }
                }
            }
          }
          
          if (inst.hasDef()) {
            int def = inst.getDef();
            String defVar = "v" + def;
            //System.out.println("new definition for: " + defVar + "statement : " + instStmt);
            newDefinition(this.procedure.defStmtSetMap, defVar, instStmt);
            // We should not redefine the access-paths corresponding to this def, 
            // since the object pointed to by the def is not changed!!!!!!
            //Set<String> accessPathSet = getAccessPathSetAliasToThisVN(def);
            //for (String accessPath : accessPathSet)
              //newDefinition(defStmtSetMap, accessPath, instStmt);
          }
          
          if (inst instanceof SSAGetInstruction) {
            String accessPath = this.accessPathMap.get((SSAFieldAccessInstruction)inst);
            //System.out.println("Access path : " + accessPath);
            Set<Statement> defStmtSet = getDefinitionStatementSet(this.procedure.defStmtSetMap, accessPath);
            for (Statement defStmt : defStmtSet) {
                //System.out.println("From : " + defStmt.getContent() + "    To : " + instStmt.getContent());
                defStmt.flowDataTo(instStmt);
            } 
            
            // For statments like v52 = v1.differ there should be a flow from v1.differ to the statement
            if (instStmt.getContent().toString().contains(", ")) {
                String objectField = instStmt.getContent().toString().split(", ")[2];
                if (objectField.contains("(") == false && objectField.contains(")") == false && instStmt.getContent().toString().contains(" > ")) {
                    String object = "v" + instStmt.getContent().toString().split(" > ")[1].split(",")[0] + "." + objectField;
                    //System.out.println("object : " + object);
                    Set<Statement> objectDefStmtSet = getDefinitionStatementSet(this.procedure.defStmtSetMap, object);
                    for (Statement defStmt : objectDefStmtSet) {
                      //System.out.println("From (else if 1) : " + defStmt.getContent() + "    To : " + actualInStmt.getContent());
                      //defStmt.flowDataTo(actualInStmt);
                      //System.out.println("From : " + defStmt.getContent() + "    To : " + instStmt.getContent());
                      defStmt.flowDataTo(instStmt);
                    }
                }
            }
          } else if (inst instanceof SSAPutInstruction) {
            String accessPath = this.accessPathMap.get((SSAFieldAccessInstruction)inst);
            newDefinition(this.procedure.defStmtSetMap, accessPath, instStmt);
          } else if (inst instanceof SSAReturnInstruction) {
            SSAReturnInstruction retInst = (SSAReturnInstruction)inst;
            retInstSet.add(retInst);
          }
        }
      }
      
      // check if the analysis for this node is stable
      Map<String, Set<Statement>> oldDefStmtSetMap = reachDef.get(node);
      if (oldDefStmtSetMap == null || !this.procedure.defStmtSetMap.equals(oldDefStmtSetMap)) {
        reachDef.put(node, this.procedure.defStmtSetMap);
        Iterator<ISSABasicBlock> succNodeIter = cfg.getSuccNodes(node);
        while (succNodeIter.hasNext()) {
          ISSABasicBlock succNode = succNodeIter.next();
          if (!worklist.contains(succNode))
            worklist.addLast(succNode);
        }
      }
    }
    
    // clear-up formal-out parameter statements
    Map<String, Set<Statement>> exitDefStmtSetMap = reachDef.get(exit);
    Set<Statement> formalInStmtSet = getFormalInStatementSet();
    for (Statement formalInStmt : formalInStmtSet) {
      String formalIn = (String)formalInStmt.getContent();
      Set<Statement> defStmtSet = getDefinitionStatementSet(exitDefStmtSetMap, formalIn);
      for (Statement defStmt : defStmtSet)
        if (defStmt.getStatementType() != StatementType.FormalIn) {
          Statement formalOutStmt = this.formalInOutModel.requireOutStatement(formalIn);
          defStmt.flowDataTo(formalOutStmt);
        }
    }
    for (SSAReturnInstruction retInst : retInstSet) {
      if (retInst.returnsVoid())
        continue;
      Statement retInstStmt = requireInstructionStatement(retInst);
      Statement resultStmt = this.formalInOutModel.requireOutStatement("v0");
      retInstStmt.flowDataTo(resultStmt);
    }
  }
  
  private Set<String> getReachingAliasSet(Map<String, Set<Statement>> defStmtSetMap, int vn) {
    Set<String> reachSet = new HashSet<>();
    reachSet.add("v" + vn);
    
    Set<Integer> aliasSet = this.procedure.getAliasSet(vn);
    for (Integer alias : aliasSet) {
      String aliasVar = "v" + alias;
      if (defStmtSetMap.containsKey(aliasVar))
        reachSet.add(aliasVar);
    }
    
    Set<String> accessPathSet = getAccessPathSetAliasToThisVN(vn);
    for (String accessPath : accessPathSet)
      if (defStmtSetMap.containsKey(accessPath))
        reachSet.add(accessPath);
    
    return reachSet;
  }
  
  private Set<Statement> getDefinitionStatementSet(Map<String, Set<Statement>> defStmtSetMap, String var) {
    Set<Statement> defStmtSet = defStmtSetMap.get(var);
    if (defStmtSet != null)
      return defStmtSet;
    else
      return new HashSet<>();
  }
  
  private void newDefinition(Map<String, Set<Statement>> defStmtSetMap, String var, Statement stmt) {
    Set<Statement> defStmtSet = defStmtSetMap.get(var);
    if (defStmtSet == null) {
      defStmtSet = new HashSet<>();
      defStmtSetMap.put(var, defStmtSet);
    }
    defStmtSet.clear();
    defStmtSet.add(stmt);
  }
  
  private void modifyWholeObject(Map<String, Set<Statement>> defStmtSetMap, String var, Statement stmt) {
    newDefinition(defStmtSetMap, var, stmt);
    Set<String> accessPathSet = getAccessPathSetStartingWith(var);
    for (String accessPath : accessPathSet)
      newDefinition(defStmtSetMap, accessPath, stmt);
  }
  
  //---------------------public-------------------------------------------------
  final public Set<ISSABasicBlock> getBaseLevelNodeSet() {
    ISSABasicBlock entry = this.procedure.getCFG().entry();
    List<Set<ISSABasicBlock>> ctrlDepNodeSetList = getControlDependentNodeSetList(entry);
    if (!ctrlDepNodeSetList.isEmpty())
      return ctrlDepNodeSetList.get(0);
    else
      return new HashSet<>();
  }
  
  final public List<Set<ISSABasicBlock>> getControlDependentNodeSetList(ISSABasicBlock node) {
    if (this.controlDependentNodeSetListMap.containsKey(node))
      return this.controlDependentNodeSetListMap.get(node);
    else
      return new LinkedList<>();
  }
  
  final public ISSABasicBlock getControlPredicateNode(ISSABasicBlock node) {
    return this.controlPredicateNodeMap.get(node);
  }
  
  final public Procedure getProcedure() {
    return this.procedure;
  }
  
  final public Statement getEntryStatement() {
    return this.entryStatement;
  }
  
  final public Statement requireInstructionStatement(SSAInstruction inst) {
    Statement instStmt = this.instrucionStatementMap.get(inst);
    if (instStmt == null) {
      ISSABasicBlock node = this.procedure.getNode(inst);
      ISSABasicBlock controller = getControlPredicateNode(node);
      if (controller == null)
        controller = this.procedure.getCFG().entry();
      instStmt = new Statement(StatementType.Instruction, inst, controller, this);
      this.instrucionStatementMap.put(inst, instStmt);
    }
    return instStmt;
  }
  
  final public Set<Statement> getFormalInStatementSet() {
    return new HashSet<>(this.formalInOutModel.inStatementMap.values());
  }
  
  final public Set<Statement> getFormalOutStatementSet() {
    return new HashSet<>(this.formalInOutModel.outStatementMap.values());
  }
  
  final public Set<Statement> getInstructionStatementSet() {
    return new HashSet<>(this.instrucionStatementMap.values());
  }
  
  final public Set<Statement> getActualInStatementSet(SSAInvokeInstruction invokeInst) {
    InOutModel inOutModel = this.actualInOutModelMap.get(invokeInst);
    Set<Statement> actualInStmtSet = new HashSet<>();
    if (inOutModel != null)
      actualInStmtSet.addAll(inOutModel.inStatementMap.values());
    return actualInStmtSet;
  }
  
  final public Set<Statement> getActualOutStatementSet(SSAInvokeInstruction invokeInst) {
    InOutModel inOutModel = this.actualInOutModelMap.get(invokeInst);
    Set<Statement> actualOutStmtSet = new HashSet<>();
    if (inOutModel != null)
      actualOutStmtSet.addAll(inOutModel.outStatementMap.values());
    return actualOutStmtSet;
  }
  
  final public Set<SSAInvokeInstruction> getInvokeInstructionSet() {
    return this.actualInOutModelMap.keySet();
  }
  
  final public Set<String> getActualAccessPathSet(String formalAccessPath, SSAInvokeInstruction invokeInst) {
    Map<SSAInvokeInstruction, Set<String>> tracker = this.actualAccessPathSetMap.get(formalAccessPath);
    if (tracker == null)
      return new HashSet<>();
    Set<String> actualAccessPathSet = tracker.get(invokeInst);
    if (actualAccessPathSet == null)
      return new HashSet<>();
    return actualAccessPathSet;
  }
  
  final public Set<String> getAccessPathSetAliasToThisVN(int vn) {
    String var = "v" + vn;
    Set<String> accessPathSet = this.accessPathSetMap.get(var);
    if (accessPathSet != null)
      return accessPathSet;
    else
      return new HashSet<>();
  }
  
  final public Set<String> getVNSetAliasToThisAccessPath(String accessPath) {
    Set<String> fieldAliasSet = this.fieldAliasSetMap.get(accessPath);
    if (fieldAliasSet != null)
      return fieldAliasSet;
    else
      return new HashSet<>();
  }
  
  final public boolean hasSameInOutModelsWith(ProcedureDependenceGraph procDepGraph) {
    if (!this.formalInOutModel.isIdentical(procDepGraph.formalInOutModel))
      return false;
    
    Set<SSAInvokeInstruction> thisInvokeInstSet = this.actualInOutModelMap.keySet();
    Set<SSAInvokeInstruction> otherInvokeInstSet = procDepGraph.actualInOutModelMap.keySet();
    if (!thisInvokeInstSet.equals(otherInvokeInstSet))
      return false;
    
    for (Map.Entry<SSAInvokeInstruction, InOutModel> actualInOutModelMapEnt : actualInOutModelMap.entrySet()) {
      SSAInvokeInstruction invokeInst = actualInOutModelMapEnt.getKey();
      InOutModel thisInOutModel = actualInOutModelMapEnt.getValue();
      InOutModel otherInOutModel = procDepGraph.actualInOutModelMap.get(invokeInst);
      if (!thisInOutModel.isIdentical(otherInOutModel))
        return false;
    }
    
    return true;
  }
}
