package core.escape;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author zzk
 */
abstract public class Node {
  private String      name;
  private Escapement  escapement;
  private Set<String> predecessorNameSet = new HashSet<>();
  
  public Node(String name, Escapement escape) {
    this.name = name;
    this.escapement = escape;
  }
  
  final public String getName() {
    return this.name;
  }
  
  final public Escapement getEscapment() {
    return this.escapement;
  }
  
  final public void setEscapement(Escapement escape) {
    this.escapement = escape;
  }
  
  final public void addPredecessor(Node predNode) {
    this.predecessorNameSet.add(predNode.name);
  }
  
  final public void removePredecessor(Node predNode) {
    this.predecessorNameSet.remove(predNode.name);
  }
  
  final public Set<String> getPredecessorNameSet() {
    return this.predecessorNameSet;
  }
}
