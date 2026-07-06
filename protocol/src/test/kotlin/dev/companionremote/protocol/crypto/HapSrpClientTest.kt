package dev.companionremote.protocol.crypto

import dev.companionremote.protocol.hap.hexToBytes
import dev.companionremote.protocol.hap.toHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Golden vector generated with srptools 1.0.1 (the SRP library pyatv uses),
 * SRPClientSession + SRPServerSession with fixed private keys:
 * username "Pair-Setup", PIN "1234",
 * salt 10dade5f7cbc4b7c95a5ce2b3f9bcbf9,
 * a = 60975527035cf2ad1989806f0407210bc81edc04e2762a56afd529ddda2d4393,
 * b = e487cb59d31ac550471e81f00f6928e01dda08e974a004f49e61f5d105284d20.
 * The server session's key and proof matched the client's, so these values
 * are a verified two-sided exchange.
 */
class HapSrpClientTest {

    private val salt = "10dade5f7cbc4b7c95a5ce2b3f9bcbf9".hexToBytes()
    private val aPrivate = "60975527035cf2ad1989806f0407210bc81edc04e2762a56afd529ddda2d4393".hexToBytes()

    private val expectedA =
        "fab6f5d2615d1e323512e7991cc37443f487da604ca8c9230fcb04e541dce628" +
            "0b27ca4680b0374f179dc3bdc7553fe62459798c701ad864a91390a28c93b644" +
            "adbf9c00745b942b79f9012a21b9b78782319d83a1f8362866fbd6f46bfc0ddb" +
            "2e1ab6e4b45a9906b82e37f05d6f97f6a3eb6e182079759c4f6847837b62321a" +
            "c1b4fa68641fcb4bb98dd697a0c73641385f4bab25b793584cc39fc8d48d4bd8" +
            "67a9a3c10f8ea12170268e34fe3bbe6ff89998d60da2f3e4283cbec1393d52af" +
            "724a57230c604e9fbce583d7613e6bffd67596ad121a8707eec4694495703368" +
            "6a155f644d5c5863b48f61bdbf19a53eab6dad0a186b8c152e5f5d8cad4b0ef8" +
            "aa4ea5008834c3cd342e5e0f167ad04592cd8bd279639398ef9e114dfaaab919" +
            "e14e850989224ddd98576d79385d2210902e9f9b1f2d86cfa47ee244635465f7" +
            "1058421a0184be51dd10cc9d079e6f1604e7aa9b7cf7883c7d4ce12b06ebe160" +
            "81e23f27a231d18432d7d1bb55c28ae21ffcf005f57528d15a88881bb3bbb7fe"

    private val serverB = (
        "5b2817a38ab2450d4bb67c7ec30963bb986f21b0f95bc7077ef80de50c5f0d81" +
            "e819d1defe5b5c1abcebe2a61bc1c0073822c0138d3005dbc6b306b2edf10fb6" +
            "3a749abe6cb3e2e955e28d1e83abbca1edc12538154b2e58c074928cf5e76b03" +
            "57af5e2c6e9b49ce8d6ee0899be685e5a1eb6debd60cea132e7728faf7ad01fd" +
            "f4e53ee17f5e9e823254016b66cac2c3d4a69e224532a4421e5d3762285d0764" +
            "ec6ac63c016bdc16cc173a01e69e1de90bd50c064a0a32e38808c47017a2155d" +
            "b4c512f8bf77927108906c18c0af8fe4339296d3b6c820674c7371052ab3991f" +
            "7c7e2be0e1fb45f725f5f56e3226fa352a78a9d66c1fd8c156555fd380a83e29" +
            "5d6701fc0cf3b9fc4c50376d17b5b2ec8773f974a3569f732bca3b6ea75ff788" +
            "768caa348557927ecc3e3946b696f0b8e024df716051ea2f214c9c377d9e87e3" +
            "e53c439910fb1cdf27ca03a411102ee33d3ecbf8c9bf36c7e56c64e5c1b199f4" +
            "57fafc03584b781f0ce3c13f32b1a825d8a991ac2c4050071948fb279eebb8bc"
        ).hexToBytes()

    @Test
    fun `matches srptools golden vector`() {
        val client = HapSrpClient("Pair-Setup", "1234", aPrivate)
        assertEquals(expectedA, client.publicKey.toHex())

        client.processServerParams(serverB, salt)
        assertEquals(
            "a79f22ee61cb71c92a406af29cc5255c13105c497f5caad22aa435f50de74eca" +
                "5986474f8ece4493b0ced7ccc6c3efb0ec28666ed05c0bdf665489c46c2237ca",
            client.sessionKey.toHex(),
        )
        assertEquals(
            "c904847b9904252289fceb8652e71efb04cf0ed606736ac5610fdfe3e5edd3ca" +
                "b8bd6c39cc28db98bfe3c1fa0a52bb7d9f765aa0cd4c62f17394dd250736d716",
            client.clientProof.toHex(),
        )
        assertTrue(
            client.verifyServerProof(
                (
                    "92ef5ea8357dbef4f553e178230b22b725a54e8383ca0987ca2179af18e05f9b" +
                        "08918e3a6ef60244afe36068899d9012c767e408e8133aa878fef58299f9baeb"
                    ).hexToBytes(),
            ),
        )
        assertFalse(client.verifyServerProof(ByteArray(64)))
    }

    @Test
    fun `different pins produce different proofs`() {
        val c1 = HapSrpClient("Pair-Setup", "1234", aPrivate)
        val c2 = HapSrpClient("Pair-Setup", "0000", aPrivate)
        c1.processServerParams(serverB, salt)
        c2.processServerParams(serverB, salt)
        assertFalse(c1.clientProof.contentEquals(c2.clientProof))
    }
}
