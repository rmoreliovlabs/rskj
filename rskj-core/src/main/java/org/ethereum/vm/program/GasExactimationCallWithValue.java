package org.ethereum.vm.program;

import org.ethereum.vm.GasCost;

/**
 * This kind of ProgramResult it's useful to estimate contract calls with value transfer,
 * it adds the stipend gas cost to the final gasUsed
 * */
public class GasExactimationCallWithValue extends ProgramResult {
    public GasExactimationCallWithValue(ProgramResult result) {
        this.addDeductedRefund(result.getDeductedRefund());
        this.setGasUsed(result.getGasUsed());
    }

    @Override
    public long getGasUsed() {
        return super.getGasUsed() + GasCost.STIPEND_CALL;
    }
}
