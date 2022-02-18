package core;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author zzk
 */
public class Recursion {
  private Set<Procedure> recursionEntrySet = new HashSet<>();
  private Set<Procedure> recursionBody = new HashSet<>();
  
  public Recursion(Set<Procedure> procSet) {
    this.recursionBody.addAll(procSet);
    for (Procedure proc : this.recursionBody) {
      Set<Procedure> callerSet = proc.getCallerSet();
      if (this.recursionBody.containsAll(callerSet))
        continue;
      this.recursionEntrySet.add(proc);
    }
    if (this.recursionEntrySet.isEmpty())
      this.recursionEntrySet.add(procSet.iterator().next());
  }
  
  final public Set<Procedure> getRecursionEntrySet() {
    return this.recursionEntrySet;
  }
  
  final public Set<Procedure> getRecursionBody() {
    return this.recursionBody;
  }
}
