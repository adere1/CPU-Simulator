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
                setActivity("----: NULL");
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
        public void compute() {
            // Since this stage has multiple outputs, must read input(s) 
            // explicitly
            Latch input = this.readInput(0).duplicate();
            InstructionBase ins = input.getInstruction();
            IGlobals globals = (GlobalData)getCore().getGlobals();
            IRegFile regfile = globals.getRegisterFile();
            // Default to no squashing.
            squashing_instruction = false;
            
            setActivity(ins.toString());

            /*for(int r =0; r<256 ; r++){
                if(regfile.isValid(r) && regfile.isRenamed(r) && regfile.isUsed(r)){
                	//regfile.markUsed(regnum, false);
                	//regfile.changeFlags(GlobalData.register_alias_table[opr0regnum],)
                	regfile.markUsed(r, false);
                	//break;
                }
            }*/
            
            
            
            if (globals.getPropertyBoolean("decode_squash")) {
                // Drop the fall-through instruction.
                globals.setClockedProperty("decode_squash", false);
                squashing_instruction = false;
                //setActivity("----: NULL");
//                globals.setClockedProperty("branch_state_decode", GlobalData.BRANCH_STATE_NULL);
                
                // Squashing the fall-through instruction is "consuming" it, so we
                // mustn't forget to consume it.
                input.consume();
                return;
            }
            
            if (ins.isNull()) return;
            
            EnumOpcode opcode = ins.getOpcode();
            Operand oper0 = ins.getOper0();
            
            
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
            
            
            
            
            
            int d2e_output_num = lookupOutput("DecodeToExecute");
            Latch d2e_output = this.newOutput(d2e_output_num);
            
            
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
                    
                    // Having completed execution of the BRA instruction, we must
                    // explicitly indicate that it has been consumed.
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
                    
                    // Having completed execution of the JMP instruction, we must
                    // explicitly indicate that it has been consumed.
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
                    // through Execute.
                    if (!d2e_output.canAcceptWork()) return;
                    
                    // To get the return address into Writeback, we will
                    // replace the instruction's source operands with the
                    // address of the instruction and a constant 1.
                    
                    //if(opcode.needsWriteback()){
	                    
                     
	                   
                    //}*/
                    
                    
                    //GlobalData.freesource = GlobalData.register_alias_table[opr0regnum];
                    
                    Operand pc_operand = Operand.newRegister(Operand.PC_REGNUM);
                    pc_operand.setIntValue(ins.getPCAddress());
                    //System.out.println("ins.getoper0"+ins.getOper0().getRegisterNumber());
                    //System.out.println("ins.setSrc1"+ins.getSrc1().getValue());
                    ins.setSrc1(pc_operand);
                    //System.out.println("ins.setSrc1"+ins.getSrc1().getValue());
                    //System.out.println("ins.setSrc2"+ins.getSrc2().getValue());
                    ins.setSrc2(Operand.newLiteralSource(1));
                    //System.out.println("ins.setSrc2"+ins.getSrc2().getValue());
                    ins.setLabelTarget(VoidLabelTarget.getVoidLabelTarget());
                    d2e_output.setInstruction(ins);
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
    	            
    	            d2e_output.copyAllPropertiesFrom(input);
    	            /*ins.getOper0().rename(GlobalData.register_alias_table[opr0regnum]);
                    */
                    
                    globals.setClockedProperty("program_counter_takenbranch", value1);
                    globals.setClockedProperty("branch_state_decode", GlobalData.BRANCH_STATE_TAKEN);
                    globals.setClockedProperty("decode_squash", true);
                    
                    d2e_output.write();
                    input.consume();
                    return;
                    
                    // Having completed execution of the JMP instruction, we must
                    // explicitly indicate that it has been consumed.
//                    input.consume();
//                    return;
            }
            
            
            // Allocate an output latch for the output pipeline register
            // appropriate for the type of instruction being processed.
            Latch output;
            int output_num;
            if (opcode == EnumOpcode.MUL) {
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
            }
            // If the desired output is stalled, then just bail out.
            // No inputs have been claimed, so this will result in a
            // automatic pipeline stall.
            if (!output.canAcceptWork()) return;
            
            
            
            
          /* if(opcode.needsWriteback()){
	            int opr0regnum = ins.getOper0().getRegisterNumber();            	 
	            regfile.markRenamed(GlobalData.register_alias_table[opr0regnum], true);
	            //regfile.markUsed(regnum, true);
	            //regfile.changeFlags(GlobalData.register_alias_table[opr0regnum], IRegFile.SET_RENAMED, IRegFile.CLEAR_INVALID);
	            regfile.changeFlags(regnum,IRegFile.SET_USED | IRegFile.SET_INVALID ,IRegFile.CLEAR_FLOAT | IRegFile.CLEAR_RENAMED);
	            GlobalData.register_alias_table[opr0regnum] = regnum;
	            ins.getOper0().rename(GlobalData.register_alias_table[opr0regnum]);
            }*/
            
            
            int[] srcRegs = new int[3];
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
            }
            
            
            if (CpuSimulator.printForwarding) {
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
            }            
                    
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
    

    /*** Memory Stage ***/
 /*   static class Memory extends PipelineStageBase {
        public Memory(ICpuCore core) {
            super(core, "Memory");
        }

        @Override
        public void compute(Latch input, Latch output) {
            if (input.isNull()) return;
            doPostedForwarding(input);
            InstructionBase ins = input.getInstruction();
            setActivity(ins.toString());

            Operand oper0 = ins.getOper0();
            int oper0val = ins.getOper0().getValue();
            int source1 = ins.getSrc1().getValue();
            int source2 = ins.getSrc2().getValue();
            
            // The Memory stage no longer follows Execute.  It is an independent
            // functional unit parallel to Execute.  Therefore we must perform
            // address calculation here.
            int addr = source1 + source2;
            
            int value = 0;
            IGlobals globals = (GlobalData)getCore().getGlobals();
            int[] memory = globals.getPropertyIntArray(MAIN_MEMORY);

            switch (ins.getOpcode()) {
                case LOAD:
                    // Fetch the value from main memory at the address
                    // retrieved above.
                    value = memory[addr];
                    output.setResultValue(value);
                    output.setInstruction(ins);
                    addStatusWord("Mem[" + addr + "]");
                    break;
                
                case STORE:
                    // For store, the value to be stored in main memory is
                    // in oper0, which was fetched in Decode.
                    memory[addr] = oper0val;
                    addStatusWord("Mem[" + addr + "]=" + ins.getOper0().getValueAsString());
                    return;
                    
                default:
                    throw new RuntimeException("Non-memory instruction got into Memory stage");
            }
        }
    }*/
    

    /*** Writeback Stage ***/
    static class Writeback extends PipelineStageBase {
        public Writeback(CpuCore core) {
            super(core, "Writeback");
        }

        @Override
        public void compute() {
            IGlobals globals = (GlobalData)getCore().getGlobals();
            // Get register file and valid flags from globals
            IRegFile regfile = globals.getRegisterFile();
            
            // Writeback has multiple inputs, so we just loop over them
            int num_inputs = this.getInputRegisters().size();
            for (int i=0; i<num_inputs; i++) {
                // Get the input by index and the instruction it contains
                Latch input = readInput(i);
                
                // Skip to the next iteration of there is no instruction.
                if (input.isNull()) continue;
                
                InstructionBase ins = input.getInstruction();
                
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

                if (input.getInstruction().getOpcode() == EnumOpcode.HALT) {
                    globals.setProperty("running", false);
                }
                
                // There are no outputs that could stall, so just consume
                // all valid inputs.
                input.consume();
            }
        }
    }
}
