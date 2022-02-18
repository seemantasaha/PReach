package core;

import com.google.common.collect.HashBiMap;
import com.ibm.wala.analysis.pointers.HeapGraph;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.TypeReference;
import core.escape.EscapeSummary;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import wala.ExceptionPrunedCFG;
import wala.PrunedCFG;

/**
 *
 * @author zzk
 */
public class Procedure {
  private IR                                        ir = null;
  private DefUse                                    defUse = null;
  private PrunedCFG<SSAInstruction, ISSABasicBlock> cfg = null;
  private TypeInference                             typeInference = null;
  private Map<Integer, Argument>                    argumentMap = new HashMap<>();
  private Map<Integer, Set<Integer>>                aliasSetMap = new HashMap<>();
  
  private Map<ISSABasicBlock, Integer>              forwardNumberMap = new HashMap<>();
  private Map<ISSABasicBlock, Integer>              backwardNumberMap = new HashMap<>();
  private LinkedList<ISSABasicBlock>                nodeListForward = new LinkedList<>();
  private LinkedList<ISSABasicBlock>                nodeListBackward = new LinkedList<>();
  
  private Map<ISSABasicBlock, ISSABasicBlock>       dominatorMap = new HashMap<>();
  private Map<ISSABasicBlock, ISSABasicBlock>       postDominatorMap = new HashMap<>();
  public Map<String, Set<Statement>>               defStmtSetMap = new HashMap<>();
  
  private Map<ISSABasicBlock, Loop>                 loopMap = new HashMap<>();
  private Set<Loop>                                 topLoopSet = new HashSet<>();
  
  private Set<NewObject>                            newObjectSet = new HashSet<>();
  
  private Set<Procedure>                            callerSet = new TreeSet<>(new ProcedureComparator());
  private Map<Procedure, Set<ISSABasicBlock>>       callNodeSetMap = new TreeMap<>(new ProcedureComparator());
  private Map<ISSABasicBlock, Set<Procedure>>       calleeSetMap = new HashMap<>();
  
  private EscapeSummary                             escapeSummary = null;
  
  public Set<String> dependentNodes;
  
  public Procedure(IR ir) {
    this.ir = ir;
    this.defUse = new DefUse(ir);
    this.cfg = ExceptionPrunedCFG.make(ir.getControlFlowGraph());
    this.typeInference = TypeInference.make(this.ir, true);
    this.dependentNodes = new HashSet<>();
    
    Set<ISSABasicBlock> nodeFlagSet = new HashSet<>();
    generateNodeListForward(this.cfg.entry(), nodeFlagSet);
    // if exit is not contained, it means exit is not reachable from entry in this pruned CFG
    if (!nodeFlagSet.contains(this.cfg.exit()))
      this.nodeListForward.addLast(this.cfg.exit());
    for (int i = 0; i < this.nodeListForward.size(); i++)
      this.forwardNumberMap.put(this.nodeListForward.get(i), i);
    
    nodeFlagSet.clear();
    generateNodeListBackward(this.cfg.exit(), nodeFlagSet);
    // if entry is not contained, it means entry is not reachable from exit in this pruned backward CFG
    if (!nodeFlagSet.contains(this.cfg.entry()))
      this.nodeListBackward.addLast(this.cfg.entry());
    for (int i = 0; i < this.nodeListBackward.size(); i++)
      this.backwardNumberMap.put(this.nodeListBackward.get(i), i);
    
    generateDominatorTree();
    generatePostDominatorTree();
    
    identifyLoops();
    constructLoopHierarchy();
    
    collectNewObjects();
    collectArguments();
    collectAliases();
  }
  
  // use DFS to generate reverse post-order for forward CFG
  private void generateNodeListForward(ISSABasicBlock node, Set<ISSABasicBlock> nodeFlagSet) {
    nodeFlagSet.add(node);
    
    //Collection<ISSABasicBlock> succNodeSet = this.cfg.getNormalSuccessors(node);
    //for (ISSABasicBlock succ : succNodeSet)
    Iterator<ISSABasicBlock> succIter = this.cfg.getSuccNodes(node);
    while (succIter.hasNext()) {
      ISSABasicBlock succ = succIter.next();
      if (!nodeFlagSet.contains(succ))
        generateNodeListForward(succ, nodeFlagSet);
    }
    
    // RPO needs to have a node at front of all its followers
    this.nodeListForward.addFirst(node);
  }
  
  // use DFS to generate reverse post-order for backward CFG
  private void generateNodeListBackward(ISSABasicBlock node, Set<ISSABasicBlock> nodeFlagSet) {
    nodeFlagSet.add(node);
    
    //Collection<ISSABasicBlock> predNodeSet = this.cfg.getNormalPredecessors(node);
    //for (ISSABasicBlock pred : predNodeSet)
    Iterator<ISSABasicBlock> predIter = this.cfg.getPredNodes(node);
    while (predIter.hasNext()) {
      ISSABasicBlock pred = predIter.next();
      if (!nodeFlagSet.contains(pred))
        generateNodeListBackward(pred, nodeFlagSet);
    }
    
    // RPO needs to have a node at front of all its followers
    this.nodeListBackward.addFirst(node);
  }
  
  // refer to Keith Cooper's excellent paper "A Simple, Fast Dominance Algorithm"
  private void generateDominatorTree() {
    // entry is unique in a CFG
    ISSABasicBlock entry = this.cfg.entry();
    this.dominatorMap.put(entry, entry);
    
    boolean change = true;
    while (change) {
      change = false;
      // as the paper says: for all nodes except the entry in RPO
      for (ISSABasicBlock node : this.nodeListForward) {
        if (node == entry)
          continue;
        ISSABasicBlock oldDomNode = this.dominatorMap.get(node);
        ISSABasicBlock newDomNode = null;
        boolean init = true;
        //Collection<ISSABasicBlock> predNodeSet = this.cfg.getNormalPredecessors(node);
        //for (ISSABasicBlock predNode : predNodeSet) {
        Iterator<ISSABasicBlock> predNodeIter = this.cfg.getPredNodes(node);
        while (predNodeIter.hasNext()) {
          ISSABasicBlock predNode = predNodeIter.next();
          // if it has not been assigned a idominator, it has not been processed yet
          if (!this.dominatorMap.containsKey(predNode))
            continue;
          
          // pick up any processed predecessor as the initial idominator
          if (init) {
            newDomNode = predNode;
            init = false;
          }
          
          // move the fingers up along the dominator tree till they converge
          ISSABasicBlock tempDomNode = predNode;
          while (newDomNode != tempDomNode) {
            int newDomNodeNum = this.forwardNumberMap.get(newDomNode);
            int tempDomNodeNum = this.forwardNumberMap.get(tempDomNode);
            if (newDomNodeNum > tempDomNodeNum)
              newDomNode = this.dominatorMap.get(newDomNode);
            else
              tempDomNode = this.dominatorMap.get(tempDomNode);
          }
        }
        
        if (newDomNode != oldDomNode) {
          this.dominatorMap.put(node, newDomNode);
          change = true;
        }
      }
    }
  }
  
  // refer to Keith Cooper's excellent paper "A Simple, Fast Dominance Algorithm"
  private void generatePostDominatorTree() {
    // exit is unique in a CFG
    ISSABasicBlock exit = this.cfg.exit();
    this.postDominatorMap.put(exit, exit);
    
    boolean change = true;
    while (change) {
      change = false;
      // as the paper says: for all nodes except the beginning node in RPO
      for (ISSABasicBlock node : this.nodeListBackward) {
        if (node == exit)
          continue;
        ISSABasicBlock oldPostDomNode = this.postDominatorMap.get(node);
        ISSABasicBlock newPostDomNode = null;
        boolean init = true;
        //Collection<ISSABasicBlock> succNodeSet = this.cfg.getNormalSuccessors(node);
        //for (ISSABasicBlock succNode : succNodeSet) {
        Iterator<ISSABasicBlock> succNodeIter = this.cfg.getSuccNodes(node);
        while (succNodeIter.hasNext()) {
          ISSABasicBlock succNode = succNodeIter.next();
          // if it has not been assigned a ipost-dominator, it has not been processed yet
          if (!this.postDominatorMap.containsKey(succNode))
            continue;
          
          // pick up any processed successor as the initial ipost-dominator
          if (init) {
            newPostDomNode = succNode;
            init = false;
          }
          
          // move the fingers up along the post-dominator tree till they converge
          ISSABasicBlock tempPostDomNode = succNode;
          while (newPostDomNode != tempPostDomNode) {
            int newPostDomNodeNum = this.backwardNumberMap.get(newPostDomNode);
            int tempPostDomNodeNum = this.backwardNumberMap.get(tempPostDomNode);
            if (newPostDomNodeNum > tempPostDomNodeNum)
              newPostDomNode = this.postDominatorMap.get(newPostDomNode);
            else
              tempPostDomNode = this.postDominatorMap.get(tempPostDomNode);
          }
        }
        
        if (newPostDomNode != oldPostDomNode) {
          this.postDominatorMap.put(node, newPostDomNode);
          change = true;
        }
      }
    }
  }
  
  // identify loops in this method
  private void identifyLoops() {
    // entry is unique in a CFG
    ISSABasicBlock entry = this.cfg.entry();
    
    for (ISSABasicBlock node : this.nodeListForward) {
      int nodeNum = this.forwardNumberMap.get(node);
      Collection<ISSABasicBlock> succNodeSet = this.cfg.getNormalSuccessors(node);
      for (ISSABasicBlock succNode : succNodeSet) {
        // check if this is a back edge in the graph
        int succNodeNum = this.forwardNumberMap.get(succNode);
        if (succNodeNum > nodeNum)
          continue;
        
        // it is a back edge (i.e. succNodeNum <= nodeNum), so find which dominator this edge goes back to
        // since it may go back to itself, we start from itself
        ISSABasicBlock domNode = node;
        while (domNode != entry || domNode == succNode) {
          // if the node goes back to its dominator, its dominator is the loop header
          if (domNode == succNode) {
            Loop loop = this.loopMap.get(domNode);
            if (loop == null) {
              loop = new Loop(this, domNode);
              loopMap.put(domNode, loop);
            }
            
            // record its loop body (may be partial if other back edges exist)
            Stack<ISSABasicBlock> nodeStk = new Stack<>();
            nodeStk.push(node);
            while (!nodeStk.empty()) {
              ISSABasicBlock topNode = nodeStk.pop();
              // if the top node is not in the loop body yet
              if (!loop.isInLoopBody(topNode)) {
                loop.expandLoopBody(topNode);
                Collection<ISSABasicBlock> predNodeSet = this.cfg.getNormalPredecessors(topNode);
                for (ISSABasicBlock predNode : predNodeSet)
                  nodeStk.push(predNode);
              }
            }
            
            // label the one who brings up the back edge as a back node 
            loop.labelBackNode(node);
            
            // break out the back edge searching while-loop
            break;
          }
          
          domNode = this.dominatorMap.get(domNode);
        }
      }
    }
  }
  
  // construct loop hierarchy in this procedure (e.g. L1 with L1_L1 and L1_L2; L2 with L2_L1 with L2_L1_L1)
  private void constructLoopHierarchy() {
    // entry is unique in a CFG
    ISSABasicBlock entry = this.cfg.entry();
    
    for (Map.Entry<ISSABasicBlock, Loop> loopMapEnt : this.loopMap.entrySet()) {
      ISSABasicBlock loopHeader = loopMapEnt.getKey();
      Loop loop = loopMapEnt.getValue();
      
      // if loop A is nested in loop B
      // (1) loop B's header dominates loop A's header
      // (2) loop B's body includes loop A's header
      ISSABasicBlock domNode = this.dominatorMap.get(loopHeader);
      while (domNode != entry && domNode != null) {
        Loop loopUpr = this.loopMap.get(domNode);
        if (loopUpr != null && loopUpr.isInLoopBody(loopHeader)) {
          loopUpr.addNextLevelLoop(loop);
          break;
        }
        domNode = this.dominatorMap.get(domNode);
      }
      
      if (domNode == entry)
        this.topLoopSet.add(loop);
    }
    
    // set each loop's loopLevel
    for (Loop topLoop : this.topLoopSet)
      topLoop.propagateLoopLevel(0);
    
    // set each loop's loopNumber (i.e. L1, L1_L1, L1_L1_L1, and L1_L1_L2 etc.)
    Queue<Set<Loop>> loopSetQ = new LinkedList<>();
    loopSetQ.add(this.topLoopSet);
    while (!loopSetQ.isEmpty()) {
      Map<Integer, Loop> loopMap = new TreeMap<>();
      Set<Loop> loopSet = loopSetQ.remove();
      for (Loop loop : loopSet) {
        ISSABasicBlock loopHeader = loop.getLoopHeader();
        int forwardNum = this.forwardNumberMap.get(loopHeader);
        loopMap.put(forwardNum, loop);
        Set<Loop> nextedLevelLoopSet = loop.getNextLevelLoopSet();
        if (!nextedLevelLoopSet.isEmpty())
          loopSetQ.add(nextedLevelLoopSet);
      }
      
      int i = 1;
      for (Loop loop : loopMap.values())
        loop.setLoopNumber(i++);
    }
  }
  
  private void collectNewObjects() {
    for (ISSABasicBlock node : this.nodeListForward) {
      Iterator<SSAInstruction> instIter = node.iterator();
      while (instIter.hasNext()) {
        SSAInstruction inst = instIter.next();
        // OK, we find a new instruction in this node
        if (inst instanceof SSANewInstruction) {
          SSANewInstruction newInst = (SSANewInstruction)inst;
          NewObject newObj = new NewObject(this, newInst);
          this.newObjectSet.add(newObj);
        }
      }
    }
  }
  
  // collect all the variables that are used in some method invocation in this procedure
  private void collectArguments() {
    Iterator<SSAInstruction> instIter = this.ir.iterateAllInstructions();
    while (instIter.hasNext()) {
      SSAInstruction inst = instIter.next();
      if (!(inst instanceof SSAInvokeInstruction))
        continue;
      SSAInvokeInstruction callInst = (SSAInvokeInstruction)inst;
      if (!callInst.getCallSite().isStatic()) {
        int objNum = callInst.getUse(0);
        Argument obj = this.argumentMap.get(objNum);
        if (obj == null) {
          TypeReference typeRef = this.typeInference.getType(objNum).getTypeReference();
          obj = new Argument(typeRef);
          this.argumentMap.put(objNum, obj);
        }
        
        for (int i = 1; i < callInst.getNumberOfParameters(); i++) {
          int argNum = callInst.getUse(i);
          Argument arg = this.argumentMap.get(argNum);
          if (arg == null) {
            TypeReference typeRef = this.typeInference.getType(argNum).getTypeReference();
            arg = new Argument(typeRef);
            this.argumentMap.put(argNum, arg);
          }
          obj.addArgumentForMethodCalledOnThis(arg);
        }
      } else {
        for (int i = 0; i < callInst.getNumberOfParameters(); i++) {
          int argNum = callInst.getUse(i);
          Argument arg = this.argumentMap.get(argNum);
          if (arg == null) {
            TypeReference typeRef = this.typeInference.getType(argNum).getTypeReference();
            arg = new Argument(typeRef);
            this.argumentMap.put(argNum, arg);
          }
        }
      }
    }
  }
  
  private Set<InstanceKey> getInstanceKeySet(int vn) {
    Set<InstanceKey> ikSet = new HashSet<>();
    
    HeapGraph hg = Program.getHeapGraph();
    HeapModel hm = hg.getHeapModel();
    PointerAnalysis pts = hg.getPointerAnalysis();
    
    Set<CGNode> cgNodeSet = Program.getCallGraph().getNodes(this.ir.getMethod().getReference());
    for (CGNode cgNode : cgNodeSet) {
      PointerKey pk = hm.getPointerKeyForLocal(cgNode, vn);
      if (pk != null) {
        Iterator<InstanceKey> ikIter = pts.getPointsToSet(pk).iterator();
        while (ikIter.hasNext()) {
          InstanceKey ik = ikIter.next();
          ikSet.add(ik);
        }
      }
    }
    
    return ikSet;
  }
  
  private void collectAliases() {
    SymbolTable symTab = this.ir.getSymbolTable();
    for (int vn = 1; vn <= symTab.getMaxValueNumber(); vn++) {
      Set<Integer> aliasSet = new HashSet<>();
      
      Set<InstanceKey> ikSet = getInstanceKeySet(vn);
      for (int i = 1; i <= symTab.getMaxValueNumber(); i++) {
        if (i == vn)
          continue;
        Set<InstanceKey> otherSet = getInstanceKeySet(i);
        for (InstanceKey other : otherSet) {
          if (ikSet.contains(other) && !other.toString().contains("java") && !other.toString().contains("Ljava")) {
            aliasSet.add(i);
            break;
          }
        }
      }
      
      this.aliasSetMap.put(vn, aliasSet);
    }
    
    // if Averroes is not used, alias analysis is not messed up
    if (!ProgramOption.getAverroesFlag())
      return;
    
    // heuristics using the type knowledge (e.g. given by SSACheckCastInstruction) to refine alias info
    for (Map.Entry<Integer, Set<Integer>> aliasSetMapEnt : this.aliasSetMap.entrySet()) {
      int vn = aliasSetMapEnt.getKey();
      TypeReference typeRef = getTypeReference(vn);
      Set<Integer> aliasSet = aliasSetMapEnt.getValue();
      List<Set<Integer>> groupList = new LinkedList<>();
      for (Integer alias : aliasSet) {
        TypeReference aliasTypeRef = getTypeReference(alias);
        boolean added = false;
        for (Set<Integer> group : groupList) {
          boolean sameGroup = false;
          for (Integer member : group) {
            TypeReference memberTypeRef = getTypeReference(member);
            if (Program.isTypesSuperSubRelated(aliasTypeRef, memberTypeRef)) {
              sameGroup = true;
              break;
            }
          }
          if (sameGroup) {
            group.add(alias);
            added = true;
          }
        }
        // no group can hold this alias, so we have a new group
        if (!added) {
          Set<Integer> newGroup = new HashSet<>();
          newGroup.add(alias);
          groupList.add(newGroup);
        }
      }
      
      // if there are more than one group....
      if (groupList.size() <= 1)
        continue;
      // first heuristics -- SSACheckCastInstruction
      boolean reduced = false;
      Set<SSAInstruction> useInstSet = getUseInstructionSet(vn);
      for (SSAInstruction useInst : useInstSet) {
        // if one group has a def of a SSACheckCastInstruction, this group is removed
        // it means all the numbers in this group may be aliases of vn
        if (useInst instanceof SSACheckCastInstruction) {
          int def = useInst.getDef();
          Iterator<Set<Integer>> groupIter = groupList.iterator();
          while (groupIter.hasNext()) {
            Set<Integer> group = groupIter.next();
            if (group.contains(def)) {
              groupIter.remove();
              reduced = true;
            }
          }
        }
      }
      // second heuristics -- type relation with the key
      if (!reduced) {
        Iterator<Set<Integer>> groupIter = groupList.iterator();
        while (groupIter.hasNext()) {
          Set<Integer> group = groupIter.next();
          for (Integer member : group) {
            TypeReference memberTypeRef = getTypeReference(member);
            if (Program.isTypesSuperSubRelated(typeRef, memberTypeRef)) {
              groupIter.remove();
              reduced = true;
              break;
            }
          }
        }
      }
      if (reduced) {
        // the rest of groups should be considered as NON-aliases
        for (Set<Integer> group : groupList) {
          for (Integer member : group) {
            aliasSet.remove(member);
            Set<Integer> memberAliasSet = this.aliasSetMap.get(member);
            // since every vn has an alias-set, we don't need to check null before remove
            memberAliasSet.remove(vn);
          }
        }
      }
      /*
      else {
        System.out.println(vn + " never casted in " + getFullSignature());
        System.out.print("\t\t");
        for (Integer alias : aliasSet)
          System.out.print(alias + " ");
        System.out.println("\n");
      }
      */
    }
  }
  
  final public String getClassName() {
    return this.ir.getMethod().getDeclaringClass().getName().toString();
  }
  
  final public String getProcedureName() {
    return this.ir.getMethod().getName().toString();
  }
  
  final public String getSelectorName() {
    IMethod mth = this.ir.getMethod();
    return mth.getSelector().toString();
  }
  
  final public String getFullSignature() {
    IMethod mth = this.ir.getMethod();
    String clsName = mth.getDeclaringClass().getName().toString();
    return clsName + "." + mth.getSelector().toString();
  }
  
  final public IR getIR() {
    return this.ir;
  }
  
  final public ControlFlowGraph<SSAInstruction, ISSABasicBlock> getCFG() {
    return this.cfg;
  }
  
  final public TypeInference getTypeInference() {
    return this.typeInference;
  }
  
  final public TypeReference getTypeReference(int vn) {
    return this.typeInference.getType(vn).getTypeReference();
  }
  
  final public Set<Argument> getArgumentSet(int paramNum) {
    Set<Argument> argSet = new HashSet<>();
    if (paramNum >= this.ir.getNumberOfParameters())
      return argSet;
    for (Procedure caller : this.callerSet) {
      Set<ISSABasicBlock> callNodeSet = caller.getCallNodeSet(this);
      for (ISSABasicBlock callNode : callNodeSet) {
        SSAInstruction inst = callNode.getLastInstruction();
        if (!(inst instanceof SSAInvokeInstruction))
          continue;
        SSAInvokeInstruction callInst = (SSAInvokeInstruction)inst;
        int useNum = callInst.getUse(paramNum);
        Argument arg = caller.argumentMap.get(useNum);
        if (arg != null)
          argSet.add(arg);
      }
    }
    return argSet;
  }
  
  final public Set<ArrayList<Argument>> getArgumentArraySet() {
    Set<ArrayList<Argument>> argArraySet = new HashSet<>();
    for (Procedure caller : this.callerSet) {
      Set<ISSABasicBlock> callNodeSet = caller.getCallNodeSet(this);
      for (ISSABasicBlock callNode : callNodeSet) {
        SSAInstruction inst = callNode.getLastInstruction();
        if (!(inst instanceof SSAInvokeInstruction))
          continue;
        SSAInvokeInstruction callInst = (SSAInvokeInstruction)inst;
        // should we use an addFlag to indicate whether to add this to Map<>?
        // due to null met
        ArrayList<Argument> argArray = new ArrayList<>();
        boolean addFlag = true;
        for (int i = 0; i < callInst.getNumberOfParameters(); i++) {
          int useNum = callInst.getUse(i);
          Argument arg = caller.argumentMap.get(useNum);
          if (arg == null) {
            addFlag = false;
            break;
          }
          argArray.add(arg);
        }
        if (addFlag)
          argArraySet.add(argArray);
      }
    }
    return argArraySet;
  }
  
  final public Set<Integer> getAliasSet(int vn) {
    Set<Integer> aliasSet = this.aliasSetMap.get(vn);
    if (aliasSet != null)
      return aliasSet;
    else
      return new HashSet<>();
  }
  
  final public SSAInstruction getDefinitionInstruction(int vn) {
    return this.defUse.getDef(vn);
  }
  
  final public Set<SSAInstruction> getUseInstructionSet(int vn) {
    Set<SSAInstruction> useInstSet = new HashSet<>();
    Iterator<SSAInstruction> useInstIter = this.defUse.getUses(vn);
    while (useInstIter.hasNext()) {
      SSAInstruction useInst = useInstIter.next();
      useInstSet.add(useInst);
    }
    return useInstSet;
  }
  
  final public LinkedList<ISSABasicBlock> getNodeListForward() {
    return this.nodeListForward;
  }
  
  final public LinkedList<ISSABasicBlock> getNodeListBackward() {
    return this.nodeListBackward;
  }
  
  final public Set<ISSABasicBlock> getNodeSet() {
    Set<ISSABasicBlock> nodeSet = new HashSet<>();
    Iterator<ISSABasicBlock> nodeIter = this.cfg.iterator();
    while (nodeIter.hasNext()) {
      ISSABasicBlock node = nodeIter.next();
      nodeSet.add(node);
    }
    return nodeSet;
  }
  
  final public List<ISSABasicBlock> getNodeList() {
    List<ISSABasicBlock> nodeList = new ArrayList<>();
    Iterator<ISSABasicBlock> nodeIter = this.cfg.iterator();
    while (nodeIter.hasNext()) {
      ISSABasicBlock node = nodeIter.next();
      nodeList.add(node);
    }
    return nodeList;
  }
  
  final public ISSABasicBlock getNode(SSAInstruction inst) {
    return this.ir.getBasicBlockForInstruction(inst);
  }
  
  final public ISSABasicBlock getImmediateDominator(ISSABasicBlock node) {
    return this.dominatorMap.get(node);
  }
  
  final public ISSABasicBlock getImmediatePostDominator(ISSABasicBlock node) {
    return this.postDominatorMap.get(node);
  }
  
  // dominator set contains the node itself, since it dominates itself
  final public Set<ISSABasicBlock> getDominatorSet(ISSABasicBlock node) {
    Set<ISSABasicBlock> domSet = new HashSet<>();
    while (node != null && !domSet.contains(node)) {
      domSet.add(node);
      node = getImmediateDominator(node);
    }
    return domSet;
  }
  
  // post-dominator set contains the node itself, since it post-dominates itself
  final public Set<ISSABasicBlock> getPostDominatorSet(ISSABasicBlock node) {
    Set<ISSABasicBlock> postDomSet = new HashSet<>();
    while (node != null && !postDomSet.contains(node)) {
      postDomSet.add(node);
      node = getImmediatePostDominator(node);
    }
    return postDomSet;
  }
  
  final public Set<Loop> getLoopSet() {
    return new HashSet<>(this.loopMap.values());
  }
  
  final public Set<Loop> getTopLoopSet() {
    return this.topLoopSet;
  }
  
  final public Loop getLoop(String loopId) {
    for (Loop loop : this.loopMap.values())
      if (loopId.equals(loop.getLoopIdentifier()))
        return loop;
    
    return null;
  }
  
  // get the loop that directly include this node in itself
  final public Loop getCoveringLoop(ISSABasicBlock node) {
    for (Loop loop : this.loopMap.values()) {
      if (!loop.isInLoopBody(node))
        continue;
      // now we know this loop includes this node
      // but if any of its next level loops include this node, this node is not directly included by this loop
      boolean directIncFlag = true;
      Set<Loop> nextLevelLoopSet = loop.getNextLevelLoopSet();
      for (Loop nextLevelLoop : nextLevelLoopSet)
        if (nextLevelLoop.isInLoopBody(node))
          directIncFlag = false;
      if (directIncFlag)
        return loop;
    }
    // this node is not in any loop of this procedure
    return null;
  }
  
  final public boolean isInSameLoop(ISSABasicBlock node1, ISSABasicBlock node2) {
    for (Loop loop : this.loopMap.values())
      if (loop.isInLoopBody(node1) && loop.isInLoopBody(node2))
        return true;
    return false;
  }
  
  final public void addCallee(Procedure callee, CallSiteReference callSiteRef) {
    if (callee == null || callSiteRef == null)
      return;
    
    Set<ISSABasicBlock> callNodeSet = this.callNodeSetMap.get(callee);
    if (callNodeSet == null) {
      callNodeSet = new HashSet<>();
      this.callNodeSetMap.put(callee, callNodeSet);
    }
    
    for (ISSABasicBlock callNode : this.ir.getBasicBlocksForCall(callSiteRef)) {
      callNodeSet.add(callNode);
      
      Set<Procedure> calleeSet = this.calleeSetMap.get(callNode);
      if (calleeSet == null) {
        calleeSet = new TreeSet<>(new ProcedureComparator());
        this.calleeSetMap.put(callNode, calleeSet);
      }
      calleeSet.add(callee);
    }
    
    callee.callerSet.add(this);
  }
  
  final public Set<Procedure> getCallerSet() {
    return this.callerSet;
  }
  
  final public Set<Procedure> getCalleeSet() {
    return this.callNodeSetMap.keySet();
  }
  
  final public Set<Procedure> getCalleeSet(ISSABasicBlock callNode) {
    Set<Procedure> calleeSet = calleeSetMap.get(callNode);
    if (calleeSet != null)
      return calleeSet;
    else
      return new TreeSet<>();
  }
  
  final public Set<Procedure> getReachableCalleeSet(ISSABasicBlock callNode) {
    Stack<Procedure> procStk = new Stack<>();
    Set<Procedure> flagSet = new TreeSet<>(new ProcedureComparator());
    
    Set<Procedure> calleeSet = getCalleeSet(callNode);
    procStk.addAll(calleeSet);
    flagSet.addAll(calleeSet);
    while (!procStk.empty()) {
      Procedure proc = procStk.pop();
      calleeSet = proc.getCalleeSet();
      for (Procedure callee : calleeSet) {
        if (flagSet.contains(callee))
          continue;
        procStk.push(callee);
        flagSet.add(callee);
      }
    }
    
    return flagSet;
  }
  
  final public Set<Procedure> getReachableCalleeSet() {
    Set<Procedure> procSet = new TreeSet<>(new ProcedureComparator());
    for (ISSABasicBlock callNode : this.calleeSetMap.keySet())
      procSet.addAll(getReachableCalleeSet(callNode));
    return procSet;
  }
  
  final public Set<ISSABasicBlock> getCallNodeSet() {
    return this.calleeSetMap.keySet();
  }
  
  final public Set<ISSABasicBlock> getCallNodeSet(Procedure callee) {
    Set<ISSABasicBlock> callNodeSet = this.callNodeSetMap.get(callee);
    if (callNodeSet != null)
      return callNodeSet;
    else
      return new HashSet<>();
  }
  
  final public Set<ISSABasicBlock> getTopLevelCallNodeSet(Loop loop) {
    Set<ISSABasicBlock> loopBody = new HashSet<>(loop.getLoopBody());
    Set<Loop> nextLevelLoopSet = loop.getNextLevelLoopSet();
    for (Loop nextLevelLoop : nextLevelLoopSet) {
      Set<ISSABasicBlock> nextLevelLoopBody = nextLevelLoop.getLoopBody();
      loopBody.removeAll(nextLevelLoopBody);
    }
    
    Set<ISSABasicBlock> callNodeSet = new HashSet<>();
    for (ISSABasicBlock callNode : this.calleeSetMap.keySet()) {
      if (loopBody.contains(callNode))
        callNodeSet.add(callNode);
    }
    
    return callNodeSet;
  }
  
  final public Set<NewObject> getNewObjectSet() {
    return this.newObjectSet;
  }
  
  final public void setEscapeSummary(EscapeSummary summary) {
    this.escapeSummary = summary;
  }
  
  final public EscapeSummary getEscapeSummary() {
    return this.escapeSummary;
  }
}
