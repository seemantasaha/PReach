package core;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * @author zzk
 */
public class LibrarySummary {
  class MethodSummary {
    String    methodName;
    boolean   modifySelf;
    boolean[] modifyParams;
    boolean[] storeParams;
  }
  
  private static Map<String, MethodSummary> methodSummaryMap = new HashMap<>();
  private static Set<String>                unknownMethodSet = new HashSet<>();
  
  public static void loadLibrarySummary() throws IOException {
    List<Path> pathList = Files.walk(Paths.get("database")).filter(Files::isRegularFile).collect(Collectors.toList());
    for (Path path : pathList) {
      Gson gson = new Gson();
      JsonReader reader = new JsonReader(new FileReader(path.toString()));
      MethodSummary[] mthSumArray = gson.fromJson(reader, MethodSummary[].class);
      
      for (MethodSummary mthSum : mthSumArray) {
        String mthName = mthSum.methodName;
        methodSummaryMap.put(mthName, mthSum);
      }
    }
  }
  
  public static boolean checkExistence(String mthName) {
    if (!methodSummaryMap.containsKey(mthName)) {
      unknownMethodSet.add(mthName);
      return false;
    }
    return true;
  }
  
  public static Set<String> getUnknownMethodSet() {
    return unknownMethodSet;
  }
  
  public static boolean isSelfModified(String mthName) {
    MethodSummary mthSum = methodSummaryMap.get(mthName);
    // true is a safe value? or should we throw an exception?
    if (mthSum == null)
      return true;
    return mthSum.modifySelf;
  }
  
  public static boolean isParameterModified(String mthName, int paramNum) {
    MethodSummary mthSum = methodSummaryMap.get(mthName);
    // true is a safe value? or should we throw an exception?
    if (mthSum == null)
      return true;
    if (mthSum.modifyParams == null)
      return true;
    if (mthSum.modifyParams.length <= paramNum)
      return true;
    return mthSum.modifyParams[paramNum];
  }
  
  public static boolean isParameterStored(String mthName, int paramNum) {
    MethodSummary mthSum = methodSummaryMap.get(mthName);
    // true is a safe value? or should we throw an exception?
    if (mthSum == null)
      return true;
    if (mthSum.storeParams == null)
      return true;
    if (mthSum.storeParams.length <= paramNum)
      return true;
    return mthSum.storeParams[paramNum];
  }
}
