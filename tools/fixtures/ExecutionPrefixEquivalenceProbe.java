package com.flippingfriend.companion;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.util.*;

/** Compile once, run separately against retained old/new artifacts; SELECT-only, no bundle writer. */
public final class ExecutionPrefixEquivalenceProbe {
    static final Gson JSON=new Gson();
    static String hash(Object object)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var out=new OutputStreamWriter(new DigestOutputStream(OutputStream.nullOutputStream(),digest),StandardCharsets.UTF_8)) {
            JSON.toJson(object,out);out.write('\n');
        }
        StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString();
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Explicit scoped frozen database and account required");
        try(var store=SqliteStore.openReadOnly(Path.of(args[0]))) {
            var source=store.freezeExecutionSources(args[1]);var observations=source.observations();
            var evaluation=new ExecutionHorizonEvaluation(observations,null);
            Map<String,Object> result=new LinkedHashMap<>();result.put("sourceRows",source.offers.size());result.put("gapRows",source.gaps.size());
            result.put("sourceCursor",source.sourceCursor);result.put("offersSha256",hash(source.offers));result.put("gapsSha256",hash(source.gaps));
            result.put("observationsSha256",hash(observations));result.put("evaluationSha256",hash(evaluation));
            result.put("totalObservations",evaluation.totalObservations);result.put("eligible",evaluation.eligible);
            result.put("promotionStatus",evaluation.promotionStatus);System.out.println(JSON.toJson(result));
        }
    }
}
