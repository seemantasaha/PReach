package core;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.SubtypesEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXCFABuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.strings.Atom;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 *
 * @author zzk
 */
public class ConfigMaker {
  private static String getEntryMethodName(String entry) {
    int periodPos = entry.lastIndexOf('.');
    int parenPos = entry.lastIndexOf('(');
    return entry.substring(periodPos + 1, parenPos);
  }
  
  private static String getEntryClassName(String entry) {
    int periodPos = entry.lastIndexOf('.');
    return entry.substring(0, periodPos);
  }
  
  private static String getEntryParameterList(String entry) {
    int parenPos = entry.lastIndexOf('(');
    return entry.substring(parenPos);
  }
  
  public static Iterable<Entrypoint> makeEntrypoints(AnalysisScope scope, IClassHierarchy cha, Set<String> otherEntrySet) {
    ClassLoaderReference appClsLoaderRef = scope.getApplicationLoader();
    Atom mainMth = Atom.findOrCreateAsciiAtom("main");
    HashSet<Entrypoint> result = HashSetFactory.make();
    for (IClass cls : cha) {
      if (cls.getClassLoader().getReference().equals(appClsLoaderRef)) {
        MethodReference mainRef = MethodReference.findOrCreate(cls.getReference(), mainMth, Descriptor.findOrCreateUTF8("([Ljava/lang/String;)V"));
        IMethod mth = cls.getMethod(mainRef.getSelector());
        if (mth != null)
          result.add(new DefaultEntrypoint(mth, cha));
      }
    }
    
    for (String entry : otherEntrySet) {
      Atom entryMth = Atom.findOrCreateAsciiAtom(getEntryMethodName(entry));
      System.out.println(entryMth);
      TypeReference typeRef = TypeReference.findOrCreate(appClsLoaderRef, TypeName.string2TypeName(getEntryClassName(entry)));
      System.out.println(typeRef);
      System.out.println(typeRef.getName());
      IClass cls = cha.lookupClass(typeRef);
      System.out.println(cls);
      if (cls.isAbstract()) {
        // TODO: we need to check whether the subclass is still abstract? and find the nearest non-abstract desendant
        for (IClass subCls : cha.getImmediateSubclasses(cls)) {
          MethodReference entryRef = MethodReference.findOrCreate(subCls.getReference(), entryMth, Descriptor.findOrCreateUTF8(getEntryParameterList(entry)));
          result.add(new SubtypesEntrypoint(entryRef, cha));
        }
      } else {
        MethodReference entryRef = MethodReference.findOrCreate(typeRef, entryMth, Descriptor.findOrCreateUTF8(getEntryParameterList(entry)));
        result.add(new DefaultEntrypoint(entryRef, cha));
      }
    }
    
    if (ProgramOption.getAverroesFlag()) {
      TypeReference typeRef = TypeReference.findOrCreate(appClsLoaderRef, TypeName.string2TypeName("Laverroes/Library"));
      MethodReference clinitRef = MethodReference.findOrCreate(typeRef, MethodReference.clinitName, MethodReference.clinitSelector.getDescriptor());
      result.add(new DefaultEntrypoint(clinitRef, cha));
    }
        
    return new Iterable<Entrypoint>() {
      @Override
      public Iterator<Entrypoint> iterator() {
        return result.iterator();
      }
    };
  }
  
  public static Iterable<Entrypoint> makeAllPublicEntryPoints(AnalysisScope scope, IClassHierarchy cha) {
    ClassLoaderReference appClsLoaderRef = scope.getApplicationLoader();
    HashSet<Entrypoint> result = HashSetFactory.make();
    for (IClass cls : cha) {
      if (!cls.getClassLoader().getReference().equals(appClsLoaderRef))
        continue;
      
      for(IMethod mth : cls.getDeclaredMethods()) {
        if(!mth.isPublic() || mth.isAbstract() || mth.isInit() || mth.isBridge() || mth.isSynthetic())
          continue;
        result.add(new DefaultEntrypoint(mth, cha));
      }
    }
    
    return new Iterable<Entrypoint>() {
      @Override
      public Iterator<Entrypoint> iterator() {
        return result.iterator();
      }
    };
  }
  
  public static SSAPropagationCallGraphBuilder makeAverroesZeroCFABuilder(AnalysisOptions options, AnalysisCache cache, IClassHierarchy cha, AnalysisScope scope) {
    Util.addDefaultSelectors(options, cha);
    
    return ZeroXCFABuilder.make(cha, options, cache, null, null, 
      ZeroXInstanceKeys.ALLOCATIONS | 
      ZeroXInstanceKeys.SMUSH_MANY | 
      ZeroXInstanceKeys.SMUSH_PRIMITIVE_HOLDERS | 
      ZeroXInstanceKeys.SMUSH_STRINGS | 
      ZeroXInstanceKeys.SMUSH_THROWABLES);
	}
  
  public static SSAPropagationCallGraphBuilder makeAverroesZeroOneCFABuilder(AnalysisOptions options, AnalysisCache cache, IClassHierarchy cha, AnalysisScope scope) {
    Util.addDefaultSelectors(options, cha);
    
    return ZeroXCFABuilder.make(cha, options, cache, null, null, 
      ZeroXInstanceKeys.ALLOCATIONS | 
      ZeroXInstanceKeys.CONSTANT_SPECIFIC);
	}
}
