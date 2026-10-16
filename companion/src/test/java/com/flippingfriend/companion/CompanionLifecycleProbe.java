package com.flippingfriend.companion;

import java.nio.file.Paths;

/** Disposable child JVM: only lock files and stdin, never service/account initialization. */
public final class CompanionLifecycleProbe
{
    public static void main(String[] args) throws Exception
    {
        try (CompanionLifecycle lifecycle = CompanionLifecycle.tryStart(Paths.get(args[0])))
        {
            if (lifecycle == null) { System.out.println("REFUSED"); return; }
            lifecycle.started();
            System.out.println("STARTED");
            new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
        }
    }
}
