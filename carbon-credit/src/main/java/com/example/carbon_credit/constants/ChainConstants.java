package com.example.carbon_credit.constants;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component // ✅ Make it a Spring Bean
public class ChainConstants {

    public static final String INDEXER_ID = "main_indexer";
    public static final String SuperAdmin_Transferred_Hash = "0x0f62530a074f4e1e883a8c916fa7f8639d52598edb7f9b5aa3148d991db5610d";
    public static final String Admin_Added_Hash = "0x44d6d25963f097ad14f29f06854a01f575648a1ef82f30e562ccd3889717e339";
    public static final String Admin_Removed_Hash = "0xa3b62bc36326052d97ea62d63c3d60308ed4c3ea8ac079dd8499f1e9c4f80c0f";
    public static final String Government_Added_Hash = "0x3b72f0d97b927d43f0f6f2d85f081ef76123de67b17444225cfd99359a138aab";
    public static final String Government_Removed_Hash = "0x09e7453b60e6f6a2e5bef680fb002f94eff6650dd37279d3a32ac251c51fa850";
    public static final String Organization_Verified_Hash = "0x6d9fac1782b03778ad5aa47a795806df6ba72e0b6d87312cc52c8d431e87e676";
    public static final String Organization_Revoked_Hash = "0x8a22fae4569bf2ed61be7a5768800f97175fc239f44f3dd3a38c402323f3c7f1";
    public static final String CreditQuota_Updated_Hash = "0x7eb5cc61185a147f337fda154771e4eafee28ca265d95d75b48199db309ee10e";
    public static final String Project_Approved_Hash = "0x4bb64b043b2013f1fbb5ee3c42ac0d4172b266dfa63dd3ec6bee6937935ce56a";
    public static final String Project_Revoked_Hash = "0x7202198224b75a4ebcf733db1dd07aa584c3f9adb97fc2b6a2acdb8dec042f50";
    public static final String Credit_Minted_Hash = "0x25d31585bb9ac3bfae733c1b5357883a8a37de1a6a586879740c4ff36dc9a026";
    public static final String Credit_Retired_Hash = "0xaa7680d417cf375b919ced5fd036189837a2ec6d372edd5ea0d04939f90928ff";
    public static final String Certificate_Minted_Hash = "0xc5c6b84f0b4cbac9809291495149f8373f97e7397609361a07aca0cd8c87a21a";
    public static final String Batch_Certificate_Retired_Hash = "0x92db6c779211decf4218f3e16844e4a1af30a83a3f9f8db6ad5baae1cc9dc629";
    public static final String Native_Deposited_Hash = "0xb5d7700fb0cf415158b8db7cc7c39f0eab16a825c92e221404b4c8bb099b4bbb";
    public static final String Native_Withdrawn_Hash = "0xc303ca808382409472acbbf899c316cf439f409f6584aae22df86dfa3c9ed504";
    public static final String Credit_Deposited_Hash = "0x35b9e02196dc7d2b0a6dc3d357011ded388e28d8737d98c2fbaa73840c468483";
    public static final String Credit_Withdrawn_Hash = "0x77982616940619ddbda4afe3675280fff7c605d31503336add3633fbea69d8ba";
    public static final String Balance_Locked_Hash = "0x981774f64f452b6e5ad025397395f9b8e9032ff281192dd76b1120bd9e717e1f";
    public static final String Balance_Unlocked_Hash = "0x6d29f7bae110042a5e6b14de4a933aeceaa64a5abcf524da973b546c4611f33d";
    public static final String Trade_Settled_Hash = "0x84896e7b4aae65369793a1be8e76b1797d74208ccc05ec4f342cf1c42119d969";
    public static final String Batch_Settled_Hash = "0x394d10d0c361d6f116b7ddf0a96fedd949bf97857d795bad6b2c861ca9204c60";
    public static final String Settlement_Operator_Updated_Hash = "0x1fd86ee97516e805df1aa58110ecb8ff02b3e66f573e25bf47e2c45bd2537c4f";
    // ✅ Non-static fields with @Value
    @Value("${blockchain.contract.exchange.address}")
    private String exchangeContract;
    @Value("${blockchain.contract.system.address}")
    private String systemContract;
    // ✅ Will be initialized after @Value injection
    private List<String> listenedAddresses;

    @PostConstruct
    public void init() {
        // Initialize after Spring injects values
        this.listenedAddresses = Arrays.asList(exchangeContract, systemContract);
        System.out.println("✅ Listened addresses: " + listenedAddresses);
    }

    // ✅ Getter methods
    public String getExchangeContract() {
        return exchangeContract;
    }

    public String getSystemContract() {
        return systemContract;
    }

    public List<String> getListenedAddresses() {
        return listenedAddresses;
    }
}
