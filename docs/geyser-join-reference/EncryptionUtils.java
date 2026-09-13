/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.cloudburstmc.protocol.bedrock.data.auth.AuthPayload
 *  org.cloudburstmc.protocol.bedrock.data.auth.AuthType
 *  org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload
 *  org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload
 *  org.cloudburstmc.protocol.bedrock.util.ChainValidationResult
 *  org.cloudburstmc.protocol.bedrock.util.JsonUtils
 *  org.jose4j.json.JsonUtil
 *  org.jose4j.json.internal.json_simple.parser.JSONParser
 *  org.jose4j.jwa.AlgorithmConstraints
 *  org.jose4j.jwa.AlgorithmConstraints$ConstraintType
 *  org.jose4j.jwk.HttpsJwks
 *  org.jose4j.jws.JsonWebSignature
 *  org.jose4j.jwt.JwtClaims
 *  org.jose4j.jwt.consumer.InvalidJwtException
 *  org.jose4j.jwt.consumer.JwtConsumer
 *  org.jose4j.jwt.consumer.JwtConsumerBuilder
 *  org.jose4j.jwt.consumer.JwtContext
 *  org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver
 *  org.jose4j.keys.resolvers.VerificationKeyResolver
 *  org.jose4j.lang.JoseException
 */
package org.cloudburstmc.protocol.bedrock.util;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.JsonUtils;
import org.jose4j.json.JsonUtil;
import org.jose4j.json.internal.json_simple.parser.JSONParser;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.HttpsJwks;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.VerificationKeyResolver;
import org.jose4j.lang.JoseException;

public final class EncryptionUtils {
    private static final ECPublicKey MOJANG_PUBLIC_KEY;
    private static final SecureRandom SECURE_RANDOM;
    private static final String MOJANG_PUBLIC_KEY_BASE64 = "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAECRXueJeTDqNRRgJi/vlRufByu/2G0i2Ebt6YMar5QX/R0DIIyrJMcUpruK4QveTfJSTp3Shlq4Gk34cD/4GUWwkv0DVuzeuB+tXija7HBxii03NHDbPAD0AKnLr2wdAp";
    private static final KeyPairGenerator KEY_PAIR_GEN;
    public static final String ALGORITHM_TYPE = "ES384";
    private static final AlgorithmConstraints ALGORITHM_CONSTRAINTS;
    private static final String DISCOVERY_ENDPOINT = "https://client.discovery.minecraft-services.net/api/v1.0/discovery/MinecraftPE/builds/1.0.0.0";
    private static final JSONParser JSON_PARSER;
    private static final Map<String, Object> DISCOVERY_DATA;
    private static final Map<String, Object> OPENID_CONFIGURATION;
    private static final String JWKS_URL;
    private static final String ISSUER;
    private static final HttpsJwks JWKS;
    private static final HttpsJwksVerificationKeyResolver RESOLVER;
    private static final JwtConsumer MOJANG_CONSUMER;
    private static final JwtConsumer OFFLINE_CONSUMER;

    /*
     * Exception decompiling
     */
    private static Map<String, Object> getDiscoveryData() {
        /*
         * This method has failed to decompile.  When submitting a bug report, please provide this stack trace, and (if you hold appropriate legal rights) the relevant class file.
         * 
         * org.benf.cfr.reader.util.ConfusedCFRException: Started 3 blocks at once
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.getStartingBlocks(Op04StructuredStatement.java:412)
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.buildNestedBlocks(Op04StructuredStatement.java:487)
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement.createInitialStructuredBlock(Op03SimpleStatement.java:736)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisInner(CodeAnalyser.java:850)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisOrWrapFail(CodeAnalyser.java:278)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysis(CodeAnalyser.java:201)
         *     at org.benf.cfr.reader.entities.attributes.AttributeCode.analyse(AttributeCode.java:94)
         *     at org.benf.cfr.reader.entities.Method.analyse(Method.java:531)
         *     at org.benf.cfr.reader.entities.ClassFile.analyseMid(ClassFile.java:1055)
         *     at org.benf.cfr.reader.entities.ClassFile.analyseTop(ClassFile.java:942)
         *     at org.benf.cfr.reader.Driver.doClass(Driver.java:84)
         *     at org.benf.cfr.reader.CfrDriverImpl.analyse(CfrDriverImpl.java:78)
         *     at org.benf.cfr.reader.Main.main(Main.java:54)
         */
        throw new IllegalStateException("Decompilation failed");
    }

    private static Map<String, Object> getAuthEnvironment() {
        Map result = (Map)DISCOVERY_DATA.get("result");
        if (result == null) {
            throw new AssertionError((Object)("Discovery data does not contain 'result' key" + DISCOVERY_DATA));
        }
        Map environments = (Map)result.get("serviceEnvironments");
        if (environments == null) {
            throw new AssertionError((Object)("Discovery data does not contain 'serviceEnvironments' key" + result));
        }
        Map authEnv = (Map)environments.get("auth");
        if (authEnv == null) {
            throw new AssertionError((Object)("Discovery data does not contain 'auth' environment" + environments));
        }
        Map prodEnv = (Map)authEnv.get("prod");
        if (prodEnv == null) {
            throw new AssertionError((Object)("Discovery data does not contain 'prod' environment" + authEnv));
        }
        return prodEnv;
    }

    private static String getServiceUri() {
        String issuer = (String)EncryptionUtils.getAuthEnvironment().get("serviceUri");
        if (issuer == null) {
            throw new AssertionError((Object)"Discovery data does not contain 'issuer' key in 'prod' environment");
        }
        return issuer;
    }

    /*
     * Exception decompiling
     */
    private static Map<String, Object> getOpenIdConfiguration() {
        /*
         * This method has failed to decompile.  When submitting a bug report, please provide this stack trace, and (if you hold appropriate legal rights) the relevant class file.
         * 
         * org.benf.cfr.reader.util.ConfusedCFRException: Started 3 blocks at once
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.getStartingBlocks(Op04StructuredStatement.java:412)
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.buildNestedBlocks(Op04StructuredStatement.java:487)
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement.createInitialStructuredBlock(Op03SimpleStatement.java:736)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisInner(CodeAnalyser.java:850)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisOrWrapFail(CodeAnalyser.java:278)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysis(CodeAnalyser.java:201)
         *     at org.benf.cfr.reader.entities.attributes.AttributeCode.analyse(AttributeCode.java:94)
         *     at org.benf.cfr.reader.entities.Method.analyse(Method.java:531)
         *     at org.benf.cfr.reader.entities.ClassFile.analyseMid(ClassFile.java:1055)
         *     at org.benf.cfr.reader.entities.ClassFile.analyseTop(ClassFile.java:942)
         *     at org.benf.cfr.reader.Driver.doClass(Driver.java:84)
         *     at org.benf.cfr.reader.CfrDriverImpl.analyse(CfrDriverImpl.java:78)
         *     at org.benf.cfr.reader.Main.main(Main.java:54)
         */
        throw new IllegalStateException("Decompilation failed");
    }

    private static String getJwksUrl() {
        String jwksUrl = (String)OPENID_CONFIGURATION.get("jwks_uri");
        if (jwksUrl == null || jwksUrl.isEmpty()) {
            throw new AssertionError((Object)("OpenID configuration does not contain 'jwks_uri' key: " + OPENID_CONFIGURATION));
        }
        return jwksUrl;
    }

    private static String getIssuer() {
        String issuer = (String)OPENID_CONFIGURATION.get("issuer");
        if (issuer == null || issuer.isEmpty()) {
            throw new AssertionError((Object)("OpenID configuration does not contain 'issuer' key: " + OPENID_CONFIGURATION));
        }
        return issuer;
    }

    public static ECPublicKey parseKey(String b64) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return (ECPublicKey)KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
    }

    public static KeyPair createKeyPair() {
        return KEY_PAIR_GEN.generateKeyPair();
    }

    public static byte[] verifyClientData(String clientDataJwt, String identityPublicKey) throws NoSuchAlgorithmException, InvalidKeySpecException, JoseException {
        return EncryptionUtils.verifyClientData(clientDataJwt, EncryptionUtils.parseKey(identityPublicKey));
    }

    public static byte[] verifyClientData(String clientDataJwt, PublicKey identityPublicKey) throws JoseException {
        JsonWebSignature clientData = new JsonWebSignature();
        clientData.setCompactSerialization(clientDataJwt);
        clientData.setKey((Key)identityPublicKey);
        if (!clientData.verifySignature()) {
            return null;
        }
        return clientData.getUnverifiedPayloadBytes();
    }

    public static ChainValidationResult validatePayload(AuthPayload payload) throws JoseException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidJwtException {
        ChainValidationResult result;
        if (payload instanceof CertificateChainPayload) {
            CertificateChainPayload chainPayload = (CertificateChainPayload)payload;
            List chain = chainPayload.getChain();
            if (chain == null || chain.isEmpty()) {
                throw new IllegalStateException("Certificate chain is empty");
            }
            result = EncryptionUtils.validateChain(chain);
        } else if (payload instanceof TokenPayload) {
            TokenPayload tokenPayload = (TokenPayload)payload;
            String token = tokenPayload.getToken();
            if (token == null || token.isEmpty()) {
                throw new IllegalStateException("Token is empty");
            }
            result = EncryptionUtils.validateToken(payload.getAuthType(), token);
        } else {
            throw new IllegalArgumentException("Unsupported AuthPayload type: " + payload.getClass().getName());
        }
        if (payload.getAuthType() == AuthType.FULL && (result.identityClaims().extraData == null || result.identityClaims().extraData.xuid == null || result.identityClaims().extraData.displayName == null)) {
            throw new IllegalStateException("Missing extraData for full auth");
        }
        return result;
    }

    public static ChainValidationResult validateChain(List<String> chain) throws JoseException, NoSuchAlgorithmException, InvalidKeySpecException {
        switch (chain.size()) {
            case 1: {
                JsonWebSignature identity = new JsonWebSignature();
                identity.setCompactSerialization(chain.get(0));
                return new ChainValidationResult(false, identity.getUnverifiedPayload());
            }
            case 3: {
                ECPublicKey currentKey = null;
                Map parsedPayload = null;
                for (int i = 0; i < 3; ++i) {
                    JsonWebSignature signature = new JsonWebSignature();
                    signature.setCompactSerialization(chain.get(i));
                    ECPublicKey expectedKey = EncryptionUtils.parseKey(signature.getHeader("x5u"));
                    if (currentKey == null) {
                        currentKey = expectedKey;
                    } else if (!currentKey.equals(expectedKey)) {
                        throw new IllegalStateException("Received broken chain");
                    }
                    signature.setAlgorithmConstraints(ALGORITHM_CONSTRAINTS);
                    signature.setKey((Key)currentKey);
                    if (!signature.verifySignature()) {
                        throw new IllegalStateException("Chain signature doesn't match content");
                    }
                    if (i == 1 && !currentKey.equals(MOJANG_PUBLIC_KEY)) {
                        throw new IllegalStateException("The chain isn't signed by Mojang!");
                    }
                    parsedPayload = JsonUtil.parseJson((String)signature.getUnverifiedPayload());
                    String identityPublicKey = (String)JsonUtils.childAsType((Map)parsedPayload, (String)"identityPublicKey", String.class);
                    currentKey = EncryptionUtils.parseKey(identityPublicKey);
                }
                return new ChainValidationResult(true, parsedPayload);
            }
        }
        throw new IllegalStateException("Unexpected login chain length");
    }

    public static ChainValidationResult validateToken(AuthType type, String token) throws InvalidJwtException, JoseException {
        if (type == AuthType.FULL || type == AuthType.GUEST) {
            JwtContext context = MOJANG_CONSUMER.process(token);
            return new ChainValidationResult(true, context);
        }
        if (type == AuthType.SELF_SIGNED) {
            JwtContext context = OFFLINE_CONSUMER.process(token);
            return new ChainValidationResult(false, context);
        }
        throw new JoseException("Unsupported AuthType: " + type);
    }

    public static SecretKey getSecretKey(PrivateKey localPrivateKey, PublicKey remotePublicKey, byte[] token) throws InvalidKeyException {
        MessageDigest digest;
        byte[] sharedSecret = EncryptionUtils.getEcdhSecret(localPrivateKey, remotePublicKey);
        try {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e) {
            throw new AssertionError((Object)e);
        }
        digest.update(token);
        digest.update(sharedSecret);
        byte[] secretKeyBytes = digest.digest();
        return new SecretKeySpec(secretKeyBytes, "AES");
    }

    private static byte[] getEcdhSecret(PrivateKey localPrivateKey, PublicKey remotePublicKey) throws InvalidKeyException {
        KeyAgreement agreement;
        try {
            agreement = KeyAgreement.getInstance("ECDH");
        }
        catch (NoSuchAlgorithmException e) {
            throw new AssertionError((Object)e);
        }
        agreement.init(localPrivateKey);
        agreement.doPhase(remotePublicKey, true);
        return agreement.generateSecret();
    }

    public static String createHandshakeJwt(KeyPair serverKeyPair, byte[] token) throws JoseException {
        JsonWebSignature signature = new JsonWebSignature();
        signature.setAlgorithmHeaderValue(ALGORITHM_TYPE);
        signature.setHeader("x5u", Base64.getEncoder().encodeToString(serverKeyPair.getPublic().getEncoded()));
        signature.setKey((Key)serverKeyPair.getPrivate());
        JwtClaims claims = new JwtClaims();
        claims.setClaim("salt", (Object)Base64.getEncoder().encodeToString(token));
        signature.setPayload(claims.toJson());
        return signature.getCompactSerialization();
    }

    public static byte[] generateRandomToken() {
        byte[] token = new byte[16];
        SECURE_RANDOM.nextBytes(token);
        return token;
    }

    public static ECPublicKey getMojangPublicKey() {
        return MOJANG_PUBLIC_KEY;
    }

    public static Cipher createCipher(boolean gcm, boolean encrypt, SecretKey key) {
        try {
            String transformation;
            byte[] iv;
            if (gcm) {
                iv = new byte[16];
                System.arraycopy(key.getEncoded(), 0, iv, 0, 12);
                iv[15] = 2;
                transformation = "AES/CTR/NoPadding";
            } else {
                iv = Arrays.copyOf(key.getEncoded(), 16);
                transformation = "AES/CFB8/NoPadding";
            }
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(encrypt ? 1 : 2, (Key)key, new IvParameterSpec(iv));
            return cipher;
        }
        catch (InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new AssertionError("Unable to initialize required encryption", e);
        }
    }

    private EncryptionUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    static {
        SECURE_RANDOM = new SecureRandom();
        ALGORITHM_CONSTRAINTS = new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, new String[]{ALGORITHM_TYPE});
        JSON_PARSER = new JSONParser();
        DISCOVERY_DATA = EncryptionUtils.getDiscoveryData();
        OPENID_CONFIGURATION = EncryptionUtils.getOpenIdConfiguration();
        JWKS_URL = EncryptionUtils.getJwksUrl();
        ISSUER = EncryptionUtils.getIssuer();
        JWKS = new HttpsJwks(JWKS_URL);
        RESOLVER = new HttpsJwksVerificationKeyResolver(JWKS);
        MOJANG_CONSUMER = new JwtConsumerBuilder().setVerificationKeyResolver((VerificationKeyResolver)RESOLVER).setRequireExpirationTime().setRequireSubject().setExpectedAudience(true, new String[]{"api://auth-minecraft-services/multiplayer"}).setExpectedIssuer(ISSUER).build();
        OFFLINE_CONSUMER = new JwtConsumerBuilder().setSkipAllValidators().setSkipSignatureVerification().setRequireExpirationTime().setSkipDefaultAudienceValidation().build();
        String namedGroups = System.getProperty("jdk.tls.namedGroups");
        System.setProperty("jdk.tls.namedGroups", namedGroups == null || namedGroups.isEmpty() ? "secp384r1" : namedGroups + ", secp384r1");
        try {
            KEY_PAIR_GEN = KeyPairGenerator.getInstance("EC");
            KEY_PAIR_GEN.initialize(new ECGenParameterSpec("secp384r1"));
            MOJANG_PUBLIC_KEY = EncryptionUtils.parseKey(MOJANG_PUBLIC_KEY_BASE64);
        }
        catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new AssertionError("Unable to initialize required encryption", e);
        }
    }
}
