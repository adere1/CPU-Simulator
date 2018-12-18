package implementation;

import utilitytypes.ICpuCore;
import baseclasses.InstructionBase;
import baseclasses.Latch;
import baseclasses.PipelineStageBase;

public class IntDiv extends PipelineStageBase {
	public IntDiv(ICpuCore core) {
        super(core, "IntDiv");
    }
	
	@Override
    public void compute(Latch input, Latch output) {
        if (input.isNull()) return;
        doPostedForwarding(input);
        InstructionBase ins = input.getInstruction();

        int source1 = ins.getSrc1().getValue();
        int source2 = ins.getSrc2().getValue();
        int oper0 =   ins.getOper0().getValue();
        
        int result = source1/source2;
        
        if(GlobalData.Counter2 < 16){        	
        	setResourceWait("Loop"+GlobalData.Counter1);
        	GlobalData.Counter2++;
        }else {
        	GlobalData.Counter2 = 1;
        }
        
        boolean isfloat = ins.getSrc1().isFloat() || ins.getSrc2().isFloat();
        output.setResultValue(result, isfloat);
        output.setInstruction(ins);
    }	
	
}
