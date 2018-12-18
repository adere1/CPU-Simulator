package implementation;

import static utilitytypes.IProperties.MAIN_MEMORY;
import tools.MultiStageDelayUnit;
import tools.PassthroughPipeStage;
import utilitytypes.IFunctionalUnit;
import utilitytypes.IGlobals;
import utilitytypes.IModule;
import utilitytypes.Operand;
import baseclasses.FunctionalUnitBase;
import baseclasses.InstructionBase;
import baseclasses.Latch;
import baseclasses.PipelineStageBase;

public class MemUnit extends FunctionalUnitBase{
	public MemUnit(IModule parent, String name) {
        super(parent, name);
    }

    private static class Adder extends PipelineStageBase {
        public Adder(IModule parent) {
            // For simplicity, we just call this stage "in".
            super(parent, "in:Addr");
//            super(parent, "in:Math");  // this would be fine too
        }
        @Override
        public void compute(Latch input, Latch output) {
            if (input.isNull()) return;
            doPostedForwarding(input);
            InstructionBase ins = input.getInstruction();
            int source1 = ins.getSrc1().getValue();
            int source2 = ins.getSrc2().getValue();
            int result = source1 + source2;
            //output.setResultValue(result);
            output.setProperty("address", result);
            output.setInstruction(ins);
        }
    }
    
    private static class LSQ extends PipelineStageBase {
        public LSQ(IModule parent) {
            // For simplicity, we just call this stage "in".
            super(parent, "LSQ");
//            super(parent, "in:Math");  // this would be fine too
        }
        @Override
        public void compute(Latch input, Latch output) {
            if (input.isNull()) return;
            doPostedForwarding(input);
            InstructionBase ins = input.getInstruction();
            //addStatusWord("Addr="+ins.getSrc1().getValue());
            this.addStatusWord("Addr=" + input.getPropertyInteger("address"));
            output.setInstruction(ins);
            output.copyAllPropertiesFrom(input);
        }
    }
	
    private static class Dcache extends PipelineStageBase {
        public Dcache(IModule parent) {
            // For simplicity, we just call this stage "in".
            super(parent, "DCache");
//            super(parent, "in:Math");  // this would be fine too
        }
        @Override
        public void compute(Latch input, Latch output) {
            if (input.isNull()) return;
            int value = 0;
            InstructionBase ins = input.getInstruction();
            IGlobals globals = (GlobalData)getCore().getGlobals();
            int[] memory = globals.getPropertyIntArray(MAIN_MEMORY);
            //int addr = input.getPropertyInteger("result_value");
              int addr = ins.getSrc1().getValue();
            switch (ins.getOpcode()) {
                case LOAD:
                    // Fetch the value from main memory at the address
                    // retrieved above.
                    value = memory[addr];
                    output.setResultValue(value);
                    output.setInstruction(ins);
                    addStatusWord(ins.getOper0().getRegisterName() +"="+"Mem[" + addr + "]");
                    break;
                
                case STORE:
                    // For store, the value to be stored in main memory is
                    // in oper0, which was fetched in Decode.
                    memory[addr] = ins.getOper0().getValue();
                    addStatusWord("Mem[" + addr + "]=" + ins.getOper0().getValueAsString());
                    return;
                    
                default:
                    throw new RuntimeException("Non-memory instruction got into Memory stage");
            }

        }
    }
    
	@Override
	public void createPipelineRegisters() {
		// TODO Auto-generated method stub
		createPipeReg("AdderToLSQ"); 
		createPipeReg("LSQToDcache"); 
		createPipeReg("DcacheToOut"); 
	}

	@Override
	public void createPipelineStages() {
		// TODO Auto-generated method stub
		addPipeStage(new Adder(this));
		addPipeStage(new LSQ(this));
		addPipeStage(new Dcache(this));
	}

	@Override
	public void createChildModules() {
		// TODO Auto-generated method stub
		//IFunctionalUnit child = new MultiStageDelayUnit(this, "Delay", 5);
        //addChildUnit(child);
		
	}

	@Override
	public void createConnections() {
		// TODO Auto-generated method stub
		connect("in:Addr", "AdderToLSQ", "LSQ");
		connect("LSQ", "LSQToDcache", "DCache");
		addRegAlias("DcacheToOut", "out");
		connect("DCache", "DcacheToOut");
	}

	@Override
	public void specifyForwardingSources() {
		// TODO Auto-generated method stub
		//addForwardingSource("DcacheToOut");
		addForwardingSource("out");
	}
}
