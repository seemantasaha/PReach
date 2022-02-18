package core;

import com.ibm.wala.ssa.ISSABasicBlock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

/**
 *
 * @author zzk
 */
public class NestedLoopAnalysis {
  static private Set<NestedLoop> interproceduralNestedLoopSet = new HashSet<>();
  
  static private void computeLoopDependence(Procedure proc, Set<Procedure> flagSet) {
    if (flagSet.contains(proc))
      return;
    flagSet.add(proc);
    
    // for each loop, find what loops can be reached in ANOTHER method (i.e. the methods called on this loop level)
    Set<Loop> loopSet = proc.getLoopSet();
    for (Loop loop : loopSet) {
      Set<ISSABasicBlock> callNodeSet = proc.getTopLevelCallNodeSet(loop);
      for (ISSABasicBlock callNode : callNodeSet) {
        Set<Procedure> calleeSet = proc.getCalleeSet(callNode);
        for (Procedure callee : calleeSet) {
          Stack<Procedure> procStk = new Stack<>();
          Set<Procedure> procSet = new HashSet<>();
          procStk.push(callee);
          procSet.add(callee);
          
          while (!procStk.empty()) {
            Procedure popProc = procStk.pop();
            Set<ISSABasicBlock> reachCallNodeSet = new HashSet<>(popProc.getCallNodeSet());
            Set<Loop> topLoopSet = popProc.getTopLoopSet();
            // we only care about top loops; if a method is called inside a top loop, we don't care 
            for (Loop topLoop : topLoopSet) {
              ArrayList<Procedure> path = new ArrayList<>(procStk);
              
              // let us ensure no inter-dependence loop is introduced!!!!!
              // (1) this topLoop is the loop, i.e. it depends on itself
              // (2) this topLoop is already a superior of the loop
              if (topLoop == loop || topLoop.isSubsidiary(loop))
                continue;
              
              topLoop.addDependenceLoop(loop, path);
              Set<ISSABasicBlock> topLoopBody = topLoop.getLoopBody();
              reachCallNodeSet.removeAll(topLoopBody);
            }
            
            // continue with the methods called outside any top loop
            for (ISSABasicBlock reachCallNode : reachCallNodeSet) {
              Set<Procedure> reachCalleeSet = popProc.getCalleeSet(reachCallNode);
              boolean deeper = false;
              for (Procedure reachCallee : reachCalleeSet) {
                if (procSet.contains(reachCallee))
                  continue;
                // there is a method that can be explored, put back the original for tracing
                procStk.push(popProc);
                procStk.push(reachCallee);
                procSet.add(reachCallee);
                deeper = true;
                break;
              }
              if (deeper)
                break;
            }
          }
        }
      }
    }
    
    Set<Procedure> calleeSet = proc.getCalleeSet();
    for (Procedure callee : calleeSet)
      computeLoopDependence(callee, flagSet);
  }
  
  // get all the loops that directly cover this node
  // one iteration of a covering loop can execute the node once
  static private Set<Loop> collectCoveringLoopSet(Procedure proc, ISSABasicBlock node, Set<Procedure> flagSet) {
    Set<Loop> loopSet = new HashSet<>();
    if (flagSet.contains(proc))
      return loopSet;
    flagSet.add(proc);
    
    Loop coverLoop = proc.getCoveringLoop(node);
    if (coverLoop != null) {
      loopSet.add(coverLoop);
      return loopSet;
    }
    
    // if coverLoop is null, it means this node is not in any loop of this procedure
    // then we need to consider the call nodes in their callers that invokes this procedure
    Set<Procedure> callerSet = proc.getCallerSet();
    for (Procedure caller : callerSet) {
      Set<ISSABasicBlock> callNodeSet = caller.getCallNodeSet(proc);
      for (ISSABasicBlock callNode : callNodeSet) {
        Set<Loop> tempLoopSet = collectCoveringLoopSet(caller, callNode, flagSet);
        loopSet.addAll(tempLoopSet);
      }
    }
    return loopSet;
  }
  
  // collect all the interprocedural-nested-loop-decks starting from this loop
  static private Set<NestedLoop> trackInterproceduralNestedLoopSet(Loop loop) {
    Set<NestedLoop> nestedLoopSet = new HashSet<>();
    Set<Loop> subLoopSet = loop.getSubsidiaryLoopSet();
    if (subLoopSet.isEmpty()) {
      NestedLoop nestedLoop = new NestedLoop();
      nestedLoop.addLast(loop);
      nestedLoopSet.add(nestedLoop);
      return nestedLoopSet;
    }
    
    // since any cyclic chain is already cut, we do not need to use flag set to check visited
    for (Loop subLoop : subLoopSet) {
      Set<NestedLoop> subNestedLoopSet = trackInterproceduralNestedLoopSet(subLoop);
      for (NestedLoop subNestedLoop : subNestedLoopSet) {
        subNestedLoop.addFirst(loop);
        nestedLoopSet.add(subNestedLoop);
      }
    }
    
    return nestedLoopSet;
  }
  
  // collect all the interprocedural-nested-loop-decks ending with this loop
  static private Set<NestedLoop> backtraceInterproceduralNestedLoopSet(Loop loop) {
    Set<NestedLoop> nestedLoopSet = new HashSet<>();
    Set<Loop> depLoopSet = loop.getDependenceLoopSet();
    if (depLoopSet.isEmpty()) {
      NestedLoop nestedLoop = new NestedLoop();
      nestedLoop.addFirst(loop);
      nestedLoopSet.add(nestedLoop);
      return nestedLoopSet;
    }
    
    // since any cyclic chain is already cut, we do not need to use flag set to check visited
    for (Loop depLoop : depLoopSet) {
      Set<NestedLoop> depNestedLoopSet = backtraceInterproceduralNestedLoopSet(depLoop);
      for (NestedLoop depNestedLoop : depNestedLoopSet) {
        depNestedLoop.addLast(loop);
        nestedLoopSet.add(depNestedLoop);
      }
    }
    
    return nestedLoopSet;
  }
  
  static public void deriveInterproceduralNestedLoopSet() {
    Set<Procedure> flagSet = new HashSet<>();
    for (Procedure entryProc : Program.getEntryProcedureSet())
      computeLoopDependence(entryProc, flagSet);
    
    // establish interprocedural nested loops
    for (Procedure proc : Program.getProcedureSet()) {
      Set<Loop> topLoopSet = proc.getTopLoopSet();
      for (Loop topLoop : topLoopSet) {
        // if this top-loop also depends on some other loop, it is not a topmost loop
        Set<Loop> depLoopSet = topLoop.getDependenceLoopSet();
        if (!depLoopSet.isEmpty())
          continue;
        
        Set<NestedLoop> nestedLoopSet = trackInterproceduralNestedLoopSet(topLoop);
        interproceduralNestedLoopSet.addAll(nestedLoopSet);
      }
    }
  }
  
  static public Set<NestedLoop> getInterproceduralNestedLoopSet() {
    return interproceduralNestedLoopSet;
  }
  
  static public Set<NestedLoop> getInterproceduralNestedLoopSet(Procedure proc, ISSABasicBlock node) {
    Set<NestedLoop> nestedLoopSet = new HashSet<>();
    Set<Procedure> flagSet = new HashSet<>();
    Set<Loop> coverLoopSet = collectCoveringLoopSet(proc, node, flagSet);
    for (Loop coverLoop : coverLoopSet) {
      Set<NestedLoop> tempNestedLoopSet = backtraceInterproceduralNestedLoopSet(coverLoop);
      for (NestedLoop tempNestedLoop : tempNestedLoopSet) {
        boolean addedFlag = false;
        for (NestedLoop nestedLoop : nestedLoopSet)
          if (nestedLoop.equals(tempNestedLoop)) {
            addedFlag = true;
            break;
          }
        if (!addedFlag)
          nestedLoopSet.add(tempNestedLoop);
      }
    }
    return nestedLoopSet;
  }
}
