/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cmd;

import com.ibm.wala.ssa.ISSABasicBlock;
import core.Procedure;

/**
 *
 * @author seemanta
 */
public class ImbalanceAnalysisItem {
    private String itemID;
    private ISSABasicBlock node;
    private Procedure proc;
    private int numberOfInstructions;
    private String itemInstructions;
    private Integer nodeCost;
    private String[] incomingItems;
    private String[] outgoingItems;
    private boolean exceptionNodeFlag;
    
    public ImbalanceAnalysisItem(String id, ISSABasicBlock node, Procedure proc, int numberOfInstructions, String instructions, Integer nodeCost, String[] incomingItems, String[] outgoingItems, boolean exceptionNodeFlag) {
        this.itemID = id;
        this.node = node;
        this.proc = proc;
        this.numberOfInstructions = numberOfInstructions;
        this.itemInstructions = instructions;
        this.nodeCost = nodeCost;
        this.incomingItems = incomingItems;
        this.outgoingItems = outgoingItems;
        this.exceptionNodeFlag = exceptionNodeFlag;
    }
    
    public String getID() {
        return this.itemID;
    }

    public ISSABasicBlock getBlockNode() {
        return this.node;
    }

    public Procedure getProcedure() { return this.proc; }
    
    public int getNumberOfInstructions() {
        return this.numberOfInstructions;
    }
    
    public String getInstruction() {
        return this.itemInstructions;
    }
    
    public Integer getNodeCost() {
        return this.nodeCost;
    }
    
    public String[] getIncomingItems() {
        return this.incomingItems;
    }
    
    public String[] getOutgoingItems() {
        return this.outgoingItems;
    }

    public boolean getExceptionNodeFlag() { return this.exceptionNodeFlag; }
}
