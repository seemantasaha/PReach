/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAAbstractBinaryInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ibm.wala.ssa.ISSABasicBlock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import com.ibm.wala.shrikeBT.BinaryOpInstruction;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;

/**
 *
 * @author madeline
 * @author tm
 */
public class OtherAnalysis {
  static private Set<Procedure> moduloSet = new HashSet<>();
  static private Set<Procedure> stringCompSet = new HashSet<>();
  
  /*
  Finds the set of procedures that contain the Modulo function
  */
  static public void deriveModuloSet() {
    for (Procedure proc : Program.getProcedureSet()) {
      ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = proc.getCFG();
      Iterator<ISSABasicBlock> nodeIter = cfg.iterator();
      while (nodeIter.hasNext()) {
        ISSABasicBlock node = nodeIter.next();
        Iterator<SSAInstruction> instIter = node.iterator();
        while (instIter.hasNext()) {
          SSAInstruction inst = instIter.next();
          if (inst instanceof SSABinaryOpInstruction) { //unsure what type getOperator() works on
            SSABinaryOpInstruction check = (SSABinaryOpInstruction)inst;
            String test = check.getOperator().toString();
            if (test.equals("rem")) {
                moduloSet.add(proc);
            }
          }
        }
      }
    }
  }
  
  static public void deriveStringCompSet() {
    for (Procedure proc : Program.getProcedureSet()) {
      ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = proc.getCFG();
      Iterator<ISSABasicBlock> nodeIter = cfg.iterator();
      while (nodeIter.hasNext()) {
        ISSABasicBlock node = nodeIter.next();
        Iterator<SSAInstruction> instIter = node.iterator();
        while (instIter.hasNext()) {
          SSAInstruction inst = instIter.next();
          if (inst instanceof SSAFieldAccessInstruction) {
            SSAFieldAccessInstruction check =
                    (SSAFieldAccessInstruction)inst;
            if (check.isStatic()) {
              System.out.println(check.getDeclaredField().getSignature()); 
              if (check.getDeclaredField().getSignature().
                      contains("Ljava/lang/String.CASE_INSENSITIVE_ORDER")) {
                  stringCompSet.add(proc);
              }
            }
          }
        }
      }
    }
  }
  
  static public Set<Procedure> getModuloSet() {
    return moduloSet;
  }
  
  static public Set<Procedure> getStringCompSet() {
    return stringCompSet;    
  }
}
