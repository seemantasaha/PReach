package core.escape;

import core.Procedure;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author zzk
 */
public class EscapeSummary {
  private Procedure   procedure = null;
  private Set<String> globalObjectNodeNameSet = new HashSet<>();
  private Set<String> proceduralObjectNodeNameSet = new HashSet<>();
  private Set<String> globalArgumentNodeNameSet = new HashSet<>();
  private Set<String> reachableArgumentNodeNameSet = new HashSet<>();
  private Set<String> globalSummaryNameSet = new HashSet<>();
  private Set<String> proceduralSummaryNameSet = new HashSet<>();
  
  public EscapeSummary(Procedure proc) {
    this.procedure = proc;
  }
  
  final public Procedure getProcedure() {
    return this.procedure;
  }
  
  final public void addGlobalObjectNodeName(String objNodeName) {
    this.globalObjectNodeNameSet.add(objNodeName);
  }
  
  final public void addProceduralObjectNodeName(String objNodeName) {
    this.proceduralObjectNodeNameSet.add(objNodeName);
  }
  
  final public void addGlobalArgumentNodeName(String argNodeName) {
    this.globalArgumentNodeNameSet.add(argNodeName);
  }
  
  final public void addReachableArgumentNodeName(String argNodeName) {
    this.reachableArgumentNodeNameSet.add(argNodeName);
  }
  
  final public void addGlobalSummaryName(String procName) {
    this.globalSummaryNameSet.add(procName);
  }
  
  final public void addProceduralSummaryName(String procName) {
    this.proceduralSummaryNameSet.add(procName);
  }
  
  final public Set<String> getGlobalObjectNodeNameSet() {
    return this.globalObjectNodeNameSet;
  }
  
  final public Set<String> getProceduralObjectNodeNameSet() {
    return this.proceduralObjectNodeNameSet;
  }
  
  final public Set<String> getGlobalArgumentNodeNameSet() {
    return this.globalArgumentNodeNameSet;
  }
  
  final public Set<String> getReachableArgumentNodeNameSet() {
    return this.reachableArgumentNodeNameSet;
  }
  
  final public Set<String> getGlobalSummaryNameSet() {
    return this.globalSummaryNameSet;
  }
  
  final public Set<String> getProceduralSummaryNameSet() {
    return this.proceduralSummaryNameSet;
  }
  
  final public boolean isEqual(EscapeSummary otherSummary) {
    if (!this.globalObjectNodeNameSet.equals(otherSummary.globalObjectNodeNameSet))
      return false;
    else if (!this.proceduralObjectNodeNameSet.equals(otherSummary.proceduralObjectNodeNameSet))
      return false;
    else if (!this.globalArgumentNodeNameSet.equals((otherSummary.globalArgumentNodeNameSet)))
      return false;
    else if (!this.reachableArgumentNodeNameSet.equals(otherSummary.reachableArgumentNodeNameSet))
      return false;
    else if (!this.globalSummaryNameSet.equals(otherSummary.globalSummaryNameSet))
      return false;
    else if (!this.proceduralSummaryNameSet.equals(otherSummary.proceduralSummaryNameSet))
      return false;
    else
      return true;
  }
}
