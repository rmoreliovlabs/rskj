/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.rpc.modules.eth;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.EthModuleUtils;
import org.ethereum.vm.program.ProgramResult;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.math.BigInteger;

import static org.junit.Assert.*;

/**
 * Created by patogallaiovlabs on 28/10/2020.
 */
public class EthModuleDSLTest {
    @Test
    public void testCall_getRevertReason() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/eth_module/revert_reason.txt");
        World world = new World();

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        TransactionReceipt transactionReceipt = world.getTransactionReceiptByName("tx02");
        byte[] status = transactionReceipt.getStatus();

        Assert.assertNotNull(status);
        Assert.assertEquals(0, status.length);

        EthModule eth = EthModuleUtils.buildBasicEthModule(world);
        final Transaction tx01 = world.getTransactionByName("tx01");
        final CallArguments args = new CallArguments();
        args.setTo(tx01.getContractAddress().toHexString()); //"6252703f5ba322ec64d3ac45e56241b7d9e481ad";
        args.setData("d96a094a0000000000000000000000000000000000000000000000000000000000000000"); // call to contract with param value = 0
        args.setValue("0");
        args.setNonce("1");
        args.setGas("10000000");
        try {
            eth.call(args, "0x2");
            fail();
        } catch (RskJsonRpcRequestException e) {
            assertThat(e.getMessage(), Matchers.containsString("Negative value."));
        }

        args.setData("d96a094a0000000000000000000000000000000000000000000000000000000000000001"); // call to contract with param value = 1
        final String call = eth.call(args, "0x2");
        assertEquals("0x", call);
    }

    @Test
    public void testEstimateGasUsingCallWithValue() throws FileNotFoundException, DslProcessorException {
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
        args.setValue("10000"); // some value
        args.setNonce("1");
        args.setGas("10000000");

        Block block = world.getBlockChain().getBestBlock();

        // Evaluate the gas used
        long gasUsed = eth.callConstant(args, block).getGasUsed();

        // Estimate the gas to use
        String estimation = eth.estimateGas(args);
        long estimatedGas = Long.parseLong(estimation.substring(2), 16);

        // The estimated gas should be greater than the gas used in the call
        Assert.assertTrue(gasUsed < estimatedGas);

        // Call same transaction with estimated gas
        args.setGas(Long.toString(estimatedGas, 16));

        Assert.assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with gas used should fail
        args.setGas(Long.toString(gasUsed, 16));

        Assert.assertFalse(runWithArgumentsAndBlock(eth, args, block));
    }

    public boolean runWithArgumentsAndBlock(EthModule ethModule, CallArguments args, Block block) {
        ProgramResult res = ethModule.callConstant(args, block);

        return res.getException() == null;
    }

    /**
     * Any transaction that frees storage state, will produce a gas refund (REFUND_SSTORE)
     * */
    @Test
    public void testEstimateGasUsingUpdateStorage() throws FileNotFoundException, DslProcessorException {
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
        args.setValue("0");
        args.setNonce("1");
        args.setGas("10000000");
        args.setData("7b8d56e3" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000000"); // setValue(1,0)
        long clearStorageGasUsed = eth.callConstant(args, block).getGasUsed();
        long clearStoreageEstimatedGas = Long.parseLong(eth.estimateGas(args).substring(2), 16);

        // The estimated gas should be less than the gas used for initializing a storage cell
        assertTrue(clearStoreageEstimatedGas < initStorageGasUsed);
        assertTrue(clearStorageGasUsed < initStorageGasUsed);
        assertEquals(clearStoreageEstimatedGas, clearStorageGasUsed);

        // Call same transaction with estimated gas
        args.setGas(Long.toString(clearStoreageEstimatedGas, 16));
        assertTrue(runWithArgumentsAndBlock(eth, args, block));

        // Call same transaction with estimated gas minus 1
        args.setGas(Long.toString(clearStoreageEstimatedGas - 1, 16));
        assertFalse(runWithArgumentsAndBlock(eth, args, block));

        // estimate gas for updating a storage cell from non-zero to non-zero
        args.setGas("10000000");
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

        // estimate gas for initializing another storage cell
        args.setGas("10000000");
        args.setData("7b8d56e3" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "0000000000000000000000000000000000000000000000000000000000000001"); // setValue(2,1)
        long anotherInitStorageGasUsed = eth.callConstant(args, block).getGasUsed();
        long anotherInitStorageEstimatedGas = Long.parseLong(eth.estimateGas(args).substring(2), 16);

        // the estimated gas should be equal to tx02 gas used
        assertEquals(initStorageGasUsed, anotherInitStorageGasUsed);
        assertEquals(anotherInitStorageEstimatedGas, anotherInitStorageGasUsed);
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

        Long tonsOfGas = gasEstimationCap + 1000000000;

        CallArguments callArguments = new CallArguments();
        callArguments.setFrom(sender); // the creator
        callArguments.setTo(contractAddress);  // deployed contract
        callArguments.setGas(tonsOfGas.toString()); // exceeding the gas cap
        callArguments.setData("31fe52e8"); // call outOfGas()

        String estimatedGas = eth.estimateGas(callArguments);

        Assert.assertEquals(gasEstimationCap, Long.decode(estimatedGas).longValue());
    }
}
