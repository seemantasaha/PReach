package cmd;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import core.Loop;
import core.NestedLoop;
import core.Procedure;
import core.Program;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;

/**
 *
 * @author zzk
 */
public class CG extends BaseGraph<Procedure> {
  public CG() {
    CallGraph cg = Program.getCallGraph();
    Set<CGNode> cgNodeSet = new HashSet<>();
    Stack<CGNode> cgNodeStk = new Stack<>();
    for (CGNode entryCGNode : cg.getEntrypointNodes()) {
      // if this entryCGNode has no procedure object in the program
      if (Program.getProcedure(entryCGNode) == null)
        continue;
      // if this entryCGNode is alone by itself
      boolean needFlag = false;
      Iterator<CGNode> succCGNodeIter = cg.getSuccNodes(entryCGNode);
      while (succCGNodeIter.hasNext()) {
        CGNode succCGNode = succCGNodeIter.next();
        if (Program.getProcedure(succCGNode) != null)
          needFlag = true;
      }
      if (needFlag) {
        cgNodeStk.push(entryCGNode);
        cgNodeSet.add(entryCGNode);
      }
    }
    
    while (!cgNodeStk.empty()) {
      CGNode top = cgNodeStk.pop();
      Procedure proc = Program.getProcedure(top);
      if (getVertex(proc) == null)
        addVertex(proc, 1, proc.getProcedureName());
      
      Iterator<CGNode> cgNodeIter = cg.getSuccNodes(top);
      while (cgNodeIter.hasNext()) {
        CGNode succ = cgNodeIter.next();
        Procedure succProc = Program.getProcedure(succ);
        //!succ.getMethod().getDeclaringClass().getClassLoader().getReference().equals(ClassLoaderReference.Application)
        if (succProc == null)
          continue;
        
        if (getVertex(succProc) == null)
          addVertex(succProc, 1, succProc.getProcedureName());
        
        addEdge(proc, succProc, null);
        
        if (!cgNodeSet.contains(succ)) {
          cgNodeStk.push(succ);
          cgNodeSet.add(succ);
        }
      }
    }
    
    layoutGraph();
  }
  
  /*added by Madeline Sgro 7/6/2017
  scales vertices for every vertex in a procedure set by a given scaleFactor
  */
  final public void scaleProcedureSet(Set<Procedure> procSet, double scaleFactor) {
    for (Procedure proc : procSet){
      scaleVertex(proc, scaleFactor);
    }
  }
  
  final public void paintProcedure(Procedure proc, String color) {
    colorVertex(proc, color);
  }
  
  final public void paintProcedureSet(Set<Procedure> procSet, String color) {
    for (Procedure proc : procSet)
      colorVertex(proc, color);
  }
  
  final public void paintNestedLoop(NestedLoop nestedLoop, Set<Loop> filteredLoopSet) {
    Iterator<Loop> nestedLoopIter = nestedLoop.iterator();
    // this is safe, since a nested loop has at least one loop level
    Loop loop = nestedLoopIter.next();
    Procedure proc = loop.getProcedure();
    
    if (!filteredLoopSet.contains(loop))
      colorVertex(proc, "yellow");
    else
      colorVertex(proc, "cyan");
    
    while (nestedLoopIter.hasNext()) {
      Loop subLoop = nestedLoopIter.next();
      Procedure subProc = subLoop.getProcedure();
      if (subProc == proc) {
        if (!filteredLoopSet.contains(subLoop))
          colorVertex(subProc, "yellow");
        loop = subLoop;
        continue;
      }
      
      if (!filteredLoopSet.contains(subLoop))
        colorVertex(subProc, "yellow");
      else
        colorVertex(subProc, "cyan");
      
      Set<ArrayList<Procedure>> subLoopPathSet = loop.getSubsidiaryLoopPathSet(subLoop);
      for (ArrayList<Procedure> subLoopPath : subLoopPathSet) {
        for (Procedure pathProc : subLoopPath)
          colorVertex(pathProc, "pink");
      }
      
      loop = subLoop;
      proc = subProc;
    }
  }
}
