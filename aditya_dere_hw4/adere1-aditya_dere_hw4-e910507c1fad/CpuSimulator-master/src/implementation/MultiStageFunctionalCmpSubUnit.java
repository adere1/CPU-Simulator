package implementation;

import tools.MultiStageDelayUnit;
import utilitytypes.EnumOpcode;
import utilitytypes.IFunctionalUnit;
import utilitytypes.IModule;
import baseclasses.FunctionalUnitBase;
import baseclasses.InstructionBase;
import baseclasses.Latch;
import baseclasses.PipelineStageBase;


public class MultiStageFunctionalCmpSubUnit extends FunctionalUnitBase{
	   public MultiStageFunctionalCmpSubUnit(IModule parent, String name) {
	        super(parent, name);
	    }

	    private static class MyMathUnit extends PipelineStageBase {
	        public MyMathUnit(IModule parent) {
	            // For simplicity, we just call this stage "in".
	            super(parent, "in");
//	            super(parent, "in:Math");  // this would be fine too
	        }
	        
	        @Override
	        public void compute(Latch input, Latch output) {
	            if (input.isNull()) return;
	            doPostedForwarding(input);
	            InstructionBase ins = input.getInstruction();
	            float source1 = ins.getSrc1().getFloatValue();
	            float source2 = ins.getSrc2().getFloatValue();
                
	            float result;
	            
	            if(ins.getOpcode() == EnumOpcode.FADD) {
	            	result = source1 + source2;
	            }else {
	            	result = source1 - source2;
	            }            
	            output.setResultFloatValue(result);
	            output.setInstruction(ins);
	        }
	    }
	    
	    @Override
	    public void createPipelineRegisters() {
	        createPipeReg("MathToDelay");  
	    }

	    @Override
	    public void createPipelineStages() {
	        addPipeStage(new MyMathUnit(this));
	    }

	    @Override
	    public void createChildModules() {
	        IFunctionalUnit child = new MultiStageDelayUnit(this, "Delay", 5);
	        addChildUnit(child);
	        
	    }

	    @Override
	    public void createConnections() {
	    	addRegAlias("Delay.out", "out");
	        connect("in", "MathToDelay", "Delay");
	    }

	    @Override
	    public void specifyForwardingSources() {
	        addForwardingSource("out");
	    }

}
