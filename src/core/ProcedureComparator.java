package core;

import java.util.Comparator;

/**
 *
 * @author zzk
 */
public class ProcedureComparator implements Comparator<Procedure> {
  @Override
  public int compare(Procedure proc1, Procedure proc2) {
    String procName1 = proc1.getProcedureName();
    String procSig1 = proc1.getFullSignature();
    String procName2 = proc2.getProcedureName();
    String procSig2 = proc2.getFullSignature();
    if (procName1.equals(procName2))
      return procSig1.compareTo(procSig2);
    else if (procName1.equals("main"))
      return -1;
    else if (procName2.equals("main"))
      return 1;
    else
      return procName1.compareTo(procName2);
  }
}
