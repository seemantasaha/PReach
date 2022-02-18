package cmd;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import core.Procedure;
import java.util.Iterator;
import java.util.Set;

import core.Reporter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author zzk, seemanta
 */
public class CFG extends BaseGraph<ISSABasicBlock> {
  private Procedure procedure;
  private int nodeInstructionIndex;
  private IInstruction[] byteCodeInstructions;
  private List<String> imbalanceAnalysisJSON;
  private List<ImbalanceAnalysisItem> imbalanceAnalysisJSONItems;
  Map<Integer, ImbalanceAnalysisItem > nodeShrikeInstructionsMap;
  Map<ImbalanceAnalysisItem, IInstruction> bytecodeMap;
  
  private String endingInstruction;

  private static boolean exceptionNodeFlag;
  
  public CFG(Procedure proc) throws InvalidClassFileException {
      
    this.procedure = proc;
    this.endingInstruction = "1001001";
    
    this.imbalanceAnalysisJSON = new ArrayList<String>();
    this.imbalanceAnalysisJSONItems = new ArrayList<ImbalanceAnalysisItem>();
    //this.imbalanceAnalysisJSON = "[ ";
    
    this.nodeInstructionIndex = 0;
    
    // All the effort to make json id unique
    String classInfo = this.procedure.getClassName();
    String classInfoParts = "";
    for (int j = 0; j < classInfo.length(); j++) {
        if(Character.isUpperCase(classInfo.charAt(j)) || Character.isDigit(classInfo.charAt(j))) {
            classInfoParts += classInfo.charAt(j);
        }
    }
    classInfo = classInfoParts;
    
    String parameterInfo = "";
    for (int i = 0; i< this.procedure.getIR().getNumberOfParameters(); i++) {
        String[] paramComponents = this.procedure.getIR().getParameterType(i).toString().split("/");
        String paramType = paramComponents[paramComponents.length - 1];
        String paramInfoParts = "";
        for (int j = 0; j < paramType.length(); j++) {
            if(Character.isUpperCase(paramType.charAt(j))) {
                paramInfoParts += paramType.charAt(j);
            }
        }
        parameterInfo += paramInfoParts;
    }

    MainLogic.itemProcMap.put(this.procedure.getProcedureName() + classInfo + parameterInfo, this.procedure);

    ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = proc.getCFG();
    IBytecodeMethod method = (IBytecodeMethod)this.procedure.getIR().getMethod();
    this.byteCodeInstructions = method.getInstructions();
    /*System.out.println("Byte code instruction for method : " + method.toString() + " --------------------->");
    for (IInstruction ins : this.byteCodeInstructions) {
        System.out.println(ins.);
    }*/
    nodeShrikeInstructionsMap = new HashMap<>();
    bytecodeMap = new HashMap<>();

    List<ISSABasicBlock> nodeList = proc.getNodeList();
      
    for (ISSABasicBlock node : nodeList) {
      exceptionNodeFlag = false;
      if (getVertex(node) == null) {
        String nodeShrikeInstructions = constructNodeVertex(node);
        String[] shrikeInstructions = nodeShrikeInstructions.split(" ");
        int nodeCost = 0;
        for (String insInNode : shrikeInstructions)
            nodeCost = nodeCost + getCostOfInstruction(insInNode);
        
        String instruction = "";
        String jsonItemID = this.procedure.getProcedureName() + classInfo + parameterInfo + "#" + node.getNumber();
        
        String[] nodeInstructions = nodeShrikeInstructions.split(" ");
        String lastInstructionOfANode = nodeInstructions[nodeInstructions.length - 1];

        if(node.getNumber() == 0) {
            instruction = "start";
        } else if (cfg.getSuccNodes(node).hasNext() == false && !lastInstructionOfANode.equals("Throw()")) {
            instruction = "end";
            jsonItemID = this.procedure.getProcedureName() + classInfo + parameterInfo + "#" + this.endingInstruction;
        } else {
            instruction = nodeShrikeInstructions;
        }
        if (instruction.equals(""))
            instruction = "null";

        instruction = instruction.replace("\"", "'");
        instruction = instruction.replace("\\\n", "newline");

        ImbalanceAnalysisItem iaNode = new ImbalanceAnalysisItem(jsonItemID, node, this.procedure, shrikeInstructions.length, instruction, nodeCost, null, null, exceptionNodeFlag);

        nodeShrikeInstructionsMap.put(node.getNumber(), iaNode);

        MainLogic.itemNodeMap.put(jsonItemID,node);
        MainLogic.nodeItemMap.put(node,jsonItemID);
      }
    }
    
    int k = 0;
    for (ISSABasicBlock node : nodeList) {
      if (getVertex(node) == null)
        constructNodeVertex(node);      
      
      ImbalanceAnalysisItem nodeItem = nodeShrikeInstructionsMap.get(node.getNumber());
      
      String jsonItem = "{ \"id\" : \"";

      jsonItem += "[ " + nodeItem.getID() + " ] " + nodeItem.getInstruction() + "\", \"instruction_count\" : \"" + nodeItem.getNumberOfInstructions()
                    + "\", \"cost\" : \"" + nodeItem.getNodeCost()
                    + "\", \"secret_dependent_branch\" : \"" + "false"
                    + "\", \"exception\" : \"" + nodeItem.getExceptionNodeFlag()
                    + "\"," +" \"incoming\" : { },";
      
      SSAInstruction lastInst = null;
      if (node.getLastInstructionIndex() >= 0)
        lastInst = node.getLastInstruction();
      
      Iterator<ISSABasicBlock> succNodeIter = cfg.getSuccNodes(node);
      jsonItem += " \"outgoing\" : {";
      
      String[] nodeInstructions = nodeItem.getInstruction().split(" ");
      String lastInstructionOfANode = nodeInstructions[nodeInstructions.length - 1];
      
      if (lastInstructionOfANode.equals("Throw()")) {
        jsonItem += " \"" + this.procedure.getProcedureName()+ classInfo + parameterInfo + "#" + this.endingInstruction + "\" : \"Implicit\"" ;
      } 

      int c = 1;
      while (succNodeIter.hasNext()) {
        ISSABasicBlock succNode = succNodeIter.next();
        if (getVertex(succNode) == null)
          constructNodeVertex(succNode);
        
        ImbalanceAnalysisItem succNodeItem = nodeShrikeInstructionsMap.get(succNode.getNumber());
                
        if (lastInst instanceof SSAConditionalBranchInstruction) {
          String reportedIns = Reporter.getSSAInstructionString(lastInst);
          jsonItem = jsonItem.replace("\"secret_dependent_branch\" : \"false\"", "\"secret_dependent_branch\" : \"branch\", \"ins_to_translate\" : \"" + reportedIns + "\"");
          int target = ((SSAConditionalBranchInstruction)lastInst).getTarget();
          if (succNode == cfg.getBlockForInstruction(target)) {
              addEdge(node, succNode, "TRUE");
               jsonItem += " \"" + succNodeItem.getID() + "\" : \"TRUE\",";
          }
          else {
              addEdge(node, succNode, "FALSE");
              jsonItem += " \"" + succNodeItem.getID() + "\" : \"FALSE\" }";
          }
          continue;
        }
        
        addEdge(node, succNode, null);
        
        if(c == 2)
            jsonItem += ",";
        
        if (lastInstructionOfANode.equals("")) {
            jsonItem += " \"" + succNodeItem.getID() + "\" : \"Implicit\" " ;
        } else if (lastInstructionOfANode.startsWith("Invoke") && lastInstructionOfANode.contains("Exception")) {
            jsonItem += " \"" + succNodeItem.getID() + "\" : \"Throw\" " ;
        } else if (lastInstructionOfANode.startsWith("Invoke")) {
            jsonItem += " \"" + succNodeItem.getID() + "\" : \"Invoke\" " ;
        } else if (lastInstructionOfANode.startsWith("Return")) {
            jsonItem += " \"" + succNodeItem.getID() + "\" : \"Return\" " ;
        } else if (lastInstructionOfANode.startsWith("Goto")) {            
            jsonItem += " \"" + succNodeItem.getID() + "\" : \"Jump\" " ;
        } else {
            jsonItem += " \"" + succNodeItem.getID() + "\" : \"Implicit\" " ;
        }
        c++;
      }
      jsonItem += "}";
      //temporary fix for switch
      if (jsonItem.contains("Switch")) {
        String oldSwitchOutgoing = jsonItem.split("outgoing\" : ")[1];
        String newSwitchOutgoing = oldSwitchOutgoing.replace("}", ",");
        jsonItem = jsonItem.replace(oldSwitchOutgoing, newSwitchOutgoing);
        jsonItem = jsonItem.substring(0, jsonItem.length()- 2);
        jsonItem += " }";
      }
              
      jsonItem += " }";
      
      if ((k == nodeList.size()-1) || (cfg.getSuccNodes(node).hasNext() == false))
          jsonItem += " }";
      this.imbalanceAnalysisJSON.add(jsonItem);
      this.imbalanceAnalysisJSONItems.add(nodeItem);
      k++;
    }
    
    //this.imbalanceAnalysisJSON += " ]";
    
    //System.out.println(this.imbalanceAnalysisJSON);
    
    layoutGraph();    
  }

  public ImbalanceAnalysisItem getJsonItem(String nodeStringID) {
      for (ImbalanceAnalysisItem item : imbalanceAnalysisJSONItems) {
          String itemID = item.getID();
          boolean found = itemID.equals(nodeStringID);
          if (found == true) {
              return item;
          }
      }
      return null;
  }
  
  private Integer getCostOfInstruction(String instruction) {
      if (instruction.equals("BinaryOp(I,add)") || instruction.equals("BinaryOp(I,sub)"))
          return 2;
      if (instruction.equals("BinaryOp(I,div)"))
          return 50;
      if (instruction.equals("BinaryOp(I,mul)"))
          return 10;
      if (instruction.equals("BinaryOp(I,rem)"))
          return 20;
      if (instruction.contains("Ljava/lang/Math;,pow"))
          return 100;
      if (instruction.contains("Ljava/lang/Math;,exp"))
          return 100;
      if (instruction.contains("Ljava/lang/Math;,sqrt"))
          return 100;
      if (instruction.contains("Ljava/math/BigInteger;,add"))
          return 4;
      if (instruction.contains("Ljava/math/BigInteger;,subtract"))
          return 4;
      if (instruction.contains("Ljava/math/BigInteger;,multiply"))
          return 20;
      if (instruction.contains("Ljava/math/BigInteger;,divide"))
          return 50;
      if (instruction.contains("Ljava/math/BigInteger;,mod"))
          return 50;
      if (instruction.contains("Ljava/math/BigInteger;,pow"))
          return 4;
      if (instruction.contains(";,write"))
          return 100;
      //if (instruction.contains("sleep"))
      //    return Integer.parseInt(instruction.split("sleep")[1].split("(")[0]);
      return 1;
  }
  
  private String constructNodeVertex(ISSABasicBlock node) throws InvalidClassFileException {
    
    String nodeString = "Node#:" + node.getNumber() + "\n";
    String nodeStr = "";
    int instNum = 1;
    Iterator<SSAInstruction> instIter = node.iterator();
      
    ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = this.procedure.getCFG();
    SSAInstruction[] ins = cfg.getInstructions();
    
    boolean flag = false;
    
    while (instIter.hasNext()) {
      flag = true;
      SSAInstruction inst = instIter.next();
      IInstruction neededByteCodeInst = null;
      
      int i = 0;
      for(SSAInstruction in : ins) {
        String inString = "";
        if (in != null) inString = in.toString();
        if (inString.equals(inst.toString())) {
          neededByteCodeInst = this.byteCodeInstructions[i];
          break;
        }
        i++;
      }
      
      //nodeString += "instruction: " + inst.toString() + "\n"; //format: conditional branch(lt, to iindex=43) 26,27
      nodeString += Reporter.getSSAInstructionString(inst) + "\n"; //printing the texts are shown in the cfg node
      instNum++;

      if(nodeString.contains("getCaughtException"))
          exceptionNodeFlag = true;

      while (neededByteCodeInst != null && this.nodeInstructionIndex < this.byteCodeInstructions.length) {
        //nodeStr += "[" + this.nodeInstructionIndex + "]";
        nodeStr += byteCodeInstructions[this.nodeInstructionIndex] + " ";
        this.nodeInstructionIndex++;
          //System.out.println(byteCodeInstructions[this.nodeInstructionIndex-1]);
          //System.out.println("Needed bytecode instruction: " + neededByteCodeInst);
        if (byteCodeInstructions[this.nodeInstructionIndex-1].equals(neededByteCodeInst))
          break;
      }
    }

//    if (!flag) {
//       Iterator<ISSABasicBlock> succNodeIter = cfg.getSuccNodes(node);
//       if (succNodeIter.hasNext()) {
//        ISSABasicBlock succNode = succNodeIter.next();
//        //System.out.println(succNode.getNumber());
//        Iterator<SSAInstruction> succInstIter = succNode.iterator();
//        if (succInstIter.hasNext()) {
//          SSAInstruction inst = succInstIter.next();
//          //System.out.println(inst.toString());
//          if(inst.toString().contains("phi")) {
//              while (this.nodeInstructionIndex < this.byteCodeInstructions.length && !byteCodeInstructions[this.nodeInstructionIndex].toString().contains("LocalLoad(I,")) {
//                nodeStr += byteCodeInstructions[this.nodeInstructionIndex] + " ";
//                this.nodeInstructionIndex++;
//              }
//          }
//        }
//       }
//    }
    //System.out.println("ns : " + nodeString);
    addVertex(node, instNum, nodeString);
    return nodeStr;
  }
  
  final public Procedure getProcedure() {
    return this.procedure;
  }
  
  final public List<String> getJSON() {
      return this.imbalanceAnalysisJSON;
  }

    final public List<ImbalanceAnalysisItem> getJSONItems() {
        return this.imbalanceAnalysisJSONItems;
    }
  
  final public void paintNode(ISSABasicBlock node, String color) {
    colorVertex(node, color);
  }
  
  final public void paintNodeSet(Set<ISSABasicBlock> nodeSet, String color) {
    for (ISSABasicBlock node : nodeSet)
      colorVertex(node, color);
  }
  
  /*added by Madeline Sgro 07/07/2017
  allows the node set in the CFG to be scaled
  */
  final public void scaleNodeSet(Set<ISSABasicBlock> nodeSet, double scaleFactor){
    for (ISSABasicBlock node : nodeSet){
      scaleVertex(node, scaleFactor);
    }
  }
}
