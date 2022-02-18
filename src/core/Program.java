package core;

import com.ibm.wala.analysis.pointers.BasicHeapGraph;
import com.ibm.wala.analysis.pointers.HeapGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphStats;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.PhiStatement;
import com.ibm.wala.ipa.slicer.PiStatement;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import wala.FileOfClasses;

@SuppressWarnings({"rawtypes", "unchecked", "serial"})

/**
 *
 * @author zzk
 */
public class Program {
  static private AnalysisScope          scope = null;
  static private IClassHierarchy        cha = null;
  static private CallGraph              cg = null;
  static private HeapGraph              hg = null;
  static private PointerAnalysis        pts = null;
  
  static private Map<IR, Procedure>     procedureMap = new HashMap<>();
  static private LinkedList<Procedure>  procedurePostOrderList = new LinkedList<>();
  static private Set<Procedure>         entryProcedureSet = new TreeSet<>(new ProcedureComparator());
  
  static private Set<Recursion>         recursionSet = new HashSet<>();
  
  static public void makeProgram(ArrayList<String> appPaths, ArrayList<String> libPaths, String apiPath, String entryFilePath) throws Exception {
    LibrarySummary.loadLibrarySummary();
    scope = AnalysisScope.createJavaAnalysisScope();
    
    // load the app to analyze
    for (String appPath : appPaths) {
      File app = new File(appPath);
      if (appPath.endsWith(".jar"))
        scope.addToScope(ClassLoaderReference.Application, new JarFile(app));
      else if (appPath.endsWith(".class"))
        scope.addClassFileToScope(ClassLoaderReference.Application, app);
    }
    
    // load the libs for the app
    for (String libPath : libPaths) {
      JarFile lib = new JarFile(libPath);
      scope.addToScope(ClassLoaderReference.Primordial, lib);
    }
    
    // class hierarchy
    cha = ClassHierarchy.make(scope);
    
    if (!apiPath.isEmpty() && !ProgramOption.getAverroesFlag()) {
      Set<String> exclusionSet = Exclusion.extractExclusionSet(cha, apiPath);
      
      FileOfClasses exculusionFile = new FileOfClasses(exclusionSet);
      scope.setExclusions(exculusionFile);
      cha = ClassHierarchy.make(scope);
    }
    
    System.out.println("constructing CG");
    
    constructCG(entryFilePath);
    
    Set<Procedure> procFlagSet = new HashSet<>();
    for (Procedure entryProc : entryProcedureSet)
      generateProcedurePostOrderList(entryProc, procFlagSet);
    
    //collectRecursion();
  }
  
  /*
  Modified by Madeline Sgro 07/14/2017
  Included a call to OtherAnalysis.deriveModuloSet() so that way the 
  program also finds the set of procedures that contain modulos
  */
  static public void analyzeProgram() {
    // the order to call these methods are important! keep it!
    //NestedLoopAnalysis.deriveInterproceduralNestedLoopSet();
    //NewObjectAnalysis.deriveNewObjectNestedLoopList();
    
    //OtherAnalysis.deriveModuloSet(); //Added by Madeline Sgro
    //OtherAnalysis.deriveStringCompSet(); //Added by Thomas Marosz
    
    ProgramDependenceGraph.makeProgramDependenceGraph();
    //PathLengthAnalysis.summarizeProgram();
    
    int numProc = 0;
    HashSet<String> classes = new HashSet<>();
    for (Procedure proc : procedureMap.values()) {
      numProc++;
      classes.add(proc.getClassName());
      //for (Loop loop : proc.getLoopSet())
      //  loop.checkLoopExitCondition();
    }
    System.out.println("#Classes : " + classes.size() + "   #Methods : " + numProc);
  }
  
  static private void constructCG(String entryFilePath) throws Exception {    
    Set<String> otherEntrySet = new HashSet<>();
    if (entryFilePath != null && !entryFilePath.isEmpty()) {
      File entryFile = new File(entryFilePath);
      BufferedReader reader = new BufferedReader(new FileReader(entryFile));
      String line;
      while ((line = reader.readLine()) != null)
        otherEntrySet.add(line);
      reader.close();
    }
    
    Iterable<Entrypoint> entryPts = entryFilePath != null ?
        ConfigMaker.makeEntrypoints(scope, cha, otherEntrySet) :
        ConfigMaker.makeAllPublicEntryPoints(scope, cha);
    AnalysisOptions opts = new AnalysisOptions(scope, entryPts);
    if (ProgramOption.getAverroesFlag()) {
      opts.setReflectionOptions(ReflectionOptions.NONE);
      opts.setHandleZeroLengthArray(false);
    } else {
      // for APIsUsed, why if we do not include this, WALA gives us NullPointerException?
      opts.setReflectionOptions(ReflectionOptions.NO_METHOD_INVOKE);
    }
    
    AnalysisCache cache = new AnalysisCache();
    CallGraphBuilder cgBuilder;
    switch (ProgramOption.getCGType()) {
      case RTA:
        cgBuilder = Util.makeRTABuilder(opts, cache, cha, scope);
        break;
      case ZeroCFA:
        if (ProgramOption.getAverroesFlag())
          cgBuilder = ConfigMaker.makeAverroesZeroCFABuilder(opts, cache, cha, scope);
        else
          cgBuilder = Util.makeZeroCFABuilder(opts, cache, cha, scope);
        break;
      default:
        if (ProgramOption.getAverroesFlag())
          cgBuilder = ConfigMaker.makeAverroesZeroOneCFABuilder(opts, cache, cha, scope);
        else
          cgBuilder = Util.makeZeroOneCFABuilder(opts, cache, cha, scope);
    }
    long startTime = System.currentTimeMillis();
    cg = cgBuilder.makeCallGraph(opts, null);
    long endTime = System.currentTimeMillis();
    long elapsedTime = endTime - startTime;
    System.out.println("Constructing call graph: " + elapsedTime/1000 + "s");
    pts = cgBuilder.getPointerAnalysis();
    startTime = System.currentTimeMillis();
    hg = new BasicHeapGraph(pts, cg);
    endTime = System.currentTimeMillis();
    elapsedTime = endTime - startTime;
    System.out.println("Constructing heap graph: " + elapsedTime/1000 + "s");
    System.out.print(CallGraphStats.getCGStats(cg));
    
    Collection<CGNode> entryCGNodes = cg.getEntrypointNodes();
    for (CGNode cgNode : entryCGNodes) {
      Procedure entryProc = extractProcedure(cgNode);
      if (entryProc != null)
        entryProcedureSet.add(entryProc);
    }
    
    int nP = 0;
    HashSet<String> cls = new HashSet<>();
    for (Procedure p : procedureMap.values()) {
        nP++;
        cls.add(p.getClassName());
    }
    System.out.println("Root --> #Classes : " + cls.size() + "  #Methods : " + nP);
  }
  
  static private Procedure extractProcedure(CGNode cgNode) {
    IMethod mth = cgNode.getMethod();
    if (!mth.getDeclaringClass().getClassLoader().getReference().equals(ClassLoaderReference.Application))
      return null;
    
    if (mth.getDeclaringClass().getName().toString().equals("Laverroes/Library"))
      return null;
    
    IR ir = cgNode.getIR();
    Procedure proc = procedureMap.get(ir);
    if (proc != null)
      return proc;
    
    proc = new Procedure(ir);
    procedureMap.put(ir, proc);
    
    Iterator<CGNode> cgNodeIter = cg.getSuccNodes(cgNode);
    while (cgNodeIter.hasNext()) {
      CGNode succCGNode = cgNodeIter.next();
      Procedure callee = extractProcedure(succCGNode);
      if (callee == null)
        continue;
      
      Iterator<CallSiteReference> callSiteRefIter = cg.getPossibleSites(cgNode, succCGNode);
      while (callSiteRefIter.hasNext()) {
        CallSiteReference callSiteRef = callSiteRefIter.next();
        proc.addCallee(callee, callSiteRef);
      }
    }
    
    return proc;
  }
  
  static private void generateProcedurePostOrderList(Procedure proc, Set<Procedure> procFlagSet) {
    if (procFlagSet.contains(proc))
      return;
    procFlagSet.add(proc);
    
    Set<Procedure> calleeSet = proc.getCalleeSet();
    for (Procedure callee : calleeSet)
      generateProcedurePostOrderList(callee, procFlagSet);
    
    procedurePostOrderList.addLast(proc);
  }
  
  static private void generateStronglyConnectedComponent(Procedure proc, Set<Procedure> scc, Set<Procedure> flagSet) {
    if (flagSet.contains(proc))
      return;
    flagSet.add(proc);
    
    scc.add(proc);
    Set<Procedure> callerSet = proc.getCallerSet();
    for (Procedure caller : callerSet)
      generateStronglyConnectedComponent(caller, scc, flagSet);
  }
  
  // since the call graph in general is not reducible, we compute SCCs as recursions
  static private void collectRecursion() {
    LinkedList<Procedure> rpo = new LinkedList<>();
    for (Procedure proc : procedurePostOrderList)
      rpo.addFirst(proc);
    
    Set<Procedure> flagSet = new HashSet<>();
    for (Procedure proc : rpo) {
      Set<Procedure> scc = new HashSet<>();
      generateStronglyConnectedComponent(proc, scc, flagSet);
      // if SCC only has one method, check if this method calls itself
      if (scc.isEmpty())
        continue;
      else if (scc.size() == 1) {
        Procedure sccMember = scc.iterator().next();
        if (!sccMember.getCallerSet().contains(sccMember))
          continue;
      }
      recursionSet.add(new Recursion(scc));
    }
  }
  
  //---------------------public-------------------------------------------------
  static public Set<String> checkAnalysisScope() {
    Set<String> misses = new TreeSet<>();
    for (CGNode cgNode : cg) {
      ClassLoaderReference clsLoaderRef = cgNode.getMethod().getDeclaringClass().getClassLoader().getReference();
      if (!clsLoaderRef.equals(ClassLoaderReference.Application))
        continue;
      Iterator<CallSiteReference> callSiteRefIter = cgNode.iterateCallSites();
      while (callSiteRefIter.hasNext()) {
        CallSiteReference callSiteRef = callSiteRefIter.next();
        if (cg.getNumberOfTargets(cgNode, callSiteRef) == 0)
          misses.add(callSiteRef.getDeclaredTarget().getSignature());
      }
    }
    return misses;
  }
    
  static public IClassHierarchy getClassHierarchy() {
    return cha;
  }
  
  static public CallGraph getCallGraph() {
    return cg;
  }
  
  static public HeapGraph getHeapGraph() {
    return hg;
  }
  
  static public IClass getClass(String clsName) {
    for (IClass cls : cha)
      if (cls.getName().toString().equals(clsName))
        return cls;
    return cha.getRootClass();
  }
  
  static public IClass getClass(TypeReference typeRef) {
    return cha.lookupClass(typeRef);
  }
  
  static public boolean isTypesSuperSubRelated(TypeReference typeRef1, TypeReference typeRef2) {
    // if we cannot know, we say it's related for safety
    if (typeRef1 == null || typeRef2 == null)
      return true;
    TypeReference supRef = cha.getLeastCommonSuperclass(typeRef1, typeRef2);
    if (supRef == typeRef1 || supRef == typeRef2)
      return true;
    else
      return false;
  }
  
  static public boolean isSubclassOf(String subClsName, String supClsName) {
    IClass subCls = getClass(subClsName);
    IClass supCls = getClass(supClsName);
    return cha.isSubclassOf(subCls, supCls);
  }
  
  static public boolean isImplementationOf(String clsName, String infName) {
    IClass cls = getClass(clsName);
    IClass inf = getClass(infName);
    return cha.implementsInterface(cls, inf);
  }
  
  static public boolean isApplicationMethodCalled(MethodReference mthRef) {
    Set<CGNode> cgNodeSet = cg.getNodes(mthRef);
    Iterator<CGNode> cgNodeIter = cgNodeSet.iterator();
    if (cgNodeIter.hasNext()) {
      CGNode cgNode = cgNodeIter.next();
      ClassLoaderReference clsLoaderRef = cgNode.getMethod().getDeclaringClass().getClassLoader().getReference();
      if (!clsLoaderRef.equals(ClassLoaderReference.Application))
        return false;
    }
    return true;
  }
  
  static public Procedure getProcedure(CGNode cgNode) {
    IR ir = cgNode.getIR();
    return procedureMap.get(ir);
  }
  
  static public Procedure getProcedure(String procSig) {
    for (Procedure proc : procedureMap.values()) {
      String procSignToCompare = proc.getFullSignature();
      //System.out.println("proc sign to compare: " + procSignToCompare);
      //System.out.println("original proc sign: " + procSig);
      if (procSignToCompare.equals(procSig))
        return proc;
    }
    return null;
  }
  
  static public Set<Procedure> getProcedureSet() {
    return new HashSet<>(procedureMap.values());
  }
  
  static public Set<Procedure> getProcedureSet(String clsName) {
    Set<Procedure> procSet = new HashSet<>();
    for (Procedure proc : procedureMap.values())
      if (clsName.equals(proc.getClassName()))
        procSet.add(proc);
    return procSet;
  }
  
  static public Set<Procedure> getProcedureSet(ISSABasicBlock node) {
    Set<Procedure> procSet = new HashSet<>();
    MethodReference mthRef = node.getMethod().getReference();
    Set<CGNode> cgNodeSet = cg.getNodes(mthRef);
    for (CGNode cgNode : cgNodeSet) {
      Procedure proc = getProcedure(cgNode);
      procSet.add(proc);
    }
    return procSet;
  }
  
  static public Set<Procedure> getAncestorProcedureSet(Procedure proc) {
    Set<Procedure> ancestorSet = new HashSet<>();
    LinkedList<Procedure> queue = new LinkedList<>(proc.getCallerSet());
    while (!queue.isEmpty()) {
      Procedure ancestor = queue.pollFirst();
      ancestorSet.add(ancestor);
      Set<Procedure> ancestorCallerSet = ancestor.getCallerSet();
      for (Procedure ancestorCaller : ancestorCallerSet)
        if (!ancestorSet.contains(ancestorCaller))
          queue.addLast(ancestorCaller);
    }
    return ancestorSet;
  }
  
  static public Set<Procedure> getDescendantProcedureSet(Procedure proc) {
    Set<Procedure> descendantSet = new HashSet<>();
    LinkedList<Procedure> queue = new LinkedList<>(proc.getCalleeSet());
    while (!queue.isEmpty()) {
      Procedure descendant = queue.pollFirst();
      descendantSet.add(descendant);
      Set<Procedure> descendantCalleeSet = descendant.getCalleeSet();
      for (Procedure descendantCallee : descendantCalleeSet)
        if (!descendantSet.contains(descendantCallee))
          queue.addLast(descendantCallee);
    }
    return descendantSet;
  }
  
  static public LinkedList<Procedure> getProcedurePostOrderList() {
    return procedurePostOrderList;
  }
  
  static public Set<Procedure> getEntryProcedureSet() {
    return entryProcedureSet;
  }
  
  static public Set<Recursion> getRecursionSet() {
    return recursionSet;
  }
}
