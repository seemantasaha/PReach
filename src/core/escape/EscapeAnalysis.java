package core.escape;

import core.Procedure;
import core.Program;
import java.util.LinkedList;

/**
 *
 * @author zzk
 */
public class EscapeAnalysis {
  static public void performEscapeAnalysis() {
    LinkedList<Procedure> procPostOrderList = Program.getProcedurePostOrderList();
    
    // paper says: we iterate over the nodes in the call graph 
    // in a reverse topological order until the data flow solution converges
    while (true) {
      boolean converge = true;
      for (Procedure proc : procPostOrderList) {
        IntraEscapeAnalysis analysis = new IntraEscapeAnalysis(proc);
        EscapeSummary oldSummary = proc.getEscapeSummary();
        EscapeSummary newSummary = analysis.getEscapeSummary();
        if (oldSummary == null || !newSummary.isEqual(oldSummary)) {
          converge = false;
          proc.setEscapeSummary(newSummary);
        }
      }
      if (converge)
        break;
    }
  }
}
