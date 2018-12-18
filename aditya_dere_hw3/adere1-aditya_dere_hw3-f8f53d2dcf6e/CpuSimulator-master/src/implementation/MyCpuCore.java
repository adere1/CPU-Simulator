/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import baseclasses.PipelineRegister;
import baseclasses.PipelineStageBase;
import baseclasses.CpuCore;
import tools.InstructionSequence;
import utilitytypes.IPipeReg;
import utilitytypes.IPipeStage;
import static utilitytypes.IProperties.*;
import utilitytypes.Logger;
import voidtypes.VoidRegister;

/**
 * This is an example of a class that builds a specific CPU simulator out of
 * pipeline stages and pipeline registers.
 * 
 * @author 
 */
public class MyCpuCore extends CpuCore {
    static final String[] producer_props = {RESULT_VALUE};
    public static int set_counter_fdiv =0;
        
    public void initProperties() {
        properties = new GlobalData();
    }
    
    public void loadProgram(InstructionSequence program) {
        getGlobals().loadProgram(program);
    }
    
    public void runProgram() {
        properties.setProperty("running", true);
      while (properties.getPropertyBoolean("running")) {
            Logger.out.println("## Cycle number: " + cycle_number);
            advanceClock();
        }
        /*for(int i =0;i<390;i++){
        	advanceClock();
        }*/
    }

    @Override
    public void createPipelineRegisters() {
        createPipeReg("FetchToDecode");
        createPipeReg("DecodeToExecute");
        createPipeReg("DecodeToMemory");
        createPipeReg("DecodeToIntMul");
        createPipeReg("ExecuteToWriteback");
        //createPipeReg("MemoryToWriteback");
        
        //-------------- Aditya ----------------------------
        createPipeReg("DecodeToMSCMPSUB");
        createPipeReg("DecodeToMSMUL");
        createPipeReg("DecodeToIntDiv");
        createPipeReg("IntDivToWriteback");
        createPipeReg("DecodeToFloatDiv");
        createPipeReg("FloatDivToWriteback");
        //createPipeReg("MemoryToLSQ");
        
        
        //createPipeReg("DecodeToMSADD");
        //-------------- Aditya ----------------------------
    }

    @Override
    public void createPipelineStages() {
        addPipeStage(new AllMyStages.Fetch(this));
        addPipeStage(new AllMyStages.Decode(this));
        addPipeStage(new AllMyStages.Execute(this));
        addPipeStage(new IntDiv(this));
        addPipeStage(new FloatDiv(this));
       // addPipeStage(new MemoryUnit(this));
        //addPipeStage(new LSQUnit(this));
        //addPipeStage(new MemUnit(this));        
        //addPipeStage(new AllMyStages.Memory(this));
        addPipeStage(new AllMyStages.Writeback(this));
    }

    @Override
    public void createChildModules() {
        // IntMul is an example multistage functional unit.  Use this as a
        // basis for FMul, IMul, and FAddSub functional units.
        addChildUnit(new MultiStageFunctionalUnit(this, "IntMul"));
      //-------------- Aditya ----------------------------
        addChildUnit(new MultiStageFunctionalCmpSubUnit(this, "FloatAddSub"));
        //addChildUnit(new MultiStageFunctionalAdd(this, "MSADD"));
        addChildUnit(new MultiStageFunctionalMul(this, "FloatMul"));
        addChildUnit(new MemUnit(this, "MemUnit"));
        
      //-------------- Aditya ----------------------------  
    }

    @Override
    public void createConnections() {
        // Connect pipeline elements by name.  Notice that 
        // Decode has multiple outputs, able to send to Memory, Execute,
        // or any other compute stages or functional units.
        // Writeback also has multiple inputs, able to receive from 
        // any of the compute units.
        // NOTE: Memory no longer connects to Execute.  It is now a fully 
        // independent functional unit, parallel to Execute.
        
        // Connect two stages through a pipelin register
        connect("Fetch", "FetchToDecode", "Decode");
        
        // Decode has multiple output registers, connecting to different
        // execute units.  
        // "IntMul" is an example multistage functional unit.  Those that
        // follow the convention of having a single input stage and single
        // output register can be connected simply my naming the functional
        // unit.  The input to IntMul is really called "IntMul.in".
        connect("Decode", "DecodeToExecute", "Execute");
        connect("Decode", "DecodeToMemory", "MemUnit");
        connect("Decode", "DecodeToIntMul", "IntMul");
      //-------------- Aditya ----------------------------
        //connect("MemUnit","MemoryToLSQ","LSQ");
        connect("Decode", "DecodeToMSCMPSUB", "FloatAddSub");
        connect("Decode", "DecodeToMSMUL", "FloatMul");
        connect("Decode", "DecodeToIntDiv", "IntDiv");
        connect("Decode", "DecodeToFloatDiv", "FloatDiv");
        
        //connect("Decode", "DecodeToMSADD", "MSADD");
      //-------------- Aditya ----------------------------  
        
        // Writeback has multiple input connections from different execute
        // units.  The output from IntMul is really called "IntMul.Delay.out",
        // which was aliased to "IntMul.out" so that it would be automatically
        // identified as an output from IntMul.
        connect("Execute","ExecuteToWriteback", "Writeback");
        //connect("Memory", "MemoryToWriteback", "Writeback");
        connect("IntMul", "Writeback");
      //-------------- Aditya ----------------------------
        connect("FloatAddSub", "Writeback");
       // connect("LSQ" ,"MemoryToWriteback", "Writeback");
        //connect("DcacheToOut.out" ,"MemoryToWriteback", "Writeback");
        
        //connect("MemUnit" ,"MemoryToWriteback", "Writeback");
        connect("MemUnit" , "Writeback");
        //connect("MSADD", "Writeback");
        connect("FloatMul", "Writeback");
        connect("IntDiv", "IntDivToWriteback" ,"Writeback");
        connect("FloatDiv","FloatDivToWriteback", "Writeback");
      //-------------- Aditya ----------------------------  
    }

    @Override
    public void specifyForwardingSources() {
        addForwardingSource("ExecuteToWriteback");
       // addForwardingSource("MemoryToWriteback");
        
         // IntMul.specifyForwardingSources is where this forwarding source is added
        // addForwardingSource("IntMul.out");
      //-------------- Aditya ----------------------------
        addForwardingSource("FloatAddSub.out");
        //addForwardingSource("MSMEM.out");
        //addForwardingSource("MemoryToWriteback");
        addForwardingSource("FloatMul.out");
        addForwardingSource("IntDivToWriteback");
        addForwardingSource("FloatDivToWriteback");
        //addForwardingSource("DcacheToOut.out");
        addForwardingSource("MemUnit.out");
        
        //addForwardingSource("MSADD.out");
      //-------------- Aditya ----------------------------  
    }

    @Override
    public void specifyForwardingTargets() {
        // Not really used for anything yet
    }

    @Override
    public IPipeStage getFirstStage() {
        // CpuCore will sort stages into an optimal ordering.  This provides
        // the starting point.
        return getPipeStage("Fetch");
    }
    
    public MyCpuCore() {
        super(null, "core");
        initModule();
        printHierarchy();
        Logger.out.println("");
    }
}
