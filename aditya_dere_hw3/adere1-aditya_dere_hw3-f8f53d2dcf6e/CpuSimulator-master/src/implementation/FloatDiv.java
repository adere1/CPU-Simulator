package implementation;

import utilitytypes.ICpuCore;
import utilitytypes.IGlobals;
import baseclasses.CpuCore;
import baseclasses.InstructionBase;
import baseclasses.Latch;
import baseclasses.PipelineStageBase;
import implementation.MyCpuCore;

public class FloatDiv extends PipelineStageBase {
	public FloatDiv(ICpuCore core) {
        super(core, "FloatDiv");
    }
	
	@Override
    public void compute(Latch input, Latch output) {
		int set_counter_fdiv = 0;
		if (input.isNull()) return;
        doPostedForwarding(input);
        InstructionBase ins = input.getInstruction();

        float source1 = ins.getSrc1().getFloatValue();
        float source2 = ins.getSrc2().getFloatValue();
        float result = source1/source2;
        IGlobals globals = (GlobalData)getCore().getGlobals();
        if(GlobalData.Counter1 < 15){
        	GlobalData.Counter1++;
        	setResourceWait("Float Div 16-Cycles-Non Pipeline");
        }else {
        	GlobalData.Counter1 = 0;
        }
        output.setResultFloatValue(result);
        output.setInstruction(ins);
    }	
	
}
