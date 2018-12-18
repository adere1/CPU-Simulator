/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import implementation.AllMyLatches.*;
import utilitytypes.EnumComparison;
import utilitytypes.EnumOpcode;
import baseclasses.InstructionBase;
import baseclasses.PipelineRegister;
import baseclasses.PipelineStageBase;
import voidtypes.VoidLatch;
import baseclasses.CpuCore;
import implementation.GlobalData;
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
	//public static boolean  is_stall_resolved = false;
	
    static class Fetch extends PipelineStageBase<VoidLatch,FetchToDecode> {
    	
    	public Fetch(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }
        
        @Override
        public String getStatus() {
            // Generate a string that helps you debug.        	
        	//--Aditya
        	return null;
        }

        @Override
        public void compute(VoidLatch input, FetchToDecode output) {
            GlobalData globals = (GlobalData)core.getGlobalResources();
            int pc = globals.program_counter;
            // Fetch the instruction
            InstructionBase ins = globals.program.getInstructionAt(pc);
            if (ins.isNull()) return;

            // Do something idempotent to compute the next program counter.
            // Don't forget branches, which MUST be resolved in the Decode
            // stage.  You will make use of global resources to commmunicate
            // between stages.
            // Your code goes here...
            
            if( globals.l_jmp_instr){
	           	if(ins.getOper0().isRegister()){
	           		globals.register_invalid[ins.getOper0().getRegisterNumber()] = false;
	           	}
	         return;
           }
           output.setInstruction(ins);
        }
        
        @Override
        public boolean stageWaitingOnResource() {
            // Hint:  You will need to implement this for when branches
            // are being resolved.
        	
        	//--Aditya
        	return false;
        	
        }
        /**
         * This function is to advance state to the next clock cycle and
         * can be applied to any data that must be updated but which is
         * not stored in a pipeline register.
         */ 
        @Override
        public void advanceClock() {
            // Hint:  You will need to implement this help with waiting
            // for branch resolution and updating the program counter.
            // Don't forget to check for stall conditions, such as when
            // nextStageCanAcceptWork() returns false.
           GlobalData globals = (GlobalData)core.getGlobalResources();
	       if(nextStageCanAcceptWork()){  
	        		globals.program_counter++;
	        }
        }
    }

    
    /*** Decode Stage ***/
    static class Decode extends PipelineStageBase<FetchToDecode,DecodeToExecute> {
        
    	boolean set_stall_condition;
    	
    	public Decode(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }
        
        @Override
        public boolean stageWaitingOnResource() {
            // Hint:  You will need to implement this to deal with 
            // dependencies.
        	GlobalData globals = (GlobalData)core.getGlobalResources();
        	if(globals.l_set_stall){
        		return true;
        	}else{
        		return false;
        	}
        }
        
        @Override
        public void compute(FetchToDecode input, DecodeToExecute output) {
        	GlobalData globals = (GlobalData)core.getGlobalResources();
        	InstructionBase ins = input.getInstruction();        	
            // You're going to want to do something like this:
            
            // VVVVV LOOK AT THIS VVVVV
            ins = ins.duplicate();
            // ^^^^^ LOOK AT THIS ^^^^^
            
            // The above will allow you to do things like look up register 
            // values for operands in the instruction and set them but avoid 
            // altering the input latch if you're in a stall condition.
            // The point is that every time you enter this method, you want
            // the instruction and other contents of the input latch to be
            // in their original state, unaffected by whatever you did 
            // in this method when there was a stall condition.
            // By cloning the instruction, you can alter it however you
            // want, and if this stage is stalled, the duplicate gets thrown
            // away without affecting the original.  This helps with 
            // idempotency.

            // These null instruction checks are mostly just to speed up
            // the simulation.  The Void types were created so that null
            // checks can be almost completely avoided.
            if (ins.isNull()) return;
            int [] regfile = globals.register_file;
            // Do what the decode stage does:
            // - Look up source operands
            // - Decode instruction
            // - Resolve branches   
            	
           boolean l_stage1 = true;
           boolean l_stage2 = true;
           
           
           if(globals.l_brach_taken ){
	           	if(ins.getOper0().isRegister()){
	           		globals.register_invalid[ins.getOper0().getRegisterNumber()] = false;
	           	}
	           return;
       		}

	       	if(globals.l_jmp_instr){
	       		if (ins.getOper0().isRegister()){
	               	globals.register_invalid[ins.getOper0().getRegisterNumber()] = false;            	
	               }
	       		globals.l_jmp_instr = false;
	       		return;
	       	}
           
           if(ins.getOpcode()== EnumOpcode.JMP){
        	  int result;
        	  if(ins.getLabelTarget().getAddress()<0)
      	    	result =  -1;
      	      else
      	    	result =  ins.getLabelTarget().getAddress();
         	  if(result != -1){
         		  globals.l_jmp_instr = true;
         		  globals.program_counter = result-2;
         	  }else{
         		  globals.l_jmp_instr = false;
         	  }
           }
           if(ins.getSrc1().isRegister()){
            	//if(!globals.register_invalid[ins.getSrc1().getRegisterNumber()]){
            		ins.getSrc1().setValue(globals.register_file[ins.getSrc1().getRegisterNumber()]);
            		//globals.l_set_stall = false;
            	/*}else {
            		if(ins.getSrc1().getRegisterNumber()!= ins.getOper0().getRegisterNumber()){
            			//globals.l_set_stall = true;
            			l_stage1 = false;
            		}	
            	}*/
            }
            if(ins.getSrc2().isRegister()){
            	//if(!globals.register_invalid[ins.getSrc2().getRegisterNumber()]){	
                	ins.getSrc2().setValue(globals.register_file[ins.getSrc2().getRegisterNumber()]);
            		//globals.l_set_stall = false;
            	/*}else {            		
            		if((ins.getSrc2().getRegisterNumber()!= ins.getOper0().getRegisterNumber() 
            				|| ins.getSrc2().getRegisterNumber()!= ins.getSrc1().getRegisterNumber())){
            			//globals.l_set_stall = true;
            			l_stage2 = false;
            		}	
            	}*/
            }
            if (ins.getOper0().isRegister()){
            	//if(!globals.register_invalid[ins.getOper0().getRegisterNumber()]){ //&& l_stage2 && l_stage1){
            		ins.getOper0().setValue(globals.register_file[ins.getOper0().getRegisterNumber()]);
            		//globals.register_invalid[ins.getOper0().getRegisterNumber()] = true;
            		//globals.l_set_stall = false;
            	/*}else {
            		//globals.l_set_stall = true;
            		globals.stall_caused_by_opr0 = "R"+ins.getOper0().getRegisterNumber();
            	}*/
            } 
            
            
           
            if(!globals.l_load_value_available){
	            if(output_reg.read().getInstruction().getOpcode() == EnumOpcode.LOAD ){
	            	if(ins.getSrc2().getRegisterNumber() == output_reg.read().getInstruction().getOper0().getRegisterNumber()
	            		||	ins.getSrc1().getRegisterNumber() == output_reg.read().getInstruction().getOper0().getRegisterNumber()
	            		|| ins.getOper0().getRegisterNumber() == output_reg.read().getInstruction().getOper0().getRegisterNumber()){
	            		globals.l_set_stall = true;	            		
	            	}
	            }
            }else{
            	globals.l_set_stall = false;
            	globals.l_load_value_available = false;
            }	
            
            if(!globals.l_set_stall && ins.getOpcode()!= EnumOpcode.JMP)
            output.setInstruction(ins);
        }
    }
    

    /*** Execute Stage ***/
    static class Execute extends PipelineStageBase<DecodeToExecute,ExecuteToMemory> {
    	
    	public Execute(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }
    	
        @Override
        public void compute(DecodeToExecute input, ExecuteToMemory output) {
        	GlobalData globals = (GlobalData)core.getGlobalResources();        	
        	output.register_saved = input.register_saved;
        	InstructionBase ins = input.getInstruction();
        	if(globals.l_brach_taken){
        		if (ins.getOper0().isRegister()){
                	globals.register_invalid[ins.getOper0().getRegisterNumber()] = false;            	
                }
        		globals.l_brach_taken = false;
        		return;
            }
            
            if (ins.isNull()) return;
            int source1 = ins.getSrc1().getValue();
            int source2 = ins.getSrc2().getValue();
            int oper0 =   ins.getOper0().getValue();
            
            if(ins.getOpcode() == EnumOpcode.BRA){
        		if(ins.getOper0().isRegister()){
        		  if(ins.getOper0().getRegisterNumber() == output_reg.read().getInstruction().getOper0().getRegisterNumber()){	
        			oper0 = output_reg.read().l_alu_output;
        		  }else{
        			  if(globals.l_maintain_register_WB != null){
		    	        	if(ins.getOper0().getRegisterNumber() == Integer.parseInt(globals.l_maintain_register_WB.split("R")[1])){
		    	        		oper0 = globals.l_maintain_value_WB;
			        		}
	    	           }
        		  }
        		}
        	}
            
            if(globals.l_my_next_load){            	
                if(ins.getOper0().isRegister()){
	            	if(ins.getOper0().getRegisterNumber() == Integer.parseInt(globals.l_load_memory_register.split("R")[1])){
	    	        		oper0=globals.l_memory_output;
	    	        }
            	}
            	if(ins.getSrc1().isRegister()){
            		if(ins.getSrc1().getRegisterNumber() == Integer.parseInt(globals.l_load_memory_register.split("R")[1])){
	        		 source1=globals.l_memory_output;
            		}
            	}
            	if(ins.getSrc2().isRegister()){
            		if(ins.getSrc2().getRegisterNumber() == Integer.parseInt(globals.l_load_memory_register.split("R")[1])){
	        		 source2=globals.l_memory_output;
            		}
            	}	
            	globals.l_my_next_load = false; 
            }
            
            if(ins.getOpcode() == EnumOpcode.STORE){
            	if(ins.getOper0().isRegister()){
	            	if(ins.getOper0().getRegisterNumber() == output_reg.read().getInstruction().getOper0().getRegisterNumber()){
	    	        		oper0=output_reg.read().l_alu_output;
	    	        }else {
	    	        	if(globals.l_maintain_register_WB != null){
		    	        	if(ins.getOper0().getRegisterNumber() == Integer.parseInt(globals.l_maintain_register_WB.split("R")[1])){
		    	        		oper0 = globals.l_maintain_value_WB;
			        		}
	    	            }
	    	        }
	            	
            	}
            	if(ins.getSrc1().isRegister()){
            		if(ins.getSrc1().getRegisterNumber() == output_reg.read().getInstruction().getOper0().getRegisterNumber()){
	        		 source1=output_reg.read().l_alu_output;
            		}else {
            			if(globals.l_maintain_register_WB != null){
            				if(ins.getSrc1().getRegisterNumber() == Integer.parseInt(globals.l_maintain_register_WB.split("R")[1])){
            					source1 = globals.l_maintain_value_WB;
			        		}
            			}
            		}
            	}
            	if(ins.getSrc2().isRegister()){
            		if(ins.getSrc2().getRegisterNumber() == output_reg.read().getInstruction().getOper0().getRegisterNumber()){
	        		 source2=output_reg.read().l_alu_output;
            		}else{
            			if(globals.l_maintain_register_WB != null){
	            			if(ins.getSrc2().getRegisterNumber() == Integer.parseInt(globals.l_maintain_register_WB.split("R")[1])){
	            				source2 = globals.l_maintain_value_WB;
			        		}
            			}
            		}
            	}	
        	}
        	
        	if(ins.getSrc1().isRegister() && ins.getOpcode() != EnumOpcode.STORE){
	        	if(ins.getSrc1().getRegisterNumber() == output_reg.read().getInstruction().getOper0().getRegisterNumber()
	        		&& output_reg.read().getInstruction().getOpcode() != EnumOpcode.STORE){
	        		//System.out.println("output.l_alu_output "+output_reg.read().l_alu_output);
	        		source1=output_reg.read().l_alu_output;
	        	}else {
	        		if(globals.l_maintain_register_WB != null){
		        		if(ins.getSrc1().getRegisterNumber() == Integer.parseInt(globals.l_maintain_register_WB.split("R")[1])
		    	        		&& output_reg.read().getInstruction().getOpcode() != EnumOpcode.STORE){
		        			source1 = globals.l_maintain_value_WB;
		        		}
	        		}
	        	}	
        	}
        	if(ins.getSrc2().isRegister() && ins.getOpcode() != EnumOpcode.STORE){
	        	if(ins.getSrc2().getRegisterNumber() == output_reg.read().getInstruction().getOper0().getRegisterNumber()
	        		&& output_reg.read().getInstruction().getOpcode() != EnumOpcode.STORE){
	        		source2 = output_reg.read().l_alu_output;
	        	}else{
	        		if(globals.l_maintain_register_WB != null){
		        		if(ins.getSrc2().getRegisterNumber() == Integer.parseInt(globals.l_maintain_register_WB.split("R")[1])
		    	        		&& output_reg.read().getInstruction().getOpcode() != EnumOpcode.STORE){
		        			source2 = globals.l_maintain_value_WB;
		        		}
	        		}
	        	}	
        	}
            
            int result = 0;
            if(ins.getOpcode()!= EnumOpcode.LOAD){
	            if(ins.getOpcode()!= EnumOpcode.BRA){
	            	result = MyALU.execute(ins.getOpcode(), source1, source2, oper0);
	            }else{
	            		if(ins.getOpcode()== EnumOpcode.BRA){
			            	int	l_source_bra = 0;
			            	if(ins.getComparison().toString().equals(EnumComparison.LT.toString())){
			            		l_source_bra = -1;
			            	}else{
			            		if(ins.getComparison().toString().equals(EnumComparison.GE.toString())){
			            			l_source_bra = 1;
			            		}else {
			            			if(ins.getComparison().toString().equals(EnumComparison.EQ.toString())){
			            				l_source_bra = 0;
			            			}
			            		}
			            	}
			            	result = MyALU.execute(ins.getOpcode(), oper0, ins.getLabelTarget().getAddress(),l_source_bra);
			            	if(result != -1){
			            		globals.l_brach_taken = true;
			            		globals.program_counter = result-1;
			            	}else {
			            		globals.l_brach_taken = false;
			            	}
	            		}	
	            	}
	            }
            // Fill output with what passes to Memory stage...
            if(ins.getOper0().isRegister() && ins.getOpcode()!= EnumOpcode.BRA){
	            if(ins.getOper0().getRegisterNumber() >=0){ 
	            	if(ins.getOpcode()== EnumOpcode.LOAD){
	        	    	globals.l_load_value_available = true;
	        	    	if(globals.l_set_stall){
	        	    		globals.l_my_next_load = true;
	        	    	}
	        	    }
	            		output.l_alu_output = result;
		            	output.setInstruction(ins);
	            }
            }else{
            	if (ins.getOper0().isRegister()){
                	globals.register_invalid[ins.getOper0().getRegisterNumber()] = false;            	
                }
            }
            if(input.getInstruction().getOpcode() == EnumOpcode.HALT){
            	output.setInstruction(ins);
            }
    }
 }   

    /*** Memory Stage ***/
    static class Memory extends PipelineStageBase<ExecuteToMemory,MemoryToWriteback> {
        public Memory(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public void compute(ExecuteToMemory input, MemoryToWriteback output) {
            InstructionBase ins = input.getInstruction();
            GlobalData globals = (GlobalData)core.getGlobalResources();           
            if (ins.isNull()){
            	globals.l_maintain_register = null;
            	globals.l_maintain_value = 0;
            	return;
            }
            // Access memory...
            
            if(ins.getOpcode() == EnumOpcode.LOAD){
            	if(output_reg.read().getInstruction().getOper0().getRegisterNumber() == ins.getSrc1().getRegisterNumber()){
            		input.l_alu_output = globals.l_Memory_Block[output_reg.read().l_alu_output];
                	globals.l_memory_output = globals.l_Memory_Block[output_reg.read().l_alu_output];
	            }else{
	            	boolean wb_false = true;
	            	if(globals.l_maintain_register_WB != null){
		            	if(ins.getSrc1().getRegisterNumber() == Integer.parseInt(globals.l_maintain_register_WB.split("R")[1])){

		            		input.l_alu_output = globals.l_Memory_Block[globals.l_maintain_value_WB];
		                	globals.l_memory_output = globals.l_Memory_Block[globals.l_maintain_value_WB];
		                	wb_false = false;
		            	}
	            	}
	            	if(wb_false){
	            		input.l_alu_output = globals.l_Memory_Block[ins.getSrc1().getValue()];
	            		globals.l_memory_output = globals.l_Memory_Block[ins.getSrc1().getValue()];
	            	}
	            }
            	
            	globals.l_load_memory_register = "R"+ins.getOper0().getRegisterNumber();
            }
            boolean l_ins_store_load = false;
            if(ins.getOpcode() == EnumOpcode.STORE){
            	if(ins.getSrc1().isRegister() && ins.getOper0().isRegister()){
	            	if(output_reg.read().getInstruction().getOper0().getRegisterNumber() == ins.getOper0().getRegisterNumber()){
	            			globals.l_Memory_Block[input.l_alu_output] = output_reg.read().l_alu_output;
	            	}else{
		            		globals.l_Memory_Block[input.l_alu_output] = ins.getOper0().getValue();
		            }
            	}
                if(ins.getOper0().isRegister()){
                	globals.register_invalid[ins.getOper0().getRegisterNumber()] = false;
                }
                l_ins_store_load = true;
            }
            
            if(ins.getOpcode() == EnumOpcode.OUT){
            	output.l_out_output = globals.l_Memory_Block[ins.getOper0().getValue()];
            }
            
           
        	globals.l_maintain_register = "R"+ ins.getOper0().getValue() ;
            globals.l_maintain_value = input.l_alu_output;
            
            output.l_alu_output =  input.l_alu_output;
            if(!l_ins_store_load){
            	output.setInstruction(ins);
            }	
            // Set other data that's passed to the next stage.
        }
    }
    

    /*** Writeback Stage ***/
    static class Writeback extends PipelineStageBase<MemoryToWriteback,VoidLatch> {
        public Writeback(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public void compute(MemoryToWriteback input, VoidLatch output) {
        	GlobalData globals = (GlobalData)core.getGlobalResources();
        	InstructionBase ins = input.getInstruction();
            if (ins.isNull()){
            	globals.l_maintain_value_WB = 0;
            	globals.l_maintain_register_WB = null;
            	return;
            }

            // Write back result to register file
            if(ins.getOpcode() != EnumOpcode.STORE && ins.getOpcode() != EnumOpcode.LOAD){
	            if (ins.getOper0().isRegister() ){
	            	globals.register_file[ins.getOper0().getRegisterNumber()] = input.l_alu_output;
	            	globals.l_maintain_register_WB = "R"+ ins.getOper0().getRegisterNumber() ;
	 	            globals.l_maintain_value_WB =globals.register_file[ins.getOper0().getRegisterNumber()];
	            }
	            if (ins.getOper0().isRegister()){
	            	globals.register_invalid[ins.getOper0().getRegisterNumber()] = false;            	
	            } 
	           
            }         
            else if(ins.getOpcode() == EnumOpcode.LOAD){
            	if(ins.getSrc1().isRegister() && ins.getOper0().isRegister()){
            		globals.register_file[ins.getOper0().getRegisterNumber()] = input.l_alu_output;
            		globals.l_maintain_register_WB = "R"+ ins.getOper0().getRegisterNumber() ;
	 	            globals.l_maintain_value_WB =globals.register_file[ins.getOper0().getRegisterNumber()];
            	}	
            		if (ins.getOper0().isRegister()){
                    	globals.register_invalid[ins.getOper0().getRegisterNumber()] = false;
                    } 
            }
            if(ins.getOpcode() == EnumOpcode.OUT){
            	System.out.println("@@output: "+input.l_out_output);
            }
            if (input.getInstruction().getOpcode() == EnumOpcode.HALT) {            	
            	// Stop the simulation
            	globals.l_halt = false;
            }
        }
    }
}
