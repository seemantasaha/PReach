package cmd;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import core.*;
import javafx.util.Pair;
import vlab.cs.ucsb.edu.ModelCounter;

import java.io.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.*;

public class MainLogic {

  public MainLogic() throws InvalidClassFileException {

    this.recursiveBound = 1;

    //this.program.printLoopGroup();
    //loadClassHierarchy();

    loadCallGraph();
    //loadNestedLoops();
    //loadNewObjects();
    //loadRecursions();

    //this.program.printArgumentType();
  }



  private void loadCallGraph() throws InvalidClassFileException {
    this.callGraph = new CG();
    int numverOFProcedure = 0;
    HashSet<String> classNames = new HashSet<>();
    for (Procedure proc : Program.getProcedureSet()) {
      numverOFProcedure++;
      classNames.add(proc.getClassName());
      loadControlFlowGraph(proc);
      loadProcedureDependenceGraph(proc);
    }

    System.out.println("Number of Classes: " + classNames.size());
    System.out.println("Number of Methods: " + numverOFProcedure);
  }

  private void loadControlFlowGraph(Procedure proc) throws InvalidClassFileException {
    CFG ctrlFlowGraph = this.controlFlowGraphMap.get(proc);
    if (ctrlFlowGraph != null)
      return;
    ctrlFlowGraph = new CFG(proc);
    ctrlFlowGraph.paintNodeSet(proc.getNodeSet(), "aquamarine");
    this.controlFlowGraphMap.put(proc, ctrlFlowGraph);
    System.out.println(proc.getFullSignature());
    this.jsonMap.put(proc.getFullSignature(), ctrlFlowGraph.getJSON());
    recursiveBoundMap.put(proc.getFullSignature(), 1);
  }

  private void loadProcedureDependenceGraph(Procedure proc) {
    PDG procDepGraph = this.procedureDependenceGraphMap.get(proc);
    if (procDepGraph != null)
      return;
    procDepGraph = new PDG(proc);
    this.procedureDependenceGraphMap.put(proc, procDepGraph);
  }

  private void paintSlice(Set<Statement> stmtSet) {
    CG cg = this.getCG();
    cg.paintProcedureSet(Program.getProcedureSet(), "white");
    for (Procedure proc : Program.getProcedureSet()) {
      CFG cfg = this.getCFG(proc);
      cfg.getProcedure().dependentNodes.clear();
      cfg.paintNodeSet(proc.getNodeSet(), "aquamarine");
    }

    for (Statement stmt : stmtSet) {
      Procedure proc = stmt.getOwner().getProcedure();
      CFG cfg = this.getCFG(proc);
      if (proc == null || cfg == null)
        continue;
      cg.colorVertex(proc, "green");

      StatementType stmtType = stmt.getStatementType();
      if (stmtType == StatementType.Instruction) {
        SSAInstruction inst = (SSAInstruction)stmt.getContent();
        ISSABasicBlock target = proc.getNode(inst);
        int bcIndex = 0;
        try {
          if (inst.toString().startsWith("conditional branch")) {
            System.out.println ( "Bytecode : " + inst.toString() );
            bcIndex = ((IBytecodeMethod) target.getMethod()).getBytecodeIndex(inst.iindex);
            try {
              int src_line_number = target.getMethod().getLineNumber(bcIndex);
              nodeLineMap.put(target, src_line_number);
              System.out.println("Source line number = " + src_line_number);
              MainLogic.allSourceLines.put(src_line_number, proc.getProcedureName());
            } catch (Exception e) {
              System.out.println("Bytecode index is incorrect");
              System.out.println(e.getMessage());
            }
          }
        } catch (Exception e ) {
          System.err.println("it's probably not a BT method (e.g. it's a fakeroot method)");
          System.err.println(e.getMessage());
        }
        if (target != null) {
          cfg.getProcedure().dependentNodes.add(""+target.getNumber());
          cfg.paintNode(target, "green");
        }
      } else if (stmtType == StatementType.ActualIn || stmtType == StatementType.ActualOut) {
        Statement ctrlStmt = stmt.getControlStatement();
        if (ctrlStmt.getStatementType() == StatementType.Instruction) {
          SSAInstruction inst = (SSAInstruction)ctrlStmt.getContent();
          ISSABasicBlock target = proc.getNode(inst);
          int bcIndex = 0;
          try {
            if (inst.toString().startsWith("conditional branch")) {
              System.out.println ( "Bytecode : " + inst.toString() );
              bcIndex = ((IBytecodeMethod) target.getMethod()).getBytecodeIndex(inst.iindex);
              try {
                int src_line_number = target.getMethod().getLineNumber(bcIndex);
                System.out.println("Source line number = " + src_line_number);
                MainLogic.allSourceLines.put(src_line_number, proc.getProcedureName());
              } catch (Exception e) {
                System.out.println("Bytecode index no good");
                System.out.println(e.getMessage());
              }
            }
          } catch (Exception e ) {
            System.out.println("it's probably not a BT method (e.g. it's a fakeroot method)");
            System.out.println(e.getMessage());
          }
          if (target != null) {
            cfg.getProcedure().dependentNodes.add(""+target.getNumber());
            cfg.paintNode(target, "green");
          }
        }
      }

      for (ISSABasicBlock block : proc.getNodeSet()) {
        if (block.getLastInstructionIndex() < 0)
          continue;
        SSAInstruction inst = block.getLastInstruction();
        if (inst == null)
          continue;
        System.err.println(inst);
        if (inst.toString().startsWith("return") /*|| inst.toString().contains("= invokevirtual")*/) {
          ISSABasicBlock target = proc.getNode(inst);
          int bcIndex = 0;
          try {
            bcIndex = ((IBytecodeMethod) target.getMethod()).getBytecodeIndex(inst.iindex);
            try {
              int src_line_number = target.getMethod().getLineNumber(bcIndex);
              System.err.println("Source line number = " + src_line_number);
              if(MainLogic.allSourceLines.containsKey(src_line_number)) {
                MainLogic.allSourceLines.remove(src_line_number);
              }
            } catch (Exception e) {
              System.err.println("Bytecode index no good");
              System.err.println(e.getMessage());
            }
          } catch (Exception e ) {
            System.err.println("it's probably not a BT method (e.g. it's a fakeroot method)");
            System.err.println(e.getMessage());
          }
        }
      }
    }
  }

  /*added by Seemanta Saha 01/17/2018
  check that a node is successor of another node
  */
  private ISSABasicBlock checkLoop(ISSABasicBlock node){
    ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = currentCFG.getProcedure().getCFG();
    Iterator<ISSABasicBlock> succNodeIter = cfg.getSuccNodes(node);
    if (succNodeIter.hasNext()) {
      ISSABasicBlock succNode = succNodeIter.next();
      if (succNode != null) {
        String nodeIns = "";
        Iterator<SSAInstruction> instIter = succNode.iterator();
        while (instIter.hasNext()) {
          SSAInstruction insts = instIter.next();
          nodeIns += Reporter.getSSAInstructionString(insts) + " ";
        }
        if (nodeIns.contains("phi") && nodeIns.contains("if")) {
          System.out.println("Got a loop node: " + succNode.getNumber() + " for " + node.getNumber());
          return succNode;
        }
      }
    }

    return null;
  }

  /*added by Seemanta Saha 01/17/2018
  check that a node in cfg is inside a loop
  */
  private ArrayList<ISSABasicBlock> checkNodeIsInLoop(ISSABasicBlock node, ArrayList<ISSABasicBlock> checkList) {
    ArrayList<ISSABasicBlock> nodeList = new ArrayList<ISSABasicBlock>();
    ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = currentCFG.getProcedure().getCFG();
    Iterator<ISSABasicBlock> succNodeIter = cfg.getSuccNodes(node);
    while (succNodeIter.hasNext()) {
      ISSABasicBlock succNode = succNodeIter.next();
      System.out.println(succNode.getNumber());
      if (succNode != null) {
        String nodeIns = "";
        Iterator<SSAInstruction> instIter = succNode.iterator();
        while (instIter.hasNext()) {
          SSAInstruction insts = instIter.next();
          nodeIns += Reporter.getSSAInstructionString(insts) + " ";
        }
        System.out.println(nodeIns);
        if (nodeIns.contains("phi") && nodeIns.contains("+") && nodeIns.contains("goto")) {
          System.out.println("Got a loop entering node: " + succNode.getNumber() + " for " + node.getNumber());
          ISSABasicBlock loopNode = checkLoop(succNode);
          if (loopNode != null) {
            nodeList.add(0, succNode);
            nodeList.add(1, loopNode);
          }
        } else if (!checkList.contains(succNode)){
          checkList.add(succNode);
          checkNodeIsInLoop(succNode, checkList);
        }
      }
    }
    return nodeList;
  }

  private ArrayList<ISSABasicBlock> checkEffectedBranchNode(Statement s) {
    ArrayList<ISSABasicBlock> finalNodeList = new ArrayList<ISSABasicBlock>();
    if (s.getStatementType() == StatementType.Instruction) {
      SSAInstruction inst = (SSAInstruction)s.getContent();
      ISSABasicBlock target = currentCFG.getProcedure().getNode(inst);
      if (target != null) {
        System.out.println(target.getNumber() + ":");
        String nodeIns = "";
        Iterator<SSAInstruction> instIter = target.iterator();
        while (instIter.hasNext()) {
          SSAInstruction insts = instIter.next();
          nodeIns += Reporter.getSSAInstructionString(insts) + " ";
        }
        System.out.println(nodeIns);
        //Check that node is a if conditional branch (not loop)
        if (!nodeIns.contains("phi") && nodeIns.contains("if")) {
          System.out.println("Branch condition:" + target.getNumber());
          ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = currentCFG.getProcedure().getCFG();
          Iterator<ISSABasicBlock> succNodeIter = cfg.getSuccNodes(target);
          boolean flag_branch = true;
          while (succNodeIter.hasNext()) { // only working with the first successor of if node considering it is the FALSE node
            ISSABasicBlock succNode = succNodeIter.next();
            if (flag_branch == false) {
              System.out.println("FALSE node: " + succNode.getNumber());
              //Check that the node is in a loop :(
              ArrayList<ISSABasicBlock> checkList = new ArrayList<ISSABasicBlock>();
              checkList.add(succNode);
              ArrayList<ISSABasicBlock> nodeList = checkNodeIsInLoop(succNode, checkList);
              if (nodeList.size() == 2) {
                System.out.println(target.getNumber() + " in loop");
                System.out.println("Work with node: " + succNode.getNumber());
                System.out.println("Node entering loop: " + nodeList.get(0).getNumber());
                System.out.println("Loop node: " + nodeList.get(1).getNumber());
                finalNodeList.add(0, succNode);
                finalNodeList.addAll(1, nodeList);
              }
            }
            flag_branch = false;
          }
        }
      }
    }
    return finalNodeList;
  }

  public void doDependencyAnalysis(String procSign, ArrayList<String> testInputParams) {
    System.out.println("Dependency analysis started ...");
    for (String param: testInputParams) {
      doDependencyAnalysisPerParam(procSign, param);
    }
  }

  public String removeFirstAndLast(String s) {
    StringBuilder sb = new StringBuilder(s);
    sb.deleteCharAt(s.length()-1);
    sb.deleteCharAt(0);
    return sb.toString();
  }


  public void doDependencyAnalysisPerParam(String procSign, String param) {
    Set found = new HashSet<>();
    long start = System.currentTimeMillis();
    //System.out.println(procSign);
    if (procSign.charAt(0) == '\'' && procSign.charAt(procSign.length()-1) == '\'') {
      procSign = removeFirstAndLast(procSign);
    }
    //System.out.println("Trimmed sign: " + procSign);
    Procedure proc = Program.getProcedure(procSign);
    currentCFG = this.controlFlowGraphMap.get(proc);
    if (currentCFG == null){
      return;
    }

    if (!found.isEmpty()){
      currentCFG.paintNodeSet(found, "aquamarine");
      found.clear();
    }

    Set<Statement> sliceStmtSet = new HashSet<>();
    Set<Statement> stmtSet = new HashSet<>();
    Set<Statement> cntrlStmtSet = new HashSet<>();
    String selected = param;

    if (selected != null){
      selected = selected.split("@")[0];
      MainLogic.selectedVariables.add(selected);
      Map<ISSABasicBlock, Object> mapCFG = currentCFG.getVertexMap();
      for (ISSABasicBlock entry : mapCFG.keySet()){
        String compare = currentCFG.getGraph().getLabel(mapCFG.get(entry));
        String[] compareArray = compare.split("\n");
        if (compareArray.length > 1){
          for (int i = 0; i < compareArray.length; i++) {
            if (i == 0) continue;
            compare = compareArray[i];
            if (compare.matches(".*" + selected + "([^0-9].*)?") ||
                    ((compare.contains("phi") || compare.contains("arrayload") || compare.contains("arraylength")) &&
                            compare.matches("(.*[^0-9])?" + selected.substring(1, selected.length()) + "([^0-9].*)?"))){
              found.add(entry);
              System.out.println("\n=============================\n");
              Iterator<SSAInstruction> itr = entry.iterator();
              while(itr.hasNext()) {
                SSAInstruction ins = itr.next();
                System.out.println(ins);
                if(Reporter.getSSAInstructionString(ins).contains("=")) {
                  String[] inspart = Reporter.getSSAInstructionString(ins).split("=");
                  if(inspart[1].contains(selected)) {
                    stmtSet.addAll(ProgramDependenceGraph.sliceProgramForward(currentCFG.getProcedure(),ins));
                    String newvar = inspart[0].replace(" ","");
                    System.out.println(newvar);
                    MainLogic.selectedVariables.add(newvar);
                  }
                }
              }
              System.out.println("\n=============================\n");
              String[] inspart = Reporter.getSSAInstructionString(entry.getLastInstruction()).split(" ");
              boolean runFlag = false;
              for(String s : inspart){
                for(String sv : MainLogic.selectedVariables) {
                  if(s.equals(sv)){
                    runFlag = true;
                    break;
                  }
                }
              }
              if(runFlag) {
                stmtSet.addAll(ProgramDependenceGraph.sliceProgramForward(currentCFG.getProcedure(), entry.getLastInstruction()));
                MainLogic.allStmtSet.addAll(stmtSet);
              }
              for (Statement s : MainLogic.allStmtSet) {
                sliceStmtSet.add(s);
                ArrayList<ISSABasicBlock> nodeList = checkEffectedBranchNode(s);
                if (nodeList.size() == 3) {
                  ISSABasicBlock insideIfNode = nodeList.get(0);
                  ISSABasicBlock loopEnteringNode = nodeList.get(1);
                  ISSABasicBlock loopNode = nodeList.get(2);
                  if (loopEnteringNode != null) {
                    Iterator<SSAInstruction> instIter = loopEnteringNode.iterator();
                    while (instIter.hasNext()) {
                      SSAInstruction insts = instIter.next();
                      if (insts.toString().contains("phi")) {
                        String var = insts.toString().split(" = ")[0];
                        System.out.println("variable : v" + var);
                        Iterator<SSAInstruction> loopInstIter = loopNode.iterator();
                        while (loopInstIter.hasNext()) {
                          SSAInstruction instsLoop = loopInstIter.next();
                          if (instsLoop.toString().contains("phi  " + var)) {
                            String varToSlice = "v" + instsLoop.toString().split(" = ")[0];
                            System.out.println("variable to slice : " + varToSlice);
                            for (ISSABasicBlock node : mapCFG.keySet()){
                              String compareNode = currentCFG.getGraph().getLabel(mapCFG.get(node));
                              String[] compareNodeArray = compareNode.split("\n");
                              if (compareNodeArray.length > 1){
                                for (int j = 0; j < compareNodeArray.length; j++) {
                                  if (j == 0) continue;
                                  compareNode = compareNodeArray[i];
                                  if (compareNode.matches(".*" + varToSlice + "([^0-9].*)?") ||
                                          ((compareNode.contains("phi") || compareNode.contains("arrayload") || compareNode.contains("arraylength")) &&
                                                  compareNode.matches("(.*[^0-9])?" + varToSlice.substring(1, varToSlice.length()) + "([^0-9].*)?"))){
                                    found.add(node);
                                    cntrlStmtSet = ProgramDependenceGraph.sliceProgramForward(currentCFG.getProcedure(), node.getLastInstruction());
                                    for (Statement stmt : cntrlStmtSet) {
                                      sliceStmtSet.add(stmt);
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      } else {
                        break;
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      paintSlice(sliceStmtSet);

      long finish = System.currentTimeMillis();
      long timeElapsed = finish - start;
      dependencyAnalysisTime += timeElapsed;
    }
  }

  private String getNodeFromID(String jsonItemID) {
    if(nodeMap.get(jsonItemID) == null) {
      nodeMap.put(jsonItemID, counter);
      idMap.put(counter,jsonItemID);
      counter++;
      numberofNodes=counter;
    }
    return nodeMap.get(jsonItemID).toString();
  }

  private void removeAllTransitions(String node, String assertNode, Procedure proc, boolean[] visited) {
    if(visited[Integer.parseInt(node)])
      return;
    visited[Integer.parseInt(node)] = true;
    List<MarkovChainInformation> list = transitionlistMap.get(node);
    if(list == null)
      return;
    for(MarkovChainInformation mi : list) {
      if(!mi.getFromNode().equals(mi.getToNode()) && !mi.getToNode().equals(assertNode)) {
        removeAllTransitions(mi.getToNode(), assertNode, proc, visited);
        ISSABasicBlock aNode = itemNodeMap.get(idMap.get(Integer.parseInt(assertNode)));
        ISSABasicBlock mNode = itemNodeMap.get(idMap.get(Integer.parseInt(mi.getToNode())));
        Set<ISSABasicBlock> aNodeDominatorSet = proc.getDominatorSet(aNode);
        if(assertNode.equals(assertionNode) && !aNodeDominatorSet.contains(mNode)) {
          //numberofNodesReduced += 1;
          transitionlistMap.remove(mi.getToNode());
        }
      }
    }
  }

  public void doMarkovChainAnalysis(String branchProbFile, String prismBinary) {

    System.out.println("Starting Markov Chain analysis ...");

    Map<String, Double> branchProbMap = new HashMap<>();

    BufferedReader reader;
    try {
      System.out.println(branchProbFile);
      reader = new BufferedReader(new FileReader(branchProbFile));
      String line = reader.readLine();
      while (line != null) {
        //System.out.println(line);
        String[] temp = line.split("\t");
        if (temp.length < 2)
          continue;
//        if(temp[2].equals("1")) {
        branchProbMap.put(temp[0], 1.0 - Double.parseDouble(temp[1].split(",")[0]));
//        } else {
//            branchProbMap.put(temp[0], Double.parseDouble(temp[1]));
//        }
        line = reader.readLine();
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    long start = System.currentTimeMillis();

    long dts = System.currentTimeMillis();


    if (this.currentCFG == null) {
      System.out.println("Can not find the control flow graph with the provided method signtaure");
      return;
    }

    //System.out.println("Procedure name: " + this.currentCFG.getProcedure().getProcedureName());

    List<String> jsonItems = this.currentCFG.getJSON();
    List<String> invokedProcedures = new ArrayList<String>();
    invokedProcedures.add(this.currentCFG.getProcedure().getFullSignature());

    String modelName = currentCFG.getProcedure().getClassName().replace("/","_") + "_" + currentCFG.getProcedure().getProcedureName();
    modelName = modelName.replace("$","_");
    Procedure cureProc = this.currentCFG.getProcedure();


    //preprocessing source code branch condition in CFG
    List<String> jsonItemsToBeAdded = new ArrayList<>();
    List<String> jsonItemsToBeRemoved = new ArrayList<>();
    String trueNodeToUpdate = "";
    String falseNodeToUpdate = "";
    String insTotranslate = "";
    String jsonItem1 = "";
    String jsonItemId1 = "";
    String prevUpdatedJsonItem = "";


    //numberofNodes = jsonItems.size();

    String completeJSON = "[ ";
    int i = 0;
    for (String jsonItem: jsonItems) {

      String jsonItemID = jsonItem.split(" ")[4];
      String jsonItemNodeNumber = jsonItemID.split("#")[1];

      //remmeber this: Procedure cureProc = this.currentCFG.getProcedure();
      if (cureProc.dependentNodes.contains(jsonItemNodeNumber) && jsonItem.contains("\"secret_dependent_branch\" : \"branch\"")) {
        jsonItem = jsonItem.replace("\"secret_dependent_branch\" : \"branch\"", "\"secret_dependent_branch\" : \"true\"");
      }
      completeJSON += jsonItem;
      if (i < jsonItems.size() - 1)
        completeJSON += ",\n";
      i++;

      // Recursive inlining of function calls
      if (jsonItem.contains("Invoke") && !jsonItem.contains("<init>")) {
        completeJSON = recursiveInlining(invokedProcedures, jsonItems, i, jsonItemID, jsonItem, completeJSON, 0);
      }
    }
    completeJSON += " ]";

    System.out.println(completeJSON);


    String[] interProcItems = completeJSON.split("\n");
    List<String> interProcItemsList = new ArrayList<String>(interProcItems.length);
    for (String s:interProcItems) {
      interProcItemsList.add( s );
    }


    long dts5 = System.currentTimeMillis();

    Map<String, String> idItemMap = new HashMap<>();
    Map<String, Boolean> idConditionalMap = new HashMap<>();
    Map<String, List<String>> idToNodesMap = new HashMap<>();

    for(Iterator<String> it = interProcItemsList.iterator(); it.hasNext();) {
      String jsonItem = it.next();
      if (jsonItem.startsWith("[ "))
        jsonItem = jsonItem.substring(2);
      String jsonItemID = jsonItem.split(" ")[4];
      idItemMap.put(jsonItemID, jsonItem);
      idConditionalMap.put(jsonItemID, false);
      String jsonItemNodeNumber = jsonItemID.split("#")[1];

      if (jsonItem.contains("outgoing") && jsonItem.contains("{") && jsonItem.contains(":")) {
        if (jsonItem.contains("\"secret_dependent_branch\" : \"branch\"") || jsonItem.contains("\"secret_dependent_branch\" : \"true\"")) {
          idConditionalMap.put(jsonItemID, true);
        }
        String[] temp = jsonItem.split("\"outgoing\" : \\{ ");
        if(temp.length < 2)
          continue;
        String part = temp[1].split(" }")[0];
        String[] outgoingNodes = part.split(",");

        List<String> toNodesList = new ArrayList<>();

        if (outgoingNodes.length >= 1 && outgoingNodes[0].contains("\"")) {

          String trueNode = outgoingNodes[0].split("\"")[1];
          toNodesList.add(trueNode);
          idToNodesMap.put(jsonItemID, toNodesList);

          String falseNode = "";
          String insn = "";
          String jsonItem2 = "";
          String jsonItemId2 = "";

          if (outgoingNodes.length == 2 && outgoingNodes[1].contains("\"")) {
            falseNode = outgoingNodes[1].split("\"")[1];
            idToNodesMap.get(jsonItemID).add(falseNode);
          }
        }
      }
    }


    for(Iterator<String> it = interProcItemsList.iterator(); it.hasNext();) {
      String jsonItem = it.next();
      if(jsonItem.startsWith("[ "))
        jsonItem = jsonItem.substring(2);
      String jsonItemID = jsonItem.split(" ")[4];

      String jsonItemNodeNumber = jsonItemID.split("#")[1];
      if (cureProc.dependentNodes.contains(jsonItemNodeNumber) && jsonItem.contains("\"secret_dependent_branch\" : \"true\"")) {

        String[] outgoingNodes = jsonItem.split("\"outgoing\" : \\{ ")[1].split(" }")[0].split(",");

        List<String> toNodesList = new ArrayList<>();

        String trueNode = outgoingNodes[0].split("\"")[1];

        String falseNode = "";
        String insn = "";
        String jsonItem2 = "";
        String jsonItemId2 = "";

        if(outgoingNodes.length == 2 && outgoingNodes[1].contains("\"")) {
          falseNode = outgoingNodes[1].split("\"")[1];

          insn = jsonItem.split("\"ins_to_translate\" : \"")[1].split("\"")[0];
          jsonItemId2 = jsonItemID;
          jsonItem2 = jsonItem;

//          boolean sameVarFlag = false;
//          String[] ins1 = insTotranslate.split(" ");
//          String[] ins2 = insn.split(" ");
//          HashSet<String> tmp = new HashSet<String>();
//          for (String s : ins1) {
//            if(s.contains("v"))
//              tmp.add(s);
//          }
//          for (String s : ins2) {
//            if (tmp.contains(s)) {
//              sameVarFlag = true;
//              break;
//            }
//          }

          boolean sameLineFlag = false;
          if(!jsonItemId1.equals("") && !jsonItemId2.equals("")) {
            ISSABasicBlock n1 = itemNodeMap.get(jsonItemId1);
            if(n1 == null) {
              String[] jsonItem1Arr = jsonItemId1.split("#");
              n1 = itemNodeMap.get(jsonItem1Arr[0]+"#"+jsonItem1Arr[jsonItem1Arr.length-1]);
            }
            ISSABasicBlock n2 = itemNodeMap.get(jsonItemId2);
            if(n2 == null) {
              String[] jsonItem2Arr = jsonItemId2.split("#");
              n2 = itemNodeMap.get(jsonItem2Arr[0]+"#"+jsonItem2Arr[jsonItem2Arr.length-1]);
            }
//            System.out.println(n1 != null);
//            System.out.println(n2 != null );
//            System.out.println(nodeLineMap.containsKey(n1));
//            System.out.println(nodeLineMap.containsKey(n2));
//            System.out.println((nodeLineMap.get(n1).equals(nodeLineMap.get(n2))));
            if (n1 != null && n2 != null && nodeLineMap.containsKey(n1) && nodeLineMap.containsKey(n2) &&
                    (nodeLineMap.get(n1).equals(nodeLineMap.get(n2)))) {
              sameLineFlag = true;
            }
          }

          if(jsonItemID.equals(trueNodeToUpdate) /*&& sameVarFlag*/) {
            if(sameLineFlag) {
              jsonItemsToBeRemoved.add(jsonItem);

              List<String> itemlist = lineItemsMap.get(jsonItem1);
              itemlist.add(jsonItem);
              lineItemsMap.put(jsonItem1, itemlist);
              lineItemsMap.put(jsonItem, itemlist);

              insTotranslate = insTotranslate + " and " + insn;
              String updatedJsonItem = jsonItem.replace(insn, insTotranslate);
              jsonItemsToBeAdded.add(updatedJsonItem);
              if(!prevUpdatedJsonItem.equals("")) {
                jsonItemsToBeAdded.remove(prevUpdatedJsonItem);
              }
              prevUpdatedJsonItem = updatedJsonItem;
            }else {
              insTotranslate = insn;
              jsonItemId1 = jsonItemID;
              jsonItem1 = jsonItem;
              jsonItemsToBeRemoved.add(jsonItem);

              List<String> itemlist = new ArrayList<>();
              itemlist.add(jsonItem);
              lineItemsMap.put(jsonItem, itemlist);

              prevUpdatedJsonItem="";
            }
          }
          else if(jsonItemID.equals(falseNodeToUpdate) /*&& sameVarFlag*/) {
            if(sameLineFlag) {
              jsonItemsToBeRemoved.add(jsonItem);

              List<String> itemlist = lineItemsMap.get(jsonItem1);
              itemlist.add(jsonItem);
              lineItemsMap.put(jsonItem1, itemlist);
              lineItemsMap.put(jsonItem, itemlist);

              insTotranslate = "not (" + insTotranslate + ") and not (" + insn + ")";
              String updatedJsonItem = jsonItem.replace(insn, insTotranslate);
              jsonItemsToBeAdded.add(updatedJsonItem);
              if(!prevUpdatedJsonItem.equals("")) {
                jsonItemsToBeAdded.remove(prevUpdatedJsonItem);
              }
              prevUpdatedJsonItem = updatedJsonItem;
            }else {
              insTotranslate = insn;
              jsonItemId1 = jsonItemID;
              jsonItem1 = jsonItem;
              jsonItemsToBeRemoved.add(jsonItem);

              List<String> itemlist = new ArrayList<>();
              itemlist.add(jsonItem);
              lineItemsMap.put(jsonItem, itemlist);

              prevUpdatedJsonItem="";
            }
          }
          else {
            insTotranslate = insn;
            jsonItemId1 = jsonItemID;
            jsonItem1 = jsonItem;
            jsonItemsToBeRemoved.add(jsonItem);

            List<String> itemlist = new ArrayList<>();
            itemlist.add(jsonItem);
            lineItemsMap.put(jsonItem, itemlist);

            prevUpdatedJsonItem="";
          }
          trueNodeToUpdate = trueNode;

//          if(idConditionalMap.get(falseNode)) {
//              falseNodeToUpdate = falseNode;
//          } else {
//              if(idToNodesMap.containsKey(falseNode) && idToNodesMap.get(falseNode).size() >= 1) {
//                  falseNodeToUpdate = idToNodesMap.get(falseNode).get(0);
//
//              } else {
//                  falseNodeToUpdate = falseNode;
//              }
//          }

          falseNodeToUpdate = falseNode;
          while(!idConditionalMap.get(falseNodeToUpdate) && idToNodesMap.containsKey(falseNodeToUpdate) && idToNodesMap.get(falseNodeToUpdate).size() >= 1) {
            falseNodeToUpdate = idToNodesMap.get(falseNodeToUpdate).get(0);
          }
        }
      }
    }

    if(jsonItemsToBeAdded.size() == 0)
      jsonItemsToBeRemoved.clear();

    List<String> temp = new ArrayList<>();
    for (String item : jsonItemsToBeRemoved) {
      if(lineItemsMap.get(item).size() != 1)
        temp.add(item);
    }
    jsonItemsToBeRemoved.clear();
    jsonItemsToBeRemoved.addAll(temp);
    temp.clear();

    Map<String, String> replaceMap = new HashMap<>();
    for (int j = 0; j < jsonItemsToBeAdded.size(); j ++) {
      String it = jsonItemsToBeAdded.get(j);
      String itId = it.split(" ")[4];
      for (int k = 0; k < jsonItemsToBeRemoved.size(); k++) {
        String item = jsonItemsToBeRemoved.get(k);
        String itemId = item.split(" ")[4];
        if (itemId.equals(itId)) {
          j++;
          if (j < jsonItemsToBeAdded.size()) {
            it = jsonItemsToBeAdded.get(j);
            itId = it.split(" ")[4];
          }
          continue;
        }
        if(j < jsonItemsToBeAdded.size()) {
//            String[] itemIDArr = itemId.split("#");
//            itemId = itemIDArr[0] + "#" + itemIDArr[itemIDArr.length-1];
//            String[] itIDArr = itId.split("#");
//            itId = itIDArr[0] + "#" + itIDArr[itIDArr.length-1];
          replaceMap.put("\"" + itemId + "\"", "\"" + itId + "\"");
        }
      }
    }
    numberofNodesMerged = jsonItemsToBeRemoved.size()-jsonItemsToBeAdded.size();
    for (String item : jsonItemsToBeRemoved) {
      interProcItemsList.remove(item);
    }
    for(String item : jsonItemsToBeAdded) {
      interProcItemsList.add(item);
    }

    List<String> newJsonItems = new ArrayList<>();
    for(Iterator<String> it = interProcItemsList.iterator(); it.hasNext();) {
      String item  = it.next();
      boolean flag = false;
      for (Map.Entry<String,String> entry : replaceMap.entrySet()) {
//        String[] itIDArr = entry.getKey().split("#");
//        String itID = itIDArr[0] + "#" + itIDArr[itIDArr.length-1];
//        if(item.contains(itID)) {
        if(item.contains(entry.getKey())) {
//          String[] upitIDArr = entry.getValue().split("#");
//          String upitID = "";
//          if(upitIDArr.length == 3)
//            upitID = upitIDArr[0] + "#" + itIDArr[1] + "#" + upitIDArr[upitIDArr.length-1];
//          else
//            upitID = entry.getValue();
//          String newItem = item.replace(entry.getKey(), upitID);
          String newItem = item.replace(entry.getKey(), entry.getValue());
          newJsonItems.add(newItem);
          flag = true;
        }
      }
      if(!flag) {
        newJsonItems.add(item);
      }
    }
    interProcItemsList.clear();
    interProcItemsList.addAll(newJsonItems);


    long dfs5 = System.currentTimeMillis();
    long det5 = dfs5 - dts5;
    /*List<String> actualList = new ArrayList<>();
    for(String item : jsonItemsToBeAdded) {
      String itemId = item.split(" ")[4];
      String itemId2 = itemId;
      int n = Integer.parseInt(itemId.split("#")[1]) - 1;
      String itemId1 = itemId .split("#")[0] + "#" + n;
      int count = 0;
      List<String> tempList = new ArrayList<>();
      for(String it : jsonItemsToBeRemoved) {
        String itId = it.split(" ")[4];
        if(itId.equals(itemId1) || itId.equals(itemId2)) {
          tempList.add(it);
          count++;
          if(count == 2)
            break;
        }
      }
      if(count == 2) {
        actualList.addAll(tempList);
      }
      tempList.clear();
    }
    jsonItemsToBeRemoved.clear();
    jsonItemsToBeRemoved.addAll(actualList);

    //if(jsonItemsToBeRemoved.size() == 2 * jsonItemsToBeAdded.size()) {
    int count = 1;
    String key = "", val = "";
    Map<String, String> replaceMap = new HashMap<>();
    for(String item : jsonItemsToBeRemoved) {
      jsonItems.remove(item);
      if(count % 2 == 1) {
        key = "\""+item.split(" ")[4]+"\"";
      } else {
        val = "\""+item.split(" ")[4]+"\"";
        replaceMap.put(key,val);
      }
      count++;
    }
    for(String item : jsonItemsToBeAdded) {
      jsonItems.add(item);
    }

    List<String> newJsonItems = new ArrayList<>();
    for(Iterator<String> it = jsonItems.iterator(); it.hasNext();) {
      String item  = it.next();
      boolean flag = false;
      for (Map.Entry<String,String> entry : replaceMap.entrySet()) {
        if(item.contains(entry.getKey())) {
          String newItem = item.replace(entry.getKey(), entry.getValue());
          newJsonItems.add(newItem);
          flag = true;
        }
      }
      if(!flag) {
        newJsonItems.add(item);
      }
    }
    jsonItems.clear();
    jsonItems.addAll(newJsonItems);*/
    //}





    //MARKOV CHAIN CONSTRUCTION
    System.out.println("Constructing Markov chain...");
    numberofNodes = interProcItemsList.size() + numberofNodesMerged;

    String graphOutput = "digraph {\n";
    String prismModel = "dtmc\n\n" + "module " + modelName + "\n\n";
    String assertionReachabilityNode="", assertionExecutionNode="";
    prismModel += "\t" + "s : [0.." + numberofNodes +"] init 0;\n\n";


    for (String jsonItem: interProcItemsList) {

      if(jsonItem.startsWith("[ "))
        jsonItem = jsonItem.substring(2);
      String jsonItemID = jsonItem.split(" ")[4];

      if (jsonItem.contains("\"secret_dependent_branch\" : \"true\"")) {
        //start: additional code for counting secret dependent branches
        long startTime = System.currentTimeMillis();
        String ins_to_translate = jsonItem.split("\"ins_to_translate\" : \"")[1].split("\"")[0];
        System.out.println("Instruction to translate: " + ins_to_translate);

        List<String> smtConsList = translateToSMTLib(ins_to_translate, itemProcMap.get(jsonItemID.split("#")[0]));
        System.out.println(smtConsList.get(1));

        modelCounter.setBound(15);
        modelCounter.setModelCountMode("abc.linear_integer_arithmetic");
        BigDecimal cons_count = modelCounter.getModelCount(smtConsList.get(1));
        BigDecimal dom_count = modelCounter.getModelCount(smtConsList.get(0));

        double true_prob = 0.0;
        double false_prob = 0.0;

        String[] ins_part = ins_to_translate.split("and");

        //boolean flag_to_update_prob = false;
        if(ins_part.length >= 2 && ins_part[1].contains("not")) {
          false_prob = cons_count.doubleValue() / dom_count.doubleValue();
          true_prob = 1.0 - false_prob;
        } else {
          //flag_to_update_prob = true;
          true_prob = cons_count.doubleValue() / dom_count.doubleValue();
          false_prob = 1.0 - true_prob;
        }

        System.out.println("Probability of true branch: " + true_prob);
        System.out.println("Probability of false branch: " + false_prob);

        // Using branch selectivity separately ---------------------------------------
        ISSABasicBlock node = itemNodeMap.get(jsonItemID);
        if(node == null) {
          String[] jsonItemArr = jsonItemID.split("#");
          node = itemNodeMap.get(jsonItemArr[0]+"#"+jsonItemArr[jsonItemArr.length-1]);
        }
        if(nodeLineMap.containsKey(node)) {
          int line = nodeLineMap.get(node);
          String className = currentCFG.getProcedure().getClassName().replace("/", ".");
          String[] classNamePart = className.split("\\.");
          className = classNamePart[classNamePart.length-1];
          String key = className.replace("L", "") + ".java:" + line;
          if (branchProbMap.containsKey(key)) {
            //if(flag_to_update_prob) {
            true_prob = branchProbMap.get(key);
            false_prob = 1.0 - true_prob;
            //} else {
            //    false_prob = branchProbMap.get(key);
            //    true_prob = 1.0 - false_prob;
            //}
          }
        } else {
          true_prob = 0.5;
          false_prob = 0.5;
        }
        //----------------------------------------------------------------------------


        //end: additional code for counting secret dependent branches
        jsonItem = jsonItem.replace("\"secret_dependent_branch\" : \"true\"", "\"secret_dependent_branch\" : \"true\", \"true_branch_probability\" : \"" + true_prob + "\", \"false_branch_probability\" : \"" + false_prob + "\"");


        String fromNode = getNodeFromID(jsonItemID);


        String[] outgoingNodes = jsonItem.split("\"outgoing\" : \\{ ")[1].split(" }")[0].split(",");

        String trueNode = getNodeFromID(outgoingNodes[0].split("\"")[1]);

        if(outgoingNodes.length == 2 && outgoingNodes[1].contains("\"")) {

          String falseNode = getNodeFromID(outgoingNodes[1].split("\"")[1]);

          String trueNodeProb = "";
          String falseNodeProb = "";

          if (fromNode.equals(assertionReachabilityNode)) {
            assertionExecutionNode = falseNode;
          }

          if (jsonItem.contains("$assertionsDisabled")) {
            assertionReachabilityNode = falseNode;
            assertionNode = assertionReachabilityNode;

            //quick naive fix to deal with assertion reachble node when there is an exception to handle
            if (!jsonItem.contains("\"exception\" : \"true\"")) {
              trueNodeProb = "0.0";
              falseNodeProb = "1.0";
            } else {
              trueNodeProb = Double.toString(true_prob);
              falseNodeProb = Double.toString(false_prob);
            }
          } else {
            trueNodeProb = jsonItem.split("\"true_branch_probability\" : \"")[1].split("\"")[0];
            falseNodeProb = jsonItem.split("\"false_branch_probability\" : \"")[1].split("\"")[0];
          }

          List<String> edgeList = edgeMap.get(fromNode);
          if (edgeList == null) {
            edgeList = new ArrayList<>();
          }
          edgeList.add(trueNode);
          edgeList.add(falseNode);
          edgeMap.put(fromNode,edgeList);

          MarkovChainInformation trueChain = new MarkovChainInformation(fromNode,trueNode,trueNodeProb,true, false, false);
          MarkovChainInformation falseChain = new MarkovChainInformation(fromNode,falseNode,falseNodeProb,true, false, false);
          transitionMap.put(new Pair<>(fromNode,trueNode), trueChain);
          transitionMap.put(new Pair<>(fromNode,falseNode), falseChain);

          List<MarkovChainInformation> list = new ArrayList<>();
          list.add(trueChain);
          list.add(falseChain);
          transitionlistMap.put(fromNode, list);

          graphOutput += "\t" + fromNode + " -> " + trueNode + "[label= " + "\"" + trueNodeProb + "\"];\n";
          graphOutput += "\t" + fromNode + " -> " + falseNode + "[label= " + "\"" + falseNodeProb + "\"];\n";

          prismModel += "\t" + "[] s = " + fromNode + " -> " + trueNodeProb + " : " + "(s' = " + trueNode + ") + " + falseNodeProb + " : " + "(s' = " + falseNode + ");\n";

          branchNodes.add(trueNode);
          branchNodes.add(falseNode);

        } else {
          List<String> edgeList = edgeMap.get(fromNode);
          if (edgeList == null) {
            edgeList = new ArrayList<>();
          }
          edgeList.add(trueNode);
          edgeMap.put(fromNode,edgeList);

          MarkovChainInformation trueChain = new MarkovChainInformation(fromNode,trueNode,"1.0",true, false, false);
          transitionMap.put(new Pair<>(fromNode,trueNode), trueChain);
          List<MarkovChainInformation> list = new ArrayList<>();
          list.add(trueChain);
          transitionlistMap.put(fromNode, list);

          graphOutput += "\t" + fromNode + " -> " + trueNode + "[label= " + "\"" + "1.0" + "\"];\n";

          prismModel += "\t" + "[] s = " + fromNode + " -> " + "1.0" + " : " + "(s' = " + trueNode + ");\n";
        }

        String[] splittedID = jsonItemID.split("#");
        String idModelCount = splittedID[0]+"#"+splittedID[splittedID.length-1];
        long finishTime = System.currentTimeMillis();
        long elapsedTime = finishTime - startTime;
        modelCountingTimeMap.put(idModelCount, elapsedTime);

      } else if (jsonItem.contains("\"secret_dependent_branch\" : \"branch\"")) {

        String fromNode = getNodeFromID(jsonItemID);


        String[] outgoingNodes = jsonItem.split("\"outgoing\" : \\{ ")[1].split(" }")[0].split(",");

        String trueNode = getNodeFromID(outgoingNodes[0].split("\"")[1]);


        if (outgoingNodes.length == 2) {
          System.out.println(jsonItemID);
          System.out.println(outgoingNodes[1]);
          if(!outgoingNodes[1].contains("\""))
            continue;
          String falseNode = getNodeFromID(outgoingNodes[1].split("\"")[1]);

          if (fromNode.equals(assertionReachabilityNode)) {
            assertionExecutionNode = falseNode;
          }

          List<String> edgeList = edgeMap.get(fromNode);
          if (edgeList == null) {
            edgeList = new ArrayList<>();
          }
          edgeList.add(trueNode);
          edgeList.add(falseNode);
          edgeMap.put(fromNode,edgeList);


          if (jsonItem.contains("$assertionsDisabled")) {
            assertionReachabilityNode = falseNode;
            assertionNode = assertionReachabilityNode;
            if (!jsonItem.contains("\"exception\" : \"true\"")) {
              MarkovChainInformation trueChain = new MarkovChainInformation(fromNode,trueNode,"0.0",false, jsonItem.contains("$assertionsDisabled"), true);
              MarkovChainInformation falseChain = new MarkovChainInformation(fromNode,falseNode,"1.0",false, jsonItem.contains("$assertionsDisabled"), true);
              transitionMap.put(new Pair<>(fromNode,trueNode), trueChain);
              transitionMap.put(new Pair<>(fromNode,falseNode), falseChain);

              List<MarkovChainInformation> list = new ArrayList<>();
              list.add(trueChain);
              list.add(falseChain);
              transitionlistMap.put(fromNode, list);

              graphOutput += "\t" + fromNode + " -> " + trueNode + "[label= " + "\"" + "0.0" + "\"];\n";
              graphOutput += "\t" + fromNode + " -> " + falseNode + "[label= " + "\"" + "1.0" + "\"];\n";
              prismModel += "\t" + "[] s = " + fromNode + " -> " + "0.0" + " : " + "(s' = " + trueNode + ") + " + "1.0" + " : " + "(s' = " + falseNode + ");\n";

              //branchNodes.add(trueNode);
              //branchNodes.add(falseNode);

            } else {
              long startTime = System.currentTimeMillis();
              String ins_to_translate = jsonItem.split("\"ins_to_translate\" : \"")[1].split("\"")[0];
              System.out.println("Instruction to translate: " + ins_to_translate);

              List<String> smtConsList = translateToSMTLib(ins_to_translate, itemProcMap.get(jsonItemID.split("#")[0]));
              System.out.println(smtConsList.get(1));

              modelCounter.setBound(31);
              modelCounter.setModelCountMode("abc.linear_integer_arithmetic");
              BigDecimal cons_count = modelCounter.getModelCount(smtConsList.get(1));
              BigDecimal dom_count = modelCounter.getModelCount(smtConsList.get(0));

              double true_prob = 0.0;
              double false_prob = 0.0;

              String[] ins_part = ins_to_translate.split("and");

              //boolean flag_to_update_prob = false;
              if(ins_part.length >= 2 && ins_part[1].contains("not")) {
                false_prob = cons_count.doubleValue() / dom_count.doubleValue();
                true_prob = 1.0 - true_prob;
              } else {
                //flag_to_update_prob = true;
                true_prob = cons_count.doubleValue() / dom_count.doubleValue();
                false_prob = 1.0 - true_prob;
              }

              // Using branch selectivity separately ---------------------------------------
              ISSABasicBlock node = itemNodeMap.get(jsonItemID);
              if(node == null) {
                String[] jsonItemArr = jsonItemID.split("#");
                node = itemNodeMap.get(jsonItemArr[0]+"#"+jsonItemArr[jsonItemArr.length-1]);
              }
              if(nodeLineMap.containsKey(node)) {
                int line = nodeLineMap.get(node);
                String className = currentCFG.getProcedure().getClassName().replace("/", ".");
                String key = className.replace("L", "") + ".java:" + line;
                if (branchProbMap.containsKey(key)) {
                  //if(flag_to_update_prob) {
                  true_prob = branchProbMap.get(key);
                  false_prob = 1.0 - true_prob;
                  //} else {
                  //    false_prob = branchProbMap.get(key);
                  //    true_prob = 1.0 - false_prob;
                  //}
                }
              } else {
                true_prob = 0.5;
                false_prob = 0.5;
              }
              //----------------------------------------------------------------------------

              //end: additional code for counting secret dependent branches
              jsonItem = jsonItem.replace("\"secret_dependent_branch\" : \"branch\"", "\"secret_dependent_branch\" : \"true\", \"true_branch_probability\" : \"" + true_prob + "\", \"false_branch_probability\" : \"" + false_prob + "\"");




              MarkovChainInformation trueChain = new MarkovChainInformation(fromNode,trueNode,Double.toString(true_prob),false, jsonItem.contains("$assertionsDisabled"), false);
              MarkovChainInformation falseChain = new MarkovChainInformation(fromNode,falseNode,Double.toString(false_prob),false, jsonItem.contains("$assertionsDisabled"), false);
              transitionMap.put(new Pair<>(fromNode,trueNode), trueChain);
              transitionMap.put(new Pair<>(fromNode,falseNode), falseChain);

              List<MarkovChainInformation> list = new ArrayList<>();
              list.add(trueChain);
              list.add(falseChain);
              transitionlistMap.put(fromNode, list);

              graphOutput += "\t" + fromNode + " -> " + trueNode + "[label= " + "\"" + true_prob + "\"];\n";
              graphOutput += "\t" + fromNode + " -> " + falseNode + "[label= " + "\"" + false_prob + "\"];\n";
              prismModel += "\t" + "[] s = " + fromNode + " -> " + true_prob + " : " + "(s' = " + trueNode + ") + " + false_prob + " : " + "(s' = " + falseNode + ");\n";

              branchNodes.add(trueNode);
              branchNodes.add(falseNode);

              String[] splittedID = jsonItemID.split("#");
              String idModelCount = splittedID[0]+"#"+splittedID[splittedID.length-1];
              long finishTime = System.currentTimeMillis();
              long elapsedTime = finishTime - startTime;
              modelCountingTimeMap.put(idModelCount, elapsedTime);
            }
          } else {
            MarkovChainInformation trueChain = new MarkovChainInformation(fromNode,trueNode,"1.0",false, jsonItem.contains("$assertionsDisabled"), false);
            MarkovChainInformation falseChain = new MarkovChainInformation(fromNode,falseNode,"1.0",false, jsonItem.contains("$assertionsDisabled"), false);
            transitionMap.put(new Pair<>(fromNode,trueNode), trueChain);
            transitionMap.put(new Pair<>(fromNode,falseNode), falseChain);

            List<MarkovChainInformation> list = new ArrayList<>();
            list.add(trueChain);
            list.add(falseChain);
            transitionlistMap.put(fromNode, list);

            graphOutput += "\t" + fromNode + " -> " + trueNode + "[label= " + "\"" + "1.0" + "\"];\n";
            graphOutput += "\t" + fromNode + " -> " + falseNode + "[label= " + "\"" + "1.0" + "\"];\n";
            prismModel += "\t" + "[] s = " + fromNode + " -> " + "1.0" + " : " + "(s' = " + trueNode + ");\n";
            prismModel += "\t" + "[] s = " + fromNode + " -> " + "1.0" + " : " + "(s' = " + falseNode + ");\n";
          }
        } else {
          List<String> edgeList = edgeMap.get(fromNode);
          if (edgeList == null) {
            edgeList = new ArrayList<>();
          }
          edgeList.add(trueNode);
          edgeMap.put(fromNode,edgeList);

          MarkovChainInformation trueChain = new MarkovChainInformation(fromNode,trueNode,"1.0",false, false, false);
          transitionMap.put(new Pair<>(fromNode,trueNode), trueChain);
          List<MarkovChainInformation> list = new ArrayList<>();
          list.add(trueChain);
          transitionlistMap.put(fromNode, list);

          graphOutput += "\t" + fromNode + " -> " + trueNode + "[label= " + "\"" + "1.0" + "\"];\n";
          prismModel += "\t" + "[] s = " + fromNode + " -> " + "1.0" + " : " + "(s' = " + trueNode + ");\n";
        }
      } else {
        String fromNode = getNodeFromID(jsonItemID);

        String[] outgoingNodeStringArr = jsonItem.split("\"outgoing\" : \\{ ");

        String outgoingNodeString = "";

        if(outgoingNodeStringArr.length > 1)
          outgoingNodeString = outgoingNodeStringArr[1].split(" }")[0];

        if(outgoingNodeString.equals(""))
          continue;

        String[] outgoingNodes = outgoingNodeString.split(",");

        String selectedOutgoing = "";
        String nonSelectedOutgoing = "";

        if(outgoingNodes.length > 1) {
          selectedOutgoing = outgoingNodes[1];
          nonSelectedOutgoing = outgoingNodes[0];
        } else {
          selectedOutgoing = outgoingNodes[0];
        }

        if (selectedOutgoing.contains("#")) {
          String toJsonItemID = selectedOutgoing.split("\"")[1];
          String toNode = getNodeFromID(toJsonItemID);


          String[] jIDs = jsonItemID.split("#");
          String[] toJIDs = toJsonItemID.split("#");

          String id1=jIDs[0];
          String id2=toJIDs[0];
          for(int j=1; j < jIDs.length-1; j++) {
            id1 += "#"+jIDs[j];
          }
          for(int j=1; j < toJIDs.length-1; j++) {
            id2 += "#"+toJIDs[j];
          }

          String checkID1 = jIDs[0]+"#"+jIDs[1];
          String checkID2 = toJIDs[0]+"#"+toJIDs[1];

          if(!id1.equals(id2) && !jsonItemID.contains("1001001")) {
            procCallMap.put(checkID1,checkID2);
            interProcDomMap.put(toNode, fromNode);
            interProcPostDomMap.put(fromNode,toNode);
          }

          List<String> edgeList = edgeMap.get(fromNode);
          if (edgeList == null) {
            edgeList = new ArrayList<>();
          }
          edgeList.add(toNode);
          edgeMap.put(fromNode,edgeList);

          MarkovChainInformation trueChain = new MarkovChainInformation(fromNode,toNode,"1.0",false, false, false);
          transitionMap.put(new Pair<>(fromNode,toNode), trueChain);
          List<MarkovChainInformation> list = new ArrayList<>();
          list.add(trueChain);
          transitionlistMap.put(fromNode, list);

          graphOutput += "\t" + fromNode + " -> " + toNode + "[label= " + "\"" + "1.0" + "\"];\n";

          if (!nonSelectedOutgoing.equals("") && nonSelectedOutgoing.contains("#")) {
            String ignoredJsonItemID = nonSelectedOutgoing.split("\"")[1];
            String ignoredNode = getNodeFromID(ignoredJsonItemID);

            MarkovChainInformation ignoredChain = new MarkovChainInformation(fromNode,ignoredNode,"0.0",false, false, false);
            transitionMap.put(new Pair<>(fromNode,ignoredNode), ignoredChain);
            List<MarkovChainInformation> list2 = transitionlistMap.get(fromNode);
            list2.add(ignoredChain);
            transitionlistMap.put(fromNode, list2);

            graphOutput += "\t" + fromNode + " -> " + toNode + "[label= " + "\"" + "0.0" + "\"];\n";
            prismModel += "\t" + "[] s = " + fromNode + " -> " + "1.0" + " : " + "(s' = " + toNode + ") + " + "0.0" + " : " + "(s' = " + ignoredNode + ");\n";
          } else {
            prismModel += "\t" + "[] s = " + fromNode + " -> " + "1.0" + " : " + "(s' = " + toNode + ");\n";
          }
        } else {
          MarkovChainInformation trueChain = new MarkovChainInformation(fromNode,fromNode,"1.0",false, false, false);
          transitionMap.put(new Pair<>(fromNode,fromNode), trueChain);
          List<MarkovChainInformation> list = new ArrayList<>();
          list.add(trueChain);
          transitionlistMap.put(fromNode, list);

          graphOutput += "\t" + fromNode + " -> " + fromNode + "[label= " + "\"" + "1.0" + "\"];\n";
          prismModel += "\t" + "[] s = " + fromNode + " -> " + "1.0" + " : " + "(s' = " + fromNode + ");\n";

          endNode = fromNode;
        }
      }
    }

    graphOutput += "}";
    //System.out.println(graphOutput);

    prismModel += "\nendmodule";
    //System.out.println(prismModel);


    long dfs = System.currentTimeMillis();
    long det = dfs - dts;

    long dts2 = System.currentTimeMillis();

    String id = idMap.get(Integer.parseInt(assertionReachabilityNode));
    String[] splittedID = id.split("#");
    Procedure proc = itemProcMap.get(splittedID[0]);
    ISSABasicBlock node = itemNodeMap.get(splittedID[0]+"#"+splittedID[splittedID.length-1]);
    Set<ISSABasicBlock> domSet = proc.getDominatorSet(node);

    /* Extract assertion subgraph given a target statement */
    if(extractSubGraphFlag) {
      extractAssertionSubGraph(proc, domSet, node, assertionReachabilityNode, replaceMap);
    }

    long dfs2 = System.currentTimeMillis();
    long det2 = dfs2 - dts2;
    long dts3 = System.currentTimeMillis();

    /* Loop unrolling */
    if (loopUnrollingFlag) {
      Pair<String, String> graph_dtmc = unrollLoop(proc, domSet, replaceMap, modelName);
      graphOutput = graph_dtmc.getKey();
      prismModel = graph_dtmc.getValue();
    }


    long dfs3 = System.currentTimeMillis();
    long det3 = dfs3 - dts3;

    long dts4 = System.currentTimeMillis();


    String fileName = currentCFG.getProcedure().getClassName().replace("/","_") + "_" + currentCFG.getProcedure().getProcedureName() + ".dot";
    try {
      BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
      //writer.write(markovChainOutput);
      System.out.println(graphOutput);
      writer.write(graphOutput); //ignoring assertion subgraph extraction
      writer.close();
    } catch(IOException ex) {
      System.out.println(ex);
    }

    String model_file = currentCFG.getProcedure().getClassName().replace("/","_") + "_" + currentCFG.getProcedure().getProcedureName() + ".sm";
    try {
      BufferedWriter writer = new BufferedWriter(new FileWriter(model_file));
      //writer.write(prismOutput);
      System.out.println(prismModel);
      writer.write(prismModel); //ignoring assertion subgraph extraction
      writer.close();
    } catch(IOException ex) {
      System.out.println(ex);
    }

    String assertionReachabilitySpec = "";
    String assertionExecutionSpec = "";

    if (!assertionReachabilityNode.equals("")) {
      if(loopbound > 1 && backEdgeExists) {
        assertionReachabilitySpec = "P=? [F (s = " + assertionReachabilityNode + ")";
        for(int b=1; b<loopbound; b++) {
          String bNode = Integer.toString(Integer.parseInt(assertionReachabilityNode) + b * numberofNodes);
          assertionReachabilitySpec += " | (s = " + bNode + ")";
        }
        assertionReachabilitySpec += "]";
      } else {
        assertionReachabilitySpec = "P=? [F s = " + assertionReachabilityNode + "]";
      }
    }

    //if (!assertionExecutionNode.equals(""))
    //  assertionExecutionSpec = "P=? [F s = " + assertionExecutionNode + "]";

    System.out.println("assertionReachabilitySpec: " + assertionReachabilitySpec);
    //System.out.println("assertionExecutionSpec: " + assertionExecutionSpec);

    int num_properties = 0;
    String proerties_file = currentCFG.getProcedure().getClassName().replace("/","_") + "_" + currentCFG.getProcedure().getProcedureName() + ".csl";
    try {
      BufferedWriter writer = new BufferedWriter(new FileWriter(proerties_file));
      if(!assertionReachabilitySpec.equals("") && !assertionExecutionSpec.equals("")) {
        writer.write(assertionReachabilitySpec + "\n" + assertionExecutionSpec);
        num_properties = 2;
      }
      else if (!assertionReachabilitySpec.equals("")) {
        writer.write(assertionReachabilitySpec);
        num_properties = 1;
      }

//      for(String p : branchNodes) {
//          writer.write("P=? [F s = " + p + "]\n");
//      }

      writer.close();
    } catch(IOException ex) {
      System.out.println(ex);
    }


    long p1timeElapsed=0,p2timeElapsed=0;
    //if (num_properties >= 1) {
    long p1start = System.currentTimeMillis();
    Process proc1 = null;
    try {
      //proc1 = Runtime.getRuntime().exec(new String[]{"/PReach/prism-4.5-linux64/bin/prism", model_file, proerties_file, "-prop", "1"});

      proc1 = Runtime.getRuntime().exec(new String[]{prismBinary, model_file, proerties_file});

      if (proc1 == null) return;

      BufferedReader stdInput = new BufferedReader(new
              InputStreamReader(proc1.getInputStream()));

      BufferedReader stdError = new BufferedReader(new
              InputStreamReader(proc1.getErrorStream()));

      // Read the output from the command
      String s = null;
//      if ((s = stdInput.readLine()) != null) {
//        System.out.println("PRISM run output:\n");
//      }

        /*int numOfSelectiveBranchNodes = 0;
        while ((s = stdInput.readLine()) != null) {
          System.out.println(s);
          if (s.contains("Result: ")) {
            String prob = s.split("Result: ")[1].split(" ")[0];
            //System.out.println("Probability to reach assertion: " + prob);
              double state_prob = Double.parseDouble(prob);
              if (state_prob < 0.05) {
                  numOfSelectiveBranchNodes++;
              }
          }
        }

        System.out.println("Number of branch nodes: " + branchNodes.size());
        System.out.println("Number of selective branch nodes: " + numOfSelectiveBranchNodes);*/

      // Read any errors from the attempted command
      while ((s = stdError.readLine()) != null) {
        System.out.println(s);
      }

      while ((s = stdInput.readLine()) != null) {
        System.out.println(s);
        if (s.contains("Result: ")) {
          String prob = s.split("Result: ")[1].split(" ")[0];
          System.out.println("Probability for assertion reachability: " + prob);
          break;
        }
      }
    } catch (Exception ex) {
      System.out.println(ex);
    }
    long p1finish = System.currentTimeMillis();
    p1timeElapsed = p1finish - p1start;
    //}

    if(num_properties == 2) {
      long p2start = System.currentTimeMillis();
      Process proc2 = null;
      try {
        proc2 = Runtime.getRuntime().exec(new String[]{prismBinary, model_file, proerties_file, "-prop", "2"});

        if (proc2 == null) return;
        ;

        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(proc2.getInputStream()));

        BufferedReader stdError = new BufferedReader(new
                InputStreamReader(proc2.getErrorStream()));

        // Read the output from the command
        String s = null;
//      if ((s = stdInput.readLine()) != null) {
//        System.out.println("PRISM run output:\n");
//      }


        // Read any errors from the attempted command
        while ((s = stdError.readLine()) != null) {
          System.out.println(s);
        }

        while ((s = stdInput.readLine()) != null) {
          //System.out.println(s);
          if (s.contains("Result: ")) {
            String prob = s.split("Result: ")[1].split(" ")[0];
            System.out.println("Probability for assertion failure: " + prob);
            break;
          }
        }
      } catch (Exception ex) {
        System.out.println(ex);
      }
      long p2finish = System.currentTimeMillis();
      p2timeElapsed = p2finish - p2start;
    }

    long finish = System.currentTimeMillis();
    long timeElapsed = finish - start;

    long dfs4 = System.currentTimeMillis();
    long det4 = dfs4 - dts4;


    long totalExecutionTime = dependencyAnalysisTime + timeElapsed;

    long executionTimeWithDominatorAnalysis = totalExecutionTime - extraModelCountingTime;

    System.out.println("Dependency analysis time: " + dependencyAnalysisTime + "ms");
    System.out.println("Branch condition preprocessing time: " + det5 + "ms");
    System.out.println("Dominator Analysis time: " + det2 + "ms");
    System.out.println("Loop unrolling time: " + det3 + "ms");
    System.out.println("File writing and PRISM time: " + det4 + "ms");
    System.out.println("Execution time for probabilistic analysis: " + timeElapsed + "ms");
    System.out.println("Total Execution time: " + totalExecutionTime + "ms");
    System.out.println("Total Execution time with dominator analysis: " + executionTimeWithDominatorAnalysis + "ms");
    float percentageOfNodesReduced = (float)numberofNodesReduced/(numberofNodes+numberofNodesMerged);
    percentageOfNodesReduced = percentageOfNodesReduced * 100;
    System.out.println("Number of nodes reduced in subgraph: " + numberofNodesReduced + "(" + percentageOfNodesReduced + "%)") ;
    //long p1execTime = totalExecutionTime - p2timeElapsed;
    //long p2execTime = totalExecutionTime - p1timeElapsed;
    //System.out.println("Execution time for P1: " + p1execTime + "ms");
    //System.out.println("Execution time for P2: " + p2execTime + "ms");
  }


  public void extractAssertionSubGraph(Procedure proc, Set<ISSABasicBlock> domSet, ISSABasicBlock node, String assertionReachabilityNode, Map<String, String> replaceMap) {

    /* dominator analysis for probability distribution to true or false branch
    ++removing extra analyis where branching and merging does not effect result */


    /* For all the dominators of assertionReachability node if there is no direct connection between a pair of dominators
     * 1. make a direct connection with probability 1.0
     * 2. remove all in between nodes and the transitions, it can be tricky, start from the dominator for which there
     * was no edge to the other dominator node. From both false and true node remove all the nodes until it reaches the
     * other dominator node
     * */

    ISSABasicBlock prevImDom = node;
    ISSABasicBlock imDom = proc.getImmediateDominator(node);

    String nodeItem = nodeItemMap.get(imDom);
    String imDomNode = "";
    if(nodeMap.get(nodeItem) == null) {
      String[] nodeItemArr = nodeItem.split("#");
      nodeItem = nodeItemArr[0] + "#1#" + nodeItemArr[nodeItemArr.length-1];
    }
    imDomNode = Integer.toString(nodeMap.get(nodeItem));
    if(procCallMap.containsKey(nodeItemMap.get(imDom))) {

      String idProc = procCallMap.get(nodeItemMap.get(imDom));
      Procedure procedure = itemProcMap.get(idProc.split("#")[0]);

      while(!idProc.equals("")) {
        for (ISSABasicBlock nd : procedure.getNodeSet()) {
          String n = nodeItemMap.get(nd);
          if (modelCountingTimeMap.get(n) != null)
            extraModelCountingTime += modelCountingTimeMap.get(n);
        }
        if(procCallMap.containsKey(idProc)) {
          idProc = procCallMap.get(idProc);
          procedure = itemProcMap.get(idProc.split("#")[0]);
        } else {
          idProc = "";
        }
      }
    }
//      if(interProcDomMap.get(imDomNode) != null) {
//          imDomNode = interProcDomMap.get(imDomNode);
//          String imDomNodeID = idMap.get(Integer.parseInt(imDomNode));
//          String[] imDomNodeIDsplitted = imDomNodeID.split("#");
//          imDomNodeID = imDomNodeIDsplitted[0] + "#"+ imDomNodeIDsplitted[imDomNodeIDsplitted.length-1];
//          imDom = itemNodeMap.get(imDomNodeID);
//      }

    while(imDom != null) {
      String ns = nodeItemMap.get(imDom);
      if(nodeMap.get(ns) == null) {
//          String[] itIDArr = ns.split("#");
//          String itID = itIDArr[0] + "#" + itIDArr[itIDArr.length-1];
//          String updatedNS = replaceMap.get("\""+itID+"\"");
        String updatedNS = replaceMap.get("\""+ns+"\"");
//          if(itIDArr.length == 3) {
//            String[] updateditIDArr = updatedNS.split("#");
//            updatedNS = updateditIDArr[0] + "#" + itIDArr[1] + "#" + itIDArr[itIDArr.length - 1];
//          }
        if(updatedNS != null) {
          ns = updatedNS.substring(1,updatedNS.length()-1);
          if(nodeMap.get(ns) == null) {
            ns = ns.split("#")[0] + "#1#" + ns.split("#")[1]; //todo: change this implementation
          }
        }
      }
      if(nodeMap.get(ns) != null) {
        String fromNode = Integer.toString(nodeMap.get(ns));

        ns = nodeItemMap.get(prevImDom);
        if(nodeMap.get(ns) == null) {
//              String[] itIDArr = ns.split("#");
//              String itID = itIDArr[0] + "#" + itIDArr[itIDArr.length-1];
//              String updatedNS = replaceMap.get("\""+itID+"\"");
          String updatedNS = replaceMap.get("\""+ns+"\"");
//              if(itIDArr.length == 3) {
//                String[] updateditIDArr = updatedNS.split("#");
//                updatedNS = updateditIDArr[0] + "#" + itIDArr[1] + "#" + itIDArr[itIDArr.length - 1];
//              }
          if(updatedNS != null) {
            ns = updatedNS.substring(1,updatedNS.length()-1);
            if(nodeMap.get(ns) == null) {
              ns = ns.split("#")[0] + "#1#" + ns.split("#")[1]; //todo: change this implementation
            }
          }
        }
        if (nodeMap.get(ns) != null) {
          String toNode = Integer.toString(nodeMap.get(ns));

          List<MarkovChainInformation> toList = transitionlistMap.get(fromNode);
          boolean flagToUpdate = true;
          ISSABasicBlock fNode = itemNodeMap.get(idMap.get(Integer.parseInt(fromNode)));
          ISSABasicBlock tNode = itemNodeMap.get(idMap.get(Integer.parseInt(toNode)));
          if(fromNode.equals(toNode) || !proc.getPostDominatorSet(fNode).contains(tNode) || toList.size() == 1) {
            flagToUpdate = false;
          }
//          for (MarkovChainInformation mi : toList) {
//            if (mi.getToNode().equals(toNode) /*&& mi.getProb().equals("1.0")*/) { // to make sure that there is no connection between dominators
//              flagToUpdate = false;
//              break;
//            }
//          }


          if (flagToUpdate) {
            MarkovChainInformation directChain = new MarkovChainInformation(fromNode, toNode, "1.0", false, false, false);
            List<MarkovChainInformation> list = new ArrayList<>();
            list.add(directChain);
            transitionlistMap.put(fromNode, list);
            String m = idMap.get(Integer.parseInt(fromNode));
            if(modelCountingTimeMap.get(m) != null) {
              //numberofNodesReduced += 2;
              extraModelCountingTime += modelCountingTimeMap.get(m);
            }


            for (MarkovChainInformation mi : toList) {
              if (!mi.getToNode().equals(toNode)) {
                boolean[] removeVisited = new boolean[numberofNodes+1];
                removeAllTransitions(mi.getToNode(), toNode,proc, removeVisited);
                ISSABasicBlock aNode = itemNodeMap.get(idMap.get(Integer.parseInt(toNode)));
                ISSABasicBlock mNode = itemNodeMap.get(idMap.get(Integer.parseInt(mi.getToNode())));
                Set<ISSABasicBlock> aNodeDominatorSet = proc.getDominatorSet(aNode);
                if(toNode.equals(assertionNode) && !aNodeDominatorSet.contains(mNode)) {
                  //numberofNodesReduced += 1;
                  transitionlistMap.remove(mi.getToNode());
                }
                m = idMap.get(Integer.parseInt(mi.getToNode()));
                if(modelCountingTimeMap.get(m) != null) {
                  //numberofNodesReduced += 2;
                  extraModelCountingTime += modelCountingTimeMap.get(m);
                }
              }
            }
          }
        }
      }

      prevImDom = imDom;
      imDom = proc.getImmediateDominator(imDom);
//        if(procCallMap.containsKey(nodeItemMap.get(imDom))) {
//
//            String idProc = procCallMap.get(nodeItemMap.get(imDom));
//            Procedure procedure = itemProcMap.get(idProc.split("#")[0]);
//
//            while(!idProc.equals("")) {
//                for (ISSABasicBlock nd : procedure.getNodeSet()) {
//                    numberofNodesReduced++;
//                    String n = nodeItemMap.get(nd);
//                    if (modelCountingTimeMap.get(n) != null)
//                        extraModelCountingTime += modelCountingTimeMap.get(n);
//                }
//                if(procCallMap.containsKey(idProc)) {
//                    idProc = procCallMap.get(idProc);
//                    procedure = itemProcMap.get(idProc.split("#")[0]);
//                } else {
//                    idProc = "";
//                }
//            }
//        }


      if(imDom.equals(prevImDom))
        break;
    }

  }

  public Pair<String, String> unrollLoop(Procedure proc, Set<ISSABasicBlock> domSet, Map<String, String> replaceMap, String modelName) {
    boolean[] visited = new boolean[numberofNodes+1];
    boolean[] recStack = new boolean[numberofNodes+1];


    isCyclicUtil(0, 0, visited, recStack);

    //to remove all the unnecessary branching which are not relared to assertion node
    String aNodeID = idMap.get(Integer.parseInt(assertionNode));
    if(itemNodeMap.get(aNodeID) == null) {
      String[] aNodeIDArr = aNodeID.split("#");
      aNodeID = aNodeIDArr[0] + "#" + aNodeIDArr[aNodeIDArr.length-1];
    }
    ISSABasicBlock aNode = itemNodeMap.get(aNodeID);
    aNode = proc.getImmediateDominator(aNode);

    Set<ISSABasicBlock> assertionDomSet = proc.getDominatorSet(aNode);


    //Add nodes from other methods in the dominator set
    Set<ISSABasicBlock> tempSet = new HashSet<>();
    for(ISSABasicBlock domNode : assertionDomSet) {
      String itemID = nodeItemMap.get(domNode);

      if(nodeMap.get(itemID) == null) {
        //String[] itemIDArr = itemID.split("#");
        //itemID = itemIDArr[0] + "#" + itemIDArr[itemIDArr.length - 1];
        if(replaceMap.containsKey("\""+itemID+"\"")) {

          itemID = replaceMap.get("\""+itemID+"\"");

//          if(itemIDArr.length == 3) {
//            String[] upitemIDArr = itemID.split("#");
//            itemID = upitemIDArr[0] + "#" + itemIDArr[1] + "#" + itemIDArr[upitemIDArr.length - 1];
//          }

          itemID = itemID.substring(1,itemID.length()-1);
        }
        if(nodeMap.get(itemID) == null) {
          String[] itemIDArr = itemID.split("#");
          itemID = itemIDArr[0] + "#1#" + itemIDArr[itemIDArr.length - 1];
          if(replaceMap.containsKey("\""+itemID+"\"")) {
            itemID = replaceMap.get("\""+itemID+"\"");
            itemID = itemID.substring(1,itemID.length()-1);
          }
        }
      }

      String item = Integer.toString(nodeMap.get(itemID));

      if(interProcDomMap.containsKey(item)) {
        String newItemID = idMap.get(Integer.parseInt(interProcDomMap.get(item)));
        String[] newItemIDArr = newItemID.split("#");
        newItemID = newItemIDArr[0] + "#" + newItemIDArr[newItemIDArr.length-1];
        ISSABasicBlock newNode = itemNodeMap.get(newItemID);
        Procedure newProc = itemProcMap.get(newItemID.split("#")[0]);
        tempSet.addAll(newProc.getDominatorSet(newNode));
      }
    }

    assertionDomSet.addAll(tempSet);

    for (Map.Entry<String,List<MarkovChainInformation>> entry : transitionlistMap.entrySet()) {
      String eNodeID = idMap.get(Integer.parseInt(entry.getKey()));
      if(itemNodeMap.get(eNodeID) == null) {
        String[] eNodeIDArr = eNodeID.split("#");
        eNodeID = eNodeIDArr[0] + "#" + eNodeIDArr[eNodeIDArr.length-1];
      }
      ISSABasicBlock eNode = itemNodeMap.get(eNodeID);

      //if(!nodeItemMap.get(aNode).split("#")[0].equals(nodeItemMap.get(eNode).split("#")[0]))
      //  continue;
      if(eNode != null && !assertionDomSet.contains(eNode) && entry.getValue().size() == 2) {
        List<MarkovChainInformation> miList = entry.getValue();
        miList.clear();
        Procedure nodeProc = itemProcMap.get(nodeItemMap.get(eNode).split("#")[0]);
        ISSABasicBlock directNode = nodeProc.getImmediatePostDominator(eNode);
        String eNodeItem = nodeItemMap.get(eNode);
        if(nodeMap.get(eNodeItem) == null) {
          String[] eNodeItemArr = eNodeItem.split("#");
          eNodeItem = eNodeItemArr[0] + "#1#" + eNodeItemArr[eNodeItemArr.length-1];
        }
        if(nodeMap.get(eNodeItem) == null) {
          eNodeItem = replaceMap.get("\""+eNodeItem+"\"");
          eNodeItem = eNodeItem.substring(1,eNodeItem.length()-1);
        }
        String eNodeString = Integer.toString(nodeMap.get(eNodeItem));

        String directNodeItem = nodeItemMap.get(directNode);
        if(nodeMap.get(directNodeItem) == null) {
          String[] directNodeItemArr = directNodeItem.split("#");
          directNodeItem = directNodeItemArr[0] + "#1#" + directNodeItemArr[directNodeItemArr.length-1];
        }
        if(nodeMap.get(directNodeItem) == null) {
          directNodeItem = replaceMap.get("\""+directNodeItem+"\"");
          directNodeItem = directNodeItem.substring(1,directNodeItem.length()-1);
        }
        String directNodeString = Integer.toString(nodeMap.get(directNodeItem));
        MarkovChainInformation directChain = new MarkovChainInformation(eNodeString, directNodeString, "1.0", false, false, false);
        miList.add(directChain);
        transitionlistMap.put(entry.getKey(),miList);

        String[] eNodeItemArr = eNodeItem.split("#");
        eNodeItem = eNodeItemArr[0] + "#" + eNodeItemArr[eNodeItemArr.length-1];
        if(modelCountingTimeMap.get(eNodeItem) != null) {
          extraModelCountingTime += modelCountingTimeMap.get(eNodeItem);
        }
      }
    }

    //code to remove unnecessary nodes after domination analysis
    visitedToCheck = new boolean[numberofNodes+1];
    dfsToCheck("0");
    for(int idx=0; idx < visitedToCheck.length-1; idx++) {
      if(!visitedToCheck[idx]) {
        transitionlistMap.remove(Integer.toString(idx));
        numberofNodesReduced++;
      }
    }


    String markovChainOutput = "digraph {\n";
    String prismOutput = "dtmc\n\n" + "module " + modelName + "\n\n";
    if(loopbound > 0 && backEdgeExists)
      prismOutput += "\t" + "s : [0.." + (numberofNodes * loopbound) +"] init 0;\n\n";
    else
      prismOutput += "\t" + "s : [0.." + (numberofNodes) +"] init 0;\n\n";


    for (Map.Entry<String, List<MarkovChainInformation>> entry : transitionlistMap.entrySet()) {
      List<MarkovChainInformation> mChainList = entry.getValue();

      String fromNode = entry.getKey();

      if(mChainList.size() >= 1) {
        MarkovChainInformation trueChain = mChainList.get(0);
        String trueNode = trueChain.getToNode();
        String trueNodeProb = trueChain.getProb();
        boolean depNode = trueChain.isDepBranchNode();
        boolean assertNode = trueChain.isAssertNode();
        String falseNode = "", falseNodeProb = "";
        if(mChainList.size() == 2) {
          MarkovChainInformation falseChain = mChainList.get(1);
          falseNode = falseChain.getToNode();
          falseNodeProb = falseChain.getProb();

          if(depNode) {
            markovChainOutput += "\t" + fromNode + " -> " + trueNode + "[label= " + "\"" + trueNodeProb + "\"];\n";
            markovChainOutput += "\t" + fromNode + " -> "  + falseNode + "[label= " + "\"" + falseNodeProb + "\"];\n";
            prismOutput += "\t" + "[] s = " + fromNode + " -> " + trueNodeProb + " : " + "(s' = " + trueNode + ") + " + falseNodeProb + " : " + "(s' = " + falseNode + ");\n";

          } else {
            if(assertNode) {
              markovChainOutput += "\t" + fromNode + " -> " + trueNode + "[label= " + "\"" + trueNodeProb + "\"];\n";
              markovChainOutput += "\t" + fromNode + " -> " + falseNode + "[label= " + "\"" + falseNodeProb + "\"];\n";
              prismOutput += "\t" + "[] s = " + fromNode + " -> " + trueNodeProb + " : " + "(s' = " + trueNode + ") + " + falseNodeProb + " : " + "(s' = " + falseNode + ");\n";
            } else {

              String fID = idMap.get(Integer.parseInt(falseNode));
              String[] splittedfID = fID.split("#");
              ISSABasicBlock fNode = itemNodeMap.get(splittedfID[0]+"#"+splittedfID[splittedfID.length-1]);

              String tID = idMap.get(Integer.parseInt(trueNode));
              String[] splittedtID = tID.split("#");
              ISSABasicBlock tNode = itemNodeMap.get(splittedtID[0]+"#"+splittedtID[splittedtID.length-1]);

              if(domSet.contains(tNode) && !domSet.contains(fNode)) {
                markovChainOutput += "\t" + fromNode + " -> " + trueNode + "[label= " + "\"" + "1.0" + "\"];\n";
                markovChainOutput += "\t" + fromNode + " -> " + falseNode + "[label= " + "\"" + "0.0" + "\"];\n";
                prismOutput += "\t" + "[] s = " + fromNode + " -> " + "1.0" + " : " + "(s' = " + trueNode + ") + " + "0.0" + " : " + "(s' = " + falseNode + ");\n";
              }

              else if(domSet.contains(fNode) && !domSet.contains(tNode)) {
                markovChainOutput += "\t" + fromNode + " -> " + trueNode + "[label= " + "\"" + "0.0" + "\"];\n";
                markovChainOutput += "\t" + fromNode + " -> " + falseNode + "[label= " + "\"" + "1.0" + "\"];\n";
                prismOutput += "\t" + "[] s = " + fromNode + " -> " + "0.0" + " : " + "(s' = " + trueNode + ") + " + "1.0" + " : " + "(s' = " + falseNode + ");\n";
              }

              else if(!domSet.contains(fNode) && !domSet.contains(tNode)) {
                //Need to change this later
                markovChainOutput += "\t" + fromNode + " -> " + trueNode + "[label= " + "\"" + "1.0" + "\"];\n";
                markovChainOutput += "\t" + fromNode + " -> " + falseNode + "[label= " + "\"" + "1.0" + "\"];\n";
                prismOutput += "\t" + "[] s = " + fromNode + " -> " + "1.0" + " : " + "(s' = " + trueNode + ");\n";
                prismOutput += "\t" + "[] s = " + fromNode + " -> " + "1.0" + " : " + "(s' = " + falseNode + ");\n";
              }
              else {
                //this case is not possible, false and true node both being in dominator set
              }
            }
          }
        } else{
          markovChainOutput += "\t" + fromNode + " -> " + trueNode + "[label= " + "\"" + "1.0" + "\"];\n";
          prismOutput += "\t" + "[] s = " + fromNode + " -> " + "1.0" + " : " + "(s' = " + trueNode + ");\n";
        }
      }
    }

    markovChainOutput += "}";
    prismOutput += "\nendmodule";


    System.out.println(markovChainOutput);

    System.out.println(prismOutput);

    return new Pair<String,String>(markovChainOutput, prismOutput);
  }

  private void dfsToCheck(String i) {
    visitedToCheck[Integer.parseInt(i)] = true;

    List<MarkovChainInformation> miList = transitionlistMap.get(i);

    if(miList != null) {
      for(MarkovChainInformation mi : miList) {
        if(!visitedToCheck[Integer.parseInt(mi.getToNode())])
          dfsToCheck(mi.getToNode());
      }
    }
  }

  private boolean isCyclicUtil(int s, int i, boolean[] visited, boolean[] recStack) {

    // Mark the current node as visited and
    // part of recursion stack
    if (recStack[i])
      return true;

    if (visited[i])
      return false;

    visited[i] = true;

    recStack[i] = true;
    List<String> children = edgeMap.get(Integer.toString(i));

    if (children != null) {
      for (String c : children) {
        if (isCyclicUtil(i, Integer.parseInt(c), visited, recStack) && i != Integer.parseInt(c)) {
          System.out.println("backedge: " + i + " --> " + c);
          backEdgeExists = true;


          //dominator analysis for loop structure
          //if assertion node is dominated by loop condition but not dominated by backedge from node
          //then assertion node do not need to consider the other branch from loop condition
          //so, probability to assertion condition is 1.0 and the other branch is 0.0
          ISSABasicBlock backedgeFromNode = itemNodeMap.get(idMap.get(i));
          ISSABasicBlock backedgeToNode = itemNodeMap.get(idMap.get(Integer.parseInt(c)));


          String id = idMap.get(Integer.parseInt(assertionNode));
          String[] splittedID = id.split("#");
          Procedure proc = itemProcMap.get(splittedID[0]);
          ISSABasicBlock node = itemNodeMap.get(splittedID[0]+"#"+splittedID[splittedID.length-1]);
          node = proc.getImmediateDominator(node); //to ge the branch node from where it reaches to the assert node
          Set<ISSABasicBlock> domSet = proc.getDominatorSet(node);
          Set<ISSABasicBlock> postDomSet = proc.getPostDominatorSet(node);
          if(domSet.contains(backedgeToNode) && !postDomSet.contains(backedgeFromNode)) {
            String asserDomNode = Integer.toString(nodeMap.get(nodeItemMap.get(node)));
            List<MarkovChainInformation> miList = null;
            if(!backedgeToNode.getLastInstruction().toString().contains("conditional")) {
              miList = transitionlistMap.get(Integer.toString(Integer.parseInt(c)+1)); //loop backedgetonode is not conditional branch
            } else {
              miList = transitionlistMap.get(c);
            }
            boolean flagToUpDateProb = false;
            for(MarkovChainInformation mi : miList) {
              ISSABasicBlock miToNode = itemNodeMap.get(idMap.get(Integer.parseInt(mi.getToNode())));
              if(domSet.contains(miToNode)) {
                flagToUpDateProb = true;
                backEdgeExists = false;
                break;
              }
            }
            List<String> miListTORemove = new ArrayList<>();
            if(flagToUpDateProb) {
              //MarkovChainInformation miToRemove = null;
              for (MarkovChainInformation mi : miList) {
//                if (mi.getToNode().equals(asserDomNode)) {
//                  //mi.updateProb("1.0");
//                } else {
                //miToRemove = mi;
                ISSABasicBlock newBackedgeToNode = null;
                if(!backedgeToNode.getLastInstruction().toString().contains("conditional")) {
                  newBackedgeToNode = itemNodeMap.get(idMap.get(Integer.parseInt(c)+1));
                } else {
                  newBackedgeToNode = itemNodeMap.get(idMap.get(Integer.parseInt(c)));
                }
                ISSABasicBlock newNode = backedgeFromNode;
                ISSABasicBlock prevNewNode = newNode;
                while(!newNode.equals(newBackedgeToNode)) {
                  newNode = proc.getImmediateDominator(newNode);
                  String prevNewNodeID = Integer.toString(nodeMap.get(nodeItemMap.get(prevNewNode)));
                  if(transitionlistMap.get(prevNewNodeID) != null) {
                    for (MarkovChainInformation m : transitionlistMap.get(prevNewNodeID)) {
                      if(!m.getToNode().equals(c)) {
                        ISSABasicBlock nodeToCheck = itemNodeMap.get(idMap.get(Integer.parseInt(m.getToNode())));
                        Set<ISSABasicBlock> befDomSet = proc.getDominatorSet(backedgeFromNode);
                        if(!befDomSet.contains(nodeToCheck)) {
                          flagToUpDateProb = false;
                          backEdgeExists = true;
                          break;
                        }
                      }
                    }
                  }
                  if(!flagToUpDateProb)
                    break;

                  miListTORemove.add(prevNewNodeID);
                  prevNewNode = newNode;
                }
                //}
              }
            }
            if(flagToUpDateProb) {
              MarkovChainInformation miToRemove = null;
              for (MarkovChainInformation mi : miList) {
                ISSABasicBlock miToNode = itemNodeMap.get(idMap.get(Integer.parseInt(mi.getToNode())));
                if(domSet.contains(miToNode)) {
                  mi.updateProb("1.0");
                  String m = idMap.get(Integer.parseInt(mi.getFromNode()));
                  if(modelCountingTimeMap.get(m) != null) {
                    extraModelCountingTime += modelCountingTimeMap.get(m);
                  }
                } else {
                  miToRemove = mi;
                }
              }
              miList.remove(miToRemove);
              for(String m : miListTORemove) {
                transitionlistMap.remove(m);
                m = idMap.get(Integer.parseInt(m));
                if(modelCountingTimeMap.get(m) != null) {
                  extraModelCountingTime += modelCountingTimeMap.get(m);
                }
              }
            }
          }


          //unroll loops
          if(loopbound > 1 && backEdgeExists) {
            int newNode = Integer.parseInt(c) + numberofNodes;
            //edgeMap.get(Integer.toString(i)).remove(c);
            //edgeMap.get(Integer.toString(i)).add(Integer.toString(newNode));
            //transitionMap.put(new Pair<>(Integer.toString(i), c), transitionMap.get(new Pair<>(Integer.toString(i), c)).updateToNode(Integer.toString(newNode)));
            //transitionMap.reprob = "1.0"move(new Pair<>(Integer.toString(i), c));

            MarkovChainInformation chain = null;
            if (loopbound > 0) {
              chain = transitionMap.get(new Pair<>(Integer.toString(i), c)).updateToNode(Integer.toString(newNode));
            } else {
              chain = transitionMap.get(new Pair<>(Integer.toString(i), c)).updateToNode(endNode);
            }
            transitionMap.put(new Pair<>(Integer.toString(i), c), chain);
            List<MarkovChainInformation> list = new ArrayList<>();
            list.add(chain);
            transitionlistMap.put(Integer.toString(i), list);

            transitionMap.remove(new Pair<>(Integer.toString(i), c));
            List<MarkovChainInformation> oldList = transitionlistMap.get(Integer.toString(i));
            for (MarkovChainInformation m : oldList) {
              if (m.getToNode().equals(c))
                oldList.remove(m);
            }

            int be_from = i;
            List<Integer> beFromList = new ArrayList<>();
            beFromList.add(be_from);
            int be = Integer.parseInt(c);
            List<Integer> beList = new ArrayList<>();
            beList.add(be);
            for (int x = 0; x < loopbound - 1; x++) {
              boolean[] visitedUnroll = new boolean[numberofNodes];
              dfsToAddUnrolledNodes(Integer.parseInt(c), visitedUnroll, be_from, be, x, beList, beFromList, c);
              be_from = be_from + numberofNodes;
              be = be + numberofNodes;
              beFromList.add(be_from);
              beList.add(be);
            }
          }
        }
      }
    }


    recStack[i] = false;

    return false;
  }

  private void dfsToAddUnrolledNodes(int i, boolean[] visited, int be_from, int be, int x, List<Integer> beList, List<Integer> beFromList, String beTONode) {

    if (visited[i])
      return;

    visited[i] = true;

    int bounded_node = (numberofNodes * (x+1));

    String from = Integer.toString(i+bounded_node);
    List<String> children = edgeMap.get(Integer.toString(i));
    List<String> fromChildren = new ArrayList<>();

    if (children != null) {
      for (String c : children) {
        if (beList.contains(Integer.parseInt(c)))
          continue;
        if (Integer.parseInt(c) > bounded_node)
          continue;
        //if (Integer.parseInt(c) != numberofNodes) {
        String to = "";
        if(Integer.parseInt(c) == /*numberofNodes-1*/Integer.parseInt(endNode))
          to = c;
        else
          to = Integer.toString(Integer.parseInt(c) + bounded_node);
        fromChildren.add(to);
        //edgeMap.put(from, fromChildren);

        MarkovChainInformation mChain = transitionMap.get(new Pair<>(Integer.toString(i),c));
        if(mChain == null)
          continue;
        MarkovChainInformation chain = new MarkovChainInformation(from, to, mChain.getProb(), mChain.isDepBranchNode(), mChain.isAssertNode(), mChain.isExceptionNode());
        transitionMap.put(new Pair<>(from, to), chain);

        List<MarkovChainInformation> list = new ArrayList<>();
        if(transitionlistMap.get(from) != null) {
          list = transitionlistMap.get(from);
        }
        list.add(chain);
        transitionlistMap.put(from, list);

        if(beFromList.contains(Integer.parseInt(c)) && !visited[Integer.parseInt(c)]) {
          if(x == loopbound-2) {
            ISSABasicBlock backedgeToNode = itemNodeMap.get(idMap.get(Integer.parseInt(beTONode)));
            if(!backedgeToNode.getLastInstruction().toString().contains("conditional")) {
              beTONode = Integer.toString(Integer.parseInt(beTONode) + 1);
            }
            List<MarkovChainInformation> listLoop = new ArrayList<>();
            if (transitionlistMap.get(beTONode) != null) {
              listLoop = transitionlistMap.get(beTONode);
            }
            for(MarkovChainInformation m : listLoop) {
              ISSABasicBlock mnode = itemNodeMap.get(idMap.get(Integer.parseInt(m.getToNode())));

              int origNode = Integer.parseInt(to) % numberofNodes;
              ISSABasicBlock node = itemNodeMap.get(idMap.get(origNode));
              String id = idMap.get(origNode);
              String[] splittedID = id.split("#");
              Procedure proc = itemProcMap.get(splittedID[0]);
              Set<ISSABasicBlock> domSet = proc.getDominatorSet(node);

              if(!domSet.contains(mnode)) {
                if(transitionMap.get(new Pair<>(to, Integer.toString(Integer.parseInt(m.getToNode()) + (numberofNodes*(x+1))))) == null) {
                  MarkovChainInformation chain2 = new MarkovChainInformation(to, Integer.toString(Integer.parseInt(m.getToNode()) + (numberofNodes*(x+1))), "1.0", false, false, false);
                  transitionMap.put(new Pair<>(to, Integer.toString(Integer.parseInt(m.getToNode()) + (numberofNodes*(x+1))) + (numberofNodes*(x+1))), chain2);

                  List<MarkovChainInformation> list2 = new ArrayList<>();
                  if (transitionlistMap.get(to) != null) {
                    list2 = transitionlistMap.get(to);
                  }
                  list2.add(chain2);
                  transitionlistMap.put(to, list2);
                }
                break;
              }
            }
          } else {
            if(transitionMap.get(new Pair<>(to, Integer.toString(be + (numberofNodes*2)))) == null) {
              MarkovChainInformation chain2 = new MarkovChainInformation(to, Integer.toString(be + (numberofNodes * 2)), "1.0", false, false, false);
              transitionMap.put(new Pair<>(to, Integer.toString(be + (numberofNodes * 2))), chain2);

              List<MarkovChainInformation> list2 = new ArrayList<>();
              if (transitionlistMap.get(to) != null) {
                list2 = transitionlistMap.get(to);
              }
              list2.add(chain2);
              transitionlistMap.put(to, list2);
            }
          }
        }

        dfsToAddUnrolledNodes(Integer.parseInt(c), visited, be_from, be, x, beList, beFromList,beTONode);
        //}
      }
    }
  }


  private List<String> translateToSMTLib(String ins_to_translate, Procedure proc) {
    System.out.println(MainLogic.selectedVariables);

    String[] consArr = ins_to_translate.split(" and ");

    String cons = "";
    String dom_cons = "";
    String consVar = "";

    Set<String> varSet = new HashSet<>();

    SymbolTable symTab = proc.getIR().getSymbolTable();

    for(String con: consArr) {
      String[] ins_comps = con.split(" ");

      String sign = "";

      List<String> vars = new ArrayList<>();
      for (int in = 0; in < ins_comps.length; in++) {
        String incomp = ins_comps[in].replace("(","").replace(")","");
        if (incomp.contains("v"))
          vars.add(incomp);
        else
          sign = incomp;
      }



      for(String var : vars) {
        //if (MainFrame.selectedVariables.contains(var)) {
        varSet.add(var);
        //}
      }


      String end = "";
      if(sign.equals("!=")) {
        if (!con.contains("not")) {
          cons += "(assert (not (= ";
          end = ")";
        }
        else
          cons += "(assert (= ";
      } else if(sign.equals("==")) {
        if (!con.contains("not"))
          cons += "(assert (= ";
        else {
          cons += "(assert (not (= ";
          end = ")";
        }
      } else {
        if (!con.contains("not"))
          cons += "(assert (" + sign + " ";
        else {
          cons += "(assert (not (" + sign + " ";
          end = ")";
        }
      }


      int var1 = Integer.parseInt(vars.get(0).substring(1));
      int var2 = Integer.parseInt(vars.get(1).substring(1));

      if (symTab.isNumberConstant(var1)) {
        int v1 = symTab.getIntValue(var1);
        if(v1 < 0) {
//        if(v1 >= 10000)
//          cons += vars.get(0) + " ";
//        else
          cons += vars.get(1) + " ";
//        else
          if (v1 == Integer.MIN_VALUE) {
            v1 = 32767;
            cons += "(- " + v1 + ") ";
          } else
            cons += "(- " + Math.abs(v1) + ") ";
        } else {
          cons += v1 + " ";
        }
      } else {
        cons += vars.get(0) + " ";
      }
      if (symTab.isNumberConstant(var2)) {
        int v2 = symTab.getIntValue(var2);
        if(v2 < 0) {
//        if(v2 >= 10000)
//          cons += vars.get(1) + " ";
//        else
          if (v2 == Integer.MIN_VALUE) {
            v2 = 32767;
            cons += "(- " + v2 + ") ";
          } else
            cons += "(- " + Math.abs(v2) + ") ";
        } else {
          cons += v2 + " ";
        }
      } else {
        cons += vars.get(1);
      }

      cons += end;
      cons += "))\n";
    }

    for(String var : varSet) {
      //if (MainFrame.selectedVariables.contains(var)) {
      int varInt = Integer.parseInt(var.substring(1));
      //String v = symTab.
      //String className = symTab.getValue(varInt).getClass().getName();
      consVar += "(declare-fun " + var + "() Int)\n";
      //}
    }
    dom_cons = consVar;

    cons = consVar + cons;
    cons += "(check-sat)";
    dom_cons += "(check-sat)";

    List<String> consList = new ArrayList<>();
    consList.add(dom_cons);
    consList.add(cons);

    return consList;
  }

  private String recursiveInlining(List<String> invokedProcedures, List<String> jsonItems, int i, String jsonItemID, String jsonItem, String completeJSON, Integer oldProcRecursiveBound) {
    //JOptionPane.showMessageDialog(MainFrame.this, "At Start: \n" + completeJSON);

    String[] jsonItemComponents = jsonItem.split("Invoke")[1].split(",");

    String inlineProcClass = jsonItemComponents[1].substring(0, jsonItemComponents[1].length() - 1);
    String inlineProcName = jsonItemComponents[2];
    String inlineProcArgs = jsonItemComponents[3].substring(0, jsonItemComponents[3].length() - 3);
    String inlineProcSignature = inlineProcClass + "." + inlineProcName + inlineProcArgs;
    Procedure inlineProc = Program.getProcedure(inlineProcSignature);

    System.out.println(inlineProcSignature);
    //System.out.println("jsonItemID : " + jsonItemID);

    if (jsonMap.get(inlineProcSignature) == null) {
      return completeJSON;
    }

    if (recursiveBoundMap.get(inlineProcSignature) == null) {
      return completeJSON;
    }

    //System.out.println("jsonItemID : " + jsonItemID);
    if (recursiveBoundMap.get(jsonItemID) == null) {
      recursiveBoundMap.put(jsonItemID, 1);
    }

    Integer oldRecursiveBound = recursiveBoundMap.get(inlineProcSignature);
    //System.out.println("oldRecursiveBound : " + oldProcRecursiveBound);
    Integer oldRecursiveBoundForItem = recursiveBoundMap.get(jsonItemID);
    //System.out.println("oldRecursiveBoundForItem : " + oldRecursiveBoundForItem);

    if (oldRecursiveBoundForItem > this.recursiveBound) {
      return completeJSON;
    }

    recursiveBoundMap.put(inlineProcSignature, oldRecursiveBound + 1);
    recursiveBoundMap.put(jsonItemID, oldRecursiveBoundForItem + 1);

    List<String> inlineProcJSON = jsonMap.get(inlineProcSignature);

    if (inlineProcJSON != null) {
      String entryOutgoingOld = jsonItem.split("\"outgoing\" : ")[1];
      String entryOutgoingNew = inlineProcJSON.get(0).split(" ")[4];
      //System.out.println("entryOutgoingOld: " + entryOutgoingOld);
      //System.out.println("entryOutgoingNew: " + entryOutgoingNew);
      String oldEntryOutgoingpart = entryOutgoingNew.split("#")[0] + "#";
      String newEntryOutgoingpart = oldEntryOutgoingpart + oldRecursiveBound + "#";
      entryOutgoingNew = entryOutgoingNew.replace(oldEntryOutgoingpart, newEntryOutgoingpart);
      completeJSON = completeJSON.replace(entryOutgoingOld, "{ \"" + entryOutgoingNew + "\" : \"Invoke\"} }");

      int k = 0;
      //JOptionPane.showMessageDialog(MainFrame.this, "Before loop: \n" + completeJSON);
      for(String item : inlineProcJSON) {

        String itemID = item.split(" ")[4];
        String itemNodeNumber = itemID.split("#")[1];

        if (inlineProc.dependentNodes.contains(itemNodeNumber) && item.contains("\"secret_dependent_branch\" : \"branch\"")) {
          item = item.replace("\"secret_dependent_branch\" : \"branch\"", "\"secret_dependent_branch\" : \"true\"");
        }

        //changing the id to set each id unique
        String oldID = itemID.split("#")[0] + "#";
        String newID = oldID + oldRecursiveBound + "#";
        //System.out.println("item = " + item + "    oldID = " + oldID + "    newID = " + newID);
        //System.out.println(item);
        item = item.replace(oldID, newID);
        //itemID = item.split(" ")[4];

        if (k == inlineProcJSON.size()-1) {

          String exitOutgoingNew = "";

          String checkString = jsonItems.get(i - 1).split("\"outgoing\" : \\{ ")[1].split(" }")[0];

          if (checkString.contains(",")) { //there are two outgoing special case for function calling and exception occurring later
            exitOutgoingNew = jsonItems.get(i - 1).split("\"outgoing\" : \\{ ")[1].split(" }")[0];
            if(item.contains("\"outgoing\" : { }"))
              item = item.replace("\"outgoing\" : { }", "\"outgoing\" : { " + exitOutgoingNew + " }");
            else
              item = item.replace("\"outgoing\" : {}", "\"outgoing\" : { " + exitOutgoingNew + " }");
          }

          else {

            exitOutgoingNew = jsonItems.get(i).split(" ")[4];

            //exitOutgoingNew = jsonItems.get(i).split("\"outgoing\" : \\{ \"")[1].split("\"")[0];
            //System.out.println("exitOutgoingNew: " + exitOutgoingNew);
            String oldExitOutgoingpart = exitOutgoingNew.split("#")[0] + "#";
            //System.out.println("oldExitOutgoingpart: " + oldExitOutgoingpart);
            if (oldProcRecursiveBound > 0) {
              String newExitOutgoingpart = oldExitOutgoingpart + oldProcRecursiveBound + "#";
              //System.out.println("newExitOutgoingpart: " + newExitOutgoingpart);
              exitOutgoingNew = exitOutgoingNew.replace(oldExitOutgoingpart, newExitOutgoingpart);
              //System.out.println("exitOutgoingNew: " + exitOutgoingNew);
            }
            if(item.contains("\"outgoing\" : { }"))
              item = item.replace("\"outgoing\" : { }", "\"outgoing\" : { \"" + exitOutgoingNew + "\" : \"Implicit\" }");
            else
              item = item.replace("\"outgoing\" : {}", "\"outgoing\" : { \"" + exitOutgoingNew + "\" : \"Implicit\" }");
          }
        }

        completeJSON += item + ",\n";
        //JOptionPane.showMessageDialog(MainFrame.this, "Adding item: \n" + completeJSON);
        k++;

        //System.out.println(item);

        if (item.contains("Invoke") && !item.contains("<init>") && !item.split("\"outgoing\" : \\{ ")[1].split(" }")[0].contains(",")) {
          //System.out.println(item);
          //JOptionPane.showMessageDialog(MainFrame.this, "At condition: \n" + completeJSON);
                /*String itemIDToPass = "";
                if (jsonItemID.contains(" "))
                    itemIDToPass = jsonItemID.split(" ")[1] + " " + itemID;
                else
                    itemIDToPass = jsonItemID + " " + itemID;*/

          //System.out.println(itemIDToPass);
          completeJSON = recursiveInlining(invokedProcedures, inlineProcJSON, k, itemID, item, completeJSON, oldRecursiveBound);
        }
      }
    }
    return completeJSON;
  }

  enum CGFocus {
    Null,
    Loop,
    New,
    Recursion,
    Slice,
    Zoom
  }

  private ConfigInfo        configInfo = null;

  private CG                                        callGraph = null;
  private Map<Procedure, CFG>                       controlFlowGraphMap = new HashMap<>();
  private Map<Procedure, PDG>                       procedureDependenceGraphMap = new HashMap<>();
  private Map<Integer, List<NestedLoop>>            nestedLoopListMap = new TreeMap<>(Collections.reverseOrder());
  private Map<Integer, List<NewObject>>             newObjectListMap = new TreeMap<>(Collections.reverseOrder());
  private Map<Integer, Recursion>                   recursionMap = new TreeMap<>();
  private Map<String, List<String>>                 jsonMap = new HashMap<>();
  private Map<String, Integer>                      recursiveBoundMap = new HashMap<>();
  private List<Procedure>                           otherList = null; //Added by Madeline Sgro 07/14/2017

  private CGFocus                                   focus = CGFocus.Null;
  private BaseGraph<Procedure>                      currentCG = null;
  private CFG                                       currentCFG = null;
  private PDG                                       currentPDG = null;
  private ISSABasicBlock                            currentNode = null;
  private NestedLoop                                currentNestedLoop = null;


  private int                                       recursiveBound;
  private int                                       numberofNodes;
  private int                                       numberofNodesMerged;
  private int                                       numberofNodesReduced;
  private boolean                                   extractSubGraphFlag = false;
  private boolean                                   loopUnrollingFlag = false;
  private int                                       loopbound = 4;
  public static long                                dependencyAnalysisTime = 0;
  private static long                               extraModelCountingTime = 0;
  private boolean backEdgeExists = false;
  public static String cfgConsTime = "";

  private static int counter = 0;
  private static Map<String, Integer> nodeMap = new HashMap<>();
  private static Map<Integer, String> idMap = new HashMap<>();
  private static Map<String, List<String>> edgeMap =  new HashMap<>();
  private static List<String> branchNodes = new ArrayList<>();
  private static String endNode = "";
  private static String assertionNode = "";
  private static Map<Pair<String, String>, MarkovChainInformation> transitionMap = new HashMap<>();
  private static Map<String, List<MarkovChainInformation>> transitionlistMap = new HashMap<>();
  private static Map<ISSABasicBlock, Integer> nodeLineMap = new HashMap<>();
  private static Map<String, String> interProcDomMap = new HashMap<>();
  private static Map<String, String> interProcPostDomMap = new HashMap<>();
  private static Map<String, String> procCallMap = new HashMap<>();
  private static Map<String, String> procCallReverseMap = new HashMap<>();

  private Map<String, Map<Double, Set<Procedure>>>  jBondMap = new TreeMap<>();
  static public Set<Statement> allStmtSet = new HashSet<>();
  static public Set<String> selectedVariables = new HashSet<>();
  static public Map<Integer, String> allSourceLines = new HashMap<>();
  static public Map<String, Procedure> itemProcMap = new HashMap<>();
  static public Map<String, ISSABasicBlock> itemNodeMap = new HashMap<>();
  static public Map<ISSABasicBlock, String> nodeItemMap = new HashMap<>();
  static private Map<String,Long> modelCountingTimeMap = new HashMap<>();
  static private Map<String,List<String>> lineItemsMap = new HashMap<>();
  static private boolean[] visitedToCheck;
  static public String rootDir;

  static public ModelCounter modelCounter = new ModelCounter(4, "abc.string");

  final public CG getCG() {
    return this.callGraph;
  }

  final public CFG getCFG(Procedure proc) {
    return this.controlFlowGraphMap.get(proc);
  }


  // End of variables declaration//GEN-END:variables
  public File fileToSave = null;


  private class MarkovChainInformation {

    String fromNode, toNode, prob;
    boolean depBranchNode, assertNode, exceptionNode;

    public MarkovChainInformation(String fromNode, String toNode, String prob, boolean depBranchNode, boolean assertNode, boolean exceptionNode) {
      this.fromNode = fromNode;
      this.toNode = toNode;
      this.prob = prob;
      this.depBranchNode = depBranchNode;
      this.assertNode = assertNode;
      this.exceptionNode = exceptionNode;
    }

    public String getFromNode() {
      return fromNode;
    }

    public String getToNode() {
      return toNode;
    }

    public String getProb() {
      return prob;
    }

    public boolean isDepBranchNode() {
      return depBranchNode;
    }

    public boolean isAssertNode() {
      return assertNode;
    }

    public boolean isExceptionNode() { return exceptionNode; }

    public MarkovChainInformation updateToNode(String toNode) {
      this.toNode = toNode;
      return this;
    }

    public MarkovChainInformation updateProb(String prob) {
      this.prob = prob;
      return this;
    }
  }
}
