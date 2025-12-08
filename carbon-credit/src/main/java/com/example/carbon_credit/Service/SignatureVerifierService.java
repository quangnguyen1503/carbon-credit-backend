package com.example.carbon_credit.Service;

import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Service để verify Ethereum signature và recover address.
 * Dùng cho SIWE (Sign-In with Ethereum).
 */
@Service
public class SignatureVerifierService {

    /**
     * Verify signature và recover address từ message.
     * @param message Tin nhắn gốc (string)
     * @param signature Chữ ký hex (0x + 130 chars)
     * @return Recovered address nếu hợp lệ, null nếu fail
     */
    public String verifyAndRecoverAddress(String message, String signature) {
        try {
            // Bước 1: Tạo prefixed message hash (Ethereum personal_sign standard)
            String prefixedMessage = "\u0019Ethereum Signed Message:\n" + message.length() + message;
            byte[] messageBytes = prefixedMessage.getBytes(StandardCharsets.UTF_8);
            byte[] messageHash = Hash.sha3(messageBytes);  // Keccak256 hash (32 bytes)

            // Bước 2: Parse signature thành r, s, v
            byte[] sigBytes = Numeric.hexStringToByteArray(signature);
            if (sigBytes.length != 65) {
                throw new IllegalArgumentException("Signature phải 65 bytes (0x + 130 hex chars)");
            }

            byte v = sigBytes[64];  // Recovery ID (thường 27 or 28)
            if (v < 27) {
                v = (byte) (v + 27);  // Normalize v to 27/28
            }

            BigInteger r = Numeric.toBigInt(Arrays.copyOfRange(sigBytes, 0, 32));  // r (32 bytes)
            BigInteger s = Numeric.toBigInt(Arrays.copyOfRange(sigBytes, 32, 64));  // s (32 bytes)

            // Bước 3: Tạo SignatureData và recover public key
            Sign.SignatureData sigData = new Sign.SignatureData(v, Numeric.toBytesPadded(r, 32), Numeric.toBytesPadded(s, 32));
            BigInteger publicKey = Sign.signedMessageHashToKey(messageHash, sigData);

            // Bước 4: Derive Ethereum address từ public key
            String recoveredAddress = "0x" + Keys.getAddress(publicKey);

            return recoveredAddress;
        } catch (Exception e) {
            System.err.println("Signature verification failed: " + e.getMessage());
            return null;  // Hoặc throw custom exception
        }
    }

    /**
     * Verify chữ ký có khớp address không (wrapper tiện dụng)
     * @param message Tin nhắn
     * @param signature Chữ ký
     * @param expectedAddress Address mong đợi từ FE
     * @return true nếu khớp
     */
    public boolean isValidSignature(String message, String signature, String expectedAddress) {
        String recovered = verifyAndRecoverAddress(message, signature);
        if (recovered == null) {
            return false;
        }
        // So sánh lowercase (Ethereum address case-insensitive)
        return expectedAddress.toLowerCase().equals(recovered.toLowerCase());
    }
}