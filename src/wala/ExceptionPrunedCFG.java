/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 * 
 * The source code is modified for ISSTAC project
 * Modifiers: zzk
 * 
 *******************************************************************************/
package wala;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.cfg.IBasicBlock;
import com.ibm.wala.ipa.cfg.EdgeFilter;
import core.ProgramOption;

/**
 * A view of a CFG that ignores exceptional edges
 */
public class ExceptionPrunedCFG {

  private static class ExceptionEdgePruner<I, T extends IBasicBlock<I>> implements EdgeFilter<T>{
    private final ControlFlowGraph<I, T> cfg;

    ExceptionEdgePruner(ControlFlowGraph<I, T> cfg) {
      this.cfg = cfg;
    }

    @Override
    public boolean hasNormalEdge(T src, T dst) {
      return cfg.getNormalSuccessors(src).contains(dst);
    }

    @Override
    public boolean hasExceptionalEdge(T src, T dst) {
      // zzk modified here: cutting exceptional edges depends on program options
      if (ProgramOption.getExceptionFlag()) {
        if (dst.isCatchBlock())
          return cfg.getExceptionalSuccessors(src).contains(dst);
        else
          return false;
      }
      return false;
    }
  };

  public static <I, T extends IBasicBlock<I>> PrunedCFG<I, T> make(ControlFlowGraph<I, T> cfg) {
    return PrunedCFG.make(cfg, new ExceptionEdgePruner<I, T>(cfg));
  }
}

