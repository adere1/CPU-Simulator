/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import baseclasses.InstructionBase;
import baseclasses.LatchBase;

/**
 * Definitions of latch contents for pipeline registers.  Pipeline registers
 * create instances of these for passing data between pipeline stages.
 * 
 * AllMyLatches is merely to collect all of these classes into one place.
 * It is not necessary for you to do it this way.
 * 
 * You must fill in each latch type with the kind of data that passes between
 * pipeline stages.
 * 
 * @author 
 */
public class AllMyLatches {
    public static class FetchToDecode extends LatchBase {
        // LatchBase already includes a field for the instruction.
    	public static InstructionBase ins1 ; 
    }
    
    public static class DecodeToExecute extends LatchBase {
        // LatchBase already includes a field for the instruction.
        // What else do you need here?
    	
        public static int [] register_saved = new int[32];
        //public static InstructionBase ins1 ;
        //public static boolean [] invalid_register_saved = new boolean[32];
    }
    
    public static class ExecuteToMemory extends LatchBase {
        // LatchBase already includes a field for the instruction.
        // What do you need here?
    	 public static int [] register_saved = new int[32];
    	 public int l_alu_output;
    	 public int l_store_output;
    	 public int l_load_output;
    	 //public static boolean [] invalid_register_saved = new boolean[32];
    }

    public static class MemoryToWriteback extends LatchBase {
        // LatchBase already includes a field for the instruction.
        // What do you need here?
    	public static int [] register_saved = new int[32];
    	public int l_alu_output;
    	public int l_store_output;
   	 	public int l_load_output;
   	 	public int l_out_output;
    	//public static boolean [] invalid_register_saved = new boolean[32];
    }    
}
