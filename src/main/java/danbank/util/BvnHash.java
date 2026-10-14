package danbank.util;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class BvnHash {

    private static final String ALGORITHM ="HmacSHA256";
    private final SecretKeySpec key;


    public BvnHash(@Value("${bank.security.bvn-secret}") String secret){
        if(secret == null || secret.length() < 32){
            throw new IllegalArgumentException("Key not valid, Lest than 32 characters");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hash(String bvn){
        try{
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte [] result =  mac.doFinal(bvn.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
