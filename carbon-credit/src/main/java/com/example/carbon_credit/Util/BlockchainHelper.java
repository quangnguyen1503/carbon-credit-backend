package com.example.carbon_credit.Util;

import com.example.carbon_credit.DTO.BlockchainEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;
import java.util.List;

@Slf4j
public class BlockchainHelper {

    private BlockchainHelper() {}

    // ==================== 1. EXTRACT TỪ TOPIC (INDEXED) ====================

    /**
     * Lấy địa chỉ ví từ Topic
     */
    public static String extractAddressFromTopic(BlockchainEventDTO event, int topicIndex) {
        try {
            if (event.getTopics().size() <= topicIndex) {
                return null;
            }
            String topic = event.getTopics().get(topicIndex);
            Address address = TypeDecoder.decodeAddress(topic);
            return address.getValue();
        } catch (Exception e) {
            log.error("❌ Failed to extract address from topic {}: {}", topicIndex, e.getMessage());
            return null;
        }
    }

    public static BigInteger extractUint256FromTopic(BlockchainEventDTO event, int topicIndex) {
        try {
            if (event.getTopics() == null || event.getTopics().size() <= topicIndex) {
                return null;
            }

            String hex = event.getTopics().get(topicIndex);

            // Bỏ prefix "0x"
            if (hex.startsWith("0x")) {
                hex = hex.substring(2);
            }

            // Topic luôn là 32 bytes (64 ký tự hex)
            return new BigInteger(hex, 16);

        } catch (Exception e) {
            log.error("❌ Failed to extract uint256 from topic {}: {}", topicIndex, e.getMessage());
            return null;
        }
    }

    /**
     * Lấy số (Uint256) từ Topic
     */


    // ==================== 2. EXTRACT TỪ DATA (NON-INDEXED) ====================

    /**
     * Lấy số (Uint256) từ Data (Dùng cho event đơn giản chỉ có 1 tham số trong data)
     */
    public static BigInteger extractUint256FromData(BlockchainEventDTO event, int dataIndex) {
        try {
            String data = event.getData();
            if (data.startsWith("0x")) {
                data = data.substring(2);
            }

            int offset = dataIndex * 64;
            if (data.length() < offset + 64) {
                return null;
            }

            String hexValue = data.substring(offset, offset + 64);
            Uint256 uint = TypeDecoder.decodeNumeric(hexValue, Uint256.class);
            return uint.getValue();
        } catch (Exception e) {
            log.error("❌ Failed to extract uint256 at index {}: {}", dataIndex, e.getMessage());
            return null;
        }
    }

    /**
     * Lấy Boolean từ Data
     */
    public static boolean extractBooleanFromData(BlockchainEventDTO event, int dataIndex) {
        try {
            BigInteger val = extractUint256FromData(event, dataIndex);
            return val != null && val.compareTo(java.math.BigInteger.ZERO) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 3. DECODERS PHỨC TẠP (SPECIFIC EVENTS) ====================
    public static List<Type> decodeAnyData(String data, List<TypeReference<Type>> outputParameters) {
        try {
            return FunctionReturnDecoder.decode(data, outputParameters);
        } catch (Exception e) {
            log.error("❌ Failed to decode data: {}", e.getMessage());
            return null;
        }
    }
}