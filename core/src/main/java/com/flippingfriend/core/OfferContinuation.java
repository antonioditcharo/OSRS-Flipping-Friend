package com.flippingfriend.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Local cancel/collect workflow evidence only. Never financial or order-placement authority. */
public final class OfferContinuation {
    public enum Status { WAITING, COLLECTED, CLOSED, UNKNOWN }
    public static final class Observation {
        public final int slot,item,price,total,filled;
        public final long firstSeen;
        public final boolean buying,collected;
        public final String episode,state;
        public Observation(int slot,int item,boolean buying,int price,int total,int filled,long firstSeen,
                String episode,String state,boolean collected) {
            this.slot=slot;this.item=item;this.buying=buying;this.price=price;this.total=total;this.filled=filled;
            this.firstSeen=firstSeen;this.episode=episode;this.state=state;this.collected=collected;
        }
    }
    // Existing sell-relist-intent.json field names are retained. Missing legacy buying means SELL.
    private int item,slot,quantity,price,total,filled;
    private long firstSeen,createdAt,expiresAt;
    private String episode;
    /** Accepted working-sale source; legacy hints retain no permission to sacrifice a target. */
    private String saleRepriceSource;
    private long saleRepriceProgressAt;
    /** A later first source binding does not change when the original workflow was created. */
    private long saleRepriceAcceptedAt;
    private boolean buying,managed,collectionVerified;
    private int collectedRemaining,collectedFilled,maximumFilled;
    private List<Key> existingSales;
    /** Direct successor placements consume this workflow's cap once, independently of fills. */
    private List<ContinuationSuccessorProof> successorClaims;
    private boolean successorBudgetUncertain;
    private transient long revision;
    private transient boolean closed;

    private OfferContinuation() { }
    public static OfferContinuation advise(Observation original,Collection<Observation> board,
            boolean managed,long at,long expiresAt) {
        if (original==null || original.slot<0 || original.slot>=8 || original.item<=0 || original.price<=0
            || original.total<=0 || original.filled<0 || original.filled>=original.total || original.firstSeen<=0
            || original.collected || !active(original) || at<original.firstSeen
            || expiresAt!=0 && expiresAt<=at) return null;
        var intent=new OfferContinuation();
        intent.item=original.item;intent.slot=original.slot;intent.buying=original.buying;
        intent.price=original.price;intent.total=original.total;intent.filled=original.filled;
        intent.maximumFilled=original.filled;intent.quantity=original.total-original.filled;
        intent.firstSeen=original.firstSeen;intent.episode=original.episode;intent.managed=managed;
        intent.createdAt=at;intent.expiresAt=expiresAt;intent.existingSales=new ArrayList<>();
        if(board!=null) for(var other:board)
            if(other!=null && other.buying==intent.buying && other.item==intent.item && !intent.matches(other))
                intent.existingSales.add(new Key(other));
        return intent;
    }
    public boolean matches(Observation offer) {
        return offer!=null && offer.buying==buying && offer.item==item && offer.slot==slot
            && offer.firstSeen==firstSeen && Objects.equals(episode,offer.episode)
            && (price==0 || price==offer.price) && (total==0 || total==offer.total);
    }
    /** Recognized original/parallel episodes from the accepted board; no successor inference. */
    public boolean recognizes(Observation offer){return matches(offer) || alreadyPresent(offer);}
    public boolean valid(long now) {
        return !closed && !successorBudgetUncertain && isWellFormed()
            && firstSeen<=now && createdAt<=now && saleRepriceAcceptedAt<=now && !futureSuccessor(now)
            && (expiresAt==0 || now<=expiresAt);
    }
    /** Retained ordered evidence is temporarily unavailable when the local clock moves backwards. */
    public boolean hasFutureEvidence(long now) {
        return !closed && isWellFormed()
            && (firstSeen>now || createdAt>now || saleRepriceAcceptedAt>now || futureSuccessor(now));
    }
    private boolean futureSuccessor(long now){
        if(successorClaims!=null)for(var proof:successorClaims)
            if(proof!=null && (proof.getAcceptedAt()>now || proof.getFirstSeenAt()>now))return true;
        return false;
    }
    /** Exact observed cancellation of this promise; it neither proves collection nor creates fills. */
    public boolean acknowledgesCancellation(Observation offer,long now) {
        return buying && valid(now) && matches(offer) && cancelled(offer) && validCounter(offer)
            && !offer.collected && offer.filled<offer.total;
    }
    /** Validate retained evidence independently of its legacy recommendation expiry. */
    public boolean isWellFormed() {
        return item>0 && slot>=0 && slot<8 && quantity>0 && firstSeen>0 && filled>=0 && price>=0
            && total>=0 && maximumFilled>=0 && collectedFilled>=0 && createdAt>=0 && expiresAt>=0
            && (total==0 || filled<total && quantity<=total-filled && maximumFilled<=total && collectedFilled<=total)
            && (expiresAt==0 || expiresAt>createdAt)
            && (createdAt==0 || createdAt>=firstSeen)
            && saleRepriceAcceptedAt>=0
            && (saleRepriceSource==null ? saleRepriceProgressAt==0 && saleRepriceAcceptedAt==0
                : !buying && !saleRepriceSource.isBlank() && saleRepriceProgressAt>=firstSeen
                && (saleRepriceAcceptedAt==0 ? saleRepriceProgressAt<=createdAt
                    : saleRepriceAcceptedAt>=createdAt && saleRepriceProgressAt<=saleRepriceAcceptedAt))
            && validSuccessorClaims()
            && (!collectionVerified || collectedRemaining>=0 && (collectedRemaining>0 || getClaimedQuantity()>0) && collectedRemaining<=quantity
                && (total==0 || collectedRemaining<=total-Math.max(filled,Math.max(maximumFilled,collectedFilled))));
    }
    /** Workflow evidence survives time; every resulting order still needs fresh final authority. */
    public boolean retainForFreshRevalidation() {
        if(!buying || expiresAt==0)return false;
        expiresAt=0;revision++;return true;
    }
    /** Call before temporary settled evidence is pruned, and again before using a retained hint. */
    public Status observe(Collection<Observation> board,Observation settled,long now) {
        return observe(board,settled,now,Collections.emptyList(),Collections.emptySet());
    }
    /** Relationships must already be qualified against the owning account and accepted origin. */
    public Status observe(Collection<Observation> board,Observation settled,long now,
            Collection<ContinuationSuccessorProof> successors,Collection<String> unresolvedEpisodes) {
        if(successorBudgetUncertain)return Status.UNKNOWN;
        boolean future=hasFutureEvidence(now);
        // Clock qualification gates guidance, not retention of current observed fill/closure facts.
        if(!future && !valid(now)) return close();
        boolean unknown=!claimSuccessors(successors);
        Observation original=null;
        if(board!=null) for(var offer:board) {
            if(offer==null) continue;
            if(matches(offer)) original=offer;
            else if(offer.buying==buying && offer.item==item && !alreadyPresent(offer)) {
                var relation=successorFor(offer,successors);
                if(relation!=null)continue;
                String identity=identity(offer.slot,offer.item,offer.buying,offer.price,offer.total,offer.firstSeen,offer.episode);
                if(unresolvedEpisodes!=null && unresolvedEpisodes.contains(identity))unknown=true;
                else return close();
            }
        }
        if(original!=null) {
            if(!validCounter(original))return counterRefusal();
            progress(original);
            if(claimsExceedCap())return Status.UNKNOWN;
            if(completed(original) || original.filled>=original.total)return close();
            return unknown?Status.UNKNOWN:Status.WAITING; // A cancelled offer still in its slot is not collected.
        }
        if(matches(settled)) {
            if(!validCounter(settled))return counterRefusal();
            progress(settled);
            if(claimsExceedCap())return Status.UNKNOWN;
            if(completed(settled) || !settled.collected || !cancelled(settled))return close();
            int remainder=Math.min(quantity,settled.total-settled.filled);
            if(collectionVerified)remainder=Math.min(remainder,collectedRemaining);
            if(remainder<=0) return close();
            if(!collectionVerified || collectedRemaining!=remainder || collectedFilled!=settled.filled) {
                collectionVerified=true;collectedRemaining=remainder;collectedFilled=settled.filled;revision++;
            }
        }
        // Check again after accepting collection in this same pass. Collected sale receipts and
        // cancelled relists are still exit decisions; they cannot restore an old rebuy promise.
        if(collectionVerified && board!=null)for(var offer:board)
            if(buying && createdAt>0 && offer!=null && !offer.buying && offer.item==item
                && offer.firstSeen>createdAt && offer.firstSeen>firstSeen && offer.firstSeen<=now
                && offer.episode!=null && !offer.episode.isBlank() && !Objects.equals(episode,offer.episode)
                && offer.price>0 && offer.total>0 && offer.filled>=0 && offer.filled<=offer.total
                && (active(offer) || completed(offer) || cancelled(offer))) {
                String identity=identity(offer.slot,offer.item,offer.buying,offer.price,offer.total,offer.firstSeen,offer.episode);
                if(unresolvedEpisodes!=null && unresolvedEpisodes.contains(identity))unknown=true;else return close();
            }
        if(unknown || claimsExceedCap())return Status.UNKNOWN;
        // The store retains consumed origins separately, so later original fills can still expose
        // a contradiction. Budget consumption itself is not an irreversible lifecycle closure.
        if(isFullyClaimed())return Status.CLOSED;
        return !future && !hasFutureEvidence(now) && collectionVerified && getCollectedRemaining()>0 && collectedRemaining<=quantity
            ?Status.COLLECTED:Status.WAITING;
    }
    private boolean validSuccessorClaims() {
        if(successorClaims==null)return true;
        var identities=new HashSet<String>();
        for(var proof:successorClaims)if(!ownClaim(proof) || !identities.add(proof.getSuccessorIdentity()))return false;
        return true;
    }
    private boolean ownClaim(ContinuationSuccessorProof proof) {
        return proof!=null && proof.isBound() && proof.getItemId()==item && proof.isBuying()==buying
            && Objects.equals(getOriginalIdentity(),proof.getOriginIdentity())
            && !Objects.equals(getOriginalIdentity(),proof.getSuccessorIdentity()) && proof.getAcceptedAt()>=createdAt;
    }
    private boolean claimSuccessors(Collection<ContinuationSuccessorProof> proofs) {
        if(proofs==null)return true;
        boolean valid=true;
        for(var proof:proofs)if(proof!=null && Objects.equals(getOriginalIdentity(),proof.getOriginIdentity())) {
            if(!ownClaim(proof) || !collectionVerified){valid=false;continue;}
            ContinuationSuccessorProof existing=null;
            if(successorClaims!=null)for(var old:successorClaims)
                if(Objects.equals(old.getSuccessorIdentity(),proof.getSuccessorIdentity())){existing=old;break;}
            if(existing!=null){if(!existing.equals(proof))valid=false;continue;}
            if(successorClaims==null)successorClaims=new ArrayList<>();
            successorClaims.add(proof);revision++;
        }
        return valid;
    }
    private ContinuationSuccessorProof successorFor(Observation offer,Collection<ContinuationSuccessorProof> proofs) {
        ContinuationSuccessorProof found=null;
        var all=new ArrayList<ContinuationSuccessorProof>();
        if(successorClaims!=null)all.addAll(successorClaims);if(proofs!=null)all.addAll(proofs);
        for(var proof:all) {
            if(proof==null || !proof.isBound() || proof.getItemId()!=offer.item || proof.isBuying()!=offer.buying
                || proof.getSlot()!=offer.slot || proof.getPrice()!=offer.price || proof.getQuantity()!=offer.total
                || proof.getFirstSeenAt()!=offer.firstSeen || !proof.matches(offer))continue;
            if(found!=null && !found.equals(proof))return null;
            found=proof;
        }
        return found;
    }
    private boolean claimsExceedCap(){return getClaimedQuantity()>0 && (!collectionVerified || getClaimedQuantity()>collectedRemaining);}
    private Status counterRefusal(){
        if(getClaimedQuantity()==0)return close();
        successorBudgetUncertain=true;revision++;return Status.UNKNOWN;
    }
    /** Consumed/retired allocation evidence still monitors its own original counters. Other new
     * episodes cannot terminate this read-only budget monitor or erase a later contradiction. */
    public Status observeConsumedBudget(Collection<Observation> board,Observation settled,
            Collection<ContinuationSuccessorProof> successors) {
        if(!isWellFormed() || getClaimedQuantity()<=0 || successorBudgetUncertain)return Status.UNKNOWN;
        boolean qualified=claimSuccessors(successors);
        Observation original=null;
        if(board!=null)for(var offer:board)if(matches(offer))original=offer;
        if(original==null && matches(settled))original=settled;
        if(original!=null) {
            if(!validCounter(original)) {
                successorBudgetUncertain=true;revision++;return Status.UNKNOWN;
            }
            progress(original);
        }
        return !qualified || claimsExceedCap()?Status.UNKNOWN:Status.WAITING;
    }
    public boolean supersededBy(ContinuationDisposition sale) {
        return buying && collectionVerified && createdAt>0 && sale!=null && sale.isValid()
            && sale.getItemId()==item && !Objects.equals(episode,sale.getOfferIdentity())
            && sale.getFirstSeenAt()>createdAt && sale.getFirstSeenAt()>firstSeen;
    }
    private boolean validCounter(Observation offer) {
        return offer.total>0 && offer.filled>=Math.max(Math.max(filled,maximumFilled),collectedFilled)
            && offer.filled<=offer.total;
    }
    private void progress(Observation offer) {
        boolean changed=offer.filled>maximumFilled;
        maximumFilled=Math.max(maximumFilled,offer.filled);
        // Use the verified current total even when legacy metadata lacks it. Do not rewrite the
        // original identity fields or enlarge a previously more conservative collection receipt.
        if(collectionVerified && offer.total-offer.filled<collectedRemaining) {
            collectedRemaining=offer.total-offer.filled;changed=true;
        }
        if(changed)revision++;
    }
    private Status close() { if(!closed) {closed=true;revision++;}return Status.CLOSED; }
    private boolean alreadyPresent(Observation offer) {
        if(existingSales!=null) for(var key:existingSales) if(key!=null && key.matches(offer)) return true;
        return false;
    }
    private static boolean active(Observation o) {return (o.buying?"BUYING":"SELLING").equals(o.state);}
    private static boolean completed(Observation o) {return (o.buying?"BOUGHT":"SOLD").equals(o.state);}
    private static boolean cancelled(Observation o) {return (o.buying?"CANCELLED_BUY":"CANCELLED_SELL").equals(o.state);}
    public int getItemId(){return item;}
    public int getSlot(){return slot;}
    public static String identity(int slot,int item,boolean buying,int price,int total,long firstSeen,String episode) {
        return episode!=null?episode:slot+":"+item+":"+buying+":"+price+":"+total+":"+firstSeen;
    }
    public String getOriginalIdentity(){return identity(slot,item,buying,price,total,firstSeen,episode);}
    public int getCollectedRemaining(){return (int)Math.max(0,(long)collectedRemaining-getClaimedQuantity());}
    public int getCollectedCap(){return collectedRemaining;}
    public long getClaimedQuantity(){
        long claimed=0;if(successorClaims!=null)for(var proof:successorClaims) {
            if(proof==null || !proof.isBound())return Long.MAX_VALUE;
            try{claimed=Math.addExact(claimed,proof.getQuantity());}catch(ArithmeticException overflow){return Long.MAX_VALUE;}
        }
        return claimed;
    }
    public List<ContinuationSuccessorProof> getSuccessorClaims(){return successorClaims==null?Collections.emptyList():Collections.unmodifiableList(successorClaims);}
    public boolean isFullyClaimed(){return !closed && !successorBudgetUncertain && collectionVerified && getClaimedQuantity()>0 && getClaimedQuantity()==collectedRemaining;}
    public boolean isManaged(){return managed;}
    public boolean isBuying(){return buying;}
    public String getSaleRepriceSource(){return saleRepriceSource;}
    public int getOriginalPrice(){return price;}
    public long getFirstSeenAt(){return firstSeen;}
    public long getSaleRepriceProgressAt(){return saleRepriceProgressAt;}
    public long getSaleRepriceAcceptedAt(){return saleRepriceAcceptedAt==0?saleRepriceSource==null?0:createdAt:saleRepriceAcceptedAt;}
    public void bindSaleRepriceSource(String source) {
        bindSaleRepriceSource(source,firstSeen);
    }
    public void bindSaleRepriceSource(String source,long progressAt) {
        bindSaleRepriceSource(source,progressAt,createdAt);
    }
    public void bindSaleRepriceSource(String source,long progressAt,long acceptedAt) {
        if(buying || source==null || source.isBlank())throw new IllegalArgumentException("Sale source required");
        if(progressAt<firstSeen || acceptedAt<createdAt || acceptedAt<progressAt)
            throw new IllegalArgumentException("Ordered sale progress and acceptance required");
        if(saleRepriceSource!=null && !saleRepriceSource.equals(source))
            throw new IllegalStateException("Accepted sale source changed");
        if(saleRepriceSource==null){saleRepriceSource=source;saleRepriceProgressAt=progressAt;saleRepriceAcceptedAt=acceptedAt;revision++;}
    }
    public long getRevision(){return revision;}
    public boolean isClosed(){return closed;}
    public void requireManagedCost(){if(!managed){managed=true;revision++;}}
    private static final class Key {
        private int item,slot,price,total;private long firstSeen;private String episode;
        Key(Observation offer){item=offer.item;slot=offer.slot;price=offer.price;total=offer.total;firstSeen=offer.firstSeen;episode=offer.episode;}
        boolean matches(Observation offer){return item==offer.item && slot==offer.slot && price==offer.price
            && total==offer.total && firstSeen==offer.firstSeen && Objects.equals(episode,offer.episode);}
    }
}
