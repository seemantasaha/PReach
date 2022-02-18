package core.escape;

import com.rits.cloning.Cloner;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author zzk
 */
public class ReferenceNode extends Node {
  private ReferenceNodeType referenceNodeType;
  private Set<String>       deferredNodeNameSet = new HashSet<>();
  private Set<String>       pointsToNodeNameSet = new HashSet<>();
  
  public ReferenceNode(String refNodeName, ReferenceNodeType refNodeType) {
    super(refNodeName, Escapement.NoEscape);
    if (refNodeType == ReferenceNodeType.Actual)
      setEscapement(Escapement.ArgEscape);
    else if (refNodeType == ReferenceNodeType.StaticField)
      setEscapement(Escapement.GlobalEscape);
    this.referenceNodeType = refNodeType;
  }
  
  final public ReferenceNodeType getReferenceNodeType() {
    return this.referenceNodeType;
  }
  
  final public void addDeferredEdge(ReferenceNode refNode) {
    this.deferredNodeNameSet.add(refNode.getName());
    refNode.addPredecessor(this);
  }
  
  final public void addPointsToEdge(ObjectNode objNode) {
    this.pointsToNodeNameSet.add(objNode.getName());
    objNode.addPredecessor(this);
  }
  
  // if an edge is ReferenceNode -> ReferenceNode, it's a deferred edge
  // if an edge is ReferenceNode -> ObjectNode, it's a points-to edge
  // if an edge is ObjectNode -> ReferenceNode, it's a field edge
  final public void removeEdge(Node node) {
    if (node instanceof ReferenceNode)
      this.deferredNodeNameSet.remove(node.getName());
    else
      this.pointsToNodeNameSet.remove(node.getName());
    node.removePredecessor(this);
  }
  
  final public Set<String> getDeferredNodeNameSet() {
    return this.deferredNodeNameSet;
  }
  
  final public Set<String> getPointsToNodeNameSet() {
    return this.pointsToNodeNameSet;
  }
  
  final public ReferenceNode cloneItself() {
    Cloner cloner = new Cloner();
    ReferenceNode refNode = cloner.deepClone(this);
    return refNode;
  }
}
