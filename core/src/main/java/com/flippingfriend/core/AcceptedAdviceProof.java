package com.flippingfriend.core;

import java.util.Objects;

/** Original accepted advice preceded the first copied offer in this process; not learning evidence. */
public final class AcceptedAdviceProof {
    private final int formatVersion;
    private final CaptureOrder advice,capture;
    public AcceptedAdviceProof(CaptureOrder advice,CaptureOrder capture){formatVersion=1;this.advice=advice;this.capture=capture;}
    public boolean isValid(){return formatVersion==1 && advice!=null && advice.precedes(capture);}
    @Override public boolean equals(Object other){
        if(!(other instanceof AcceptedAdviceProof))return false;
        var p=(AcceptedAdviceProof)other;
        return isValid() && p.isValid() && Objects.equals(advice.getProcess(),p.advice.getProcess())
            && advice.getOrder()==p.advice.getOrder() && capture.getOrder()==p.capture.getOrder();
    }
    @Override public int hashCode(){return advice==null?0:Objects.hash(advice.getProcess(),advice.getOrder(),capture==null?0:capture.getOrder());}
}
