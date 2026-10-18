package com.flippingfriend.companion;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/** Fits and validates only from verified frozen prefixes, without opening or altering a database. */
public final class ExecutionChallengerMain {
    private static final Gson JSON=new Gson();
    private ExecutionChallengerMain(){}
    public static void main(String[] args)throws Exception {
        if(args.length==3 && "fit".equals(args[0])) {
            var training=ExecutionEvaluationMain.verify(Path.of(args[1]));
            Path out=AccountReplayMain.privateNewDirectory(Path.of(args[2]));Files.createDirectory(out);
            byte[] model=AccountReplayMain.bytes(new ExecutionChallenger(training));
            Files.write(out.resolve("model.json"),model,StandardOpenOption.CREATE_NEW);
            byte[] code=ExecutionChallenger.artifact();
            if(code!=null)Files.write(out.resolve("fitter.jar.bin"),code,StandardOpenOption.CREATE_NEW);
            Files.writeString(out.resolve("model.sha256"),ExecutionEvaluationMain.hash(model)+"\n",StandardOpenOption.CREATE_NEW);
            System.out.println("Frozen challenger fitted; runtime remains on the public-price baseline.");return;
        }
        if(args.length==4 && "evaluate".equals(args[0])) {
            Path train=Path.of(args[1]),model=Path.of(args[2]),valid=Path.of(args[3]);
            System.out.println(JSON.toJson(evaluate(train,model,valid)));return;
        }
        throw new IllegalArgumentException("Usage: fit <verified-training-bundle> <new-local-data-model-directory>; evaluate <training-bundle> <model-directory> <later-validation-bundle>");
    }
    static ExecutionChallenger load(Path training,Path model)throws Exception {
        var train=ExecutionEvaluationMain.verify(training);var expected=new ExecutionChallenger(train);
        byte[] supplied=AccountReplayMain.read(model.resolve("model.json"));
        String checksum=Files.readString(model.resolve("model.sha256"),StandardCharsets.UTF_8).trim();
        if(!ExecutionEvaluationMain.hash(supplied).equals(checksum) || !Arrays.equals(supplied,AccountReplayMain.bytes(expected)))
            throw new IllegalArgumentException("Model does not reproduce from its frozen training sources");
        if(expected.fitterArtifactSha256!=null && !expected.fitterArtifactSha256.equals(
            ExecutionEvaluationMain.hash(AccountReplayMain.read(model.resolve("fitter.jar.bin")))))
            throw new IllegalArgumentException("Retained fitting artifact does not match its model");
        return expected;
    }
    static ExecutionChallenger.Validation evaluate(Path training,Path model,Path validation)throws Exception {
        var fitted=load(training,model);var comparison=ExecutionEvaluationMain.compare(training,validation);
        return new ExecutionChallenger.Validation(fitted,ExecutionEvaluationMain.verify(validation),comparison.validation);
    }
}
