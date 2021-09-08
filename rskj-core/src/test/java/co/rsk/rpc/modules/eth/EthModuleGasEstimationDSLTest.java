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

    @Test
    public void testEstimateGas_contractCallsWithValueTransfer() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/callWithValue.txt");

        // Deploy Check
        TransactionReceipt deployTransactionReceipt = world.getTransactionReceiptByName("tx01");
        byte[] status = deployTransactionReceipt.getStatus();
        RskAddress contractAddress = deployTransactionReceipt.getTransaction().getContractAddress();

        Assert.assertNotNull(status);
        Assert.assertEquals(1, status.length);
        Assert.assertEquals(0x01, status[0]);
        Assert.assertEquals("6252703f5ba322ec64d3ac45e56241b7d9e481ad", contractAddress.toHexString());

        // Call with value estimation
        EthModule eth = EthModuleUtils.buildBasicEthModule(world);

        final CallArguments args = new CallArguments();
        args.setTo(contractAddress.toHexString());
        args.setData("c3cefd36"); // callWithValue()
        args.setValue(TypeConverter.toQuantityJsonHex(10000)); // some value
        args.setNonce(TypeConverter.toQuantityJsonHex(1));
        args.setGas(TypeConverter.toQuantityJsonHex(10000000));

        Block block = world.getBlockChain().getBestBlock();

        // Evaluate the gas used
        long gasUsed = eth.callConstant(args, block).getGasUsed();

        // Estimate the gas to use
        String estimation = eth.estimateGas(args);
        long estimatedGas = Long.parseLong(estimation.substring(2), 16);

        // The estimated gas should be greater than the gas used in the call
        Assert.assertTrue(gasUsed < estimatedGas);

        // Call same transaction with estimated gas
        args.setGas(TypeConverter.toQuantityJsonHex(estimatedGas));

        Assert.assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with gas used should fail
        args.setGas(TypeConverter.toQuantityJsonHex(gasUsed));

        Assert.assertFalse(runWithArgumentsAndBlock(eth, args, block));
    }

    @Test
    public void testEstimateGas_storageRefunds() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/updateStorage.txt");

        TransactionReceipt deployTransactionReceipt = world.getTransactionReceiptByName("tx01");
        String contractAddress = deployTransactionReceipt.getTransaction().getContractAddress().toHexString();
        byte[] status = deployTransactionReceipt.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);

        TransactionReceipt initStorageTransactionReceipt = world.getTransactionReceiptByName("tx02");
        byte[] status2 = initStorageTransactionReceipt.getStatus();
        long initStorageGasUsed = new BigInteger(1, initStorageTransactionReceipt.getGasUsed()).longValue();

        assertNotNull(status2);
        assertEquals(1, status2.length);
        assertEquals(0x01, status2[0]);

        EthModule eth = EthModuleUtils.buildBasicEthModule(world);
        Block block = world.getBlockChain().getBestBlock();

        // from non-zero to zero - setValue(1, 0) - it should have a refund
        final CallArguments args = new CallArguments();
        args.setTo(contractAddress); // "6252703f5ba322ec64d3ac45e56241b7d9e481ad";
        args.setValue(TypeConverter.toQuantityJsonHex(0));
        args.setNonce(TypeConverter.toQuantityJsonHex(1));
        args.setGas(TypeConverter.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("7b8d56e3" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000000"); // setValue(1,0)

        ProgramResult callConstantResult = eth.callConstant(args, block);

        long clearStorageGasUsed = callConstantResult.getGasUsed();
        long clearStoreageEstimatedGas = Long.parseLong(eth.estimateGas(args).substring(2), 16);

        assertTrue( 0 < clearStorageGasUsed && clearStorageGasUsed < initStorageGasUsed);
        assertTrue(clearStoreageEstimatedGas < initStorageGasUsed);
        assertTrue(clearStoreageEstimatedGas > clearStorageGasUsed);
        assertEquals(clearStoreageEstimatedGas,
                clearStorageGasUsed + callConstantResult.getDeductedRefund());

        // Call same transaction with estimated gas
        args.setGas(TypeConverter.toQuantityJsonHex(clearStoreageEstimatedGas));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas minus 1
        args.setGas(TypeConverter.toQuantityJsonHex(clearStoreageEstimatedGas - 1));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        // estimate gas for updating a storage cell from non-zero to non-zero
        args.setGas(TypeConverter.toQuantityJsonHex(BLOCK_GAS_LIMIT));
        args.setData("7b8d56e3" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000001"); // setValue(1,1)
        long updateStorageGasUsed = eth.callConstant(args, block).getGasUsed();
        long updateStoreageEstimatedGas = Long.parseLong(eth.estimateGas(args).substring(2), 16);

        // The estimated gas should be less than the gas used gas for initializing a storage cell
        assertTrue(updateStorageGasUsed < initStorageGasUsed);
        assertTrue(updateStoreageEstimatedGas < initStorageGasUsed);
        assertEquals(updateStoreageEstimatedGas, updateStorageGasUsed);

        // Call same transaction with estimated gas
        args.setGas(Long.toString(updateStoreageEstimatedGas, 16));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas minus 1
        args.setGas(Long.toString(updateStoreageEstimatedGas - 1, 16));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        // Check against another already initialized (2,42) storage cell
        TransactionReceipt anotherInitStorageTransactionReceipt = world.getTransactionReceiptByName("tx02");
        byte[] status3 = anotherInitStorageTransactionReceipt.getStatus();
        long anotherInitStorageGasUsed = new BigInteger(1, anotherInitStorageTransactionReceipt.getGasUsed()).longValue();

        assertNotNull(status3);
        assertEquals(1, status3.length);
        assertEquals(0x01, status3[0]);

        // Change this storage cell to zero and compare
        args.setData("7b8d56e3" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "0000000000000000000000000000000000000000000000000000000000000000");
        args.setGas(TypeConverter.toQuantityJsonHex(BLOCK_GAS_LIMIT));

        ProgramResult anotherCallConstantResult = eth.callConstant(args, block);
        long anotherClearStorageGasUsed = anotherCallConstantResult.getGasUsed();
        long anotherClearStorageEstimatedGas = Long.parseLong(eth.estimateGas(args).substring("0x".length()), 16);

        assertEquals(initStorageGasUsed, anotherInitStorageGasUsed);
        assertEquals(clearStoreageEstimatedGas, anotherClearStorageEstimatedGas);
        assertEquals(clearStorageGasUsed, anotherClearStorageGasUsed);
    }

    @Test
    public void estimateGas_gasCap() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/eth_module/estimateGas/gasCap.txt");

        TransactionReceipt deployTransactionReceipt = world.getTransactionReceiptByName("tx01");
        String sender = deployTransactionReceipt.getTransaction().getSender().toHexString();
        String contractAddress = deployTransactionReceipt.getTransaction().getContractAddress().toHexString();
        byte[] status = deployTransactionReceipt.getStatus();

        assertNotNull(status);
        assertEquals(1, status.length);
        assertEquals(0x01, status[0]);

        EthModule eth = EthModuleUtils.buildBasicEthModule(world);
        long gasEstimationCap = new TestSystemProperties().getGasEstimationCap();

        CallArguments callArguments = new CallArguments();
        callArguments.setFrom(sender); // the creator
        callArguments.setTo(contractAddress);  // deployed contract
        callArguments.setGas(TypeConverter.toQuantityJsonHex(gasEstimationCap + 1000000000)); // exceeding the gas cap
        callArguments.setData("31fe52e8"); // call outOfGas()

        String estimatedGas = eth.estimateGas(callArguments);

        Assert.assertEquals(gasEstimationCap, Long.decode(estimatedGas).longValue());
    }
}
