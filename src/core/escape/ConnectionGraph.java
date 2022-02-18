package core.escape;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.FieldReference;
import core.Procedure;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author zzk
 */
public class ConnectionGraph {
  // the first component gives object's allocation-site-PC/reference-variable's value-number
  private Map<String, ObjectNode>     objectNodeMap = new HashMap<>();
  private Map<String, ReferenceNode>  referenceNodeMap = new HashMap<>();
  
  public ConnectionGraph(int paramCnt) {
    for (int i = 0; i < paramCnt; i++) {
      String argNodeName = "a" + (i + 1);
      ReferenceNode argNode = getArgumentNode(argNodeName, true);
      
      String refNodeName = "r" + (i + 1);
      ReferenceNode refNode = getReferenceNode(refNodeName, true);
      refNode.addDeferredEdge(argNode);
      
      //String objNodeName = "ao" + (i + 1);
      //ObjectNode objNode = getObjectNode(objNodeName, true);
      //argNode.addPointsToEdge(objNode);
    }
  }
  
  private Node getNode(String nodeName) {
    if (nodeName.startsWith("o") || nodeName.startsWith("p"))
      return this.objectNodeMap.get(nodeName);
    else
      return this.referenceNodeMap.get(nodeName);
  }
  
  private ReferenceNode getReferenceNode(String refNodeName, boolean make) {
    ReferenceNode refNode = this.referenceNodeMap.get(refNodeName);
    if (refNode == null && make) {
      refNode = new ReferenceNode(refNodeName, ReferenceNodeType.Variable);
      this.referenceNodeMap.put(refNodeName, refNode);
    }
    return refNode;
  }
  
  private ReferenceNode getArgumentNode(String argNodeName, boolean make) {
    ReferenceNode argNode = this.referenceNodeMap.get(argNodeName);
    if (argNode == null && make) {
      argNode = new ReferenceNode(argNodeName, ReferenceNodeType.Actual);
      this.referenceNodeMap.put(argNodeName, argNode);
    }
    return argNode;
  }
  
  private ReferenceNode getNonStaticFieldNode(String fieldNodeName, boolean make) {
    ReferenceNode fieldNode = this.referenceNodeMap.get(fieldNodeName);
    if (fieldNode == null && make) {
      fieldNode = new ReferenceNode(fieldNodeName, ReferenceNodeType.NonStaticField);
      this.referenceNodeMap.put(fieldNodeName, fieldNode);
    }
    return fieldNode;
  }
  
  private ReferenceNode getStaticFieldNode(String staticFieldNodeName, boolean make) {
    ReferenceNode staticFieldNode = this.referenceNodeMap.get(staticFieldNodeName);
    if (staticFieldNode == null && make) {
      staticFieldNode = new ReferenceNode(staticFieldNodeName, ReferenceNodeType.StaticField);
      this.referenceNodeMap.put(staticFieldNodeName, staticFieldNode);
    }
    return staticFieldNode;
  }
  
  private ObjectNode getObjectNode(String objNodeName, boolean make) {
    ObjectNode objNode = this.objectNodeMap.get(objNodeName);
    if (objNode == null && make) {
      objNode = new ObjectNode(objNodeName, ObjectNodeType.Normal);
      this.objectNodeMap.put(objNodeName, objNode);
    }
    return objNode;
  }
  
  private ObjectNode getPhantomNode(String refNodeName, boolean make) {
    String phanNodeName = "p-" + refNodeName;
    ObjectNode phanNode = this.objectNodeMap.get(phanNodeName);
    if (phanNode == null && make) {
      phanNode = new ObjectNode(phanNodeName, ObjectNodeType.Phantom);
      this.objectNodeMap.put(phanNodeName, phanNode);
    }
    return phanNode;
  }
  
  private Escapement joinEscapement(Escapement escape1, Escapement escape2) {
    if (escape1 == Escapement.GlobalEscape || escape2 == Escapement.GlobalEscape)
      return Escapement.GlobalEscape;
    else if (escape1 == Escapement.NoEscape)
      return escape2;
    else if (escape2 == Escapement.NoEscape)
      return escape1;
    else
      return escape1;
  }
  
  // why not need a nodeFlagSet to keep record of visited nodes?
  // because, we do recursion only when "oldEscape.ordinal() < newEscape.ordinal()"
  // escapement of a node only moves in a one-way direction
  private void propagateEscapement(Node node) {    
    Escapement newEscape = node.getEscapment();
    if (node instanceof ObjectNode) {
      ObjectNode objNode = (ObjectNode)node;
      Set<String> fieldNodeNameSet = objNode.getFieldNodeNameSet();
      for (String fieldNodeName : fieldNodeNameSet) {
        ReferenceNode fieldNode = this.referenceNodeMap.get(fieldNodeName);
        Escapement oldEscape = fieldNode.getEscapment();
        if (oldEscape.ordinal() < newEscape.ordinal()) {
          fieldNode.setEscapement(newEscape);
          propagateEscapement(fieldNode);
        }
      }
    } else {
      ReferenceNode refNode = (ReferenceNode)node;
      Set<String> deferredNodeNameSet = refNode.getDeferredNodeNameSet();
      for (String deferredNodeName : deferredNodeNameSet) {
        ReferenceNode deferredNode = this.referenceNodeMap.get(deferredNodeName);
        Escapement oldEscape = deferredNode.getEscapment();
        if (oldEscape.ordinal() < newEscape.ordinal()) {
          deferredNode.setEscapement(newEscape);
          propagateEscapement(deferredNode);
        }
      }
      Set<String> pointsToNodeNameSet = refNode.getPointsToNodeNameSet();
      for (String pointsToNodeName : pointsToNodeNameSet) {
        ObjectNode pointsToNode = this.objectNodeMap.get(pointsToNodeName);
        Escapement oldEscape = pointsToNode.getEscapment();
        if (oldEscape.ordinal() < newEscape.ordinal()) {
          pointsToNode.setEscapement(newEscape);
          propagateEscapement(pointsToNode);
        }
      }
    }
  }
  
  private Set<ObjectNode> getPointsToSet(ReferenceNode refNode, Set<ReferenceNode> refNodeFlagSet) {
    Set<ObjectNode> ptSet = new HashSet<>();
    if (refNodeFlagSet.contains(refNode))
      return ptSet;
    refNodeFlagSet.add(refNode);
    
    for (String objNodeName : refNode.getPointsToNodeNameSet()) {
      ObjectNode objNode = getObjectNode(objNodeName, false);
      ptSet.add(objNode);
    }
    
    for (String refNodeName : refNode.getDeferredNodeNameSet()) {
      ReferenceNode deferRefNode = getReferenceNode(refNodeName, false);
      ptSet.addAll(getPointsToSet(deferRefNode, refNodeFlagSet));
    }
    
    return ptSet;
  }
  
  private Set<ReferenceNode> getDeferredReferenceNodeSet(ReferenceNode refNode, Set<ReferenceNode> refNodeFlagSet) {
    Set<ReferenceNode> deferRefNodeSet = new HashSet<>();
    if (refNodeFlagSet.contains(refNode))
      return deferRefNodeSet;
    refNodeFlagSet.add(refNode);
    
    for (String deferRefNodeName : refNode.getDeferredNodeNameSet()) {
      ReferenceNode deferRefNode = getReferenceNode(deferRefNodeName, false);
      deferRefNodeSet.add(deferRefNode);
      deferRefNodeSet.addAll(getDeferredReferenceNodeSet(deferRefNode, refNodeFlagSet));
    }
    
    return deferRefNodeSet;
  }
  
  private void bypassReferenceNode(ReferenceNode refNode) {
    // since these sets inside the refNode are going to change in loops, we make copies of them
    Set<String> deferredNodeNameSet = new HashSet<>(refNode.getDeferredNodeNameSet());
    Set<String> pointsToNodeNameSet = new HashSet<>(refNode.getPointsToNodeNameSet());
    Set<String> predNodeNameSet = new HashSet<>(refNode.getPredecessorNameSet());
    
    for (String predNodeName : predNodeNameSet) {
      ReferenceNode predRefNode = getReferenceNode(predNodeName, false);
      if (predRefNode == null)
        continue;
      
      for (String deferredNodeName : deferredNodeNameSet) {
        ReferenceNode deferredNode = getReferenceNode(deferredNodeName, false);
        predRefNode.addDeferredEdge(deferredNode);
      }
      
      for (String pointsToNodeName : pointsToNodeNameSet) {
        ObjectNode pointsToNode = getObjectNode(pointsToNodeName, false);
        predRefNode.addPointsToEdge(pointsToNode);
      }
      
      predRefNode.removeEdge(refNode);
    }
    
    for (String deferredNodeName : deferredNodeNameSet) {
      ReferenceNode deferredNode = getReferenceNode(deferredNodeName, false);
      refNode.removeEdge(deferredNode);
    }
    
    for (String pointsToNodeName : pointsToNodeNameSet) {
      ObjectNode pointsToNode = getObjectNode(pointsToNodeName, false);
      refNode.removeEdge(pointsToNode);
    }
  }
  
  private boolean isConnected(Node srcNode, Node dstNode, Set<Node> nodeFlagSet) {
    if (srcNode == null || dstNode == null)
      return false;
    
    Set<String> predNodeNameSet = dstNode.getPredecessorNameSet();
    if (predNodeNameSet.contains(srcNode.getName()))
      return true;
    
    for (String predNodeName : predNodeNameSet) {
      Node predNode = getNode(predNodeName);
      if (predNode == null || nodeFlagSet.contains(predNode))
        continue;
      nodeFlagSet.add(predNode);
      if (isConnected(srcNode, predNode, nodeFlagSet))
        return true;
    }
    
    return false;
  }
  
  final public void summarizeConnectionGraph(EscapeSummary summary) {
    for (ReferenceNode refNode : this.referenceNodeMap.values())
      if (refNode.getEscapment() != Escapement.NoEscape)
        propagateEscapement(refNode);
    
    // collect all the objects that may escape
    for (ObjectNode objNode : this.objectNodeMap.values()) {
      String objNodeName = objNode.getName();
      if (!objNodeName.startsWith("o"))
        continue;
      Escapement escape = objNode.getEscapment();
      if (escape == Escapement.ArgEscape)
        summary.addProceduralObjectNodeName(objNodeName);
      else if (escape == Escapement.GlobalEscape)
        summary.addGlobalObjectNodeName(objNodeName);
    }
    
    // collect argument, return, and summary nodes that may connect to some escaping objects
    ReferenceNode retNode = this.referenceNodeMap.get("return");
    if (retNode != null && retNode.getEscapment() == Escapement.GlobalEscape)
      summary.addGlobalArgumentNodeName(retNode.getName());
    
    for (ReferenceNode refNode : this.referenceNodeMap.values()) {
      String refNodeName = refNode.getName();
      if (refNodeName.startsWith("a")) {
        Set<Node> nodeFlagSet = new HashSet<>();
        if (isConnected(retNode, refNode, nodeFlagSet))
          summary.addReachableArgumentNodeName(refNodeName);
        if (refNode.getEscapment() == Escapement.GlobalEscape)
          summary.addGlobalArgumentNodeName(refNodeName);
      } else if (refNodeName.startsWith("s")) {
        Escapement escape = refNode.getEscapment();
        if (escape == Escapement.ArgEscape)
          summary.addProceduralSummaryName(refNodeName);
        else if (escape == Escapement.GlobalEscape)
          summary.addGlobalSummaryName(refNodeName);
      }
    }
  }
  
  // I don't want to override equals() method
  final public boolean isEqual(ConnectionGraph otherGraph) {
    if (this.objectNodeMap.size() != otherGraph.objectNodeMap.size())
      return false;
    else if (this.referenceNodeMap.size() != otherGraph.referenceNodeMap.size())
      return false;
    
    for (Map.Entry<String, ObjectNode> objNodeMapEnt : this.objectNodeMap.entrySet()) {
      String objNodeName = objNodeMapEnt.getKey();
      ObjectNode objNode = objNodeMapEnt.getValue();
      ObjectNode otherObjNode = otherGraph.objectNodeMap.get(objNodeName);
      if (otherObjNode == null)
        return false;
      Set<String> fieldNodeNameSet = objNode.getFieldNodeNameSet();
      Set<String> otherFieldNodeNameSet = otherObjNode.getFieldNodeNameSet();
      if (!fieldNodeNameSet.equals(otherFieldNodeNameSet))
        return false;
    }
    
    for (Map.Entry<String, ReferenceNode> refNodeMapEnt : this.referenceNodeMap.entrySet()) {
      String refNodeName = refNodeMapEnt.getKey();
      ReferenceNode refNode = refNodeMapEnt.getValue();
      ReferenceNode otherRefNode = otherGraph.referenceNodeMap.get(refNodeName);
      if (otherRefNode == null)
        return false;
      Set<String> deferredNodeNameSet = refNode.getDeferredNodeNameSet();
      Set<String> otherDeferredNodeNameSet = otherRefNode.getDeferredNodeNameSet();
      if (!deferredNodeNameSet.equals(otherDeferredNodeNameSet))
        return false;
      Set<String> pointsToNodeNameSet = refNode.getPointsToNodeNameSet();
      Set<String> otherPointsToNodeNameSet = otherRefNode.getPointsToNodeNameSet();
      if (!pointsToNodeNameSet.equals(otherPointsToNodeNameSet))
        return false;
    }
    
    return true;
  }
  
  final public void mergeConnectionGraph(ConnectionGraph otherGraph) {
    for (Map.Entry<String, ObjectNode> otherObjNodeMapEnt : otherGraph.objectNodeMap.entrySet()) {
      String objNodeName = otherObjNodeMapEnt.getKey();
      ObjectNode otherObjNode = otherObjNodeMapEnt.getValue();
      ObjectNode objNode = this.objectNodeMap.get(objNodeName);
      if (objNode == null) {
        objNode = otherObjNode.cloneItself();
        this.objectNodeMap.put(objNodeName, objNode);
        continue;
      }
      
      // take the join of escapements
      Escapement escape = joinEscapement(otherObjNode.getEscapment(), objNode.getEscapment());
      objNode.setEscapement(escape);
      
      Set<String> needFieldNodeNameSet = new HashSet<>(otherObjNode.getFieldNodeNameSet());
      needFieldNodeNameSet.removeAll(objNode.getFieldNodeNameSet());
      for (String needFieldNodeName : needFieldNodeNameSet) {
        ReferenceNode needFieldNode = getNonStaticFieldNode(needFieldNodeName, true);
        objNode.addFieldEdge(needFieldNode);
      }
    }
    
    for (Map.Entry<String, ReferenceNode> otherRefNodeMapEnt : otherGraph.referenceNodeMap.entrySet()) {
      String refNodeName = otherRefNodeMapEnt.getKey();
      ReferenceNode otherRefNode = otherRefNodeMapEnt.getValue();
      ReferenceNode refNode = this.referenceNodeMap.get(refNodeName);
      if (refNode == null) {
        refNode = otherRefNode.cloneItself();
        this.referenceNodeMap.put(refNodeName, refNode);
        continue;
      }
      
      // take the join of escapements
      Escapement escape = joinEscapement(otherRefNode.getEscapment(), refNode.getEscapment());
      refNode.setEscapement(escape);
      
      Set<String> needDeferredNodeNameSet = new HashSet<>(otherRefNode.getDeferredNodeNameSet());
      needDeferredNodeNameSet.removeAll(refNode.getDeferredNodeNameSet());
      for (String needDeferredNodeName : needDeferredNodeNameSet) {
        ReferenceNode needDeferredNode = getReferenceNode(needDeferredNodeName, true);
        refNode.addDeferredEdge(needDeferredNode);
      }
      
      Set<String> needPointsToNodeNameSet = new HashSet<>(otherRefNode.getPointsToNodeNameSet());
      needPointsToNodeNameSet.removeAll(refNode.getPointsToNodeNameSet());
      for (String needPointsToNodeName : needPointsToNodeNameSet) {
        ObjectNode needPointsToNode = getObjectNode(needPointsToNodeName, true);
        refNode.addPointsToEdge(needPointsToNode);
      }
    }
  }
  
  final public void updateConnectionGraph(Procedure proc, ISSABasicBlock BB) {
    Iterator<SSAInstruction> instIter = BB.iterator();
    while (instIter.hasNext()) {
      SSAInstruction inst = instIter.next();
      if (inst instanceof SSAPhiInstruction) {
        // --------> p = phi(q1, q2, ...);
        SSAPhiInstruction phiInst = (SSAPhiInstruction)inst;
        
        String refNodeName = "r" + phiInst.getDef();
        ReferenceNode refNode = getReferenceNode(refNodeName, true);
        
        // since the program is in SSA form, we never reassign p, so we don't need bypass p
        //bypassReferenceNode(refNode);
        for (int i = 0; i < phiInst.getNumberOfUses(); i++) {
          String useNodeName = "r" + phiInst.getUse(i);
          ReferenceNode useNode = getReferenceNode(useNodeName, true);
          refNode.addDeferredEdge(useNode);
        }
      } else if (inst instanceof SSANewInstruction) {
        // --------> p = new T();
        SSANewInstruction newInst = (SSANewInstruction)inst;
        
        String refNodeName = "r" + newInst.getDef();
        ReferenceNode refNode = getReferenceNode(refNodeName, true);
        
        NewSiteReference newSiteRef = newInst.getNewSite();
        String objNodeName = "o" + newSiteRef.getProgramCounter();
        ObjectNode objNode = getObjectNode(objNodeName, true);
        
        // since the program is in SSA form, we never reassign p, so we don't need bypass p
        //bypassReferenceNode(refNode);
        refNode.addPointsToEdge(objNode);
      } else if (inst instanceof SSAGetInstruction) {
        // --------> p = q.f;
        SSAGetInstruction getInst = (SSAGetInstruction)inst;
        FieldReference fieldRef = getInst.getDeclaredField();
        if (fieldRef.getFieldType().isPrimitiveType())
          continue;
        
        // refNode corresponds to "p"
        String refNodeName = "r" + getInst.getDef();
        ReferenceNode refNode = getReferenceNode(refNodeName, true);
        
        String fieldName = fieldRef.getName().toString();
        Set<ReferenceNode> fieldRefNodeSet = new HashSet<>();
        if (getInst.isStatic()) {
          String typeName = fieldRef.getDeclaringClass().getName().toString();
          String fullFieldName = typeName + "." + fieldName;
          ReferenceNode staticFieldRefNode = getStaticFieldNode(fullFieldName, true);
          fieldRefNodeSet.add(staticFieldRefNode);
        } else {
          // useNode corresponds to "q"
          String useNodeName = "r" + getInst.getRef();
          ReferenceNode useNode = getReferenceNode(useNodeName, true);
          Set<ReferenceNode> refNodeFlagSet = new HashSet<>();
          Set<ObjectNode> ptSet = getPointsToSet(useNode, refNodeFlagSet);
          if (ptSet.isEmpty()) {
            ObjectNode phanNode = getPhantomNode(useNodeName, true);
            refNodeFlagSet.clear();
            Set<ReferenceNode> deferRefNodeSet = getDeferredReferenceNodeSet(useNode, refNodeFlagSet);
            for (ReferenceNode deferRefNode : deferRefNodeSet)
              deferRefNode.addPointsToEdge(phanNode);
            ptSet.add(phanNode);
          }
          
          for (ObjectNode objNode : ptSet) {
            String objName = objNode.getName();
            String fullFieldName = objName + "." + fieldName;
            ReferenceNode fieldRefNode = getNonStaticFieldNode(fullFieldName, true);
            // since it's added to HashSet, even it's already in the set, re-adding is OK
            objNode.addFieldEdge(fieldRefNode);
            fieldRefNodeSet.add(fieldRefNode);
          }
        }
        
        // since the program is in SSA form, we never reassign p, so we don't need bypass p
        //bypassReferenceNode(refNode);
        for (ReferenceNode fieldRefNode : fieldRefNodeSet)
          refNode.addDeferredEdge(fieldRefNode);
      } else if (inst instanceof SSAPutInstruction) {
        // --------> p.f = q;
        SSAPutInstruction putInst = (SSAPutInstruction)inst;
        FieldReference fieldRef = putInst.getDeclaredField();
        if (fieldRef.getFieldType().isPrimitiveType())
          continue;
        
        // refNode corresponds to "q"
        String refNodeName = "r" + putInst.getVal();
        ReferenceNode refNode = getReferenceNode(refNodeName, true);
        
        String fieldName = fieldRef.getName().toString();
        Set<ReferenceNode> fieldRefNodeSet = new HashSet<>();
        if (putInst.isStatic()) {
          String typeName = fieldRef.getDeclaringClass().getName().toString();
          String fullFieldName = typeName + "." + fieldName;
          ReferenceNode staticFieldRefNode = getStaticFieldNode(fullFieldName, true);
          fieldRefNodeSet.add(staticFieldRefNode);
        } else {
          // useNode corresponds to "p"
          String useNodeName = "r" + putInst.getRef();
          ReferenceNode useNode = getReferenceNode(useNodeName, true);
          Set<ReferenceNode> refNodeFlagSet = new HashSet<>();
          Set<ObjectNode> ptSet = getPointsToSet(useNode, refNodeFlagSet);
          if (ptSet.isEmpty()) {
            ObjectNode phanNode = getPhantomNode(useNodeName, true);
            refNodeFlagSet.clear();
            Set<ReferenceNode> deferRefNodeSet = getDeferredReferenceNodeSet(useNode, refNodeFlagSet);
            for (ReferenceNode deferRefNode : deferRefNodeSet)
              deferRefNode.addPointsToEdge(phanNode);
            ptSet.add(phanNode);
          }
          
          for (ObjectNode objNode : ptSet) {
            String objName = objNode.getName();
            String fullFieldName = objName + "." + fieldName;
            ReferenceNode fieldRefNode = getNonStaticFieldNode(fullFieldName, true);
            // since it's added to HashSet, even it's already in the set, re-adding is OK
            objNode.addFieldEdge(fieldRefNode);
            fieldRefNodeSet.add(fieldRefNode);
          }
        }
        
        // we cannot in general kill whatever p.f was pointing to, so we do not apply bypassReferenceNode(fieldRefNode)
        for (ReferenceNode fieldRefNode : fieldRefNodeSet)
          fieldRefNode.addDeferredEdge(refNode);
      } else if (inst instanceof SSAArrayStoreInstruction) {
        // --------> p[i] = q;
        SSAArrayStoreInstruction storeInst = (SSAArrayStoreInstruction)inst;
        if (storeInst.typeIsPrimitive())
          continue;
        
        // refNode corresponds to "q"
        String refNodeName = "r" + storeInst.getValue();
        ReferenceNode refNode = getReferenceNode(refNodeName, true);
        
        // useNode corresponds to "p"
        String useNodeName = "r" + storeInst.getArrayRef();
        ReferenceNode useNode = getReferenceNode(useNodeName, true);
        Set<ReferenceNode> refNodeFlagSet = new HashSet<>();
        Set<ObjectNode> ptSet = getPointsToSet(useNode, refNodeFlagSet);
        if (ptSet.isEmpty()) {
          ObjectNode phanNode = getPhantomNode(useNodeName, true);
          refNodeFlagSet.clear();
          Set<ReferenceNode> deferRefNodeSet = getDeferredReferenceNodeSet(useNode, refNodeFlagSet);
          for (ReferenceNode deferRefNode : deferRefNodeSet)
            deferRefNode.addPointsToEdge(phanNode);
          ptSet.add(phanNode);
        }
        
        // we model i as a field in the array object
        String fieldName = "r" + storeInst.getIndex();
        for (ObjectNode objNode : ptSet) {
          String objName = objNode.getName();
          String fullFieldName = objName + "." + fieldName;
          ReferenceNode fieldRefNode = getNonStaticFieldNode(fullFieldName, true);
          objNode.addFieldEdge(fieldRefNode);
          fieldRefNode.addDeferredEdge(refNode);
        }
      } else if (inst instanceof SSAReturnInstruction) {
        // --------> return p;
        SSAReturnInstruction retInst = (SSAReturnInstruction)inst;
        if (retInst.returnsVoid() || retInst.returnsPrimitiveType())
          continue;
        
        String refNodeName = "r" + retInst.getResult();
        ReferenceNode refNode = getReferenceNode(refNodeName, true);
        ReferenceNode retNode = getArgumentNode("return", true);
        retNode.addDeferredEdge(refNode);
      } else if (inst instanceof SSAInvokeInstruction) {
        // --------> q = p.foo(x, y, z);
        // retNode is q, argNodeList[0] is p, argNodeList[1] is x, argNodeList[2] is y, ...
        SSAInvokeInstruction callInst = (SSAInvokeInstruction)inst;
        CallSiteReference callSiteRef = callInst.getCallSite();
        ReferenceNode retNode = null;
        if (callInst.getNumberOfReturnValues() != 0) {
          String retNodeName = "r" + callInst.getReturnValue(0);
          retNode = getReferenceNode(retNodeName, true);
        }
        
        ArrayList<ReferenceNode> argNodeList = new ArrayList<>();
        for (int i = 0; i < callInst.getNumberOfUses(); i++) {
          String argNodeName = "r" + callInst.getUse(i);
          ReferenceNode argNode = getReferenceNode(argNodeName, true);
          argNodeList.add(argNode);
        }
        
        Set<Procedure> calleeSet = proc.getCalleeSet(BB);
        for (Procedure callee : calleeSet) {
          EscapeSummary summary = callee.getEscapeSummary();
          if (summary == null)
            continue;
          // make a summary node to represent this callee
          String sumNodeName = "s" + callSiteRef.getProgramCounter() + "-" + callee.getFullSignature();
          ReferenceNode sumNode = getReferenceNode(sumNodeName, true);
          
          if (retNode != null) {
            retNode.addDeferredEdge(sumNode);
            if (summary.getGlobalArgumentNodeNameSet().contains("return"))
              retNode.setEscapement(Escapement.GlobalEscape);
          }
          
          for (int i = 0; i < argNodeList.size(); i++) {
            ReferenceNode argNode = argNodeList.get(i);
            String cntrpartName = "a" + i;
            if (summary.getReachableArgumentNodeNameSet().contains(cntrpartName))
              sumNode.addDeferredEdge(argNode);
            if (summary.getGlobalArgumentNodeNameSet().contains(cntrpartName))
              argNode.setEscapement(Escapement.GlobalEscape);
          }
        }
      }
    }
  }
}
