package com.flippingfriend.companion;

import com.flippingfriend.core.*;
import java.util.*;

/** Qualifies prospective client acceptance against canonical issuance and its first cancellation. */
final class SalePolicyEvidence {
    static final class Resolution {
        final boolean claimed;final AcceptedSalePolicyProof proof;
        Resolution(boolean claimed,AcceptedSalePolicyProof proof){this.claimed=claimed;this.proof=proof;}
        boolean valid(){return proof!=null;}
        long patience(){return proof==null?0:proof.getReceipt().getPatienceSeconds();}
    }
    static Resolution resolve(List<OfferEvent> history,String source,SalePolicyReceiptStore.Evidence issued) {
        if(history==null || source==null)return new Resolution(false,null);
        OfferEvent original=null;AcceptedSalePolicyProof accepted=null;boolean claimed=false,cancelled=false,invalid=false;
        for(var event:history) {
            if(event==null)continue;
            if(source.equals(event.getEventId())) {
                if(original!=null)invalid=true;original=event;
                if(event.getSalePolicyEvidenceVersion()!=null) {
                    claimed=true;if(!"1".equals(event.getSalePolicyEvidenceVersion()))invalid=true;
                }
            }
            var proof=event.getAcceptedSalePolicyProof();var receipt=proof==null?null:proof.getReceipt();
            if(receipt!=null && source.equals(receipt.getSourceEventId())) {
                claimed=true;
                if(!"1".equals(event.getSalePolicyEvidenceVersion()) || original==null || cancelled && accepted==null || !receipt.matchesSource(original)
                    || !proof.matchesEpisode(event) || event.getSequence()<=original.getSequence()) {invalid=true;continue;}
                if(accepted==null) {
                    if(issued==null || !issued.qualifies(proof,event)) {invalid=true;continue;}
                    accepted=proof;
                }else if(!accepted.equals(proof))invalid=true;
            }
            if(original!=null && Objects.equals(original.getAccountId(),event.getAccountId())
                && Objects.equals(original.getSessionId(),event.getSessionId()) && Objects.equals(original.getOfferIdentity(),event.getOfferIdentity())) {
                if(accepted!=null && event.getSequence()>=accepted.getSequence()
                    && (!accepted.matchesEpisode(event) || !accepted.equals(proof)))invalid=true;
                if("CANCELLED_SELL".equals(event.getEventType()))cancelled=true;
            }
        }
        return new Resolution(claimed,claimed && !invalid?accepted:null);
    }
}
