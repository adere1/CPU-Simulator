/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import tools.MultiStageDelayUnit;
import tools.MyALU;
import utilitytypes.EnumOpcode;
import baseclasses.InstructionBase;
import baseclasses.ModuleBase;
import baseclasses.PipelineRegister;
import baseclasses.PipelineStageBase;
import voidtypes.VoidLatch;
import baseclasses.CpuCore;
import baseclasses.Latch;
import cpusimulator.CpuSimulator;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static utilitytypes.EnumOpcode.*;
import utilitytypes.ICpuCore;
import utilitytypes.IFunctionalUnit;
import utilitytypes.IGlobals;
import utilitytypes.IPipeReg;
import static utilitytypes.IProperties.*;
import utilitytypes.IRegFile;
import utilitytypes.Logger;
import utilitytypes.Operand;
import voidtypes.VoidLabelTarget;

/**
 * The AllMyStages class merely collects together all of the pipeline stage 
 * classes into one place.  You are free to split them out into top-level
 * classes.
 * 
 * Each inner class here implements the logic for a pipeline stage.
 * 
 * It is recommended that the compute methods be idempotent.  This means
 * that if compute is called multiple times in a clock cycle, it should
 * compute the same output for the same input.
 * 
 * How might we make updating the program counter idempotent?
 * 
 * @author
 */
public class AllMyStages {
    /*** Fetch Stage ***/
    static class Fetch extends PipelineStageBase {
        public Fetch(ICpuCore core) {
            super(core, "Fetch");
        }
        
        // Does this state have an instruction it wants to send to the next
        // stage?  Note that this is computed only for display and debugging
        // purposes.
        boolean has_work;
                
        /** 
         * For Fetch, this method only has diagnostic value.  However, 
         * stageHasWorkToDo is very important for other stages.
         * 
         * @return Status of Fetch, indicating that it has fetched an 
         *         instruction that needs to be sent to Decode.
         */
        @Override
        public boolean stageHasWorkToDo() {
            return has_work;
        }
        
        @Override
        public String getStatus() {
            IGlobals globals = (GlobalData)getCore().getGlobals();
            if (globals.getPropertyInteger("branch_state_fetch") == GlobalData.BRANCH_STATE_WAITING) {
                addStatusWord("ResolveWait");
            }
            return super.getStatus();
        }

        @Override
        public void compute(Latch input, Latch output) {
            IGlobals globals = (GlobalData)getCore().getGlobals();
            
            // Get the PC and fetch the instruction
            int pc_no_branch    = globals.getPropertyInteger(PROGRAM_COUNTER);
            int pc_taken_branch = globals.getPropertyInteger("program_counter_takenbranch");
            int branch_state_decode = globals.getPropertyInteger("branch_state_decode");
            int branch_state_fetch = globals.getPropertyInteger("branch_state_fetch");
            int pc = (branch_state_decode == GlobalData.BRANCH_STATE_TAKEN) ?
                    pc_taken_branch : pc_no_branch;
            InstructionBase ins = globals.getInstructionAt(pc);
            
            // Initialize this status flag to assume a stall or bubble condition
            // by default.
            has_work = false;
            
            // If the instruction is NULL (like we ran off the end of the
            // program), just return.  However, for diagnostic purposes,
            // we make sure something meaningful appears when 
            // CpuSimulator.printStagesEveryCycle is set to true.
            if (ins.isNull()) {
                // Fetch is working on no instruction at no address
                setActivity("");
            } else {            
                // Since there is no input pipeline register, we have to inform
                // the diagnostic helper code explicitly what instruction Fetch
                // is working on.
                has_work = true;
                output.setInstruction(ins);
                setActivity(ins.toString());
            }
            
            // If the output cannot accept work, then 
            if (!output.canAcceptWork()) return;
            
//            Logger.out.println("No stall");
            globals.setClockedProperty(PROGRAM_COUNTER, pc + 1);
            
            boolean branch_wait = false;
            if (branch_state_fetch == GlobalData.BRANCH_STATE_WAITING) {
                branch_wait = true;
            }
            if (branch_state_decode != GlobalData.BRANCH_STATE_NULL) {
//                Logger.out.println("branch state resolved");
                globals.setClockedProperty("branch_state_fetch", GlobalData.BRANCH_STATE_NULL);
                globals.setClockedProperty("branch_state_decode", GlobalData.BRANCH_STATE_NULL);
                branch_wait = false;
            }
            if (!branch_wait) {
                if (ins.getOpcode().isBranch()) {
                    globals.setClockedProperty("branch_state_fetch", GlobalData.BRANCH_STATE_WAITING);
                }
            }
        }
    }

    
    /*** Decode Stage ***/
    static class Decode extends PipelineStageBase {
        public Decode(ICpuCore core) {
            super(core, "Decode");
        }
        
        
        // When a branch is taken, we have to squash the next instruction
        // sent in by Fetch, because it is the fall-through that we don't
        // want to execute.  This flag is set only for status reporting purposes.
        boolean squashing_instruction = false;
        boolean shutting_down = false;

        @Override
        public String getStatus() {
            IGlobals globals = (GlobalData)getCore().getGlobals();
            String s = super.getStatus();
            if (globals.getPropertyBoolean("decode_squash")) {
                s = "Squashing";
            }
            return s;
        }
        

//        private static final String[] fwd_regs = {"ExecuteToWriteback", 
//            "MemoryToWriteback"};
        
        @Override
        public void compute(Latch input, Latch output) {
            if (shutting_down) {
                addStatusWord("Shutting down");
                setActivity("");
                return;
            }
            
            input = input.duplicate();
            InstructionBase ins = input.getInstruction();
            IGlobals globals = (GlobalData)getCore().getGlobals();
            IRegFile regfile = globals.getRegisterFile();
            // Default to no squashing.
            squashing_instruction = false;
            
            setActivity(ins.toString());

            //IGlobals globals = (GlobalData)getCore().getGlobals();
            if (globals.getPropertyBoolean("decode_squash")) {
                // Drop the fall-through instruction.
                globals.setClockedProperty("decode_squash", false);
                squashing_instruction = false;
                //setActivity("----: NULL");
//                globals.setClockedProperty("branch_state_decode", GlobalData.BRANCH_STATE_NULL);
                
                // Since we don't pass an instruction to the next stage,
                // must explicitly call input.consume in the case that
                // the next stage is busy.
                input.consume();
                return;
            }
            
            if (ins.isNull()) return;
            
            EnumOpcode opcode = ins.getOpcode();
            Operand oper0 = ins.getOper0();
            //IRegFile regfile = globals.getRegisterFile();
            
            // This code is to prevent having more than one of the same regster
            // as a destiation register in the pipeline at the same time.
                    /* if (opcode.needsWriteback()) {
                int oper0reg = oper0.getRegisterNumber();
                
                if (regfile.isInvalid(oper0reg)) {
                    //Logger.out.println("Stall because dest R" + oper0reg + " is invalid");
                    setResourceWait("Dest:"+oper0.getRegisterName());
                    return;
                }
            }*/
           
            
            if(ins.getSrc1().isRegister()){
            	ins.getSrc1().rename(GlobalData.register_alias_table[ins.getSrc1().getRegisterNumber()]);
            	//System.out.println("REG "+globals.getRegisterFile().getFlags(ins.getSrc1().getRegisterNumber()));
            	//regfile.markRenamed(GlobalData.register_alias_table[ins.getSrc1().getRegisterNumber()], true);
            }
            if(ins.getSrc2().isRegister()){
            	ins.getSrc2().rename(GlobalData.register_alias_table[ins.getSrc2().getRegisterNumber()]);
            	//regfile.markRenamed(GlobalData.register_alias_table[ins.getSrc2().getRegisterNumber()], true);
            }
            
            if(opcode.oper0IsSource() && ins.getOper0().isRegister()){
            	ins.getOper0().rename(GlobalData.register_alias_table[ins.getOper0().getRegisterNumber()]);
            	//regfile.markRenamed(GlobalData.register_alias_table[ins.getOper0().getRegisterNumber()], true);
            }
            
            int regnum = -1;
            for(int i=0;i<256;i++){
            	if(!globals.getRegisterFile().isUsed(i)){
            		regnum = i;	
            		break;
            	}
            }
            
            // See what operands can be fetched from the register file
            registerFileLookup(input);
            
            // See what operands can be fetched by forwarding
            forwardingSearch(input);
            
            Operand src1  = ins.getSrc1();
            Operand src2  = ins.getSrc2();
            
            
            boolean take_branch = false;
            int value0 = 0;
            int value1 = 0;
            
            
            // Find out whether or not DecodeToExecute can accept work.
            // We do this here for CALL, which can't be allowed to do anything
            // unless it can pass along its work to Writeback, and we pass
            // the call return address through Execute.
           // int d2e_output_num = lookupOutput("DecodeToIssueQueue");
            //Latch d2e_output = this.newOutput(d2e_output_num);
            
            
            switch (opcode) {
                case BRA:
                    if (!oper0.hasValue()) {
                        // If we do not already have a value for the branch
                        // condition register, must stall.
//                        Logger.out.println("Stall BRA wants oper0 R" + oper0.getRegisterNumber());
                        this.setResourceWait(oper0.getRegisterName());
                        // Nothing else to do.  Bail out.
                        return;
                    }
                    value0 = oper0.getValue();
                    
                    // The CMP instruction just sets its destination to
                    // (src1-src2).  The result of that is in oper0 for the
                    // BRA instruction.  See comment in MyALU.java.
                    switch (ins.getComparison()) {
                        case EQ:
                            take_branch = (value0 == 0);
                            break;
                        case NE:
                            take_branch = (value0 != 0);
                            break;
                        case GT:
                            take_branch = (value0 > 0);
                            break;
                        case GE:
                            take_branch = (value0 >= 0);
                            break;
                        case LT:
                            take_branch = (value0 < 0);
                            break;
                        case LE:
                            take_branch = (value0 <= 0);
                            break;
                    }
                    
                    if (take_branch) {
                        // If the branch is taken, send a signal to Fetch
                        // that specifies the branch target address, via
                        // "globals.next_program_counter_takenbranch".  
                        // If the label is valid, then use its address.  
                        // Otherwise, the target address will be found in 
                        // src1.
                        if (ins.getLabelTarget().isNull()) {
                            // If branching to address in register, make sure
                            // operand is valid.
                            if (!src1.hasValue()) {
//                                Logger.out.println("Stall BRA wants src1 R" + src1.getRegisterNumber());
                                this.setResourceWait(src1.getRegisterName());
                                // Nothing else to do.  Bail out.
                                return;
                            }
                            
                            value1 = src1.getValue();
                        } else {
                            value1 = ins.getLabelTarget().getAddress();
                        }
                        globals.setClockedProperty("program_counter_takenbranch", value1);
                        
                        // Send a signal to Fetch, indicating that the branch
                        // is resolved taken.  This will be picked up by
                        // Fetch.advanceClock on the same clock cycle.
                        globals.setClockedProperty("branch_state_decode", GlobalData.BRANCH_STATE_TAKEN);
                        globals.setClockedProperty("decode_squash", true);
//                        Logger.out.println("Resolving branch taken");
                    } else {
                        // Send a signal to Fetch, indicating that the branch
                        // is resolved not taken.
                        globals.setClockedProperty("branch_state_decode", GlobalData.BRANCH_STATE_NOT_TAKEN);
//                        Logger.out.println("Resolving branch not taken");
                    }
                    
                    // Since we don't pass an instruction to the next stage,
                    // must explicitly call input.consume in the case that
                    // the next stage is busy.
                    input.consume();
                    // All done; return.
                    return;
                    
                case JMP:
                    // JMP is an inconditionally taken branch.  If the
                    // label is valid, then take its address.  Otherwise
                    // its operand0 contains the target address.
                    if (ins.getLabelTarget().isNull()) {
                        if (!oper0.hasValue()) {
                            // If branching to address in register, make sure
                            // operand is valid.
//                            Logger.out.println("Stall JMP wants oper0 R" + oper0.getRegisterNumber());
                            this.setResourceWait(oper0.getRegisterName());
                            // Nothing else to do.  Bail out.
                            return;
                        }
                        
                        value0 = oper0.getValue();
                    } else {
                        value0 = ins.getLabelTarget().getAddress();
                    }
                    globals.setClockedProperty("program_counter_takenbranch", value0);
                    globals.setClockedProperty("branch_state_decode", GlobalData.BRANCH_STATE_TAKEN);
                    globals.setClockedProperty("decode_squash", true);
                    
                    // Since we don't pass an instruction to the next stage,
                    // must explicitly call input.consume in the case that
                    // the next stage is busy.
                    input.consume();
                    return;
                    
                case CALL:
                    // CALL is an inconditionally taken branch.  If the
                    // label is valid, then take its address.  Otherwise
                    // its src1 contains the target address.
                    if (ins.getLabelTarget().isNull()) {
                        if (!src1.hasValue()) {
                            // If branching to address in register, make sure
                            // operand is valid.
//                            Logger.out.println("Stall JMP wants oper0 R" + oper0.getRegisterNumber());
                            this.setResourceWait(src1.getRegisterName());
                            // Nothing else to do.  Bail out.
                            return;
                        }
                        
                        value1 = src1.getValue();
                    } else {
                        value1 = ins.getLabelTarget().getAddress();
                    }
                    
                    // CALL also has a destination register, which is oper0.
                    // Before we can resolve the branch, we have to make sure
                    // that the return address can be passed to Writeback
                    // through Execute before we go setting any globals.
                    
                   // if(GlobalData.issuequeue[255] != "")
                     // if (!output.canAcceptWork()) return;
                    
                    // To get the return address into Writeback, we will
                    // replace the instruction's source operands with the
                    // address of the instruction and a constant 1.
                    
                    Operand pc_operand = Operand.newRegister(Operand.PC_REGNUM);
                    pc_operand.setIntValue(ins.getPCAddress());
                    ins.setSrc1(pc_operand);
                    ins.setSrc2(Operand.newLiteralSource(1));
                    ins.setLabelTarget(VoidLabelTarget.getVoidLabelTarget());
                    output.setInstruction(ins);
                    //regfile.markInvalid(oper0.getRegisterNumber());
                          
                    int opr0regnum = ins.getOper0().getRegisterNumber();    
                    Logger.out.print("Dest "+oper0.getRegisterName()+":");
    	            regfile.markRenamed(GlobalData.register_alias_table[opr0regnum], true);
    	            Logger.out.print(" P"+GlobalData.register_alias_table[opr0regnum] + " released, ");
    	            //regfile.changeFlags(GlobalData.register_alias_table[opr0regnum],0,IRegFile.CLEAR_USED);
    	            regfile.changeFlags(regnum,IRegFile.SET_USED | IRegFile.SET_INVALID ,IRegFile.CLEAR_FLOAT | IRegFile.CLEAR_RENAMED);
    	            GlobalData.register_alias_table[opr0regnum] = regnum;
    	            Logger.out.print("P"+GlobalData.register_alias_table[opr0regnum]+" allocated \n");
    	            oper0.rename(regnum);
    	            
    	            
    	            /*ins.getOper0().rename(GlobalData.register_alias_table[opr0regnum]);
                    */
                    
                    globals.setClockedProperty("program_counter_takenbranch", value1);
                    globals.setClockedProperty("branch_state_decode", GlobalData.BRANCH_STATE_TAKEN);
                    globals.setClockedProperty("decode_squash", true);
                    
                    // Do need to pass CALL to the next stage, so we do need
                    // to stall if the next stage can't accept work, so we
                    // do not explicitly consume the input here.  Since
                    // this code already fills the output latch, we can
                    // just quit. [hint for HW5]
                    if(GlobalData.IQ_full){
                     output.write();
                     output.copyAllPropertiesFrom(input);
                    }
                    //input.consume();
                    return;
            }
            
            
            // Allocate an output latch for the output pipeline register
            // appropriate for the type of instruction being processed.
           // Latch output;
            //int output_num;
            /*if (opcode == EnumOpcode.MUL) {
                output_num = lookupOutput("DecodeToMSFU");
                output = this.newOutput(output_num);
            } else {
            	if (opcode == EnumOpcode.FCMP || opcode == EnumOpcode.FSUB || opcode == EnumOpcode.FADD) {
                    output_num = lookupOutput("DecodeToMSCMPSUB");
                    output = this.newOutput(output_num);
                }
	            else{
	            	if(opcode == EnumOpcode.FDIV || opcode == EnumOpcode.DIV){
	            		if(opcode == EnumOpcode.FDIV)
	            			output_num = lookupOutput("DecodeToFloatDiv");
	            		else 
	            		    output_num = lookupOutput("DecodeToIntDiv");
	                    output = this.newOutput(output_num);
	            	}
	            	else{
	            		if(opcode == EnumOpcode.FMUL){
	            			output_num = lookupOutput("DecodeToMSMUL");
	                        output = this.newOutput(output_num);
	            		}
	            		else {
            if (opcode.accessesMemory()) {
                output_num = lookupOutput("DecodeToMemory");
                output = this.newOutput(output_num);
            } else {
                output_num = lookupOutput("DecodeToExecute");
                output = this.newOutput(output_num);
            }
	            		}
	            	}
	            }    
            }*/
            // If the desired output is stalled, then just bail out.
            // No inputs have been claimed, so this will result in a
            // automatic pipeline stall.
            //if (!output.canAcceptWork()) return;
            
            
          /*  int[] srcRegs = new int[3];
            // Only want to forward to oper0 if it's a source.
            srcRegs[0] = opcode.oper0IsSource() ? oper0.getRegisterNumber() : -1;
            srcRegs[1] = src1.getRegisterNumber();
            srcRegs[2] = src2.getRegisterNumber();
            Operand[] operArray = {oper0, src1, src2};
            
            // Loop over source operands, looking to see if any can be
            // forwarded to the next stage.
            for (int sn=0; sn<3; sn++) {
                int srcRegNum = srcRegs[sn];
                // Skip any operands that are not register sources
                if (srcRegNum < 0) continue;
                // Skip any that already have values
                if (operArray[sn].hasValue()) continue;
                
                String propname = "forward" + sn;
                if (!input.hasProperty(propname)) {
                    // If any source operand is not available
                    // now or on the next cycle, then stall.
                    //Logger.out.println("Stall because no " + propname);
                    this.setResourceWait(operArray[sn].getRegisterName());
                    // Nothing else to do.  Bail out.
                    return;
                }
            }*/
            
            if (ins.getOpcode() == EnumOpcode.HALT) shutting_down = true;            
            
            /*if (CpuSimulator.printForwarding) {
                for (int sn=0; sn<3; sn++) {
                    String propname = "forward" + sn;
                    if (input.hasProperty(propname)) {
                        String operName = PipelineStageBase.operNames[sn];
                        String srcFoundIn = input.getPropertyString(propname);
                        String srcRegName = operArray[sn].getRegisterName();
                        Logger.out.printf("# Posting forward %s from %s to %s next stage\n", 
                                srcRegName,
                                srcFoundIn, operName);
                    }
                }
            } */           
                    
            // If we managed to find all source operands, mark the destination
            // register invalid then finish putting data into the output latch 
            // and send it.
            
            // Mark the destination register invalid
            if (opcode.needsWriteback()) {
                int oper0reg = oper0.getRegisterNumber();
               // regfile.markInvalid(oper0reg);
            	Logger.out.print("Dest "+oper0.getRegisterName()+":");
                //int opr0regnum = ins.getOper0().getRegisterNumber();            	 
	            regfile.markRenamed(GlobalData.register_alias_table[oper0reg], true);
	            Logger.out.print(" P"+GlobalData.register_alias_table[oper0reg] + " released, ");
	            //regfile.changeFlags(GlobalData.register_alias_table[oper0reg],0,IRegFile.CLEAR_USED);
	            regfile.changeFlags(regnum,IRegFile.SET_USED | IRegFile.SET_INVALID ,IRegFile.CLEAR_FLOAT | IRegFile.CLEAR_RENAMED);
	            GlobalData.register_alias_table[oper0reg] = regnum;
	            Logger.out.print("P"+GlobalData.register_alias_table[oper0reg]+" allocated \n");
	            oper0.rename(regnum);
	            
	            
            }            
            
            if(GlobalData.IQ_full){
            
            // Copy the forward# properties
            output.copyAllPropertiesFrom(input);
            // Copy the instruction
            output.setInstruction(ins);
            // Send the latch data to the next stage
            output.write();
            
            // And don't forget to indicate that the input was consumed!
            input.consume();
            }
        }
    }
    
    
    
  /*** Issue Queue ***/  
    static class IssueQueue extends PipelineStageBase {
        public IssueQueue(ICpuCore core) {
            super(core, "IssueQueue");
        }  
        
        boolean has_work;
        
        
        @Override
        public boolean stageHasWorkToDo() {
            return has_work;
        }
        
        @Override
        public void compute() {
        	Latch input = this.readInput(0).duplicate();
        	//if (input.isNull()) return;
        	boolean Outputstall = false;        	
        	//doPostedForwarding(input);
            InstructionBase ins = input.getInstruction();            
            //setActivity(ins.toString());  
            int output_num;
            int index_store = 0;
            String l_printvalue1 = "";
            String output_value = "";
            String [] l_printvalue = new String [256];
            int Count = 0;
            IGlobals globals = (GlobalData)getCore().getGlobals();
            has_work =false;
            for (int i =0;i<256;i++){
            	if(GlobalData.issuequeue[i] == ""  ){
            		GlobalData.issuequeue[i] = input.getInstruction().toString();
            		index_store = i;
            		//l_printvalue += input.getInstruction().toString();
            		getCore().incIssued();
            		//GlobalData.latch[i] = new Latch();
            		GlobalData.latch[i] = input;
            		input.consume();
            		GlobalData.IQ_full = true;
            		break;
            	}
            }
            
            if(!GlobalData.IQ_full){
            	addStatusWord("Issue Queue Is Full");
            }
            
           /* for (int i =0;i<256;i++){
            	if(GlobalData.issuequeue[i] != ""){
            		System.out.println("IssueQueue : "+i +" :"+GlobalData.issuequeue[i]);
            		//input.consume();
            		
            	}
            }*/
            
            
            
            for(int i =0;i<GlobalData.issuequeue.length;i++){
               
            	
               if(GlobalData.issuequeue[i] != ""){	
	            	Latch output;
	            	//InstructionBase ins1 = new InstructionBase();
	            	//doPostedForwarding(GlobalData.latch[i]);
	            	
	            	
	            	
	            	  if (!GlobalData.latch[i].isDuplicate()) {
	                      throw new RuntimeException("forwardingSearch must be called on a duplicate of the original input latch!");
	                  }
	                  InstructionBase ins1 = GlobalData.latch[i].getInstruction();
	                  ICpuCore core = getCore();
	                  
	                  GlobalData.latch[i].deleteProperty("forward0");
	                  GlobalData.latch[i].deleteProperty("forward1");
	                  GlobalData.latch[i].deleteProperty("forward2");
	                  
	                  Set<String> fwdSources = core.getForwardingSources();
//	                  for (String s : fwdSources) {
//	                      Logger.out.println(s);
//	                  }

	                  EnumOpcode opcod = ins1.getOpcode();
	                  boolean oper0src = opcod.oper0IsSource();

	                  Operand ope0 = ins1.getOper0();
	                  Operand sr1  = ins1.getSrc1();
	                  Operand sr2  = ins1.getSrc2();
	                  // Put operands into array because we will loop over them,
	                  // searching the pipeline for forwarding opportunities.
	                  Operand[] operArray1 = {ope0, sr1, sr2};

	                  // For operands that are not registers, getRegisterNumber() will
	                  // return -1.  We will use that to determine whether or not to
	                  // look for a given register in the pipeline.
	                  int[] srcReg = new int[3];
	                  // Only want to forward to oper0 if it's a source.
	                  srcReg[0] = oper0src ? ope0.getRegisterNumber() : -1;
	                  srcReg[1] = sr1.getRegisterNumber();
	                  srcReg[2] = sr2.getRegisterNumber();

	                  for (int sn=0; sn<3; sn++) {
	                      int srcRegNum = srcReg[sn];
	                      // Skip any operands that are not register sources
	                      if (srcRegNum < 0) continue;
	                      // Skip any operands that already have values
	                      if (operArray1[sn].hasValue()) continue;
	                      Operand oper = operArray1[sn];
	                      String srcRegName = oper.getRegisterName();
	                      String operName = operNames[sn];
	                      
	                      if(operName == "src1"){
	                        	operName = "op1";
	                      }else {
	                        	if(operName == "src2")
	                        		operName = "op2";	
	                      }
	                      
	                      String srcFoundIn = null;
	                      boolean next_cycle = false;

	                      prn_loop:
	                      for (String fwd_pipe_reg_name : fwdSources) {
	                          IPipeReg.EnumForwardingStatus fwd_stat = core.matchForwardingRegister(fwd_pipe_reg_name, srcRegNum);

	                          switch (fwd_stat) {
	                              case NULL:
	                                  break;
	                              case VALID_NOW:
	                                  srcFoundIn = fwd_pipe_reg_name;
	                                  break prn_loop;
	                              case VALID_NEXT_CYCLE:
	                                  srcFoundIn = fwd_pipe_reg_name;
	                                  next_cycle = true;
	                                  break prn_loop;
	                          }
	                      }

	                      if (srcFoundIn != null) {
	                          if (!next_cycle) {
	                              // If the register number was found and there is a valid
	                              // result, go ahead and get the value.
	                              int value = core.getResultValue(srcFoundIn);
	                              boolean isfloat = core.isResultFloat(srcFoundIn);
	                              operArray1[sn].setValue(value, isfloat);
	                              
	                              String [] holddata = new String [256];
	                              holddata = GlobalData.issuequeue[i].split(" ");
	                              
	                              for(int z=0;z<holddata.length;z++){
	                            	  if(holddata[z]!=null && holddata[z]!=""){
	                            	   if(holddata[z].equals(srcRegName)){	                            		  
	                            		   holddata[z] = holddata[z]+"="+oper.getValueAsString();	                            		   
	                            		   //System.out.println("Hodldata "+holddata[z]);
	                            	   }
	                            	  }
	                              }	 
	                              
	                              String totaldata="";
	                              for(int l = 0; l<holddata.length;l++){
	                            	  totaldata += holddata[l]+" ";
	                              }
	                              
	                              if (CpuSimulator.printForwarding) {
	                                  Logger.out.printf("# Forward from %s this cycle to IQ: %s of %s\n", 
	                                          
	                                          srcFoundIn, operName,totaldata
	                                          );
	                              }
	                          } else {
	                              // Post forwarding for the next stage on the next cycle by
	                              // setting a property on the latch that specifies which
	                              // operand(s) is forwarded from what pipeline register.
	                              // For instance, setting the property "forward1" to the 
	                              // value "ExecuteToWriteback" will inform the next stage
	                              // to get a value for src1 from ExecuteToWriteback.
	                              String propname = "forward" + sn;
	                              GlobalData.latch[i].setProperty(propname, srcFoundIn);
	                              
//	                              if (CpuSimulator.printForwarding) {
//	                                  Logger.out.printf("Posting forward %s from %s to %s next stage\n", 
//	                                          srcRegName,
//	                                          srcFoundIn, operName);
//	                              }
	                          }
	                      }
	                  }

	            	
	            	
	            	
	            	
	            	
	            	
	            	ins = GlobalData.latch[i].getInstruction();
	            	if(GlobalData.latch[i].getInstruction().toString() != null && GlobalData.latch[i].getInstruction().toString() != ""){
		            	if(i == index_store ){
		            		l_printvalue[Count] = GlobalData.latch[i].getInstruction().toString()+" [new]";
		            		has_work = true;
		            	}else{ 
		            		l_printvalue[Count] = GlobalData.latch[i].getInstruction().toString();
		            	}	
		            	 Count ++;
	            	} 
	            	Operand src1  = ins.getSrc1();
	                Operand src2  = ins.getSrc2();
	                Operand oper0 = ins.getOper0();
	                EnumOpcode opcode = ins.getOpcode();
	            	
	            	
		            if (opcode == EnumOpcode.MUL) {
		                output_num = lookupOutput("IssueQueueToMSFU");
		                output = this.newOutput(output_num);
		                output_value = "in:";
		            } else {
		            	if (opcode == EnumOpcode.FCMP || opcode == EnumOpcode.FSUB || opcode == EnumOpcode.FADD) {
		                    output_num = lookupOutput("IssueQueueToMSCMPSUB");
		                    output = this.newOutput(output_num);
		                    output_value = "in:";
		                }else{
			            	if(opcode == EnumOpcode.FDIV || opcode == EnumOpcode.DIV){
			            		if(opcode == EnumOpcode.FDIV)
			            			output_num = lookupOutput("IssueQueueToFloatDiv");
			            		else 
			            		    output_num = lookupOutput("IssueQueueToIntDiv");
			                    output = this.newOutput(output_num);
			                    output_value = "in:";
			            	}
			            	else{
			            		if(opcode == EnumOpcode.FMUL){
			            			output_num = lookupOutput("IssueQueueToMSMUL");
			                        output = this.newOutput(output_num);
			                        output_value = "in:";
			            		}else {
						            if (opcode.accessesMemory()) {
						                output_num = lookupOutput("IssueQueueToMemory");
						                output = this.newOutput(output_num);
						                output_value = "in:Addr:";
						            } else {
						                output_num = lookupOutput("IssueQueueToExecute");
						                output = this.newOutput(output_num);
						            }
			            		}
			            	}
			            }    
		            }
		            
		            //if (!output.canAcceptWork()) continue;
		            
		            
		            
		            int[] srcRegs = new int[3];
		            // Only want to forward to oper0 if it's a source.
		            srcRegs[0] = opcode.oper0IsSource() ? oper0.getRegisterNumber() : -1;
		            srcRegs[1] = src1.getRegisterNumber();
		            srcRegs[2] = src2.getRegisterNumber();
		            Operand[] operArray = {oper0, src1, src2};
		            boolean try1 = false;
		            
		            // Loop over source operands, looking to see if any can be
		            // forwarded to the next stage.
		            
		            
		            for (int sn=0; sn<3; sn++) {
		                int srcRegNum = srcRegs[sn];
		                // Skip any operands that are not register sources
		                if (srcRegNum < 0) continue;
		                // Skip any that already have values
		                if (operArray[sn].hasValue()) continue;
		                
		                String propname = "forward" + sn;
		                if (!GlobalData.latch[i].hasProperty(propname)) {
		                    // If any source operand is not available
		                    // now or on the next cycle, then stall.
		                    //Logger.out.println("Stall because no " + propname);
		                  // this.setResourceWait(operArray[sn].getRegisterName());
		                    // Nothing else to do.  Bail out.
		                   // System.out.println("-----------------------Wait-----------------");
		                    //addStatusWord("OutputStall");
		                    
		                   // has_work = true;
		                    try1 = true;
		                }
		            }
		            
		            if(try1){
		            	try1 = false;
		            	continue;
		            }
		            
		            if (!try1) {
		                for (int sn=0; sn<3; sn++) {
		                    String propname = "forward" + sn;
		                    if (GlobalData.latch[i].hasProperty(propname)) {
		                        String operName = PipelineStageBase.operNames[sn];
		                        String pipe_reg_name = null;
		                        if(operName == "src1"){
		                        	operName = "op1";
		                        }else {
		                        	if(operName == "src2")
		                        		operName = "op2";	
		                        }
		                        
		                       /* if(propname.equals("forward0")){
		                        	pipe_reg_name = input.getPropertyString("forward0");
		                        }
		                        if(propname.equals("forward1")){
		                        	 pipe_reg_name = input.getPropertyString("forward1");
		                        }
		                        if(propname.equals("forward2")){
		                        	 pipe_reg_name = input.getPropertyString("forward2");
		                        }
		                        	
		                        */
		                        
		                        //String pipelinename =  output.getName();
		                        String srcFoundIn = GlobalData.latch[i].getPropertyString(propname);
		                        String srcRegName = operArray[sn].getRegisterName();
		                        //System.out.println("------------val-----------"+pipe_reg_name);
		                        //System.out.println("-----------name---- "+pipelinename);
		                       /* if(srcFoundIn == ""){
		                        	if(pipelinename == "IssueQueueToMSMUL")
		                        	    srcFoundIn = "FloatMul.out";
		                        }*/
		                        
		                        Logger.out.printf("# Posting forward %s from %s next cycle to %s %s of %s\n", 
		                                srcRegName,
		                                srcFoundIn, operName,output_value,GlobalData.issuequeue[i]);
		                    }
		                }
		            }
		           // GlobalData.issuequeue[i] = "";
		            
		            //output.copyAllPropertiesFrom(input);
		            	
		            if(!outputCanAcceptWork(output_num) ){
		            	
		            	continue;
		            }else {
		            	l_printvalue[Count-1] = l_printvalue[Count-1]+" [selected]";
		            


	            	
	            		
	            	
		            output.copyAllPropertiesFrom(GlobalData.latch[i]);
		            output.setInstruction(ins);
	            	output.write();
	            	//System.out.println("Dispatched : " + GlobalData.issuequeue[i] ); 
	            	GlobalData.issuequeue[i] = "";
	            	getCore().incDispatched();
	            	GlobalData.latch[i] = null;
	            	
	            	Outputstall = true;
		            
		           /* for(int j = i; j<GlobalData.issuequeue.length;j++){
		            	if(GlobalData.issuequeue[j+1] != ""){
		            		GlobalData.issuequeue[j] = GlobalData.issuequeue[j+1];
		            		GlobalData.latch[j] = GlobalData.latch[j+1];
		            	}else {
		            		GlobalData.issuequeue[j] = "";
		            		GlobalData.latch[j] = null;
		            		break;
		            	}
		            }*/
		            
		            //
		            

	            	//input.consume();
	            	//try1 = false;
		            //
		            }  
                }
            }
            
            if(!ins.isNull()){
	            if(!Outputstall && !has_work){
	            	addStatusWord("OutputStall(IQ2FD)");
	            }
            }    
            for (int i =0; i<l_printvalue.length;i++){
            	if(l_printvalue[i] != null && l_printvalue[i] != "")
            	    l_printvalue1 += l_printvalue[i]+"\n";
            }
            setActivity(l_printvalue1);
            /*for (int i =0;i<256;i++){
            	if(GlobalData.issuequeue[i] != ""){
            		System.out.println("IssueQueue : "+i +" :"+GlobalData.issuequeue[i]);
            		//input.consume();
            		
            	}
            }*/
            
        }
        
    }   
 /*** Issue Queue ***/  

    /*** Execute Stage ***/
    static class Execute extends PipelineStageBase {
        public Execute(ICpuCore core) {
            super(core, "Execute");
        }

        @Override
        public void compute(Latch input, Latch output) {
            if (input.isNull()) return;
            doPostedForwarding(input);
            InstructionBase ins = input.getInstruction();

            int source1 = ins.getSrc1().getValue();
            int source2 = ins.getSrc2().getValue();
            int oper0 =   ins.getOper0().getValue();

            int result = MyALU.execute(ins.getOpcode(), source1, source2, oper0);
                        
            boolean isfloat = ins.getSrc1().isFloat() || ins.getSrc2().isFloat();
            output.setResultValue(result, isfloat);
            output.setInstruction(ins);
        }
    }
    

    /*** Writeback Stage ***/
    static class Writeback extends PipelineStageBase {
        public Writeback(CpuCore core) {
            super(core, "Writeback");
        }
        
        boolean shutting_down = false;

        @Override
        public void compute() {
            List<String> doing = new ArrayList<String>();
            
            ICpuCore core = getCore();
            IGlobals globals = (GlobalData)core.getGlobals();
            // Get register file and valid flags from globals
            IRegFile regfile = globals.getRegisterFile();
            
            if (shutting_down) {
                Logger.out.println("disp=" + core.numDispatched() + " compl=" + core.numCompleted());
                setActivity("Shutting down");
            }
            if (shutting_down && core.numCompleted() >= core.numDispatched()) {
                globals.setProperty("running", false);
            }
            
            // Writeback has multiple inputs, so we just loop over them
            int num_inputs = this.getInputRegisters().size();
            for (int i=0; i<num_inputs; i++) {
                // Get the input by index and the instruction it contains
                Latch input = readInput(i);
                
                // Skip to the next iteration of there is no instruction.
                if (input.isNull()) continue;
                
                InstructionBase ins = input.getInstruction();
                if (ins.isValid()) core.incCompleted();
                doing.add(ins.toString());
                
                if (ins.getOpcode().needsWriteback()) {
                    // By definition, oper0 is a register and the destination.
                    // Get its register number;
                    Operand op = ins.getOper0();
                    String regname = op.getRegisterName();
                    int regnum = op.getRegisterNumber();
                    int value = input.getResultValue();
                    boolean isfloat = input.isResultFloat();

                    addStatusWord(regname + "=" + input.getResultValueAsString());
                    regfile.setValue(regnum, value, isfloat);
                }

                if (ins.getOpcode() == EnumOpcode.HALT) {
                    shutting_down = true;
                }
                
                // There are no outputs that could stall, so just consume
                // all valid inputs.
                input.consume();
            }
            
            setActivity(String.join("\n", doing));
        }
    }
}
