package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutPoint;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.RedeemData;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ErpFederationTest {
    private ErpFederation federation;

    // ERP federation keys
    private static final List<BtcECKey> ERP_FED_KEYS = Arrays.stream(new String[]{
        "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
        "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
        "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
        "03776b1fd8f86da3c1db3d69699e8250a15877d286734ea9a6da8e9d8ad25d16c1",
        "03ab0e2cd7ed158687fc13b88019990860cdb72b1f5777b58513312550ea1584bc"
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

    private static final long ACTIVATION_DELAY_VALUE = 5063;

    @Before
    public void createErpFederation() {
        federation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            ERP_FED_KEYS,
            ACTIVATION_DELAY_VALUE
        );
    }

    @Test
    public void getErpPubKeys() {
        Assert.assertEquals(ERP_FED_KEYS, federation.getErpPubKeys());
    }

    @Test
    public void getActivationDelay() {
        Assert.assertEquals(ACTIVATION_DELAY_VALUE, federation.getActivationDelay());
    }

    @Test
    public void getRedeemScript() {
        Script redeemScript = federation.getRedeemScript();
        Assert.assertEquals(19, redeemScript.getChunks().size());

        // First element: OP_0 - Belonging to the standard of BTC
        // M elements OP_0 - Belonging to M/N amount of signatures
        // OP_0 - Belonging to ERP
        // Last element: Program of redeem script
        String expectedProgram = "64522102ed3bace23c5e17652e174c835fb72bf53ee306b3406a26890221b4ce"
            + "f7500f88210385a7b790fc9d962493788317e4874a4ab07f1e9c78c773c47f2f6c96df756f052103cd5"
            + "a3be41717d65683fe7a9de8ae5b4b8feced69f26a8b55eeefbcc2e74b75fb53670213c7b2755321029c"
            + "ecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e433012103284178e5fbcc63c54"
            + "c3b38e3ef88adf2da6c526313650041b0ef955763634ebd2103776b1fd8f86da3c1db3d69699e8250a1"
            + "5877d286734ea9a6da8e9d8ad25d16c12103ab0e2cd7ed158687fc13b88019990860cdb72b1f5777b58"
            + "513312550ea1584bc2103b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b"
            + "245568ae";

        Assert.assertEquals(expectedProgram, Hex.toHexString(redeemScript.getProgram()));
    }

    @Test
    public void getP2SHScript() {
        Script p2shs = federation.getP2SHScript();
        String expectedProgram = "a914bbb7b7942d0fb850bd619b399e96d8b8b36ff89187";

        Assert.assertEquals(expectedProgram, Hex.toHexString(p2shs.getProgram()));
        Assert.assertEquals(3, p2shs.getChunks().size());
        Assert.assertEquals(
            federation.getAddress(),
            p2shs.getToAddress(NetworkParameters.fromID(NetworkParameters.ID_REGTEST))
        );
    }

    @Test
    public void getAddress() {
        String fedAddress = federation.getAddress().toBase58();
        String expectedAddress = "2NAMnS3XpcWw1KrYkszRw7gWHFkxuMYrU2Z";

        Assert.assertEquals(expectedAddress, fedAddress);
    }

    @Test
    public void getErpPubKeys_compressed_public_keys() {
        Assert.assertEquals(ERP_FED_KEYS, federation.getErpPubKeys());
    }

    @Test
    public void getErpPubKeys_uncompressed_public_keys() {
        // Public keys used for creating federation, but uncompressed format now
        List<BtcECKey> erpPubKeysList = Arrays.stream(new String[]{
            "04b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b9"
                + "67e6b243635dfd897d936044b05344860cd5494283aad8508d52a784eb6a1f4527e2c9f",
            "049cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301b069"
                + "dfae714467c15649fbdb61c70e367fb43f326dc807691923cd16698af99e",
            "04284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd4076b8bb"
                + "c11b4a3f559c8041b03a903d7d7efacc4dd3796a27df324c7aa3bc5d",
            "04776b1fd8f86da3c1db3d69699e8250a15877d286734ea9a6da8e9d8ad25d16c118424627ece3cba0" 
                + "028fcbd4a0372485641a02383f4cdcee932542efd60d1029",
            "04ab0e2cd7ed158687fc13b88019990860cdb72b1f5777b58513312550ea1584bc08b4554783b4960c6a"
                + "bb761979d24d76a08ac38e775d72b960cd5644e1a54f01"
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        // Recreate federation
        federation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300, 400, 500),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            erpPubKeysList,
            ACTIVATION_DELAY_VALUE
        );

        Assert.assertEquals(ERP_FED_KEYS, federation.getErpPubKeys());
    }

    @Test
    public void spendFromErpFed() {

        // Created with GenNodeKeyId using seed 'fed1'
        //byte[] publicKeyBytes = Hex.decode("043267e382e076cbaa199d49ea7362535f95b135de181caf66b391f541bf39ab0e75b8577faac2183782cb0d76820cf9f356831d216e99d886f8a6bc47fe696939");
        byte[] publicKeyBytes = Hex.decode("024c759affafc5589872d218ca30377e6d97211c039c375672c169ba76ce7fad6a");
        BtcECKey btcKey = BtcECKey.fromPublicOnly(publicKeyBytes);
        ECKey rskKey = ECKey.fromPublicOnly(publicKeyBytes);
        FederationMember fed1 = new FederationMember(btcKey, rskKey, rskKey);
        //BtcECKey fed1PrivKey = BtcECKey.fromPrivate(Hex.decode("529822842595a3a6b3b3e51e9cffa0db66452599f7beec542382a02b1e42be4b"));

        // Created with GenNodeKeyId using seed 'fed2'
        //publicKeyBytes = Hex.decode("04bd5b51b1c5d799da190285c8078a2712b8e5dc6f73c799751e6256bb89a4bd04c6444b00289fc76ee853fcfa52b3083d66c42e84f8640f53a4cdf575e4d4a399");
        publicKeyBytes = Hex.decode("031f4aa4943fa2b731cd99c551d6992021555877b3b32c125385600fbc1b89c2a9");
        btcKey = BtcECKey.fromPublicOnly(publicKeyBytes);
        rskKey = ECKey.fromPublicOnly(Hex.decode("029b5c1e4f62f0fc7cde71906c5014ed6bcbf23040ce7f47ffe86f46bababd4a40"));
        ECKey mstKey = ECKey.fromPublicOnly(Hex.decode("021e8ca577b7ca124dd3bdc9fc1daec5e80f016c1b203fe7a8e7b035b8b674d7d3"));
        FederationMember fed2 = new FederationMember(btcKey, rskKey, mstKey);
        //BtcECKey fed2PrivKey = BtcECKey.fromPrivate(Hex.decode("fa013890aa14dd269a0ca16003cabde1688021358b662d17b1e8c555f5cccc6e"));

        // Created with GenNodeKeyId using seed 'fed3'
        //publicKeyBytes = Hex.decode("0443e106d90183e2eef7d5cb7538a634439bf1301d731787c6736922ff19e750ed39e74a76731fed620aeedbcd77e4de403fc4148efd3b5dbfc6cef550aa63c377");
        publicKeyBytes = Hex.decode("03767a0994daa8babee7215b2371916d09fc1158de3c23feeefaae2dfe5baf4830");
        btcKey = BtcECKey.fromPublicOnly(publicKeyBytes);
        rskKey = ECKey.fromPublicOnly(publicKeyBytes);
        FederationMember fed3 = new FederationMember(btcKey, rskKey, rskKey);
        //BtcECKey fed3PrivKey = BtcECKey.fromPrivate(Hex.decode("b2889610e66cd3f7de37c81c20c786b576349b80b3f844f8409e3a29d95c0c7c"));

        // Created with GenNodeKeyId using seed 'erp1'
        //publicKeyBytes = Hex.decode("048f5a88b08d75765b36951254e68060759de5be7e559972c37c67fc8cedafeb2643a4a8a618125530e275fe310c72dbdd55fa662cdcf8e134012f8a8d4b7e8400");
//        BtcECKey erp1Key = BtcECKey.fromPublicOnly(publicKeyBytes);
        //BtcECKey erp1PrivKey = BtcECKey.fromPrivate(Hex.decode("1f28656deb5f108f8cdf14af34ac4ff7a5643a7ac3f77b8de826b9ad9775f0ca"));
        BtcECKey erp1PrivKey = BtcECKey.fromPrivate(Hex.decode("e77effb6858f373c5e9a2b7eb68b5d9e0ae2f28a430142452a197f877daf15ac"));
        BtcECKey erp1Key = BtcECKey.fromPublicOnly(erp1PrivKey.getPubKey());

        // Created with GenNodeKeyId using seed 'erp2'
        //publicKeyBytes = Hex.decode("04deba35a96add157b6de58f48bb6e23bcb0a17037bed1beb8ba98de6b0a0d71d60f3ce246954b78243b41337cf8f93b38563c3bcd6a5329f1d68c057d0e5146e8");
        //BtcECKey erp2Key = BtcECKey.fromPublicOnly(publicKeyBytes);
        //BtcECKey erp2PrivKey = BtcECKey.fromPrivate(Hex.decode("4e58ebe9cd04ffea5ab81dd2aded3ab8a63e44f3b47aef334e369d895c351646"));
        BtcECKey erp2PrivKey = BtcECKey.fromPrivate(Hex.decode("233360738d2227fe43cb1fac655fc228d246aeabdf06295dc34bac01f730baeb"));
        BtcECKey erp2Key = BtcECKey.fromPublicOnly(erp2PrivKey.getPubKey());

        // Created with GenNodeKeyId using seed 'erp3'
        //publicKeyBytes = Hex.decode("04c34fcd05cef2733ea7337c37f50ae26245646aba124948c6ff8dcdf82128499808fc9148dfbc0e0ab510b4f4a78bf7a58f8b6574e03dae002533c5059973b61f");
        //BtcECKey erp3Key = BtcECKey.fromPublicOnly(publicKeyBytes);
        //BtcECKey erp3PrivKey = BtcECKey.fromPrivate(Hex.decode("57e8d2cd51c3b076ca96a1043c8c6d32c6c18447e411a6279cda29d70650977b"));
        BtcECKey erp3PrivKey = BtcECKey.fromPrivate(Hex.decode("1f486630a370ced74e77e5c3be5b486f70b138750fdd4384c4f6c2f812b8679d"));
        BtcECKey erp3Key = BtcECKey.fromPublicOnly(erp3PrivKey.getPubKey());

        NetworkParameters networkParameters = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        ErpFederation erpFed = new ErpFederation(
            Arrays.asList(fed1, fed2, fed3),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            networkParameters,
            Arrays.asList(erp1Key, erp2Key, erp3Key),
            50 // 50 (decimal) in hex
        );
        System.out.println("ERP fed address: " + erpFed.getAddress().toBase58());

        //TODO Replace with alphanet tx
        BtcTransaction pegInTx = new BtcTransaction(networkParameters, Hex.decode("02000000062f3b99c00a0c06c13730939aa7a97f4c7f48a04ee58e1ea71dc2f2a167811b35000000006a47304402203d609edddf5c9e01a1c71adfae347b2839b43826e17bcca61402dc78935ea801022012a727000de5ab0ac0df72a589b408aff94351f8f35c9c8d4f9f375cd53b5a900121030abf8e48ef637a681537ccc60ccf453c71cd48519f512b43a1f643da389b6679fdffffffc2cac5502ade43cdda0ca558107d1f975c2819d4c5b549cf2817ced6d27071ae000000006a47304402201f7899305c0140691ea25b8d3beb29fff94e5a8cf4c22023aeddf447e709c95702203d4a275535043634cc5b3a3ca49d2e5a2275e0f88906cf4fd2fc04f81d2357950121030abf8e48ef637a681537ccc60ccf453c71cd48519f512b43a1f643da389b6679fdffffff6eebfb15094cd6fb1adc5a58f46c4d24175679fcf9da09d66f151660dbd2efe0000000006a4730440220661ee85bb6b5bab36b5a55582a49801611d47a924c83765ea03bcd09020fa442022064456adac7147d627ff47515136bfdfe354a133709dfac0bafc78239a7ae2ce80121030abf8e48ef637a681537ccc60ccf453c71cd48519f512b43a1f643da389b6679fdffffff0c938fe31dc5c08dbc10aabef6118f52e69111a2746c82364c692da31cd948ef000000006a473044022075e3bfeafa151c2e4b87bce2ad130f5da485faf5173904f3f5eb280adf80e6c90220761e5185104c25a758a0da5142f87c8813fea0354f9f93bce3f3a323fd10f9290121030abf8e48ef637a681537ccc60ccf453c71cd48519f512b43a1f643da389b6679fdffffff13f050792d5a5397a10571730991f3d2cdfc9f6b3dc8f547d0b05b35f72afaf4000000006b483045022100967c54d4960e27616f3995ebc38fd5ba3fe4e9ee9486d76a961ece561f9252b102206c16bf7ce7f05e7f06af5b093c01d7b23f3c6e5a68dc636fbf2d933a8d4f70140121030abf8e48ef637a681537ccc60ccf453c71cd48519f512b43a1f643da389b6679fdffffff01724031bd8b47b66d05158a1aa8f737aee95bfdbad0592270198f38cd68c2ff000000006b4830450221009b7f4fc590334248c2e1d0e5b1b303f5962314da04ccb65a4059d1afd65caa4c0220099c2f1ea118b1eeb8e8efe5031bb77cd2b7503aa9c9a2c3d65f409d7499901f0121030abf8e48ef637a681537ccc60ccf453c71cd48519f512b43a1f643da389b6679fdffffff0240420f000000000017a914ba053351893c7495e0c75d5abacb3ed886cf1ff8872a4d2301000000001976a9140a4f09cbd39d5d8072b24385e1a9eb1c84ae544688acb9981e00"));

        Address destinationAddress = Address.fromBase58(networkParameters, "mgTTjeVwRV5H1EjmcqtqrfvnBHMokPcFUc");
        System.out.println("Destination address: " + destinationAddress.toBase58());
        BtcTransaction pegOutTx = new BtcTransaction(networkParameters);
        pegOutTx.addInput(pegInTx.getOutput(0)); //TODO Check output index when executing in alphanet
        pegOutTx.addOutput(Coin.valueOf(990000), destinationAddress); //TODO Check amount when executing in alphanet
        pegOutTx.setVersion(2);
        pegOutTx.getInput(0).setSequenceNumber(60L);

        // Create signatures
        Sha256Hash sigHash = pegOutTx.hashForSignature(0, erpFed.getRedeemScript(), BtcTransaction.SigHash.ALL, false);

        BtcECKey.ECDSASignature signature1 = erp1PrivKey.sign(sigHash);
        TransactionSignature txSignature1 = new TransactionSignature(signature1, BtcTransaction.SigHash.ALL, false);
        byte[] txSignature1Encoded = txSignature1.encodeToBitcoin();

        BtcECKey.ECDSASignature signature2 = erp2PrivKey.sign(sigHash);
        TransactionSignature txSignature2 = new TransactionSignature(signature2, BtcTransaction.SigHash.ALL, false);
        byte[] txSignature2Encoded = txSignature2.encodeToBitcoin();

        ScriptBuilder scriptBuilder = new ScriptBuilder();
        Script inputScript = scriptBuilder
            .number(0)
            .data(txSignature1Encoded)
            .data(txSignature2Encoded)
            .number(1)
            .data(erpFed.getRedeemScript().getProgram())
            .build();

        pegOutTx.getInput(0).setScriptSig(inputScript);

        byte[] result = pegOutTx.bitcoinSerialize();

        inputScript.correctlySpends(pegOutTx,0, pegInTx.getOutput(0).getScriptPubKey());

        System.out.println(Hex.toHexString(result));
    }
}
