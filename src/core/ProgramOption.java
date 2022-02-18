package core;

/**
 *
 * @author zzk
 */
public class ProgramOption {
  private static CGType   cgType = CGType.ZeroOneCFA;
  private static boolean  averroes = true;
  private static boolean  exception = false;
  private static boolean  infiniteLoop = true;
  
  public static void setCGType(CGType type) {
    cgType = type;
  }
  
  public static CGType getCGType() {
    return cgType;
  }
  
  public static void setAverroesFlag(boolean ave) {
    averroes = ave;
  }
  
  public static boolean getAverroesFlag() {
    return averroes;
  }
  
  public static void setExceptionFlag(boolean except) {
    exception = except;
  }
  
  public static boolean getExceptionFlag() {
    return exception;
  }
  
  public static void setInfiniteLoopFlag(boolean inf) {
    infiniteLoop = inf;
  }
  
  public static boolean getInfiniteLoopFlag() {
    return infiniteLoop;
  }
}
