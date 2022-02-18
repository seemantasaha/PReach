package core;

import com.ibm.wala.types.TypeReference;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author zzk
 */
public class Argument {
  private TypeReference argumentType = null;
  private Set<Argument> calledMethodArgumentSet = new HashSet<>();
  
  public Argument(TypeReference typeRef) {
    this.argumentType = typeRef;
  }
  
  final public void addArgumentForMethodCalledOnThis(Argument arg) {
    this.calledMethodArgumentSet.add(arg);
  }
  
  final public TypeReference getArgumentType() {
    return this.argumentType;
  }
  
  final public Set<Argument> getArgumentSetForMethodCalledOnThis() {
    return this.calledMethodArgumentSet;
  }
}
