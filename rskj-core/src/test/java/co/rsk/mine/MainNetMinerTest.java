/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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
package co.rsk.mine;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.ConfigUtils;
import co.rsk.config.MiningConfig;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockChainImplTest;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.MiningMainchainView;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.db.RepositoryLocator;
import co.rsk.mine.gas.provider.FixedMinGasPriceProvider;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.BlockUnclesValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.db.BlockStore;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.RskTestFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Created by SerAdmin on 1/3/2018.
 */
class MainNetMinerTest {

    @TempDir
    public Path tempDir;

    private TestSystemProperties config;
    private MiningMainchainView mainchainView;
    private TransactionPool transactionPool;
    private BlockStore blockStore;
    private NodeBlockProcessor blockProcessor;
    private RepositoryLocator repositoryLocator;
    private BlockFactory blockFactory;
    private BlockExecutor blockExecutor;

    @BeforeEach
    void setup() {
        config = spy(new TestSystemProperties());
        when(config.getNetworkConstants()).thenReturn(Constants.mainnet());
        when(config.getActivationConfig()).thenReturn(ActivationConfigsForTest.all());
        RskTestFactory factory = new RskTestFactory(tempDir, config) {
            @Override
            public GenesisLoader buildGenesisLoader() {
                return new TestGenesisLoader(getTrieStore(), "rsk-unittests.json", BigInteger.ZERO, true, true, true) {
                    @Override
                    public Genesis load() {
                        Genesis genesis = super.load();
                        genesis.getHeader().setDifficulty(new BlockDifficulty(BigInteger.valueOf(300000)));
                        return genesis;
                    }
                };
            }
        };
        mainchainView = factory.getMiningMainchainView();
        transactionPool = factory.getTransactionPool();
        blockStore = factory.getBlockStore();
        blockProcessor = factory.getNodeBlockProcessor();
        repositoryLocator = factory.getRepositoryLocator();
        blockFactory = factory.getBlockFactory();
        blockExecutor = factory.getBlockExecutor();
    }

    /*
     * This test is probabilistic, but it has a really high chance to pass. We will generate
     * a random block that it is unlikely to pass the Long.MAX_VALUE difficulty, though
     * it may happen once. Twice would be suspicious.
     */
    @Test
    void submitBitcoinBlockProofOfWorkNotGoodEnough() {
        /* We need a low target */
        BlockChainBuilder blockChainBuilder = new BlockChainBuilder();
        BlockChainImpl blockchain = blockChainBuilder.build();
        Genesis gen = (Genesis) BlockChainImplTest.getGenesisBlock(blockChainBuilder.getTrieStore());
        gen.getHeader().setDifficulty(new BlockDifficulty(BigInteger.valueOf(Long.MAX_VALUE)));
        blockchain.setStatus(gen, gen.getCumulativeDifficulty());

        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                mainchainView,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                blockToMineBuilder(),
                clock,
                blockFactory,
                new BuildInfo("cb7f28e", "master"),
                ConfigUtils.getDefaultMiningConfig()
        );
        try {
            minerServer.start();
            MinerWork work = minerServer.getWork();

            co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlock(work);

            bitcoinMergedMiningBlock.setNonce(2);

            SubmitBlockResult result = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

            Assertions.assertEquals("ERROR", result.getStatus());
            Assertions.assertNull(result.getBlockInfo());

            Mockito.verify(ethereumImpl, Mockito.times(0)).addNewMinedBlock(Mockito.any());
        } finally {
            minerServer.stop();
        }
    }

    /*
     * This test is much more likely to fail than the
     * submitBitcoinBlockProofOfWorkNotGoodEnough test. Even then
     * it should almost never fail.
     */
    @Test
    void submitBitcoinBlockInvalidBlockDoesntEliminateCache() {
        //////////////////////////////////////////////////////////////////////
        // To make this test work we need a special network spec with
        // medium minimum difficulty (this is not the mainnet nor the regnet)
        ////////////////////////////////////////////////////////////////////
        /* We need a low, but not too low, target */

        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        when(ethereumImpl.addNewMinedBlock(Mockito.any())).thenReturn(ImportResult.IMPORTED_BEST);

        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                mainchainView,
                blockProcessor,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                blockToMineBuilder(),
                clock,
                blockFactory,
                new BuildInfo("cb7f28e", "master"),
                ConfigUtils.getDefaultMiningConfig()
        );
        try {
        minerServer.start();
        MinerWork work = minerServer.getWork();

        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlock(work);

        bitcoinMergedMiningBlock.setNonce(1);

        // Try to submit a block with invalid PoW, this should not eliminate the block from the cache
        SubmitBlockResult result1 = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

        Assertions.assertEquals("ERROR", result1.getStatus());
        Assertions.assertNull(result1.getBlockInfo());
        Mockito.verify(ethereumImpl, Mockito.times(0)).addNewMinedBlock(Mockito.any());

        // Now try to submit the same block, this should work fine since the block remains in the cache

        // This WON't work in mainnet because difficulty is HIGH
        /*---------------------------------------------------------
        findNonce(work, bitcoinMergedMiningBlock);

        SubmitBlockResult result2 = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

        Assertions.assertEquals("OK", result2.getStatus());
        Assertions.assertNotNull(result2.getBlockInfo());
        Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(Mockito.any());

        // Finally, submit the same block again and validate that addNewMinedBlock is called again
        SubmitBlockResult result3 = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

        Assertions.assertEquals("OK", result3.getStatus());
        Assertions.assertNotNull(result3.getBlockInfo());
        Mockito.verify(ethereumImpl, Mockito.times(2)).addNewMinedBlock(Mockito.any());
        -------------------------------*/
        } finally {
            minerServer.stop();
        }
    }

    private co.rsk.bitcoinj.core.BtcBlock getMergedMiningBlock(MinerWork work) {
        NetworkParameters bitcoinNetworkParameters = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, work);
        return MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);
    }

    private BlockToMineBuilder blockToMineBuilder() {
        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MiningConfig miningConfig = ConfigUtils.getDefaultMiningConfig();
        return new BlockToMineBuilder(
                config.getActivationConfig(),
                miningConfig,
                repositoryLocator,
                blockStore,
                transactionPool,
                new DifficultyCalculator(config.getActivationConfig(), config.getNetworkConstants()),
                new GasLimitCalculator(config.getNetworkConstants()),
                new ForkDetectionDataCalculator(),
                unclesValidationRule,
                clock,
                blockFactory,
                blockExecutor,
                new MinimumGasPriceCalculator(new FixedMinGasPriceProvider(config.minerMinGasPrice())),
                new MinerUtils(),
                new BlockTxSignatureCache(new ReceivedTxSignatureCache())
        );
    }
}