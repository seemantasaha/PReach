package core;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author zzk
 */
public class Exclusion {
  static public Set<String> extractExclusionSet(IClassHierarchy cha, String apiPath) throws Exception {
    Set<String> clsNameSet = new HashSet<>();
    
    // read the API file to collect all the classes that have been used in this program
    File api = new File(apiPath);
    BufferedReader reader = new BufferedReader(new FileReader(api));
    String line;
    while ((line = reader.readLine()) != null) {
      // replace all whitespaces with ""
      line = line.replaceAll("\\s+","");
      if (line.lastIndexOf('(') == -1)
        continue;
      String mthName = line.substring(0, line.indexOf('('));
      String clsName = line.indexOf(':') == -1 ? mthName : line.substring(0, mthName.lastIndexOf('.'));
      
      // USED LIBRARY classes are added into *clsNameSet*
      clsNameSet.add(clsName);
    }
    reader.close();
    
    // traverse through the whole class hierarchy to 
    // (1) record which classes are abstract classes/interfaces
    // (2) collect all the APPLICATION classes
    Set<String> absClsNameSet = new HashSet<>();
    for (IClass cls : cha) {
      String clsName = cls.getName().toString().substring(1).replace('/', '.');
      if (!cls.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
        if ((cls.isAbstract() || cls.isInterface()) && clsNameSet.contains(clsName))
          absClsNameSet.add(clsName);
        continue;
      }
      
      // APPLICATION classes are added into *clsNameSet*
      clsNameSet.add(clsName);
    }
        
    for (IClass cls : cha) {
      Collection<IClass> interClsSet = cls.getAllImplementedInterfaces();
      for (IClass interCls : interClsSet) {
        String interClsName = interCls.getName().toString().substring(1).replace('/', '.');
        if (absClsNameSet.contains(interClsName)) {
          String clsName = cls.getName().toString().substring(1).replace('/', '.');
          clsNameSet.add(clsName);
          break;
        }
      }
    }
    
    // initially what I did is to track class hierachy for each class that is in clsNameSet, but it's not good!
    // if the class is an abstract or interface, doing this will rule out its implementation!
    // i.e. String clsName = cls.getName().toString().substring(1).replace('/', '.');
    //      if (!clsNameSet.contains(clsName))
    //        continue;
    Set<String> superClsNameSet = new HashSet<>();
    for (IClass cls : cha) {
      Set<String> clsHierNameSet = new HashSet<>();
      boolean needFlag = false;
      IClass superCls = cls;
      while (!superCls.getName().toString().equals("Ljava/lang/Object")) {
        String superClsName = superCls.getName().toString().substring(1).replace('/', '.');
        clsHierNameSet.add(superClsName);
        if (clsNameSet.contains(superClsName))
          needFlag = true;
        superCls = superCls.getSuperclass();
      }
      if (needFlag)
        superClsNameSet.addAll(clsHierNameSet);
    }
    clsNameSet.addAll(superClsNameSet);
    
    Set<String> exclusionSet = new HashSet<>();
    for (IClass cls : cha) {
      String clsName = cls.getName().toString().substring(1).replace('/', '.');
      if (clsNameSet.contains(clsName))
        continue;
      String exclusion = clsName.replace(".", "\\/");
      exclusionSet.add(exclusion);
    }
    
    return exclusionSet;
  }
}
