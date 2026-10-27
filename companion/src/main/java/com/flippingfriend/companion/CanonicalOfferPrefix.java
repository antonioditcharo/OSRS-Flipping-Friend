package com.flippingfriend.companion;

import com.flippingfriend.core.OfferEvent;
import java.util.*;

/** Immutable ordered DTO references. A warm append copies block pointers and the tail block only. */
final class CanonicalOfferPrefix extends AbstractList<OfferEvent> implements RandomAccess {
    private static final int BLOCK=256;
    private final OfferEvent[][] blocks;
    private final int size;
    private CanonicalOfferPrefix(OfferEvent[][] blocks,int size){this.blocks=blocks;this.size=size;}
    static CanonicalOfferPrefix append(List<OfferEvent> previous,List<OfferEvent> tail) {
        Objects.requireNonNull(tail);
        if(previous!=null && !(previous instanceof CanonicalOfferPrefix)) {
            var all=new ArrayList<OfferEvent>(Math.addExact(previous.size(),tail.size()));all.addAll(previous);all.addAll(tail);
            return append(null,all);
        }
        int prior=previous==null?0:previous.size(),size=Math.addExact(prior,tail.size());
        if(tail.isEmpty() && previous!=null)return (CanonicalOfferPrefix)previous;
        int count=(int)((size+(long)BLOCK-1)/BLOCK);
        OfferEvent[][] blocks=previous==null?new OfferEvent[count][]:Arrays.copyOf(((CanonicalOfferPrefix)previous).blocks,count);
        if(prior%BLOCK!=0)blocks[prior/BLOCK]=blocks[prior/BLOCK].clone();
        int at=prior;
        for(var event:tail) {
            if(at%BLOCK==0)blocks[at/BLOCK]=new OfferEvent[BLOCK];
            blocks[at/BLOCK][at%BLOCK]=event;at++;
        }
        return new CanonicalOfferPrefix(blocks,size);
    }
    @Override public OfferEvent get(int index){Objects.checkIndex(index,size);return blocks[index/BLOCK][index%BLOCK];}
    @Override public int size(){return size;}
    /** Test/diagnostic bound, not financial or preparation authority. */
    int sharedBlocks(CanonicalOfferPrefix other) {
        int count=0;for(int i=0;i<Math.min(blocks.length,other.blocks.length);i++)if(blocks[i]==other.blocks[i])count++;return count;
    }
}
