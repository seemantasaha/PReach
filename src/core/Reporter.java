package core;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

/**
 *
 * @author zzk
 */
public class Reporter {
  static public String getSSAInstructionString(SSAInstruction inst) {
    String instStr = "";
    if (inst == null)
      return instStr;
    
    if (inst instanceof SSAInvokeInstruction) {
      SSAInvokeInstruction invokeInst = (SSAInvokeInstruction)inst;
      if (invokeInst.getNumberOfReturnValues() != 0)
        instStr += "v" + invokeInst.getReturnValue(0) + " = ";
      CallSiteReference callSiteRef = invokeInst.getCallSite();
      MethodReference mthRef = invokeInst.getDeclaredTarget();
      int useNum = 0;
      if (callSiteRef.isStatic()) {
        TypeReference typeRef = mthRef.getDeclaringClass();
        instStr += typeRef.getName().getClassName().toString() + "." + mthRef.getName().toString();
      } else {
        int recv = invokeInst.getReceiver();
        useNum++;
        instStr += "v" + recv + "." + mthRef.getName().toString();
      }
      
      instStr += "(";
      while (useNum < invokeInst.getNumberOfParameters()) {
        int valNum = invokeInst.getUse(useNum);
        instStr += "v" + valNum;
        if (++useNum < invokeInst.getNumberOfParameters())
          instStr += ", ";
      }
      instStr += ")";
    } else if (inst instanceof SSAConditionalBranchInstruction) {
      SSAConditionalBranchInstruction branchInst = (SSAConditionalBranchInstruction)inst;
      // branchInst.getNumberOfUses() always returns 2
      instStr += "if v" + branchInst.getUse(0);
      IConditionalBranchInstruction.IOperator op = branchInst.getOperator();
      if (op == IConditionalBranchInstruction.Operator.EQ)
        instStr += " == v" + branchInst.getUse(1);
      else if (op == IConditionalBranchInstruction.Operator.NE)
        instStr += " != v" + branchInst.getUse(1);
      else if (op == IConditionalBranchInstruction.Operator.GE)
        instStr += " >= v" + branchInst.getUse(1);
      else if (op == IConditionalBranchInstruction.Operator.GT)
        instStr += " > v" + branchInst.getUse(1);
      else if (op == IConditionalBranchInstruction.Operator.LE)
        instStr += " <= v" + branchInst.getUse(1);
      else
        instStr += " < v" + branchInst.getUse(1);
    } else if (inst instanceof SSABinaryOpInstruction) {
      SSABinaryOpInstruction binOpInst = (SSABinaryOpInstruction)inst;
      instStr += "v" + binOpInst.getDef() + " = ";
      IBinaryOpInstruction.IOperator op = binOpInst.getOperator();
      if (op == IBinaryOpInstruction.Operator.ADD)
        instStr += "v" + binOpInst.getUse(0) + " + " + "v" + binOpInst.getUse(1);
      else if (op == IBinaryOpInstruction.Operator.SUB)
        instStr += "v" + binOpInst.getUse(0) + " - " + "v" + binOpInst.getUse(1);
      else if (op == IBinaryOpInstruction.Operator.MUL)
        instStr += "v" + binOpInst.getUse(0) + " * " + "v" + binOpInst.getUse(1);
      else if (op == IBinaryOpInstruction.Operator.DIV)
        instStr += "v" + binOpInst.getUse(0) + " / " + "v" + binOpInst.getUse(1);
      else if (op == IBinaryOpInstruction.Operator.REM)
        instStr += "v" + binOpInst.getUse(0) + " % " + "v" + binOpInst.getUse(1);
      else if (op == IBinaryOpInstruction.Operator.AND)
        instStr += "v" + binOpInst.getUse(0) + " & " + "v" + binOpInst.getUse(1);
      else if (op == IBinaryOpInstruction.Operator.OR)
        instStr += "v" + binOpInst.getUse(0) + " | " + "v" + binOpInst.getUse(1);
      else if (op == IBinaryOpInstruction.Operator.XOR)
        instStr += "v" + binOpInst.getUse(0) + " ^ " + "v" + binOpInst.getUse(1);
    }
    else if (inst instanceof SSAGetInstruction) {
      SSAGetInstruction getInst = (SSAGetInstruction)inst;
      instStr += "v" + getInst.getDef() + " = ";
      FieldReference field = getInst.getDeclaredField();
      if (getInst.isStatic())
        instStr += field.getDeclaringClass().getName().toString() + "." + field.getName().toString();
      else
        instStr += "v" + getInst.getRef() + "." + field.getName().toString();
    } else if (inst instanceof SSAPutInstruction) {
      SSAPutInstruction putInst = (SSAPutInstruction)inst;
      FieldReference field = putInst.getDeclaredField();
      if (putInst.isStatic())
        instStr += field.getDeclaringClass().getName().toString() + "." + field.getName().toString();
      else
        instStr += "v" + putInst.getRef() + "." + field.getName().toString();
      instStr += " = " + "v" + putInst.getVal();
    }
    else {
      instStr = inst.toString();
    }
    return instStr;
  }
}
