package com.flippingfriend.companion;

import okhttp3.OkHttpClient;

/** Shared HTTP identity and connection pool for active companion market requests. */
final class CompanionHttp
{
        static final String USER_AGENT =
                "OSRS-Flipping-Friend/1.0.0 - companion - https:" +
                "//github.com/antonioditcharo/OSRS-Flipping-Friend";

        static final OkHttpClient CLIENT = new OkHttpClient();

        private CompanionHttp()
        {
        }
}
