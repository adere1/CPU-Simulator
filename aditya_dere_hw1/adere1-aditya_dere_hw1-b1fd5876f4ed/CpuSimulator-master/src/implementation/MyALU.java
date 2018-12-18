/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import java.util.BitSet;

import utilitytypes.EnumOpcode;

/**
 * The code that implements the ALU has been separates out into a static
 * method in its own class.  However, this is just a design choice, and you
 * are not required to do this.
 * 
 * @author 
 */
public class MyALU {
    static int execute(EnumOpcode opcode, int input1, int input2, int oper0) {
        int result = 0;
        
        // Implement code here that performs appropriate computations for
        // any instruction that requires an ALU operation.  See
        // EnumOpcode.
        
        /*ADD, SUB, AND, OR, SHL, ASR, LSR, XOR, CMP, ROL, ROR,
        MULS, MULU, DIVS, DIVU,
        BRA, JMP, CALL, 
        LOAD, STORE, MOVC, OUT,
        HALT, NOP, INVALID, NULL;
        */
        
        //GlobalData globals = (GlobalData)core.getGlobalResources();
        
        switch(opcode.toString()){
        case "ADD" :     
        	       // System.out.println("Add "+input1 + " "+input2);    
        			result = input1 + input2;
        			break
        ;
        case "SUB" : 
			result = input1 - input2;
			break
		;
        case "AND" : 
			result = input1 & input2;
			break
		;	
        case "SHL" : 
			result = input1 << input2;
			break
		;
        case "ASR" : 
			result = input1 >> input2;
			break
		;
        case "LSR" : 
			result = input1 << input2;
			break
		;
        case "XOR" : 
			result = input1 ^ input2;
			break
		;
        case "CMP" : 
        	//System.out.println("Inside CMP");
			 if(input1 == input2){
				 result = 0;
			 }	 
			 else{
				if(input1 < input2){ 
					result = -1;
				}	
				else{
					if(input1 > input2){
						result = 1;
					}	
				}	
			 }
			break
		;
       /* case "ROL" : 
			result = input1 | input2;
			break
		;*/
        case "MULS" : 
			result = input1 * input2;
			break
		;
        case "MULU" : 
			result = input1 * input2;
			break
		;
        case "DIVS" : 
			result = input1 / input2;
			break
		;
			
        case "DIVU" : 
			result = input1 / input2;
			break
		;	
        
        case "MOVC":
        	//System.out.println("inside movc");
        	result = input1;
              break
        ; 
              
        case  "BRA":
        	//System.out.println(" opcode "+opcode+ " oper0 "+oper0);
        	
        	if(oper0 == -1){
        		if(input1 == -1){
        			result =  input2;
        		}else{
        			result =  -1;
        		}
        	}
        	if(oper0 == 1){
        		
        		//System.out.println("input1" + input1);
        		
        		if(input1 >= 1){
        			result =  input2;
        		}else{
        				
        				result =  -1;
        			}	
        		
        	}
        	if(oper0 == 0){
        		if(input1 == 0){
        			result =  input2;
        		}else{
        			result = -1;
        		}
        	}
        	break
        ;
        	
        case "STORE":
        	result = input1+input2;
        break;	
             	

        }
        
        return result;
    }    
}
