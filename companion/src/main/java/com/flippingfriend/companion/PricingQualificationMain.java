package com.flippingfriend.companion;

import com.flippingfriend.model.AccountPriceScenarios;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Separate chronological pricing experiment. No live store, policy switch or learning promotion. */
public final class PricingQualificationMain {
    private static final Gson JSON=new Gson();
    private static final String VERSION="PRICING_QUALIFICATION_V1";
    static final class Exclusions {
        String schemaVersion;
        Long tunedThrough;
        List<String> inputSha256;
        String availabilityEvidence;
    }
    static final class Manifest {
        String schemaVersion="1",method=VERSION,policy=AccountPriceScenarios.VERSION;
        String inputSha256,exclusionsSha256,artifactSha256;
        long startAt,fitEnd,validationEnd,endAt;
    }
    static final class Pair {
        final int cadenceMinutes,captureShareBps;
        final AccountReplay.Result baseline,challenger;
        Pair(int cadence,int share,AccountReplay.Result base,AccountReplay.Result changed) {
            cadenceMinutes=cadence;captureShareBps=share;baseline=base;challenger=changed;
        }
    }
    static final class Report {
        final String method,policy;
        final String scope="PUBLIC_VOLUME_SIMULATION; NOT REAL PROFIT SUPERIORITY OR RUNTIME PROMOTION";
        final String fold,cohortSha256,artifactSha256;
        final List<Pair> comparisons;
        final Map<Integer,String> cadenceOutcomes;
        Report(String fold,Manifest manifest,List<Pair> pairs,Map<Integer,String> outcomes) {
            method=VERSION;policy=AccountPriceScenarios.VERSION;
            this.fold=fold;cohortSha256=manifest.inputSha256;artifactSha256=manifest.artifactSha256;
            comparisons=List.copyOf(pairs);cadenceOutcomes=Collections.unmodifiableMap(new TreeMap<>(outcomes));
        }
    }
    private PricingQualificationMain(){}
    public static void main(String[] args)throws Exception {
        if(args.length==4 && "freeze".equals(args[0])){freeze(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]));return;}
        if(args.length==3 && "evaluate".equals(args[0])){evaluate(Path.of(args[1]),args[2]);return;}
        throw new IllegalArgumentException("Usage: freeze <new-public-replay-input> <tuned-exclusions.json> <new-local-data-cohort>; evaluate <cohort> <fit|validation|test>");
    }
    static void freeze(Path source,Path exclusions,Path output)throws Exception {
        byte[] input=AccountReplayMain.read(source),excluded=AccountReplayMain.read(exclusions);
        var data=FrozenReplayInputs.load(source);data.validate();
        var prior=JSON.fromJson(new String(excluded,StandardCharsets.UTF_8),Exclusions.class);
        if(prior==null || !"1".equals(prior.schemaVersion) || prior.tunedThrough==null || prior.tunedThrough<0
            || prior.inputSha256==null || prior.inputSha256.isEmpty() || prior.availabilityEvidence==null
            || !Set.of("RECORDED_PUBLIC_AVAILABILITY","RETROSPECTIVE_ASSUMPTIONS","DEVELOPMENT_ONLY").contains(prior.availabilityEvidence)
            || prior.inputSha256.stream().anyMatch(s->s==null || !s.matches("[0-9a-f]{64}"))
            || prior.inputSha256.contains(ExecutionEvaluationMain.hash(input)) || data.startAt<=prior.tunedThrough)
            throw new IllegalArgumentException("New chronological cohort must exclude all declared tuned fixtures/windows");
        long duration=data.endAt-data.startAt;
        if(duration<1500)throw new IllegalArgumentException("At least five completed decision buckets required even for a smoke cohort");
        var manifest=new Manifest();manifest.inputSha256=ExecutionEvaluationMain.hash(input);manifest.exclusionsSha256=ExecutionEvaluationMain.hash(excluded);
        manifest.artifactSha256=artifactIdentity();manifest.startAt=data.startAt;manifest.endAt=data.endAt;
        manifest.fitEnd=data.startAt+duration*3/5;manifest.validationEnd=data.startAt+duration*4/5;
        Path out=AccountReplayMain.privateNewDirectory(output);Files.createDirectory(out);
        FrozenReplayInputs.copy(source,out.resolve("input.json"));
        Files.write(out.resolve("exclusions.json"),excluded,StandardOpenOption.CREATE_NEW);
        Path code=Path.of(PricingQualificationMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if(Files.isRegularFile(code))Files.write(out.resolve("evaluator.jar.bin"),AccountReplayMain.read(code),StandardOpenOption.CREATE_NEW);
        Files.write(out.resolve("manifest.json"),AccountReplayMain.bytes(manifest),StandardOpenOption.CREATE_NEW);
        System.out.println("Pricing cohort frozen 60/20/20; runtime pricing and learning unchanged.");
    }
    static Manifest verify(Path cohort)throws Exception {
        var manifest=JSON.fromJson(Files.readString(cohort.resolve("manifest.json")),Manifest.class);
        if(manifest==null || !"1".equals(manifest.schemaVersion) || !VERSION.equals(manifest.method)
            || !AccountPriceScenarios.VERSION.equals(manifest.policy) || !Objects.equals(manifest.artifactSha256,artifactIdentity())
            || !Objects.equals(manifest.inputSha256,ExecutionEvaluationMain.hash(AccountReplayMain.read(cohort.resolve("input.json"))))
            || !Objects.equals(manifest.exclusionsSha256,ExecutionEvaluationMain.hash(AccountReplayMain.read(cohort.resolve("exclusions.json")))))
            throw new IllegalArgumentException("Frozen cohort, policy or evaluator changed");
        if(!"UNPACKAGED_DEVELOPMENT_CLASSES".equals(manifest.artifactSha256)
            && !manifest.artifactSha256.equals(ExecutionEvaluationMain.hash(AccountReplayMain.read(cohort.resolve("evaluator.jar.bin")))))
            throw new IllegalArgumentException("Retained pricing evaluator changed");
        long duration=manifest.endAt-manifest.startAt;
        if(duration<1500 || manifest.fitEnd!=manifest.startAt+duration*3/5 || manifest.validationEnd!=manifest.startAt+duration*4/5)
            throw new IllegalArgumentException("Chronological boundaries changed");
        FrozenReplayInputs.load(cohort.resolve("input.json")).validate();
        return manifest;
    }
    static AccountReplayInput slice(AccountReplayInput input,long start,long end) {
        return input.window(start,end);
    }
    static void evaluate(Path cohort,String fold)throws Exception {
        if(!Set.of("fit","validation","test").contains(fold))throw new IllegalArgumentException("Unknown chronological fold");
        Manifest manifest=verify(cohort);
        var input=FrozenReplayInputs.load(cohort.resolve("input.json"));input.validate();
        if(input.startAt!=manifest.startAt || input.endAt!=manifest.endAt)throw new IllegalArgumentException("Cohort boundaries disagree");
        var exclusions=JSON.fromJson(Files.readString(cohort.resolve("exclusions.json")),Exclusions.class);
        Set<Integer> eligible=new HashSet<>(List.of(5,30,120,480));
        if(!"fit".equals(fold)) {
            String prior="test".equals(fold)?"validation":"fit";
            byte[] previousBytes=AccountReplayMain.read(cohort.resolve(prior+"/results.json"));
            var receipt=JSON.fromJson(new String(AccountReplayMain.read(cohort.resolve(prior+"/receipt.json")),StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
            if(receipt==null || !receipt.has("sha256") || !ExecutionEvaluationMain.hash(previousBytes).equals(receipt.get("sha256").getAsString()))
                throw new IllegalArgumentException("Prior fold result changed");
            var previous=JSON.fromJson(new String(previousBytes,StandardCharsets.UTF_8),Report.class);
            if(previous==null || !prior.equals(previous.fold) || !Objects.equals(manifest.inputSha256,previous.cohortSha256)
                || !VERSION.equals(previous.method) || !AccountPriceScenarios.VERSION.equals(previous.policy)
                || !Objects.equals(manifest.artifactSha256,previous.artifactSha256))throw new IllegalArgumentException("Prior fold identity mismatch");
            if("test".equals(fold))eligible.removeIf(c->!"PASSED_SIMULATION_ONLY".equals(previous.cadenceOutcomes.get(c)));
        }
        if("test".equals(fold) && eligible.isEmpty())throw new IllegalStateException("No cadence qualified validation; final test remains unopened");
        Path out=AccountReplayMain.privateNewDirectory(cohort.resolve(fold));
        // CREATE_NEW consumes this cohort/policy's final test once, before reading its outcomes.
        if("test".equals(fold))Files.writeString(cohort.resolve("test-consumed.json"),JSON.toJson(Map.of(
            "cohort",manifest.inputSha256,"artifact",manifest.artifactSha256,"policy",manifest.policy)),StandardOpenOption.CREATE_NEW);
        Files.createDirectory(out);
        long start="fit".equals(fold)?manifest.startAt:"validation".equals(fold)?manifest.fitEnd:manifest.validationEnd;
        long end="fit".equals(fold)?manifest.fitEnd:"validation".equals(fold)?manifest.validationEnd:manifest.endAt;
        var data=slice(input,start,end);var pairs=new ArrayList<Pair>();var outcomes=new TreeMap<Integer,String>();
        for(int cadence:new int[]{5,30,120,480}) {
            if(!eligible.contains(cadence)){outcomes.put(cadence,"NOT_RUN_VALIDATION_FAILED");continue;}
            for(int share:new int[]{10,50,100}) {
                AccountReplay.Result base,changed;
                try(var replay=new AccountReplay(data,cadence,share,out.resolve("baseline-"+cadence+"-"+share+".db"))){base=replay.run();}
                try(var replay=new AccountReplay(data,cadence,share,out.resolve("challenger-"+cadence+"-"+share+".db"),null,null,true)){changed=replay.run();}
                pairs.add(new Pair(cadence,share,base,changed));
            }
            outcomes.put(cadence,outcome(pairs,cadence,end-start,exclusions.availabilityEvidence));
        }
        var report=new Report(fold,manifest,pairs,outcomes);
        byte[] result=AccountReplayMain.bytes(report);
        Files.write(out.resolve("results.json"),result,StandardOpenOption.CREATE_NEW);
        Files.write(out.resolve("receipt.json"),AccountReplayMain.bytes(Map.of("sha256",ExecutionEvaluationMain.hash(result),
            "cohort",manifest.inputSha256,"artifact",manifest.artifactSha256,"fold",fold)),StandardOpenOption.CREATE_NEW);
        System.out.println(JSON.toJson(outcomes));
    }
    private static String outcome(List<Pair> pairs,int cadence,long duration,String clocks) {
        if(!"RECORDED_PUBLIC_AVAILABILITY".equals(clocks))return "UNSUPPORTED_AVAILABILITY_CLOCKS";
        if(duration<Math.max(86400,cadence*60L*6))return "INSUFFICIENT_ELAPSED_COVERAGE";
        var shares=new HashSet<Integer>();
        for(var p:pairs)if(p.cadenceMinutes==cadence) {
            if(!shares.add(p.captureShareBps) || !Set.of(10,50,100).contains(p.captureShareBps))return "INVALID_STRESS_COVERAGE";
            if(!p.baseline.terminalMarksComplete || !p.challenger.terminalMarksComplete)return "UNKNOWN_REMAINING_STOCK_VALUE";
            if(p.challenger.completedSales<20)return "INSUFFICIENT_COMPLETED_SALES";
            if(p.challenger.maximumDrawdown>15_000_000)return "LOSS_GUARD_FAILED";
            if(p.challenger.netEquityChangeLower<=p.baseline.netEquityChangeLower)return "HELD_OUT_GAIN_NOT_POSITIVE_UNDER_STRESS";
        }
        return shares.size()==3?"PASSED_SIMULATION_ONLY":"INVALID_STRESS_COVERAGE";
    }
    private static String artifactIdentity()throws Exception {
        Path code=Path.of(PricingQualificationMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return Files.isRegularFile(code)?ExecutionEvaluationMain.hash(AccountReplayMain.read(code)):"UNPACKAGED_DEVELOPMENT_CLASSES";
    }
}
