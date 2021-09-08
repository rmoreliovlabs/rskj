package co.rsk.rpc.modules.eth;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.test.World;
import co.rsk.test.dsl.DslProcessorException;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.EthModuleUtils;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.program.ProgramResult;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.math.BigInteger;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class EthModuleGasEstimationDSLTest {

    public static final long BLOCK_GAS_LIMIT = new TestSystemProperties().getTargetGasLimit();

    @Test
    public void estimateGas_callWithValuePlusSStoreRefund() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/callWithValuePlusSstoreRefund.txt");

        TransactionReceipt deployValueTransferContract = world.getTransactionReceiptByName("tx01");
        String valueTransferContractAddress = deployValueTransferContract.getTransaction().getContractAddress().toHexString();
        byte[] status = deployValueTransferContract.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);

        TransactionReceipt deploySStoreClear = world.getTransactionReceiptByName("tx02");
        String sStoreClearContractAddress = deploySStoreClear.getTransaction().getContractAddress().toHexString();
        byte[] status2 = deployValueTransferContract.getStatus();

        assertNotNull(status2);
        assertEquals(1, status2.length);
        assertEquals(0x01, status2[0]);

        TransactionReceipt initStorageTransaction = world.getTransactionReceiptByName("tx03");
        long initStorageGasUsed = ByteUtil.byteArrayToLong(initStorageTransaction.getGasUsed());
        byte[] status3 = deployValueTransferContract.getStatus();

        assertNotNull(status3);
        assertEquals(1, status3.length);
        assertEquals(0x01, status3[0]);

        EthModule eth = EthModuleUtils.buildBasicEthModule(world);
        Block block = world.getBlockChain().getBestBlock();

        // check if storage it's initialized by calling isStorageInit function
        final CallArguments args = new CallArguments();
        args.setTo(valueTransferContractAddress); // "56aa252dd82173789984fa164ee26ce2da9336ff";
        args.setValue(TypeConverter.toQuantityJsonHex(0));
        args.setNonce(TypeConverter.toQuantityJsonHex(3));
        args.setGas(TypeConverter.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("26d7ae02"); // isStorageInit()

//        eth.callConstant(args, block);

        // call the first contract, it should call and transfer 1 eth to the SStoreClearContract,
        // then that contract will free a storage cell and emit two events
        args.setTo(valueTransferContractAddress); // "56aa252dd82173789984fa164ee26ce2da9336ff";
        args.setValue(TypeConverter.toQuantityJsonHex(1));
        args.setNonce(TypeConverter.toQuantityJsonHex(4));
        args.setGas(TypeConverter.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("00c6f1e6000000000000000000000000" +
                sStoreClearContractAddress); // callSstoreClearWithValue("56aa252dd82173789984fa164ee26ce2da9336ff")

        ProgramResult clearStoragePlusCallWithValue = eth.callConstant(args, block);
        long clearStoragePlusCallWithValueGasUsed = clearStoragePlusCallWithValue.getGasUsed();
        long clearStoreageEstimatedGas = Long.parseLong(eth.estimateGas(args).substring("0x".length()), 16);

        assertTrue( 0 < clearStoragePlusCallWithValueGasUsed && clearStoragePlusCallWithValueGasUsed < initStorageGasUsed);
        assertTrue(clearStoreageEstimatedGas > clearStoragePlusCallWithValueGasUsed);
        assertEquals(clearStoreageEstimatedGas, // todo(fedejinich) according to this tests, gasDeductingRefund it's omitting the stipend cost
                clearStoragePlusCallWithValueGasUsed + clearStoragePlusCallWithValue.getDeductedRefund() + GasCost.STIPEND_CALL);
    }

    public boolean runWithArgumentsAndBlock(EthModule ethModule, CallArguments args, Block block) {
        ProgramResult res = ethModule.callConstant(args, block);

        return res.getException() == null;
    }
}
