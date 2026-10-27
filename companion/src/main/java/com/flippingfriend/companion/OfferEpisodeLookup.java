package com.flippingfriend.companion;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Exact retained-identity/unknown-history lookup. No cache, replay or financial effect. */
final class OfferEpisodeLookup {
    static final String SOURCES="event_id IS NOT NULL AND event_id<>''";
    // CASE must guard every JSON function from malformed historical payloads. SQLite's first-key
    // semantics remain identical to the original query; final Gson qualification is unchanged.
    static final String IDENTITY="CASE WHEN json_valid(payload)=0 THEN NULL "
        +"WHEN json_type(payload)<>'object' THEN NULL "
        +"WHEN json_type(payload,'$.offerIdentity')='text' THEN json_extract(payload,'$.offerIdentity') END";
    static final String UNKNOWN="CASE WHEN json_valid(payload)=0 THEN 1 "
        +"WHEN json_type(payload)<>'object' THEN 1 "
        +"WHEN json_type(payload,'$.offerIdentity')='text' THEN 0 "
        +"WHEN event_type='EMPTY' THEN 0 ELSE 1 END";
    static final String SQL="SELECT id,payload FROM event_log INDEXED BY event_log_episode_identity WHERE "
        +SOURCES+" AND event_id<>? AND ("+IDENTITY+")=? UNION ALL "
        +"SELECT id,payload FROM event_log INDEXED BY event_log_episode_unknown WHERE "
        +SOURCES+" AND event_id<>? AND ("+UNKNOWN+")=1 ORDER BY id DESC LIMIT 1";

    static void initialize(Statement statement)throws SQLException {
        statement.execute("CREATE INDEX IF NOT EXISTS event_log_episode_identity ON event_log(("+IDENTITY+"),id) WHERE "+SOURCES);
        statement.execute("CREATE INDEX IF NOT EXISTS event_log_episode_unknown ON event_log(id) WHERE "+SOURCES+" AND ("+UNKNOWN+")=1");
    }
    static final class Match {
        final long row;final String payload;
        Match(long row,String payload){this.row=row;this.payload=payload;}
    }
    static Match find(Connection db,String incomingEventId,String identity)throws SQLException {
        try(var sql=db.prepareStatement(SQL)) {
            sql.setString(1,incomingEventId);sql.setString(2,identity);sql.setString(3,incomingEventId);
            try(var rows=sql.executeQuery()){return rows.next()?new Match(rows.getLong(1),rows.getString(2)):null;}
        }
    }
    private OfferEpisodeLookup(){}
}
