package core.escape;

import com.rits.cloning.Cloner;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author zzk
 */
public class ObjectNode extends Node {
  private ObjectNodeType  objectNodeType;
  private Set<String>     fieldNodeNameSet = new HashSet<>();
  
  public ObjectNode(String objNodeName, ObjectNodeType objNodeType) {
    super(objNodeName, Escapement.NoEscape);
    this.objectNodeType = objNodeType;
  }
  
  final public ObjectNodeType getObjectNodeType() {
    return this.objectNodeType;
  }
  
  final public void addFieldEdge(ReferenceNode fieldNode) {
    this.fieldNodeNameSet.add(fieldNode.getName());
    fieldNode.addPredecessor(this);
  }
  
  final public Set<String> getFieldNodeNameSet() {
    return this.fieldNodeNameSet;
  }
  
  final public ObjectNode cloneItself() {
    Cloner cloner = new Cloner();
    ObjectNode objNode = cloner.deepClone(this);
    return objNode;
  }
}
